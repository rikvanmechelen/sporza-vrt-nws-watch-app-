package be.vanmechelen.vrtnws.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

private val VrtColors = Colors(
    primary = Color(0xFFFFD200),      // VRT yellow
    onPrimary = Color(0xFF141E3C),
    secondary = Color(0xFF8AB4F8),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF1B1B1F),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFBFC3C9),
    error = Color(0xFFCF6679),
)

@Composable
fun VrtNwsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = VrtColors, content = content)
}
