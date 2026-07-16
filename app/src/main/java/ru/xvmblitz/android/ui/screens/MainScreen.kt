package ru.xvmblitz.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.xvmblitz.android.ui.MainUiState
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
fun MainScreen(
    state: MainUiState,
    statusMessage: String?,
    isCapturing: Boolean,
    onApiKeyClick: () -> Unit,
    onLogout: () -> Unit,
    onRefreshUsage: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onConfigModeChange: (Boolean) -> Unit,
    onOverlayVisibleChange: (Boolean) -> Unit,
    onFloatingButtonEnabledChange: (Boolean) -> Unit,
    onClearBattle: () -> Unit,
    onCaptureClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "XVM Blitz Android",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Кнопка «Статистика» висит поверх игры. Тап — захват, перетаскивание — позиция.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Квота", style = MaterialTheme.typography.titleMedium)
                val usage = state.usage
                if (state.isUsageLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (usage != null) {
                    val remaining = (usage.totalLimit - usage.currentUsage).coerceAtLeast(0)
                    val progress = if (usage.totalLimit == 0) {
                        0f
                    } else {
                        usage.currentUsage.toFloat() / usage.totalLimit.toFloat()
                    }
                    Text("Использовано: ${usage.currentUsage} / ${usage.totalLimit}")
                    Text("Осталось: $remaining")
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Период: ${formatUsageDate(usage.periodStart)} — ${formatUsageDate(usage.periodEnd)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(state.usageError ?: "Нет данных о квоте")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRefreshUsage) { Text("Обновить") }
                    OutlinedButton(onClick = onApiKeyClick) { Text("API ключ") }
                    OutlinedButton(onClick = onLogout) { Text("Выйти") }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Бой", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = onCaptureClick,
                    enabled = !isCapturing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isCapturing) "Идёт захват…" else "Считать статистику")
                }
                if (statusMessage != null) {
                    Text(statusMessage, style = MaterialTheme.typography.bodyMedium)
                }
                if (state.battle.hasBattle) {
                    Text(
                        text = "Есть данные боя: ${state.battle.allies.count { !it.isMissing }} союзников, " +
                            "${state.battle.enemies.count { !it.isMissing }} противников",
                    )
                    OutlinedButton(onClick = onClearBattle, modifier = Modifier.fillMaxWidth()) {
                        Text("Скрыть / очистить бой")
                    }
                } else {
                    Text("Статистика боя ещё не загружена")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Обновление", style = MaterialTheme.typography.titleMedium)
                Text("Текущая версия: ${state.update.currentVersion}")
                if (state.update.isChecking) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (state.update.isUpdateAvailable) {
                    Text(
                        text = "Доступна новая версия",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text("Последняя версия: ${state.update.latestVersion}")
                } else if (state.update.isUpToDate) {
                    Text("Установлена актуальная версия")
                }
                if (state.update.error != null) {
                    Text(
                        text = state.update.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCheckForUpdates,
                        enabled = !state.update.isChecking,
                    ) {
                        Text("Проверить обновление")
                    }
                    if (state.update.isUpdateAvailable) {
                        Button(onClick = onDownloadUpdate) {
                            Text("Скачать")
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Оверлей", style = MaterialTheme.typography.titleMedium)
                SettingSwitchRow(
                    title = "Кнопка поверх экрана",
                    checked = state.settings.floatingButtonEnabled,
                    onCheckedChange = onFloatingButtonEnabledChange,
                )
                SettingSwitchRow(
                    title = "Показывать оверлей",
                    checked = state.settings.overlayVisible,
                    onCheckedChange = onOverlayVisibleChange,
                )
                SettingSwitchRow(
                    title = "Режим настройки панелей",
                    checked = state.settings.configMode,
                    onCheckedChange = onConfigModeChange,
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = SettingRowMinHeight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Размер шрифта: ${"%.0f".format(state.settings.fontSizeSp)}")
                    }
                    Slider(
                        value = state.settings.fontSizeSp,
                        onValueChange = onFontSizeChange,
                        valueRange = 10f..18f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                    )
                }
                Text(
                    text = "Позиции: Союзники(${state.settings.alliesX}, ${state.settings.alliesY}), " +
                        "Противники(${state.settings.enemiesX}, ${state.settings.enemiesY})",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = SettingRowMinHeight),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

private val SettingRowMinHeight = 48.dp

private val usageDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private fun formatUsageDate(raw: String): String {
    return runCatching {
        OffsetDateTime.parse(raw).format(usageDateFormatter)
    }.recoverCatching {
        raw.take(10).let { datePart ->
            val parts = datePart.split("-")
            if (parts.size == 3) {
                "${parts[2]}.${parts[1]}.${parts[0]}"
            } else {
                datePart
            }
        }
    }.getOrDefault(raw)
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SettingRowMinHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
