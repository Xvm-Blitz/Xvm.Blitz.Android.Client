package ru.xvmblitz.android.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

@Composable
fun OverlayFab(
    errorPulse: Int = 0,
    modifier: Modifier = Modifier,
) {
    val shake = remember { Animatable(0f) }
    var isError by remember { mutableStateOf(false) }
    val backgroundColor by animateColorAsState(
        targetValue = if (isError) Color(0xE6C62828) else Color(0xE63D7EA6),
        animationSpec = tween(160),
        label = "fab-color",
    )

    LaunchedEffect(errorPulse) {
        if (errorPulse <= 0) {
            isError = false
            shake.snapTo(0f)
            return@LaunchedEffect
        }
        isError = true
        val durationMs = 520
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < durationMs) {
            val progress = (System.currentTimeMillis() - start) / durationMs.toFloat()
            shake.snapTo(sin(progress * Math.PI.toFloat() * 10f) * 6f * (1f - progress))
            kotlinx.coroutines.delay(16)
        }
        shake.snapTo(0f)
        isError = false
    }

    Text(
        text = "Статистика",
        color = Color.White,
        fontSize = 8.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .offset(x = shake.value.dp)
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}
