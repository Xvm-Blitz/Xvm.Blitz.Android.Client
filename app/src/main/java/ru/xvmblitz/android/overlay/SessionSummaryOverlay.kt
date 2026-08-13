package ru.xvmblitz.android.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
    val horizontalPadding = sessionSummaryOverlayPaddingHorizontalDp(scaleX, scaleY)
    val verticalPadding = sessionSummaryOverlayPaddingVerticalDp(scaleY)
    val spacing = sessionSummaryOverlaySpacingDp(scaleX, scaleY).dp
    val minWidth = sessionSummaryOverlayMinWidthDp(scaleX, scaleY)
    val minHeight = sessionSummaryOverlayMinHeightDp(scaleY)
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.92f).dp
    val endPadding = if (configMode) horizontalPadding + 14f else horizontalPadding
    val bottomPadding = if (configMode) verticalPadding + 8f else verticalPadding
    Box(
        modifier = Modifier
            .widthIn(min = minWidth.dp, max = maxWidth)
            .heightIn(min = minHeight.dp),
    ) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xB3000000))
                .padding(
                    start = horizontalPadding.dp,
                    top = verticalPadding.dp,
                    end = endPadding.dp,
                    bottom = bottomPadding.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryOverlayText(text = battlesText, fontSize = fontSize)
            SummaryOverlayText(text = "·", fontSize = fontSize, alpha = 0.5f)
            SummaryOverlayText(text = winRateText, fontSize = fontSize, weight = FontWeight.SemiBold)
            SummaryOverlayText(text = "·", fontSize = fontSize, alpha = 0.5f)
            SummaryOverlayText(text = damageText, fontSize = fontSize)
        }
        if (configMode) {
            OverlayResizeSquareHandle(
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
    fontSize: Float,
    alpha: Float = 0.9f,
    weight: FontWeight? = null,
) {
    Text(
        text = text,
        color = Color.White.copy(alpha = alpha),
        fontSize = fontSize.sp,
        fontWeight = weight,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}
