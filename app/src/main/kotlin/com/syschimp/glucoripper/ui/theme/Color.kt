package com.syschimp.glucoripper.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Glucoripper brand palette — a clinical teal paired with a deep indigo and a
// warm amber accent. These are the fallback colours used when Android dynamic
// colour isn't available (pre-Android 12 or user disabled Material You).

private val Teal10 = Color(0xFF001F24)
private val Teal20 = Color(0xFF00363D)
private val Teal30 = Color(0xFF004F58)
private val Teal40 = Color(0xFF006874)
private val Teal80 = Color(0xFF4FD8EB)
private val Teal90 = Color(0xFFA2EEFF)

private val Indigo20 = Color(0xFF1A237E)
private val Indigo30 = Color(0xFF283593)
private val Indigo40 = Color(0xFF3949AB)
private val Indigo80 = Color(0xFFB7C3FF)
private val Indigo90 = Color(0xFFDEE0FF)

private val Amber30 = Color(0xFF6B4E00)
private val Amber40 = Color(0xFF8A6600)
private val Amber80 = Color(0xFFFFD166)
private val Amber90 = Color(0xFFFFE8B3)

private val NeutralLightBg = Color(0xFFFBFCFF)
private val NeutralLightSurface = Color(0xFFF3F5F9)
private val NeutralLightSurfaceVariant = Color(0xFFDCE3E9)
private val NeutralLightOnSurface = Color(0xFF111417)
private val NeutralLightOnSurfaceVariant = Color(0xFF41484D)

private val NeutralDarkBg = Color(0xFF0E1113)
private val NeutralDarkSurface = Color(0xFF161A1D)
private val NeutralDarkSurfaceContainer = Color(0xFF1C2125)
private val NeutralDarkSurfaceContainerLow = Color(0xFF191D20)
private val NeutralDarkSurfaceVariant = Color(0xFF40484C)
private val NeutralDarkOnSurface = Color(0xFFE1E3E6)
private val NeutralDarkOnSurfaceVariant = Color(0xFFBFC8CD)

private val Error40 = Color(0xFFB3261E)
private val Error80 = Color(0xFFF2B8B5)
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val ErrorContainerDark = Color(0xFF8C1D18)

val LightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal10,
    secondary = Indigo40,
    onSecondary = Color.White,
    secondaryContainer = Indigo90,
    onSecondaryContainer = Indigo20,
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber30,
    error = Error40,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF410002),
    background = NeutralLightBg,
    onBackground = NeutralLightOnSurface,
    surface = NeutralLightBg,
    onSurface = NeutralLightOnSurface,
    surfaceVariant = NeutralLightSurfaceVariant,
    onSurfaceVariant = NeutralLightOnSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F9FC),
    surfaceContainer = NeutralLightSurface,
    surfaceContainerHigh = Color(0xFFEAEEF3),
    surfaceContainerHighest = Color(0xFFE2E7ED),
    outline = Color(0xFF71787D),
    outlineVariant = Color(0xFFC0C7CC),
)

val DarkColors = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal20,
    primaryContainer = Teal30,
    onPrimaryContainer = Teal90,
    secondary = Indigo80,
    onSecondary = Indigo20,
    secondaryContainer = Indigo30,
    onSecondaryContainer = Indigo90,
    tertiary = Amber80,
    onTertiary = Amber30,
    tertiaryContainer = Amber30,
    onTertiaryContainer = Amber90,
    error = Error80,
    onError = Color(0xFF601410),
    errorContainer = ErrorContainerDark,
    onErrorContainer = Color(0xFFF9DEDC),
    background = NeutralDarkBg,
    onBackground = NeutralDarkOnSurface,
    surface = NeutralDarkBg,
    onSurface = NeutralDarkOnSurface,
    surfaceVariant = NeutralDarkSurfaceVariant,
    onSurfaceVariant = NeutralDarkOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF0B0E10),
    surfaceContainerLow = NeutralDarkSurfaceContainerLow,
    surfaceContainer = NeutralDarkSurfaceContainer,
    surfaceContainerHigh = Color(0xFF262B2F),
    surfaceContainerHighest = Color(0xFF31363A),
    outline = Color(0xFF8B9296),
    outlineVariant = Color(0xFF40484C),
)

// Fixed status colors — used for glucose bands regardless of theme.
val GlucoseLow = Color(0xFFE5484D)
val GlucoseInRange = Color(0xFF30A46C)
val GlucoseElevated = Color(0xFFF5A524)
val GlucoseHigh = Color(0xFFE5484D)
