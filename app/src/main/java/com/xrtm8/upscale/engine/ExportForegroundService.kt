package com.xrtm8.upscale.engine

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.xrtm8.upscale.MainActivity
import com.xrtm8.upscale.model.ExportOptions
import com.xrtm8.upscale.model.ExportPlan
import com.xrtm8.upscale.model.ExportState
import com.xrtm8.upscale.model.VideoMetadata
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@UnstableApi
class ExportForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val cancelled = AtomicBoolean(false)

    private var transformer: Transformer? = null
    private var tempFile: File? = null
    private var currentJobId: String = ""
    private var currentOutputName: String = ""
    private var currentDurationMs: Long = 1L

    private val progressPoller = object : Runnable {
        override fun run() {
            val active = transformer ?: return
            if (cancelled.get()) return
            val holder = ProgressHolder()
            if (active.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                val pct = (holder.progress / 100f).coerceIn(0f, 0.97f)
                ExportRepository.setState(
                    ExportState.Running(
                        jobId = currentJobId,
                        progress = pct,
                        processedMs = (currentDurationMs * pct).toLong(),
                        totalMs = currentDurationMs,
                        outputName = currentOutputName,
                    ),
                )
                updateNotification((pct * 100).toInt(), currentOutputName)
            }
            handler.postDelayed(this, 250L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelExport()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val request = pendingRequest
                pendingRequest = null
                if (request == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundJob(request)
                return START_NOT_STICKY
            }
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    private fun startForegroundJob(request: ActiveExportRequest) {
        cancelled.set(false)
        val jobId = UUID.randomUUID().toString()
        val outputName = "UPSCALE-${jobId.take(10)}.mp4"
        currentJobId = jobId
        currentOutputName = outputName
        currentDurationMs = request.plan.effectiveDurationMs.coerceAtLeast(1L)

        val notification = buildNotification(progressPercent = 0, outputName = outputName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        ExportRepository.registerCancel { cancelExport() }
        ExportRepository.setState(
            ExportState.Running(
                jobId = jobId,
                progress = 0f,
                processedMs = 0L,
                totalMs = currentDurationMs,
                outputName = outputName,
            ),
        )

        val outDir = File(cacheDir, "upscale-exports").apply { mkdirs() }
        val file = File(outDir, "result-$jobId.mp4")
        tempFile = file

        val videoMime = if (request.plan.useHevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264
        val encoderFactory = DefaultEncoderFactory.Builder(this)
            .setEnableFallback(true)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(request.plan.bitrateBps)
                    .build(),
            )
            .build()

        val activeTransformer = Transformer.Builder(this)
            .setEncoderFactory(encoderFactory)
            .setVideoMimeType(videoMime)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    handler.removeCallbacks(progressPoller)
                    transformer = null
                    if (cancelled.get()) {
                        finishCancelled()
                        return
                    }
                    ExportRepository.setState(
                        ExportState.Running(
                            jobId = jobId,
                            progress = 0.98f,
                            processedMs = currentDurationMs,
                            totalMs = currentDurationMs,
                            outputName = outputName,
                        ),
                    )
                    updateNotification(98, outputName)
                    finalizeExport(file, outputName, jobId, request.plan, request.source.hasAudio)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exception: ExportException,
                ) {
                    handler.removeCallbacks(progressPoller)
                    transformer = null
                    if (cancelled.get()) {
                        finishCancelled()
                    } else {
                        failExport(exception.message ?: "Device video encoder failed.")
                    }
                }
            })
            .build()

        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(request.source.uriString))
        if (request.plan.trimStartMs > 0L || request.plan.trimEndMs < request.source.durationMs) {
            mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(request.plan.trimStartMs)
                    .setEndPositionMs(request.plan.trimEndMs)
                    .build(),
            )
        }

        val effects = VideoEffectsFactory.buildExportEffects(request.plan, request.options)
        val editedItem = EditedMediaItem.Builder(mediaItemBuilder.build())
            .setEffects(Effects(emptyList(), effects))
            .build()

        val composition = Composition.Builder(
            androidx.media3.transformer.EditedMediaItemSequence.Builder(listOf(editedItem)).build(),
        ).apply {
            if (request.plan.toneMapHdrToSdr) {
                setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            }
        }.build()

        try {
            transformer = activeTransformer
            activeTransformer.start(composition, file.absolutePath)
            handler.postDelayed(progressPoller, 250L)
        } catch (error: Exception) {
            handler.removeCallbacks(progressPoller)
            transformer = null
            failExport(error.message ?: "Unable to start export.")
        }
    }

    private fun finalizeExport(
        file: File,
        outputName: String,
        jobId: String,
        plan: ExportPlan,
        hadAudio: Boolean,
    ) {
        ioExecutor.execute {
            val published = ExportRepository.publishVerifiedExport(
                context = applicationContext,
                tempFile = file,
                outputName = outputName,
                plan = plan,
                hadAudio = hadAudio,
                isCancelled = { cancelled.get() },
            )
            file.delete()
            handler.post {
                if (cancelled.get()) {
                    finishCancelled()
                } else {
                    published.onSuccess { (uri, meta) ->
                        ExportRepository.registerCancel(null)
                        ExportRepository.setState(
                            ExportState.Completed(
                                jobId = jobId,
                                outputUriString = uri.toString(),
                                outputName = outputName,
                                metadata = meta,
                            ),
                        )
                        stopForegroundAndSelf()
                    }.onFailure { error ->
                        failExport(error.message ?: "Export verification failed.")
                    }
                }
            }
        }
    }

    private fun cancelExport() {
        if (!cancelled.compareAndSet(false, true)) return
        handler.removeCallbacks(progressPoller)
        transformer?.cancel()
        transformer = null
        finishCancelled()
    }

    private fun finishCancelled() {
        tempFile?.delete()
        tempFile = null
        ExportRepository.registerCancel(null)
        ExportRepository.setState(ExportState.Cancelled)
        stopForegroundAndSelf()
    }

    private fun failExport(message: String) {
        tempFile?.delete()
        tempFile = null
        ExportRepository.registerCancel(null)
        ExportRepository.setState(ExportState.Failed(message))
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(progressPercent: Int, outputName: String) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(progressPercent, outputName))
    }

    private fun buildNotification(progressPercent: Int, outputName: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ExportForegroundService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, ExportRepository.EXPORT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("UPSCALE Studio · $progressPercent%")
            .setContentText(outputName)
            .setProgress(100, progressPercent.coerceIn(0, 100), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "Cancel", cancelIntent)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressPoller)
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 4001
        private const val ACTION_START = "com.xrtm8.upscale.action.START_EXPORT"
        private const val ACTION_CANCEL = "com.xrtm8.upscale.action.CANCEL_EXPORT"

        @Volatile
        private var pendingRequest: ActiveExportRequest? = null

        fun start(
            context: Context,
            source: VideoMetadata,
            options: ExportOptions,
            plan: ExportPlan,
        ) {
            pendingRequest = ActiveExportRequest(source, options, plan)
            val intent = Intent(context, ExportForegroundService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }
    }
}
