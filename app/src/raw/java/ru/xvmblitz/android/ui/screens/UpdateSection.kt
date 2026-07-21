package ru.xvmblitz.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.xvmblitz.android.ui.components.AdaptiveButton
import ru.xvmblitz.android.ui.components.AdaptiveOutlinedButton
import ru.xvmblitz.android.update.UpdateUiState

@Composable
fun UpdateSection(
    state: UpdateUiState,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Обновление", style = MaterialTheme.typography.titleMedium)
            Text("Текущая версия: ${state.currentVersion}")
            if (state.isChecking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state.isUpdateAvailable) {
                Text(
                    text = "Доступна новая версия",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text("Последняя версия: ${state.latestVersion}")
            } else if (state.isUpToDate) {
                Text("Установлена актуальная версия")
            }
            if (state.isDownloading) {
                Text("Скачивание: ${(state.downloadProgress * 100).toInt()}%")
                LinearProgressIndicator(
                    progress = { state.downloadProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (state.isInstalling) {
                Text("Установка обновления…")
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state.error != null) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AdaptiveOutlinedButton(
                    text = "Проверить обновление",
                    onClick = onCheckForUpdates,
                    enabled = !state.isChecking &&
                        !state.isDownloading &&
                        !state.isInstalling,
                    modifier = Modifier.weight(1f),
                )
                if (state.isUpdateAvailable) {
                    AdaptiveButton(
                        text = when {
                            state.isDownloading -> "Скачивание…"
                            state.isInstalling -> "Установка…"
                            else -> "Обновить"
                        },
                        onClick = onDownloadUpdate,
                        enabled = !state.isDownloading && !state.isInstalling,
                    )
                }
            }
        }
    }
}
