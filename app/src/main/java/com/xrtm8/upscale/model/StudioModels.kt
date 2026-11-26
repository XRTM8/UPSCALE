package com.xrtm8.upscale.model

import kotlin.math.roundToInt

enum class AppLanguage(val code: String, val isRtl: Boolean) {
    AR("ar", true),
    EN("en", false),
}

enum class PlatformPreset(val label: String) {
    INSTAGRAM("Instagram"),
    TIKTOK("TikTok"),
    YOUTUBE("YouTube"),
    FACEBOOK("Facebook"),
}

enum class PostFormat(val aspectLabel: String, val ratioWidth: Int, val ratioHeight: Int) {
    REEL("9:16", 9, 16),
    FEED("4:5", 4, 5),
    STORY("9:16", 9, 16),
    LANDSCAPE("16:9", 16, 9),
}

enum class QualityMode {
    MAXIMUM,
    BALANCED,
}

enum class FramingMode {
    CONTAIN,
    COVER,
}

enum class OutputResolution(val shortEdge: Int, val label: String) {
    FHD_1080P(1080, "1080p"),
    UHD_4K(2160, "4K"),
}

enum class VideoCodecChoice(val label: String) {
    H264("H.264"),
    HEVC("HEVC"),
}

enum class TargetFps(val fps: Int?) {
    SOURCE(null),
    FPS_30(30),
    FPS_60(60),
}

enum class EnhancementPreset {
    NATURAL,
    CRISP_TEXT,
    LOW_LIGHT,
    CINEMA,
    CUSTOM,
}

sealed interface AppError {
    data object InvalidSourceVideo : AppError
    data class InsufficientStorage(val requiredMb: Long, val availableMb: Long) : AppError
    data class UnableToReadVideo(val detail: String? = null) : AppError
    data class UnableToPrepareExport(val detail: String? = null) : AppError
    data object NoVideoPlayerFound : AppError
    data object ExportMissingAudio : AppError
    data object ExportDurationMismatch : AppError
    data class ExportDimensionMismatch(
        val actualWidth: Int,
        val actualHeight: Int,
        val expectedWidth: Int,
        val expectedHeight: Int,
    ) : AppError
    data class GenericFailure(val detail: String) : AppError
}

class LocalizedExportException(val appError: AppError, message: String) : IllegalStateException(message)

data class VideoMetadata(
    val uriString: String,
    val name: String,
    val width: Int,
    val height: Int,
    val displayWidth: Int,
    val displayHeight: Int,
    val durationMs: Long,
    val frameRate: Double,
    val mimeType: String,
    val hasAudio: Boolean,
    val isHdr: Boolean,
    val sizeBytes: Long,
)

data class DeviceCapabilities(
    val supports1080pH264: Boolean = true,
    val supports1080pHevc: Boolean = false,
    val supports4kH264: Boolean = false,
    val supports4kHevc: Boolean = false,
    val thermalStatus: Int = 0,
    val freeStorageBytes: Long = Long.MAX_VALUE,
) {
    val supports4k: Boolean get() = supports4kH264 || supports4kHevc
    val isThermallyThrottled: Boolean get() = thermalStatus >= 3 // PowerManager.THERMAL_STATUS_SEVERE

    fun supportsHevcFor(resolution: OutputResolution): Boolean = when (resolution) {
        OutputResolution.FHD_1080P -> supports1080pHevc || supports4kHevc
        OutputResolution.UHD_4K -> supports4kHevc
    }
}

