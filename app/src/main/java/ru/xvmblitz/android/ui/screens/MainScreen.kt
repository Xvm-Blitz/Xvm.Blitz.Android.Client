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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.xvmblitz.android.ui.MainUiState

@Composable
fun MainScreen(
    state: MainUiState,
    onAuthClick: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onConfigModeChange: (Boolean) -> Unit,
    onOverlayVisibleChange: (Boolean) -> Unit,
    onUpdateAlliesPosition: (Int, Int) -> Unit,
    onUpdateEnemiesPosition: (Int, Int) -> Unit,
    onOpenGuide: () -> Unit,
) {
    var alliesXText by remember { mutableStateOf(state.settings.alliesX.toString()) }
    var alliesYText by remember { mutableStateOf(state.settings.alliesY.toString()) }
    var enemiesXText by remember { mutableStateOf(state.settings.enemiesX.toString()) }
    var enemiesYText by remember { mutableStateOf(state.settings.enemiesY.toString()) }

    LaunchedEffect(
        state.settings.alliesX,
        state.settings.alliesY,
        state.settings.enemiesX,
        state.settings.enemiesY,
    ) {
        alliesXText = state.settings.alliesX.toString()
        alliesYText = state.settings.alliesY.toString()
        enemiesXText = state.settings.enemiesX.toString()
        enemiesYText = state.settings.enemiesY.toString()
    }

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
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onOpenGuide) {
                Text("Обучение")
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
                    title = "Показывать оверлей",
                    checked = state.settings.overlayVisible,
                    onCheckedChange = onOverlayVisibleChange,
                )
                SettingSwitchRow(
                    title = "Режим настройки панелей",
                    checked = state.settings.configMode,
                    onCheckedChange = onConfigModeChange,
                )
                if (state.settings.configMode) {
                    Text(
                        text = "Экран зафиксирован горизонтально. Перетащите панели или задайте координаты ниже.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text("Координаты союзников", style = MaterialTheme.typography.titleSmall)
                CoordinateRow(
                    xText = alliesXText,
                    yText = alliesYText,
                    onXChange = { alliesXText = it.filter(Char::isDigit).take(5) },
                    onYChange = { alliesYText = it.filter(Char::isDigit).take(5) },
                    onApply = {
                        val x = alliesXText.toIntOrNull() ?: return@CoordinateRow
                        val y = alliesYText.toIntOrNull() ?: return@CoordinateRow
                        onUpdateAlliesPosition(x, y)
                    },
                )

                Text("Координаты противников", style = MaterialTheme.typography.titleSmall)
                CoordinateRow(
                    xText = enemiesXText,
                    yText = enemiesYText,
                    onXChange = { enemiesXText = it.filter(Char::isDigit).take(5) },
                    onYChange = { enemiesYText = it.filter(Char::isDigit).take(5) },
                    onApply = {
                        val x = enemiesXText.toIntOrNull() ?: return@CoordinateRow
                        val y = enemiesYText.toIntOrNull() ?: return@CoordinateRow
                        onUpdateEnemiesPosition(x, y)
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun CoordinateRow(
    xText: String,
    yText: String,
    onXChange: (String) -> Unit,
    onYChange: (String) -> Unit,
    onApply: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = xText,
                onValueChange = onXChange,
                label = { Text("X") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = yText,
                onValueChange = onYChange,
                label = { Text("Y") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedButton(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Применить")
        }
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
