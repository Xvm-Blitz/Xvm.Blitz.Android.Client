package ru.xvmblitz.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorScheme = darkColorScheme(
    primary = Color(0xFF3D7EA6),
    secondary = Color(0xFF93CF93),
    tertiary = Color(0xFFB497CC),
    background = Color(0xFF12151C),
    surface = Color(0xFF1B1F2A),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFFE8ECF2),
    onSurface = Color(0xFFE8ECF2),
)

@Composable
fun XvmBlitzTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content,
    )
}
