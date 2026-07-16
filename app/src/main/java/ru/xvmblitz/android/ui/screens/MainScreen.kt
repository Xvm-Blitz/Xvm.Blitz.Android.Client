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

@Composable
fun MainScreen(
    state: MainUiState,
    statusMessage: String?,
    isCapturing: Boolean,
    onAuthClick: () -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "XVM Blitz Android",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Кнопка «Статистика» висит поверх игры. Тап — захват, перетаскивание — позиция.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            OutlinedButton(onClick = onAuthClick) {
                Text(if (state.isAuthorized) "Профиль" else "Войти")
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
                if (state.update.isDownloading) {
                    Text("Скачивание: ${(state.update.downloadProgress * 100).toInt()}%")
                    LinearProgressIndicator(
                        progress = { state.update.downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (state.update.isInstalling) {
                    Text("Установка обновления…")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                        enabled = !state.update.isChecking &&
                            !state.update.isDownloading &&
                            !state.update.isInstalling,
                    ) {
                        Text("Проверить обновление")
                    }
                    if (state.update.isUpdateAvailable) {
                        Button(
                            onClick = onDownloadUpdate,
                            enabled = !state.update.isDownloading && !state.update.isInstalling,
                        ) {
                            Text(
                                when {
                                    state.update.isDownloading -> "Скачивание…"
                                    state.update.isInstalling -> "Установка…"
                                    else -> "Обновить"
                                },
                            )
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
