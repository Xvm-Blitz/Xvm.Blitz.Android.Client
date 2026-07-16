package ru.xvmblitz.android.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.xvmblitz.android.data.api.BattlePlayerStatisticsDto
import ru.xvmblitz.android.data.api.BattleStatisticsDto

data class PlayerSlot(
    val tableNumber: Int,
    val isMissing: Boolean,
    val nicknameWithClanTag: String? = null,
    val tank: String? = null,
    val numberOfBattles: Int? = null,
    val winRate: Double? = null,
)

data class BattleUiState(
    val allies: List<PlayerSlot> = emptyList(),
    val enemies: List<PlayerSlot> = emptyList(),
    val hasBattle: Boolean = false,
)

class BattleStatisticsStore {
    private val _state = MutableStateFlow(BattleUiState())
    val state: StateFlow<BattleUiState> = _state.asStateFlow()

    fun publish(battle: BattleStatisticsDto) {
        _state.value = BattleUiState(
            allies = mapSide(battle.allies),
            enemies = mapSide(battle.enemies),
            hasBattle = true,
        )
    }

    fun clear() {
        _state.value = BattleUiState()
    }

    private fun mapSide(players: List<BattlePlayerStatisticsDto>): List<PlayerSlot> {
        val byTable = players.groupBy { it.tableNumber }
        return (0 until TABLE_SLOTS).map { tableNumber ->
            val group = byTable[tableNumber].orEmpty()
            if (group.isEmpty()) {
                PlayerSlot(tableNumber = tableNumber, isMissing = true)
            } else {
                val player = group.first()
                val nicknameWithClanTag = when {
                    player.clanTag.isNullOrBlank() -> player.nickname
                    else -> "[${player.clanTag}] ${player.nickname}"
                }
                PlayerSlot(
                    tableNumber = tableNumber,
                    isMissing = false,
                    nicknameWithClanTag = nicknameWithClanTag,
                    tank = player.tank ?: "неизвестный танк",
                    numberOfBattles = player.numberOfBattles,
                    winRate = player.winRatePercents,
                )
            }
        }
    }

    companion object {
        const val TABLE_SLOTS = 7

        val previewAllies: List<PlayerSlot> = (0 until TABLE_SLOTS).map { tableNumber ->
            PlayerSlot(
                tableNumber = tableNumber,
                isMissing = false,
                nicknameWithClanTag = "Ally$tableNumber",
                tank = "Танк",
                numberOfBattles = 1200 + tableNumber * 100,
                winRate = 45.0 + tableNumber * 4,
            )
        }

        val previewEnemies: List<PlayerSlot> = (0 until TABLE_SLOTS).map { tableNumber ->
            PlayerSlot(
                tableNumber = tableNumber,
                isMissing = false,
                nicknameWithClanTag = "Enemy$tableNumber",
                tank = "Танк",
                numberOfBattles = 800 + tableNumber * 150,
                winRate = 42.0 + tableNumber * 5,
            )
        }
    }
}
