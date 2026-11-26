package com.xrtm8.upscale.engine

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.media3.common.MimeTypes
import com.xrtm8.upscale.model.DeviceCapabilities
import com.xrtm8.upscale.model.VideoMetadata

data class RecentExportItem(
    val uriString: String,
    val name: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val sizeBytes: Long,
)

object MediaInspector {

    fun inspectUri(context: Context, uri: Uri): Result<VideoMetadata> = runCatching {
        val resolver = context.contentResolver
        var displayName = "video.mp4"
        var sizeBytes = 0L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) displayName = cursor.getString(nameIdx)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
                }
            }

        val extractor = MediaExtractor()
        val retriever = MediaMetadataRetriever()
        try {
            extractor.setDataSource(context, uri, null)
            retriever.setDataSource(context, uri)
            extractMetadata(extractor, retriever, uri.toString(), displayName, sizeBytes)
        } finally {
            runCatching { extractor.release() }
            runCatching { retriever.release() }
        }
    }

    fun inspectFile(path: String, name: String, sizeBytes: Long): Result<VideoMetadata> = runCatching {
        val extractor = MediaExtractor()
        val retriever = MediaMetadataRetriever()
        try {
            extractor.setDataSource(path)
            retriever.setDataSource(path)
            extractMetadata(extractor, retriever, path, name, sizeBytes)
        } finally {
            runCatching { extractor.release() }
            runCatching { retriever.release() }
        }
    }

    private fun extractMetadata(
        extractor: MediaExtractor,
        retriever: MediaMetadataRetriever,
        uriString: String,
        name: String,
        sizeBytes: Long,
    ): VideoMetadata {
        val tracks = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
        val video = requireNotNull(
            tracks.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true },
        ) { "No supported video track was found." }

        val width = video.getInteger(MediaFormat.KEY_WIDTH)
        val height = video.getInteger(MediaFormat.KEY_HEIGHT)
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        val rotated = rotation % 180 != 0
        val durationMs = if (video.containsKey(MediaFormat.KEY_DURATION)) {
            video.getLong(MediaFormat.KEY_DURATION) / 1_000L
        } else {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
        require(width > 0 && height > 0 && durationMs > 0L) {
            "Invalid video dimensions or duration."
        }
        val fps = runCatching { video.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble() }
            .getOrElse {
                runCatching { video.getFloat(MediaFormat.KEY_FRAME_RATE).toDouble() }.getOrDefault(0.0)
            }
            .let { if (it.isFinite() && it > 0.0) it else 30.0 }

        val transfer = if (video.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
            video.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
        } else {
            0
        }
        val isHdr = transfer == MediaFormat.COLOR_TRANSFER_ST2084 ||
            transfer == MediaFormat.COLOR_TRANSFER_HLG
        val hasAudio = tracks.any { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        val mime = video.getString(MediaFormat.KEY_MIME) ?: "video/avc"

        return VideoMetadata(
            uriString = uriString,
            name = name,
            width = width,
            height = height,
            displayWidth = if (rotated) height else width,
            displayHeight = if (rotated) width else height,
            durationMs = durationMs,
            frameRate = fps,
            mimeType = mime,
            hasAudio = hasAudio,
            isHdr = isHdr,
            sizeBytes = sizeBytes,
        )
    }

    fun inspectDeviceCapabilities(context: Context): DeviceCapabilities {
        val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        val h264Encoders = codecs.filter { it.isEncoder && MimeTypes.VIDEO_H264 in it.supportedTypes }
        val hevcEncoders = codecs.filter { it.isEncoder && MimeTypes.VIDEO_H265 in it.supportedTypes }

        val supports1080pH264 = h264Encoders.any { supportsSize(it, MimeTypes.VIDEO_H264, 1080, 1920) }
        val supports1080pHevc = hevcEncoders.any { supportsSize(it, MimeTypes.VIDEO_H265, 1080, 1920) }
        val supports4kH264 = h264Encoders.any { supportsSize(it, MimeTypes.VIDEO_H264, 2160, 3840) }
        val supports4kHevc = hevcEncoders.any { supportsSize(it, MimeTypes.VIDEO_H265, 2160, 3840) }

        val thermal = runCatching {
            context.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: 0
        }.getOrDefault(0)

        val cacheFree = runCatching {
            StatFs(context.cacheDir.absolutePath).availableBytes
        }.getOrDefault(Long.MAX_VALUE)

        val externalFree = runCatching {
            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: Environment.getExternalStorageDirectory()
            StatFs(moviesDir.absolutePath).availableBytes
        }.getOrDefault(Long.MAX_VALUE)

        return DeviceCapabilities(
            supports1080pH264 = supports1080pH264,
            supports1080pHevc = supports1080pHevc,
            supports4kH264 = supports4kH264,
            supports4kHevc = supports4kHevc,
            thermalStatus = thermal,
            freeStorageBytes = minOf(cacheFree, externalFree),
        )
    }

    private fun supportsSize(
        info: MediaCodecInfo,
        mime: String,
        width: Int,
        height: Int,
    ): Boolean = runCatching {
        val videoCaps = info.getCapabilitiesForType(mime).videoCapabilities
        videoCaps?.isSizeSupported(width, height) == true ||
            videoCaps?.isSizeSupported(height, width) == true
    }.getOrDefault(false)

    fun queryLatestExport(context: Context): RecentExportItem? = queryRecentExports(context, limit = 1).firstOrNull()

    fun queryRecentExports(context: Context, limit: Int = 8): List<RecentExportItem> = runCatching {
        val results = mutableListOf<RecentExportItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
        )
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Video.Media.IS_PENDING} = 0"
        val args = arrayOf("%UPSCALE%")
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            sortOrder,
        )?.use { cursor ->
            while (cursor.moveToNext() && results.size < limit) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)) ?: "UPSCALE.mp4"
                val w = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH))
                val h = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT))
                val dur = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE))
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                if (size > 0L) {
                    results += RecentExportItem(
                        uriString = uri.toString(),
                        name = name,
                        width = w,
                        height = h,
                        durationMs = dur,
                        sizeBytes = size,
                    )
                }
            }
        }
        results
    }.getOrDefault(emptyList())

    fun extractTrimThumbnails(
        context: Context,
        uriString: String,
        durationMs: Long,
        count: Int = 7,
    ): List<Bitmap> = runCatching {
        if (durationMs <= 0L || count <= 0) return emptyList()
        val retriever = MediaMetadataRetriever()
        val bitmaps = mutableListOf<Bitmap>()
        try {
            retriever.setDataSource(context, Uri.parse(uriString))
            for (i in 0 until count) {
                val fraction = if (count == 1) 0.5f else i.toFloat() / (count - 1).toFloat()
                val timeUs = (durationMs * 1_000L * fraction).toLong().coerceAtLeast(0L)
                val frame = retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    140,
                    200,
                )
                if (frame != null) {
                    bitmaps += frame
                }
            }
        } finally {
            runCatching { retriever.release() }
        }
        bitmaps
    }.getOrDefault(emptyList())
}
