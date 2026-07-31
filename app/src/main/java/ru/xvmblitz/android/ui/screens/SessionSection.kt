package ru.xvmblitz.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import ru.xvmblitz.android.ui.components.AdaptiveButton
import ru.xvmblitz.android.ui.components.AdaptiveOutlinedButton
import ru.xvmblitz.android.ui.session.SessionBattleListItem
import ru.xvmblitz.android.ui.session.SessionListItem
import ru.xvmblitz.android.ui.session.SessionUiState

@Composable
fun SessionSection(
    session: SessionUiState,
    onSelectSession: (SessionListItem?) -> Unit,
    onStartSession: () -> Unit,
    onRestoreSessions: () -> Unit,
    onEndSession: () -> Unit,
    onPreviousHistoryPage: () -> Unit,
    onNextHistoryPage: () -> Unit,
    onRefreshBattles: () -> Unit,
    onPreviousBattlesPage: () -> Unit,
    onNextBattlesPage: () -> Unit,
    onToggleSummaryOverlay: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Сессии боёв", style = MaterialTheme.typography.titleMedium)
        SessionWarningBanner()

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("История сессий", style = MaterialTheme.typography.titleSmall)
                SessionHistoryDropdown(
                    session = session,
                    onSelectSession = onSelectSession,
                    onPreviousHistoryPage = onPreviousHistoryPage,
                    onNextHistoryPage = onNextHistoryPage,
                )
            }
        }

        if (session.hasSelectedSession) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SessionBattlesSection(
                        session = session,
                        onRefreshBattles = onRefreshBattles,
                        onPreviousBattlesPage = onPreviousBattlesPage,
                        onNextBattlesPage = onNextBattlesPage,
                        onToggleSummaryOverlay = onToggleSummaryOverlay,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Действия", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AdaptiveButton(
                        text = "Начать сессию",
                        onClick = onStartSession,
                        enabled = !session.isBusy,
                        modifier = Modifier.weight(1f),
                    )
                    AdaptiveOutlinedButton(
                        text = "История",
                        onClick = onRestoreSessions,
                        enabled = !session.isBusy,
                        modifier = Modifier.weight(1f),
                    )
                }
                AdaptiveOutlinedButton(
                    text = "Завершить сессию",
                    onClick = onEndSession,
                    enabled = session.canEndSelectedSession,
                    modifier = Modifier.fillMaxWidth(),
                )
                SessionStatusTexts(session = session)
            }
        }
    }
}

@Composable
private fun SessionWarningBanner() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Сессия действует не дольше 24 часов",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = "По истечении 24 часов незавершённая сессия будет автоматически закрыта, если в ней нет начавшихся боёв.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionHistoryDropdown(
    session: SessionUiState,
    onSelectSession: (SessionListItem?) -> Unit,
    onPreviousHistoryPage: () -> Unit,
    onNextHistoryPage: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (!session.isBusy) expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = session.selectedSession?.displayText ?: "Нет сессий в истории",
            onValueChange = {},
            readOnly = true,
            label = { Text("Сессия") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = !session.isBusy,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (session.availableSessions.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Нет сессий") },
                    onClick = { expanded = false },
                )
            } else {
                session.availableSessions.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.displayText, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            onSelectSession(item)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AdaptiveOutlinedButton(
            text = "←",
            onClick = onPreviousHistoryPage,
            enabled = session.hasPreviousHistoryPage && !session.isBusy,
        )
        Text(session.historyPageText, style = MaterialTheme.typography.bodyMedium)
        AdaptiveOutlinedButton(
            text = "→",
            onClick = onNextHistoryPage,
            enabled = session.hasNextHistoryPage && !session.isBusy,
        )
    }
}

