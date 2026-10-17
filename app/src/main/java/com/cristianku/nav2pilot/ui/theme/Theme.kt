package com.cristianku.nav2pilot.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Green = Color(0xFF1B8A5A)
private val GreenLight = Color(0xFF7DDBA8)
private val Slate = Color(0xFF3F5560)

private val LightColors = lightColorScheme(
    primary = Green,
    secondary = Slate,
)

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    secondary = Color(0xFFB6CAD4),
)

/** Material 3 theme; uses the wallpaper colours (dynamic color) on Android 12 and newer. */
@Composable
fun Nav2PilotTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme: ColorScheme
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) {
            colorScheme = dynamicDarkColorScheme(context)
        } else {
            colorScheme = dynamicLightColorScheme(context)
        }
    } else {
        if (darkTheme) {
            colorScheme = DarkColors
        } else {
            colorScheme = LightColors
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
