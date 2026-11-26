package com.xrtm8.upscale.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object StudioColors {
    val Background = Color(0xFF0C0E11)
    val Surface = Color(0xFF15181E)
    val Glass = Color(0xDE1C1E23)
    val GlassElevated = Color(0xEC22252C)
    val Hairline = Color(0x24EBEFF7)
    val HairlineStrong = Color(0x3DF1F3F9)
    val TextPrimary = Color(0xFFE0E1E4)
    val TextSecondary = Color(0xFFA6AAB3)
    val TextMuted = Color(0xFF7D828D)
    val PrimaryFill = Color(0xFFE6E8ED)
    val PrimaryOnFill = Color(0xFF14171D)
    val AccentSoft = Color(0xFF353B46)
    val Success = Color(0xFF6BD19E)
    val Warning = Color(0xFFE4B96C)
    val Danger = Color(0xFFE28578)
}

private val StudioColorScheme = darkColorScheme(
    primary = StudioColors.PrimaryFill,
    onPrimary = StudioColors.PrimaryOnFill,
    secondary = StudioColors.AccentSoft,
    onSecondary = StudioColors.TextPrimary,
    background = StudioColors.Background,
    onBackground = StudioColors.TextPrimary,
    surface = StudioColors.Surface,
    onSurface = StudioColors.TextPrimary,
    surfaceVariant = StudioColors.Glass,
    onSurfaceVariant = StudioColors.TextSecondary,
    outline = StudioColors.Hairline,
    error = StudioColors.Danger,
)

private val StudioTypography = Typography(
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp,
        color = StudioColors.TextPrimary,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
        color = StudioColors.TextPrimary,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 17.sp,
        color = StudioColors.TextSecondary,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
        color = StudioColors.TextPrimary,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 14.sp,
        color = StudioColors.TextSecondary,
    ),
)

@Composable
fun UpscaleStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StudioColorScheme,
        typography = StudioTypography,
        content = content,
    )
}