@Composable
private fun SessionBattlesSection(
    session: SessionUiState,
    onRefreshBattles: () -> Unit,
    onPreviousBattlesPage: () -> Unit,
    onNextBattlesPage: () -> Unit,
    onToggleSummaryOverlay: () -> Unit,
) {
    Text(session.battlesHeader, style = MaterialTheme.typography.titleSmall)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AdaptiveOutlinedButton(
            text = "Обновить",
            onClick = onRefreshBattles,
            enabled = !session.isBattlesLoading,
            modifier = Modifier.weight(1f),
        )
        AdaptiveOutlinedButton(
            text = session.summaryOverlayButtonText,
            onClick = onToggleSummaryOverlay,
            modifier = Modifier.weight(1f),
            colors = if (session.isSummaryOverlayVisible) {
                ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                ButtonDefaults.outlinedButtonColors()
            },
        )
    }
    if (session.isBattlesLoading) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (session.isTrialStatistics) {
        if (!session.hasSummary) {
            Text(
                text = "В этой сессии пока нет боёв",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    } else if (session.hasNoSessionBattles) {
        Text(
            text = "В этой сессии пока нет боёв",
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        SessionBattlesTable(battles = session.battles)
        if (session.showBattlesPagination) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdaptiveOutlinedButton(
                    text = "←",
                    onClick = onPreviousBattlesPage,
                    enabled = session.hasPreviousBattlesPage && !session.isBattlesLoading,
                )
                Text(session.battlesPageText, style = MaterialTheme.typography.bodyMedium)
                AdaptiveOutlinedButton(
                    text = "→",
                    onClick = onNextBattlesPage,
                    enabled = session.hasNextBattlesPage && !session.isBattlesLoading,
                )
            }
        }
    }
    if (session.hasSummary) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(session.totalSummary, style = MaterialTheme.typography.bodySmall)
            Text(session.winRateSummary, style = MaterialTheme.typography.bodySmall)
            Text(session.averageDamageSummary, style = MaterialTheme.typography.bodySmall)
            Text(session.averageFragsSummary, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (session.showStatisticsDisclaimer) {
        Text(
            text = SessionUiState.STATISTICS_DISCLAIMER_TEXT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionBattlesTable(battles: List<SessionBattleListItem>) {
    val horizontalScroll = rememberScrollState()
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val headerStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val bodyStyle = MaterialTheme.typography.bodySmall
    val columnGap = 8.dp
    val resultPadding = 8.dp

    val columnWidths = remember(battles, headerStyle, bodyStyle, density) {
        fun measureWidth(text: String, isHeader: Boolean): Dp {
            val style = if (isHeader) headerStyle else bodyStyle
            val widthPx = textMeasurer.measure(text = text, style = style).size.width
            return with(density) { widthPx.toDp() }
        }

        var startedAt = measureWidth("Начало", isHeader = true)
        var tank = measureWidth("Танк", isHeader = true)
        var result = measureWidth("Результат", isHeader = true) + resultPadding
        var frags = measureWidth("Фраги", isHeader = true)
        var damage = measureWidth("Урон", isHeader = true)

        battles.forEach { battle ->
            startedAt = max(startedAt, measureWidth(battle.startedAtText, isHeader = false))
            tank = max(tank, measureWidth(battle.tankName, isHeader = false))
            result = max(result, measureWidth(battle.resultText, isHeader = false) + resultPadding)
            frags = max(frags, measureWidth(battle.fragsText, isHeader = false))
            damage = max(damage, measureWidth(battle.damageText, isHeader = false))
        }

        SessionBattleColumnWidths(startedAt, tank, result, frags, damage)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScroll),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SessionBattleTableRow(
            startedAt = "Начало",
            tank = "Танк",
            result = "Результат",
            frags = "Фраги",
            damage = "Урон",
            widths = columnWidths,
            columnGap = columnGap,
            isHeader = true,
        )
        battles.forEach { battle ->
            SessionBattleTableRow(
                startedAt = battle.startedAtText,
                tank = battle.tankName,
                result = battle.resultText,
                frags = battle.fragsText,
                damage = battle.damageText,
                widths = columnWidths,
                columnGap = columnGap,
                resultBackground = battle.resultBackground,
            )
        }
    }
}

private data class SessionBattleColumnWidths(
    val startedAt: Dp,
    val tank: Dp,
    val result: Dp,
    val frags: Dp,
    val damage: Dp,
)

@Composable
private fun SessionBattleTableRow(
    startedAt: String,
    tank: String,
    result: String,
    frags: String,
    damage: String,
    widths: SessionBattleColumnWidths,
    columnGap: Dp,
    isHeader: Boolean = false,
    resultBackground: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
) {
    val style = if (isHeader) {
        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    } else {
        MaterialTheme.typography.bodySmall
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(columnGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(startedAt, style = style, modifier = Modifier.width(widths.startedAt), maxLines = 1)
        Text(tank, style = style, modifier = Modifier.width(widths.tank), maxLines = 1)
        Box(
            modifier = Modifier
                .width(widths.result)
                .clip(RoundedCornerShape(4.dp))
                .background(resultBackground)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(result, style = style, maxLines = 1)
        }
        Text(frags, style = style, modifier = Modifier.width(widths.frags), maxLines = 1)
        Text(damage, style = style, modifier = Modifier.width(widths.damage), maxLines = 1)
    }
}

@Composable
private fun SessionStatusTexts(session: SessionUiState) {
    if (session.hasStatusError) {
        Text(
            text = session.statusMessage.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (session.hasStatusSuccess) {
        Text(
            text = session.statusMessage.orEmpty(),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
