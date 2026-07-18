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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

enum class FabErrorSide {
    Top,
    Bottom,
    Left,
    Right,
}

fun resolveFabErrorSide(
    buttonX: Int,
    buttonY: Int,
    buttonWidth: Int,
    buttonHeight: Int,
    popupWidth: Int,
    popupHeight: Int,
    screenWidth: Int,
    screenHeight: Int,
    gapPx: Int,
): FabErrorSide {
    val spaceTop = buttonY
    val spaceBottom = screenHeight - (buttonY + buttonHeight)
    val spaceLeft = buttonX
    val spaceRight = screenWidth - (buttonX + buttonWidth)
    val neededVertical = popupHeight + gapPx
    val neededHorizontal = popupWidth + gapPx
    val candidates = listOf(
        FabErrorSide.Top to (spaceTop >= neededVertical),
        FabErrorSide.Bottom to (spaceBottom >= neededVertical),
        FabErrorSide.Left to (spaceLeft >= neededHorizontal),
        FabErrorSide.Right to (spaceRight >= neededHorizontal),
    )
    candidates.firstOrNull { (_, fits) -> fits }?.let { (side, _) -> return side }
    return listOf(
        FabErrorSide.Top to spaceTop,
        FabErrorSide.Bottom to spaceBottom,
        FabErrorSide.Left to spaceLeft,
        FabErrorSide.Right to spaceRight,
    ).maxBy { (_, space) -> space }.first
}

@Composable
fun OverlayFab(
    errorPulse: Int = 0,
    errorMessage: String? = null,
    buttonX: Int = 0,
    buttonY: Int = 0,
    screenWidthPx: Int = 0,
    screenHeightPx: Int = 0,
    onWindowOriginOffset: (offsetX: Int, offsetY: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val shake = remember { Animatable(0f) }
    var isError by remember { mutableStateOf(false) }
    var buttonSize by remember { mutableStateOf(IntSize.Zero) }
    var popupSize by remember { mutableStateOf(IntSize.Zero) }
    val gapPx = with(density) { 6.dp.roundToPx() }
    val backgroundColor by animateColorAsState(
        targetValue = if (isError) Color(0xE6C62828) else Color(0xE63D7EA6),
        animationSpec = tween(160),
        label = "fab-color",
    )
    val showError = !errorMessage.isNullOrBlank()
    val errorSide = remember(
        showError,
        buttonX,
        buttonY,
        buttonSize,
        popupSize,
        screenWidthPx,
        screenHeightPx,
    ) {
        if (!showError || buttonSize == IntSize.Zero) {
            FabErrorSide.Top
        } else {
            val estimatedPopup = if (popupSize == IntSize.Zero) {
                IntSize(
                    width = with(density) { 180.dp.roundToPx() },
                    height = with(density) { 36.dp.roundToPx() },
                )
            } else {
                popupSize
            }
            resolveFabErrorSide(
                buttonX = buttonX,
                buttonY = buttonY,
                buttonWidth = buttonSize.width,
                buttonHeight = buttonSize.height,
                popupWidth = estimatedPopup.width,
                popupHeight = estimatedPopup.height,
                screenWidth = screenWidthPx,
                screenHeight = screenHeightPx,
                gapPx = gapPx,
            )
        }
    }

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

    LaunchedEffect(showError, errorSide, popupSize, buttonSize) {
        if (!showError || popupSize == IntSize.Zero || buttonSize == IntSize.Zero) {
            onWindowOriginOffset(0, 0)
            return@LaunchedEffect
        }
        val offsetX = when (errorSide) {
            FabErrorSide.Left -> -(popupSize.width + gapPx)
            FabErrorSide.Right -> 0
            FabErrorSide.Top, FabErrorSide.Bottom -> 0
        }
        val offsetY = when (errorSide) {
            FabErrorSide.Top -> -(popupSize.height + gapPx)
            FabErrorSide.Bottom -> 0
            FabErrorSide.Left, FabErrorSide.Right -> 0
        }
        onWindowOriginOffset(offsetX, offsetY)
    }

    val errorPopup: @Composable () -> Unit = {
        AnimatedVisibility(
            visible = showError,
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
                    .onSizeChanged { popupSize = it }
                    .background(Color(0xF2C62828), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }

    val buttonLabel: @Composable () -> Unit = {
        Text(
            text = "Статистика",
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .onSizeChanged { buttonSize = it }
                .offset(x = shake.value.dp)
                .background(backgroundColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }

    when {
        !showError -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                buttonLabel()
            }
        }
        errorSide == FabErrorSide.Top -> {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                errorPopup()
                buttonLabel()
            }
        }
        errorSide == FabErrorSide.Bottom -> {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                buttonLabel()
                errorPopup()
            }
        }
        errorSide == FabErrorSide.Left -> {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                errorPopup()
                buttonLabel()
            }
        }
        else -> {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                buttonLabel()
                errorPopup()
            }
        }
    }
}

enum class CaptureButtonOffScreenDirection {
    Left,
    Right,
    Top,
    Bottom,
}

fun resolveCaptureButtonOffScreenDirection(
    buttonX: Int,
    buttonY: Int,
    buttonWidth: Int,
    buttonHeight: Int,
    screenWidth: Int,
    screenHeight: Int,
): CaptureButtonOffScreenDirection? {
    if (buttonWidth <= 0 || buttonHeight <= 0 || screenWidth <= 0 || screenHeight <= 0) {
        return null
    }
    val visibleLeft = maxOf(0, buttonX)
    val visibleTop = maxOf(0, buttonY)
    val visibleRight = minOf(screenWidth, buttonX + buttonWidth)
    val visibleBottom = minOf(screenHeight, buttonY + buttonHeight)
    if (visibleRight > visibleLeft && visibleBottom > visibleTop) {
        return null
    }

    val centerX = buttonX + buttonWidth / 2f
    val centerY = buttonY + buttonHeight / 2f
    val overflowLeft = maxOf(0f, -centerX)
    val overflowRight = maxOf(0f, centerX - screenWidth)
    val overflowTop = maxOf(0f, -centerY)
    val overflowBottom = maxOf(0f, centerY - screenHeight)
    val maxOverflow = maxOf(overflowLeft, overflowRight, overflowTop, overflowBottom)
    return when (maxOverflow) {
        overflowLeft -> CaptureButtonOffScreenDirection.Left
        overflowRight -> CaptureButtonOffScreenDirection.Right
        overflowTop -> CaptureButtonOffScreenDirection.Top
        else -> CaptureButtonOffScreenDirection.Bottom
    }
}

@Composable
fun CaptureButtonDirectionHint(
    direction: CaptureButtonOffScreenDirection,
    modifier: Modifier = Modifier,
) {
    val arrow = when (direction) {
        CaptureButtonOffScreenDirection.Left -> "◀"
        CaptureButtonOffScreenDirection.Right -> "▶"
        CaptureButtonOffScreenDirection.Top -> "▲"
        CaptureButtonOffScreenDirection.Bottom -> "▼"
    }
    Text(
        text = arrow,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(Color(0xE63D7EA6), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}
