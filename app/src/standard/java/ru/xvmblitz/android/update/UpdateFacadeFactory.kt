package ru.xvmblitz.android.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.xvmblitz.android.data.AppContainer

fun createAppUpdateFacade(container: AppContainer): AppUpdateFacade = NoOpAppUpdateFacade()

private class NoOpAppUpdateFacade : AppUpdateFacade {
    private val updateState = MutableStateFlow(UpdateUiState())

    override val state: StateFlow<UpdateUiState> = updateState.asStateFlow()

    override fun startPeriodicChecks(scope: CoroutineScope) = Unit

    override suspend fun checkForUpdates(showLoading: Boolean) = Unit

    override suspend fun downloadAndInstallUpdate() = Unit
}
