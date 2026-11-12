use sha2::{Digest, Sha256};
use std::{
    fs,
    io::{self, Read, Seek},
    path::{Path, PathBuf},
    time::Duration,
};
use tauri::{AppHandle, Manager};
use upscale_core::Toolchain;

const PACKAGE: &str = "ffmpeg-9.0.2-essentials_build";
const URL: &str = "https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-9.0.2-essentials_build.zip";
const SHA256: &str = "60f467265b1e312373dbcd92200c2618a74850f98d3d078e94296bb3fa2047ba";
const MAX_ARCHIVE: u64 = 160 * 1024 * 1024;

fn directory(app: &AppHandle) -> tauri::Result<PathBuf> {
    Ok(app
        .path()
        .app_local_data_dir()?
        .join("video-engine")
        .join(PACKAGE))
}

fn toolchain(path: &Path) -> Toolchain {
    Toolchain {
        ffmpeg: path.join("ffmpeg.exe"),
        ffprobe: path.join("ffprobe.exe"),
    }
}

pub(crate) fn installed(app: &AppHandle) -> Option<Toolchain> {
    let tools = toolchain(&directory(app).ok()?);
    (tools.ffmpeg.is_file() && tools.ffprobe.is_file()).then_some(tools)
}

pub(crate) fn install(app: &AppHandle) -> Result<Toolchain, String> {
    install_inner(app).map_err(|error| error.to_string())
}

fn install_inner(app: &AppHandle) -> Result<Toolchain, Box<dyn std::error::Error>> {
    if let Some(tools) = installed(app)
        && tools.verify().is_ok()
    {
        return Ok(tools);
    }
    let destination = directory(app)?;
    fs::create_dir_all(&destination)?;
    let mut archive = tempfile::tempfile_in(&destination)?;
    let mut response = reqwest::blocking::Client::builder()
        .https_only(true)
        .timeout(Duration::from_secs(600))
        .build()?
        .get(URL)
        .send()?
        .error_for_status()?
        .take(MAX_ARCHIVE + 1);
    let bytes = io::copy(&mut response, &mut archive)?;
    if bytes > MAX_ARCHIVE {
        return Err("The FFmpeg archive exceeds the expected size limit.".into());
    }
    archive.rewind()?;
    let mut digest = Sha256::new();
    io::copy(&mut archive, &mut digest)?;
    if format!("{:x}", digest.finalize()) != SHA256 {
        return Err("The FFmpeg download did not match its published checksum.".into());
    }
    archive.rewind()?;
    let mut zip = zip::ZipArchive::new(archive)?;
    let staging = tempfile::tempdir_in(&destination)?;
    for name in ["ffmpeg.exe", "ffprobe.exe", "LICENSE", "README.txt"] {
        let entry = if name.ends_with(".exe") {
            format!("{PACKAGE}/bin/{name}")
        } else {
            format!("{PACKAGE}/{name}")
        };
        let mut member = zip.by_name(&entry)?;
        io::copy(
            &mut member,
            &mut fs::File::create(staging.path().join(name))?,
        )?;
    }
    toolchain(staging.path()).verify()?;
    for name in ["ffmpeg.exe", "ffprobe.exe", "LICENSE", "README.txt"] {
        let mut source = fs::File::open(staging.path().join(name))?;
        let mut output = tempfile::NamedTempFile::new_in(&destination)?;
        io::copy(&mut source, &mut output)?;
        output.persist(destination.join(name))?;
    }
    Ok(toolchain(&destination))
}
