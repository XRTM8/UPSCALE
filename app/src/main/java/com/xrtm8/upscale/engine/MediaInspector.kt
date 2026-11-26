package com.xrtm8.upscale.engine

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.PowerManager
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.media3.common.MimeTypes
import com.xrtm8.upscale.model.DeviceCapabilities
import com.xrtm8.upscale.model.VideoMetadata

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
        val supports4kH264 = h264Encoders.any { supportsSize(it, MimeTypes.VIDEO_H264, 2160, 3840) }
        val supports4kHevc = hevcEncoders.any { supportsSize(it, MimeTypes.VIDEO_H265, 2160, 3840) }

        val thermal = runCatching {
            context.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: 0
        }.getOrDefault(0)

        val freeBytes = runCatching {
            StatFs(context.cacheDir.absolutePath).availableBytes
        }.getOrDefault(Long.MAX_VALUE)

        return DeviceCapabilities(
            supports1080pH264 = supports1080pH264,
            supports4kH264 = supports4kH264,
            supports4kHevc = supports4kHevc,
            thermalStatus = thermal,
            freeStorageBytes = freeBytes,
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
}
