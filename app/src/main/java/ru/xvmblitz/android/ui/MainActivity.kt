package ru.xvmblitz.android.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.xvmblitz.android.XvmBlitzApp
import ru.xvmblitz.android.capture.CaptureEvents
import ru.xvmblitz.android.overlay.OverlayService
import ru.xvmblitz.android.ui.screens.AuthScreen
import ru.xvmblitz.android.ui.screens.MainScreen
import ru.xvmblitz.android.ui.theme.XvmBlitzTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XvmBlitzTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val container = XvmBlitzApp.instance.container
                    val navController = rememberNavController()
                    val mainViewModel: MainViewModel = viewModel(
                        factory = MainViewModel.factory(container),
                    )
                    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
                    var statusMessage by remember { mutableStateOf<String?>(null) }
                    var pendingCapture by remember { mutableStateOf(false) }

                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { /* no-op */ }

                    fun ensureOverlayRunning() {
                        if (!Settings.canDrawOverlays(this@MainActivity)) {
                            statusMessage = "Нужно разрешение «Поверх других окон» для кнопки в игре"
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName"),
                                ),
                            )
                            return
                        }
                        OverlayService.start(this@MainActivity)
                    }

                    fun stopFloatingOverlayIfIdle() {
                        val needsPanels = uiState.settings.overlayVisible &&
                            (uiState.battle.hasBattle || uiState.settings.configMode)
                        if (!needsPanels) {
                            OverlayService.stop(this@MainActivity)
                        }
                    }

                    fun startCapture() {
                        if (!container.authRepository.isAuthorized) {
                            statusMessage = "Сначала введите API ключ"
                            navController.navigate(Routes.Auth)
                            return
                        }
                        if (!Settings.canDrawOverlays(this@MainActivity)) {
                            ensureOverlayRunning()
                            return
                        }
                        OverlayService.start(this@MainActivity)
                        pendingCapture = true
                        statusMessage = "Выберите экран для захвата"
                        OverlayService.startCapture(this@MainActivity)
                    }

                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        val settings = container.settingsRepository.current()
                        if (container.authRepository.isAuthorized &&
                            settings.floatingButtonEnabled &&
                            Settings.canDrawOverlays(this@MainActivity)
                        ) {
                            OverlayService.start(this@MainActivity)
                        }
                        CaptureEvents.events.collect { event ->
                            when (event) {
                                CaptureEvents.Result.Loading -> {
                                    statusMessage = "Распознаём скриншот…"
                                    pendingCapture = true
                                }
                                CaptureEvents.Result.Success -> {
                                    statusMessage = "Статистика получена"
                                    pendingCapture = false
                                    mainViewModel.refreshUsage()
                                }
                                is CaptureEvents.Result.Error -> {
                                    statusMessage = event.message
                                    pendingCapture = false
                                }
                            }
                        }
                    }

                    val startDestination = if (container.authRepository.isAuthorized) {
                        Routes.Main
                    } else {
                        Routes.Auth
                    }

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable(Routes.Auth) {
                            AuthScreen(
                                onAuthorized = {
                                    navController.navigate(Routes.Main) {
                                        popUpTo(Routes.Auth) { inclusive = true }
                                    }
                                    mainViewModel.refreshUsage()
                                    ensureOverlayRunning()
                                },
                            )
                        }
                        composable(Routes.Main) {
                            MainScreen(
                                state = uiState,
                                statusMessage = statusMessage,
                                isCapturing = pendingCapture,
                                onApiKeyClick = {
                                    navController.navigate(Routes.Auth)
                                },
                                onLogout = {
                                    mainViewModel.logout()
                                    OverlayService.stop(this@MainActivity)
                                    navController.navigate(Routes.Auth) {
                                        popUpTo(Routes.Main) { inclusive = true }
                                    }
                                },
                                onRefreshUsage = mainViewModel::refreshUsage,
                                onCheckForUpdates = mainViewModel::checkForUpdates,
                                onDownloadUpdate = {
                                    val downloadUrl = uiState.update.downloadUrl
                                    if (!downloadUrl.isNullOrBlank()) {
                                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                                    }
                                },
                                onFontSizeChange = mainViewModel::updateFontSize,
                                onConfigModeChange = { enabled ->
                                    mainViewModel.setConfigMode(enabled)
                                    if (enabled) {
                                        mainViewModel.setOverlayVisible(true)
                                        ensureOverlayRunning()
                                    }
                                },
                                onOverlayVisibleChange = mainViewModel::setOverlayVisible,
                                onFloatingButtonEnabledChange = { enabled ->
                                    mainViewModel.setFloatingButtonEnabled(enabled) {
                                        if (enabled) {
                                            ensureOverlayRunning()
                                        } else {
                                            stopFloatingOverlayIfIdle()
                                        }
                                    }
                                },
                                onClearBattle = {
                                    mainViewModel.clearBattle()
                                },
                                onCaptureClick = ::startCapture,
                            )
                        }
                    }
                }
            }
        }
    }
}

private object Routes {
    const val Auth = "auth"
    const val Main = "main"
}
