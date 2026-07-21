package ru.xvmblitz.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import ru.xvmblitz.android.XvmBlitzApp
import ru.xvmblitz.android.overlay.OverlayService
import ru.xvmblitz.android.ui.screens.AboutScreen
import ru.xvmblitz.android.ui.screens.AuthScreen
import ru.xvmblitz.android.ui.screens.GuideScreen
import ru.xvmblitz.android.ui.screens.MainScreen
import ru.xvmblitz.android.ui.theme.XvmBlitzTheme

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
                    val incomingIntent by incomingIntents.collectAsStateWithLifecycle()

                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { /* no-op */ }

                    fun ensureOverlayRunning() {
                        if (!Settings.canDrawOverlays(this@MainActivity)) {
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

                    LaunchedEffect(uiState.settings.configMode) {
                        requestedOrientation = if (uiState.settings.configMode) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }

                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        val settings = container.settingsRepository.current()
                        if (!settings.guideCompleted) {
                            navController.navigate(Routes.Guide) {
                                launchSingleTop = true
                            }
                        }
                        if (Settings.canDrawOverlays(this@MainActivity)) {
                            OverlayService.start(this@MainActivity)
                        } else {
                            ensureOverlayRunning()
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
                                onAuthClick = {
                                    navController.navigate(Routes.Auth) {
                                        launchSingleTop = true
                                    }
                                },
                                onConfigModeChange = { enabled ->
                                    mainViewModel.setConfigMode(enabled)
                                    if (enabled) {
                                        mainViewModel.setOverlayVisible(true)
                                        ensureOverlayRunning()
                                    }
                                },
                                onOverlayVisibleChange = mainViewModel::setOverlayVisible,
                                onUpdateAlliesPosition = mainViewModel::updateAlliesPosition,
                                onUpdateEnemiesPosition = mainViewModel::updateEnemiesPosition,
                                onResetOverlayPositions = mainViewModel::resetOverlayPositions,
                                onOpenGuide = {
                                    navController.navigate(Routes.Guide) {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenAbout = {
                                    navController.navigate(Routes.About) {
                                        launchSingleTop = true
                                    }
                                },
                                onCloseApp = {
                                    OverlayService.stop(this@MainActivity)
                                    finishAndRemoveTask()
                                    Process.killProcess(Process.myPid())
                                },
                            )
                        }
                        composable(Routes.About) {
                            AboutScreen(
                                onBack = {
                                    navController.popBackStack()
                                },
                            )
                        }
                        composable(Routes.Guide) {
                            GuideScreen(
                                onBack = {
                                    mainViewModel.setGuideCompleted(true)
                                    navController.popBackStack()
                                },
                                onFinished = {
                                    mainViewModel.setGuideCompleted(true)
                                    navController.popBackStack()
                                },
                            )
                        }
                        composable(Routes.Auth) {
                            AuthScreen(
                                isAuthorized = uiState.isAuthorized,
                                usage = uiState.usage,
                                usageError = uiState.usageError,
                                usageUpdatedAtEpochMs = uiState.usageUpdatedAtEpochMs,
                                isUsageLoading = uiState.isUsageLoading,
                                onBack = {
                                    navController.popBackStack()
                                },
                                onAuthorize = mainViewModel::authorize,
                                onAuthorized = {
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
    const val About = "about"
    const val Auth = "auth"
    const val Main = "main"
    const val Guide = "guide"
}
