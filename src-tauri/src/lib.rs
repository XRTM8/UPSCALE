use std::{
    path::{Path, PathBuf},
    sync::Mutex,
};
use tauri::{AppHandle, Manager, State};
use tauri_plugin_dialog::DialogExt;
use upscale_core::{JobManager, Toolchain, probe};
use upscale_types::{ExportOptions, ImportedMedia, JobSnapshot, MediaInfo, SystemStatus};
use uuid::Uuid;

#[derive(Clone)]
struct Source {
    id: String,
    path: PathBuf,
    info: MediaInfo,
}

struct AppState {
    tools: Option<Toolchain>,
    jobs: Option<JobManager>,
    source: Mutex<Option<Source>>,
    tool_error: Option<String>,
}

impl AppState {
    fn new() -> Self {
        match Toolchain::discover() {
            Ok(tools) => Self {
                jobs: Some(JobManager::new(tools.clone())),
                tools: Some(tools),
                source: Mutex::new(None),
                tool_error: None,
            },
            Err(error) => Self {
                tools: None,
                jobs: None,
                source: Mutex::new(None),
                tool_error: Some(error.to_string()),
            },
        }
    }
    fn tools(&self) -> Result<Toolchain, String> {
        self.tools.clone().ok_or_else(|| {
            self.tool_error
                .clone()
                .unwrap_or("FFmpeg is unavailable.".into())
        })
    }
    fn jobs(&self) -> Result<JobManager, String> {
        self.jobs.clone().ok_or_else(|| {
            self.tool_error
                .clone()
                .unwrap_or("FFmpeg is unavailable.".into())
        })
    }
}

