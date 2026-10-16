package com.dawidggs.aliashub.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.dawidggs.aliashub.data.ThemeMode

/** Whether [this] mode ends up dark, resolving SYSTEM against the device setting. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.OLED -> true
}

@Composable
fun AliasHubTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode.isDark()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    // OLED only swaps the base background; every other color stays as Material You made it.
    val finalColors = if (mode == ThemeMode.OLED) {
        colors.copy(background = Color.Black, surface = Color.Black)
    } else {
        colors
    }
    MaterialTheme(colorScheme = finalColors, content = content)
}
