use serde::{Serialize, de::DeserializeOwned};
use serde_json::json;
use tauri::{AppHandle, Builder, Manager, State, Wry, plugin::PluginHandle};
use tauri_plugin_dialog::DialogExt;
use upscale_types::{
    ExportOptions, Fit, ImportedMedia, JobSnapshot, Platform, PostKind, Quality, Resolution,
    SystemStatus,
};

pub(crate) struct AndroidMedia {
    plugin: PluginHandle<Wry>,
    import: tauri::async_runtime::Mutex<()>,
}

pub(crate) fn configure(builder: Builder<Wry>) -> Builder<Wry> {
    builder.plugin(
        tauri::plugin::Builder::new("android-media")
            .setup(|app, api| {
                let plugin =
                    api.register_android_plugin("com.xrtm8.upscale", "AndroidMediaPlugin")?;
                app.manage(AndroidMedia {
                    plugin,
                    import: Default::default(),
                });
                Ok(())
            })
            .build(),
    )
}

impl AndroidMedia {
    async fn call<T: DeserializeOwned>(
        &self,
        command: &str,
        args: impl Serialize,
    ) -> Result<T, String> {
        self.plugin
            .run_mobile_plugin_async(command, args)
            .await
            .map_err(|error| error.to_string())
    }
}

fn allow_preview(app: &AppHandle, media: ImportedMedia) -> Result<ImportedMedia, String> {
    app.asset_protocol_scope()
        .allow_file(&media.preview_url)
        .map_err(|error| error.to_string())?;
    Ok(media)
}

#[tauri::command]
pub(crate) fn system_status() -> SystemStatus {
    SystemStatus {
        desktop: false,
        tools_available: true,
        engine: "android-media3".into(),
        error: None,
        can_install_tools: false,
        can_update: false,
    }
}

#[tauri::command]
pub(crate) async fn import_video(
    app: AppHandle,
    media: State<'_, AndroidMedia>,
) -> Result<Option<ImportedMedia>, String> {
    let _guard = media
        .import
        .try_lock()
        .map_err(|_| "An import is already in progress.")?;
    let dialog = app.clone();
    let selected = tauri::async_runtime::spawn_blocking(move || {
        dialog
            .dialog()
            .file()
            .add_filter("Video", &["mp4", "mov", "m4v", "mkv", "webm", "avi"])
            .blocking_pick_file()
    })
    .await
    .map_err(|error| error.to_string())?;
    let Some(selected) = selected else {
        return Ok(None);
    };
    let source = media
        .call("importVideo", json!({"uri": selected.to_string()}))
        .await?;
    allow_preview(&app, source).map(Some)
}

#[tauri::command(rename_all = "camelCase")]
pub(crate) async fn start_export(
    media: State<'_, AndroidMedia>,
    source_id: String,
    options: ExportOptions,
) -> Result<Option<JobSnapshot>, String> {
    if options.resolution != Resolution::FullHd {
        return Err("Android exports at 1080p in this release.".into());
    }
    if options.platform == Platform::Youtube && options.post == PostKind::Story {
        return Err("Choose Reel / Short or landscape for YouTube.".into());
    }
    let (width, height) = if options.post == PostKind::Landscape {
        (1920, 1080)
    } else {
        (1080, 1920)
    };
    let bitrate = if options.quality == Quality::Maximum {
        18_000_000
    } else {
        9_000_000
    };
    media
        .call(
            "startExport",
            json!({
                "sourceId": source_id, "width": width, "height": height, "bitrate": bitrate,
                "crop": options.fit == Fit::Cover,
            }),
        )
        .await
        .map(Some)
}

#[tauri::command(rename_all = "camelCase")]
pub(crate) async fn job_status(
    media: State<'_, AndroidMedia>,
    job_id: String,
) -> Result<JobSnapshot, String> {
    media.call("jobStatus", json!({"jobId": job_id})).await
}

#[tauri::command(rename_all = "camelCase")]
pub(crate) async fn cancel_export(
    media: State<'_, AndroidMedia>,
    job_id: String,
) -> Result<(), String> {
    media.call("cancelExport", json!({"jobId": job_id})).await
}

#[tauri::command(rename_all = "camelCase")]
pub(crate) async fn preview_export(
    app: AppHandle,
    media: State<'_, AndroidMedia>,
    job_id: String,
) -> Result<ImportedMedia, String> {
    let result = media
        .call("previewExport", json!({"jobId": job_id}))
        .await?;
    allow_preview(&app, result)
}

#[tauri::command(rename_all = "camelCase")]
pub(crate) async fn show_output(
    media: State<'_, AndroidMedia>,
    job_id: String,
) -> Result<(), String> {
    media.call("shareExport", json!({"jobId": job_id})).await
}
