package ru.xvmblitz.android.domain

import ru.xvmblitz.android.data.api.BattleStatisticsDto

object SessionBattlePlayerResolver {
    fun resolveTankName(sessionNickname: String, battle: BattleStatisticsDto): String? {
        if (sessionNickname.isBlank()) {
            return null
        }
        val player = battle.allies.firstOrNull { ally ->
            nicknamesMatch(sessionNickname, ally.nickname)
        }
        return player?.tank?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun nicknamesMatch(sessionNickname: String, playerNickname: String?): Boolean =
        sessionNickname.trim().equals(playerNickname?.trim().orEmpty(), ignoreCase = true)
}
