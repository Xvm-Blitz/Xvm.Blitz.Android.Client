package ru.xvmblitz.android.overlay

import androidx.compose.ui.graphics.Color

val OverlayTableBackground = Color(0x99000000)

fun winRateColor(winRate: Double?): Color {
    val value = winRate ?: return Color(0xFF333333)
    return when {
        value >= 70.0 -> Color(0xFFB497CC)
        value >= 60.0 -> Color(0xFF85BBF2)
        value >= 50.0 -> Color(0xFF93CF93)
        else -> Color(0xFFD68585)
    }
}

fun formatBattles(numberOfBattles: Int?): String {
    val value = numberOfBattles ?: return "—"
    return if (value >= 1000) {
        String.format("%.1fk", value / 1000.0)
    } else {
        value.toString()
    }
}
