package ru.xvmblitz.android.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.xvmblitz.android.domain.PlayerSlot

@Composable
fun OverlayPanel(
    title: String,
    players: List<PlayerSlot>,
    scaleX: Float,
    scaleY: Float,
    configMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val widthScale = coerceOverlayScaleX(scaleX)
    val heightScale = coerceOverlayScaleY(scaleY)
    val fontSizeSp = overlayFontSizeSp(heightScale)
    val panelWidth = (OverlayBasePanelWidthDp * widthScale).dp
    val cornerRadius = (8f * minOf(widthScale, heightScale)).dp
    val contentPaddingX = (8f * widthScale).dp
    val contentPaddingY = overlayContentPaddingYDp(heightScale).dp
    val rowSpacing = overlayRowSpacingDp(heightScale).dp

    Box(modifier = modifier.width(panelWidth)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xCC000000), RoundedCornerShape(cornerRadius))
                .padding(horizontal = contentPaddingX, vertical = contentPaddingY),
            verticalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = (fontSizeSp + 1).sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = rowSpacing),
            )
            players.forEach { player ->
                PlayerRow(
                    player = player,
                    fontSizeSp = fontSizeSp,
                    scaleX = widthScale,
                    scaleY = heightScale,
                )
            }
        }
        if (configMode) {
            PanelEdgeHandle(
                orientation = HandleOrientation.Horizontal,
                scale = widthScale,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
            PanelEdgeHandle(
                orientation = HandleOrientation.Vertical,
                scale = heightScale,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            PanelCornerHandle(
                scale = minOf(widthScale, heightScale),
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

private enum class HandleOrientation { Horizontal, Vertical }

@Composable
private fun PanelEdgeHandle(
    orientation: HandleOrientation,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val handleThickness = (10f * scale.coerceIn(0.85f, 1.4f)).dp
    val handleLength = (28f * scale.coerceIn(0.85f, 1.4f)).dp
    Canvas(
        modifier = when (orientation) {
            HandleOrientation.Horizontal -> modifier
                .width(handleThickness)
                .height(handleLength)
                .padding(2.dp)
            HandleOrientation.Vertical -> modifier
                .width(handleLength)
                .height(handleThickness)
                .padding(2.dp)
        },
    ) {
        val color = Color(0xE0FFFFFF)
        val strokeWidth = 2.5.dp.toPx()
        when (orientation) {
            HandleOrientation.Horizontal -> {
                val centerX = size.width / 2f
                drawLine(
                    color = color,
                    start = Offset(centerX, size.height * 0.15f),
                    end = Offset(centerX, size.height * 0.85f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            HandleOrientation.Vertical -> {
                val centerY = size.height / 2f
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.15f, centerY),
                    end = Offset(size.width * 0.85f, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun PanelCornerHandle(
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val handleSize = (OverlayResizeHandleDp * scale.coerceIn(0.85f, 1.4f)).dp
    Canvas(
        modifier = modifier
            .size(handleSize)
            .padding(4.dp),
    ) {
        val strokeWidth = 2.5.dp.toPx()
        val color = Color(0xE0FFFFFF)
        val inset = 2.dp.toPx()
        val step = size.minDimension / 3.5f
        for (index in 0..2) {
            val offset = inset + step * index
            drawLine(
                color = color,
                start = Offset(size.width - inset, size.height - offset),
                end = Offset(size.width - offset, size.height - inset),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun PlayerRow(
    player: PlayerSlot,
    fontSizeSp: Float,
    scaleX: Float,
    scaleY: Float,
) {
    val textSize = fontSizeSp.sp
    val horizontalPadding = (6f * scaleX).dp
    val verticalPadding = overlayRowVerticalPaddingDp(scaleY).dp
    val cellSpacing = (8f * scaleX).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x33000000), RoundedCornerShape((4f * minOf(scaleX, scaleY)).dp))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(cellSpacing),
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
