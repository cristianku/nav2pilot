package com.cristianku.nav2pilot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// openpilot primary button blue (system/ui/widgets/button.py: rl.Color(70, 91, 234))
private val OpenpilotBlue = Color(0xFF465BEA)
private val OpenpilotBlueLight = Color(0xFF8C9BF7)

// cyan of the road markings in the logo: dark enough for text on white, bright on the dark surfaces
private val BrandCyan = Color(0xFF007C96)
private val BrandCyanBright = Color(0xFF00D5E8)

// Neutral surfaces: Material's default tonal tint turned the cards and the bars lilac.
private val LightColors = lightColorScheme(
    primary = OpenpilotBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E7FD),
    onPrimaryContainer = Color(0xFF162238),
    secondary = BrandCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F1F6),
    onSecondaryContainer = Color(0xFF00394A),
    tertiary = BrandCyan,
    onTertiary = Color.White,
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF162238),
    surface = Color.White,
    onSurface = Color(0xFF162238),
    surfaceVariant = Color(0xFFEDF1F7),
    onSurfaceVariant = Color(0xFF556477),
    surfaceTint = Color.White,
    outline = Color(0xFF8592A3),
    outlineVariant = Color(0xFFDCE2EB),
)

private val DarkColors = darkColorScheme(
    primary = OpenpilotBlueLight,
    onPrimary = Color(0xFF10182A),
    primaryContainer = Color(0xFF26336E),
    onPrimaryContainer = Color(0xFFDDE2FF),
    secondary = BrandCyanBright,
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF0E3A46),
    onSecondaryContainer = Color(0xFFBFF4FA),
    tertiary = BrandCyanBright,
    onTertiary = Color(0xFF00363D),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFF2F6FC),
    surface = Color(0xFF141F31),
    onSurface = Color(0xFFF2F6FC),
    surfaceVariant = Color(0xFF1C293E),
    onSurfaceVariant = Color(0xFFAAB8CC),
    surfaceTint = Color(0xFF141F31),
    outline = Color(0xFF6B7A90),
    outlineVariant = Color(0xFF26344A),
)

private val Nav2PilotShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

/** Status colours, kept apart from the brand colours and always shown next to an icon and a text. */
object StatusColors {
    @Composable
    fun success(): Color {
        if (isSystemInDarkTheme()) {
            return Color(0xFF6FD38A)
        }
        return Color(0xFF1E7D3A)
    }

    @Composable
    fun warning(): Color {
        if (isSystemInDarkTheme()) {
            return Color(0xFFF2B45C)
        }
        return Color(0xFF8A5300)
    }
}

/** Material 3 theme with the openpilot blue and the logo cyan; no dynamic (wallpaper) colours. */
@Composable
fun Nav2PilotTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme: ColorScheme
    if (darkTheme) {
        colorScheme = DarkColors
    } else {
        colorScheme = LightColors
    }
    MaterialTheme(colorScheme = colorScheme, shapes = Nav2PilotShapes, content = content)
}
