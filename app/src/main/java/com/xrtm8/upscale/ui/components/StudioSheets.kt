package com.xrtm8.upscale.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xrtm8.upscale.model.DeviceCapabilities
import com.xrtm8.upscale.model.ExportOptions
import com.xrtm8.upscale.model.ExportPlan
import com.xrtm8.upscale.model.FramingMode
import com.xrtm8.upscale.model.OutputResolution
import com.xrtm8.upscale.model.PlatformPreset
import com.xrtm8.upscale.model.PostFormat
import com.xrtm8.upscale.model.QualityMode
import com.xrtm8.upscale.model.VideoCodecChoice
import com.xrtm8.upscale.model.VideoMetadata
import com.xrtm8.upscale.ui.StudioStrings
import com.xrtm8.upscale.ui.theme.LocalAmoledWhite
import com.xrtm8.upscale.ui.theme.StudioColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingBottomSheet(
    options: ExportOptions,
    source: VideoMetadata?,
    capabilities: DeviceCapabilities,
    strings: StudioStrings,
    onUpdateOptions: ((ExportOptions) -> ExportOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    val isAmoledWhite = LocalAmoledWhite.current
    val sheetBg = if (isAmoledWhite) StudioColors.WhiteSurface else StudioColors.StudioSurface
    val primaryText = if (isAmoledWhite) StudioColors.DarkText else StudioColors.PureWhite
    val secondaryText = if (isAmoledWhite) StudioColors.DarkTextMuted else StudioColors.MutedWhite
    val accent = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyAccent

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        contentColor = primaryText,
        scrimColor = Color(0x99000000),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(strings.processing, style = MaterialTheme.typography.titleMedium)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.qualityMode, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ChoiceChip(
                        label = strings.maximum,
                        selected = options.quality == QualityMode.MAXIMUM,
                        onClick = { onUpdateOptions { it.copy(quality = QualityMode.MAXIMUM) } },
                        modifier = Modifier.weight(1f),
                    )
                    ChoiceChip(
                        label = strings.balanced,
                        selected = options.quality == QualityMode.BALANCED,
                        onClick = { onUpdateOptions { it.copy(quality = QualityMode.BALANCED) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            StudioSlider(
                title = strings.denoise,
                value = options.denoiseStrength,
                onValueChange = { v -> onUpdateOptions { it.copy(denoiseStrength = v) } },
            )
            StudioSlider(
                title = strings.detailCas,
                value = options.detailStrength,
                onValueChange = { v -> onUpdateOptions { it.copy(detailStrength = v) } },
            )
            StudioSlider(
                title = strings.clarity,
                value = options.clarityStrength,
                onValueChange = { v -> onUpdateOptions { it.copy(clarityStrength = v) } },
            )

            Text(
                text = strings.gpuEngineNote,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
            )

            if (source?.isHdr == true) {
                Text(
                    text = strings.hdrToneMapNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.Success,
                )
            }

            if (capabilities.isThermallyThrottled) {
                Text(
                    text = strings.thermalWarning,
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.Warning,
                )
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = StudioColors.PureWhite,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Text(strings.done, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OutputBottomSheet(
    options: ExportOptions,
    source: VideoMetadata?,
    capabilities: DeviceCapabilities,
    plan: ExportPlan?,
    strings: StudioStrings,
    onUpdateOptions: ((ExportOptions) -> ExportOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    val isAmoledWhite = LocalAmoledWhite.current
    val sheetBg = if (isAmoledWhite) StudioColors.WhiteSurface else StudioColors.StudioSurface
    val primaryText = if (isAmoledWhite) StudioColors.DarkText else StudioColors.PureWhite
    val secondaryText = if (isAmoledWhite) StudioColors.DarkTextMuted else StudioColors.MutedWhite
    val mutedText = if (isAmoledWhite) StudioColors.DarkTextSubtle else StudioColors.SubtleGray
    val accent = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyAccent
    val trackInactive = if (isAmoledWhite) StudioColors.WhiteSurfaceElevated else StudioColors.StudioSurfaceElevated

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        contentColor = primaryText,
        scrimColor = Color(0x99000000),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(strings.output, style = MaterialTheme.typography.titleMedium)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.platform, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlatformPreset.entries.forEach { preset ->
                        ChoiceChip(
                            label = preset.label,
                            selected = options.platform == preset,
                            onClick = { onUpdateOptions { it.copy(platform = preset) } },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.postType, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                val allowedFormats = ExportOptions.allowedFormats(options.platform)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    allowedFormats.forEach { format ->
                        val title = when (format) {
                            PostFormat.REEL -> strings.reel
                            PostFormat.FEED -> strings.feed
                            PostFormat.STORY -> strings.story
                            PostFormat.LANDSCAPE -> strings.landscape
                        }
                        ChoiceChip(
                            label = title,
                            selected = options.postFormat == format,
                            onClick = { onUpdateOptions { it.copy(postFormat = format) } },
                        )
                    }
                }
            }

            val allow4k = capabilities.supports4k &&
                options.platform !in setOf(PlatformPreset.INSTAGRAM, PlatformPreset.FACEBOOK)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Text(strings.resolution, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(
                            label = "1080p",
                            selected = options.resolution == OutputResolution.FHD_1080P,
                            onClick = { onUpdateOptions { it.copy(resolution = OutputResolution.FHD_1080P) } },
                            modifier = Modifier.weight(1f),
                        )
                        ChoiceChip(
                            label = "4K",
                            selected = options.resolution == OutputResolution.UHD_4K,
                            enabled = allow4k,
                            onClick = { onUpdateOptions { it.copy(resolution = OutputResolution.UHD_4K) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Text(strings.codec, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(
                            label = "H.264",
                            selected = options.codec == VideoCodecChoice.H264,
                            onClick = { onUpdateOptions { it.copy(codec = VideoCodecChoice.H264) } },
                            modifier = Modifier.weight(1f),
                        )
                        ChoiceChip(
                            label = "HEVC",
                            selected = options.codec == VideoCodecChoice.HEVC,
                            enabled = capabilities.supports4kHevc,
                            onClick = { onUpdateOptions { it.copy(codec = VideoCodecChoice.HEVC) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (!allow4k) {
                Text(
                    text = strings.fourKUnavailable,
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedText,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.framing, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ChoiceChip(
                        label = strings.contain,
                        selected = options.framing == FramingMode.CONTAIN,
                        onClick = { onUpdateOptions { it.copy(framing = FramingMode.CONTAIN) } },
                        modifier = Modifier.weight(1f),
                    )
                    ChoiceChip(
                        label = strings.cover,
                        selected = options.framing == FramingMode.COVER,
                        onClick = { onUpdateOptions { it.copy(framing = FramingMode.COVER) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (options.framing == FramingMode.COVER) {
                    Text(
                        text = strings.cropPanHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText,
                    )
                }
            }

            if (source != null && source.durationMs > 1_000L) {
                val totalMs = source.durationMs.toFloat()
                val startMs = options.trimStartMs.coerceIn(0L, source.durationMs - 500L)
                val endMs = (options.trimEndMs ?: source.durationMs).coerceIn(startMs + 500L, source.durationMs)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(strings.trimClip, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                        Text(
                            text = "${formatMs(startMs)} – ${formatMs(endMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    RangeSlider(
                        value = startMs.toFloat()..endMs.toFloat(),
                        valueRange = 0f..totalMs,
                        onValueChange = { range ->
                            val newStart = range.start.toLong().coerceIn(0L, source.durationMs - 500L)
                            val newEnd = range.endInclusive.toLong().coerceIn(newStart + 500L, source.durationMs)
                            onUpdateOptions { it.copy(trimStartMs = newStart, trimEndMs = newEnd) }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = trackInactive,
                        ),
                    )
                }
            }

            if (plan != null) {
                val estMb = (plan.estimatedBytes / (1024.0 * 1024.0)).coerceAtLeast(0.1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(strings.estimatedSize, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                    Text(
                        text = "~%.1f MB".format(estMb),
                        style = MaterialTheme.typography.labelMedium,
                        color = primaryText,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = StudioColors.PureWhite,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Text(strings.done, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val isAmoledWhite = LocalAmoledWhite.current
    val accent = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyAccent
    val bg = when {
        !enabled -> if (isAmoledWhite) StudioColors.WhiteSurface else StudioColors.StudioBackground
        selected -> StudioColors.BurgundyMuted
        else -> if (isAmoledWhite) StudioColors.WhiteSurfaceElevated else StudioColors.StudioSurfaceElevated
    }
    val borderColor = when {
        !enabled -> if (isAmoledWhite) StudioColors.WhiteBorderSubtle else StudioColors.StudioBorder
        selected -> accent
        else -> if (isAmoledWhite) StudioColors.WhiteBorder else StudioColors.StudioBorder
    }
    val textColor = when {
        !enabled -> if (isAmoledWhite) StudioColors.DarkTextSubtle else StudioColors.SubtleGray
        selected -> if (isAmoledWhite) accent else StudioColors.PureWhite
        else -> if (isAmoledWhite) StudioColors.DarkTextMuted else StudioColors.MutedWhite
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(if (selected) 1.dp else 0.5.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
        )
    }
}
