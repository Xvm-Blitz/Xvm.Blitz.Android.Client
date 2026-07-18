package ru.xvmblitz.android.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import ru.xvmblitz.android.ui.MainUiState
import ru.xvmblitz.android.ui.components.AdaptiveButton
import ru.xvmblitz.android.ui.components.AdaptiveOutlinedButton

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
    onCloseApp: () -> Unit,
) {
    var alliesXText by remember { mutableStateOf(state.settings.alliesX.toString()) }
    var alliesYText by remember { mutableStateOf(state.settings.alliesY.toString()) }
    var enemiesXText by remember { mutableStateOf(state.settings.enemiesX.toString()) }
    var enemiesYText by remember { mutableStateOf(state.settings.enemiesY.toString()) }
    val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val configuration = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    var configToggleScreenY by remember { mutableFloatStateOf(0f) }
    var preserveConfigToggleScreenY by remember { mutableStateOf<Float?>(null) }

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

    suspend fun restoreConfigToggleScreenY() {
        val targetScreenY = preserveConfigToggleScreenY ?: return
        withFrameNanos { }
        withFrameNanos { }
        val delta = (configToggleScreenY - targetScreenY).roundToInt()
        if (delta != 0) {
            scrollState.scrollTo((scrollState.value + delta).coerceAtLeast(0))
        }
        preserveConfigToggleScreenY = null
    }

    LaunchedEffect(configuration.orientation) {
        restoreConfigToggleScreenY()
    }

    LaunchedEffect(state.settings.configMode) {
        if (preserveConfigToggleScreenY == null) {
            return@LaunchedEffect
        }
        delay(350)
        restoreConfigToggleScreenY()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AdaptiveOutlinedButton(
                text = "Обучение",
                onClick = onOpenGuide,
                modifier = Modifier.weight(1f),
            )
            AdaptiveOutlinedButton(
                text = if (state.isAuthorized) "Профиль" else "Войти",
                onClick = onAuthClick,
                modifier = Modifier.weight(1f),
            )
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AdaptiveOutlinedButton(
                        text = "Проверить обновление",
                        onClick = onCheckForUpdates,
                        enabled = !state.update.isChecking &&
                            !state.update.isDownloading &&
                            !state.update.isInstalling,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.update.isUpdateAvailable) {
                        AdaptiveButton(
                            text = when {
                                state.update.isDownloading -> "Скачивание…"
                                state.update.isInstalling -> "Установка…"
                                else -> "Обновить"
                            },
                            onClick = onDownloadUpdate,
                            enabled = !state.update.isDownloading && !state.update.isInstalling,
                        )
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
                    onCheckedChange = { enabled ->
                        preserveConfigToggleScreenY = configToggleScreenY
                        onConfigModeChange(enabled)
                    },
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        configToggleScreenY = coordinates.positionInRoot().y
                    },
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
                        val x = alliesXText.toIntOrNull() ?: return@CoordinateRow false
                        val y = alliesYText.toIntOrNull() ?: return@CoordinateRow false
                        onUpdateAlliesPosition(x, y)
                        true
                    },
                )

                Text("Координаты противников", style = MaterialTheme.typography.titleSmall)
                CoordinateRow(
                    xText = enemiesXText,
                    yText = enemiesYText,
                    onXChange = { enemiesXText = it.filter(Char::isDigit).take(5) },
                    onYChange = { enemiesYText = it.filter(Char::isDigit).take(5) },
                    onApply = {
                        val x = enemiesXText.toIntOrNull() ?: return@CoordinateRow false
                        val y = enemiesYText.toIntOrNull() ?: return@CoordinateRow false
                        onUpdateEnemiesPosition(x, y)
                        true
                    },
                )
            }
        }

        AdaptiveOutlinedButton(
            text = "Закрыть приложение",
            onClick = onCloseApp,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun CoordinateRow(
    xText: String,
    yText: String,
    onXChange: (String) -> Unit,
    onYChange: (String) -> Unit,
    onApply: () -> Boolean,
) {
    var savedPulse by remember { mutableIntStateOf(0) }
    var showSaved by remember { mutableStateOf(false) }
    val checkScale by animateFloatAsState(
        targetValue = if (showSaved) 1f else 0.85f,
        animationSpec = tween(220),
        label = "saved-scale",
    )

    LaunchedEffect(savedPulse) {
        if (savedPulse <= 0) return@LaunchedEffect
        showSaved = true
        delay(1_400)
        showSaved = false
    }

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
        AdaptiveButton(
            text = if (showSaved) "Сохранено" else "Применить",
            onClick = {
                if (onApply()) {
                    savedPulse += 1
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .scale(checkScale),
            colors = if (showSaved) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
        )
    }
}

private val SettingRowMinHeight = 48.dp

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SettingRowMinHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
