package com.upitracker.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val CreditGreen = Color(0xFF4CAF50)
val DebitRed = Color(0xFFE53935)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4CAF50), secondary = Color(0xFF81C784), tertiary = Color(0xFF26A69A),
    background = Color(0xFF0F0F1A), surface = Color(0xFF1A1A2E)
)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF388E3C), secondary = Color(0xFF558B2F), tertiary = Color(0xFF00796B)
)

@Composable
fun UPITrackerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
