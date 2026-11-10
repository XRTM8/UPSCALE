mod bridge;

use leptos::prelude::*;
use serde::{Deserialize, Serialize};
use upscale_types::*;
use wasm_bindgen::JsCast;
use wasm_bindgen_futures::spawn_local;

#[derive(Debug, Default, Clone, Copy, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
enum Language {
    #[default]
    Ar,
    En,
}

impl Language {
    fn code(self) -> &'static str {
        if self == Self::Ar { "ar" } else { "en" }
    }
    fn dir(self) -> &'static str {
        if self == Self::Ar { "rtl" } else { "ltr" }
    }
}

#[derive(Default, Serialize, Deserialize)]
#[serde(default)]
struct Preferences {
    language: Language,
    options: ExportOptions,
}

fn tr(language: Language, key: &'static str) -> &'static str {
    let pair = match key {
        "project" => ["استوديو الفيديو", "Video studio"],
        "sample" => ["مشهد تجريبي", "Illustrative sample"],
        "sampleName" => ["استورد المقطع الأصلي للبدء", "Import your original footage"],
        "processing" => ["المعالجة", "Processing"],
        "output" => ["الإخراج", "Output"],
        "quality" => ["وضع الجودة", "Quality mode"],
        "maximum" => ["الجودة القصوى", "Maximum"],
        "balanced" => ["متوازن", "Balanced"],
        "denoise" => ["تنظيف التشويش", "Noise cleanup"],
        "detail" => ["تعزيز التفاصيل", "Detail enhancement"],
        "smooth" => ["سلاسة 60 إطاراً", "60 fps interpolation"],
        "platform" => ["المنصة", "Platform"],
        "postType" => ["نوع المنشور", "Post type"],
        "reel" => ["ريل / فيديو قصير", "Reel / Short"],
        "story" => ["ستوري", "Story"],
        "landscape" => ["أفقي", "Landscape"],
        "resolution" => ["دقة الإخراج", "Output resolution"],
        "fit" => ["ملاءمة الصورة", "Framing"],
        "contain" => ["كامل المقطع", "Fit entire frame"],
        "cover" => ["قص لملء الإطار", "Crop to fill"],
        "format" => ["الصيغة", "Format"],
        "done" => ["تم", "Done"],
        "close" => ["إغلاق", "Close"],
        "import" => ["استيراد فيديو", "Import video"],
        "replace" => ["تغيير الفيديو", "Change video"],
        "private" => [
            "المعالجة محلية على جهازك",
            "Processed locally on your device",
        ],
        "export" => ["تحسين وتصدير", "Enhance & export"],
        "choose" => ["اختيار فيديو", "Choose video"],
        "working" => ["جارٍ التصدير", "Exporting"],
        "cancel" => ["إلغاء", "Cancel"],
        "completed" => ["اكتمل التصدير", "Export complete"],
        "cancelled" => ["أُلغي التصدير", "Export cancelled"],
        "failed" => ["فشل التصدير", "Export failed"],
        "folder" => ["فتح المجلد", "Open folder"],
        "details" => ["التفاصيل", "Details"],
        "error" => ["تعذّر إكمال العملية", "The operation could not be completed"],
        "original" => ["الأصل", "Original"],
        "result" => ["النتيجة", "Result"],
        "browser" => [
            "معاينة المتصفح؛ التصدير متاح في تطبيق الحاسب",
            "Browser preview; export is available in the desktop app",
        ],
        "tools" => [
            "يلزم FFmpeg وffprobe للاستيراد والتصدير. راجع تعليمات التشغيل.",
            "FFmpeg and ffprobe are required. See the setup instructions.",
        ],
        "classic" => [
            "المحرك الحالي: تحسين تقليدي؛ نماذج AI لم تُفعّل بعد.",
            "Current engine: traditional enhancement; AI models are not enabled yet.",
        ],
        "smoothNote" => [
            "توليد الإطارات قد يزيد وقت التصدير ويُظهر تشوهات في الحركة.",
            "Frame interpolation can increase processing time and introduce motion artifacts.",
        ],
        "hdr" => [
            "ملف HDR: تصديره غير متاح حتى إضافة تحويل ألوان مُختبَر.",
            "HDR export is not enabled until a verified color pipeline is available.",
        ],
        "previewError" => [
            "تعذّرت معاينة هذا الترميز. قد يظل التصدير متاحاً.",
            "This codec could not be previewed. Export may still be available.",
        ],
        "opening" => ["جارٍ فتح الملف…", "Opening video…"],
        _ => [key, key],
    };
    pair[usize::from(language == Language::En)]
}

#[derive(Clone, Copy, PartialEq, Default)]
enum Panel {
    #[default]
    None,
    Processing,
    Output,
}

#[derive(Clone, Copy)]
struct Studio {
    language: RwSignal<Language>,
    options: RwSignal<ExportOptions>,
    source: RwSignal<Option<ImportedMedia>>,
    result: RwSignal<Option<ImportedMedia>>,
    show_result: RwSignal<bool>,
    panel: RwSignal<Panel>,
    loading: RwSignal<bool>,
    system: RwSignal<Option<SystemStatus>>,
    job: RwSignal<Option<JobSnapshot>>,
    error: RwSignal<Option<String>>,
}

impl Studio {
    fn text(self, key: &'static str) -> &'static str {
        tr(self.language.get(), key)
    }
    fn running(self) -> bool {
        self.job
            .with(|job| job.as_ref().is_some_and(|j| !j.status.is_terminal()))
    }
    fn locked(self) -> bool {
        self.loading.get() || self.running()
    }
    fn preview(self) -> Option<ImportedMedia> {
        if self.show_result.get() {
            self.result.get().or_else(|| self.source.get())
        } else {
            self.source.get()
        }
    }
    fn dimensions(self) -> (u32, u32) {
        let options = self.options.get();
        let (short, long) = if options.resolution == Resolution::Uhd {
            (2160, 3840)
        } else {
            (1080, 1920)
        };
        if options.post == PostKind::Landscape {
            (long, short)
        } else {
            (short, long)
        }
    }
    fn close(self) {
        let panel = self.panel.get_untracked();
        self.panel.set(Panel::None);
        bridge::focus_tool(if panel == Panel::Output {
            "output"
        } else {
            "processing"
        });
    }
    fn import(self) {
        if self.locked() {
            return;
        }
        self.loading.set(true);
        self.error.set(None);
        spawn_local(async move {
            match bridge::import().await {
                Ok(Some(imported)) => {
                    self.source.set(Some(imported));
                    self.result.set(None);
                    self.show_result.set(false);
                    self.job.set(None);
                    self.panel.set(Panel::None);
                }
                Ok(None) => {}
                Err(error) => self.error.set(Some(error)),
            }
            self.loading.set(false);
        });
    }
    fn export(self) {
        if self.locked() {
            return;
        }
        let Some(source) = self.source.get_untracked() else {
            return;
        };
        if !source.native {
            self.error.set(Some(self.text("browser").into()));
            return;
        }
        let options = self.options.get_untracked();
        self.loading.set(true);
        self.error.set(None);
        self.panel.set(Panel::None);
        spawn_local(async move {
            let started: Result<Option<JobSnapshot>, _> = bridge::call(
                "start_export",
                &serde_json::json!({"sourceId":source.id,"options":options}),
            )
            .await;
            self.loading.set(false);
            let mut current = match started {
                Ok(Some(job)) => job,
                Ok(None) => return,
                Err(error) => {
                    self.error.set(Some(error));
                    return;
                }
            };
            self.result.set(None);
            self.show_result.set(false);
            loop {
                self.job.set(Some(current.clone()));
                if current.status.is_terminal() {
                    break;
                }
                gloo_timers::future::TimeoutFuture::new(400).await;
                match bridge::call::<JobSnapshot>(
                    "job_status",
                    &serde_json::json!({"jobId":current.id}),
                )
                .await
                {
                    Ok(snapshot) => current = snapshot,
                    Err(error) => {
                        self.error.set(Some(error));
                        break;
                    }
                }
            }
            if current.status == JobStatus::Completed {
                match bridge::call::<ImportedMedia>(
                    "preview_export",
                    &serde_json::json!({"jobId":current.id}),
                )
                .await
                {
                    Ok(media) => {
                        self.result.set(Some(bridge::resolve_media(media)));
                        self.show_result.set(true);
                    }
                    Err(error) => self.error.set(Some(error)),
                }
            }
        });
    }
    fn cancel(self) {
        let Some(job) = self.job.get_untracked() else {
            return;
        };
        spawn_local(async move {
            if let Err(error) =
                bridge::call::<()>("cancel_export", &serde_json::json!({"jobId":job.id})).await
            {
                self.error.set(Some(error));
            }
        });
    }
    fn show_output(self) {
        let Some(job) = self.job.get_untracked() else {
            return;
        };
        spawn_local(async move {
            if let Err(error) =
                bridge::call::<()>("show_output", &serde_json::json!({"jobId":job.id})).await
            {
                self.error.set(Some(error));
            }
        });
    }
}

fn value(event: &web_sys::Event) -> String {
    event
        .target()
        .and_then(|target| target.dyn_into::<web_sys::HtmlSelectElement>().ok())
        .map(|input| input.value())
        .unwrap_or_default()
}

#[component]
fn Icon(name: &'static str) -> impl IntoView {
    view! { <span class="tc-icon" style=format!("--icon: url('/assets/icons/{name}.svg')") aria-hidden="true"></span> }
}

#[component]
fn Toggle(studio: Studio, field: &'static str) -> impl IntoView {
    view! {
        <label class="tc-toggle">
            <span>{move || studio.text(field)}</span>
            <input type="checkbox" disabled=move || studio.locked()
                prop:checked=move || { let o=studio.options.get(); match field {"denoise"=>o.denoise,"detail"=>o.detail,_=>o.smooth} }
                on:change=move |event| { let checked=event.target().and_then(|t|t.dyn_into::<web_sys::HtmlInputElement>().ok()).is_some_and(|i|i.checked()); studio.options.update(|o|match field {"denoise"=>o.denoise=checked,"detail"=>o.detail=checked,_=>o.smooth=checked}); } />
        </label>
    }
}

#[component]
fn PanelHeader(studio: Studio, title: &'static str) -> impl IntoView {
    view! { <div class="tc-panel-head"><h2>{move || studio.text(title)}</h2><button type="button" class="tc-close" aria-label=move || studio.text("close") on:click=move |_| studio.close()><Icon name="x"/></button></div> }
}

#[component]
fn App() -> impl IntoView {
    let preferences: Preferences =
        serde_wasm_bindgen::from_value(bridge::preferences_js()).unwrap_or_default();
    let s = Studio {
        language: RwSignal::new(preferences.language),
        options: RwSignal::new(preferences.options),
        source: RwSignal::new(None),
        result: RwSignal::new(None),
        show_result: RwSignal::new(false),
        panel: RwSignal::new(Panel::None),
        loading: RwSignal::new(false),
        system: RwSignal::new(None),
        job: RwSignal::new(None),
        error: RwSignal::new(None),
    };
    Effect::new(move |_| {
        let language = s.language.get();
        bridge::document_language(language.code());
        let preferences = Preferences {
            language,
            options: s.options.get(),
        };
        if let Ok(value) = serde_wasm_bindgen::to_value(&preferences) {
            bridge::save_preferences(value);
        }
    });
    spawn_local(async move {
        let result = if bridge::is_desktop() {
            bridge::call("system_status", &serde_json::json!({})).await
        } else {
            Ok(SystemStatus {
                desktop: false,
                tools_available: false,
                engine: "browser-preview".into(),
                error: None,
            })
        };
        match result {
            Ok(status) => s.system.set(Some(status)),
            Err(error) => s.error.set(Some(error)),
        }
    });
    view! {
      <div id="studio">
       <div class="tc-app" class:with-job=move || s.job.get().is_some() dir=move || s.language.get().dir() lang=move || s.language.get().code()
         on:keydown=move |event: web_sys::KeyboardEvent| { if event.key()=="Escape" { s.close(); } }>
        <header class="tc-head">
          <div class="tc-identity"><span class="tc-mark" aria-hidden="true">"U"</span><span class="tc-brand">"UPSCALE"</span></div>
          <div class="tc-session"><span class="tc-project">{move || s.text("project")}</span></div>
          <div class="tc-head-actions"><button type="button" class="tc-language" aria-label=move || if s.language.get()==Language::Ar {"Switch to English"} else {"التبديل إلى العربية"}
            on:click=move |_| s.language.update(|language|*language=if *language==Language::Ar{Language::En}else{Language::Ar})>{move || if s.language.get()==Language::Ar {"EN"} else {"عربي"}}</button></div>
        </header>
        <main class="tc-work">
          <div class="tc-work-top"><div class="tc-source" aria-live="polite"><Icon name="film"/><span dir="auto" title=move || s.preview().map(|m|m.info.name).unwrap_or_default()>{move || s.preview().map(|m|m.info.name).unwrap_or_else(||s.text("sampleName").into())}</span></div>
            <span class="tc-format" dir="ltr">{move || { let (w,h)=s.dimensions(); if w>h {"16:9"}else{"9:16"} }}</span>
          </div>
          <div class="tc-preview-area">
            <div class="tc-screen" class:landscape=move || s.options.get().post==PostKind::Landscape>
              <div class="tc-art" hidden=move || s.source.get().is_some() role="img" aria-label=move || s.text("sample")>
                <div class="tc-art-wall"></div><div class="tc-art-arch"></div><div class="tc-art-inner"></div><div class="tc-art-light"></div><div class="tc-art-step"></div><div class="tc-art-vase"></div><div class="tc-art-grain"></div>
              </div>
              <div class="tc-demo" hidden=move || s.source.get().is_some()>{move || s.text("sample")}</div>
              <video class="tc-video" controls playsinline preload="metadata" hidden=move || s.source.get().is_none()
                src=move || s.preview().map(|m|m.preview_url).unwrap_or_default()
                style:object-fit=move || if s.options.get().fit==Fit::Cover {"cover"}else{"contain"}
                aria-label=move || if s.show_result.get(){s.text("result")}else{s.text("original")}
                on:error=move |_| { if s.source.get_untracked().is_some(){s.error.set(Some(s.text("previewError").into()));} }></video>
            </div>
            <div class="tc-rail">
              <button type="button" class="tc-tool" data-open="processing" aria-controls="processing-panel" aria-expanded=move || (s.panel.get()==Panel::Processing).to_string()
                on:click=move |_| s.panel.update(|p|*p=if *p==Panel::Processing {Panel::None}else{Panel::Processing})><Icon name="sliders-horizontal"/><span>{move || s.text("processing")}</span></button>
              <button type="button" class="tc-tool" data-open="output" aria-controls="output-panel" aria-expanded=move || (s.panel.get()==Panel::Output).to_string()
                on:click=move |_| s.panel.update(|p|*p=if *p==Panel::Output {Panel::None}else{Panel::Output})><Icon name="arrow-up-right"/><span>{move || s.text("output")}</span></button>
            </div>
            <section class="tc-panel tc-glass" id="processing-panel" hidden=move || s.panel.get()!=Panel::Processing aria-label=move || s.text("processing")>
              <PanelHeader studio=s title="processing"/>
              <label class="tc-field"><span class="tc-label">{move || s.text("quality")}</span><select disabled=move || s.locked() prop:value=move || if s.options.get().quality==Quality::Maximum {"maximum"}else{"balanced"}
                on:change=move |event|s.options.update(|o|o.quality=if value(&event)=="balanced"{Quality::Balanced}else{Quality::Maximum})>
                <option value="maximum">{move || s.text("maximum")}</option><option value="balanced">{move || s.text("balanced")}</option></select></label>
              <div class="tc-toggles"><Toggle studio=s field="denoise"/><Toggle studio=s field="detail"/><Toggle studio=s field="smooth"/></div>
              <p class="tc-note" hidden=move || !s.options.get().smooth>{move || s.text("smoothNote")}</p>
              <p class="tc-note">{move || s.text("classic")}</p>
              <button type="button" class="tc-done" on:click=move |_|s.close()>{move ||s.text("done")}</button>
            </section>
            <section class="tc-panel tc-glass" id="output-panel" hidden=move || s.panel.get()!=Panel::Output aria-label=move || s.text("output")>
              <PanelHeader studio=s title="output"/>
              <label class="tc-field"><span class="tc-label">{move || s.text("platform")}</span><select disabled=move || s.locked() prop:value=move || match s.options.get().platform {Platform::Instagram=>"instagram",Platform::Facebook=>"facebook",Platform::Tiktok=>"tiktok",Platform::Youtube=>"youtube"}
                on:change=move |event|s.options.update(|o|{o.platform=match value(&event).as_str(){"facebook"=>Platform::Facebook,"tiktok"=>Platform::Tiktok,"youtube"=>Platform::Youtube,_=>Platform::Instagram}; if matches!(o.platform,Platform::Instagram|Platform::Facebook){o.resolution=Resolution::FullHd;}else if o.platform==Platform::Youtube&&o.post==PostKind::Story{o.post=PostKind::Reel;}})>
                <option value="instagram">"Instagram"</option><option value="facebook">"Facebook"</option><option value="tiktok">"TikTok"</option><option value="youtube">"YouTube"</option></select></label>
              <label class="tc-field"><span class="tc-label">{move ||s.text("postType")}</span><select disabled=move || s.locked() prop:value=move || match s.options.get().post {PostKind::Reel=>"reel",PostKind::Story=>"story",PostKind::Landscape=>"landscape"}
                on:change=move |event|s.options.update(|o|o.post=match value(&event).as_str(){"story"=>PostKind::Story,"landscape"=>PostKind::Landscape,_=>PostKind::Reel})>
                <option value="reel">{move ||s.text("reel")}</option><option value="story" disabled=move || s.options.get().platform==Platform::Youtube>{move ||s.text("story")}</option><option value="landscape">{move ||s.text("landscape")}</option></select></label>
              <label class="tc-field"><span class="tc-label">{move ||s.text("resolution")}</span><select disabled=move || s.locked() prop:value=move || if s.options.get().resolution==Resolution::Uhd {"uhd"}else{"full_hd"}
                on:change=move |event|s.options.update(|o|o.resolution=if value(&event)=="uhd"{Resolution::Uhd}else{Resolution::FullHd})>
                <option value="full_hd">"1080p"</option><option value="uhd" disabled=move || matches!(s.options.get().platform,Platform::Instagram|Platform::Facebook)>"4K"</option></select></label>
              <label class="tc-field"><span class="tc-label">{move ||s.text("fit")}</span><select disabled=move || s.locked() prop:value=move || if s.options.get().fit==Fit::Cover {"cover"}else{"contain"}
                on:change=move |event|s.options.update(|o|o.fit=if value(&event)=="cover"{Fit::Cover}else{Fit::Contain})>
                <option value="contain">{move ||s.text("contain")}</option><option value="cover">{move ||s.text("cover")}</option></select></label>
              <button type="button" class="tc-done" on:click=move |_|s.close()>{move ||s.text("done")}</button>
            </section>
          </div>
          <div class="tc-work-bottom"><Icon name="hard-drive"/><span>{move ||s.text("private")}</span>
            <span class="media-metadata" dir="ltr">{move ||s.preview().map(|m|format!("{} × {} · {:.1}s{}",m.info.display_width,m.info.display_height,m.info.duration_seconds,if m.native {format!(" · {:.2} fps",m.info.frame_rate)}else{String::new()})).unwrap_or_default()}</span>
          </div>
          <div class="compare" hidden=move || s.result.get().is_none() role="group" aria-label="Preview">
            <button type="button" aria-pressed=move || (!s.show_result.get()).to_string() on:click=move |_|s.show_result.set(false)>{move ||s.text("original")}</button>
            <button type="button" aria-pressed=move || s.show_result.get().to_string() on:click=move |_|s.show_result.set(true)>{move ||s.text("result")}</button>
          </div>
        </main>
        <Show when=move || s.job.get().is_some()>
          <div class="job-strip" aria-live="polite">
            <div class="job-label"><span>{move || s.job.get().map(|job|s.text(match job.status {JobStatus::Queued|JobStatus::Running=>"working",JobStatus::Completed=>"completed",JobStatus::Cancelled=>"cancelled",JobStatus::Failed=>"failed"})).unwrap_or("")}</span>
              <b dir="ltr">{move ||s.job.get().map(|j|format!("{:.0}%",j.progress*100.)).unwrap_or_default()}</b></div>
            <div class="job-progress" role="progressbar" aria-label=move ||s.text("export") aria-valuemin="0" aria-valuemax="100" aria-valuenow=move ||s.job.get().map(|j|j.progress*100.).unwrap_or(0.)><span style:width=move ||s.job.get().map(|j|format!("{}%",j.progress*100.)).unwrap_or("0%".into())></span></div>
            <button type="button" hidden=move || !s.running() on:click=move |_|s.cancel()>{move ||s.text("cancel")}</button>
            <button type="button" hidden=move || !s.job.with(|j|j.as_ref().is_some_and(|j|j.status==JobStatus::Completed)) on:click=move |_|s.show_output()><Icon name="folder-open"/>{move ||s.text("folder")}</button>
            <details hidden=move || !s.job.with(|j|j.as_ref().is_some_and(|j|j.error.is_some()))><summary>{move ||s.text("details")}</summary><pre>{move ||s.job.get().and_then(|j|j.error).unwrap_or_default()}</pre></details>
          </div>
        </Show>
        <Show when=move || s.error.get().is_some()><div class="notice" role="alert"><span>{move ||s.text("error")}</span><details><summary>{move ||s.text("details")}</summary><pre>{move ||s.error.get().unwrap_or_default()}</pre></details><button type="button" class="tc-close" aria-label=move ||s.text("close") on:click=move |_|s.error.set(None)><Icon name="x"/></button></div></Show>
        <div class="system-note" hidden=move || s.system.with(|v|v.as_ref().is_none_or(|v|v.desktop&&v.tools_available))>{move ||s.system.get().map(|v|s.text(if v.desktop{"tools"}else{"browser"})).unwrap_or("")}</div>
        <div class="system-note" hidden=move || !s.source.with(|m|m.as_ref().is_some_and(|m|m.info.hdr))>{move ||s.text("hdr")}</div>
        <footer class="tc-dock">
          <div class="tc-preset"><span class="tc-preset-label">{move ||s.text("output")}</span><div class="tc-preset-value"><span>{move ||match s.options.get().platform {Platform::Instagram=>"Instagram",Platform::Facebook=>"Facebook",Platform::Tiktok=>"TikTok",Platform::Youtube=>"YouTube"}}</span><span class="tc-separator"></span><span>{move ||s.text(match s.options.get().post{PostKind::Reel=>"reel",PostKind::Story=>"story",PostKind::Landscape=>"landscape"})}</span><span class="tc-separator"></span><span dir="ltr">{move ||{let(w,h)=s.dimensions();format!("{w} × {h}")}}</span></div></div>
          <div class="footer-actions"><button type="button" class="source-button" aria-label=move ||s.text("replace") hidden=move ||s.source.get().is_none() disabled=move ||s.locked() on:click=move |_|s.import()><Icon name="plus"/><span>{move ||s.text("replace")}</span></button>
          <button type="button" class="tc-import" disabled=move ||s.locked() ||s.source.with(|m|m.as_ref().is_some_and(|m|!m.native||m.info.hdr))
            on:click=move |_|{if s.source.get_untracked().is_some(){s.export()}else{s.import()}}><Icon name="arrow-up-right"/><span>{move ||s.text(if s.loading.get(){"opening"}else if s.source.get().is_some(){"export"}else{"import"})}</span></button></div>
        </footer>
       </div>
      </div>
    }
}

fn main() {
    console_error_panic_hook::set_once();
    leptos::mount::mount_to_body(App);
}
