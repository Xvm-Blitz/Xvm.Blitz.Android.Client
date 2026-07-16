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

        val previewAllies: List<PlayerSlot> = listOf(
            PlayerSlot(
                tableNumber = 0,
                isMissing = false,
                nicknameWithClanTag = "ИгрокСОченьДлиннымИменем",
                tank = "Т-54 первый образец великолепный",
                numberOfBattles = 0,
                winRate = 52.45,
            ),
            PlayerSlot(
                tableNumber = 1,
                isMissing = false,
                nicknameWithClanTag = "НизкийРейтинг",
                tank = "КВ-1",
                numberOfBattles = 999,
                winRate = 45.23,
            ),
            PlayerSlot(
                tableNumber = 2,
                isMissing = false,
                nicknameWithClanTag = "СреднийРейтинг",
                tank = "T-34-85",
                numberOfBattles = 1000,
                winRate = 55.78,
            ),
            PlayerSlot(
                tableNumber = 3,
                isMissing = false,
                nicknameWithClanTag = "ВысокийРейтинг",
                tank = "ИС-7",
                numberOfBattles = 1001,
                winRate = 65.92,
            ),
            PlayerSlot(
                tableNumber = 4,
                isMissing = false,
                nicknameWithClanTag = "СуперРейтинг",
                tank = "Объект 140",
                numberOfBattles = 7000,
                winRate = 75.34,
            ),
            PlayerSlot(
                tableNumber = 5,
                isMissing = false,
                nicknameWithClanTag = "СреднийРейтинг",
                tank = "T62A",
                numberOfBattles = 2134,
                winRate = 58.45,
            ),
            PlayerSlot(
                tableNumber = 6,
                isMissing = false,
                nicknameWithClanTag = "ИгрокБезТанка",
                tank = "",
                numberOfBattles = 2134,
                winRate = 50.00,
            ),
        )

        val previewEnemies: List<PlayerSlot> = listOf(
            PlayerSlot(
                tableNumber = 0,
                isMissing = false,
                nicknameWithClanTag = "VeryLongEnemyName1234567",
                tank = "Maus with long description",
                numberOfBattles = 47000,
                winRate = 51.23,
            ),
            PlayerSlot(
                tableNumber = 1,
                isMissing = false,
                nicknameWithClanTag = "Enemy1",
                tank = "Tiger II",
                numberOfBattles = 42000,
                winRate = 48.76,
            ),
            PlayerSlot(
                tableNumber = 2,
                isMissing = false,
                nicknameWithClanTag = "Enemy2",
                tank = "IS-4",
                numberOfBattles = 17000,
                winRate = 54.21,
            ),
            PlayerSlot(
                tableNumber = 3,
                isMissing = false,
                nicknameWithClanTag = "Enemy3",
                tank = "E-100",
                numberOfBattles = 45668,
                winRate = 62.45,
            ),
            PlayerSlot(
                tableNumber = 4,
                isMissing = false,
                nicknameWithClanTag = "Enemy4",
                tank = "Jagdpanzer E-100",
                numberOfBattles = 15000,
                winRate = 72.89,
            ),
            PlayerSlot(tableNumber = 5, isMissing = true),
            PlayerSlot(tableNumber = 6, isMissing = true),
        )
    }
}
