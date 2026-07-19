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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
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
    val estimatedPopupSize = remember(density) {
        IntSize(
            width = with(density) { 180.dp.roundToPx() },
            height = with(density) { 36.dp.roundToPx() },
        )
    }
    val backgroundColor by animateColorAsState(
        targetValue = if (isError) Color(0xE6C62828) else Color(0xE63D7EA6),
        animationSpec = tween(160),
        label = "fab-color",
    )
    val showError = !errorMessage.isNullOrBlank()
    val effectivePopupSize = if (popupSize == IntSize.Zero) estimatedPopupSize else popupSize
    val errorSide = remember(
        showError,
        buttonX,
        buttonY,
        buttonSize,
        effectivePopupSize,
        screenWidthPx,
        screenHeightPx,
        gapPx,
    ) {
        if (!showError || buttonSize == IntSize.Zero) {
            FabErrorSide.Top
        } else {
            resolveFabErrorSide(
                buttonX = buttonX,
                buttonY = buttonY,
                buttonWidth = buttonSize.width,
                buttonHeight = buttonSize.height,
                popupWidth = effectivePopupSize.width,
                popupHeight = effectivePopupSize.height,
                screenWidth = screenWidthPx,
                screenHeight = screenHeightPx,
                gapPx = gapPx,
            )
        }
    }

    val buttonOffsetX = when {
        !showError || buttonSize == IntSize.Zero -> 0
        errorSide == FabErrorSide.Left -> effectivePopupSize.width + gapPx
        errorSide == FabErrorSide.Top || errorSide == FabErrorSide.Bottom ->
            max(0, (effectivePopupSize.width - buttonSize.width) / 2)
        else -> 0
    }
    val buttonOffsetY = when {
        !showError || buttonSize == IntSize.Zero -> 0
        errorSide == FabErrorSide.Top -> effectivePopupSize.height + gapPx
        else -> 0
    }
    val contentWidth = when {
        !showError || buttonSize == IntSize.Zero -> if (buttonSize == IntSize.Zero) 0 else buttonSize.width
        errorSide == FabErrorSide.Left || errorSide == FabErrorSide.Right ->
            buttonSize.width + gapPx + effectivePopupSize.width
        else -> max(buttonSize.width, effectivePopupSize.width)
    }
    val contentHeight = when {
        !showError || buttonSize == IntSize.Zero -> if (buttonSize == IntSize.Zero) 0 else buttonSize.height
        errorSide == FabErrorSide.Top || errorSide == FabErrorSide.Bottom ->
            buttonSize.height + gapPx + effectivePopupSize.height
        else -> max(buttonSize.height, effectivePopupSize.height)
    }
    val popupOffsetX = when {
        !showError || buttonSize == IntSize.Zero -> 0
        errorSide == FabErrorSide.Left -> 0
        errorSide == FabErrorSide.Right -> buttonOffsetX + buttonSize.width + gapPx
        else -> max(0, (contentWidth - effectivePopupSize.width) / 2)
    }
    val popupOffsetY = when {
        !showError || buttonSize == IntSize.Zero -> 0
        errorSide == FabErrorSide.Top -> 0
        errorSide == FabErrorSide.Bottom -> buttonOffsetY + buttonSize.height + gapPx
        else -> max(0, (contentHeight - effectivePopupSize.height) / 2)
    }

    SideEffect {
        if (!showError) {
            onWindowOriginOffset(0, 0)
        } else {
            onWindowOriginOffset(-buttonOffsetX, -buttonOffsetY)
        }
    }

    LaunchedEffect(showError) {
        if (!showError) {
            popupSize = IntSize.Zero
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

    Box(
        modifier = if (contentWidth > 0 && contentHeight > 0) {
            modifier
                .width(with(density) { contentWidth.toDp() })
                .height(with(density) { contentHeight.toDp() })
        } else {
            modifier
        },
    ) {
        Text(
            text = "Статистика",
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .offset { IntOffset(buttonOffsetX, buttonOffsetY) }
                .onSizeChanged { buttonSize = it }
                .offset(x = shake.value.dp)
                .background(backgroundColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )

        AnimatedVisibility(
            visible = showError,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.92f),
            modifier = Modifier.offset { IntOffset(popupOffsetX, popupOffsetY) },
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
