package ru.xvmblitz.android.overlay

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import ru.xvmblitz.android.domain.PlayerSlot
import kotlin.math.max

const val OverlayHorizontalChromeDp = 52f

val OverlayAlliesColumnWeights = floatArrayOf(1.4f, 1.2f, 0.7f, 0.8f)
val OverlayEnemiesColumnWeights = floatArrayOf(0.8f, 0.7f, 1.2f, 1.4f)

fun playerRowCells(player: PlayerSlot, mirrored: Boolean): List<String> {
    val nickname = if (player.isMissing) {
        "—"
    } else {
        player.nicknameWithClanTag.orEmpty().ifEmpty { "—" }
    }
    val tank = if (player.isMissing) {
        "—"
    } else {
        player.tank.orEmpty().ifEmpty { "—" }
    }
    val battles = if (player.isMissing) "—" else formatBattles(player.numberOfBattles)
    val winRate = if (player.isMissing) {
        "—"
    } else {
        player.winRate?.let { String.format("%.2f%%", it) } ?: "—"
    }
    return if (mirrored) {
        listOf(winRate, battles, tank, nickname)
    } else {
        listOf(nickname, tank, battles, winRate)
    }
}

fun measureMinPanelScaleX(
    textMeasurer: TextMeasurer,
    players: List<PlayerSlot>,
    scaleY: Float,
    density: Density,
    mirrored: Boolean = false,
): Float {
    if (players.isEmpty()) {
        return OverlayMinScaleX
    }
    val columnWeights = if (mirrored) OverlayEnemiesColumnWeights else OverlayAlliesColumnWeights
    val fontSizeSp = overlayFontSizeSp(scaleY)
    val textStyle = TextStyle(
        fontSize = fontSizeSp.sp,
        lineHeight = fontSizeSp.sp,
    )
    val maxCellWidthsPx = FloatArray(columnWeights.size)
    players.forEach { player ->
        playerRowCells(player, mirrored).forEachIndexed { index, cell ->
            val widthPx = textMeasurer.measure(
                text = cell,
                style = textStyle,
                maxLines = 1,
                softWrap = false,
            ).size.width.toFloat()
            maxCellWidthsPx[index] = max(maxCellWidthsPx[index], widthPx)
        }
    }

    val weightSum = columnWeights.sum()
    val availablePerScale = (OverlayBasePanelWidthDp - OverlayHorizontalChromeDp).coerceAtLeast(1f)
    var minScaleX = OverlayMinScaleX
    columnWeights.forEachIndexed { index, weight ->
        val columnBudgetPerScale = availablePerScale * weight / weightSum
        val cellWidthDp = maxCellWidthsPx[index] / density.density
        if (columnBudgetPerScale > 0f) {
            minScaleX = max(minScaleX, cellWidthDp / columnBudgetPerScale)
        }
    }
    return minScaleX.coerceAtMost(OverlayMaxScaleX)
}
