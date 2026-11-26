package com.xrtm8.upscale.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xrtm8.upscale.engine.MediaInspector
import com.xrtm8.upscale.engine.RecentExportItem
import com.xrtm8.upscale.model.DeviceCapabilities
import com.xrtm8.upscale.model.EnhancementPreset
import com.xrtm8.upscale.model.ExportOptions
import com.xrtm8.upscale.model.ExportPlan
import com.xrtm8.upscale.model.FramingMode
import com.xrtm8.upscale.model.OutputResolution
import com.xrtm8.upscale.model.PlatformPreset
import com.xrtm8.upscale.model.PostFormat
import com.xrtm8.upscale.model.QualityMode
import com.xrtm8.upscale.model.TargetFps
import com.xrtm8.upscale.model.VideoCodecChoice
import com.xrtm8.upscale.model.VideoMetadata
import com.xrtm8.upscale.ui.StudioStrings
import com.xrtm8.upscale.ui.theme.LocalAmoledWhite
import com.xrtm8.upscale.ui.theme.StudioColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProcessingBottomSheet(
    options: ExportOptions,
    source: VideoMetadata?,
    capabilities: DeviceCapabilities,
    strings: StudioStrings,
    onUpdateOptions: ((ExportOptions) -> ExportOptions) -> Unit,
    onApplyPreset: (EnhancementPreset) -> Unit,
    onResetFilters: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isAmoledWhite = LocalAmoledWhite.current
    val sheetBg = if (isAmoledWhite) StudioColors.WhiteSurface else StudioColors.StudioSurface
    val primaryText = if (isAmoledWhite) StudioColors.DarkText else StudioColors.PureWhite
    val secondaryText = if (isAmoledWhite) StudioColors.DarkTextMuted else StudioColors.MutedWhite
    val borderColor = if (isAmoledWhite) StudioColors.WhiteBorder else StudioColors.StudioBorder
    val accentFill = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyAccent
    val accentText = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyTextDark

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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.processing, style = MaterialTheme.typography.titleMedium)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioColors.BurgundyMuted)
                        .border(0.5.dp, borderColor, RoundedCornerShape(8.dp))
                        .clickable(onClick = onResetFilters)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = strings.resetFilters,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentText,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.presets, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        EnhancementPreset.NATURAL,
                        EnhancementPreset.CRISP_TEXT,
                        EnhancementPreset.LOW_LIGHT,
                        EnhancementPreset.CINEMA,
                    ).forEach { preset ->
                        ChoiceChip(
                            label = strings.presetLabel(preset),
                            selected = options.preset == preset,
                            onClick = { onApplyPreset(preset) },
                        )
                    }
                    if (options.preset == EnhancementPreset.CUSTOM) {
                        ChoiceChip(
                            label = strings.presetCustom,
                            selected = true,
                            onClick = {},
                        )
                    }
                }
            }

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
                title = strings.deblock,
                value = options.deblockStrength,
                onValueChange = { v ->
                    onUpdateOptions { it.copy(preset = EnhancementPreset.CUSTOM, deblockStrength = v) }
                },
                decreaseDescription = strings.decrease,
                increaseDescription = strings.increase,
            )
            StudioSlider(
                title = strings.denoise,
                value = options.denoiseStrength,
                onValueChange = { v ->
                    onUpdateOptions { it.copy(preset = EnhancementPreset.CUSTOM, denoiseStrength = v) }
                },
                decreaseDescription = strings.decrease,
                increaseDescription = strings.increase,
            )
            StudioSlider(
                title = strings.detailCas,
                value = options.detailStrength,
                onValueChange = { v ->
                    onUpdateOptions { it.copy(preset = EnhancementPreset.CUSTOM, detailStrength = v) }
                },
                decreaseDescription = strings.decrease,
                increaseDescription = strings.increase,
            )
            StudioSlider(
                title = strings.clarity,
                value = options.clarityStrength,
                onValueChange = { v ->
                    onUpdateOptions { it.copy(preset = EnhancementPreset.CUSTOM, clarityStrength = v) }
                },
                decreaseDescription = strings.decrease,
                increaseDescription = strings.increase,
            )
            StudioSlider(
                title = strings.vibrance,
                value = options.vibranceStrength,
                onValueChange = { v ->
                    onUpdateOptions { it.copy(preset = EnhancementPreset.CUSTOM, vibranceStrength = v) }
                },
                decreaseDescription = strings.decrease,
                increaseDescription = strings.increase,
            )
            StudioSlider(
                title = strings.exposure,
                value = options.exposureStrength,
                valueRange = -1f..1f,
                isBipolar = true,
                step = 0.08f,
                onValueChange = { v ->
                    onUpdateOptions { it.copy(preset = EnhancementPreset.CUSTOM, exposureStrength = v) }
                },
                decreaseDescription = strings.decrease,
                increaseDescription = strings.increase,
            )
            StudioSlider(
                title = strings.warmth,
                value = options.warmthStrength,
                valueRange = -1f..1f,
                isBipolar = true,
                step = 0.08f,
                onValueChange = { v ->
                    onUpdateOptions { it.copy(preset = EnhancementPreset.CUSTOM, warmthStrength = v) }
                },
                decreaseDescription = strings.decrease,
                increaseDescription = strings.increase,
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
                    containerColor = accentFill,
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
    val context = LocalContext.current
    val isAmoledWhite = LocalAmoledWhite.current
    val sheetBg = if (isAmoledWhite) StudioColors.WhiteSurface else StudioColors.StudioSurface
    val primaryText = if (isAmoledWhite) StudioColors.DarkText else StudioColors.PureWhite
    val secondaryText = if (isAmoledWhite) StudioColors.DarkTextMuted else StudioColors.MutedWhite
    val mutedText = if (isAmoledWhite) StudioColors.DarkTextSubtle else StudioColors.SubtleGray
    val borderColor = if (isAmoledWhite) StudioColors.WhiteBorder else StudioColors.StudioBorder
    val accentFill = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyAccent
    val accentText = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyTextDark
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
            val allowHevc = capabilities.supportsHevcFor(options.resolution)
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
                            enabled = allowHevc,
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
                Text(strings.frameRate, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ChoiceChip(
                        label = strings.fpsSource,
                        selected = options.targetFps == TargetFps.SOURCE,
                        onClick = { onUpdateOptions { it.copy(targetFps = TargetFps.SOURCE) } },
                        modifier = Modifier.weight(1f),
                    )
                    ChoiceChip(
                        label = strings.fps30,
                        selected = options.targetFps == TargetFps.FPS_30,
                        onClick = { onUpdateOptions { it.copy(targetFps = TargetFps.FPS_30) } },
                        modifier = Modifier.weight(1f),
                    )
                    ChoiceChip(
                        label = strings.fps60,
                        selected = options.targetFps == TargetFps.FPS_60,
                        onClick = { onUpdateOptions { it.copy(targetFps = TargetFps.FPS_60) } },
                        modifier = Modifier.weight(1f),
                    )
                }
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

            if (source == null || source.hasAudio) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.audioTrack, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ChoiceChip(
                            label = strings.keepAudio,
                            selected = !options.muteAudio,
                            onClick = { onUpdateOptions { it.copy(muteAudio = false) } },
                            modifier = Modifier.weight(1f),
                        )
                        ChoiceChip(
                            label = strings.muteAudio,
                            selected = options.muteAudio,
                            onClick = { onUpdateOptions { it.copy(muteAudio = true) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (source != null && source.durationMs > 1_000L) {
                val totalMs = source.durationMs.toFloat()
                val committedStart = options.trimStartMs.coerceIn(0L, source.durationMs - 500L)
                val committedEnd = (options.trimEndMs ?: source.durationMs).coerceIn(committedStart + 500L, source.durationMs)
                var draftStartMs by remember(source.uriString, committedStart) { mutableLongStateOf(committedStart) }
                var draftEndMs by remember(source.uriString, committedEnd) { mutableLongStateOf(committedEnd) }
                var thumbnails by remember(source.uriString) { mutableStateOf<List<Bitmap>>(emptyList()) }

                LaunchedEffect(committedStart, committedEnd) {
                    draftStartMs = committedStart
                    draftEndMs = committedEnd
                }

                LaunchedEffect(source.uriString, source.durationMs) {
                    val frames = withContext(Dispatchers.IO) {
                        MediaInspector.extractTrimThumbnails(context, source.uriString, source.durationMs, count = 7)
                    }
                    thumbnails = frames
                }

                DisposableEffect(source.uriString) {
                    onDispose {
                        thumbnails.forEach { it.recycle() }
                    }
                }

                val isShortTest = draftStartMs == 0L && draftEndMs <= 5_200L && source.durationMs > 6_000L

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(strings.trimClip, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (source.durationMs > 6_000L) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(StudioColors.BurgundyMuted)
                                        .border(0.5.dp, borderColor, RoundedCornerShape(7.dp))
                                        .clickable {
                                            val nextStart = 0L
                                            val nextEnd = if (isShortTest) source.durationMs else 5_000L
                                            draftStartMs = nextStart
                                            draftEndMs = nextEnd
                                            onUpdateOptions {
                                                it.copy(trimStartMs = nextStart, trimEndMs = nextEnd)
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        text = if (isShortTest) strings.fullLength else strings.fiveSecTest,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accentText,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Text(
                                text = "\u2066${formatMs(draftStartMs)} – ${formatMs(draftEndMs)} (${formatMs(draftEndMs - draftStartMs)})\u2069",
                                style = MaterialTheme.typography.labelSmall,
                                color = accentText,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    if (thumbnails.isNotEmpty()) {
                        val startFrac = (draftStartMs.toFloat() / totalMs).coerceIn(0f, 1f)
                        val endFrac = (draftEndMs.toFloat() / totalMs).coerceIn(startFrac, 1f)
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(0.5.dp, borderColor, RoundedCornerShape(10.dp)),
                        ) {
                            val fullW = maxWidth
                            Row(modifier = Modifier.fillMaxSize()) {
                                thumbnails.forEach { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                    )
                                }
                            }
                            if (startFrac > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .width(fullW * startFrac)
                                        .fillMaxHeight()
                                        .background(Color(0x99000000)),
                                )
                            }
                            if (endFrac < 0.99f) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(fullW * (1f - endFrac))
                                        .fillMaxHeight()
                                        .background(Color(0x99000000)),
                                )
                            }
                        }
                    }

                    RangeSlider(
                        value = draftStartMs.toFloat()..draftEndMs.toFloat(),
                        valueRange = 0f..totalMs,
                        onValueChange = { range ->
                            val newStart = range.start.toLong().coerceIn(0L, source.durationMs - 500L)
                            val newEnd = range.endInclusive.toLong().coerceIn(newStart + 500L, source.durationMs)
                            draftStartMs = newStart
                            draftEndMs = newEnd
                        },
                        onValueChangeFinished = {
                            onUpdateOptions { it.copy(trimStartMs = draftStartMs, trimEndMs = draftEndMs) }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = accentFill,
                            activeTrackColor = accentFill,
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
                        text = "\u2066~%.1f MB\u2069".format(estMb),
                        style = MaterialTheme.typography.labelMedium,
                        color = primaryText,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentFill,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentExportsBottomSheet(
    items: List<RecentExportItem>,
    strings: StudioStrings,
    onOpen: (RecentExportItem) -> Unit,
    onShare: (RecentExportItem) -> Unit,
    onImport: (RecentExportItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val isAmoledWhite = LocalAmoledWhite.current
    val sheetBg = if (isAmoledWhite) StudioColors.WhiteSurface else StudioColors.StudioSurface
    val itemBg = if (isAmoledWhite) StudioColors.WhiteSurfaceElevated else StudioColors.StudioSurfaceElevated
    val primaryText = if (isAmoledWhite) StudioColors.DarkText else StudioColors.PureWhite
    val secondaryText = if (isAmoledWhite) StudioColors.DarkTextMuted else StudioColors.MutedWhite
    val borderColor = if (isAmoledWhite) StudioColors.WhiteBorder else StudioColors.StudioBorder
    val accentFill = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyAccent
    val accentText = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyTextDark

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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(strings.exportsHistory, style = MaterialTheme.typography.titleMedium)

            if (items.isEmpty()) {
                Text(
                    text = strings.noRecentExports,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(itemBg)
                            .border(0.5.dp, borderColor, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = primaryText,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "\u2066${item.width}×${item.height} · ${formatMs(item.durationMs)} · ${formatSizeMb(item.sizeBytes)}\u2069",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(onClick = { onImport(item) }, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = strings.useAsInput,
                                    tint = secondaryText,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            IconButton(onClick = { onOpen(item) }, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = strings.openInGallery,
                                    tint = primaryText,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            IconButton(onClick = { onShare(item) }, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = strings.shareVideo,
                                    tint = accentText,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentFill,
                    contentColor = StudioColors.PureWhite,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Text(strings.close, fontWeight = FontWeight.Bold)
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
    val accentFill = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyAccent
    val accentText = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyTextDark
    val bg = when {
        !enabled -> if (isAmoledWhite) StudioColors.WhiteSurface else StudioColors.StudioBackground
        selected -> StudioColors.BurgundyMuted
        else -> if (isAmoledWhite) StudioColors.WhiteSurfaceElevated else StudioColors.StudioSurfaceElevated
    }
    val borderColor = when {
        !enabled -> if (isAmoledWhite) StudioColors.WhiteBorderSubtle else StudioColors.StudioBorder
        selected -> accentFill
        else -> if (isAmoledWhite) StudioColors.WhiteBorder else StudioColors.StudioBorder
    }
    val textColor = when {
        !enabled -> if (isAmoledWhite) StudioColors.DarkTextSubtle else StudioColors.SubtleGray
        selected -> if (isAmoledWhite) accentText else StudioColors.PureWhite
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
