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

// Low-Glare OLED Stealth Palette — Refined for Minimal Elegance & Tonal Depth
val StealthBackground = Color(0xFF06070A)
val StealthSurface = Color(0xFF0E1017)
val StealthSurfaceHigh = Color(0xFF161922)
val StealthSurfaceElevated = Color(0xFF1E222E)

val StealthBorder = Color(0xFF1C202B)
val StealthBorderSubtle = Color(0xFF151821)
val StealthBorderHigh = Color(0xFF282E3E)

val StealthTextPrimary = Color(0xFFF3F4F6)
val StealthTextSecondary = Color(0xFF9CA3AF)
val StealthTextMuted = Color(0xFF646B78)

val StealthAccentIndigo = Color(0xFF6366F1)
val StealthAccentIndigoSoft = Color(0xFF818CF8)
val StealthStatusEmerald = Color(0xFF10B981)
val StealthStatusAmber = Color(0xFFF59E0B)
val StealthStatusCoral = Color(0xFFEF4444)

val StealthTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.15.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp
    )
)

private val StealthColorScheme = darkColorScheme(
    primary = StealthAccentIndigo,
    onPrimary = Color.White,
    primaryContainer = StealthSurfaceHigh,
    onPrimaryContainer = StealthTextPrimary,
    secondary = StealthStatusEmerald,
    onSecondary = Color.Black,
    background = StealthBackground,
    onBackground = StealthTextPrimary,
    surface = StealthSurface,
    onSurface = StealthTextPrimary,
    surfaceVariant = StealthSurfaceHigh,
    onSurfaceVariant = StealthTextSecondary,
    outline = StealthBorder,
    outlineVariant = StealthBorderHigh,
    error = StealthStatusCoral,
    onError = Color.White
)

@Composable
fun StealthOledTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StealthColorScheme,
        typography = StealthTypography,
        content = content
    )
}
