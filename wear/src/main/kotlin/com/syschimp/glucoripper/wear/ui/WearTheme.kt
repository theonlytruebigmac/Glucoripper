package com.syschimp.glucoripper.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography
import com.syschimp.glucoripper.wear.R

// Re-Diary tokens — wear edition. Watch is OLED so we lock to the dark variant.

internal val WearBg          = Color(0xFF0B0D0F)
internal val WearElev1       = Color(0xFF14171A)
internal val WearElev2       = Color(0xFF1B1F22)
internal val WearElev3       = Color(0xFF23272B)
internal val WearLine        = Color(0xFF2A2F33)
internal val WearLineSoft    = Color(0xFF1F2326)
internal val WearFg          = Color(0xFFECEEF0)
internal val WearFgMuted     = Color(0xFFA0A6AB)
internal val WearFgSubtle    = Color(0xFF6B7177)
internal val WearFgFaint     = Color(0xFF3F454A)
internal val WearAccent      = Color(0xFFECEEF0)
internal val WearAccentOn    = Color(0xFF0B0D0F)

@OptIn(ExperimentalTextApi::class)
private fun publicSans(weight: Int, italic: Boolean = false) = Font(
    resId = if (italic) R.font.public_sans_italic else R.font.public_sans,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

internal val PublicSans = FontFamily(
    publicSans(400),
    publicSans(500),
    publicSans(600),
    publicSans(700),
)

@OptIn(ExperimentalTextApi::class)
private fun jbMono(weight: Int) = Font(
    resId = R.font.jetbrains_mono,
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

internal val JetBrainsMono = FontFamily(
    jbMono(300),
    jbMono(400),
    jbMono(500),
    jbMono(600),
    jbMono(700),
)

// Numeric mono presets matched to the redesign sizes (scaled for watch).
internal object WearMono {
    val Hero = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight(600),
        fontSize = 50.sp,
        lineHeight = 50.sp,
        letterSpacing = (-1.5).sp,
        fontFeatureSettings = "tnum",
    )
    val Display = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight(600),
        fontSize = 38.sp,
        lineHeight = 38.sp,
        letterSpacing = (-1).sp,
        fontFeatureSettings = "tnum",
    )
    val Stat = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight(500),
        fontSize = 18.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.3).sp,
        fontFeatureSettings = "tnum",
    )
    val Row = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight(500),
        fontSize = 13.sp,
        lineHeight = 14.sp,
        fontFeatureSettings = "tnum",
    )
    val Caption = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight(400),
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontFeatureSettings = "tnum",
    )
}

internal val WearOverline = TextStyle(
    fontFamily = PublicSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 9.sp,
    lineHeight = 12.sp,
    letterSpacing = 1.2.sp,
    color = WearFgMuted,
)

private val WearTypography = Typography(
    defaultFontFamily = PublicSans,
    title3 = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
    body1 = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    caption1 = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
    ),
    caption2 = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
    ),
    caption3 = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
    ),
)

private val WearColors = Colors(
    primary = WearAccent,
    primaryVariant = WearElev3,
    onPrimary = WearAccentOn,
    secondary = WearAccent,
    secondaryVariant = WearElev2,
    onSecondary = WearAccentOn,
    background = WearBg,
    onBackground = WearFg,
    surface = WearElev1,
    onSurface = WearFg,
    onSurfaceVariant = WearFgMuted,
    error = GlucoseLow,
    onError = Color.White,
)

@Composable
fun GlucoWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = WearColors,
        typography = WearTypography,
        content = content,
    )
}
