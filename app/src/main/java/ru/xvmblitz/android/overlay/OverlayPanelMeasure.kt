package ru.xvmblitz.android.overlay

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.xvmblitz.android.domain.PlayerSlot

const val OverlayWinRateColumnChars = 6
const val OverlayBattlesColumnChars = 4
private const val OverlayNicknameColumnWeight = 2.4f
private const val OverlayTankColumnWeight = 1.2f
private const val OverlayColumnGapCount = 4
private const val OverlayDigitWidthFactor = 0.58f
private const val OverlayStatusDotBaseDp = 7f
private const val OverlayCellSpacingBaseDp = 8f

data class OverlayColumnWidths(
    val statusDot: Dp,
    val nickname: Dp,
    val tank: Dp,
    val battles: Dp,
    val winRate: Dp,
    val cellSpacing: Dp,
)

fun overlayStatusDotSizeDp(scale: Float): Float =
    OverlayStatusDotBaseDp * scale.coerceIn(0.85f, 1.4f)

fun overlayColumnWidths(
    panelWidthDp: Float,
    contentPaddingDp: Float,
    fontSizeSp: Float,
    rowScaleX: Float,
    statusDotScale: Float,
): OverlayColumnWidths {
    val digitWidthDp = fontSizeSp * OverlayDigitWidthFactor
    val winRateDp = digitWidthDp * OverlayWinRateColumnChars
    val battlesDp = digitWidthDp * OverlayBattlesColumnChars
    val statusDotDp = overlayStatusDotSizeDp(statusDotScale)
    val cellSpacingDp = OverlayCellSpacingBaseDp * rowScaleX
    val contentWidthDp = (panelWidthDp - contentPaddingDp * 2f).coerceAtLeast(0f)
    val flexibleWidthDp = (
        contentWidthDp -
            winRateDp -
            battlesDp -
            statusDotDp -
            cellSpacingDp * OverlayColumnGapCount
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
