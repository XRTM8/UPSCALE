package com.xrtm8.upscale.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xrtm8.upscale.engine.UpdateStatus
import com.xrtm8.upscale.model.ExportState
import com.xrtm8.upscale.model.VideoMetadata
import com.xrtm8.upscale.ui.StudioStrings
import com.xrtm8.upscale.ui.theme.LocalAmoledWhite
import com.xrtm8.upscale.ui.theme.StudioColors
import kotlin.math.roundToInt

@Composable
fun ExportStatusStrip(
    exportState: ExportState,
    source: VideoMetadata?,
    strings: StudioStrings,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    if (exportState is ExportState.Idle) return
    val isAmoledWhite = LocalAmoledWhite.current
    val cardBg = if (isAmoledWhite) StudioColors.WhiteSurface else StudioColors.StudioSurface
    val cardBorder = if (isAmoledWhite) StudioColors.WhiteBorder else StudioColors.StudioBorder
    val accentFill = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyAccent
    val secondaryText = if (isAmoledWhite) StudioColors.DarkTextMuted else StudioColors.MutedWhite

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(0.5.dp, cardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (exportState) {
            ExportState.Preparing -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.preparing, style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Text(strings.cancel, style = MaterialTheme.typography.labelSmall)
                    }
                }
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = accentFill,
                    trackColor = if (isAmoledWhite) StudioColors.WhiteSurfaceElevated else StudioColors.StudioSurfaceElevated,
                )
            }
            is ExportState.Running -> {
                val eta = exportState.estimatedRemainingSeconds(System.currentTimeMillis())
                val etaText = if (eta != null) " · ${strings.etaSeconds.format(eta)}" else ""
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${strings.exporting} · \u2066${(exportState.progress * 100f).roundToInt()}%\u2069$etaText",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Text(strings.cancel, style = MaterialTheme.typography.labelSmall)
                    }
                }
                LinearProgressIndicator(
                    progress = { exportState.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = accentFill,
                    trackColor = if (isAmoledWhite) StudioColors.WhiteSurfaceElevated else StudioColors.StudioSurfaceElevated,
                )
            }
            is ExportState.Completed -> {
                val outMeta = exportState.metadata
                val sourceSizeBytes = source?.sizeBytes ?: 0L
                val sizeCompare = if (sourceSizeBytes > 0L && outMeta.sizeBytes > 0L) {
                    " · \u2066${formatSizeMb(sourceSizeBytes)} → ${formatSizeMb(outMeta.sizeBytes)}\u2069"
                } else {
                    ""
                }
                val resCompare = if (source != null) {
                    "\u2066${source.displayWidth}×${source.displayHeight} → ${outMeta.displayWidth}×${outMeta.displayHeight}\u2069"
                } else {
                    "\u2066${outMeta.displayWidth}×${outMeta.displayHeight}\u2069"
                }
                val outMbps = if (outMeta.durationMs > 0L && outMeta.sizeBytes > 0L) {
                    (outMeta.sizeBytes * 8.0) / (outMeta.durationMs * 1_000.0)
                } else {
                    0.0
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = strings.exportComplete,
                            style = MaterialTheme.typography.labelMedium,
                            color = StudioColors.Success,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${strings.savedToGallery}$sizeCompare",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (outMbps > 0.1) "$resCompare · \u2066%.1f Mbps\u2069".format(outMbps) else resCompare,
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onOpen,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.openInGallery, style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = onShare,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentFill,
                                contentColor = StudioColors.PureWhite,
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.shareVideo, style = MaterialTheme.typography.labelSmall, color = StudioColors.PureWhite, fontWeight = FontWeight.Bold)
                        }
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
                    text = "${strings.exportFailed}: ${strings.formatError(exportState.error)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.Danger,
                )
            }
            ExportState.Idle -> {}
        }
    }
}

@Composable
fun UpdateNoticeStrip(
    status: UpdateStatus,
    strings: StudioStrings,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (status is UpdateStatus.Idle) return
    val isAmoledWhite = LocalAmoledWhite.current
    val stripBg = if (isAmoledWhite) StudioColors.WhiteSurface else StudioColors.StudioSurface
    val stripBorder = if (isAmoledWhite) StudioColors.WhiteBorder else StudioColors.StudioBorder
    val primaryText = if (isAmoledWhite) StudioColors.DarkText else StudioColors.PureWhite
    val secondaryText = if (isAmoledWhite) StudioColors.DarkTextMuted else StudioColors.MutedWhite
    val accentFill = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyAccent
    val accentText = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyTextDark

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(stripBg)
            .border(0.5.dp, stripBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    tint = if (status is UpdateStatus.Error) StudioColors.Danger else accentText,
                    modifier = Modifier.size(15.dp),
                )
                val label = when (status) {
                    UpdateStatus.Checking -> strings.checkingUpdate
                    UpdateStatus.UpToDate -> strings.upToDate
                    is UpdateStatus.Available -> "${strings.updateAvailable} · \u2066v${status.info.versionName}\u2069"
                    is UpdateStatus.Downloading -> "${strings.downloadingUpdate} · \u2066${(status.progress * 100f).roundToInt()}%\u2069"
                    is UpdateStatus.ReadyToInstall -> "${strings.updateReady} · \u2066v${status.info.versionName}\u2069"
                    is UpdateStatus.Error -> status.message
                    UpdateStatus.Idle -> ""
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = primaryText,
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
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentFill)
                                .clickable(onClick = onDownload)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = strings.updateNow,
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioColors.PureWhite,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    is UpdateStatus.ReadyToInstall -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(StudioColors.Success)
                                .clickable(onClick = onInstall)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = strings.installUpdate,
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioColors.AmoledBlack,
                                fontWeight = FontWeight.Bold,
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
                            tint = secondaryText,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        if (status is UpdateStatus.Available && status.info.releaseNotes.isNotBlank()) {
            val summary = status.info.releaseNotes
                .lineSequence()
                .map { it.trim().removePrefix("-").removePrefix("*").trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .take(2)
                .joinToString(" · ")
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
