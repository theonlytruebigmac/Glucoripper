package com.syschimp.glucoripper.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun resolveDarkTheme(mode: com.syschimp.glucoripper.data.ThemeMode): Boolean = when (mode) {
    com.syschimp.glucoripper.data.ThemeMode.LIGHT -> false
    com.syschimp.glucoripper.data.ThemeMode.DARK -> true
    com.syschimp.glucoripper.data.ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun GlucoripperTheme(
    darkTheme: Boolean = false,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = GlucoTypography,
        content = content,
    )
}
