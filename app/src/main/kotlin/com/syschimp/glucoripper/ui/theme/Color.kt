package com.syschimp.glucoripper.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Glucoripper "Re-Diary" redesign palette — neutral monochrome surfaces with
// status colors carrying all chromatic weight. Mapped 1:1 from the design
// system tokens (project/redesign/tokens.css).

// Status colors — fixed across light/dark/wear. Match tokens.css exactly.
val GlucoseLow = Color(0xFFE5484D)
val GlucoseInRange = Color(0xFF30A46C)
val GlucoseElevated = Color(0xFFF5A524)
val GlucoseHigh = Color(0xFFE5484D)

// Light tokens
private val RdLightBg          = Color(0xFFF7F7F5)
private val RdLightSurface     = Color(0xFFFFFFFF)
private val RdLightElev1       = Color(0xFFFFFFFF)
private val RdLightElev2       = Color(0xFFFFFFFF)
private val RdLightElev3       = Color(0xFFF0F0EE)
private val RdLightLine        = Color(0xFFE3E3DF)
private val RdLightLineSoft    = Color(0xFFECECE8)
private val RdLightFg          = Color(0xFF14171A)
private val RdLightFgMuted     = Color(0xFF5C6166)
private val RdLightFgSubtle    = Color(0xFF8A9096)
private val RdLightFgFaint     = Color(0xFFC8CCD0)
private val RdLightAccent      = Color(0xFF14171A)
private val RdLightAccentOn    = Color(0xFFFFFFFF)
private val RdLightErrorBg     = Color(0xFFFBE4E5)
private val RdLightAmberBg     = Color(0xFFFDEFD8)
private val RdLightOkBg        = Color(0xFFE2F2EA)

// Dark tokens
private val RdDarkBg           = Color(0xFF0B0D0F)
private val RdDarkElev1        = Color(0xFF14171A)
private val RdDarkElev2        = Color(0xFF1B1F22)
private val RdDarkElev3        = Color(0xFF23272B)
private val RdDarkLine         = Color(0xFF2A2F33)
private val RdDarkLineSoft     = Color(0xFF1F2326)
private val RdDarkFg           = Color(0xFFECEEF0)
private val RdDarkFgMuted      = Color(0xFFA0A6AB)
private val RdDarkFgSubtle     = Color(0xFF6B7177)
private val RdDarkFgFaint      = Color(0xFF3F454A)
private val RdDarkAccent       = Color(0xFFECEEF0)
private val RdDarkAccentOn     = Color(0xFF0B0D0F)
private val RdDarkErrorBg      = Color(0xFF3B1A1D)
private val RdDarkAmberBg      = Color(0xFF3E2E14)
private val RdDarkOkBg         = Color(0xFF12281F)

val LightColors = lightColorScheme(
    primary = RdLightAccent,
    onPrimary = RdLightAccentOn,
    primaryContainer = RdLightElev3,
    onPrimaryContainer = RdLightFg,
    secondary = RdLightAccent,
    onSecondary = RdLightAccentOn,
    secondaryContainer = RdLightElev3,
    onSecondaryContainer = RdLightFg,
    tertiary = RdLightAccent,
    onTertiary = RdLightAccentOn,
    tertiaryContainer = RdLightAmberBg,
    onTertiaryContainer = RdLightFg,
    error = GlucoseLow,
    onError = Color.White,
    errorContainer = RdLightErrorBg,
    onErrorContainer = GlucoseLow,
    background = RdLightBg,
    onBackground = RdLightFg,
    surface = RdLightBg,
    onSurface = RdLightFg,
    surfaceVariant = RdLightElev3,
    onSurfaceVariant = RdLightFgMuted,
    surfaceContainerLowest = RdLightSurface,
    surfaceContainerLow = RdLightElev1,
    surfaceContainer = RdLightElev1,
    surfaceContainerHigh = RdLightElev3,
    surfaceContainerHighest = RdLightElev3,
    outline = RdLightLine,
    outlineVariant = RdLightLineSoft,
)

val DarkColors = darkColorScheme(
    primary = RdDarkAccent,
    onPrimary = RdDarkAccentOn,
    primaryContainer = RdDarkElev1,
    onPrimaryContainer = RdDarkFg,
    secondary = RdDarkAccent,
    onSecondary = RdDarkAccentOn,
    secondaryContainer = RdDarkElev1,
    onSecondaryContainer = RdDarkFg,
    tertiary = RdDarkAccent,
    onTertiary = RdDarkAccentOn,
    tertiaryContainer = RdDarkAmberBg,
    onTertiaryContainer = RdDarkFg,
    error = GlucoseLow,
    onError = Color.White,
    errorContainer = RdDarkErrorBg,
    onErrorContainer = GlucoseLow,
    background = RdDarkBg,
    onBackground = RdDarkFg,
    surface = RdDarkBg,
    onSurface = RdDarkFg,
    surfaceVariant = RdDarkElev2,
    onSurfaceVariant = RdDarkFgMuted,
    surfaceContainerLowest = RdDarkBg,
    surfaceContainerLow = RdDarkElev1,
    surfaceContainer = RdDarkElev1,
    surfaceContainerHigh = RdDarkElev2,
    surfaceContainerHighest = RdDarkElev3,
    outline = RdDarkLine,
    outlineVariant = RdDarkLineSoft,
)

// Soft variants used for chips, banners, and target-band tints.
object RdStatusSoft {
    val LowLight = RdLightErrorBg
    val LowDark = RdDarkErrorBg
    val AmberLight = RdLightAmberBg
    val AmberDark = RdDarkAmberBg
    val OkLight = RdLightOkBg
    val OkDark = RdDarkOkBg
}

// Subtle text steps — exposed for screens that need finer-grain hierarchy than
// onSurface / onSurfaceVariant offer (e.g. captions under big numerics).
object RdText {
    val SubtleLight = RdLightFgSubtle
    val SubtleDark = RdDarkFgSubtle
    val FaintLight = RdLightFgFaint
    val FaintDark = RdDarkFgFaint
}
