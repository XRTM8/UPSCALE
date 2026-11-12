use std::{
    fs,
    path::{Path, PathBuf},
    process::Command,
    thread,
    time::{Duration, Instant},
};
use upscale_core::{ExportOptions, JobManager, JobStatus, Quality, Toolchain, probe};

fn fixture(directory: &Path, seconds: u32) -> (Toolchain, PathBuf) {
    let tools = Toolchain::discover().expect("Pipeline tests require FFmpeg and ffprobe on PATH");
    let input = directory.join("مقطع & [original].mp4");
    let status = Command::new(&tools.ffmpeg)
        .args(["-hide_banner", "-v", "error", "-f", "lavfi", "-i"])
        .arg(format!("testsrc2=size=160x90:rate=24:duration={seconds}"))
        .args(["-f", "lavfi", "-i"])
        .arg(format!(
            "sine=frequency=440:sample_rate=48000:duration={seconds}"
        ))
        .args([
            "-c:v",
            "libx264",
            "-preset",
            "ultrafast",
            "-pix_fmt",
            "yuv420p",
            "-c:a",
            "aac",
            "-shortest",
        ])
        .arg(&input)
        .status()
        .unwrap();
    assert!(status.success());
    (tools, input)
}

fn wait(manager: &JobManager, id: &str) -> upscale_core::JobSnapshot {
    let started = Instant::now();
    loop {
        let snapshot = manager.snapshot(id).unwrap();
        if snapshot.status.is_terminal() {
            return snapshot;
        }
        assert!(
            started.elapsed() < Duration::from_secs(90),
            "Export timed out"
        );
        thread::sleep(Duration::from_millis(50));
    }
}

#[test]
fn exports_verified_vertical_video_with_audio_and_preserves_source() {
    let dir = tempfile::tempdir().unwrap();
    let (tools, input) = fixture(dir.path(), 1);
    let original = fs::read(&input).unwrap();
    let info = probe(&tools, &input).unwrap();
    let output = dir.path().join("نتيجة [1].mp4");
    let manager = JobManager::new(tools.clone());
    let options = ExportOptions {
        quality: Quality::Balanced,
        ..Default::default()
    };
    let job = manager
        .start(input.clone(), info, options, output.clone())
        .unwrap();
    let result = wait(&manager, &job.id);
    assert_eq!(result.status, JobStatus::Completed, "{:?}", result.error);
    assert_eq!(result.progress, 1.);
    let exported = probe(&tools, &output).unwrap();
    assert_eq!((exported.width, exported.height), (1080, 1920));
    assert!(exported.has_audio);
    assert!((exported.duration_seconds - 1.).abs() < 0.15);
    assert_eq!(fs::read(input).unwrap(), original);
    assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 2);
}

#[test]
fn interpolation_keeps_the_last_frames_and_full_video_duration() {
    let dir = tempfile::tempdir().unwrap();
    let (tools, input) = fixture(dir.path(), 1);
    let info = probe(&tools, &input).unwrap();
    let output = dir.path().join("smooth.mp4");
    let manager = JobManager::new(tools.clone());
    let options = ExportOptions {
        quality: Quality::Balanced,
        smooth: true,
        ..Default::default()
    };
    let job = manager.start(input, info, options, output.clone()).unwrap();
    let result = wait(&manager, &job.id);
    assert_eq!(result.status, JobStatus::Completed, "{:?}", result.error);
    let inspection = Command::new(&tools.ffprobe)
        .args([
            "-v",
            "error",
            "-select_streams",
            "v:0",
            "-show_entries",
            "stream=duration,nb_frames,r_frame_rate",
            "-of",
            "json",
        ])
        .arg(output)
        .output()
        .unwrap();
    assert!(inspection.status.success());
    let metadata: serde_json::Value = serde_json::from_slice(&inspection.stdout).unwrap();
    let video = &metadata["streams"][0];
    assert_eq!(video["r_frame_rate"], "60/1");
    assert_eq!(video["nb_frames"], "60", "{metadata}");
    let duration: f64 = video["duration"].as_str().unwrap().parse().unwrap();
    assert!((duration - 1.).abs() < 1. / 60., "{metadata}");
}

#[test]
fn cancellation_leaves_no_final_or_partial_file_and_busy_jobs_are_rejected() {
    let dir = tempfile::tempdir().unwrap();
    let (tools, input) = fixture(dir.path(), 12);
    let info = probe(&tools, &input).unwrap();
    let manager = JobManager::new(tools);
    let output = dir.path().join("cancelled.mp4");
    let job = manager
        .start(
            input.clone(),
            info.clone(),
            ExportOptions::default(),
            output.clone(),
        )
        .unwrap();
    assert!(
        manager
            .start(
                input,
                info,
                ExportOptions::default(),
                dir.path().join("other.mp4")
            )
            .is_err()
    );
    manager.cancel(&job.id).unwrap();
    assert_eq!(wait(&manager, &job.id).status, JobStatus::Cancelled);
    assert!(!output.exists());
    assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 1);
}

#[test]
fn existing_outputs_are_never_overwritten() {
    let dir = tempfile::tempdir().unwrap();
    let (tools, input) = fixture(dir.path(), 1);
    let info = probe(&tools, &input).unwrap();
    let output = dir.path().join("existing.mp4");
    fs::write(&output, "keep me").unwrap();
    let manager = JobManager::new(tools);
    assert!(
        manager
            .start(input, info, ExportOptions::default(), output.clone())
            .is_err()
    );
    assert_eq!(fs::read_to_string(output).unwrap(), "keep me");
}

#[test]
fn encoder_failure_is_reported_and_partial_output_is_cleaned() {
    let dir = tempfile::tempdir().unwrap();
    let (tools, input) = fixture(dir.path(), 1);
    let info = probe(&tools, &input).unwrap();
    // Simulate the source changing after inspection, before FFmpeg can read it.
    fs::write(&input, b"not a video anymore").unwrap();
    let output = dir.path().join("failed.mp4");
    let manager = JobManager::new(tools);
    let job = manager
        .start(input, info, ExportOptions::default(), output.clone())
        .unwrap();
    let result = wait(&manager, &job.id);
    assert_eq!(result.status, JobStatus::Failed);
    assert!(result.error.is_some());
    assert!(!output.exists());
    assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 1);
}

#[test]
fn a_destination_created_during_encoding_is_not_replaced() {
    let dir = tempfile::tempdir().unwrap();
    let (tools, input) = fixture(dir.path(), 3);
    let info = probe(&tools, &input).unwrap();
    let output = dir.path().join("appeared-during-export.mp4");
    let manager = JobManager::new(tools);
    let job = manager
        .start(input, info, ExportOptions::default(), output.clone())
        .unwrap();
    fs::write(&output, b"preserve this new file").unwrap();
    let result = wait(&manager, &job.id);
    assert_eq!(result.status, JobStatus::Failed);
    assert_eq!(fs::read(&output).unwrap(), b"preserve this new file");
    assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 2);
}
