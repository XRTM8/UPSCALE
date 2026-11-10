use serde::{Serialize, de::DeserializeOwned};
use upscale_types::ImportedMedia;
use wasm_bindgen::prelude::*;
use wasm_bindgen_futures::JsFuture;

#[wasm_bindgen(inline_js = r#"
let browserUrl;
export function isDesktop() { return Boolean(window.__TAURI__?.core?.invoke); }
export async function invoke(command, args) {
    if (!isDesktop()) throw new Error('Export is available in the desktop application.');
    return await window.__TAURI__.core.invoke(command, args);
}
export function fileUrl(path) { return window.__TAURI__.core.convertFileSrc(path); }
export function preferences() { try { return JSON.parse(localStorage.getItem('upscale-preferences') || 'null'); } catch { return null; } }
export function savePreferences(value) { try { localStorage.setItem('upscale-preferences', JSON.stringify(value)); } catch {} }
export function documentLanguage(language) { document.documentElement.lang = language; document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr'; }
export function focusTool(name) { document.querySelector('[data-open="' + name + '"]')?.focus(); }
export function pickBrowserVideo() {
    return new Promise((resolve, reject) => {
        const input = document.createElement('input'); input.type = 'file'; input.accept = 'video/*'; input.hidden = true;
        document.body.append(input);
        input.addEventListener('cancel', () => { input.remove(); resolve(null); }, { once:true });
        input.addEventListener('change', () => {
            const file = input.files?.[0]; input.remove();
            if (!file) { resolve(null); return; }
            const nextUrl = URL.createObjectURL(file);
            const video = document.createElement('video'); video.preload = 'metadata';
            video.onloadedmetadata = () => {
                if (!Number.isFinite(video.duration) || video.duration <= 0) { URL.revokeObjectURL(nextUrl); reject(new Error('Invalid video duration.')); return; }
                if (browserUrl) URL.revokeObjectURL(browserUrl); browserUrl = nextUrl;
                resolve({ id:'browser-preview', previewUrl:nextUrl, native:false, info:{name:file.name,width:video.videoWidth,height:video.videoHeight,displayWidth:video.videoWidth,displayHeight:video.videoHeight,durationSeconds:video.duration,frameRate:0,codec:'browser',hasAudio:false,hdr:false,interlaced:false,sizeBytes:file.size,videoStream:0} });
            };
            video.onerror = () => { URL.revokeObjectURL(nextUrl); reject(new Error('This browser cannot preview that video codec.')); };
            video.src = nextUrl;
        }, { once:true });
        input.click();
    });
}
"#)]
extern "C" {
    #[wasm_bindgen(js_name = isDesktop)]
    pub fn is_desktop() -> bool;
    #[wasm_bindgen(js_name = invoke)]
    fn invoke_js(command: &str, args: JsValue) -> js_sys::Promise;
    #[wasm_bindgen(js_name = fileUrl)]
    fn file_url(path: &str) -> String;
    #[wasm_bindgen(js_name = preferences)]
    pub fn preferences_js() -> JsValue;
    #[wasm_bindgen(js_name = savePreferences)]
    pub fn save_preferences(value: JsValue);
    #[wasm_bindgen(js_name = documentLanguage)]
    pub fn document_language(language: &str);
    #[wasm_bindgen(js_name = focusTool)]
    pub fn focus_tool(name: &str);
    #[wasm_bindgen(js_name = pickBrowserVideo)]
    fn pick_browser_video() -> js_sys::Promise;
}

fn error_text(value: JsValue) -> String {
    value
        .as_string()
        .or_else(|| {
            js_sys::Reflect::get(&value, &JsValue::from_str("message"))
                .ok()?
                .as_string()
        })
        .unwrap_or_else(|| "Operation failed.".into())
}

pub async fn call<T: DeserializeOwned>(command: &str, args: &impl Serialize) -> Result<T, String> {
    let value = args
        .serialize(&serde_wasm_bindgen::Serializer::json_compatible())
        .map_err(|e| e.to_string())?;
    let result = JsFuture::from(invoke_js(command, value))
        .await
        .map_err(error_text)?;
    serde_wasm_bindgen::from_value(result).map_err(|e| e.to_string())
}

pub fn resolve_media(mut value: ImportedMedia) -> ImportedMedia {
    if value.native {
        value.preview_url = file_url(&value.preview_url);
    }
    value
}

pub async fn import() -> Result<Option<ImportedMedia>, String> {
    if is_desktop() {
        let imported: Option<ImportedMedia> = call("import_video", &serde_json::json!({})).await?;
        Ok(imported.map(resolve_media))
    } else {
        let result = JsFuture::from(pick_browser_video())
            .await
            .map_err(error_text)?;
        serde_wasm_bindgen::from_value(result).map_err(|e| e.to_string())
    }
}
