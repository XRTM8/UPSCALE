package com.xrtm8.upscale.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xrtm8.upscale.BuildConfig
import com.xrtm8.upscale.engine.LiveShaderParams
import com.xrtm8.upscale.engine.RecentExportItem
import com.xrtm8.upscale.engine.UpdateStatus
import com.xrtm8.upscale.model.AppLanguage
import com.xrtm8.upscale.model.CustomPreset
import com.xrtm8.upscale.model.EnhancementPreset
import com.xrtm8.upscale.model.ExportOptions
import com.xrtm8.upscale.model.ExportState
import com.xrtm8.upscale.model.FramingMode
import com.xrtm8.upscale.model.OutputResolution
import com.xrtm8.upscale.model.PostFormat
import com.xrtm8.upscale.ui.components.BatchQueueBottomSheet
import com.xrtm8.upscale.ui.components.ExportStatusStrip
import com.xrtm8.upscale.ui.components.OutputBottomSheet
import com.xrtm8.upscale.ui.components.ProcessingBottomSheet
import com.xrtm8.upscale.ui.components.RecentExportsBottomSheet
import com.xrtm8.upscale.ui.components.UpdateNoticeStrip
import com.xrtm8.upscale.ui.components.VideoPreviewStage
import com.xrtm8.upscale.ui.components.formatSizeMb
import com.xrtm8.upscale.ui.theme.LocalAmoledWhite
import com.xrtm8.upscale.ui.theme.StudioColors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

