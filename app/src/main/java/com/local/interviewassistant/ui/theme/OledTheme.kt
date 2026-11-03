package com.local.interviewassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Low-Glare OLED Palette — Refined for Minimal Elegance & Tonal Depth
val OledBackground = Color(0xFF06070A)
val OledSurface = Color(0xFF0E1017)
val OledSurfaceHigh = Color(0xFF161922)
@Suppress("unused")
val OledSurfaceElevated = Color(0xFF1E222E)

val OledBorder = Color(0xFF1C202B)
@Suppress("unused")
val OledBorderSubtle = Color(0xFF151821)
val OledBorderHigh = Color(0xFF282E3E)

val OledTextPrimary = Color(0xFFF3F4F6)
val OledTextSecondary = Color(0xFF9CA3AF)
val OledTextMuted = Color(0xFF646B78)

val OledAccentIndigo = Color(0xFF6366F1)
val OledAccentIndigoSoft = Color(0xFF818CF8)
val OledStatusEmerald = Color(0xFF10B981)
val OledStatusAmber = Color(0xFFF59E0B)
val OledStatusCoral = Color(0xFFEF4444)

/**
 * Strict 4-Size, 2-Weight Typography System:
 * - Sizes: 20.sp (Headline/Prompter), 15.sp (Title), 13.sp (Body), 11.sp (Label/Micro)
 * - Weights: FontWeight.Normal (400) for body/descriptions, FontWeight.SemiBold (600) for titles/CTAs/badges
 */
val OledTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.15.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

private val OledColorScheme = darkColorScheme(
    primary = OledAccentIndigo,
    onPrimary = Color.White,
    primaryContainer = OledSurfaceHigh,
    onPrimaryContainer = OledTextPrimary,
    secondary = OledStatusEmerald,
    onSecondary = Color.Black,
    background = OledBackground,
    onBackground = OledTextPrimary,
    surface = OledSurface,
    onSurface = OledTextPrimary,
    surfaceVariant = OledSurfaceHigh,
    onSurfaceVariant = OledTextSecondary,
    outline = OledBorder,
    outlineVariant = OledBorderHigh,
    error = OledStatusCoral,
    onError = Color.White
)

@Composable
fun OledTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OledColorScheme,
        typography = OledTypography,
        content = content
    )
}
