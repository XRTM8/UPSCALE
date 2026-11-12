use crate::desktop::AppState;
use std::time::Duration;
use tauri::{AppHandle, Builder, State, Wry};
use tauri_plugin_updater::{Update, UpdaterExt};

#[derive(Default)]
pub(crate) struct PendingUpdate(tauri::async_runtime::Mutex<Option<Update>>);

pub(crate) fn configure(builder: Builder<Wry>) -> Builder<Wry> {
    builder
        .plugin(tauri_plugin_updater::Builder::new().build())
        .manage(PendingUpdate::default())
}

#[tauri::command]
pub(crate) async fn check_for_update(
    app: AppHandle,
    pending: State<'_, PendingUpdate>,
) -> Result<Option<String>, String> {
    let mut candidate = pending.0.lock().await;
    *candidate = None;
    let update = app
        .updater_builder()
        .timeout(Duration::from_secs(120))
        .build()
        .map_err(|error| error.to_string())?
        .check()
        .await
        .map_err(|error| error.to_string())?;
    if let Some(update) = &update
        && (update.download_url.scheme() != "https"
            || update.download_url.host_str() != Some("github.com")
            || !update
                .download_url
                .path()
                .starts_with("/XRTM8/UPSCALE/releases/download/"))
    {
        return Err("The update is outside the official UPSCALE release channel.".into());
    }
    let version = update.as_ref().map(|update| update.version.clone());
    *candidate = update;
    Ok(version)
}

#[tauri::command]
pub(crate) async fn install_update(
    version: String,
    studio: State<'_, AppState>,
    pending: State<'_, PendingUpdate>,
) -> Result<(), String> {
    if studio.is_busy() {
        return Err("Finish the active export before installing an update.".into());
    }
    let candidate = pending.0.lock().await;
    let update = candidate
        .as_ref()
        .filter(|update| update.version == version)
        .ok_or("Check for this update again before installing it.")?;
    let bytes = update
        .download(|_, _| {}, || {})
        .await
        .map_err(|error| error.to_string())?;
    if studio.is_busy() {
        return Err("Finish the active export before installing an update.".into());
    }
    update.install(bytes).map_err(|error| error.to_string())
}
