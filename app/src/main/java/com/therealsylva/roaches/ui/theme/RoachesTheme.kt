package com.therealsylva.roaches.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class RoachesPalette(
    val canvas: Color,
    val surface: Color,
    val surfaceQuiet: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val crawl: Color,
    val error: Color,
)

private val DarkPalette = RoachesPalette(
    canvas = Color(0xFF090A0B),
    surface = Color(0xFF111315),
    surfaceQuiet = Color(0xFF181A1D),
    ink = Color(0xFFF2F0E9),
    inkMuted = Color(0xFFAAA9A4),
    inkFaint = Color(0xFF747570),
    crawl = Color(0xFFC47A45),
    error = Color(0xFFE36A6A),
)

private val LightPalette = RoachesPalette(
    canvas = Color(0xFFF4F0E8),
    surface = Color(0xFFFCFAF5),
    surfaceQuiet = Color(0xFFE6E0D7),
    ink = Color(0xFF181614),
    inkMuted = Color(0xFF625E58),
    inkFaint = Color(0xFF817A72),
    crawl = Color(0xFFA85F32),
    error = Color(0xFFB3261E),
)

private val LocalRoachesPalette = staticCompositionLocalOf { DarkPalette }

object RoachesColors {
    val Canvas: Color
        @Composable @ReadOnlyComposable get() = LocalRoachesPalette.current.canvas
    val Surface: Color
        @Composable @ReadOnlyComposable get() = LocalRoachesPalette.current.surface
    val SurfaceQuiet: Color
        @Composable @ReadOnlyComposable get() = LocalRoachesPalette.current.surfaceQuiet
    val Ink: Color
        @Composable @ReadOnlyComposable get() = LocalRoachesPalette.current.ink
    val InkMuted: Color
        @Composable @ReadOnlyComposable get() = LocalRoachesPalette.current.inkMuted
    val InkFaint: Color
        @Composable @ReadOnlyComposable get() = LocalRoachesPalette.current.inkFaint
    val Crawl: Color
        @Composable @ReadOnlyComposable get() = LocalRoachesPalette.current.crawl
    val Error: Color
        @Composable @ReadOnlyComposable get() = LocalRoachesPalette.current.error
    val Scrim: Color = Color(0xD9000000)
}

object RoachesSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object RoachesShapes {
    val Tight = RoundedCornerShape(6.dp)
    val Standard = RoundedCornerShape(12.dp)
}

private val DarkColors = darkColorScheme(
    primary = DarkPalette.ink,
    onPrimary = DarkPalette.canvas,
    secondary = DarkPalette.crawl,
    onSecondary = DarkPalette.canvas,
    background = DarkPalette.canvas,
    onBackground = DarkPalette.ink,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.ink,
    surfaceVariant = DarkPalette.surfaceQuiet,
    onSurfaceVariant = DarkPalette.inkMuted,
    error = DarkPalette.error,
    onError = DarkPalette.canvas,
    outline = Color(0xFF323438),
)

private val LightColors = lightColorScheme(
    primary = LightPalette.ink,
    onPrimary = LightPalette.canvas,
    secondary = LightPalette.crawl,
    onSecondary = LightPalette.canvas,
    background = LightPalette.canvas,
    onBackground = LightPalette.ink,
    surface = LightPalette.surface,
    onSurface = LightPalette.ink,
    surfaceVariant = LightPalette.surfaceQuiet,
    onSurfaceVariant = LightPalette.inkMuted,
    error = LightPalette.error,
    onError = LightPalette.canvas,
    outline = Color(0xFFBDB5AA),
)

private val Type = androidx.compose.material3.Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1.2).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
)

@Composable
fun RoachesTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalRoachesPalette provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Type,
            content = content,
        )
    }
}
