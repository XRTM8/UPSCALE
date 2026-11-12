#[cfg(not(target_os = "android"))]
mod desktop;
#[cfg(not(target_os = "android"))]
use desktop as platform;

#[cfg(target_os = "android")]
mod android;
#[cfg(target_os = "android")]
use android as platform;

#[cfg(windows)]
mod media_setup;
#[cfg(all(windows, feature = "installed-updater"))]
mod updates;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let builder = tauri::Builder::default().plugin(tauri_plugin_dialog::init());
    let builder = platform::configure(builder);
    #[cfg(all(windows, feature = "installed-updater"))]
    let builder = updates::configure(builder);
    builder
        .invoke_handler(tauri::generate_handler![
            platform::system_status,
            platform::import_video,
            platform::start_export,
            platform::job_status,
            platform::cancel_export,
            platform::preview_export,
            platform::show_output,
            #[cfg(windows)]
            desktop::install_media_tools,
            #[cfg(all(windows, feature = "installed-updater"))]
            updates::check_for_update,
            #[cfg(all(windows, feature = "installed-updater"))]
            updates::install_update,
        ])
        .run(tauri::generate_context!())
        .expect("Unable to start UPSCALE Studio");
}
