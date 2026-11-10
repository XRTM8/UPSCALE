use crate::{Error, MediaInfo, Result};
use serde_json::Value;
use std::{
    env,
    path::{Path, PathBuf},
    process::{Command, Output},
};

#[derive(Debug, Clone)]
pub struct Toolchain {
    pub ffmpeg: PathBuf,
    pub ffprobe: PathBuf,
}

impl Toolchain {
    pub fn discover() -> Result<Self> {
        Ok(Self {
            ffmpeg: find_tool("ffmpeg")?,
            ffprobe: find_tool("ffprobe")?,
        })
    }

    pub fn verify(&self) -> Result<()> {
        for path in [&self.ffmpeg, &self.ffprobe] {
            let result = hidden_command(path).arg("-version").output()?;
            if !result.status.success() {
                return Err(Error::ToolFailed(format!(
                    "Unable to run {}",
                    path.display()
                )));
            }
        }
        Ok(())
    }
}

fn find_tool(name: &str) -> Result<PathBuf> {
    let variable = format!("UPSCALE_{}", name.to_uppercase());
    if let Some(path) = env::var_os(variable) {
        let path = PathBuf::from(path);
        if path.is_file() {
            return Ok(path);
        }
        return Err(Error::ToolMissing(name.into()));
    }
    let executable = format!("{name}{}", env::consts::EXE_SUFFIX);
    let mut directories = vec![];
    if let Ok(exe) = env::current_exe()
        && let Some(parent) = exe.parent()
    {
        directories.push(parent.join("tools"));
        directories.push(parent.to_path_buf());
    }
    if let Some(path) = env::var_os("PATH") {
        directories.extend(env::split_paths(&path));
    }
    directories
        .into_iter()
        .map(|directory| directory.join(&executable))
        .find(|path| path.is_file())
        .ok_or_else(|| Error::ToolMissing(name.into()))
}

pub(crate) fn hidden_command(path: &Path) -> Command {
    let mut command = Command::new(path);
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x0800_0000); // CREATE_NO_WINDOW: no console per frame/job.
    }
    command
}

pub(crate) fn successful(output: Output, operation: &str) -> Result<Vec<u8>> {
    if !output.status.success() {
        let error = String::from_utf8_lossy(&output.stderr);
        let tail: String = error
            .chars()
            .rev()
            .take(2000)
            .collect::<String>()
            .chars()
            .rev()
            .collect();
        return Err(Error::ToolFailed(format!("{operation}: {tail}")));
    }
    Ok(output.stdout)
}

pub fn probe(tools: &Toolchain, path: &Path) -> Result<MediaInfo> {
    let path = path.canonicalize()?;
    let metadata = path.metadata()?;
    if !metadata.is_file() {
        return Err(Error::Invalid("Choose a regular video file.".into()));
    }
    let extension = path
        .extension()
        .and_then(|s| s.to_str())
        .unwrap_or_default()
        .to_ascii_lowercase();
    if !matches!(
        extension.as_str(),
        "mp4" | "mov" | "m4v" | "mkv" | "webm" | "avi"
    ) {
        return Err(Error::Invalid(
            "Supported inputs: MP4, MOV, M4V, MKV, WebM, AVI.".into(),
        ));
    }
    let output = hidden_command(&tools.ffprobe)
        .args([
            "-v",
            "error",
            "-protocol_whitelist",
            "file,pipe",
            "-show_format",
            "-show_streams",
            "-of",
            "json",
            "-i",
        ])
        .arg(&path)
        .output()?;
    let bytes = successful(output, "Cannot inspect video")?;
    parse_probe(
        &bytes,
        path.file_name()
            .unwrap_or_default()
            .to_string_lossy()
            .into(),
        metadata.len(),
    )
}

fn number(value: &Value) -> Option<f64> {
    value
        .as_f64()
        .or_else(|| value.as_str()?.parse().ok())
        .filter(|v| v.is_finite())
}

