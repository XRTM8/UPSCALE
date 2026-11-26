package com.xrtm8.upscale.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.xrtm8.upscale.R

object StudioColors {
    // Pure AMOLED Dark Surfaces
    val AmoledBlack = Color(0xFF000000)
    val StudioBackground = Color(0xFF0A0B0E)
    val StudioSurface = Color(0xFF14151B)
    val StudioSurfaceElevated = Color(0xFF1E2028)
    val StudioSurfaceActive = Color(0xFF282A36)

    // Pure AMOLED White Surfaces
    val AmoledWhite = Color(0xFFFFFFFF)
    val WhiteSurface = Color(0xFFF6F7F9)
    val WhiteSurfaceElevated = Color(0xFFECEEF2)
    val WhiteSurfaceActive = Color(0xFFDFE2E8)

    // Typography Hierarchy
    val PureWhite = Color(0xFFF5F6F8)
    val MutedWhite = Color(0xFFA1A3AF)
    val SubtleGray = Color(0xFF636674)

    val DarkText = Color(0xFF0F1014)
    val DarkTextMuted = Color(0xFF5A606E)
    val DarkTextSubtle = Color(0xFF8B92A0)

    // Velvet Burgundy Luxury Accent
    val BurgundyAccent = Color(0xFF9E1B42)
    val BurgundyTextDark = Color(0xFFE86488)
    val BurgundyDeep = Color(0xFF72142B)
    val BurgundyWarm = Color(0xFF8B1538)
    val BurgundyMuted = Color(0x249E1B42)
    val BurgundyBorderActive = Color(0x669E1B42)
    val BurgundyGlow = Color(0xFF220810)

    // Borders & Glass
    val StudioBorder = Color(0x14FFFFFF)
    val StudioBorderSubtle = Color(0x24FFFFFF)
    val WhiteBorder = Color(0x1F000000)
    val WhiteBorderSubtle = Color(0x14000000)
    val GlassBackground = Color(0xD90A0B0E)
    val GlassBackgroundWhite = Color(0xF4FFFFFF)

    // Functional Status
    val Success = Color(0xFF4CA87B)
    val Warning = Color(0xFFD79C4E)
    val Danger = Color(0xFFD96A5E)
}

val LocalAmoledWhite = staticCompositionLocalOf { false }

val StudioFontFamily = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.SemiBold),
    Font(R.font.tajawal_bold, FontWeight.Bold),
)

private val DarkStudioColorScheme = darkColorScheme(
    primary = StudioColors.BurgundyAccent,
    secondary = StudioColors.BurgundyWarm,
    background = StudioColors.AmoledBlack,
    surface = StudioColors.StudioSurface,
    surfaceVariant = StudioColors.StudioSurfaceElevated,
    onPrimary = StudioColors.PureWhite,
    onSecondary = StudioColors.PureWhite,
    onBackground = StudioColors.PureWhite,
    onSurface = StudioColors.PureWhite,
    onSurfaceVariant = StudioColors.MutedWhite,
    outline = StudioColors.StudioBorder,
    error = StudioColors.Danger,
)

private val LightStudioColorScheme = lightColorScheme(
    primary = StudioColors.BurgundyDeep,
    secondary = StudioColors.BurgundyWarm,
    background = StudioColors.AmoledWhite,
    surface = StudioColors.WhiteSurface,
    surfaceVariant = StudioColors.WhiteSurfaceElevated,
    onPrimary = StudioColors.AmoledWhite,
    onSecondary = StudioColors.AmoledWhite,
    onBackground = StudioColors.DarkText,
    onSurface = StudioColors.DarkText,
    onSurfaceVariant = StudioColors.DarkTextMuted,
    outline = StudioColors.WhiteBorder,
    error = StudioColors.Danger,
)

private fun studioTypography(isAmoledWhite: Boolean): Typography {
    val primary = if (isAmoledWhite) StudioColors.DarkText else StudioColors.PureWhite
    val secondary = if (isAmoledWhite) StudioColors.DarkTextMuted else StudioColors.MutedWhite
    return Typography(
        displayLarge = TextStyle(fontFamily = StudioFontFamily, fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 46.sp, color = primary),
        displayMedium = TextStyle(fontFamily = StudioFontFamily, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp, color = primary),
        displaySmall = TextStyle(fontFamily = StudioFontFamily, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp, color = primary),
        headlineLarge = TextStyle(fontFamily = StudioFontFamily, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp, color = primary),
        headlineMedium = TextStyle(fontFamily = StudioFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp, color = primary),
        headlineSmall = TextStyle(fontFamily = StudioFontFamily, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp, color = primary),
        titleLarge = TextStyle(fontFamily = StudioFontFamily, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp, color = primary),
        titleMedium = TextStyle(fontFamily = StudioFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp, color = primary),
        titleSmall = TextStyle(fontFamily = StudioFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, color = primary),
        bodyLarge = TextStyle(fontFamily = StudioFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp, color = primary),
        bodyMedium = TextStyle(fontFamily = StudioFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp, color = primary),
        bodySmall = TextStyle(fontFamily = StudioFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 17.sp, color = secondary),
        labelLarge = TextStyle(fontFamily = StudioFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp, color = primary),
        labelMedium = TextStyle(fontFamily = StudioFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, color = primary),
        labelSmall = TextStyle(fontFamily = StudioFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp, color = secondary),
    )
}

@Composable
fun UpscaleStudioTheme(
    isAmoledWhite: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAmoledWhite provides isAmoledWhite) {
        MaterialTheme(
            colorScheme = if (isAmoledWhite) LightStudioColorScheme else DarkStudioColorScheme,
            typography = studioTypography(isAmoledWhite),
            content = content,
        )
    }
}
