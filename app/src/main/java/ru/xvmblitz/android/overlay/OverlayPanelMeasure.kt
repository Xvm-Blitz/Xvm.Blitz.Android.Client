package ru.xvmblitz.android.overlay

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.xvmblitz.android.domain.PlayerSlot

private const val OverlayNicknameMinChars = 8f
private const val OverlayTankMinChars = 6f
const val OverlayWinRateColumnChars = 6
const val OverlayBattlesColumnChars = 4
private const val OverlayNicknameColumnWeight = 2.4f
private const val OverlayTankColumnWeight = 1.2f
private const val OverlayColumnGapCount = 4
private const val OverlayDigitWidthFactor = 0.58f
private const val OverlayStatusDotBaseDp = 7f
private const val OverlayCallActionBaseDp = 16f
private const val OverlayCellSpacingBaseDp = 8f

data class OverlayColumnWidths(
    val statusDot: Dp,
    val nickname: Dp,
    val tank: Dp,
    val battles: Dp,
    val winRate: Dp,
    val cellSpacing: Dp,
    val callAction: Dp = 0.dp,
)

fun overlayStatusDotSizeDp(scale: Float): Float =
    OverlayStatusDotBaseDp * scale.coerceIn(0.85f, 1.4f)

fun overlayCallActionSizeDp(scale: Float): Float =
    OverlayCallActionBaseDp * scale.coerceIn(0.85f, 1.4f)

fun overlayPanelFittedWidthDp(
    contentPaddingDp: Float,
    fontSizeSp: Float,
    fontScale: Float,
    statusDotScale: Float,
    includeCallAction: Boolean = false,
): Float {
    val digitWidthDp = fontSizeSp * OverlayDigitWidthFactor
    val gapCount = if (includeCallAction) OverlayColumnGapCount + 1 else OverlayColumnGapCount
    val callActionDp = if (includeCallAction) overlayCallActionSizeDp(fontScale) else 0f
    return contentPaddingDp * 2f +
        overlayStatusDotSizeDp(statusDotScale) +
        digitWidthDp * OverlayNicknameMinChars +
        digitWidthDp * OverlayTankMinChars +
        digitWidthDp * OverlayBattlesColumnChars +
        digitWidthDp * OverlayWinRateColumnChars +
        callActionDp +
        OverlayCellSpacingBaseDp * fontScale * gapCount
}

fun overlayColumnWidths(
    panelWidthDp: Float,
    contentPaddingDp: Float,
    fontSizeSp: Float,
    fontScale: Float,
    rowScaleX: Float,
    statusDotScale: Float,
    includeCallAction: Boolean = false,
): OverlayColumnWidths {
    val digitWidthDp = fontSizeSp * OverlayDigitWidthFactor
    val winRateDp = digitWidthDp * OverlayWinRateColumnChars
    val battlesDp = digitWidthDp * OverlayBattlesColumnChars
    val statusDotDp = overlayStatusDotSizeDp(statusDotScale)
    val callActionDp = if (includeCallAction) overlayCallActionSizeDp(rowScaleX) else 0f
    val gapCount = if (includeCallAction) OverlayColumnGapCount + 1 else OverlayColumnGapCount
    val cellSpacingDp = OverlayCellSpacingBaseDp * fontScale
    val contentWidthDp = (panelWidthDp - contentPaddingDp * 2f).coerceAtLeast(0f)
    val flexibleWidthDp = (
        contentWidthDp -
            winRateDp -
            battlesDp -
            statusDotDp -
            callActionDp -
            cellSpacingDp * gapCount
        ).coerceAtLeast(0f)
    val totalWeight = OverlayNicknameColumnWeight + OverlayTankColumnWeight
    val tankDp = flexibleWidthDp * (OverlayTankColumnWeight / totalWeight)
    val nicknameDp = (flexibleWidthDp - tankDp).coerceAtLeast(0f)
    return OverlayColumnWidths(
        statusDot = statusDotDp.dp,
        nickname = nicknameDp.dp,
        tank = tankDp.dp,
        battles = battlesDp.dp,
        winRate = winRateDp.dp,
        cellSpacing = cellSpacingDp.dp,
        callAction = callActionDp.dp,
    )
}

fun formatWinRate(winRate: Double): String {
    return if (winRate >= 99.995) {
        "100%"
    } else {
        String.format("%.2f%%", winRate)
    }
}

fun playerRowCells(player: PlayerSlot, mirrored: Boolean): List<String> {
    val nickname = formatNicknameWithClan(player, mirrored)
    val tank = if (player.isMissing) {
        "-"
    } else {
        player.tank.orEmpty().ifEmpty { "-" }
    }
    val battles = if (player.isMissing) "-" else formatBattles(player.numberOfBattles)
    val winRate = if (player.isMissing) {
        "-"
    } else {
        player.winRate?.let(::formatWinRate) ?: "-"
    }
    return if (mirrored) {
        listOf(winRate, battles, tank, nickname)
    } else {
        listOf(nickname, tank, battles, winRate)
    }
}

fun formatNicknameWithClan(player: PlayerSlot, mirrored: Boolean): String {
    if (player.isMissing) {
        return "-"
    }
    val nickname = player.nickname.orEmpty()
    val clanTag = player.clanTag
    if (clanTag.isNullOrBlank()) {
        return nickname.ifEmpty { "-" }
    }
    return if (mirrored) {
        "$nickname [$clanTag]"
    } else {
        "[$clanTag] $nickname"
    }
}
