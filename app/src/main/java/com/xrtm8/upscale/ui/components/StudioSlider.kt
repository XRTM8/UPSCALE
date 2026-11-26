package com.xrtm8.upscale.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xrtm8.upscale.ui.theme.LocalAmoledWhite
import com.xrtm8.upscale.ui.theme.StudioColors
import kotlin.math.roundToInt

/**
 * Precision studio slider modeled after Athar's visual language:
 * numeric pill badge, +/- micro-adjust controls, and tactile haptic feedback.
 */
@Composable
fun StudioSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    step: Float = 0.05f,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    isBipolar: Boolean = false,
    decreaseDescription: String? = null,
    increaseDescription: String? = null,
) {
    val haptic = LocalHapticFeedback.current
    val isAmoledWhite = LocalAmoledWhite.current
    val primaryText = if (isAmoledWhite) StudioColors.DarkText else StudioColors.PureWhite
    val secondaryText = if (isAmoledWhite) StudioColors.DarkTextMuted else StudioColors.MutedWhite
    val subtleIcon = if (isAmoledWhite) StudioColors.DarkTextSubtle else StudioColors.SubtleGray
    val surfaceColor = if (isAmoledWhite) StudioColors.WhiteSurfaceElevated else StudioColors.StudioSurface
    val borderColor = if (isAmoledWhite) StudioColors.WhiteBorder else StudioColors.StudioBorder
    val accentTrack = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyAccent
    val accentText = if (isAmoledWhite) StudioColors.BurgundyDeep else StudioColors.BurgundyTextDark

    val displayText = if (isBipolar) {
        val pct = (value * 100f).roundToInt()
        when {
            pct > 0 -> "\u2066+$pct\u2069"
            pct < 0 -> "\u2066$pct\u2069"
            else -> "0"
        }
    } else {
        "${(value * 100f).roundToInt()}%"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentText,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = title,
                    color = primaryText,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(surfaceColor)
                    .border(0.5.dp, borderColor, RoundedCornerShape(10.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable {
                            val next = (value - step).coerceIn(valueRange.start, valueRange.endInclusive)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onValueChange(next)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = decreaseDescription?.let { "$it $title" },
                        tint = if (value > valueRange.start + 0.001f) secondaryText else subtleIcon,
                        modifier = Modifier.size(13.dp),
                    )
                }

                Text(
                    text = displayText,
                    color = accentText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable {
                            val next = (value + step).coerceIn(valueRange.start, valueRange.endInclusive)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onValueChange(next)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = increaseDescription?.let { "$it $title" },
                        tint = if (value < valueRange.endInclusive - 0.001f) secondaryText else subtleIcon,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }

        Slider(
            value = value,
            valueRange = valueRange,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = accentTrack,
                activeTrackColor = accentTrack,
                inactiveTrackColor = surfaceColor,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
        )
    }
}
