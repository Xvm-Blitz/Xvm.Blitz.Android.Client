package ru.xvmblitz.android.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class OverlayFabMode {
    Capture,
    Hide,
}

@Composable
fun OverlayFab(
    mode: OverlayFabMode,
    modifier: Modifier = Modifier,
) {
    val (text, background) = when (mode) {
        OverlayFabMode.Capture -> "Статистика" to Color(0xE63D7EA6)
        OverlayFabMode.Hide -> "Скрыть статистику" to Color(0xE6C45C5C)
    }
    Text(
        text = text,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(background, RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}
