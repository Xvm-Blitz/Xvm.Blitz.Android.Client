package ru.xvmblitz.android.domain

import ru.xvmblitz.android.data.api.BattleStatisticsDto

object SessionBattlePlayerResolver {
    fun resolveTankName(playerId: Long, battle: BattleStatisticsDto): String? {
        if (playerId <= 0L) {
            return null
        }
        val player = battle.allies.firstOrNull { ally -> ally.id == playerId }
            ?: battle.enemies.firstOrNull { enemy -> enemy.id == playerId }
        return player?.tank?.trim()?.takeIf { it.isNotEmpty() }
    }
}
