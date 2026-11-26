package com.xrtm8.upscale.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xrtm8.upscale.BuildConfig
import com.xrtm8.upscale.engine.LiveShaderParams
import com.xrtm8.upscale.engine.UpdateStatus
import com.xrtm8.upscale.model.AppLanguage
import com.xrtm8.upscale.model.ExportOptions
import com.xrtm8.upscale.model.ExportState
import com.xrtm8.upscale.model.PostFormat
import com.xrtm8.upscale.ui.components.OutputBottomSheet
import com.xrtm8.upscale.ui.components.ProcessingBottomSheet
import com.xrtm8.upscale.ui.components.VideoPreviewStage
import com.xrtm8.upscale.ui.theme.StudioColors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

@Composable
fun StudioScreen(
    state: StudioUiState,
    shaderParamsRef: AtomicReference<LiveShaderParams>,
    onPickVideo: () -> Unit,
    onToggleLanguage: () -> Unit,
    onOpenSheet: (StudioSheet) -> Unit,
    onCloseSheet: () -> Unit,
    onToggleSplitCompare: () -> Unit,
    onSplitPositionChange: (Float) -> Unit,
    onToggleSafeZones: () -> Unit,
    onSetShowResult: (Boolean) -> Unit,
    onUpdateOptions: ((ExportOptions) -> ExportOptions) -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit,
    onShareOutput: () -> Unit,
    onDismissError: () -> Unit,
    onCheckUpdate: () -> Unit,
    onStartUpdateDownload: () -> Unit,
    onInstallUpdate: () -> Unit,
    onDismissUpdateBanner: () -> Unit,
) {
    val strings = stringsFor(state.language)
    val layoutDirection = if (state.language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF15181E), StudioColors.Background, StudioColors.Background),
                    ),
                )
                .safeDrawingPadding(),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF191B1F))
                    .border(0.5.dp, StudioColors.Hairline)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF393C43), Color(0xFF1A1C21))),
                                )
                                .border(1.dp, Color(0x55EFF2F8), RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "U",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFF2F3F5),
                            )
                        }
                        Text(
                            text = "UPSCALE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.6.sp,
                            color = Color(0xFFE6E7EA),
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val hasReadyUpdate = state.updateStatus is UpdateStatus.Available ||
                        state.updateStatus is UpdateStatus.ReadyToInstall
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                1.dp,
                                if (hasReadyUpdate) StudioColors.Success.copy(alpha = 0.45f) else StudioColors.Hairline,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable(onClick = onCheckUpdate)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
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
                            text = "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasReadyUpdate) StudioColors.TextPrimary else StudioColors.TextSecondary,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, StudioColors.Hairline, RoundedCornerShape(8.dp))
                            .clickable(onClick = onToggleLanguage)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = if (state.language == AppLanguage.AR) "EN" else "عربي",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            if (state.updateBannerVisible) {
                UpdateNoticeStrip(
                    status = state.updateStatus,
                    strings = strings,
                    onDownload = onStartUpdateDownload,
                    onInstall = onInstallUpdate,
                    onDismiss = onDismissUpdateBanner,
                )
            }

            // Main stage
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // File name + aspect badge
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
                            tint = StudioColors.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = state.activePreviewMetadata?.name ?: strings.noVideoLoaded,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.options.postFormat.aspectLabel,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, StudioColors.Hairline, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                // Live video stage
                VideoPreviewStage(
                    previewUri = state.activePreviewUri,
                    metadata = state.source,
                    options = state.options,
                    shaderParamsRef = shaderParamsRef,
                    splitCompareEnabled = state.splitCompareEnabled,
                    splitPosition = state.splitPosition,
                    showSafeZones = state.showSafeZones,
                    showResultPreview = state.showResultPreview,
                    isLocked = state.isLocked,
                    strings = strings,
                    onPickVideo = onPickVideo,
                    onSplitPositionChange = onSplitPositionChange,
                    onCropOffsetChange = { ox, oy ->
                        onUpdateOptions { it.copy(cropOffsetX = ox, cropOffsetY = oy) }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )

                // Quick preview tools + Original/Result switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.source != null && !state.showResultPreview) {
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
                    }

                    if (state.completedOutput != null) {
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

                // Metadata strip
                val meta = state.activePreviewMetadata
                Text(
                    text = if (meta != null) {
                        val hdrTag = if (meta.isHdr) " · HDR" else ""
                        "${meta.displayWidth} × ${meta.displayHeight} · %.1fs · %.0f fps%s".format(
                            meta.durationMs / 1000.0,
                            meta.frameRate,
                            hdrTag,
                        )
                    } else {
                        strings.localNote
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.TextSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            // Export status strip
            ExportStatusStrip(
                exportState = state.exportState,
                strings = strings,
                onCancel = onCancelExport,
                onShare = onShareOutput,
            )

            // Error notice
            if (state.errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2A2421))
                        .border(0.5.dp, Color(0xFF655044))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEAD8C9),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismissError, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = strings.close,
                            tint = Color(0xFFEAD8C9),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // Bottom Dock
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1C20))
                    .border(0.5.dp, StudioColors.Hairline)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val (outW, outH) = state.targetDimensions()
                val formatLabel = when (state.options.postFormat) {
                    PostFormat.REEL -> strings.reel
                    PostFormat.FEED -> strings.feed
                    PostFormat.STORY -> strings.story
                    PostFormat.LANDSCAPE -> strings.landscape
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${state.options.platform.label} · $formatLabel · $outW × $outH",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioColors.TextSecondary,
                    )
                    Text(
                        text = state.options.codec.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioColors.TextMuted,
                    )
                }

                // Tool row: Processing + Output + Change Video
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ToolDockButton(
                        icon = Icons.Rounded.Tune,
                        label = strings.processing,
                        enabled = !state.isLocked,
                        onClick = { onOpenSheet(StudioSheet.PROCESSING) },
                        modifier = Modifier.weight(1f),
                    )
                    ToolDockButton(
                        icon = Icons.Rounded.ArrowOutward,
                        label = strings.output,
                        enabled = !state.isLocked,
                        onClick = { onOpenSheet(StudioSheet.OUTPUT) },
                        modifier = Modifier.weight(1f),
                    )
                    if (state.source != null) {
                        ToolDockButton(
                            icon = Icons.Rounded.Add,
                            label = strings.replaceVideo,
                            enabled = !state.isLocked,
                            onClick = onPickVideo,
                            modifier = Modifier.weight(0.9f),
                        )
                    }
                }

                // Primary CTA
                Button(
                    onClick = {
                        if (state.source == null) onPickVideo() else onStartExport()
                    },
                    enabled = !state.isLocked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioColors.PrimaryFill,
                        contentColor = StudioColors.PrimaryOnFill,
                        disabledContainerColor = Color(0xFF292D35),
                        disabledContentColor = Color(0xFF9EA6B4),
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(
                        text = when {
                            state.isImporting -> strings.preparing
                            state.source == null -> strings.importVideo
                            else -> strings.enhanceAndExport
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        when (state.activeSheet) {
            StudioSheet.PROCESSING -> ProcessingBottomSheet(
                options = state.options,
                source = state.source,
                capabilities = state.capabilities,
                strings = strings,
                onUpdateOptions = onUpdateOptions,
                onDismiss = onCloseSheet,
            )
            StudioSheet.OUTPUT -> OutputBottomSheet(
                options = state.options,
                source = state.source,
                capabilities = state.capabilities,
                plan = state.currentPlan(),
                strings = strings,
                onUpdateOptions = onUpdateOptions,
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) StudioColors.AccentSoft else Color(0xFF171A20))
            .border(
                1.dp,
                if (active) Color(0xFF8B919E) else StudioColors.Hairline,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) StudioColors.TextPrimary else StudioColors.TextSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) StudioColors.TextPrimary else StudioColors.TextSecondary,
        )
    }
}

