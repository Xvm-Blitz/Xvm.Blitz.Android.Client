package ru.xvmblitz.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.xvmblitz.android.BuildConfig
import ru.xvmblitz.android.data.ApiDefaults
import ru.xvmblitz.android.data.AppContainer
import ru.xvmblitz.android.data.api.GetUsageResponseDto
import ru.xvmblitz.android.data.settings.AppSettings
import ru.xvmblitz.android.domain.BattleUiState

data class MainUiState(
    val settings: AppSettings = AppSettings(),
    val usage: GetUsageResponseDto? = null,
    val battle: BattleUiState = BattleUiState(),
    val usageError: String? = null,
    val usageUpdatedAtEpochMs: Long? = null,
    val isUsageLoading: Boolean = false,
    val isAuthorized: Boolean = false,
)

class MainViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val usageState = MutableStateFlow<GetUsageResponseDto?>(null)
    private val usageError = MutableStateFlow<String?>(null)
    private val usageUpdatedAtEpochMs = MutableStateFlow<Long?>(null)
    private val usageLoading = MutableStateFlow(false)

    val uiState: StateFlow<MainUiState> = combine(
        combine(
            container.settingsRepository.settings,
            container.battleStatisticsStore.state,
            usageState,
            container.authRepository.apiKey,
        ) { settings, battle, usage, apiKey ->
            MainUiState(
                settings = settings,
                usage = usage,
                battle = battle,
                isAuthorized = !apiKey.isNullOrBlank(),
            )
        },
        combine(usageError, usageLoading, usageUpdatedAtEpochMs) { error, loading, updatedAt ->
            UsageExtras(error, loading, updatedAt)
        },
    ) { baseState, extras ->
        baseState.copy(
            usageError = extras.error,
            isUsageLoading = extras.loading,
            usageUpdatedAtEpochMs = extras.updatedAtEpochMs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    private data class UsageExtras(
        val error: String?,
        val loading: Boolean,
        val updatedAtEpochMs: Long?,
    )

    init {
        refreshUsage()
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
                usageUpdatedAtEpochMs.value = System.currentTimeMillis()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                usageError.value = exception.message ?: "Не удалось получить квоту"
            } finally {
                usageLoading.value = false
            }
        }
    }

    fun authorize(
        apiKey: String,
        apiBaseUrl: String? = null,
        onResult: (Result<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                if (BuildConfig.DEBUG && !apiBaseUrl.isNullOrBlank()) {
                    val normalized = ApiDefaults.normalizeBaseUrl(apiBaseUrl)
                    if (!normalized.startsWith("https://")) {
                        onResult(Result.failure(IllegalArgumentException("Base URL должен начинаться с https://")))
                        return@launch
                    }
                    container.setApiBaseUrl(normalized)
                    container.settingsRepository.setApiBaseUrl(normalized)
                }

                val trimmed = apiKey.trim()
                if (trimmed.isEmpty()) {
                    onResult(Result.failure(IllegalArgumentException("Ключ не может быть пустым")))
                    return@launch
                }

                usageLoading.value = true
                usageError.value = null
                if (!container.authRepository.saveApiKey(trimmed)) {
                    onResult(Result.failure(IllegalArgumentException("Ключ не может быть пустым")))
                    return@launch
                }
                onResult(Result.success(Unit))
                val usage = container.usageApi.getUsage(trimmed)
                usageState.value = usage
                usageUpdatedAtEpochMs.value = System.currentTimeMillis()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (container.authRepository.isAuthorized) {
                    usageState.value = null
                    usageUpdatedAtEpochMs.value = null
                    usageError.value = exception.message ?: "Не удалось получить квоту"
                    onResult(Result.success(Unit))
                } else {
                    onResult(Result.failure(exception))
                }
            } finally {
                usageLoading.value = false
            }
        }
    }

    fun setConfigMode(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setConfigMode(enabled)
        }
    }

    fun setGuideCompleted(completed: Boolean = true) {
        viewModelScope.launch {
            container.settingsRepository.setGuideCompleted(completed)
        }
    }

    fun setOverlayVisible(visible: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setOverlayVisible(visible)
        }
    }

    fun setCaptureFirstDelayMs(delayMs: Int) {
        viewModelScope.launch {
            container.settingsRepository.setCaptureFirstDelayMs(delayMs)
        }
    }

    fun updateAlliesPosition(x: Int, y: Int) {
        viewModelScope.launch {
            container.settingsRepository.updateAlliesPosition(x, y)
        }
    }

    fun updateEnemiesPosition(x: Int, y: Int) {
        viewModelScope.launch {
            container.settingsRepository.updateEnemiesPosition(x, y)
        }
    }

    fun clearBattle() {
        container.battleStatisticsStore.clear()
    }

    fun logout() {
        container.authRepository.logout()
        usageState.value = null
        usageError.value = null
        usageUpdatedAtEpochMs.value = null
        container.battleStatisticsStore.clear()
    }

    companion object {
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
