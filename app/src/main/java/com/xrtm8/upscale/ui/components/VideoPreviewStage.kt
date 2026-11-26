package com.xrtm8.upscale.ui.components

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.xrtm8.upscale.engine.LiveShaderParams
import com.xrtm8.upscale.engine.VideoEffectsFactory
import com.xrtm8.upscale.model.ExportOptions
import com.xrtm8.upscale.model.ExportPlanCalculator
import com.xrtm8.upscale.model.FramingMode
import com.xrtm8.upscale.model.PostFormat
import com.xrtm8.upscale.model.VideoMetadata
import com.xrtm8.upscale.ui.StudioStrings
import com.xrtm8.upscale.ui.theme.StudioColors
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPreviewStage(
    previewUri: String?,
    metadata: VideoMetadata?,
    options: ExportOptions,
    shaderParamsRef: AtomicReference<LiveShaderParams>,
    splitCompareEnabled: Boolean,
    splitPosition: Float,
    showSafeZones: Boolean,
    showResultPreview: Boolean,
    isLocked: Boolean,
    strings: StudioStrings,
    onPickVideo: () -> Unit,
    onSplitPositionChange: (Float) -> Unit,
    onCropOffsetChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val targetRatio = options.postFormat.ratioWidth.toFloat() / options.postFormat.ratioHeight.toFloat()
    val (targetW, targetH) = ExportPlanCalculator.targetDimensions(options.postFormat, options.resolution)
    val cropRect = remember(metadata, options.framing, options.cropOffsetX, options.cropOffsetY, targetW, targetH, showResultPreview) {
        if (metadata != null && !showResultPreview && options.framing == FramingMode.COVER) {
            ExportPlanCalculator.computeCoverCrop(
                sourceWidth = metadata.displayWidth,
                sourceHeight = metadata.displayHeight,
                targetWidth = targetW,
                targetHeight = targetH,
                offsetX = options.cropOffsetX,
                offsetY = options.cropOffsetY,
            )
        } else {
            null
        }
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val d = exoPlayer.duration
                if (d > 0) durationMs = d
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(previewUri, cropRect, options.trimStartMs, options.trimEndMs, showResultPreview) {
        if (previewUri == null) {
            exoPlayer.clearMediaItems()
            isPlaying = false
            positionMs = 0L
            durationMs = 0L
            return@LaunchedEffect
        }
        val effects = VideoEffectsFactory.buildPreviewEffects(shaderParamsRef, cropRect)
        exoPlayer.setVideoEffects(effects)

        val itemBuilder = MediaItem.Builder().setUri(Uri.parse(previewUri))
        if (!showResultPreview && metadata != null &&
            (options.trimStartMs > 0L || (options.trimEndMs != null && options.trimEndMs < metadata.durationMs))
        ) {
            val end = (options.trimEndMs ?: metadata.durationMs).coerceAtLeast(options.trimStartMs + 500L)
            itemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(options.trimStartMs)
                    .setEndPositionMs(end)
                    .build(),
            )
        }
        exoPlayer.setMediaItem(itemBuilder.build())
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(exoPlayer, isPlaying, previewUri) {
        while (previewUri != null) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val d = exoPlayer.duration
            if (d > 0) durationMs = d
            delay(if (isPlaying) 100L else 350L)
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (previewUri == null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(targetRatio, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF14161B))
                    .border(1.dp, StudioColors.Hairline, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF21242B))
                            .border(1.dp, StudioColors.Hairline, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = StudioColors.TextSecondary,
                        )
                    }
                    Button(
                        onClick = onPickVideo,
                        enabled = !isLocked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF262A32),
                            contentColor = StudioColors.TextPrimary,
                        ),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(strings.chooseVideo, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        } else {
            val resizeMode = if (options.framing == FramingMode.COVER || showResultPreview) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(targetRatio, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .border(1.dp, StudioColors.HairlineStrong, RoundedCornerShape(14.dp)),
                ) {
                    val stageWidthPx = with(LocalDensity.current) { maxWidth.toPx() }.coerceAtLeast(1f)
                    val layoutDirection = LocalLayoutDirection.current

                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                player = exoPlayer
                            }
                        },
                        update = { view ->
                            view.resizeMode = resizeMode
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Interactive pan gesture for Cover crop framing when split handle is not used.
                    if (!showResultPreview && options.framing == FramingMode.COVER && !isLocked) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(options.cropOffsetX, options.cropOffsetY, layoutDirection) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val dirSign = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                                        val dx = -(dragAmount.x * dirSign) / (stageWidthPx * 0.45f)
                                        val dy = (dragAmount.y) / (stageWidthPx * 0.45f)
                                        onCropOffsetChange(
                                            (options.cropOffsetX + dx).coerceIn(-1f, 1f),
                                            (options.cropOffsetY + dy).coerceIn(-1f, 1f),
                                        )
                                    }
                                },
                        )
                    }

                    // Platform Safe Zones overlay
                    if (showSafeZones) {
                        SafeZonesOverlay(postFormat = options.postFormat)
                    }

                    // Split-screen comparison divider & labels (LTR visual coordinates for texture X)
                    if (splitCompareEnabled && !showResultPreview) {
                        val splitXPx = (stageWidthPx * splitPosition).roundToInt()
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(splitXPx - 1, 0) }
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(StudioColors.PrimaryFill.copy(alpha = 0.85f)),
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                OverlayPill(strings.before)
                                OverlayPill(strings.after)
                            }

                            // Draggable split handle
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .offset { IntOffset((splitXPx - 22), 0) }
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(StudioColors.GlassElevated)
                                    .border(1.dp, StudioColors.HairlineStrong, CircleShape)
                                    .pointerInput(stageWidthPx, layoutDirection) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val visualDelta = if (layoutDirection == LayoutDirection.Rtl) {
                                                -dragAmount.x
                                            } else {
                                                dragAmount.x
                                            }
                                            onSplitPositionChange(splitPosition + visualDelta / stageWidthPx)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.CompareArrows,
                                    contentDescription = strings.compare,
                                    tint = StudioColors.TextPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Minimal playback scrub bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(34.dp),
                ) {
                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = StudioColors.TextPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    val total = durationMs.coerceAtLeast(1L)
                    Slider(
                        value = (positionMs.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { fraction ->
                            val seekMs = (fraction * total).toLong()
                            positionMs = seekMs
                            exoPlayer.seekTo(seekMs)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = StudioColors.PrimaryFill,
                            activeTrackColor = StudioColors.PrimaryFill,
                            inactiveTrackColor = StudioColors.HairlineStrong,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatMs(positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayPill(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = StudioColors.TextPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xB30C0E11))
            .border(1.dp, StudioColors.Hairline, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun SafeZonesOverlay(postFormat: PostFormat) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
        val stroke = Color(0x99E0E1E4)
        when (postFormat) {
            PostFormat.REEL, PostFormat.STORY -> {
                val topSafe = size.height * 0.14f
                val bottomSafe = size.height * 0.72f
                val rightSafe = size.width * 0.84f
                drawLine(stroke, Offset(0f, topSafe), Offset(size.width, topSafe), 2f, pathEffect = dash)
                drawLine(stroke, Offset(0f, bottomSafe), Offset(size.width, bottomSafe), 2f, pathEffect = dash)
                drawLine(stroke, Offset(rightSafe, topSafe), Offset(rightSafe, bottomSafe), 2f, pathEffect = dash)
            }
            PostFormat.FEED, PostFormat.LANDSCAPE -> {
                val insetX = size.width * 0.06f
                val insetY = size.height * 0.08f
                drawLine(stroke, Offset(insetX, insetY), Offset(size.width - insetX, insetY), 2f, pathEffect = dash)
                drawLine(stroke, Offset(insetX, size.height - insetY), Offset(size.width - insetX, size.height - insetY), 2f, pathEffect = dash)
            }
        }
    }
}

fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}
