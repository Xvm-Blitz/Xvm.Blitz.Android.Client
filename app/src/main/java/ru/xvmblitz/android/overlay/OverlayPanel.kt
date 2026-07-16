package ru.xvmblitz.android.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.xvmblitz.android.domain.PlayerSlot

private const val BaseFontSizeSp = 12f
private val BasePanelWidth = 280.dp

@Composable
fun OverlayPanel(
    title: String,
    players: List<PlayerSlot>,
    fontSizeSp: Float,
    modifier: Modifier = Modifier,
) {
    val scale = (fontSizeSp / BaseFontSizeSp).coerceIn(0.75f, 2f)
    val panelWidth = BasePanelWidth * scale

    Column(
        modifier = modifier
            .width(panelWidth)
            .background(Color(0xCC000000), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = (fontSizeSp + 1).sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        players.forEach { player ->
            PlayerRow(player = player, fontSizeSp = fontSizeSp)
        }
    }
}

@Composable
private fun PlayerRow(
    player: PlayerSlot,
    fontSizeSp: Float,
) {
    val textSize = fontSizeSp.sp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x33000000), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (player.isMissing) {
            Text("—", color = Color.White, fontSize = textSize, modifier = Modifier.weight(1.4f))
            Text("—", color = Color.White, fontSize = textSize, modifier = Modifier.weight(1.2f))
            Text("—", color = Color.White, fontSize = textSize, modifier = Modifier.weight(0.7f))
            Text("—", color = Color.White, fontSize = textSize, modifier = Modifier.weight(0.8f))
        } else {
            Text(
                text = player.nicknameWithClanTag.orEmpty(),
                color = Color.White,
                fontSize = textSize,
                maxLines = 1,
                modifier = Modifier.weight(1.4f),
            )
            Text(
                text = player.tank.orEmpty(),
                color = Color.White,
                fontSize = textSize,
                maxLines = 1,
                modifier = Modifier.weight(1.2f),
            )
            Text(
                text = formatBattles(player.numberOfBattles),
                color = Color.White,
                fontSize = textSize,
                modifier = Modifier.weight(0.7f),
            )
            Text(
                text = player.winRate?.let { String.format("%.2f%%", it) } ?: "—",
                color = winRateColor(player.winRate),
                fontSize = textSize,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(0.8f),
            )
        }
    }
}
