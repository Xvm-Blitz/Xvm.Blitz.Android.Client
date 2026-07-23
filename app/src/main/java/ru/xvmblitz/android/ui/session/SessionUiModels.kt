package ru.xvmblitz.android.ui.session

import androidx.compose.ui.graphics.Color
import ru.xvmblitz.android.data.api.SessionBattleBriefDto
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val winResultBackground = Color(0xFF1E3D24)
private val lossResultBackground = Color(0xFF3D1E1E)
private val sessionDateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

data class SessionListItem(
    val id: String,
    val createdAt: String,
    val endedAt: String?,
) {
    val isActive: Boolean
        get() = endedAt.isNullOrBlank()

    val displayText: String
        get() {
            val created = formatDateTime(createdAt)
            if (isActive) {
                return "Активная · $created"
            }
            val ended = formatDateTime(endedAt.orEmpty())
            return "Завершена · $created — $ended"
        }

    companion object {
        fun fromDto(dto: ru.xvmblitz.android.data.api.RestoredSessionDto): SessionListItem =
            SessionListItem(
                id = dto.id,
                createdAt = dto.createdAt,
                endedAt = dto.endedAt,
            )
    }
}

data class SessionBattleListItem(
    val id: Long,
    val createdAt: String,
    val tankName: String,
    val resultText: String,
    val resultBackground: Color,
    val fragsText: String,
    val damageText: String,
    val startedAtText: String,
) {
    companion object {
        fun fromDto(battle: SessionBattleBriefDto): SessionBattleListItem =
            SessionBattleListItem(
                id = battle.id,
                createdAt = battle.createdAt,
                tankName = battle.tankName?.takeIf { it.isNotBlank() } ?: "—",
                resultText = formatResult(battle.result, battle.endedAt),
                resultBackground = resolveResultBackground(battle.result, battle.endedAt),
                fragsText = battle.frags?.toString() ?: "—",
                damageText = battle.damageDealt?.toString() ?: "—",
                startedAtText = formatDateTime(battle.createdAt),
            )
    }
}

data class SessionUiState(
    val nickname: String = "",
    val secretKey: String = "",
    val availableSessions: List<SessionListItem> = emptyList(),
    val selectedSession: SessionListItem? = null,
    val battles: List<SessionBattleListItem> = emptyList(),
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val isStatusError: Boolean = false,
    val historyPage: Int = 1,
    val historyTotalCount: Int = 0,
    val isBattlesLoading: Boolean = false,
    val hasSummary: Boolean = false,
    val totalSummary: String = "",
    val winRateSummary: String = "",
    val averageDamageSummary: String = "",
    val averageFragsSummary: String = "",
    val isSummaryOverlayVisible: Boolean = false,
    val isTrialStatistics: Boolean = false,
    val isSecretKeyCopiedHighlight: Boolean = false,
) {
    val hasSelectedSession: Boolean
        get() = selectedSession != null

    val canEndSelectedSession: Boolean
        get() = !isBusy && selectedSession?.isActive == true

    val hasNoSessionBattles: Boolean
        get() = hasSelectedSession && !isBattlesLoading && battles.isEmpty() && !isTrialStatistics

    val showStatisticsDisclaimer: Boolean
        get() = hasSelectedSession && (hasSummary || battles.isNotEmpty())

    val summaryOverlayButtonText: String
        get() = if (isSummaryOverlayVisible) {
            "Суммаризация: вкл"
        } else {
            "Суммаризация: выкл"
        }

    val battlesHeader: String
        get() = if (selectedSession == null) {
            "Бои сессии"
        } else {
            "Бои сессии (${battles.size})"
        }

    val historyTotalPages: Int
        get() = maxOf(1, (historyTotalCount + SESSION_HISTORY_PAGE_SIZE - 1) / SESSION_HISTORY_PAGE_SIZE)

    val historyPageText: String
        get() = "Стр. $historyPage / $historyTotalPages"

    val hasPreviousHistoryPage: Boolean
        get() = historyPage > 1

    val hasNextHistoryPage: Boolean
        get() = historyPage < historyTotalPages

    val hasStatusError: Boolean
        get() = isStatusError && !statusMessage.isNullOrBlank()

    val hasStatusSuccess: Boolean
        get() = !isStatusError && !statusMessage.isNullOrBlank()

    companion object {
        const val SESSION_HISTORY_PAGE_SIZE = 10
        const val STATISTICS_DISCLAIMER_TEXT =
            "Точность расширенных расчётов не гарантируется и может не совпадать с реальностью."
    }
}

private fun resolveResultBackground(result: String?, endedAt: String?): Color {
    if (endedAt.isNullOrBlank()) {
        return Color.Transparent
    }
    return when (result) {
        "win", "won" -> winResultBackground
        "loss", "lost" -> lossResultBackground
        else -> Color.Transparent
    }
}

private fun formatResult(result: String?, endedAt: String?): String {
    if (endedAt.isNullOrBlank()) {
        return "В бою"
    }
    return when (result) {
        "win", "won" -> "Победа"
        "loss", "lost" -> "Поражение"
        "draw" -> "Ничья"
        else -> "—"
    }
}

private fun formatDateTime(raw: String): String =
    runCatching {
        OffsetDateTime.parse(raw)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(sessionDateTimeFormatter)
    }.getOrElse { raw }
