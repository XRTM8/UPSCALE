package com.xrtm8.upscale.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.xrtm8.upscale.model.ExportOptions
import com.xrtm8.upscale.model.ExportPlan
import com.xrtm8.upscale.model.ExportState
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

        check(info.displayWidth == plan.targetWidth && info.displayHeight == plan.targetHeight) {
            "Encoder returned ${info.displayWidth}x${info.displayHeight} instead of ${plan.targetWidth}x${plan.targetHeight}."
        }
        val toleranceMs = maxOf(600L, (3_000.0 / info.frameRate).toLong())
        check(abs(info.durationMs - plan.effectiveDurationMs) <= toleranceMs) {
            "Exported duration (${info.durationMs}ms) does not match expected (${plan.effectiveDurationMs}ms)."
        }
        check(!hadAudio || info.hasAudio) {
            "Exported video is missing its audio track."
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
}

data class ActiveExportRequest(
    val source: VideoMetadata,
    val options: ExportOptions,
    val plan: ExportPlan,
)
