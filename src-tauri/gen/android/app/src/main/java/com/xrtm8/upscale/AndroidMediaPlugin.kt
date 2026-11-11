package com.xrtm8.upscale

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import app.tauri.annotation.Command
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

@TauriPlugin
@UnstableApi
class AndroidMediaPlugin(private val activity: Activity) : Plugin(activity) {
    private val io = Executors.newSingleThreadExecutor()
    private val resolver = activity.contentResolver
    private val cache = File(activity.cacheDir, "upscale-media").apply { mkdirs() }
    private var source: Source? = null
    private var job: Job? = null
    private var importing = false

    private data class Source(val id: String, val file: File, val info: JSObject)

    private class Job(val id: String, val file: File, val name: String, val duration: Double) {
        var status = "running"
        var progress = 0.0
        var error: String? = null
        var transformer: Transformer? = null
        var info: JSObject? = null
        @Volatile var cancelled = false
        @Volatile var published: Uri? = null

        fun snapshot() = JSObject().apply {
            put("id", id)
            put("status", status)
            put("progress", progress)
            put("processedSeconds", duration * progress)
            put("outputName", name)
            error?.let { put("error", it) }
        }
    }

    init {
        val cutoff = System.currentTimeMillis() - 86_400_000L
        io.execute {
            cache.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
        }
    }

    @Command
    fun importVideo(invoke: Invoke) = onUi(invoke) {
        check(!importing && job?.status != "running") { "Finish the current operation first." }
        val uri = Uri.parse(invoke.getArgs().getString("uri"))
        require(uri.scheme == "content") { "Choose a video using the Android file picker." }
        importing = true
        io.execute {
            val id = UUID.randomUUID().toString()
            var staged: File? = null
            val result = runCatching {
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Video"
                val suffix = name.substringAfterLast('.', "mp4").lowercase()
                    .takeIf { it in setOf("mp4", "mov", "m4v", "mkv", "webm", "avi") } ?: "mp4"
                val file = File(cache, "source-$id.$suffix")
                staged = file
                val input = requireNotNull(resolver.openInputStream(uri)) { "Cannot read the selected video." }
                input.use { stream -> file.outputStream().use { stream.copyTo(it) } }
                Source(id, file, inspect(file, name))
            }
            activity.runOnUiThread {
                importing = false
                result.onSuccess { imported ->
                    source?.file?.delete()
                    job?.file?.delete()
                    job = null
                    source = imported
                    invoke.resolve(media(imported.id, imported.file, imported.info))
                }.onFailure { error ->
                    staged?.delete()
                    invoke.reject(error.message ?: "Cannot import the video.")
                }
            }
        }
    }

