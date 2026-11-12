use std::{path::PathBuf, thread, time::Duration};
use upscale_core::{ExportOptions, JobManager, JobStatus, Toolchain, probe};

fn run() -> upscale_core::Result<()> {
    let arguments: Vec<_> = std::env::args_os().collect();
    let tools = Toolchain::discover()?;
    let input = arguments.get(2).map(PathBuf::from).ok_or_else(|| {
        upscale_core::Error::Invalid(
            "Usage: upscale-cli probe INPUT | upscale-cli export INPUT OUTPUT.mp4".into(),
        )
    })?;
    let info = probe(&tools, &input)?;
    match arguments.get(1).and_then(|s| s.to_str()) {
        Some("probe") => println!("{}", serde_json::to_string_pretty(&info)?),
        Some("export") => {
            let output = arguments.get(3).map(PathBuf::from).ok_or_else(|| {
                upscale_core::Error::Invalid("An output MP4 path is required.".into())
            })?;
            let manager = JobManager::new(tools);
            let job = manager.start(input, info, ExportOptions::default(), output)?;
            loop {
                let snapshot = manager.snapshot(&job.id)?;
                println!("{}", serde_json::to_string(&snapshot)?);
                if snapshot.status.is_terminal() {
                    if snapshot.status != JobStatus::Completed {
                        return Err(upscale_core::Error::ToolFailed(
                            snapshot.error.unwrap_or("Export cancelled.".into()),
                        ));
                    }
                    break;
                }
                thread::sleep(Duration::from_millis(500));
            }
        }
        _ => {
            return Err(upscale_core::Error::Invalid(
                "Unknown command. Use probe or export.".into(),
            ));
        }
    }
    Ok(())
}

fn main() {
    if let Err(error) = run() {
        eprintln!("{error}");
        std::process::exit(1);
    }
}
