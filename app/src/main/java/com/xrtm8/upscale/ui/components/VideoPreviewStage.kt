package com.xrtm8.upscale.ui.components

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.xrtm8.upscale.engine.LiveShaderParams
import com.xrtm8.upscale.engine.RecentExportItem
import com.xrtm8.upscale.engine.VideoEffectsFactory
import com.xrtm8.upscale.model.ExportOptions
import com.xrtm8.upscale.model.ExportPlanCalculator
import com.xrtm8.upscale.model.FramingMode
import com.xrtm8.upscale.model.PostFormat
import com.xrtm8.upscale.model.VideoMetadata
import com.xrtm8.upscale.ui.StudioStrings
import com.xrtm8.upscale.ui.theme.LocalAmoledWhite
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
    previewZoom: Float,
    showResultPreview: Boolean,
    isLocked: Boolean,
    recentExport: RecentExportItem?,
    strings: StudioStrings,
    onPickVideo: () -> Unit,
    onSelectQuickFormat: (PostFormat) -> Unit,
    onSplitPositionChange: (Float) -> Unit,
    onCropOffsetChange: (Float, Float) -> Unit,
    onZoomChange: (Float) -> Unit,
    onOpenRecent: (RecentExportItem) -> Unit,
    onShareRecent: (RecentExportItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isAmoledWhite = LocalAmoledWhite.current
    val accentFill = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyAccent
    val accentText = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyTextDark
    val primaryText = if (isAmoledWhite) StudioColors.DarkText else StudioColors.PureWhite
    val secondaryText = if (isAmoledWhite) StudioColors.DarkTextMuted else StudioColors.MutedWhite
    val cardBg = if (isAmoledWhite) StudioColors.WhiteSurface else StudioColors.StudioSurface
    val cardBorder = if (isAmoledWhite) StudioColors.WhiteBorder else StudioColors.StudioBorder

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
    var zoomPanX by remember(previewUri) { mutableFloatStateOf(0f) }
    var zoomPanY by remember(previewUri) { mutableFloatStateOf(0f) }

    LaunchedEffect(previewZoom) {
        if (previewZoom <= 1.01f) {
            zoomPanX = 0f
            zoomPanY = 0f
        }
    }

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
            val heroGrad = if (isAmoledWhite) {
                Brush.verticalGradient(listOf(StudioColors.WhiteSurface, StudioColors.WhiteSurfaceElevated))
            } else {
                Brush.verticalGradient(listOf(StudioColors.BurgundyGlow, StudioColors.StudioSurface))
            }
            val heroBorder = if (isAmoledWhite) {
                Brush.horizontalGradient(listOf(accentFill.copy(alpha = 0.45f), StudioColors.WhiteBorder))
            } else {
                Brush.horizontalGradient(listOf(accentFill.copy(alpha = 0.7f), Color(0x33E02447)))
            }

            Column(
                modifier = Modifier.fillMaxWidth(0.94f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(heroGrad)
                        .border(1.dp, heroBorder, RoundedCornerShape(22.dp))
                        .padding(20.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(accentFill),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = StudioColors.PureWhite,
                                modifier = Modifier.size(25.dp),
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = strings.heroTitle,
                                color = primaryText,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = strings.heroSubtitle,
                                color = secondaryText,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                            )
                        }

                        // Quick aspect/format selector inside Hero Card (no dead space, single action flow)
                        val quickFormats = ExportOptions.allowedFormats(options.platform)
                            .filter { it != PostFormat.STORY }
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            quickFormats.forEach { format ->
                                val selected = options.postFormat == format
                                val label = when (format) {
                                    PostFormat.REEL -> "${strings.reel} · \u20669:16\u2069"
                                    PostFormat.FEED -> "${strings.feed} · \u20664:5\u2069"
                                    PostFormat.LANDSCAPE -> "${strings.landscape}"
                                    PostFormat.STORY -> strings.story
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selected) accentFill else if (isAmoledWhite) StudioColors.WhiteSurface else StudioColors.StudioBackground,
                                        )
                                        .border(
                                            0.5.dp,
                                            if (selected) accentFill else cardBorder,
                                            RoundedCornerShape(10.dp),
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSelectQuickFormat(format)
                                        }
                                        .padding(horizontal = 11.dp, vertical = 7.dp),
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) StudioColors.PureWhite else primaryText,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPickVideo()
                            },
                            enabled = !isLocked,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentFill,
                                contentColor = StudioColors.PureWhite,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                        ) {
                            Text(
                                text = strings.chooseVideo,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = StudioColors.PureWhite,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                // Real latest exported video from Movies/UPSCALE (zero fake projects)
                if (recentExport != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg)
                            .border(0.5.dp, cardBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.latestExport,
                                color = accentText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${recentExport.name} · \u2066${recentExport.width}×${recentExport.height}\u2069 · \u2066${formatSizeMb(recentExport.sizeBytes)}\u2069",
                                color = secondaryText,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { onOpenRecent(recentExport) },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.OpenInNew,
                                    contentDescription = strings.openInGallery,
                                    tint = primaryText,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = { onShareRecent(recentExport) },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = strings.shareVideo,
                                    tint = accentText,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(StudioColors.AmoledBlack)
                        .border(0.8.dp, if (splitCompareEnabled) StudioColors.BurgundyBorderActive else cardBorder, RoundedCornerShape(16.dp)),
                ) {
                    val stageWidthPx = with(LocalDensity.current) { maxWidth.toPx() }.coerceAtLeast(1f)
                    val stageHeightPx = with(LocalDensity.current) { maxHeight.toPx() }.coerceAtLeast(1f)
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
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = previewZoom,
                                scaleY = previewZoom,
                                translationX = zoomPanX,
                                translationY = zoomPanY,
                            ),
                    )

                    // Zoom & Pan when zoomed > 1x, or 2-finger pinch anytime
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(previewZoom, stageWidthPx, stageHeightPx) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val nextZoom = (previewZoom * zoom).coerceIn(1f, 3f)
                                    if (nextZoom != previewZoom) {
                                        onZoomChange(nextZoom)
                                    }
                                    if (nextZoom > 1.02f) {
                                        val maxPanX = stageWidthPx * (nextZoom - 1f) * 0.5f
                                        val maxPanY = stageHeightPx * (nextZoom - 1f) * 0.5f
                                        zoomPanX = (zoomPanX + pan.x).coerceIn(-maxPanX, maxPanX)
                                        zoomPanY = (zoomPanY + pan.y).coerceIn(-maxPanY, maxPanY)
                                    }
                                }
                            },
                    )

                    // 1-finger drag to adjust Cover crop framing when not zoomed
                    if (!showResultPreview && options.framing == FramingMode.COVER && !isLocked && previewZoom <= 1.05f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(options.cropOffsetX, options.cropOffsetY, layoutDirection) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val dirSign = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                                        val dx = -(dragAmount.x * dirSign) / (stageWidthPx * 0.45f)
                                        val dy = dragAmount.y / (stageWidthPx * 0.45f)
                                        onCropOffsetChange(
                                            (options.cropOffsetX + dx).coerceIn(-1f, 1f),
                                            (options.cropOffsetY + dy).coerceIn(-1f, 1f),
                                        )
                                    }
                                },
                        )

                        if (options.cropOffsetX == 0f && options.cropOffsetY == 0f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp),
                            ) {
                                OverlayPill(strings.dragCropHint)
                            }
                        }
                    }

                    if (showSafeZones) {
                        SafeZonesOverlay(postFormat = options.postFormat)
                    }

                    if (splitCompareEnabled && !showResultPreview) {
                        val splitXPx = (stageWidthPx * splitPosition).roundToInt()
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(splitXPx - 1, 0) }
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(StudioColors.BurgundyAccent),
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

                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .offset { IntOffset((splitXPx - 21), 0) }
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(StudioColors.GlassBackground)
                                    .border(1.dp, StudioColors.BurgundyAccent, CircleShape)
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
                                    tint = StudioColors.PureWhite,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(34.dp),
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = accentText,
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
                            thumbColor = accentFill,
                            activeTrackColor = accentFill,
                            inactiveTrackColor = if (isAmoledWhite) StudioColors.WhiteSurfaceElevated else StudioColors.StudioSurfaceElevated,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatMs(positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryText,
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
        color = StudioColors.PureWhite,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(StudioColors.GlassBackground)
            .border(0.5.dp, StudioColors.StudioBorderSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun SafeZonesOverlay(postFormat: PostFormat) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
        val stroke = Color(0xAAF5F6F8)
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

fun formatSizeMb(bytes: Long): String {
    val mb = (bytes / (1024.0 * 1024.0)).coerceAtLeast(0.1)
    return "%.1f MB".format(mb)
}
