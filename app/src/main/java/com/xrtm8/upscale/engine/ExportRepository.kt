package com.xrtm8.upscale.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import com.xrtm8.upscale.model.AppError
import com.xrtm8.upscale.model.ExportOptions
import com.xrtm8.upscale.model.ExportPlan
import com.xrtm8.upscale.model.ExportState
import com.xrtm8.upscale.model.LocalizedExportException
import com.xrtm8.upscale.model.VideoMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.abs

object ExportRepository {
    const val EXPORT_CHANNEL_ID = "upscale_export_channel"

    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    @Volatile
    private var activeCancelCallback: (() -> Unit)? = null

    fun setState(next: ExportState) {
        _state.value = next
    }

    fun registerCancel(callback: (() -> Unit)?) {
        activeCancelCallback = callback
    }

    fun cancelActiveExport() {
        activeCancelCallback?.invoke()
    }

    fun resetIfTerminal() {
        val current = _state.value
        if (current is ExportState.Completed || current is ExportState.Cancelled || current is ExportState.Failed) {
            _state.value = ExportState.Idle
        }
    }

    fun cleanupStaleCache(context: Context) {
        val cutoff = System.currentTimeMillis() - 12L * 3600L * 1000L
        val dir = File(context.cacheDir, "upscale-exports")
        if (!dir.exists()) return
        dir.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    fun isVerifiedDimensions(actualWidth: Int, actualHeight: Int, expectedWidth: Int, expectedHeight: Int): Boolean {
        // Hardware encoders sometimes align odd/non-mod16 heights (e.g., 1350 -> 1344 or 1360).
        return abs(actualWidth - expectedWidth) <= 16 && abs(actualHeight - expectedHeight) <= 16
    }

    fun publishVerifiedExport(
        context: Context,
        tempFile: File,
        outputName: String,
        plan: ExportPlan,
        hadAudio: Boolean,
        isCancelled: () -> Boolean,
    ): Result<Pair<Uri, VideoMetadata>> = runCatching {
        val info = MediaInspector.inspectFile(tempFile.absolutePath, outputName, tempFile.length())
            .getOrThrow()

        if (!isVerifiedDimensions(info.displayWidth, info.displayHeight, plan.targetWidth, plan.targetHeight)) {
            throw LocalizedExportException(
                AppError.ExportDimensionMismatch(info.displayWidth, info.displayHeight, plan.targetWidth, plan.targetHeight),
                "Encoder returned ${info.displayWidth}x${info.displayHeight} instead of ${plan.targetWidth}x${plan.targetHeight}.",
            )
        }
        val toleranceMs = maxOf(600L, (3_000.0 / info.frameRate).toLong())
        if (abs(info.durationMs - plan.effectiveDurationMs) > toleranceMs) {
            throw LocalizedExportException(
                AppError.ExportDurationMismatch,
                "Exported duration (${info.durationMs}ms) does not match expected (${plan.effectiveDurationMs}ms).",
            )
        }
        if (!plan.muteAudio && hadAudio && !info.hasAudio) {
            throw LocalizedExportException(
                AppError.ExportMissingAudio,
                "Exported video is missing its audio track.",
            )
        }
        check(!isCancelled()) { "Cancelled" }

        val resolver = context.contentResolver
        val metadata = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, outputName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/UPSCALE")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, metadata)) {
            "Could not create entry in MediaStore."
        }
        var published = false
        try {
            val out = requireNotNull(resolver.openOutputStream(uri, "w")) {
                "Could not open MediaStore output stream."
            }
            out.use { sink ->
                tempFile.inputStream().use { input ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        check(!isCancelled()) { "Cancelled" }
                        val read = input.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                    }
                }
            }
            check(!isCancelled()) { "Cancelled" }
            val ready = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            check(resolver.update(uri, ready, null, null) == 1) {
                "Could not finalize MediaStore video."
            }
            published = true
        } finally {
            if (!published) {
                runCatching { resolver.delete(uri, null, null) }
            }
        }
        uri to info
    }

    fun saveSnapshotBitmap(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
    ): Result<Uri> = runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/UPSCALE")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) {
            "Could not create snapshot entry."
        }
        var saved = false
        try {
            requireNotNull(resolver.openOutputStream(uri, "w")).use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    "Failed to encode snapshot PNG."
                }
            }
            val ready = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            check(resolver.update(uri, ready, null, null) == 1) {
                "Could not finalize snapshot."
            }
            saved = true
        } finally {
            if (!saved) {
                runCatching { resolver.delete(uri, null, null) }
            }
        }
        uri
    }
}

data class ActiveExportRequest(
    val source: VideoMetadata,
    val options: ExportOptions,
    val plan: ExportPlan,
)
