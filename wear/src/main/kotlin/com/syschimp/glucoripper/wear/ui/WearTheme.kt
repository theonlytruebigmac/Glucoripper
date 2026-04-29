package com.syschimp.glucoripper.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography
import com.syschimp.glucoripper.wear.R

@OptIn(ExperimentalTextApi::class)
private fun publicSans(weight: Int, italic: Boolean = false) = Font(
    resId = if (italic) R.font.public_sans_italic else R.font.public_sans,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val PublicSans = FontFamily(
    publicSans(400),
    publicSans(500),
    publicSans(600),
    publicSans(700),
)

private val WearTypography = Typography(defaultFontFamily = PublicSans)

@Composable
fun GlucoWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(typography = WearTypography, content = content)
}
