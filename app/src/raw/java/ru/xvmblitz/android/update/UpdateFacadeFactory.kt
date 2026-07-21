package ru.xvmblitz.android.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.xvmblitz.android.BuildConfig
import ru.xvmblitz.android.data.AppContainer
import ru.xvmblitz.android.data.api.ClientPlatform
import ru.xvmblitz.android.util.SemVerComparer
import kotlin.time.Duration.Companion.minutes

fun createAppUpdateFacade(container: AppContainer): AppUpdateFacade = SelfAppUpdateFacade(container)

private class SelfAppUpdateFacade(
    private val container: AppContainer,
) : AppUpdateFacade {
    private val updateState = MutableStateFlow(UpdateUiState())
    private val updateInstaller = AppUpdateInstaller(container.appContext, container.httpClient)

    override val state: StateFlow<UpdateUiState> = updateState.asStateFlow()

    override fun startPeriodicChecks(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                checkForUpdates(showLoading = false)
                delay(UPDATE_CHECK_INTERVAL)
            }
        }
    }

    override suspend fun checkForUpdates(showLoading: Boolean) {
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
            updateState.value = updateState.value.copy(
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

    override suspend fun downloadAndInstallUpdate() {
        val downloadUrl = updateState.value.downloadUrl
        if (downloadUrl.isNullOrBlank()) {
            updateState.value = updateState.value.copy(error = "Ссылка на обновление отсутствует")
            return
        }
        if (updateState.value.isDownloading || updateState.value.isInstalling) {
            return
        }
        updateState.value = updateState.value.copy(
            isDownloading = true,
            isInstalling = false,
            downloadProgress = 0f,
            error = null,
        )
        try {
            updateInstaller.downloadAndInstall(downloadUrl) { progress ->
                updateState.value = updateState.value.copy(downloadProgress = progress)
            }
            updateState.value = updateState.value.copy(
                isDownloading = false,
                isInstalling = true,
                downloadProgress = 1f,
                error = null,
            )
        } catch (exception: Exception) {
            updateState.value = updateState.value.copy(
                isDownloading = false,
                isInstalling = false,
                error = exception.message ?: "Не удалось обновить приложение",
            )
        }
    }

    companion object {
        private val UPDATE_CHECK_INTERVAL = 10.minutes
    }
}
