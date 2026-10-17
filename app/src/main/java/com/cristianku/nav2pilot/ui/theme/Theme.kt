package com.cristianku.nav2pilot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// openpilot primary button blue (system/ui/widgets/button.py: rl.Color(70, 91, 234))
private val OpenpilotBlue = Color(0xFF465BEA)
private val OpenpilotBlueLight = Color(0xFF8C9BF7)
private val Slate = Color(0xFF3F5560)

private val LightColors = lightColorScheme(
    primary = OpenpilotBlue,
    secondary = Slate,
)

private val DarkColors = darkColorScheme(
    primary = OpenpilotBlueLight,
    secondary = Color(0xFFB6CAD4),
)

/** Material 3 theme with the openpilot blue; no dynamic (wallpaper) colours. */
@Composable
fun Nav2PilotTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme: ColorScheme
    if (darkTheme) {
        colorScheme = DarkColors
    } else {
        colorScheme = LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