@Composable
fun StudioScreen(
    state: StudioUiState,
    shaderParamsRef: AtomicReference<LiveShaderParams>,
    onPickVideo: () -> Unit,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSheet: (StudioSheet) -> Unit,
    onCloseSheet: () -> Unit,
    onToggleSplitCompare: () -> Unit,
    onSplitPositionChange: (Float) -> Unit,
    onToggleSafeZones: () -> Unit,
    onToggleZoom: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onSetShowResult: (Boolean) -> Unit,
    onUpdateOptions: ((ExportOptions) -> ExportOptions) -> Unit,
    onApplyPreset: (EnhancementPreset) -> Unit,
    onApplyCustomPreset: (CustomPreset) -> Unit,
    onSaveCustomPreset: (String) -> Unit,
    onDeleteCustomPreset: (String) -> Unit,
    onResetFilters: () -> Unit,
    onSaveSnapshot: (Bitmap) -> Unit,
    onSnapshotFailed: () -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit,
    onPickBatchVideos: () -> Unit,
    onRemoveBatchItem: (String) -> Unit,
    onClearDoneBatch: () -> Unit,
    onStartBatchExport: () -> Unit,
    onSeekPreview: (Long) -> Unit,
    onOpenOutput: () -> Unit,
    onShareOutput: () -> Unit,
    onOpenRecent: (RecentExportItem) -> Unit,
    onShareRecent: (RecentExportItem) -> Unit,
    onImportRecent: (RecentExportItem) -> Unit,
    onRefreshRecents: () -> Unit,
    onDismissError: () -> Unit,
    onCheckUpdate: () -> Unit,
    onStartUpdateDownload: () -> Unit,
    onInstallUpdate: () -> Unit,
    onDismissUpdateBanner: () -> Unit,
) {
    val strings = stringsFor(state.language)
    val layoutDirection = if (state.language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val isAmoledWhite = LocalAmoledWhite.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var captureFrameAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    DisposableEffect(state.isExportRunning, view) {
        view.keepScreenOn = state.isExportRunning
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshRecents()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val rootBg = if (isAmoledWhite) StudioColors.AmoledWhite else StudioColors.AmoledBlack
    val barBg = if (isAmoledWhite) StudioColors.GlassBackgroundWhite else StudioColors.GlassBackground
    val barBorder = if (isAmoledWhite) StudioColors.WhiteBorder else StudioColors.StudioBorder
    val pillBg = if (isAmoledWhite) StudioColors.WhiteSurfaceElevated else StudioColors.StudioSurface
    val primaryText = if (isAmoledWhite) StudioColors.DarkText else StudioColors.PureWhite
    val secondaryText = if (isAmoledWhite) StudioColors.DarkTextMuted else StudioColors.MutedWhite
    val mutedText = if (isAmoledWhite) StudioColors.DarkTextSubtle else StudioColors.SubtleGray
    val accentFill = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyAccent
    val accentText = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyTextDark

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(rootBg)
                .safeDrawingPadding()
                .animateContentSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(barBg)
                    .border(0.5.dp, barBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "UPSCALE",
                            color = primaryText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp,
                        )

                        val hasReadyUpdate = state.updateStatus is UpdateStatus.Available ||
                            state.updateStatus is UpdateStatus.ReadyToInstall
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(StudioColors.BurgundyMuted)
                                .border(
                                    0.5.dp,
                                    if (hasReadyUpdate) StudioColors.Success else accentText.copy(alpha = 0.45f),
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onCheckUpdate()
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            if (hasReadyUpdate) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(StudioColors.Success),
                                )
                            }
                            Text(
                                text = "\u2066v${BuildConfig.VERSION_NAME}\u2069",
                                color = accentText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(pillBg)
                                .border(0.5.dp, barBorder, CircleShape)
                                .clickable(enabled = !state.isLocked) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onOpenSheet(StudioSheet.BATCH)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Layers,
                                contentDescription = strings.batchQueue,
                                tint = if (state.batchQueue.isNotEmpty()) accentText else primaryText,
                                modifier = Modifier.size(17.dp),
                            )
                        }

                        if (state.recentExports.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(pillBg)
                                    .border(0.5.dp, barBorder, CircleShape)
                                    .clickable(enabled = !state.isLocked) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onOpenSheet(StudioSheet.EXPORTS)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = strings.exportsHistory,
                                    tint = primaryText,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(pillBg)
                                .border(0.5.dp, barBorder, CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleTheme()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (isAmoledWhite) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = strings.toggleTheme,
                                tint = if (isAmoledWhite) StudioColors.DarkText else accentText,
                                modifier = Modifier.size(17.dp),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(pillBg)
                                .border(0.5.dp, barBorder, RoundedCornerShape(17.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleLanguage()
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (state.language == AppLanguage.AR) "EN" else "عربي",
                                style = MaterialTheme.typography.labelMedium,
                                color = primaryText,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = state.updateBannerVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                UpdateNoticeStrip(
                    status = state.updateStatus,
                    strings = strings,
                    onDownload = onStartUpdateDownload,
                    onInstall = onInstallUpdate,
                    onDismiss = onDismissUpdateBanner,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.source != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Movie,
                                contentDescription = null,
                                tint = accentText,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = state.activePreviewMetadata?.name ?: strings.noVideoLoaded,
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.options.postFormat == PostFormat.ORIGINAL && state.activePreviewMetadata != null) {
                                "\u2066${state.activePreviewMetadata!!.displayWidth}:${state.activePreviewMetadata!!.displayHeight}\u2069"
                            } else {
                                "\u2066${state.options.postFormat.aspectLabel}\u2069"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryText,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(pillBg)
                                .border(0.5.dp, barBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }

                VideoPreviewStage(
                    previewUri = state.activePreviewUri,
                    metadata = state.source,
                    options = state.options,
                    shaderParamsRef = shaderParamsRef,
                    splitCompareEnabled = state.splitCompareEnabled,
                    splitPosition = state.splitPosition,
                    showSafeZones = state.showSafeZones,
                    previewZoom = state.previewZoom,
                    showResultPreview = state.showResultPreview,
                    isLocked = state.isLocked,
                    seekRequest = state.seekRequest,
                    recentExport = state.recentExport,
                    strings = strings,
                    onPickVideo = onPickVideo,
                    onSelectQuickFormat = { format ->
                        onUpdateOptions { it.copy(postFormat = format) }
                    },
                    onSplitPositionChange = onSplitPositionChange,
                    onCropOffsetChange = { ox, oy ->
                        onUpdateOptions { it.copy(cropOffsetX = ox, cropOffsetY = oy) }
                    },
                    onZoomChange = onZoomChange,
                    onOpenRecent = onOpenRecent,
                    onShareRecent = onShareRecent,
                    onCaptureActionReady = { action -> captureFrameAction = action },
                    onSaveSnapshot = onSaveSnapshot,
                    onSnapshotFailed = onSnapshotFailed,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )

                if (state.source != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            if (!state.showResultPreview) {
                                MiniChipButton(
                                    icon = Icons.Rounded.Compare,
                                    label = strings.compare,
                                    active = state.splitCompareEnabled,
                                    onClick = onToggleSplitCompare,
                                )
                                MiniChipButton(
                                    icon = Icons.Rounded.CropFree,
                                    label = strings.safeZones,
                                    active = state.showSafeZones,
                                    onClick = onToggleSafeZones,
                                )
                            }
                            MiniChipButton(
                                icon = Icons.Rounded.ZoomIn,
                                label = if (state.previewZoom > 1.05f) "\u2066%.1fx\u2069".format(state.previewZoom) else strings.zoom,
                                active = state.previewZoom > 1.05f,
                                onClick = onToggleZoom,
                            )
                            if (captureFrameAction != null) {
                                MiniChipButton(
                                    icon = Icons.Rounded.PhotoCamera,
                                    label = strings.saveFrame,
                                    active = false,
                                    onClick = { captureFrameAction?.invoke() },
                                )
                            }
                        }

                        if (state.completedOutput != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MiniChipButton(
                                    icon = null,
                                    label = strings.original,
                                    active = !state.showResultPreview,
                                    onClick = { onSetShowResult(false) },
                                )
                                MiniChipButton(
                                    icon = null,
                                    label = strings.result,
                                    active = state.showResultPreview,
                                    onClick = { onSetShowResult(true) },
                                )
                            }
                        }
                    }

                    val meta = state.activePreviewMetadata
                    if (meta != null) {
                        val hdrTag = if (meta.isHdr) " · HDR" else ""
                        Text(
                            text = "\u2066${meta.displayWidth} × ${meta.displayHeight}\u2069 · \u2066%.1fs · %.0f fps%s\u2069".format(
                                meta.durationMs / 1000.0,
                                meta.frameRate,
                                hdrTag,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryText,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }

                    if (!state.showResultPreview && state.exportState is ExportState.Idle) {
                        val srcShort = minOf(state.source.displayWidth, state.source.displayHeight)
                        val isLandscapeSrc = state.source.displayWidth > state.source.displayHeight
                        val hintText = when {
                            isLandscapeSrc && state.options.postFormat == PostFormat.REEL && state.options.framing == FramingMode.CONTAIN ->
                                strings.recommendCoverHint
                            srcShort < 1080 && state.options.resolution == OutputResolution.FHD_1080P ->
                                strings.recommendUpscaleHint
                            else -> null
                        }
                        if (hintText != null) {
                            Text(
                                text = hintText,
                                style = MaterialTheme.typography.labelSmall,
                                color = accentText,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = state.snapshotStatus != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                val ok = state.snapshotStatus == SnapshotStatus.SAVED
                Text(
                    text = if (ok) strings.snapshotSaved else strings.snapshotFailed,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ok) StudioColors.Success else StudioColors.Danger,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 2.dp),
                )
            }

            ExportStatusStrip(
                exportState = state.exportState,
                source = state.source,
                strings = strings,
                onCancel = onCancelExport,
                onOpen = onOpenOutput,
                onShare = onShareOutput,
            )

            AnimatedVisibility(visible = state.error != null) {
                state.error?.let { err ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .border(0.5.dp, StudioColors.Danger.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = strings.formatError(err),
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.PureWhite,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismissError, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = strings.close,
                                tint = StudioColors.PureWhite,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(barBg)
                    .border(0.5.dp, barBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val (outW, outH) = state.targetDimensions()
                val currentPlan = state.currentPlan()
                val formatLabel = when (state.options.postFormat) {
                    PostFormat.ORIGINAL -> strings.originalFormat
                    PostFormat.REEL -> strings.reel
                    PostFormat.FEED -> strings.feed
                    PostFormat.STORY -> strings.story
                    PostFormat.LANDSCAPE -> strings.landscape
                }
                val estSummary = if (currentPlan != null) {
                    val estMb = (currentPlan.estimatedBytes / (1024.0 * 1024.0)).coerceAtLeast(0.1)
                    " · \u2066~%.1f MB\u2069".format(estMb)
                } else {
                    ""
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${state.options.platform.label} · $formatLabel · \u2066$outW × $outH\u2069$estSummary",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryText,
                    )
                    Text(
                        text = "${strings.presetLabel(state.options.preset)} · ${state.options.codec.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedText,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StudioDockItem(
                        icon = Icons.Rounded.Tune,
                        label = strings.processing,
                        selected = state.activeSheet == StudioSheet.PROCESSING,
                        enabled = !state.isLocked,
                        onClick = { onOpenSheet(StudioSheet.PROCESSING) },
                        modifier = Modifier.weight(1f),
                    )
                    StudioDockItem(
                        icon = Icons.Rounded.ArrowOutward,
                        label = strings.output,
                        selected = state.activeSheet == StudioSheet.OUTPUT,
                        enabled = !state.isLocked,
                        onClick = { onOpenSheet(StudioSheet.OUTPUT) },
                        modifier = Modifier.weight(1f),
                    )
                    if (state.source != null) {
                        StudioDockItem(
                            icon = Icons.Rounded.Add,
                            label = strings.replaceVideo,
                            selected = false,
                            enabled = !state.isLocked,
                            onClick = onPickVideo,
                            modifier = Modifier.weight(0.9f),
                        )
                    }
                }

                if (state.source != null || state.isImporting) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartExport()
                        },
                        enabled = !state.isLocked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentFill,
                            contentColor = StudioColors.PureWhite,
                            disabledContainerColor = if (isAmoledWhite) StudioColors.WhiteSurfaceActive else StudioColors.StudioSurfaceActive,
                            disabledContentColor = mutedText,
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                    ) {
                        Text(
                            text = if (state.isImporting) strings.preparing else strings.enhanceAndExport,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        when (state.activeSheet) {
            StudioSheet.PROCESSING -> ProcessingBottomSheet(
                options = state.options,
                source = state.source,
                capabilities = state.capabilities,
                customPresets = state.customPresets,
                strings = strings,
                onUpdateOptions = onUpdateOptions,
                onApplyPreset = onApplyPreset,
                onApplyCustomPreset = onApplyCustomPreset,
                onSaveCustomPreset = onSaveCustomPreset,
                onDeleteCustomPreset = onDeleteCustomPreset,
                onResetFilters = onResetFilters,
                onDismiss = onCloseSheet,
            )
            StudioSheet.OUTPUT -> OutputBottomSheet(
                options = state.options,
                source = state.source,
                capabilities = state.capabilities,
                plan = state.currentPlan(),
                strings = strings,
                onUpdateOptions = onUpdateOptions,
                onSeekPreview = onSeekPreview,
                onDismiss = onCloseSheet,
            )
            StudioSheet.EXPORTS -> RecentExportsBottomSheet(
                items = state.recentExports,
                strings = strings,
                onOpen = onOpenRecent,
                onShare = onShareRecent,
                onImport = onImportRecent,
                onDismiss = onCloseSheet,
            )
            StudioSheet.BATCH -> BatchQueueBottomSheet(
                items = state.batchQueue,
                isBatchRunning = state.isBatchRunning,
                strings = strings,
                onAddVideos = onPickBatchVideos,
                onRemoveItem = onRemoveBatchItem,
                onClearDone = onClearDoneBatch,
                onStartBatch = onStartBatchExport,
                onDismiss = onCloseSheet,
            )
            StudioSheet.NONE -> {}
        }
    }
}

@Composable
private fun MiniChipButton(
    icon: ImageVector?,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val isAmoledWhite = LocalAmoledWhite.current
    val accentBorder = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyAccent
    val accentText = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyTextDark
    val bg = when {
        active -> StudioColors.BurgundyMuted
        isAmoledWhite -> StudioColors.WhiteSurfaceElevated
        else -> StudioColors.StudioSurface
    }
    val border = when {
        active -> accentBorder
        isAmoledWhite -> StudioColors.WhiteBorder
        else -> StudioColors.StudioBorder
    }
    val textColor = when {
        active -> if (isAmoledWhite) accentText else StudioColors.PureWhite
        isAmoledWhite -> StudioColors.DarkTextMuted
        else -> StudioColors.MutedWhite
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(if (active) 0.8.dp else 0.5.dp, border, RoundedCornerShape(10.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) accentText else textColor,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun StudioDockItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val isAmoledWhite = LocalAmoledWhite.current
    val accentBg = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyAccent
    val accentText = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyTextDark
    val mutedText = if (isAmoledWhite) StudioColors.DarkTextMuted else StudioColors.MutedWhite
    val subtleIcon = if (isAmoledWhite) StudioColors.DarkTextSubtle else StudioColors.SubtleGray

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accentBg.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (!enabled) subtleIcon else if (selected) accentText else subtleIcon,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (!enabled) subtleIcon else if (selected) accentText else mutedText,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