data class ExportOptions(
    val platform: PlatformPreset = PlatformPreset.INSTAGRAM,
    val postFormat: PostFormat = PostFormat.REEL,
    val quality: QualityMode = QualityMode.MAXIMUM,
    val framing: FramingMode = FramingMode.CONTAIN,
    val resolution: OutputResolution = OutputResolution.FHD_1080P,
    val codec: VideoCodecChoice = VideoCodecChoice.H264,
    val targetFps: TargetFps = TargetFps.SOURCE,
    val preset: EnhancementPreset = EnhancementPreset.NATURAL,
    val deblockStrength: Float = 0.25f,
    val denoiseStrength: Float = 0.45f,
    val detailStrength: Float = 0.65f,
    val clarityStrength: Float = 0.25f,
    val vibranceStrength: Float = 0.10f,
    val exposureStrength: Float = 0f,
    val warmthStrength: Float = 0f,
    val muteAudio: Boolean = false,
    val cropOffsetX: Float = 0f,
    val cropOffsetY: Float = 0f,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long? = null,
) {
    fun withPreset(targetPreset: EnhancementPreset): ExportOptions = when (targetPreset) {
        EnhancementPreset.NATURAL -> copy(
            preset = EnhancementPreset.NATURAL,
            deblockStrength = 0.25f,
            denoiseStrength = 0.45f,
            detailStrength = 0.65f,
            clarityStrength = 0.25f,
            vibranceStrength = 0.10f,
            exposureStrength = 0f,
            warmthStrength = 0f,
        )
        EnhancementPreset.CRISP_TEXT -> copy(
            preset = EnhancementPreset.CRISP_TEXT,
            deblockStrength = 0.20f,
            denoiseStrength = 0.20f,
            detailStrength = 0.85f,
            clarityStrength = 0.35f,
            vibranceStrength = 0.05f,
            exposureStrength = 0.05f,
            warmthStrength = 0f,
        )
        EnhancementPreset.LOW_LIGHT -> copy(
            preset = EnhancementPreset.LOW_LIGHT,
            deblockStrength = 0.45f,
            denoiseStrength = 0.75f,
            detailStrength = 0.45f,
            clarityStrength = 0.25f,
            vibranceStrength = 0.15f,
            exposureStrength = 0.18f,
            warmthStrength = 0.05f,
        )
        EnhancementPreset.CINEMA -> copy(
            preset = EnhancementPreset.CINEMA,
            deblockStrength = 0.15f,
            denoiseStrength = 0.30f,
            detailStrength = 0.55f,
            clarityStrength = 0.40f,
            vibranceStrength = 0.22f,
            exposureStrength = -0.04f,
            warmthStrength = 0.14f,
        )
        EnhancementPreset.CUSTOM -> copy(preset = EnhancementPreset.CUSTOM)
    }

    fun normalizedForPlatform(capabilities: DeviceCapabilities): ExportOptions {
        val validFormats = allowedFormats(platform)
        val nextFormat = if (postFormat in validFormats) postFormat else validFormats.first()
        val allow4k = capabilities.supports4k && platform !in setOf(PlatformPreset.INSTAGRAM, PlatformPreset.FACEBOOK)
        val nextRes = if (resolution == OutputResolution.UHD_4K && !allow4k) {
            OutputResolution.FHD_1080P
        } else {
            resolution
        }
        val canUseHevc = capabilities.supportsHevcFor(nextRes)
        val nextCodec = if (nextRes == OutputResolution.UHD_4K && !capabilities.supports4kH264 && capabilities.supports4kHevc) {
            VideoCodecChoice.HEVC
        } else if (codec == VideoCodecChoice.HEVC && !canUseHevc) {
            VideoCodecChoice.H264
        } else {
            codec
        }
        return copy(
            postFormat = nextFormat,
            resolution = nextRes,
            codec = nextCodec,
            deblockStrength = deblockStrength.coerceIn(0f, 1f),
            denoiseStrength = denoiseStrength.coerceIn(0f, 1f),
            detailStrength = detailStrength.coerceIn(0f, 1f),
            clarityStrength = clarityStrength.coerceIn(0f, 1f),
            vibranceStrength = vibranceStrength.coerceIn(0f, 1f),
            exposureStrength = exposureStrength.coerceIn(-1f, 1f),
            warmthStrength = warmthStrength.coerceIn(-1f, 1f),
            cropOffsetX = cropOffsetX.coerceIn(-1f, 1f),
            cropOffsetY = cropOffsetY.coerceIn(-1f, 1f),
        )
    }

    companion object {
        fun allowedFormats(platform: PlatformPreset): List<PostFormat> = when (platform) {
            PlatformPreset.INSTAGRAM, PlatformPreset.FACEBOOK -> listOf(
                PostFormat.REEL,
                PostFormat.FEED,
                PostFormat.STORY,
                PostFormat.LANDSCAPE,
            )
            PlatformPreset.TIKTOK -> listOf(
                PostFormat.REEL,
                PostFormat.STORY,
                PostFormat.LANDSCAPE,
            )
            PlatformPreset.YOUTUBE -> listOf(
                PostFormat.REEL,
                PostFormat.LANDSCAPE,
            )
        }
    }
}

