package com.therealsylva.roaches.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object RoachesColors {
    val Canvas = Color(0xFF090A0B)
    val Surface = Color(0xFF111315)
    val SurfaceQuiet = Color(0xFF181A1D)
    val Ink = Color(0xFFF2F0E9)
    val InkMuted = Color(0xFFAAA9A4)
    val InkFaint = Color(0xFF747570)
    val Crawl = Color(0xFFC47A45)
    val Error = Color(0xFFE36A6A)
    val Scrim = Color(0xD9000000)
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

private val Colors = darkColorScheme(
    primary = RoachesColors.Ink,
    onPrimary = RoachesColors.Canvas,
    secondary = RoachesColors.Crawl,
    onSecondary = RoachesColors.Canvas,
    background = RoachesColors.Canvas,
    onBackground = RoachesColors.Ink,
    surface = RoachesColors.Surface,
    onSurface = RoachesColors.Ink,
    surfaceVariant = RoachesColors.SurfaceQuiet,
    onSurfaceVariant = RoachesColors.InkMuted,
    error = RoachesColors.Error,
    onError = RoachesColors.Canvas,
    outline = Color(0xFF323438),
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
fun RoachesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
