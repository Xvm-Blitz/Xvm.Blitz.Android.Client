package ru.xvmblitz.android.overlay

const val OverlayBaseFontSizeSp = 12f
const val OverlayBasePanelWidthDp = 280f
const val OverlayMinScaleX = 0.67f
const val OverlayMaxScaleX = 2f
const val OverlayMinScaleY = 0.25f
const val OverlayMaxScaleY = 2f
const val OverlayResizeHandleDp = 36f

private const val OverlayMinFontScale = 0.75f

fun coerceOverlayScaleX(scale: Float): Float = scale.coerceIn(OverlayMinScaleX, OverlayMaxScaleX)

fun coerceOverlayScaleY(scale: Float): Float = scale.coerceIn(OverlayMinScaleY, OverlayMaxScaleY)

private fun overlayShrinkProgress(scaleY: Float): Float {
    val coerced = coerceOverlayScaleY(scaleY)
    if (coerced >= 1f) {
        return 1f
    }
    return ((coerced - OverlayMinScaleY) / (1f - OverlayMinScaleY)).coerceIn(0f, 1f)
}

fun overlayFontSizeSp(scaleY: Float): Float {
    val coerced = coerceOverlayScaleY(scaleY)
    if (coerced >= 1f) {
        return OverlayBaseFontSizeSp * coerced
    }
    val fontScale = OverlayMinFontScale + (1f - OverlayMinFontScale) * overlayShrinkProgress(coerced)
    return OverlayBaseFontSizeSp * fontScale
}

fun overlayRowSpacingDp(scaleY: Float): Float {
    val coerced = coerceOverlayScaleY(scaleY)
    if (coerced >= 1f) {
        return 1.5f * coerced
    }
    val progress = overlayShrinkProgress(coerced)
    return 1.5f * progress * progress * progress
}

fun overlayRowVerticalPaddingDp(scaleY: Float): Float {
    val coerced = coerceOverlayScaleY(scaleY)
    if (coerced >= 1f) {
        return 2f * coerced
    }
    val progress = overlayShrinkProgress(coerced)
    return (2f * progress * progress).coerceAtLeast(0.25f)
}

fun overlayContentPaddingYDp(scaleY: Float): Float {
    val coerced = coerceOverlayScaleY(scaleY)
    if (coerced >= 1f) {
        return 5f * coerced
    }
    val progress = overlayShrinkProgress(coerced)
    return (4f * progress * progress).coerceAtLeast(1f)
}
