package com.syschimp.glucoripper.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.syschimp.glucoripper.R

// Public Sans — UI sans, variable wght axis 100..900.
@OptIn(ExperimentalTextApi::class)
private fun publicSans(weight: Int, italic: Boolean = false) = Font(
    resId = if (italic) R.font.public_sans_italic else R.font.public_sans,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val PublicSans = FontFamily(
    publicSans(400),
    publicSans(500),
    publicSans(600),
    publicSans(700),
    publicSans(400, italic = true),
    publicSans(500, italic = true),
    publicSans(600, italic = true),
    publicSans(700, italic = true),
)

// JetBrains Mono — numeric / data readouts. Variable wght axis 100..800.
@OptIn(ExperimentalTextApi::class)
private fun jbMono(weight: Int) = Font(
    resId = R.font.jetbrains_mono,
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val JetBrainsMono = FontFamily(
    jbMono(200),
    jbMono(300),
    jbMono(400),
    jbMono(500),
    jbMono(600),
    jbMono(700),
)

// Tightened display/title weights give the UI a more editorial, professional
// feel while staying within M3 defaults so all existing style references still
// work (displayLarge, headlineMedium, titleMedium, etc.).
val GlucoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.25).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

// Numeric mono presets for use anywhere a glucose value, percentage, or other
// data point is displayed. All use tabular figures so digits line up vertically
// across rows.
object RdMono {
    val Hero = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight(200),
        fontSize = 96.sp,
        lineHeight = 96.sp,
        letterSpacing = (-4).sp,
        fontFeatureSettings = "tnum",
    )
    val DisplayLarge = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight(200),
        fontSize = 72.sp,
        lineHeight = 72.sp,
        letterSpacing = (-3).sp,
        fontFeatureSettings = "tnum",
    )
    val Display = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight(200),
        fontSize = 56.sp,
        lineHeight = 56.sp,
        letterSpacing = (-2).sp,
        fontFeatureSettings = "tnum",
    )
    val LargeReadout = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight(300),
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum",
    )
    val Stat = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight(300),
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum",
    )
    val Row = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight(400),
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.3).sp,
        fontFeatureSettings = "tnum",
    )
    val RowSmall = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight(400),
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.3).sp,
        fontFeatureSettings = "tnum",
    )
    val Label = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight(500),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = "tnum",
    )
    val Caption = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight(400),
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontFeatureSettings = "tnum",
    )
    val Tiny = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight(400),
        fontSize = 9.sp,
        lineHeight = 12.sp,
        fontFeatureSettings = "tnum",
    )
}

// Uppercase tracking-wide caption — matches `.rd-overline` from the redesign
// (10px, 600 weight, 0.12em letter-spacing). Renders text uppercase via
// caller-side .uppercase() to keep the style itself reusable.
val RdOverline = TextStyle(
    fontFamily = PublicSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.2.sp,
)
