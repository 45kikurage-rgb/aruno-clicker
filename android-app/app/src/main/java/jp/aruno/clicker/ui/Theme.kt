package jp.aruno.clicker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MatrixGreen = Color(0xFF43FF73)
val MatrixGreenSoft = Color(0xFF1DDA58)
val MatrixBlack = Color(0xFF031008)
val MatrixSurface = Color(0xFF0A1B10)
val MatrixSurfaceRaised = Color(0xFF102719)
val MatrixTextMuted = Color(0xFF9AB9A3)
val AlertAmber = Color(0xFFFFCB68)
val DangerRed = Color(0xFFFF6B6B)

private val AppColorScheme = darkColorScheme(
    primary = MatrixGreen,
    onPrimary = Color(0xFF00210A),
    primaryContainer = Color(0xFF0C4D24),
    onPrimaryContainer = Color(0xFF9CFFB0),
    secondary = MatrixGreenSoft,
    background = MatrixBlack,
    onBackground = Color(0xFFE8F5EA),
    surface = MatrixSurface,
    onSurface = Color(0xFFE8F5EA),
    surfaceVariant = MatrixSurfaceRaised,
    onSurfaceVariant = MatrixTextMuted,
    outline = Color(0xFF3B6147),
    error = DangerRed,
)

@Composable
fun ArunoClickerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
