package ru.xvmblitz.android.overlay

import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import ru.xvmblitz.android.voice.VoicePhase
import ru.xvmblitz.android.voice.VoiceUiState

@Composable
fun VoiceIncomingBanner(
    state: VoiceUiState,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    hitTester: OverlayInteractiveHitTester,
) {
    val actionBounds = remember { mutableStateMapOf<String, RectF>() }
    fun publishBounds() {
        hitTester.replace(actionBounds.values)
    }
    Column(
        modifier = Modifier
            .widthIn(min = 180.dp, max = 280.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xE012151C))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Входящий вызов",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = state.nickname(state.incomingFromPlayerId),
            color = Color(0xFF93CF93),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VoiceOverlayButton(
                icon = Icons.Filled.Call,
                tint = Color(0xFF4CAF50),
                label = "Принять",
                onClick = onAccept,
                onBounds = { rect ->
                    actionBounds["accept"] = rect
                    publishBounds()
                },
            )
            VoiceOverlayButton(
                icon = Icons.Filled.CallEnd,
                tint = Color(0xFFD68585),
                label = "Отклонить",
                onClick = onReject,
                onBounds = { rect ->
                    actionBounds["reject"] = rect
                    publishBounds()
                },
            )
        }
    }
}

@Composable
fun VoiceInviteBar(
    nickname: String,
    onInvite: () -> Unit,
    onDismiss: () -> Unit,
    hitTester: OverlayInteractiveHitTester,
) {
    val actionBounds = remember { mutableStateMapOf<String, RectF>() }
    fun publishBounds() {
        hitTester.replace(actionBounds.values)
    }
    Column(
        modifier = Modifier
            .widthIn(min = 220.dp, max = 300.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xE012151C))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = nickname,
            color = Color(0xFF93CF93),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2E7D32))
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    actionBounds["invite"] = RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
                    publishBounds()
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onInvite,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Пригласить во взвод",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x33FFFFFF))
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    actionBounds["dismiss"] = RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
                    publishBounds()
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Отмена",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun VoiceCallWidget(
    state: VoiceUiState,
    onToggleMute: () -> Unit,
    onHangup: () -> Unit,
    hitTester: OverlayInteractiveHitTester,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    configMode: Boolean = false,
) {
    val actionBounds = remember { mutableStateMapOf<String, RectF>() }
    fun publishBounds() {
        if (configMode) {
            hitTester.replace(emptyList())
            return
        }
        hitTester.replace(actionBounds.values)
    }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.endsAtMs, state.incomingExpiresAtMs, state.phase) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
    }
    val useExample = configMode && state.phase == VoicePhase.Idle
    val nickLines = if (useExample) {
        listOf("Игрок1, Игрок2")
    } else {
        formatNicknameLines(widgetNicknameList(state))
    }
    val countdown = if (useExample) "1:24" else widgetCountdown(state, nowMs)
    val titleSize = voiceCallOverlayTitleFontSizeSp(scaleY)
    val fontSize = voiceCallOverlayFontSizeSp(scaleY)
    val titleStyle = compactOverlayTextStyle(titleSize.sp)
    val fontStyle = compactOverlayTextStyle(fontSize.sp)
    val horizontalPadding = voiceCallOverlayPaddingHorizontalDp(scaleX, scaleY).dp
    val verticalPadding = voiceCallOverlayPaddingVerticalDp(scaleY).dp
    val buttonSize = voiceCallOverlayButtonDp(scaleY)
    val minWidth = voiceCallOverlayMinWidthDp(scaleX, scaleY)
    Box(modifier = Modifier.wrapContentHeight()) {
        Row(
            modifier = Modifier
                .width(minWidth.dp)
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xE012151C))
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalArrangement = Arrangement.spacedBy(horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(verticalPadding),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    nickLines.forEach { line ->
                        Text(
                            text = line,
                            color = Color.White,
                            style = titleStyle.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = countdown,
                    color = Color(0xFFB0B8C4),
                    style = fontStyle,
                    maxLines = 1,
                )
            }
            VoiceOverlayIconButton(
                icon = if (state.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                tint = if (state.muted) Color(0xFFBDBDBD) else Color.White,
                label = "Микрофон",
                background = if (state.muted) Color(0x33FFFFFF) else Color(0xFF2E7D32),
                enabled = !configMode,
                buttonSizeDp = buttonSize,
                onClick = onToggleMute,
                onBounds = { rect ->
                    actionBounds["mute"] = rect
                    publishBounds()
                },
            )
            VoiceOverlayIconButton(
                icon = Icons.Filled.CallEnd,
                tint = Color(0xFFD68585),
                label = "Сброс",
                enabled = !configMode,
                buttonSizeDp = buttonSize,
                onClick = onHangup,
                onBounds = { rect ->
                    actionBounds["hangup"] = rect
                    publishBounds()
                },
            )
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
private fun VoiceOverlayIconButton(
    icon: ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit,
    onBounds: (RectF) -> Unit,
    buttonSizeDp: Float,
    background: Color = Color(0x33FFFFFF),
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .width(buttonSizeDp.dp)
            .fillMaxHeight()
            .heightIn(min = buttonSizeDp.dp)
            .clip(RoundedCornerShape((buttonSizeDp * 0.18f).dp))
            .background(background)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                onBounds(RectF(bounds.left, bounds.top, bounds.right, bounds.bottom))
            }
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.fillMaxSize(0.62f),
        )
    }
}

@Composable
private fun VoiceOverlayButton(
    icon: ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit,
    onBounds: (RectF) -> Unit,
    background: Color = Color(0x33FFFFFF),
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                onBounds(RectF(bounds.left, bounds.top, bounds.right, bounds.bottom))
            }
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

private fun widgetNicknameList(state: VoiceUiState): List<String> {
    val ids = buildList {
        if (state.phase == VoicePhase.OutgoingRinging) {
            state.outgoingTargetPlayerId?.let(::add)
        }
        addAll(state.memberIds.filter { memberId -> memberId != state.selfPlayerId })
        if (state.phase == VoicePhase.InCall) {
            state.outgoingTargetPlayerId?.let(::add)
        }
    }.distinct()
    if (ids.isEmpty()) {
        return listOf(
            if (state.phase == VoicePhase.OutgoingRinging) "Вызов…" else "Голосовой чат",
        )
    }
    return ids.map { playerId -> state.nickname(playerId) }
}

private fun formatNicknameLines(nicks: List<String>): List<String> {
    if (nicks.size in 3..4) {
        return nicks
    }
    return nicks.chunked(2) { pair -> pair.joinToString(", ") }
}

private fun widgetCountdown(state: VoiceUiState, nowMs: Long): String {
    val deadline = state.endsAtMs ?: state.incomingExpiresAtMs
    if (deadline != null) {
        val remaining = ((deadline - nowMs) / 1000L).coerceAtLeast(0L)
        return "${formatMmSs(remaining)}"
    }
    return if (state.phase == VoicePhase.OutgoingRinging) {
        "Ожидание ответа…"
    } else {
        "Разговор"
    }
}

private fun formatMmSs(totalSeconds: Long): String {
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