fn rate(value: &Value) -> Option<f64> {
    let string = value.as_str()?;
    let (a, b) = string.split_once('/')?;
    let (a, b): (f64, f64) = (a.parse().ok()?, b.parse().ok()?);
    let result = a / b;
    (result.is_finite() && result > 0.).then_some(result)
}

fn parse_probe(bytes: &[u8], name: String, size_bytes: u64) -> Result<MediaInfo> {
    let value: Value = serde_json::from_slice(bytes)?;
    let streams = value["streams"]
        .as_array()
        .ok_or_else(|| Error::Invalid("Video streams are missing.".into()))?;
    let video = streams
        .iter()
        .find(|s| s["codec_type"] == "video" && s["disposition"]["attached_pic"] != 1)
        .ok_or_else(|| Error::Invalid("The selected file has no video stream.".into()))?;
    let width = video["width"].as_u64().unwrap_or(0) as u32;
    let height = video["height"].as_u64().unwrap_or(0) as u32;
    let duration_seconds = number(&value["format"]["duration"])
        .or_else(|| number(&video["duration"]))
        .unwrap_or(0.);
    let frame_rate = rate(&video["avg_frame_rate"])
        .or_else(|| rate(&video["r_frame_rate"]))
        .unwrap_or(0.);
    if width == 0 || height == 0 || duration_seconds <= 0. || frame_rate <= 0. {
        return Err(Error::Invalid(
            "Video dimensions, duration, or frame rate are invalid.".into(),
        ));
    }
    let rotation = video["side_data_list"]
        .as_array()
        .and_then(|data| data.iter().find_map(|item| number(&item["rotation"])))
        .or_else(|| number(&video["tags"]["rotate"]))
        .unwrap_or(0.)
        .round() as i32;
    let rotated = rotation.rem_euclid(180) == 90;
    let transfer = video["color_transfer"].as_str().unwrap_or("");
    let order = video["field_order"].as_str().unwrap_or("");
    Ok(MediaInfo {
        name,
        width,
        height,
        display_width: if rotated { height } else { width },
        display_height: if rotated { width } else { height },
        duration_seconds,
        frame_rate,
        codec: video["codec_name"].as_str().unwrap_or("unknown").into(),
        has_audio: streams.iter().any(|s| s["codec_type"] == "audio"),
        hdr: matches!(transfer, "smpte2084" | "arib-std-b67"),
        interlaced: matches!(order, "tt" | "bb" | "tb" | "bt"),
        size_bytes,
        video_stream: video["index"].as_u64().unwrap_or(0) as u32,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rotation_audio_and_fractional_rate_are_preserved() {
        let json = br#"{"format":{"duration":"12.5"},"streams":[{"index":0,"codec_type":"video","codec_name":"h264","width":1920,"height":1080,"avg_frame_rate":"30000/1001","side_data_list":[{"rotation":-90}]},{"codec_type":"audio"}]}"#;
        let info = parse_probe(json, "video.mp4".into(), 123).unwrap();
        assert_eq!((info.display_width, info.display_height), (1080, 1920));
        assert!((info.frame_rate - 29.97002997).abs() < 0.0001);
        assert!(info.has_audio);
    }

    #[test]
    fn attached_artwork_is_not_selected_as_video() {
        let json = br#"{"format":{"duration":"1"},"streams":[{"codec_type":"video","disposition":{"attached_pic":1},"width":100,"height":100},{"index":2,"codec_type":"video","width":720,"height":1280,"avg_frame_rate":"25/1","color_transfer":"smpte2084"}]}"#;
        let info = parse_probe(json, "video.mp4".into(), 123).unwrap();
        assert_eq!(info.video_stream, 2);
        assert!(info.hdr);
    }

    #[test]
    fn corrupt_or_non_finite_metadata_is_rejected() {
        assert!(parse_probe(b"{}", "bad.mp4".into(), 1).is_err());
        let json = br#"{"format":{"duration":"NaN"},"streams":[{"codec_type":"video","width":10,"height":10,"avg_frame_rate":"1/0"}]}"#;
        assert!(parse_probe(json, "bad.mp4".into(), 1).is_err());
    }
}
