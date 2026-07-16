package ru.xvmblitz.android.overlay

const val OverlayBaseFontSizeSp = 12f
const val OverlayBasePanelWidthDp = 280f
const val OverlayMinScaleX = 0.67f
const val OverlayMaxScaleX = 2f
const val OverlayMinScaleY = 0.35f
const val OverlayMaxScaleY = 2f
const val OverlayResizeHandleDp = 36f

fun coerceOverlayScaleX(scale: Float): Float = scale.coerceIn(OverlayMinScaleX, OverlayMaxScaleX)

fun coerceOverlayScaleY(scale: Float): Float = scale.coerceIn(OverlayMinScaleY, OverlayMaxScaleY)

fun overlayVerticalSpacingScale(scaleY: Float): Float {
    val coerced = coerceOverlayScaleY(scaleY)
    return if (coerced >= 1f) {
        coerced
    } else {
        coerced * coerced
    }
}