fn local_url_path(path: &Path) -> String {
    let value = path.to_string_lossy();
    value.strip_prefix(r"\\?\").unwrap_or(&value).to_string()
}

#[tauri::command]
async fn system_status(state: State<'_, AppState>) -> Result<SystemStatus, String> {
    let tools = state.tools();
    tauri::async_runtime::spawn_blocking(move || {
        let result = tools.and_then(|tools| tools.verify().map_err(|e| e.to_string()));
        SystemStatus {
            desktop: true,
            tools_available: result.is_ok(),
            engine: "ffmpeg-classic".into(),
            error: result.err(),
        }
    })
    .await
    .map_err(|e| e.to_string())
}

#[tauri::command]
async fn import_video(
    app: AppHandle,
    state: State<'_, AppState>,
) -> Result<Option<ImportedMedia>, String> {
    let tools = state.tools()?;
    if state.jobs()?.is_busy() {
        return Err("Finish or cancel the current export before importing another video.".into());
    }
    let dialog_app = app.clone();
    let selected = tauri::async_runtime::spawn_blocking(move || {
        dialog_app
            .dialog()
            .file()
            .add_filter("Video", &["mp4", "mov", "m4v", "mkv", "webm", "avi"])
            .blocking_pick_file()
    })
    .await
    .map_err(|e| e.to_string())?;
    let Some(selected) = selected else {
        return Ok(None);
    };
    let path = selected
        .into_path()
        .map_err(|_| "Only local files are supported in this desktop version.")?
        .canonicalize()
        .map_err(|e| e.to_string())?;
    let probe_path = path.clone();
    let info = tauri::async_runtime::spawn_blocking(move || probe(&tools, &probe_path))
        .await
        .map_err(|e| e.to_string())?
        .map_err(|e| e.to_string())?;
    app.asset_protocol_scope()
        .allow_file(&path)
        .map_err(|e| e.to_string())?;
    let id = Uuid::new_v4().to_string();
    let imported = ImportedMedia {
        id: id.clone(),
        preview_url: local_url_path(&path),
        info: info.clone(),
        native: true,
    };
    *state
        .source
        .lock()
        .map_err(|_| "Source state is unavailable.")? = Some(Source { id, path, info });
    Ok(Some(imported))
}

#[tauri::command(rename_all = "camelCase")]
async fn start_export(
    app: AppHandle,
    state: State<'_, AppState>,
    source_id: String,
    options: ExportOptions,
) -> Result<Option<JobSnapshot>, String> {
    let jobs = state.jobs()?;
    if jobs.is_busy() {
        return Err("An export is already running.".into());
    }
    let source = state
        .source
        .lock()
        .map_err(|_| "Source state is unavailable.")?
        .as_ref()
        .filter(|source| source.id == source_id)
        .cloned()
        .ok_or("Import a video before exporting.")?;
    // Validate settings before showing a save dialog or allocating a worker.
    upscale_core::ExportPlan::new(&source.info, options.clone()).map_err(|e| e.to_string())?;
    let name = format!(
        "{}_upscaled.mp4",
        source
            .path
            .file_stem()
            .unwrap_or_default()
            .to_string_lossy()
    );
    let selected = tauri::async_runtime::spawn_blocking(move || {
        app.dialog()
            .file()
            .set_file_name(name)
            .add_filter("MP4", &["mp4"])
            .blocking_save_file()
    })
    .await
    .map_err(|e| e.to_string())?;
    let Some(selected) = selected else {
        return Ok(None);
    };
    let output = selected
        .into_path()
        .map_err(|_| "Choose a local output file.")?;
    jobs.start(source.path, source.info, options, output)
        .map(Some)
        .map_err(|e| e.to_string())
}

#[tauri::command(rename_all = "camelCase")]
fn job_status(state: State<'_, AppState>, job_id: String) -> Result<JobSnapshot, String> {
    state.jobs()?.snapshot(&job_id).map_err(|e| e.to_string())
}

#[tauri::command(rename_all = "camelCase")]
fn cancel_export(state: State<'_, AppState>, job_id: String) -> Result<(), String> {
    state.jobs()?.cancel(&job_id).map_err(|e| e.to_string())
}

#[tauri::command(rename_all = "camelCase")]
async fn preview_export(
    app: AppHandle,
    state: State<'_, AppState>,
    job_id: String,
) -> Result<ImportedMedia, String> {
    let path = state.jobs()?.output(&job_id).map_err(|e| e.to_string())?;
    let tools = state.tools()?;
    let probe_path = path.clone();
    let info = tauri::async_runtime::spawn_blocking(move || probe(&tools, &probe_path))
        .await
        .map_err(|e| e.to_string())?
        .map_err(|e| e.to_string())?;
    app.asset_protocol_scope()
        .allow_file(&path)
        .map_err(|e| e.to_string())?;
    Ok(ImportedMedia {
        id: format!("output:{job_id}"),
        preview_url: local_url_path(&path),
        info,
        native: true,
    })
}

#[tauri::command(rename_all = "camelCase")]
fn show_output(state: State<'_, AppState>, job_id: String) -> Result<(), String> {
    let path = state.jobs()?.output(&job_id).map_err(|e| e.to_string())?;
    let parent = path.parent().ok_or("Output directory not found.")?;
    #[cfg(target_os = "windows")]
    let result = std::process::Command::new("explorer.exe")
        .arg(local_url_path(parent))
        .spawn();
    #[cfg(target_os = "macos")]
    let result = std::process::Command::new("open").arg(parent).spawn();
    #[cfg(not(any(target_os = "windows", target_os = "macos")))]
    let result = std::process::Command::new("xdg-open").arg(parent).spawn();
    result.map(|_| ()).map_err(|e| e.to_string())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .manage(AppState::new())
        .invoke_handler(tauri::generate_handler![
            system_status,
            import_video,
            start_export,
            job_status,
            cancel_export,
            preview_export,
            show_output
        ])
        .on_window_event(|window, event| {
            if matches!(event, tauri::WindowEvent::CloseRequested { .. })
                && let Some(jobs) = &window.state::<AppState>().jobs
            {
                jobs.cancel_all();
            }
        })
        .run(tauri::generate_context!())
        .expect("Unable to start UPSCALE Studio");
}
