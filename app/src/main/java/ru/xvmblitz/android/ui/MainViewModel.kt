package ru.xvmblitz.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.xvmblitz.android.BuildConfig
import ru.xvmblitz.android.data.AppContainer
import ru.xvmblitz.android.data.api.ClientPlatform
import ru.xvmblitz.android.data.api.GetUsageResponseDto
import ru.xvmblitz.android.data.settings.AppSettings
import ru.xvmblitz.android.domain.BattleUiState
import ru.xvmblitz.android.util.SemVerComparer
import kotlin.time.Duration.Companion.minutes

data class UpdateUiState(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val latestVersion: String? = null,
    val downloadUrl: String? = null,
    val isUpdateAvailable: Boolean = false,
    val isUpToDate: Boolean = false,
    val isChecking: Boolean = false,
    val error: String? = null,
)

data class MainUiState(
    val settings: AppSettings = AppSettings(),
    val usage: GetUsageResponseDto? = null,
    val battle: BattleUiState = BattleUiState(),
    val usageError: String? = null,
    val isUsageLoading: Boolean = false,
    val update: UpdateUiState = UpdateUiState(),
)

class MainViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val usageState = MutableStateFlow<GetUsageResponseDto?>(null)
    private val usageError = MutableStateFlow<String?>(null)
    private val usageLoading = MutableStateFlow(false)
    private val updateState = MutableStateFlow(UpdateUiState())

    val uiState: StateFlow<MainUiState> = combine(
        combine(
            container.settingsRepository.settings,
            container.battleStatisticsStore.state,
            usageState,
        ) { settings, battle, usage ->
            Triple(settings, battle, usage)
        },
        combine(usageError, usageLoading, updateState) { error, loading, update ->
            Triple(error, loading, update)
        },
    ) { settingsBattleUsage, errorLoadingUpdate ->
        val (settings, battle, usage) = settingsBattleUsage
        val (error, loading, update) = errorLoadingUpdate
        MainUiState(
            settings = settings,
            usage = usage,
            battle = battle,
            usageError = error,
            isUsageLoading = loading,
            update = update,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        refreshUsage()
        viewModelScope.launch {
            while (true) {
                checkForUpdates(showLoading = false)
                delay(UPDATE_CHECK_INTERVAL)
            }
        }
    }

    fun refreshUsage() {
        viewModelScope.launch {
            val apiKey = container.authRepository.getApiKeyOrNull()
            if (apiKey.isNullOrBlank()) {
                usageState.value = null
                usageError.value = "API ключ не задан"
                return@launch
            }
            usageLoading.value = true
            usageError.value = null
            try {
                usageState.value = container.usageApi.getUsage(apiKey)
            } catch (exception: Exception) {
                usageError.value = exception.message ?: "Не удалось получить квоту"
            } finally {
                usageLoading.value = false
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            checkForUpdates(showLoading = true)
        }
    }

    private suspend fun checkForUpdates(showLoading: Boolean) {
        val currentVersion = BuildConfig.VERSION_NAME
        if (showLoading) {
            updateState.value = updateState.value.copy(isChecking = true, error = null)
        }
        try {
            val updateInfo = container.updatesApi.getLatestVersion(
                currentVersion = currentVersion,
                platform = ClientPlatform.Android,
            )
            val latestVersion = updateInfo.version.trim()
            if (latestVersion.isEmpty()) {
                updateState.value = updateState.value.copy(
                    currentVersion = currentVersion,
                    isChecking = false,
                    error = if (showLoading) "Сервер не вернул версию" else updateState.value.error,
                )
                return
            }
            val hasUpdate = SemVerComparer.isLessThan(currentVersion, latestVersion)
            updateState.value = UpdateUiState(
                currentVersion = currentVersion,
                latestVersion = latestVersion,
                downloadUrl = updateInfo.downloadUrl,
                isUpdateAvailable = hasUpdate,
                isUpToDate = !hasUpdate,
                isChecking = false,
                error = null,
            )
        } catch (exception: Exception) {
            updateState.value = updateState.value.copy(
                currentVersion = currentVersion,
                isChecking = false,
                error = if (showLoading) {
                    exception.message ?: "Не удалось проверить обновление"
                } else {
                    updateState.value.error
                },
            )
        }
    }

    fun updateFontSize(fontSizeSp: Float) {
        viewModelScope.launch {
            container.settingsRepository.updateFontSize(fontSizeSp)
        }
    }

    fun setConfigMode(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setConfigMode(enabled)
        }
    }

    fun setOverlayVisible(visible: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setOverlayVisible(visible)
        }
    }

    fun setFloatingButtonEnabled(enabled: Boolean, onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            container.settingsRepository.setFloatingButtonEnabled(enabled)
            onDone?.invoke()
        }
    }

    fun clearBattle() {
        container.battleStatisticsStore.clear()
    }

    fun logout() {
        container.authRepository.logout()
        usageState.value = null
        container.battleStatisticsStore.clear()
    }

    companion object {
        private val UPDATE_CHECK_INTERVAL = 10.minutes

        fun factory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(container) as T
                }
            }
        }
    }
}
