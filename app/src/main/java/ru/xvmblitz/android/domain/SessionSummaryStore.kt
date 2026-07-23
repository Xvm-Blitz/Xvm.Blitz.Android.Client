package ru.xvmblitz.android.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SessionSummaryOverlayState(
    val battlesText: String = "—",
    val winRateText: String = "—",
    val damageText: String = "—",
)

class SessionSummaryStore {
    private val _overlay = MutableStateFlow(SessionSummaryOverlayState())
    val overlay: StateFlow<SessionSummaryOverlayState> = _overlay.asStateFlow()

    fun applySummary(totalBattles: Int, winRate: Double, averageDamage: Double) {
        _overlay.value = SessionSummaryOverlayState(
            battlesText = "$totalBattles б.",
            winRateText = "${winRate.formatOneDecimal()}%",
            damageText = "${averageDamage.roundToInt()} ур.",
        )
    }

    fun clear() {
        _overlay.value = SessionSummaryOverlayState()
    }

    private fun Double.formatOneDecimal(): String {
        val rounded = (this * 10).toInt() / 10.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toInt().toString()
        } else {
            rounded.toString()
        }
    }

    private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()
}
