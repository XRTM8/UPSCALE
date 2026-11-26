# UPSCALE Studio (Android)

تطبيق أندرويد لتحسين وتجهيز الفيديو محلياً باللغتين العربية والإنجليزية، مبني بالكامل بـ **Kotlin + Jetpack Compose + Media3 + OpenGL ES**. لا تُرفع ملفاتك إلى أي خادم.

> أُرشف إصدار سطح المكتب السابق (Rust / Tauri `0.3.0`) في الفرع `legacy-tauri` والوسم `archive/tauri-v0.3`.

## ما يقدمه الإصدار 0.4.0

- **معاينة وتصدير بمحرك OpenGL ES فوري (`UpscaleGlEffect`):**
  - إزالة ضوضاء ذكية تحافظ على الحواف وملامح الوجه (`Denoise`).
  - تعزيز تفاصيل تكيفي يمنع الهالات المضيئة (`Detail CAS`).
  - تحسين وضوح التباين (`Clarity`).
- **مقارنة مباشرة قبل/بعد (Split-Screen):** شريط سحب تفاعلي فوق الفيديو أثناء التشغيل لفحص الفارق قبل التصدير.
- **معالجة HDR تلقائية:** تحويل ألوان `HDR → SDR` عبر OpenGL بدلاً من رفض مقاطع HDR.
- **قوالب منصات وقص تفاعلي:**
  - `Instagram` (`Reel 9:16`، `Feed 4:5`، `Story 9:16`، `Landscape 16:9`)
  - `TikTok` (`Reel 9:16`، `Story 9:16`، `Landscape 16:9`)
  - `YouTube` (`Short 9:16`، `Landscape 16:9`)
  - `Facebook` (`Reel 9:16`، `Feed 4:5`، `Story 9:16`، `Landscape 16:9`)
  - سحب يدوي لموضع القص في وضع `قص لملء الإطار` + طبقة خطوط أمان (`Safe Zones`) + قص مدة المقطع (`Trim`).
- **دعم 1080p و4K وH.264/HEVC:** فحص قدرات مرمّز الجهاز فعلياً عبر `MediaCodecList` ومراقبة الحرارة.
- **استيراد بدون نسخ للكاش + تصدير في الخلفية:** قراءة مباشرة من `content://` وتنفيذ التصدير داخل `ForegroundService` مع إشعار تقدم وزر إلغاء، والتحقق من الناتج قبل الحفظ في `Movies/UPSCALE` والمشاركة.

## البناء والتوقيع

المتطلبات: JDK 17 وAndroid SDK (API 36).

```powershell
./gradlew testDebugUnitTest lintDebug assembleRelease
```

لتوقيع حزمة الإصدار والتحقق من الشهادة (`com.xrtm8.upscale`، `versionCode = 4000`):

```powershell
.\scripts\sign-release.ps1 -UnsignedApk app/build/outputs/apk/release/app-release-unsigned.apk
```

## الترخيص

GPL-3.0 وفق [LICENSE](LICENSE).
