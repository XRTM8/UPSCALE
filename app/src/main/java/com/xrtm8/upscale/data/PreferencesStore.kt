package com.xrtm8.upscale.data

import android.content.Context
import com.xrtm8.upscale.model.AppLanguage
import com.xrtm8.upscale.model.AudioQuality
import com.xrtm8.upscale.model.CustomPreset
import com.xrtm8.upscale.model.EnhancementPreset
import com.xrtm8.upscale.model.ExportOptions
import com.xrtm8.upscale.model.FramingMode
import com.xrtm8.upscale.model.OutputResolution
import com.xrtm8.upscale.model.PlatformPreset
import com.xrtm8.upscale.model.PostFormat
import com.xrtm8.upscale.model.QualityMode
import com.xrtm8.upscale.model.TargetFps
import com.xrtm8.upscale.model.VideoCodecChoice
import org.json.JSONArray
import org.json.JSONObject

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
        targetFps = enumValueOrDefault(prefs.getString("targetFps", null), TargetFps.SOURCE),
        preset = enumValueOrDefault(prefs.getString("preset", null), EnhancementPreset.NATURAL),
        deblockStrength = prefs.getFloat("deblockStrength", 0.25f).coerceIn(0f, 1f),
        denoiseStrength = prefs.getFloat("denoiseStrength", 0.45f).coerceIn(0f, 1f),
        detailStrength = prefs.getFloat("detailStrength", 0.65f).coerceIn(0f, 1f),
        clarityStrength = prefs.getFloat("clarityStrength", 0.25f).coerceIn(0f, 1f),
        vibranceStrength = prefs.getFloat("vibranceStrength", 0.10f).coerceIn(0f, 1f),
        exposureStrength = prefs.getFloat("exposureStrength", 0f).coerceIn(-1f, 1f),
        warmthStrength = prefs.getFloat("warmthStrength", 0f).coerceIn(-1f, 1f),
        contrastStrength = prefs.getFloat("contrastStrength", 0f).coerceIn(-1f, 1f),
        shadowsStrength = prefs.getFloat("shadowsStrength", 0f).coerceIn(-1f, 1f),
        muteAudio = prefs.getBoolean("muteAudio", false),
        audioQuality = enumValueOrDefault(prefs.getString("audioQuality", null), AudioQuality.AAC_192),
        keepHdr = prefs.getBoolean("keepHdr", false),
    )

    fun saveOptions(options: ExportOptions) {
        prefs.edit()
            .putString("platform", options.platform.name)
            .putString("postFormat", options.postFormat.name)
            .putString("quality", options.quality.name)
            .putString("framing", options.framing.name)
            .putString("resolution", options.resolution.name)
            .putString("codec", options.codec.name)
            .putString("targetFps", options.targetFps.name)
            .putString("preset", options.preset.name)
            .putFloat("deblockStrength", options.deblockStrength)
            .putFloat("denoiseStrength", options.denoiseStrength)
            .putFloat("detailStrength", options.detailStrength)
            .putFloat("clarityStrength", options.clarityStrength)
            .putFloat("vibranceStrength", options.vibranceStrength)
            .putFloat("exposureStrength", options.exposureStrength)
            .putFloat("warmthStrength", options.warmthStrength)
            .putFloat("contrastStrength", options.contrastStrength)
            .putFloat("shadowsStrength", options.shadowsStrength)
            .putBoolean("muteAudio", options.muteAudio)
            .putString("audioQuality", options.audioQuality.name)
            .putBoolean("keepHdr", options.keepHdr)
            .apply()
    }

    fun loadCustomPresets(): List<CustomPreset> {
        val raw = prefs.getString("customPresetsJson", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                CustomPreset(
                    id = id,
                    name = name,
                    deblockStrength = obj.optDouble("deblock", 0.25).toFloat().coerceIn(0f, 1f),
                    denoiseStrength = obj.optDouble("denoise", 0.45).toFloat().coerceIn(0f, 1f),
                    detailStrength = obj.optDouble("detail", 0.65).toFloat().coerceIn(0f, 1f),
                    clarityStrength = obj.optDouble("clarity", 0.25).toFloat().coerceIn(0f, 1f),
                    vibranceStrength = obj.optDouble("vibrance", 0.10).toFloat().coerceIn(0f, 1f),
                    exposureStrength = obj.optDouble("exposure", 0.0).toFloat().coerceIn(-1f, 1f),
                    warmthStrength = obj.optDouble("warmth", 0.0).toFloat().coerceIn(-1f, 1f),
                    contrastStrength = obj.optDouble("contrast", 0.0).toFloat().coerceIn(-1f, 1f),
                    shadowsStrength = obj.optDouble("shadows", 0.0).toFloat().coerceIn(-1f, 1f),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveCustomPresets(presets: List<CustomPreset>) {
        val arr = JSONArray()
        presets.forEach { preset ->
            arr.put(
                JSONObject().apply {
                    put("id", preset.id)
                    put("name", preset.name)
                    put("deblock", preset.deblockStrength.toDouble())
                    put("denoise", preset.denoiseStrength.toDouble())
                    put("detail", preset.detailStrength.toDouble())
                    put("clarity", preset.clarityStrength.toDouble())
                    put("vibrance", preset.vibranceStrength.toDouble())
                    put("exposure", preset.exposureStrength.toDouble())
                    put("warmth", preset.warmthStrength.toDouble())
                    put("contrast", preset.contrastStrength.toDouble())
                    put("shadows", preset.shadowsStrength.toDouble())
                },
            )
        }
        prefs.edit().putString("customPresetsJson", arr.toString()).apply()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, fallback: T): T {
        if (name == null) return fallback
        return runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
    }
}
