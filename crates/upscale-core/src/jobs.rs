use crate::{
    Error, ExportOptions, ExportPlan, JobSnapshot, JobStatus, MediaInfo, Result, Toolchain,
    media::hidden_command, probe,
};
use std::{
    collections::{HashMap, VecDeque},
    io::{BufRead, BufReader, Read},
    path::{Path, PathBuf},
    process::{Child, Stdio},
    sync::{
        Arc, Mutex, MutexGuard,
        atomic::{AtomicBool, Ordering},
        mpsc,
    },
    thread,
    time::Duration,
};
use tempfile::{Builder, TempPath};
use uuid::Uuid;

fn lock<T>(value: &Mutex<T>) -> MutexGuard<'_, T> {
    value
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
}

#[derive(Clone)]
struct Record {
    snapshot: Arc<Mutex<JobSnapshot>>,
    cancelled: Arc<AtomicBool>,
    child: Arc<Mutex<Option<Child>>>,
    output: PathBuf,
}

struct ProcessGuard(Arc<Mutex<Option<Child>>>);

impl Drop for ProcessGuard {
    fn drop(&mut self) {
        let mut child = lock(&self.0);
        if let Some(process) = child.as_mut() {
            let _ = process.kill();
            let _ = process.wait();
        }
        *child = None;
    }
}

#[derive(Clone)]
pub struct JobManager {
    tools: Toolchain,
    records: Arc<Mutex<HashMap<String, Record>>>,
    active: Arc<Mutex<Option<String>>>,
}

impl JobManager {
    pub fn new(tools: Toolchain) -> Self {
        Self {
            tools,
            records: Default::default(),
            active: Default::default(),
        }
    }

    pub fn is_busy(&self) -> bool {
        lock(&self.active).is_some()
    }

