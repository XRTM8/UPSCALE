package com.xrtm8.upscale.data

import android.content.Context
import com.xrtm8.upscale.model.AppLanguage
import com.xrtm8.upscale.model.ExportOptions
import com.xrtm8.upscale.model.FramingMode
import com.xrtm8.upscale.model.OutputResolution
import com.xrtm8.upscale.model.PlatformPreset
import com.xrtm8.upscale.model.PostFormat
import com.xrtm8.upscale.model.QualityMode
import com.xrtm8.upscale.model.VideoCodecChoice

class PreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("upscale_studio_prefs", Context.MODE_PRIVATE)

    fun loadLanguage(): AppLanguage {
        val code = prefs.getString("language", AppLanguage.AR.code)
        return AppLanguage.entries.firstOrNull { it.code == code } ?: AppLanguage.AR
    }

    fun saveLanguage(language: AppLanguage) {
        prefs.edit().putString("language", language.code).apply()
    }

    fun loadAmoledWhite(): Boolean = prefs.getBoolean("amoledWhite", false)

    fun saveAmoledWhite(isWhite: Boolean) {
        prefs.edit().putBoolean("amoledWhite", isWhite).apply()
    }

    fun lastUpdateCheckMs(): Long = prefs.getLong("lastUpdateCheckMs", 0L)

    fun saveLastUpdateCheckMs(timestampMs: Long) {
        prefs.edit().putLong("lastUpdateCheckMs", timestampMs).apply()
    }

    fun dismissedUpdateVersionCode(): Long = prefs.getLong("dismissedUpdateVersionCode", 0L)

    fun saveDismissedUpdateVersionCode(versionCode: Long) {
        prefs.edit().putLong("dismissedUpdateVersionCode", versionCode).apply()
    }

    fun loadOptions(): ExportOptions = ExportOptions(
        platform = enumValueOrDefault(prefs.getString("platform", null), PlatformPreset.INSTAGRAM),
        postFormat = enumValueOrDefault(prefs.getString("postFormat", null), PostFormat.REEL),
        quality = enumValueOrDefault(prefs.getString("quality", null), QualityMode.MAXIMUM),
        framing = enumValueOrDefault(prefs.getString("framing", null), FramingMode.CONTAIN),
        resolution = enumValueOrDefault(prefs.getString("resolution", null), OutputResolution.FHD_1080P),
        codec = enumValueOrDefault(prefs.getString("codec", null), VideoCodecChoice.H264),
        denoiseStrength = prefs.getFloat("denoiseStrength", 0.45f).coerceIn(0f, 1f),
        detailStrength = prefs.getFloat("detailStrength", 0.65f).coerceIn(0f, 1f),
        clarityStrength = prefs.getFloat("clarityStrength", 0.25f).coerceIn(0f, 1f),
    )

    fun saveOptions(options: ExportOptions) {
        prefs.edit()
            .putString("platform", options.platform.name)
            .putString("postFormat", options.postFormat.name)
            .putString("quality", options.quality.name)
            .putString("framing", options.framing.name)
            .putString("resolution", options.resolution.name)
            .putString("codec", options.codec.name)
            .putFloat("denoiseStrength", options.denoiseStrength)
            .putFloat("detailStrength", options.detailStrength)
            .putFloat("clarityStrength", options.clarityStrength)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, fallback: T): T {
        if (name == null) return fallback
        return runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
    }
}
