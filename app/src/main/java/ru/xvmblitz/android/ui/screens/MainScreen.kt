package ru.xvmblitz.android.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
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
    onResetOverlayPositions: () -> Unit,
    onSessionNicknameChange: (String) -> Unit,
    onSessionSecretKeyChange: (String) -> Unit,
    onGenerateSessionSecretKey: () -> Unit,
    onSelectSession: (ru.xvmblitz.android.ui.session.SessionListItem?) -> Unit,
    onStartSession: () -> Unit,
    onRestoreSessions: () -> Unit,
    onEndSession: () -> Unit,
    onPreviousSessionHistoryPage: () -> Unit,
    onNextSessionHistoryPage: () -> Unit,
    onRefreshSessionBattles: () -> Unit,
    onToggleSessionSummaryOverlay: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenAbout: () -> Unit,
    onCloseApp: () -> Unit,
) {
    val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val configuration = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    var configToggleScreenY by remember { mutableFloatStateOf(0f) }
    var preserveConfigToggleScreenY by remember { mutableStateOf<Float?>(null) }

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

        UpdateSection(
            state = state.update,
            onCheckForUpdates = onCheckForUpdates,
            onDownloadUpdate = onDownloadUpdate,
        )

        SessionSection(
            session = state.session,
            onNicknameChange = onSessionNicknameChange,
            onSecretKeyChange = onSessionSecretKeyChange,
            onGenerateSecretKey = onGenerateSessionSecretKey,
            onSelectSession = onSelectSession,
            onStartSession = onStartSession,
            onRestoreSessions = onRestoreSessions,
            onEndSession = onEndSession,
            onPreviousHistoryPage = onPreviousSessionHistoryPage,
            onNextHistoryPage = onNextSessionHistoryPage,
            onRefreshBattles = onRefreshSessionBattles,
            onToggleSummaryOverlay = onToggleSessionSummaryOverlay,
        )

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
                        text = "Экран зафиксирован горизонтально. Перетащите панели и оверлей суммаризации. Угол и края меняют размер.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                AdaptiveOutlinedButton(
                    text = "Сбросить координаты",
                    onClick = onResetOverlayPositions,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        AdaptiveOutlinedButton(
            text = "О приложении",
            onClick = onOpenAbout,
            modifier = Modifier.fillMaxWidth(),
        )

        AdaptiveButton(
            text = "Закрыть приложение",
            onClick = onCloseApp,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        )

        Spacer(modifier = Modifier.height(8.dp))
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