    pub fn start(
        &self,
        input: PathBuf,
        info: MediaInfo,
        options: ExportOptions,
        output: PathBuf,
    ) -> Result<JobSnapshot> {
        let plan = ExportPlan::new(&info, options)?;
        let input = input.canonicalize()?;
        if output
            .extension()
            .and_then(|v| v.to_str())
            .is_none_or(|v| !v.eq_ignore_ascii_case("mp4"))
        {
            return Err(Error::Invalid("The output must be an MP4 file.".into()));
        }
        let parent = output
            .parent()
            .filter(|p| !p.as_os_str().is_empty())
            .unwrap_or(Path::new("."));
        let output = parent.canonicalize()?.join(
            output
                .file_name()
                .ok_or_else(|| Error::Invalid("Invalid output filename.".into()))?,
        );
        if output.exists() {
            return Err(Error::Invalid("The output already exists. Choose a new filename; existing files are never overwritten.".into()));
        }
        let temporary = Builder::new()
            .prefix(".upscale-")
            .suffix(".mp4")
            .tempfile_in(output.parent().unwrap())?
            .into_temp_path();
        let mut active = lock(&self.active);
        if active.is_some() {
            return Err(Error::Busy);
        }
        let id = Uuid::new_v4().to_string();
        let snapshot = JobSnapshot {
            id: id.clone(),
            status: JobStatus::Queued,
            progress: 0.,
            processed_seconds: 0.,
            output_name: output.file_name().unwrap().to_string_lossy().into(),
            error: None,
        };
        let record = Record {
            snapshot: Arc::new(Mutex::new(snapshot.clone())),
            cancelled: Arc::new(AtomicBool::new(false)),
            child: Default::default(),
            output,
        };
        {
            let mut records = lock(&self.records);
            if records.len() >= 32 {
                records.retain(|_, record| !lock(&record.snapshot).status.is_terminal());
            }
            records.insert(id.clone(), record.clone());
        }
        *active = Some(id.clone());
        drop(active);
        let manager = self.clone();
        if let Err(error) = thread::Builder::new()
            .name("upscale-export".into())
            .spawn(move || {
                let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                    manager.execute(&record, &input, &info, &plan, temporary)
                }));
                let mut snapshot = lock(&record.snapshot);
                match result {
                    Ok(Ok(())) => {
                        snapshot.status = JobStatus::Completed;
                        snapshot.progress = 1.;
                        snapshot.processed_seconds = info.duration_seconds;
                    }
                    _ if record.cancelled.load(Ordering::SeqCst) => {
                        snapshot.status = JobStatus::Cancelled
                    }
                    Ok(Err(error)) => {
                        snapshot.status = JobStatus::Failed;
                        snapshot.error = Some(error.to_string());
                    }
                    Err(_) => {
                        snapshot.status = JobStatus::Failed;
                        snapshot.error = Some("The export worker stopped unexpectedly.".into());
                    }
                }
                drop(snapshot);
                if let Some(child) = lock(&record.child).as_mut() {
                    let _ = child.kill();
                    let _ = child.wait();
                }
                *lock(&record.child) = None;
                *lock(&manager.active) = None;
            })
        {
            *lock(&self.active) = None;
            lock(&self.records).remove(&id);
            return Err(error.into());
        }
        Ok(snapshot)
    }

    fn execute(
        &self,
        record: &Record,
        input: &Path,
        info: &MediaInfo,
        plan: &ExportPlan,
        temporary: TempPath,
    ) -> Result<()> {
        // A cancellation waits for this critical section, so a process cannot be
        // spawned after the window's synchronous cancellation has returned.
        let mut child_slot = lock(&record.child);
        if record.cancelled.load(Ordering::SeqCst) {
            return Err(Error::Invalid("Cancelled".into()));
        }
        let mut child = hidden_command(&self.tools.ffmpeg)
            .args(plan.args(input, &temporary))
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()?;
        let stdout = child
            .stdout
            .take()
            .ok_or_else(|| Error::ToolFailed("Cannot read FFmpeg progress.".into()))?;
        let stderr = child
            .stderr
            .take()
            .ok_or_else(|| Error::ToolFailed("Cannot read FFmpeg errors.".into()))?;
        *child_slot = Some(child);
        drop(child_slot);
        let _process_guard = ProcessGuard(record.child.clone());
        lock(&record.snapshot).status = JobStatus::Running;
        let (sender, receiver) = mpsc::channel();
        let progress_reader = thread::spawn(move || {
            for line in BufReader::new(stdout)
                .lines()
                .map_while(std::result::Result::ok)
            {
                if let Some(value) = line
                    .strip_prefix("out_time_us=")
                    .and_then(|s| s.parse::<f64>().ok())
                    .filter(|v| v.is_finite())
                {
                    let _ = sender.send((value / 1_000_000.).max(0.));
                }
            }
        });
        let errors_reader = thread::spawn(move || {
            let mut reader = BufReader::new(stderr);
            let mut tail = VecDeque::with_capacity(8192);
            let mut buffer = [0; 4096];
            loop {
                match reader.read(&mut buffer) {
                    Ok(0) | Err(_) => break,
                    Ok(count) => {
                        tail.extend(&buffer[..count]);
                        while tail.len() > 8192 {
                            tail.pop_front();
                        }
                    }
                }
            }
            String::from_utf8_lossy(&tail.into_iter().collect::<Vec<_>>()).into_owned()
        });
        let status = loop {
            if record.cancelled.load(Ordering::SeqCst)
                && let Some(child) = lock(&record.child).as_mut()
            {
                let _ = child.kill();
            }
            let next = lock(&record.child)
                .as_mut()
                .ok_or_else(|| Error::ToolFailed("Export process is missing.".into()))?
                .try_wait()?;
            if let Some(status) = next {
                break status;
            }
            if let Ok(seconds) = receiver.recv_timeout(Duration::from_millis(100)) {
                let mut snapshot = lock(&record.snapshot);
                snapshot.processed_seconds = snapshot
                    .processed_seconds
                    .max(seconds.min(info.duration_seconds));
                snapshot.progress =
                    (snapshot.processed_seconds / info.duration_seconds).clamp(0., 0.99);
            } else {
                thread::sleep(Duration::from_millis(25));
            }
        };
        let _ = progress_reader.join();
        let errors = errors_reader
            .join()
            .unwrap_or_else(|_| "Unable to read encoder output.".into());
        *lock(&record.child) = None;
        if record.cancelled.load(Ordering::SeqCst) {
            return Err(Error::Invalid("Cancelled".into()));
        }
        if !status.success() {
            return Err(Error::ToolFailed(format!(
                "FFmpeg export failed: {}",
                errors.trim()
            )));
        }
        let verified = probe(&self.tools, &temporary)?;
        let tolerance = 0.35_f64.max(3. / info.frame_rate);
        if verified.width != plan.width
            || verified.height != plan.height
            || (verified.duration_seconds - info.duration_seconds).abs() > tolerance
            || (info.has_audio && !verified.has_audio)
        {
            return Err(Error::ToolFailed("The encoded output failed validation (dimensions, duration, or audio). No final file was published.".into()));
        }
        if record.cancelled.load(Ordering::SeqCst) {
            return Err(Error::Invalid("Cancelled".into()));
        }
        temporary
            .persist_noclobber(&record.output)
            .map_err(|error| Error::Io(error.error))?;
        Ok(())
    }

    pub fn snapshot(&self, id: &str) -> Result<JobSnapshot> {
        let records = lock(&self.records);
        let record = records.get(id).ok_or(Error::UnknownJob)?;
        Ok(lock(&record.snapshot).clone())
    }

    pub fn cancel(&self, id: &str) -> Result<()> {
        let records = lock(&self.records);
        let record = records.get(id).ok_or(Error::UnknownJob)?;
        if lock(&record.snapshot).status.is_terminal() {
            return Ok(());
        }
        record.cancelled.store(true, Ordering::SeqCst);
        if let Some(child) = lock(&record.child).as_mut() {
            let _ = child.kill();
        }
        Ok(())
    }

    pub fn cancel_all(&self) {
        let ids: Vec<_> = lock(&self.records).keys().cloned().collect();
        for id in ids {
            let _ = self.cancel(&id);
        }
    }

    pub fn output(&self, id: &str) -> Result<PathBuf> {
        let records = lock(&self.records);
        let record = records.get(id).ok_or(Error::UnknownJob)?;
        if lock(&record.snapshot).status != JobStatus::Completed {
            return Err(Error::Invalid("The export is not complete.".into()));
        }
        Ok(record.output.clone())
    }
}
