package ru.xvmblitz.android.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import ru.xvmblitz.android.BuildConfig

data class UpdateUiState(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val latestVersion: String? = null,
    val downloadUrl: String? = null,
    val isUpdateAvailable: Boolean = false,
    val isUpToDate: Boolean = false,
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val isInstalling: Boolean = false,
    val downloadProgress: Float = 0f,
    val error: String? = null,
)

interface AppUpdateFacade {
    val state: StateFlow<UpdateUiState>

    fun startPeriodicChecks(scope: CoroutineScope)

    suspend fun checkForUpdates(showLoading: Boolean = true)

    suspend fun downloadAndInstallUpdate()
}
