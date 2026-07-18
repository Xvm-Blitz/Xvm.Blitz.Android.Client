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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.xvmblitz.android.domain.PlayerSlot

@Composable
fun OverlayPanel(
    players: List<PlayerSlot>,
    scaleX: Float,
    scaleY: Float,
    configMode: Boolean = false,
    mirroredColumns: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val widthScale = coerceOverlayScaleX(scaleX)
    val heightScale = coerceOverlayScaleY(scaleY)
    val fontSizeSp = overlayFontSizeSp(heightScale)
    val fontScale = fontSizeSp / OverlayBaseFontSizeSp
    val panelWidth = (OverlayBasePanelWidthDp * widthScale * fontScale).dp
    val cornerRadius = (8f * minOf(widthScale, heightScale)).dp
    val rowSpacing = with(density) { OverlayRowSpacingPx.toDp() }
    val digitWidthDp = fontSizeSp * 0.62f
    val winRateColumnWidth = (digitWidthDp * OverlayWinRateColumnChars).dp
    val battlesColumnWidth = (digitWidthDp * OverlayBattlesColumnChars).dp

    Box(modifier = modifier.width(panelWidth)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius))
                .background(OverlayTableBackground),
            verticalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            players.forEach { player ->
                PlayerRow(
                    player = player,
                    fontSizeSp = fontSizeSp,
                    scaleX = widthScale * fontScale,
                    scaleY = heightScale,
                    mirroredColumns = mirroredColumns,
                    winRateColumnWidth = winRateColumnWidth,
                    battlesColumnWidth = battlesColumnWidth,
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
    mirroredColumns: Boolean,
    winRateColumnWidth: Dp,
    battlesColumnWidth: Dp,
) {
    val textSize = fontSizeSp.sp
    val textStyle = compactOverlayTextStyle(textSize)
    val cellSpacing = (4f * scaleX).dp
    val cells = playerRowCells(player, mirroredColumns)
    val cellCorner = (4f * minOf(scaleX, scaleY)).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = OverlayTableBackground,
                    cornerRadius = CornerRadius(cellCorner.toPx(), cellCorner.toPx()),
                    blendMode = BlendMode.Src,
                )
            },
        horizontalArrangement = Arrangement.spacedBy(cellSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mirroredColumns) {
            CompactCell(
                text = cells[0],
                textStyle = textStyle.copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.End),
                modifier = Modifier.width(winRateColumnWidth),
                color = if (player.isMissing) Color.White else winRateColor(player.winRate),
                textAlign = TextAlign.End,
            )
            CompactCell(
                text = cells[1],
                textStyle = textStyle.copy(textAlign = TextAlign.End),
                modifier = Modifier.width(battlesColumnWidth),
                textAlign = TextAlign.End,
            )
            CompactCell(
                text = cells[2],
                textStyle = textStyle.copy(textAlign = TextAlign.Start),
                modifier = Modifier.weight(1.2f),
                textAlign = TextAlign.Start,
            )
            CompactCell(
                text = cells[3],
                textStyle = textStyle.copy(textAlign = TextAlign.End),
                modifier = Modifier.weight(2.4f),
                textAlign = TextAlign.End,
            )
        } else {
            CompactCell(
                text = cells[0],
                textStyle = textStyle.copy(textAlign = TextAlign.Start),
                modifier = Modifier.weight(2.4f),
                textAlign = TextAlign.Start,
            )
            CompactCell(
                text = cells[1],
                textStyle = textStyle.copy(textAlign = TextAlign.End),
                modifier = Modifier.weight(1.2f),
                textAlign = TextAlign.End,
            )
            CompactCell(
                text = cells[2],
                textStyle = textStyle.copy(textAlign = TextAlign.End),
                modifier = Modifier.width(battlesColumnWidth),
                textAlign = TextAlign.End,
            )
            CompactCell(
                text = cells[3],
                textStyle = textStyle.copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.Start),
                modifier = Modifier.width(winRateColumnWidth),
                color = if (player.isMissing) Color.White else winRateColor(player.winRate),
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun CompactCell(
    text: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        color = color,
        style = textStyle,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier,
    )
}

private fun compactOverlayTextStyle(fontSize: TextUnit): TextStyle {
    return TextStyle(
        fontSize = fontSize,
        lineHeight = fontSize,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )
}