data class NormalizedCropRect(
    val left: Float,
    val right: Float,
    val bottom: Float,
    val top: Float,
)

data class ExportPlan(
    val targetWidth: Int,
    val targetHeight: Int,
    val bitrateBps: Int,
    val useHevc: Boolean,
    val toneMapHdrToSdr: Boolean,
    val muteAudio: Boolean,
    val targetFps: Int?,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val effectiveDurationMs: Long,
    val cropRect: NormalizedCropRect?,
) {
    val estimatedBytes: Long
        get() = ((bitrateBps.toLong() + if (muteAudio) 0L else 192_000L) * effectiveDurationMs) / 8_000L
}

sealed interface ExportState {
    data object Idle : ExportState
    data object Preparing : ExportState
    data class Running(
        val jobId: String,
        val progress: Float,
        val processedMs: Long,
        val totalMs: Long,
        val outputName: String,
        val startedAtMs: Long = 0L,
    ) : ExportState {
        fun estimatedRemainingSeconds(nowMs: Long): Long? {
            if (startedAtMs <= 0L || progress < 0.05f || progress >= 0.99f) return null
            val elapsedMs = (nowMs - startedAtMs).coerceAtLeast(500L)
            val remainingMs = ((elapsedMs / progress.toDouble()) * (1.0 - progress.toDouble())).toLong()
            return (remainingMs / 1000L).coerceAtLeast(1L)
        }
    }

    data class Completed(
        val jobId: String,
        val outputUriString: String,
        val outputName: String,
        val metadata: VideoMetadata,
    ) : ExportState

    data object Cancelled : ExportState
    data class Failed(val error: AppError) : ExportState
}

object ExportPlanCalculator {
    fun buildPlan(
        source: VideoMetadata,
        rawOptions: ExportOptions,
        capabilities: DeviceCapabilities,
    ): Result<ExportPlan> {
        if (source.displayWidth <= 0 || source.displayHeight <= 0 || source.durationMs <= 0L) {
            return Result.failure(
                LocalizedExportException(AppError.InvalidSourceVideo, "Invalid source video dimensions or duration."),
            )
        }
        val options = rawOptions.normalizedForPlatform(capabilities)
        val (width, height) = targetDimensions(options.postFormat, options.resolution)

        val trimStart = options.trimStartMs.coerceIn(0L, (source.durationMs - 500L).coerceAtLeast(0L))
        val rawEnd = options.trimEndMs ?: source.durationMs
        val trimEnd = rawEnd.coerceIn((trimStart + 500L).coerceAtMost(source.durationMs), source.durationMs)
        val effectiveDuration = (trimEnd - trimStart).coerceAtLeast(1L)

        val useHevc = options.codec == VideoCodecChoice.HEVC &&
            capabilities.supportsHevcFor(options.resolution)
        val bitrate = computeBitrate(options.resolution, options.quality, useHevc)

        val requestedFps = options.targetFps.fps
        val effectiveTargetFps = if (requestedFps != null && source.frameRate > requestedFps + 1.0) {
            requestedFps
        } else {
            null
        }

        val cropRect = if (options.framing == FramingMode.COVER) {
            computeCoverCrop(
                sourceWidth = source.displayWidth,
                sourceHeight = source.displayHeight,
                targetWidth = width,
                targetHeight = height,
                offsetX = options.cropOffsetX,
                offsetY = options.cropOffsetY,
            )
        } else {
            null
        }

        val plan = ExportPlan(
            targetWidth = width,
            targetHeight = height,
            bitrateBps = bitrate,
            useHevc = useHevc,
            toneMapHdrToSdr = source.isHdr,
            muteAudio = options.muteAudio || !source.hasAudio,
            targetFps = effectiveTargetFps,
            trimStartMs = trimStart,
            trimEndMs = trimEnd,
            effectiveDurationMs = effectiveDuration,
            cropRect = cropRect,
        )

        val requiredBytes = (plan.estimatedBytes * 1.6).toLong() + 32L * 1024L * 1024L
        if (capabilities.freeStorageBytes < requiredBytes) {
            val reqMb = (requiredBytes / (1024L * 1024L)).coerceAtLeast(1L)
            val availMb = (capabilities.freeStorageBytes / (1024L * 1024L)).coerceAtLeast(0L)
            return Result.failure(
                LocalizedExportException(
                    AppError.InsufficientStorage(reqMb, availMb),
                    "Insufficient free storage for export.",
                ),
            )
        }
        return Result.success(plan)
    }

