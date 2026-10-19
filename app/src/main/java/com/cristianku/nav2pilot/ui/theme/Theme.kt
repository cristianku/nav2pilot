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

// The logo's colours: night blue background and the cyan of the road markings.
private val NightBlue = Color(0xFF010E30)
private val NightBlueSurface = Color(0xFF0A1A44)
private val NightBlueRaised = Color(0xFF13275C)
// cyan dark enough for white text and for text on white (light theme), bright on night blue (dark theme)
private val BrandCyan = Color(0xFF007C96)
private val BrandCyanBright = Color(0xFF00D5E8)

// Neutral surfaces: Material's default tonal tint turned the cards and the bars lilac.
private val LightColors = lightColorScheme(
    primary = BrandCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2F3F8),
    onPrimaryContainer = NightBlue,
    secondary = NightBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE4F5),
    onSecondaryContainer = NightBlue,
    tertiary = BrandCyan,
    onTertiary = Color.White,
    background = Color(0xFFF3F6FA),
    onBackground = NightBlue,
    surface = Color.White,
    onSurface = NightBlue,
    surfaceVariant = Color(0xFFEAF0F6),
    onSurfaceVariant = Color(0xFF4E5D74),
    surfaceTint = Color.White,
    outline = Color(0xFF8592A3),
    outlineVariant = Color(0xFFDCE2EB),
)

private val DarkColors = darkColorScheme(
    primary = BrandCyanBright,
    onPrimary = NightBlue,
    primaryContainer = Color(0xFF00475A),
    onPrimaryContainer = Color(0xFFC4F7FC),
    secondary = Color(0xFF9DB4E8),
    onSecondary = NightBlue,
    secondaryContainer = NightBlueRaised,
    onSecondaryContainer = Color(0xFFDDE6FF),
    tertiary = BrandCyanBright,
    onTertiary = NightBlue,
    background = NightBlue,
    onBackground = Color(0xFFF2F6FC),
    surface = NightBlueSurface,
    onSurface = Color(0xFFF2F6FC),
    surfaceVariant = NightBlueRaised,
    onSurfaceVariant = Color(0xFFAAB8D4),
    surfaceTint = NightBlueSurface,
    outline = Color(0xFF6B7A9A),
    outlineVariant = Color(0xFF22336A),
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

/** Material 3 theme in the logo colours, night blue and cyan; no dynamic (wallpaper) colours. */
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
