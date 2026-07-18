package ru.xvmblitz.android.overlay

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.xvmblitz.android.domain.PlayerSlot

const val OverlayWinRateColumnChars = 6
const val OverlayBattlesColumnChars = 4
private const val OverlayNicknameColumnWeight = 2.4f
private const val OverlayTankColumnWeight = 1.2f
private const val OverlayColumnGapCount = 3
private const val OverlayDigitWidthFactor = 0.58f

data class OverlayColumnWidths(
    val nickname: Dp,
    val tank: Dp,
    val battles: Dp,
    val winRate: Dp,
    val cellSpacing: Dp,
)

fun overlayColumnWidths(
    panelWidthDp: Float,
    contentPaddingDp: Float,
    fontSizeSp: Float,
    rowScaleX: Float,
): OverlayColumnWidths {
    val digitWidthDp = fontSizeSp * OverlayDigitWidthFactor
    val winRateDp = digitWidthDp * OverlayWinRateColumnChars
    val battlesDp = digitWidthDp * OverlayBattlesColumnChars
    val cellSpacingDp = 4f * rowScaleX
    val contentWidthDp = (panelWidthDp - contentPaddingDp * 2f).coerceAtLeast(0f)
    val flexibleWidthDp = (
        contentWidthDp -
            winRateDp -
            battlesDp -
            cellSpacingDp * OverlayColumnGapCount
        ).coerceAtLeast(0f)
    val totalWeight = OverlayNicknameColumnWeight + OverlayTankColumnWeight
    val tankDp = flexibleWidthDp * (OverlayTankColumnWeight / totalWeight)
    val nicknameDp = (flexibleWidthDp - tankDp).coerceAtLeast(0f)
    return OverlayColumnWidths(
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
        "—"
    } else {
        player.tank.orEmpty().ifEmpty { "—" }
    }
    val battles = if (player.isMissing) "—" else formatBattles(player.numberOfBattles)
    val winRate = if (player.isMissing) {
        "—"
    } else {
        player.winRate?.let(::formatWinRate) ?: "—"
    }
    return if (mirrored) {
        listOf(winRate, battles, tank, nickname)
    } else {
        listOf(nickname, tank, battles, winRate)
    }
}

fun formatNicknameWithClan(player: PlayerSlot, mirrored: Boolean): String {
    if (player.isMissing) {
        return "—"
    }
    val nickname = player.nickname.orEmpty()
    val clanTag = player.clanTag
    if (clanTag.isNullOrBlank()) {
        return nickname.ifEmpty { "—" }
    }
    return if (mirrored) {
        "$nickname [$clanTag]"
    } else {
        "[$clanTag] $nickname"
    }
}
