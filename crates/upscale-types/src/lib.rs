use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct MediaInfo {
    pub name: String,
    pub width: u32,
    pub height: u32,
    pub display_width: u32,
    pub display_height: u32,
    pub duration_seconds: f64,
    pub frame_rate: f64,
    pub codec: String,
    pub has_audio: bool,
    pub hdr: bool,
    pub interlaced: bool,
    pub size_bytes: u64,
    pub video_stream: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ImportedMedia {
    pub id: String,
    pub preview_url: String,
    pub info: MediaInfo,
    pub native: bool,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Default)]
#[serde(rename_all = "snake_case")]
pub enum Platform {
    #[default]
    Instagram,
    Facebook,
    Tiktok,
    Youtube,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Default)]
#[serde(rename_all = "snake_case")]
pub enum PostKind {
    #[default]
    Reel,
    Story,
    Landscape,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Default)]
#[serde(rename_all = "snake_case")]
pub enum Quality {
    #[default]
    Maximum,
    Balanced,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Default)]
#[serde(rename_all = "snake_case")]
pub enum Fit {
    #[default]
    Contain,
    Cover,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Default)]
#[serde(rename_all = "snake_case")]
pub enum Resolution {
    #[default]
    FullHd,
    Uhd,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(default, rename_all = "camelCase")]
pub struct ExportOptions {
    pub platform: Platform,
    pub post: PostKind,
    pub quality: Quality,
    pub fit: Fit,
    pub resolution: Resolution,
    pub denoise: bool,
    pub detail: bool,
    pub smooth: bool,
}

impl Default for ExportOptions {
    fn default() -> Self {
        Self {
            platform: Platform::Instagram,
            post: PostKind::Reel,
            quality: Quality::Maximum,
            fit: Fit::Contain,
            resolution: Resolution::FullHd,
            denoise: true,
            detail: true,
            smooth: false,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum JobStatus {
    Queued,
    Running,
    Completed,
    Cancelled,
    Failed,
}

impl JobStatus {
    pub fn is_terminal(&self) -> bool {
        matches!(self, Self::Completed | Self::Cancelled | Self::Failed)
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JobSnapshot {
    pub id: String,
    pub status: JobStatus,
    pub progress: f64,
    pub processed_seconds: f64,
    pub output_name: String,
    pub error: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SystemStatus {
    pub desktop: bool,
    pub tools_available: bool,
    pub engine: String,
    pub error: Option<String>,
    #[serde(default)]
    pub can_install_tools: bool,
    #[serde(default)]
    pub can_update: bool,
}
