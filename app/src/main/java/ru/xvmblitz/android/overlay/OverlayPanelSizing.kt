package ru.xvmblitz.android.overlay

const val OverlayBaseFontSizeSp = 11f
const val OverlayBasePanelWidthDp = 280f
const val OverlayBasePanelHeightDp = 220f
const val OverlayMinScaleX = 0.67f
const val OverlayMaxScaleX = 2f
const val OverlayMinScaleY = 0.25f
const val OverlayMaxScaleY = 2f
const val OverlayResizeHandleDp = 28f
const val OverlayResizeEdgeThicknessDp = 20f
const val OverlayResizeEdgeLengthDp = 36f

private const val OverlayMinFontScale = 0.55f

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

const val OverlayRowSpacingPx = 5
const val OverlayPanelContentPaddingDp = 2f
