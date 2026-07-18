package ru.xvmblitz.android.overlay

import ru.xvmblitz.android.domain.PlayerSlot

const val OverlayWinRateColumnChars = 4
const val OverlayBattlesColumnChars = 4

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
        player.winRate?.let { value -> String.format("%.0f%%", value) } ?: "—"
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
