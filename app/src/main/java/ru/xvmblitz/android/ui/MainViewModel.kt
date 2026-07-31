package ru.xvmblitz.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.xvmblitz.android.BuildConfig
import ru.xvmblitz.android.data.ApiDefaults
import ru.xvmblitz.android.data.AppContainer
import ru.xvmblitz.android.data.api.AccessType
import ru.xvmblitz.android.data.api.GetUsageResponseDto
import ru.xvmblitz.android.data.api.SessionAggregatedStatisticsDto
import ru.xvmblitz.android.data.api.SessionBattleBriefDto
import ru.xvmblitz.android.data.api.SessionBattleCompletedHubDto
import ru.xvmblitz.android.data.settings.AppSettings
import ru.xvmblitz.android.domain.BattleSessionRuntimeListener
import ru.xvmblitz.android.domain.BattleUiState
import ru.xvmblitz.android.ui.session.SessionBattleListItem
import ru.xvmblitz.android.ui.session.SessionListItem
import ru.xvmblitz.android.ui.session.SessionUiState
import ru.xvmblitz.android.update.UpdateUiState
import ru.xvmblitz.android.util.AppAlertNotifier
import ru.xvmblitz.android.util.HttpErrorMessages
import ru.xvmblitz.android.update.createAppUpdateFacade

data class MainUiState(
    val settings: AppSettings = AppSettings(),
    val usage: GetUsageResponseDto? = null,
    val battle: BattleUiState = BattleUiState(),
    val usageError: String? = null,
    val usageUpdatedAtEpochMs: Long? = null,
    val isUsageLoading: Boolean = false,
    val isAuthorized: Boolean = false,
    val update: UpdateUiState = UpdateUiState(),
    val session: SessionUiState = SessionUiState(),
)