    @Command
    fun startExport(invoke: Invoke) = onUi(invoke) {
        check(!importing && job?.status != "running") { "Finish the current operation first." }
        val args = invoke.getArgs()
        val input = requireNotNull(source?.takeIf { it.id == args.getString("sourceId") }) {
            "Import a video before exporting."
        }
        check(!input.info.getBoolean("hdr")) { "HDR export is unavailable on Android in this release." }
        val width = args.getInt("width")
        val height = args.getInt("height")
        val bitrate = args.getInt("bitrate")
        val layout = if (args.getBoolean("crop")) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            else Presentation.LAYOUT_SCALE_TO_FIT
        val id = UUID.randomUUID().toString()
        val name = "UPSCALE-${id.take(12)}.mp4"
        val next = Job(id, File(cache, "result-$id.mp4"), name, input.info.getDouble("durationSeconds"))
        val encoder = DefaultEncoderFactory.Builder(activity)
            .setEnableFallback(false)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
            .build()
        val transformer = Transformer.Builder(activity)
            .setEncoderFactory(encoder)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (next.cancelled) return
                    next.transformer = null
                    next.progress = 0.99
                    saveToGallery(next, width, height, input.info.getBoolean("hasAudio"))
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exception: ExportException) {
                    if (!next.cancelled) fail(next, exception)
                }
            }).build()
        val video = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input.file)))
            .setEffects(Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(width, height, layout))))
            .build()
        try {
            transformer.start(video, next.file.absolutePath)
        } catch (error: Exception) {
            next.file.delete()
            throw error
        }
        job?.file?.delete()
        next.transformer = transformer
        job = next
        invoke.resolve(next.snapshot())
    }

    @Command
    fun jobStatus(invoke: Invoke) = onUi(invoke) {
        val active = requireJob(invoke)
        active.transformer?.let { transformer ->
            val holder = ProgressHolder()
            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                active.progress = (holder.progress / 100.0).coerceIn(active.progress, 0.98)
            }
        }
        invoke.resolve(active.snapshot())
    }

    @Command
    fun cancelExport(invoke: Invoke) = onUi(invoke) {
        val active = requireJob(invoke)
        synchronized(active) {
            if (active.status == "running" && active.published == null) {
                active.cancelled = true
                active.transformer?.let {
                    it.cancel()
                    active.transformer = null
                    active.file.delete()
                    active.status = "cancelled"
                }
            }
        }
        invoke.resolve()
    }

    @Command
    fun previewExport(invoke: Invoke) = onUi(invoke) {
        val active = requireJob(invoke)
        val info = requireNotNull(active.info) { "The export is still in progress." }
        invoke.resolve(media("output:${active.id}", active.file, info))
    }

    @Command
    fun shareExport(invoke: Invoke) = onUi(invoke) {
        val active = requireJob(invoke)
        val uri = requireNotNull(active.published) { "The export is still in progress." }
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(resolver, active.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(share, null))
        invoke.resolve()
    }

    private fun requireJob(invoke: Invoke) = requireNotNull(job?.takeIf { it.id == invoke.getArgs().getString("jobId") }) {
        "The export job is unavailable."
    }

    private fun onUi(invoke: Invoke, action: () -> Unit) = activity.runOnUiThread {
        try {
            action()
        } catch (error: Exception) {
            invoke.reject(error.message ?: "The operation could not be completed.")
        }
    }

    private fun saveToGallery(active: Job, width: Int, height: Int, hadAudio: Boolean) {
        io.execute {
            var pending: Uri? = null
            val result = runCatching {
                val info = inspect(active.file, active.name)
                check(info.getInt("displayWidth") == width && info.getInt("displayHeight") == height) {
                    "The device encoder did not produce the requested resolution."
                }
                check(kotlin.math.abs(info.getDouble("durationSeconds") - active.duration) < 0.5) {
                    "The exported video duration does not match the source."
                }
                check(!hadAudio || info.getBoolean("hasAudio")) { "The exported video is missing audio." }
                check(!active.cancelled)
                val metadata = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, active.name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/UPSCALE")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = requireNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, metadata)) {
                    "Cannot create a video in the gallery."
                }
                pending = uri
                val output = requireNotNull(resolver.openOutputStream(uri, "w"))
                output.use { sink ->
                    active.file.inputStream().use { input ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            check(!active.cancelled)
                            val count = input.read(buffer)
                            if (count < 0) break
                            sink.write(buffer, 0, count)
                        }
                    }
                }
                synchronized(active) {
                    check(!active.cancelled)
                    val complete = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                    check(resolver.update(uri, complete, null, null) == 1) { "Cannot publish the video to the gallery." }
                    active.published = uri
                }
                info
            }
            if (result.isFailure) pending?.let { uri -> runCatching { resolver.delete(uri, null, null) } }
            activity.runOnUiThread {
                result.onSuccess { info ->
                    active.info = info
                    active.progress = 1.0
                    active.status = "completed"
                }.onFailure { error -> fail(active, error) }
            }
        }
    }

    private fun fail(active: Job, error: Throwable) {
        active.transformer = null
        active.file.delete()
        active.status = if (active.cancelled) "cancelled" else "failed"
        if (!active.cancelled) active.error = error.message ?: "The device could not encode this video."
    }

    private fun media(id: String, file: File, info: JSObject) = JSObject().apply {
        put("id", id)
        put("previewUrl", file.absolutePath)
        put("native", true)
        put("info", info)
    }

    private fun inspect(file: File, name: String): JSObject {
        val extractor = MediaExtractor()
        val retriever = MediaMetadataRetriever()
        try {
            extractor.setDataSource(file.absolutePath)
            retriever.setDataSource(file.absolutePath)
            val tracks = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
            val video = requireNotNull(tracks.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }) {
                "No supported video track was found."
            }
            val width = video.getInteger(MediaFormat.KEY_WIDTH)
            val height = video.getInteger(MediaFormat.KEY_HEIGHT)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val rotated = rotation % 180 != 0
            val duration = if (video.containsKey(MediaFormat.KEY_DURATION)) video.getLong(MediaFormat.KEY_DURATION) / 1_000_000.0
                else (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000.0
            check(width > 0 && height > 0 && duration > 0) { "Invalid video dimensions or duration." }
            val fps = runCatching { video.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble() }.getOrElse {
                runCatching { video.getFloat(MediaFormat.KEY_FRAME_RATE).toDouble() }.getOrDefault(0.0)
            }
            val transfer = video.getInteger(MediaFormat.KEY_COLOR_TRANSFER, 0)
            return JSObject().apply {
                put("name", name)
                put("width", width)
                put("height", height)
                put("displayWidth", if (rotated) height else width)
                put("displayHeight", if (rotated) width else height)
                put("durationSeconds", duration)
                put("frameRate", if (fps.isFinite() && fps > 0.0) fps else 0.0)
                put("codec", video.getString(MediaFormat.KEY_MIME))
                put("hasAudio", tracks.any { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true })
                put("hdr", transfer == MediaFormat.COLOR_TRANSFER_ST2084 || transfer == MediaFormat.COLOR_TRANSFER_HLG)
                put("interlaced", false)
                put("sizeBytes", file.length())
                put("videoStream", tracks.indexOf(video))
            }
        } finally {
            extractor.release()
            retriever.release()
        }
    }
}
