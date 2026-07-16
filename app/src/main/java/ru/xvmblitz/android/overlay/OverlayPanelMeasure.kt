package ru.xvmblitz.android.overlay

import ru.xvmblitz.android.domain.PlayerSlot

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
