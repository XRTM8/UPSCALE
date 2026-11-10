mod jobs;
mod media;
mod plan;

pub use jobs::JobManager;
pub use media::{Toolchain, probe};
pub use plan::ExportPlan;
pub use upscale_types::*;

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("{0}")]
    Invalid(String),
    #[error("Required tool not found: {0}. Install FFmpeg and ffprobe or add them to PATH.")]
    ToolMissing(String),
    #[error("{0}")]
    ToolFailed(String),
    #[error("Another export is already running.")]
    Busy,
    #[error("Unknown job.")]
    UnknownJob,
    #[error(transparent)]
    Io(#[from] std::io::Error),
    #[error(transparent)]
    Json(#[from] serde_json::Error),
}

pub type Result<T> = std::result::Result<T, Error>;
