package com.edgecue.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Low-Glare OLED Palette — Solid Luxury & High-Contrast Hardware Specification
val OledBackground = Color(0xFF07080B)       // surface-canvas: Deepest root canvas; true solid pitch obsidian
val OledSurface = Color(0xFF0E1118)          // surface-card-base: Solid midnight slate cards & modules
val OledSurfaceHigh = Color(0xFF161B26)      // surface-card-elevated: Solid elevated titanium overlays & callouts
val OledSurfaceElevated = Color(0xFF1E2433)  // surface-card-subtle: Solid surface wells, secondary chips, active turns
val OledSurfaceInput = Color(0xFF0A0C11)     // surface-input: Deep inset solid wells & code block cards

val OledBorder = Color(0xFF1D2230)           // border-hairline: Solid 0.5dp machined hardware edge (no milky haze)
val OledBorderSubtle = Color(0xFF2A3247)     // border-subtle: Solid interactive outline on focus/hover
val OledBorderHigh = Color(0xFF3E4A68)       // border-selected: Solid active tab pill outline & focused inputs

val OledTextPrimary = Color(0xFFFFFFFF)      // text-primary: 100% Solid White headings, active questions, STAR keywords
val OledTextSecondary = Color(0xFFCBD5E1)    // text-secondary: Solid crisp silver teleprompter stream body & descriptions
val OledTextMuted = Color(0xFF8892B0)        // text-tertiary: Solid slate steel section overlines, TTFT metadata, disclaimers
val OledTextWhisper = Color(0xFF5A647E)      // text-whisper: Solid subtle steel hardware parameters & disabled tags
val OledTextInverted = Color(0xFF000000)     // text-inverted: Pure pitch black typography for solid white buttons

// Primary Action Tokens (Option A: Solid Pure White Surface)
val OledPrimaryAction = Color(0xFFFFFFFF)       // btn-primary-bg: Solid pure white execution CTA (19.5:1 contrast)
val OledPrimaryActionPressed = Color(0xFFE2E8F0)// btn-primary-pressed: Active tactile feedback

// Solid Radiant Jewel State & Hardware Matrix
val OledAccentIndigo = Color(0xFF6366F1)     // Solid Electric Indigo: Hardware acceleration & LiteRT indicators
val OledAccentIndigoSoft = Color(0xFF6366F1) // Solid Electric Indigo: Overlines, metric badges, slider tracks, links
val OledStatusEmerald = Color(0xFF30A46C)    // Solid Botanical Pine Emerald: Calm, non-neon solid listening indicator & bullets
val OledStatusAmber = Color(0xFFFFA800)      // Solid Radiant Amber Gold: Synthesizing, paused, thermal warning
val OledStatusCoral = Color(0xFFFF453A)      // Solid Precision Crimson Coral: Error banners & destructive stop controls
val OledStatusNeutral = Color(0xFF5A647E)    // Solid Slate Steel: Inactive state dots & badges

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
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.0.sp
    )
)

private val OledColorScheme = darkColorScheme(
    primary = OledPrimaryAction,
    onPrimary = OledTextInverted,
    primaryContainer = OledSurfaceHigh,
    onPrimaryContainer = OledTextPrimary,
    secondary = OledStatusEmerald,
    onSecondary = OledTextInverted,
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