@Composable
private fun ToolDockButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF21242A))
            .border(1.dp, StudioColors.Hairline, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) StudioColors.TextPrimary else StudioColors.TextMuted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) StudioColors.TextPrimary else StudioColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ExportStatusStrip(
    exportState: ExportState,
    strings: StudioStrings,
    onCancel: () -> Unit,
    onShare: () -> Unit,
) {
    if (exportState is ExportState.Idle) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C2028))
            .border(0.5.dp, StudioColors.Hairline)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (exportState) {
            ExportState.Preparing -> {
                Text(strings.preparing, style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = StudioColors.PrimaryFill,
                    trackColor = Color(0xFF373E4B),
                )
            }
            is ExportState.Running -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${strings.exporting} · ${(exportState.progress * 100f).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text(strings.cancel, style = MaterialTheme.typography.labelSmall)
                    }
                }
                LinearProgressIndicator(
                    progress = { exportState.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = StudioColors.PrimaryFill,
                    trackColor = Color(0xFF373E4B),
                )
            }
            is ExportState.Completed -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.exportComplete,
                            style = MaterialTheme.typography.labelMedium,
                            color = StudioColors.Success,
                        )
                        Text(
                            text = "${strings.savedToGallery} · ${exportState.outputName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = onShare,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2B313C),
                            contentColor = StudioColors.TextPrimary,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.shareVideo, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            ExportState.Cancelled -> {
                Text(
                    text = strings.exportCancelled,
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioColors.Warning,
                )
            }
            is ExportState.Failed -> {
                Text(
                    text = "${strings.exportFailed}: ${exportState.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.Danger,
                )
            }
            ExportState.Idle -> {}
        }
    }
}

@Composable
private fun UpdateNoticeStrip(
    status: UpdateStatus,
    strings: StudioStrings,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (status is UpdateStatus.Idle) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161A20))
            .border(0.5.dp, StudioColors.Hairline)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
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
                    imageVector = Icons.Rounded.SystemUpdateAlt,
                    contentDescription = null,
                    tint = if (status is UpdateStatus.Error) StudioColors.Danger else StudioColors.Success,
                    modifier = Modifier.size(15.dp),
                )
                val label = when (status) {
                    UpdateStatus.Checking -> strings.checkingUpdate
                    UpdateStatus.UpToDate -> strings.upToDate
                    is UpdateStatus.Available -> "${strings.updateAvailable} · v${status.info.versionName}"
                    is UpdateStatus.Downloading -> "${strings.downloadingUpdate} · ${(status.progress * 100f).roundToInt()}%"
                    is UpdateStatus.ReadyToInstall -> "${strings.updateReady} · v${status.info.versionName}"
                    is UpdateStatus.Error -> status.message
                    UpdateStatus.Idle -> ""
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (status) {
                    is UpdateStatus.Available -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(StudioColors.PrimaryFill)
                                .clickable(onClick = onDownload)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = strings.updateNow,
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioColors.PrimaryOnFill,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    is UpdateStatus.ReadyToInstall -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(StudioColors.Success)
                                .clickable(onClick = onInstall)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = strings.installUpdate,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF0C0E11),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    else -> {}
                }

                if (status !is UpdateStatus.Downloading) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = strings.close,
                            tint = StudioColors.TextSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        if (status is UpdateStatus.Downloading) {
            LinearProgressIndicator(
                progress = { status.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = StudioColors.Success,
                trackColor = Color(0xFF2B313C),
            )
        }
    }
}
