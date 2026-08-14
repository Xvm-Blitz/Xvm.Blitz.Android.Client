package ru.xvmblitz.android.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

@Composable
fun SessionSummaryOverlayContent(
    battlesText: String,
    winRateText: String,
    damageText: String,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    configMode: Boolean = false,
) {
    val fontSize = sessionSummaryOverlayFontSizeSp(scaleY)
    val textStyle = compactOverlayTextStyle(fontSize.sp)
    val horizontalPadding = sessionSummaryOverlayPaddingHorizontalDp(scaleX, scaleY)
    val verticalPadding = sessionSummaryOverlayPaddingVerticalDp(scaleY)
    val spacing = sessionSummaryOverlaySpacingDp(scaleX, scaleY).dp
    val minWidth = sessionSummaryOverlayMinWidthDp(scaleX, scaleY)
    Box(
        modifier = Modifier
            .width(minWidth.dp)
            .wrapContentHeight(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xB3000000))
                .padding(horizontal = horizontalPadding.dp, vertical = verticalPadding.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryOverlayText(text = battlesText, style = textStyle)
            SummaryOverlayText(text = "·", style = textStyle, alpha = 0.5f)
            SummaryOverlayText(text = winRateText, style = textStyle, weight = FontWeight.SemiBold)
            SummaryOverlayText(text = "·", style = textStyle, alpha = 0.5f)
            SummaryOverlayText(text = damageText, style = textStyle)
        }
        if (configMode) {
            OverlayResizeCornerHandle(
                scale = minOf(scaleX, scaleY).coerceIn(0.85f, 1.4f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .zIndex(2f),
            )
        }
    }
}

@Composable
private fun SummaryOverlayText(
    text: String,
    style: TextStyle,
    alpha: Float = 0.9f,
    weight: FontWeight? = null,
) {
    Text(
        text = text,
        color = Color.White.copy(alpha = alpha),
        style = if (weight != null) style.copy(fontWeight = weight) else style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}
