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

const val OverlayBaseSessionSummaryFontSizeSp = 12f
const val OverlayBaseSessionSummaryPaddingHorizontalDp = 10f
const val OverlayBaseSessionSummaryPaddingVerticalDp = 6f
const val OverlayBaseSessionSummarySpacingDp = 10f
const val OverlayBaseSessionSummaryWidthDp = 220f
const val OverlayBaseSessionSummaryHeightDp = 36f

private const val OverlayMinFontScale = 0.55f

const val OverlaySessionSummaryMinScaleX = 0.35f
const val OverlaySessionSummaryMinScaleY = 0.2f

fun coerceSessionSummaryScaleX(scale: Float): Float =
    scale.coerceIn(OverlaySessionSummaryMinScaleX, OverlayMaxScaleX)

fun coerceSessionSummaryScaleY(scale: Float): Float =
    scale.coerceIn(OverlaySessionSummaryMinScaleY, OverlayMaxScaleY)

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

private fun overlayFontScale(scaleY: Float): Float = overlayFontSizeSp(scaleY) / OverlayBaseFontSizeSp

fun sessionSummaryOverlayFontSizeSp(scaleY: Float): Float =
    OverlayBaseSessionSummaryFontSizeSp * overlayFontScale(scaleY)

fun sessionSummaryOverlayPaddingHorizontalDp(scaleX: Float, scaleY: Float): Float =
    OverlayBaseSessionSummaryPaddingHorizontalDp * coerceSessionSummaryScaleX(scaleX) * overlayFontScale(scaleY)

fun sessionSummaryOverlayPaddingVerticalDp(scaleY: Float): Float =
    OverlayBaseSessionSummaryPaddingVerticalDp * overlayFontScale(scaleY)

fun sessionSummaryOverlaySpacingDp(scaleX: Float, scaleY: Float): Float =
    OverlayBaseSessionSummarySpacingDp * coerceSessionSummaryScaleX(scaleX) * overlayFontScale(scaleY)

fun sessionSummaryOverlayScaleXFromWidthDelta(
    initialScaleX: Float,
    initialScaleY: Float,
    widthDelta: Float,
    density: Float,
): Float {
    val baseWidthPx = OverlayBaseSessionSummaryWidthDp * density
    val startWidthPx = baseWidthPx * coerceSessionSummaryScaleX(initialScaleX) * overlayFontScale(initialScaleY)
    val newWidthPx = (startWidthPx + widthDelta).coerceAtLeast(1f)
    val denominator = baseWidthPx * overlayFontScale(initialScaleY)
    return coerceSessionSummaryScaleX(newWidthPx / denominator)
}

fun sessionSummaryOverlayScaleYFromHeightDelta(
    initialScaleY: Float,
    heightDelta: Float,
    density: Float,
): Float {
    val baseHeightPx = OverlayBaseSessionSummaryHeightDp * density
    val startHeightPx = baseHeightPx * initialScaleY
    return coerceSessionSummaryScaleY((startHeightPx + heightDelta) / baseHeightPx)
}

const val OverlayRowSpacingPx = 5
const val OverlayPanelContentPaddingDp = 2f