    fun buildSmartOutputName(sourceName: String, options: ExportOptions): String {
        val base = sourceName
            .substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9\\u0600-\\u06FF_-]+"), "_")
            .trim('_')
            .take(28)
            .ifBlank { "Video" }
        val plat = when (options.platform) {
            PlatformPreset.INSTAGRAM -> "IG"
            PlatformPreset.TIKTOK -> "TT"
            PlatformPreset.YOUTUBE -> "YT"
            PlatformPreset.FACEBOOK -> "FB"
        }
        val fmt = options.postFormat.name.lowercase().replaceFirstChar { it.uppercase() }
        val res = options.resolution.label
        val suffix = (System.currentTimeMillis() / 1000L) % 10_000L
        return "UPSCALE_${base}_${plat}_${fmt}_${res}_$suffix.mp4"
    }

    fun targetDimensions(format: PostFormat, resolution: OutputResolution): Pair<Int, Int> {
        val shortEdge = resolution.shortEdge
        return when (format) {
            PostFormat.REEL, PostFormat.STORY -> shortEdge to (shortEdge * 16 / 9)
            PostFormat.FEED -> shortEdge to ((shortEdge * 5f / 4f).roundToInt() and -2)
            PostFormat.LANDSCAPE -> (shortEdge * 16 / 9) to shortEdge
        }
    }

    fun computeBitrate(
        resolution: OutputResolution,
        quality: QualityMode,
        hevc: Boolean,
    ): Int {
        val base = when (resolution) {
            OutputResolution.FHD_1080P -> if (quality == QualityMode.MAXIMUM) 18_000_000 else 10_000_000
            OutputResolution.UHD_4K -> if (quality == QualityMode.MAXIMUM) 42_000_000 else 26_000_000
        }
        return if (hevc) (base * 0.78).roundToInt() else base
    }

    fun computeCoverCrop(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        offsetX: Float,
        offsetY: Float,
    ): NormalizedCropRect? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return null
        val sourceAspect = sourceWidth.toDouble() / sourceHeight.toDouble()
        val targetAspect = targetWidth.toDouble() / targetHeight.toDouble()
        if (kotlin.math.abs(sourceAspect - targetAspect) < 0.002) return null

        val clampedX = offsetX.coerceIn(-1f, 1f)
        val clampedY = offsetY.coerceIn(-1f, 1f)

        return if (sourceAspect > targetAspect) {
            val visibleHalfWidth = (targetAspect / sourceAspect).toFloat().coerceIn(0.05f, 1f)
            val maxShift = 1f - visibleHalfWidth
            val center = clampedX * maxShift
            NormalizedCropRect(
                left = (center - visibleHalfWidth).coerceIn(-1f, 1f),
                right = (center + visibleHalfWidth).coerceIn(-1f, 1f),
                bottom = -1f,
                top = 1f,
            )
        } else {
            val visibleHalfHeight = (sourceAspect / targetAspect).toFloat().coerceIn(0.05f, 1f)
            val maxShift = 1f - visibleHalfHeight
            val center = clampedY * maxShift
            NormalizedCropRect(
                left = -1f,
                right = 1f,
                bottom = (center - visibleHalfHeight).coerceIn(-1f, 1f),
                top = (center + visibleHalfHeight).coerceIn(-1f, 1f),
            )
        }
    }
}
