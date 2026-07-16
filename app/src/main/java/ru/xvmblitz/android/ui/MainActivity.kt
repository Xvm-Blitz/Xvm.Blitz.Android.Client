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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import ru.xvmblitz.android.XvmBlitzApp
import ru.xvmblitz.android.capture.CaptureEvents
import ru.xvmblitz.android.overlay.OverlayService
import ru.xvmblitz.android.ui.screens.AuthScreen
import ru.xvmblitz.android.ui.screens.MainScreen
import ru.xvmblitz.android.ui.theme.XvmBlitzTheme
import ru.xvmblitz.android.util.AppAlertNotifier
import ru.xvmblitz.android.util.CaptureAccessGuard
import ru.xvmblitz.android.util.CaptureAccessResult

class MainActivity : ComponentActivity() {
    private val incomingIntents = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingIntents.value = intent
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
                    val coroutineScope = rememberCoroutineScope()
                    val incomingIntent by incomingIntents.collectAsStateWithLifecycle()

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
                        coroutineScope.launch {
                            when (val access = CaptureAccessGuard.check(container)) {
                                is CaptureAccessResult.Denied -> {
                                    statusMessage = access.message
                                    AppAlertNotifier.showApiKeyRequired(
                                        this@MainActivity,
                                        access.message,
                                    )
                                    return@launch
                                }
                                CaptureAccessResult.Allowed -> Unit
                            }
                            if (!Settings.canDrawOverlays(this@MainActivity)) {
                                ensureOverlayRunning()
                                return@launch
                            }
                            OverlayService.start(this@MainActivity)
                            pendingCapture = true
                            statusMessage = "Выберите экран для захвата"
                            OverlayService.startCapture(this@MainActivity)
                        }
                    }

                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        val settings = container.settingsRepository.current()
                        if (settings.floatingButtonEnabled &&
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

                    LaunchedEffect(incomingIntent) {
                        val openAuth = incomingIntent?.getBooleanExtra(EXTRA_OPEN_AUTH, false) == true
                        if (openAuth) {
                            navController.navigate(Routes.Auth) {
                                launchSingleTop = true
                            }
                            incomingIntent?.removeExtra(EXTRA_OPEN_AUTH)
                        }
                    }

                    NavHost(navController = navController, startDestination = Routes.Main) {
                        composable(Routes.Main) {
                            MainScreen(
                                state = uiState,
                                statusMessage = statusMessage,
                                isCapturing = pendingCapture,
                                onAuthClick = {
                                    navController.navigate(Routes.Auth) {
                                        launchSingleTop = true
                                    }
                                },
                                onCheckForUpdates = mainViewModel::checkForUpdates,
                                onDownloadUpdate = mainViewModel::downloadAndInstallUpdate,
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
                        composable(Routes.Auth) {
                            AuthScreen(
                                isAuthorized = uiState.isAuthorized,
                                usage = uiState.usage,
                                usageError = uiState.usageError,
                                isUsageLoading = uiState.isUsageLoading,
                                onBack = {
                                    navController.popBackStack()
                                },
                                onAuthorized = {
                                    mainViewModel.refreshUsage()
                                    ensureOverlayRunning()
                                },
                                onLogout = {
                                    mainViewModel.logout()
                                },
                                onRefreshUsage = mainViewModel::refreshUsage,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingIntents.value = intent
    }

    companion object {
        const val EXTRA_OPEN_AUTH = "open_auth"
    }
}

private object Routes {
    const val Auth = "auth"
    const val Main = "main"
}