class MainViewModel(
    private val container: AppContainer,
) : ViewModel(), BattleSessionRuntimeListener {
    private val usageState = MutableStateFlow<GetUsageResponseDto?>(null)
    private val usageError = MutableStateFlow<String?>(null)
    private val usageUpdatedAtEpochMs = MutableStateFlow<Long?>(null)
    private val usageLoading = MutableStateFlow(false)
    private val sessionState = MutableStateFlow(SessionUiState())
    private var sessionStatusCountdownJob: Job? = null
    private val updateFacade = createAppUpdateFacade(container)

    val uiState: StateFlow<MainUiState> = combine(
        combine(
            container.settingsRepository.settings,
            container.battleStatisticsStore.state,
            usageState,
            container.authRepository.accessToken,
            sessionState,
        ) { settings, battle, usage, accessToken, session ->
            MainUiState(
                settings = settings,
                usage = usage,
                battle = battle,
                isAuthorized = !accessToken.isNullOrBlank(),
                session = session.copy(
                    isSummaryOverlayVisible = settings.sessionSummaryOverlayVisible,
                    isTrialStatistics = usage?.type == AccessType.Trial,
                ),
            )
        },
        combine(usageError, usageLoading, updateFacade.state, usageUpdatedAtEpochMs) { error, loading, update, updatedAt ->
            UsageExtras(error, loading, update, updatedAt)
        },
    ) { baseState, extras ->
        baseState.copy(
            usageError = extras.error,
            isUsageLoading = extras.loading,
            update = extras.update,
            usageUpdatedAtEpochMs = extras.updatedAtEpochMs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    private data class UsageExtras(
        val error: String?,
        val loading: Boolean,
        val update: UpdateUiState,
        val updatedAtEpochMs: Long?,
    )

    init {
        container.battleSessionRuntimeService.setListener(this)
        refreshUsage()
        updateFacade.startPeriodicChecks(viewModelScope)
        initializeSessions()
        if (container.authRepository.isAuthorized) {
            startPresence()
        }
    }

    override fun onCleared() {
        sessionStatusCountdownJob?.cancel()
        container.battleSessionRuntimeService.setListener(null)
        viewModelScope.launch {
            container.battleSessionRuntimeService.dispose()
            container.presenceRuntimeService.dispose()
        }
        super.onCleared()
    }

    override fun onBattleStarted(battle: SessionBattleBriefDto) {
        viewModelScope.launch {
            applySessionBattleStarted(battle)
        }
    }

    override fun onBattleCompleted(notification: SessionBattleCompletedHubDto) {
        viewModelScope.launch {
            applySessionBattleCompleted(notification)
        }
    }

    override fun onSessionEnded(sessionId: String) {
        viewModelScope.launch {
            applySessionEnded(sessionId)
        }
    }

    fun refreshUsage() {
        viewModelScope.launch {
            if (!container.authRepository.isAuthorized) {
                usageState.value = null
                usageError.value = AppAlertNotifier.DEFAULT_AUTH_MESSAGE
                return@launch
            }
            usageLoading.value = true
            usageError.value = null
            try {
                usageState.value = container.usageApi.getUsage()
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

    fun prepareDebugBaseUrl(
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
                    reconnectActiveSession()
                    if (container.authRepository.isAuthorized) {
                        container.presenceRuntimeService.ensureConnected()
                    }
                }
                onResult(Result.success(Unit))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                onResult(Result.failure(exception))
            }
        }
    }

    fun handleOpenIdCallback(
        accessToken: String,
        refreshToken: String,
        lestaExpiresAt: String?,
        expiresAt: String? = null,
    ) {
        viewModelScope.launch {
            if (!container.authRepository.saveTokens(accessToken, refreshToken, lestaExpiresAt, expiresAt)) {
                usageError.value = "Не удалось сохранить токены авторизации"
                return@launch
            }
            startPresence()
            usageLoading.value = true
            usageError.value = null
            try {
                usageState.value = container.usageApi.getUsage()
                usageUpdatedAtEpochMs.value = System.currentTimeMillis()
                loadSessionHistory(page = 1, showBusy = false)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                usageState.value = null
                usageUpdatedAtEpochMs.value = null
                usageError.value = exception.message ?: "Не удалось получить квоту"
            } finally {
                usageLoading.value = false
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            updateFacade.checkForUpdates(showLoading = true)
        }
    }

    fun downloadAndInstallUpdate() {
        viewModelScope.launch {
            updateFacade.downloadAndInstallUpdate()
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

    fun resetOverlayPositions() {
        viewModelScope.launch {
            container.settingsRepository.resetOverlayPositions()
        }
    }

    fun updateSessionSummaryOverlayPosition(x: Int, y: Int) {
        viewModelScope.launch {
            container.settingsRepository.updateSessionSummaryOverlayPosition(x, y)
        }
    }

    fun selectSession(session: SessionListItem?) {
        viewModelScope.launch {
            sessionState.value = sessionState.value.copy(selectedSession = session)
            container.settingsRepository.setSelectedSessionId(session?.id)
            loadSessionBattles()
            updateActiveSessionConnection()
        }
    }

    fun startSession() {
        viewModelScope.launch {
            if (sessionState.value.isBusy) {
                return@launch
            }
            if (!container.authRepository.isAuthorized) {
                setSessionStatus(AppAlertNotifier.DEFAULT_AUTH_MESSAGE, isError = true)
                return@launch
            }
            sessionState.value = sessionState.value.copy(isBusy = true)
            setSessionStatus("Создание сессии…", isError = false)
            try {
                val result = container.sessionsRepository.create()
                val createdSessionId = result.getOrElse { exception ->
                    handleSessionError(
                        exception = exception,
                        defaultMessage = "Не удалось создать сессию",
                        rateLimitMessage = HttpErrorMessages::sessionCreateRateLimitMessage,
                    )
                    return@launch
                }
                loadSessionHistory(page = 1, showBusy = false, preferSessionId = createdSessionId)
                if (!sessionState.value.isStatusError) {
                    setSessionStatus("Сессия создана", isError = false)
                }
            } finally {
                sessionState.value = sessionState.value.copy(isBusy = false)
            }
        }
    }

    fun restoreSessions() {
        viewModelScope.launch {
            loadSessionHistory(page = 1)
        }
    }

    fun endSession() {
        viewModelScope.launch {
            val selected = sessionState.value.selectedSession
            if (sessionState.value.isBusy) {
                return@launch
            }
            if (selected == null || !selected.isActive) {
                setSessionStatus("Выберите активную сессию для завершения", isError = true)
                return@launch
            }
            if (!container.authRepository.isAuthorized) {
                setSessionStatus(AppAlertNotifier.DEFAULT_AUTH_MESSAGE, isError = true)
                return@launch
            }
            sessionState.value = sessionState.value.copy(isBusy = true)
            setSessionStatus("Завершение сессии…", isError = false)
            try {
                val result = container.sessionsRepository.end(selected.id)
                result.getOrElse { exception ->
                    handleSessionError(
                        exception = exception,
                        defaultMessage = "Не удалось завершить сессию",
                        rateLimitMessage = HttpErrorMessages::quotaRateLimitMessage,
                    )
                    return@launch
                }
                loadSessionHistory(page = sessionState.value.historyPage, showBusy = false)
                if (!sessionState.value.isStatusError) {
                    setSessionStatus("Сессия завершена", isError = false)
                }
            } finally {
                sessionState.value = sessionState.value.copy(isBusy = false)
            }
        }
    }

    fun previousSessionHistoryPage() {
        viewModelScope.launch {
            loadSessionHistory(page = sessionState.value.historyPage - 1)
        }
    }

    fun nextSessionHistoryPage() {
        viewModelScope.launch {
            loadSessionHistory(page = sessionState.value.historyPage + 1)
        }
    }

    fun refreshSessionBattles() {
        viewModelScope.launch {
            loadSessionBattles(page = sessionState.value.battlesPage)
        }
    }

    fun previousSessionBattlesPage() {
        viewModelScope.launch {
            loadSessionBattles(page = sessionState.value.battlesPage - 1)
        }
    }

    fun nextSessionBattlesPage() {
        viewModelScope.launch {
            loadSessionBattles(page = sessionState.value.battlesPage + 1)
        }
    }

    fun toggleSessionSummaryOverlay() {
        viewModelScope.launch {
            val visible = !uiState.value.settings.sessionSummaryOverlayVisible
            container.settingsRepository.setSessionSummaryOverlayVisible(visible)
        }
    }

    fun hideSessionSummaryOverlay() {
        viewModelScope.launch {
            container.settingsRepository.setSessionSummaryOverlayVisible(false)
        }
    }

    fun clearBattle() {
        container.battleStatisticsStore.clear()
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { container.openIdApi.logout() }
            container.presenceRuntimeService.stop()
            container.battleSessionRuntimeService.setActiveSession(null, null)
            container.authRepository.clear()
            usageState.value = null
            usageError.value = null
            usageUpdatedAtEpochMs.value = null
            container.battleStatisticsStore.clear()
            sessionState.value = SessionUiState(
                isSummaryOverlayVisible = sessionState.value.isSummaryOverlayVisible,
            )
        }
    }

    private fun startPresence() {
        viewModelScope.launch {
            container.presenceRuntimeService.start()
        }
    }

    private fun initializeSessions() {
        viewModelScope.launch {
            try {
                if (!container.authRepository.isAuthorized) {
                    return@launch
                }
                loadSessionHistory(page = 1, showBusy = false)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun loadSessionHistory(
        page: Int,
        showBusy: Boolean = true,
        preferSessionId: String? = null,
    ) {
        if (page < 1) {
            return
        }
        if (showBusy && sessionState.value.isBusy) {
            return
        }
        if (!container.authRepository.isAuthorized) {
            setSessionStatus(AppAlertNotifier.DEFAULT_AUTH_MESSAGE, isError = true)
            return
        }
        if (showBusy) {
            sessionState.value = sessionState.value.copy(isBusy = true)
            setSessionStatus("Загрузка истории сессий…", isError = false)
        }
        try {
            val result = container.sessionsRepository.restore(
                page = page,
                pageSize = SessionUiState.SESSION_HISTORY_PAGE_SIZE,
            )
            val payload = result.getOrElse { exception ->
                handleSessionError(
                    exception = exception,
                    defaultMessage = "Не удалось загрузить историю сессий",
                    rateLimitMessage = HttpErrorMessages::quotaRateLimitMessage,
                )
                return
            }
            val settings = container.settingsRepository.current()
            val previouslySelectedId = sessionState.value.selectedSession?.id ?: settings.selectedSessionId
            val sessions = payload.sessions.map(SessionListItem::fromDto)
            val selected = when {
                preferSessionId != null ->
                    sessions.firstOrNull { it.id == preferSessionId }
                        ?: sessions.firstOrNull { it.isActive }
                        ?: sessions.firstOrNull()
                previouslySelectedId != null ->
                    sessions.firstOrNull { it.id == previouslySelectedId }
                        ?: sessions.firstOrNull { it.isActive }
                        ?: sessions.firstOrNull()
                else ->
                    sessions.firstOrNull { it.isActive } ?: sessions.firstOrNull()
            }
            sessionState.value = sessionState.value.copy(
                availableSessions = sessions,
                historyPage = payload.page,
                historyTotalCount = payload.totalCount,
                selectedSession = selected,
            )
            container.settingsRepository.setSelectedSessionId(selected?.id)
            loadSessionBattles()
            updateActiveSessionConnection()
            if (showBusy && !sessionState.value.isStatusError) {
                setSessionStatus(
                    if (payload.totalCount == 0) {
                        "История сессий пуста"
                    } else {
                        "Всего сессий: ${payload.totalCount}"
                    },
                    isError = false,
                )
            }
        } finally {
            if (showBusy) {
                sessionState.value = sessionState.value.copy(isBusy = false)
            }
        }
    }

    private suspend fun loadSessionBattles(page: Int = 1) {
        val selected = sessionState.value.selectedSession
        if (selected == null) {
            sessionState.value = sessionState.value.copy(
                battles = emptyList(),
                battlesPage = 1,
                battlesTotalCount = 0,
                isBattlesLoading = false,
                hasSummary = false,
                totalSummary = "",
                winRateSummary = "",
                averageDamageSummary = "",
                averageFragsSummary = "",
            )
            container.sessionSummaryStore.clear()
            return
        }
        sessionState.value = sessionState.value.copy(isBattlesLoading = true)
        try {
            try {
                if (!container.authRepository.isAuthorized) {
                    error(AppAlertNotifier.DEFAULT_AUTH_MESSAGE)
                }
                usageState.value = container.usageApi.getUsage()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                sessionState.value = sessionState.value.copy(
                    battles = emptyList(),
                    battlesPage = 1,
                    battlesTotalCount = 0,
                    hasSummary = false,
                    totalSummary = "",
                    winRateSummary = "",
                    averageDamageSummary = "",
                    averageFragsSummary = "",
                )
                container.sessionSummaryStore.clear()
                setSessionStatus(exception.message ?: "Не удалось загрузить статистику сессии", isError = true)
                return
            }

            val targetPage = page.coerceAtLeast(1)

            coroutineScope {
                val extendedDeferred = async {
                    container.sessionsRepository.getExtendedStatistics(
                        sessionId = selected.id,
                        page = targetPage,
                        pageSize = SessionUiState.SESSION_BATTLES_PAGE_SIZE,
                    )
                }
                val aggregatedDeferred = async {
                    container.sessionsRepository.getAggregatedStatistics(selected.id)
                }

                val extendedResult = extendedDeferred.await()
                if (extendedResult.isFailure) {
                    sessionState.value = sessionState.value.copy(
                        battles = emptyList(),
                        battlesPage = 1,
                        battlesTotalCount = 0,
                    )
                    setSessionStatus(
                        extendedResult.exceptionOrNull()?.message ?: "Не удалось загрузить бои сессии",
                        isError = true,
                    )
                } else {
                    val statistics = extendedResult.getOrThrow()
                    val battles = statistics.battles.map(SessionBattleListItem::fromDto)
                    sessionState.value = sessionState.value.copy(
                        battles = battles,
                        battlesPage = statistics.page,
                        battlesTotalCount = statistics.totalCount,
                    )
                    clearSessionStatus()
                }

                val aggregatedResult = aggregatedDeferred.await()
                if (aggregatedResult.isSuccess) {
                    applyAggregatedSummary(aggregatedResult.getOrThrow())
                } else {
                    clearSessionBattlesSummary()
                    if (extendedResult.isSuccess) {
                        setSessionStatus(
                            aggregatedResult.exceptionOrNull()?.message
                                ?: "Не удалось загрузить статистику сессии",
                            isError = true,
                        )
                    }
                }
            }
        } finally {
            sessionState.value = sessionState.value.copy(isBattlesLoading = false)
        }
    }

    private fun applyAggregatedSummary(statistics: SessionAggregatedStatisticsDto) {
        if (statistics.totalBattles == 0) {
            clearSessionBattlesSummary()
            return
        }
        val winRate = statistics.totalWins * 100.0 / statistics.totalBattles
        sessionState.value = sessionState.value.copy(
            hasSummary = true,
            totalSummary = "Всего боёв: ${statistics.totalBattles}",
            winRateSummary = "Побед: ${formatOneDecimal(winRate)}%",
            averageDamageSummary = "Средний урон: ${statistics.averageDamage.toInt()}",
            averageFragsSummary = "Среднее количество фрагов: ${formatOneDecimal(statistics.averageFrags)}",
        )
        applySessionOverlaySummary(statistics.totalBattles, winRate, statistics.averageDamage)
    }

    private fun clearSessionBattlesSummary() {
        sessionState.value = sessionState.value.copy(
            hasSummary = false,
            totalSummary = "",
            winRateSummary = "",
            averageDamageSummary = "",
            averageFragsSummary = "",
        )
        container.sessionSummaryStore.clear()
    }

    private fun applySessionBattleStarted(battle: SessionBattleBriefDto) {
        if (sessionState.value.selectedSession == null) {
            return
        }
        upsertSessionBattle(SessionBattleListItem.fromDto(battle))
    }

    private fun applySessionBattleCompleted(notification: SessionBattleCompletedHubDto) {
        if (sessionState.value.selectedSession == null) {
            return
        }
        upsertSessionBattle(SessionBattleListItem.fromDto(notification.battle))
        updateSessionBattlesSummaryFromHub(notification.aggregated)
    }

    private suspend fun applySessionEnded(sessionId: String) {
        if (sessionState.value.selectedSession?.id != sessionId) {
            return
        }
        loadSessionHistory(page = sessionState.value.historyPage, showBusy = false)
        updateActiveSessionConnection()
    }

    private fun upsertSessionBattle(battle: SessionBattleListItem) {
        val current = sessionState.value
        val battles = current.battles.toMutableList()
        val index = battles.indexOfFirst { it.id == battle.id }
        if (index >= 0) {
            battles[index] = battle
            sessionState.value = current.copy(battles = battles)
            return
        }

        val totalCount = current.battlesTotalCount + 1
        if (current.battlesPage == 1) {
            battles.add(0, battle)
            while (battles.size > SessionUiState.SESSION_BATTLES_PAGE_SIZE) {
                battles.removeAt(battles.lastIndex)
            }
        }
        sessionState.value = current.copy(
            battles = battles,
            battlesTotalCount = totalCount,
        )
    }

    private fun updateSessionBattlesSummaryFromHub(
        aggregated: ru.xvmblitz.android.data.api.SessionBattleAggregatedHubDto,
    ) {
        if (aggregated.totalBattles == 0) {
            clearSessionBattlesSummary()
            return
        }
        val winRate = aggregated.totalWins * 100.0 / aggregated.totalBattles
        sessionState.value = sessionState.value.copy(
            hasSummary = true,
            totalSummary = "Всего боёв: ${aggregated.totalBattles}",
            winRateSummary = "Побед: ${formatOneDecimal(winRate)}%",
            averageDamageSummary = "Средний урон: ${aggregated.averageDamage.toInt()}",
            averageFragsSummary = "Среднее количество фрагов: ${formatOneDecimal(aggregated.averageFrags)}",
        )
        applySessionOverlaySummary(aggregated.totalBattles, winRate, aggregated.averageDamage)
    }

    private fun applySessionOverlaySummary(totalBattles: Int, winRate: Double, averageDamage: Double) {
        container.sessionSummaryStore.applySummary(totalBattles, winRate, averageDamage)
    }

    fun ensureActiveSessionConnection() {
        viewModelScope.launch {
            updateActiveSessionConnection()
            container.battleSessionRuntimeService.ensureConnected()
            if (container.authRepository.isAuthorized) {
                container.presenceRuntimeService.ensureConnected()
            }
        }
    }

    private suspend fun updateActiveSessionConnection() {
        val selected = sessionState.value.selectedSession
        val playerId = container.authRepository.getLestaAccountId()
        if (selected?.isActive == true && playerId != null) {
            container.battleSessionRuntimeService.setActiveSession(selected.id, playerId)
            return
        }
        container.battleSessionRuntimeService.setActiveSession(null, null)
    }

    private suspend fun reconnectActiveSession() {
        updateActiveSessionConnection()
        container.battleSessionRuntimeService.ensureConnected()
    }

    private fun setSessionStatus(message: String, isError: Boolean) {
        sessionStatusCountdownJob?.cancel()
        sessionStatusCountdownJob = null
        sessionState.value = sessionState.value.copy(
            statusMessage = message,
            isStatusError = isError,
        )
    }

    private fun updateSessionStatusMessage(message: String, isError: Boolean) {
        sessionState.value = sessionState.value.copy(
            statusMessage = message,
            isStatusError = isError,
        )
    }

    private fun clearSessionStatus() {
        sessionState.value = sessionState.value.copy(
            statusMessage = null,
            isStatusError = false,
        )
    }

    private fun handleSessionError(
        exception: Throwable,
        defaultMessage: String,
        rateLimitMessage: (Long) -> String,
    ) {
        val retrySeconds = HttpErrorMessages.resolveRateLimitSeconds(exception)
        if (retrySeconds != null && retrySeconds > 0) {
            startSessionRateLimitCountdown(retrySeconds, rateLimitMessage)
            return
        }
        setSessionStatus(exception.message ?: defaultMessage, isError = true)
    }

    private fun startSessionRateLimitCountdown(
        seconds: Long,
        messageBuilder: (Long) -> String,
    ) {
        sessionStatusCountdownJob?.cancel()
        sessionStatusCountdownJob = viewModelScope.launch {
            var remaining = seconds.coerceAtLeast(1L)
            while (remaining > 0) {
                updateSessionStatusMessage(messageBuilder(remaining), isError = true)
                delay(1_000)
                remaining--
            }
            clearSessionStatus()
            sessionStatusCountdownJob = null
        }
    }

    private fun formatOneDecimal(value: Double): String {
        val rounded = (value * 10).toInt() / 10.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toInt().toString()
        } else {
            rounded.toString()
        }
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
