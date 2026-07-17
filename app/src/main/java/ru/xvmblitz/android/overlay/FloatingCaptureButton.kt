package ru.xvmblitz.android.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

@Composable
fun OverlayFab(
    errorPulse: Int = 0,
    errorMessage: String? = null,
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

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AnimatedVisibility(
            visible = !errorMessage.isNullOrBlank(),
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.92f),
        ) {
            Text(
                text = errorMessage.orEmpty(),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 180.dp)
                    .background(Color(0xF2C62828), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
        Text(
            text = "Статистика",
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .offset(x = shake.value.dp)
                .background(backgroundColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}
