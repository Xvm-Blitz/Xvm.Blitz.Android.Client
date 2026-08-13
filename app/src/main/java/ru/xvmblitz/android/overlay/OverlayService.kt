package ru.xvmblitz.android.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.PopupMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.xvmblitz.android.R
import ru.xvmblitz.android.XvmBlitzApp
import ru.xvmblitz.android.capture.CaptureRequestActivity
import ru.xvmblitz.android.data.api.XvmUsageStatus
import ru.xvmblitz.android.data.settings.AppSettings
import ru.xvmblitz.android.domain.BattleStatisticsStore
import ru.xvmblitz.android.domain.BattleUiState
import ru.xvmblitz.android.domain.PlayerSlot
import ru.xvmblitz.android.ui.MainActivity
import ru.xvmblitz.android.ui.theme.XvmBlitzTheme
import ru.xvmblitz.android.util.AppAlertNotifier
import ru.xvmblitz.android.voice.VoicePhase
import ru.xvmblitz.android.voice.VoiceUiState
import kotlin.math.abs

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private var alliesView: ComposeView? = null
    private var enemiesView: ComposeView? = null
    private var captureButtonView: ComposeView? = null
    private var directionHintView: ComposeView? = null
    private var sessionSummaryView: ComposeView? = null
    private var incomingCallView: ComposeView? = null
    private var voiceCallView: ComposeView? = null
    private var inviteBarView: ComposeView? = null
    private var alliesParams: WindowManager.LayoutParams? = null
    private var enemiesParams: WindowManager.LayoutParams? = null
    private var captureButtonParams: WindowManager.LayoutParams? = null
    private var directionHintParams: WindowManager.LayoutParams? = null
    private var sessionSummaryParams: WindowManager.LayoutParams? = null
    private var incomingCallParams: WindowManager.LayoutParams? = null
    private var voiceCallParams: WindowManager.LayoutParams? = null
    private var inviteBarParams: WindowManager.LayoutParams? = null
    private var collectJob: Job? = null
    private var currentSettings = AppSettings()
    private var currentBattle = BattleUiState()
    private var currentVoice = VoiceUiState()
    private var hiddenForCapture = false
    private var microphoneForeground = false
    private val alliesHitTester = OverlayInteractiveHitTester()
    private val enemiesHitTester = OverlayInteractiveHitTester()
    private val incomingCallHitTester = OverlayInteractiveHitTester()
    private val voiceCallHitTester = OverlayInteractiveHitTester()
    private val inviteBarHitTester = OverlayInteractiveHitTester()
    private val selectedCallPlayerId = MutableStateFlow<Long?>(null)
    private var captureButtonOriginOffsetX = 0
    private var captureButtonOriginOffsetY = 0
    private val previewPanelScale = MutableStateFlow<PanelScalePreview?>(null)
    private val previewSessionSummaryScale = MutableStateFlow<PanelScalePreview?>(null)
    private val previewVoiceCallScale = MutableStateFlow<PanelScalePreview?>(null)
    private val fabErrorPulse = MutableStateFlow(0)
    private val fabErrorMessage = MutableStateFlow<String?>(null)
    private val captureButtonOffScreenDirection =
        MutableStateFlow<CaptureButtonOffScreenDirection?>(null)
    private var fabErrorHideJob: Job? = null
    private var voiceCallDragging = false
    private val configurationCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            updateCaptureButtonWindowPosition()
            updateCaptureButtonDirectionHint()
            updateSessionSummaryOverlayLayout(adjustScale = true)
        }

        override fun onLowMemory() = Unit
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        registerComponentCallbacks(configurationCallbacks)
        startAsForeground()
        ensureViews()
        collectJob = scope.launch {
            val container = XvmBlitzApp.instance.container
            launch {
                container.settingsRepository.settings.collectLatest { settings ->
                    currentSettings = settings
                    applySettings(settings)
                    renderPanels()
                }
            }
            launch {
                container.battleStatisticsStore.state.collectLatest { battle ->
                    currentBattle = battle
                    if (!battle.hasBattle) {
                        selectedCallPlayerId.value = null
                    }
                    renderPanels()
                }
            }
            launch {
                container.sessionSummaryStore.overlay.collectLatest {
                    sessionSummaryView?.invalidate()
                }
            }
            launch {
                container.voiceRuntimeService.state.collectLatest { voice ->
                    currentVoice = voice
                    updateForegroundMicrophone(voice.capturingAudio)
                    renderPanels()
                    incomingCallView?.invalidate()
                    voiceCallView?.invalidate()
                    inviteBarView?.invalidate()
                }
            }
            launch {
                selectedCallPlayerId.collectLatest {
                    renderPanels()
                    inviteBarView?.invalidate()
                }
            }
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                scope.launch {
                    val settingsRepository = XvmBlitzApp.instance.container.settingsRepository
                    settingsRepository.setOverlayVisible(!currentSettings.overlayVisible)
                }
            }
            ACTION_HIDE_FOR_CAPTURE -> setHiddenForCapture(true)
            ACTION_RESTORE_AFTER_CAPTURE -> setHiddenForCapture(false)
            ACTION_CAPTURE -> startCaptureAfterHidingOverlay()
            ACTION_ACCESS_DENIED -> {
                val message = intent.getStringExtra(EXTRA_ACCESS_DENIED_MESSAGE)
                    ?: AppAlertNotifier.DEFAULT_AUTH_MESSAGE
                signalFabAccessDenied(message)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        unregisterComponentCallbacks(configurationCallbacks)
        removeViews()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun ensureViews() {
        if (alliesView == null) {
            alliesParams = createLayoutParams(currentSettings.alliesX, currentSettings.alliesY)
            alliesView = createComposeOverlayView { AlliesOverlayContent() }.also { view ->
                attachPanelDrag(view, PanelKind.Allies)
                windowManager.addView(view, alliesParams)
            }
        }
        if (enemiesView == null) {
            enemiesParams = createLayoutParams(currentSettings.enemiesX, currentSettings.enemiesY)
            enemiesView = createComposeOverlayView { EnemiesOverlayContent() }.also { view ->
                attachPanelDrag(view, PanelKind.Enemies)
                windowManager.addView(view, enemiesParams)
            }
        }
        if (captureButtonView == null) {
            captureButtonParams = createLayoutParams(
                currentSettings.captureButtonX,
                currentSettings.captureButtonY,
            )
            captureButtonView = createComposeOverlayView { FloatingActionButtonContent() }.also { view ->
                attachCaptureButtonTouch(view)
                view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    updateCaptureButtonDirectionHint()
                }
                windowManager.addView(view, captureButtonParams)
            }
        }
        if (directionHintView == null) {
            directionHintParams = createLayoutParams(0, 0)
            directionHintView = createComposeOverlayView { DirectionHintContent() }.also { view ->
                view.visibility = android.view.View.GONE
                windowManager.addView(view, directionHintParams)
            }
        }
        if (sessionSummaryView == null) {
            sessionSummaryParams = createLayoutParams(
                currentSettings.sessionSummaryOverlayX,
                currentSettings.sessionSummaryOverlayY,
            )
            sessionSummaryView = createComposeOverlayView { SessionSummaryOverlayContentWrapper() }.also { view ->
                attachSessionSummaryDrag(view)
                view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    updateSessionSummaryOverlayLayout(adjustScale = true)
                }
                windowManager.addView(view, sessionSummaryParams)
            }
        }
        if (incomingCallView == null) {
            incomingCallParams = createLayoutParams(0, 24)
            incomingCallView = createComposeOverlayView { IncomingCallOverlayContent() }.also { view ->
                view.setOnTouchListener { _, event ->
                    if (incomingCallHitTester.contains(event.x, event.y)) {
                        false
                    } else {
                        true
                    }
                }
                view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    placeIncomingCallBanner()
                }
                windowManager.addView(view, incomingCallParams)
            }
        }
        if (voiceCallView == null) {
            voiceCallParams = createLayoutParams(
                currentSettings.voiceCallX.takeIf { value -> value != Int.MIN_VALUE } ?: 0,
                currentSettings.voiceCallY.takeIf { value -> value != Int.MIN_VALUE } ?: 0,
            )
            voiceCallView = createComposeOverlayView { VoiceCallOverlayContent() }.also { view ->
                attachVoiceCallDrag(view)
                view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    placeVoiceCallWidget()
                }
                windowManager.addView(view, voiceCallParams)
            }
        }
        if (inviteBarView == null) {
            inviteBarParams = createLayoutParams(0, 24)
            inviteBarView = createComposeOverlayView { InviteOverlayContent() }.also { view ->
                view.setOnTouchListener { _, event ->
                    if (inviteBarHitTester.contains(event.x, event.y)) {
                        false
                    } else {
                        true
                    }
                }
                view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    placeInviteBar()
                }
                windowManager.addView(view, inviteBarParams)
            }
        }
        renderPanels()
    }

    @Composable
    private fun SessionSummaryOverlayContentWrapper() {
        val settings by XvmBlitzApp.instance.container.settingsRepository.settings.collectAsState(initial = currentSettings)
        val summary by XvmBlitzApp.instance.container.sessionSummaryStore.overlay.collectAsState()
        val preview by previewSessionSummaryScale.collectAsState()
        if (!settings.sessionSummaryOverlayVisible && !settings.configMode) {
            return
        }
        val scaleX = preview?.scaleX ?: settings.sessionSummaryOverlayScaleX
        val scaleY = preview?.scaleY ?: settings.sessionSummaryOverlayScaleY
        val useExample = settings.configMode && summary.battlesText == "-"
        SessionSummaryOverlayContent(
            battlesText = if (useExample) "12 б." else summary.battlesText,
            winRateText = if (useExample) "58.3%" else summary.winRateText,
            damageText = if (useExample) "1840 ур." else summary.damageText,
            scaleX = scaleX,
            scaleY = scaleY,
            configMode = settings.configMode,
        )
    }

    private fun createComposeOverlayView(content: @Composable () -> Unit): ComposeView {
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                XvmBlitzTheme {
                    content()
                }
            }
        }
    }

    @Composable
    private fun FloatingActionButtonContent() {
        val errorPulse by fabErrorPulse.collectAsState()
        val errorMessage by fabErrorMessage.collectAsState()
        val screen = currentScreenSizePx()
        OverlayFab(
            errorPulse = errorPulse,
            errorMessage = errorMessage,
            buttonX = currentSettings.captureButtonX,
            buttonY = currentSettings.captureButtonY,
            screenWidthPx = screen.width,
            screenHeightPx = screen.height,
            onWindowOriginOffset = { offsetX, offsetY ->
                if (captureButtonOriginOffsetX != offsetX || captureButtonOriginOffsetY != offsetY) {
                    captureButtonOriginOffsetX = offsetX
                    captureButtonOriginOffsetY = offsetY
                    updateCaptureButtonWindowPosition()
                }
            },
        )
    }

    @Composable
    private fun DirectionHintContent() {
        val direction by captureButtonOffScreenDirection.collectAsState()
        val resolved = direction ?: return
        CaptureButtonDirectionHint(direction = resolved)
    }

    @Composable
    private fun AlliesOverlayContent() {
        val settings by XvmBlitzApp.instance.container.settingsRepository.settings.collectAsState(initial = currentSettings)
        val battle by XvmBlitzApp.instance.container.battleStatisticsStore.state.collectAsState()
        val voice by XvmBlitzApp.instance.container.voiceRuntimeService.state.collectAsState()
        val selectedId by selectedCallPlayerId.collectAsState()
        val previewScale by previewPanelScale.collectAsState()
        val showPanels = settings.overlayVisible && (battle.hasBattle || settings.configMode)
        if (!showPanels) {
            return
        }
        OverlayPanel(
            players = if (battle.hasBattle) battle.allies else BattleStatisticsStore.previewAllies,
            scaleX = previewScale?.scaleX ?: settings.panelScaleX,
            scaleY = previewScale?.scaleY ?: settings.panelScaleY,
            configMode = settings.configMode,
            mirroredColumns = false,
            selectedPlayerId = selectedId,
            callAction = { player -> voiceRowCallAction(player, voice) },
            hitTester = alliesHitTester,
        )
    }

    @Composable
    private fun EnemiesOverlayContent() {
        val settings by XvmBlitzApp.instance.container.settingsRepository.settings.collectAsState(initial = currentSettings)
        val battle by XvmBlitzApp.instance.container.battleStatisticsStore.state.collectAsState()
        val voice by XvmBlitzApp.instance.container.voiceRuntimeService.state.collectAsState()
        val selectedId by selectedCallPlayerId.collectAsState()
        val previewScale by previewPanelScale.collectAsState()
        val showPanels = settings.overlayVisible && (battle.hasBattle || settings.configMode)
        if (!showPanels) {
            return
        }
        OverlayPanel(
            players = if (battle.hasBattle) battle.enemies else BattleStatisticsStore.previewEnemies,
            scaleX = previewScale?.scaleX ?: settings.panelScaleX,
            scaleY = previewScale?.scaleY ?: settings.panelScaleY,
            configMode = settings.configMode,
            mirroredColumns = true,
            selectedPlayerId = selectedId,
            callAction = { player -> voiceRowCallAction(player, voice) },
            hitTester = enemiesHitTester,
        )
    }

    @Composable
    private fun IncomingCallOverlayContent() {
        val voice by XvmBlitzApp.instance.container.voiceRuntimeService.state.collectAsState()
        if (!voice.showIncomingBanner) {
            return
        }
        VoiceIncomingBanner(
            state = voice,
            onAccept = { XvmBlitzApp.instance.container.voiceRuntimeService.acceptIncoming() },
            onReject = { XvmBlitzApp.instance.container.voiceRuntimeService.rejectIncoming() },
            hitTester = incomingCallHitTester,
        )
    }

    @Composable
    private fun VoiceCallOverlayContent() {
        val settings by XvmBlitzApp.instance.container.settingsRepository.settings.collectAsState(initial = currentSettings)
        val voice by XvmBlitzApp.instance.container.voiceRuntimeService.state.collectAsState()
        val preview by previewVoiceCallScale.collectAsState()
        if (!voice.showCallWidget && !settings.configMode) {
            return
        }
        val scaleX = preview?.scaleX ?: settings.voiceCallScaleX
        val scaleY = preview?.scaleY ?: settings.voiceCallScaleY
        VoiceCallWidget(
            state = voice,
            onToggleMute = { XvmBlitzApp.instance.container.voiceRuntimeService.toggleMute() },
            onHangup = { XvmBlitzApp.instance.container.voiceRuntimeService.hangup() },
            hitTester = voiceCallHitTester,
            scaleX = scaleX,
            scaleY = scaleY,
            configMode = settings.configMode,
        )
    }

    @Composable
    private fun InviteOverlayContent() {
        val selectedId by selectedCallPlayerId.collectAsState()
        val battle by XvmBlitzApp.instance.container.battleStatisticsStore.state.collectAsState()
        val voice by XvmBlitzApp.instance.container.voiceRuntimeService.state.collectAsState()
        val player = (battle.allies + battle.enemies)
            .firstOrNull { slot -> slot.id == selectedId }
            ?.takeIf { slot -> voiceRowCallAction(slot, voice) == OverlayCallActionKind.Invite }
            ?: return
        val playerId = player.id ?: return
        VoiceInviteBar(
            nickname = formatNicknameWithClan(player, mirrored = false),
            onInvite = {
                selectedCallPlayerId.value = null
                XvmBlitzApp.instance.container.voiceRuntimeService.invite(
                    playerId,
                    player.xvmUsage == XvmUsageStatus.Currently,
                )
            },
            onDismiss = { selectedCallPlayerId.value = null },
            hitTester = inviteBarHitTester,
        )
    }

    private fun startCaptureAfterHidingOverlay() {
        setHiddenForCapture(true)
        CaptureRequestActivity.start(this)
    }

    private fun signalFabAccessDenied(message: String) {
        fabErrorPulse.value = fabErrorPulse.value + 1
        fabErrorMessage.value = message
        vibrateError()
        fabErrorHideJob?.cancel()
        fabErrorHideJob = scope.launch {
            delay(4_500)
            fabErrorMessage.value = null
        }
        setHiddenForCapture(false)
        renderPanels()
        captureButtonView?.visibility = android.view.View.VISIBLE
        updateCaptureButtonDirectionHint()
    }

    private fun vibrateError() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService<Vibrator>()
        } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(180)
        }
    }

    private fun setHiddenForCapture(hidden: Boolean) {
        hiddenForCapture = hidden
        if (hidden) {
            alliesView?.visibility = android.view.View.GONE
            enemiesView?.visibility = android.view.View.GONE
            captureButtonView?.visibility = android.view.View.GONE
            sessionSummaryView?.visibility = android.view.View.GONE
            incomingCallView?.visibility = android.view.View.GONE
            voiceCallView?.visibility = android.view.View.GONE
            inviteBarView?.visibility = android.view.View.GONE
        } else {
            renderPanels()
        }
    }

    private fun hideBattleStatistics() {
        XvmBlitzApp.instance.container.battleStatisticsStore.clear()
        currentBattle = BattleUiState()
        hiddenForCapture = false
        renderPanels()
    }

    private fun renderPanels() {
        if (hiddenForCapture) {
            alliesView?.visibility = android.view.View.GONE
            enemiesView?.visibility = android.view.View.GONE
            captureButtonView?.visibility = android.view.View.GONE
            directionHintView?.visibility = android.view.View.GONE
            sessionSummaryView?.visibility = android.view.View.GONE
            incomingCallView?.visibility = android.view.View.GONE
            voiceCallView?.visibility = android.view.View.GONE
            inviteBarView?.visibility = android.view.View.GONE
            return
        }

        val showPanels = currentSettings.overlayVisible &&
            (currentBattle.hasBattle || currentSettings.configMode)
        val showCaptureButton = currentSettings.overlayVisible && !showPanels
        val showSessionSummary = currentSettings.overlayVisible &&
            (currentSettings.sessionSummaryOverlayVisible || currentSettings.configMode)
        val showIncoming = currentVoice.showIncomingBanner
        val showVoiceCall = currentVoice.showCallWidget || currentSettings.configMode
        val showInvite = showPanels &&
            !currentSettings.configMode &&
            !showIncoming &&
            selectedInvitePlayer() != null
        alliesView?.visibility =
            if (showPanels) android.view.View.VISIBLE else android.view.View.GONE
        enemiesView?.visibility =
            if (showPanels) android.view.View.VISIBLE else android.view.View.GONE
        captureButtonView?.visibility =
            if (showCaptureButton) android.view.View.VISIBLE else android.view.View.GONE
        sessionSummaryView?.visibility =
            if (showSessionSummary) android.view.View.VISIBLE else android.view.View.GONE
        incomingCallView?.visibility =
            if (showIncoming) android.view.View.VISIBLE else android.view.View.GONE
        voiceCallView?.visibility =
            if (showVoiceCall) android.view.View.VISIBLE else android.view.View.GONE
        inviteBarView?.visibility =
            if (showInvite) android.view.View.VISIBLE else android.view.View.GONE
        if (showSessionSummary) {
            updateSessionSummaryOverlayLayout(adjustScale = true)
        }
        if (showIncoming) {
            placeIncomingCallBanner()
        }
        if (showVoiceCall) {
            placeVoiceCallWidget()
        }
        if (showInvite) {
            placeInviteBar()
        }
        updateCaptureButtonDirectionHint()
    }

    private fun applySettings(settings: AppSettings) {
        alliesParams?.let { params ->
            params.x = settings.alliesX
            params.y = settings.alliesY
            alliesView?.let { windowManager.updateViewLayout(it, params) }
        }
        enemiesParams?.let { params ->
            params.x = settings.enemiesX
            params.y = settings.enemiesY
            enemiesView?.let { windowManager.updateViewLayout(it, params) }
        }
        sessionSummaryParams?.let { params ->
            params.x = settings.sessionSummaryOverlayX
            params.y = settings.sessionSummaryOverlayY
            sessionSummaryView?.let { windowManager.updateViewLayout(it, params) }
        }
        if (!voiceCallDragging &&
            settings.voiceCallX != Int.MIN_VALUE &&
            settings.voiceCallY != Int.MIN_VALUE
        ) {
            voiceCallParams?.let { params ->
                params.x = settings.voiceCallX
                params.y = settings.voiceCallY
                voiceCallView?.let { windowManager.updateViewLayout(it, params) }
            }
        }
        updateCaptureButtonWindowPosition()
        renderPanels()
        if (settings.sessionSummaryOverlayVisible || settings.configMode) {
            updateSessionSummaryOverlayLayout(adjustScale = true)
        }
    }

    private fun updateSessionSummaryOverlayLayout(adjustScale: Boolean) {
        val view = sessionSummaryView ?: return
        val params = sessionSummaryParams ?: return
        view.post {
            if (view.visibility != android.view.View.VISIBLE) {
                return@post
            }
            val screen = currentScreenSizePx()
            val width = view.width.coerceAtLeast(1)
            val height = view.height.coerceAtLeast(1)
            var scaleX = previewSessionSummaryScale.value?.scaleX
                ?: currentSettings.sessionSummaryOverlayScaleX
            val scaleY = previewSessionSummaryScale.value?.scaleY
                ?: currentSettings.sessionSummaryOverlayScaleY
            if (adjustScale && width > screen.width * 0.92f) {
                val fittedScaleX = coerceSessionSummaryScaleX(
                    scaleX * (screen.width * 0.92f / width),
                )
                if (fittedScaleX < scaleX - 0.01f) {
                    scaleX = fittedScaleX
                    scope.launch {
                        XvmBlitzApp.instance.container.settingsRepository
                            .updateSessionSummaryOverlayScale(scaleX, scaleY)
                    }
                }
            }
            val (clampedX, clampedY) = clampOverlayPosition(
                x = params.x,
                y = params.y,
                viewWidth = width,
                viewHeight = height,
                screen = screen,
            )
            if (clampedX != params.x || clampedY != params.y) {
                params.x = clampedX
                params.y = clampedY
                runCatching { windowManager.updateViewLayout(view, params) }
                scope.launch {
                    XvmBlitzApp.instance.container.settingsRepository
                        .updateSessionSummaryOverlayPosition(params.x, params.y)
                }
            }
        }
    }

    private fun clampOverlayPosition(
        x: Int,
        y: Int,
        viewWidth: Int,
        viewHeight: Int,
        screen: ScreenSizePx,
    ): Pair<Int, Int> {
        val maxX = (screen.width - viewWidth).coerceAtLeast(0)
        val maxY = (screen.height - viewHeight).coerceAtLeast(0)
        return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
    }

    private fun updateCaptureButtonWindowPosition() {
        val params = captureButtonParams ?: return
        val view = captureButtonView ?: return
        params.x = currentSettings.captureButtonX + captureButtonOriginOffsetX
        params.y = currentSettings.captureButtonY + captureButtonOriginOffsetY
        runCatching { windowManager.updateViewLayout(view, params) }
        updateCaptureButtonDirectionHint()
    }

    private fun currentScreenSizePx(): ScreenSizePx {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return ScreenSizePx(width = metrics.widthPixels, height = metrics.heightPixels)
    }

    private fun updateCaptureButtonDirectionHint() {
        val hintView = directionHintView
        val hintParams = directionHintParams
        if (hintView == null || hintParams == null) {
            return
        }
        val showCaptureButton = currentSettings.overlayVisible &&
            !hiddenForCapture &&
            !(currentBattle.hasBattle || currentSettings.configMode)
        if (!showCaptureButton) {
            captureButtonOffScreenDirection.value = null
            hintView.visibility = android.view.View.GONE
            return
        }

        val screen = currentScreenSizePx()
        val density = resources.displayMetrics.density
        val buttonWidth = (64 * density).toInt()
        val buttonHeight = (22 * density).toInt()
        val buttonX = currentSettings.captureButtonX
        val buttonY = currentSettings.captureButtonY
        val direction = resolveCaptureButtonOffScreenDirection(
            buttonX = buttonX,
            buttonY = buttonY,
            buttonWidth = buttonWidth,
            buttonHeight = buttonHeight,
            screenWidth = screen.width,
            screenHeight = screen.height,
        )
        captureButtonOffScreenDirection.value = direction
        if (direction == null) {
            hintView.visibility = android.view.View.GONE
            return
        }

        val hintSize = (36 * density).toInt()
        val edgePadding = (10 * density).toInt()
        val buttonCenterX = buttonX + buttonWidth / 2
        val buttonCenterY = buttonY + buttonHeight / 2
        when (direction) {
            CaptureButtonOffScreenDirection.Left -> {
                hintParams.x = edgePadding
                hintParams.y = (buttonCenterY - hintSize / 2)
                    .coerceIn(edgePadding, screen.height - hintSize - edgePadding)
            }
            CaptureButtonOffScreenDirection.Right -> {
                hintParams.x = screen.width - hintSize - edgePadding
                hintParams.y = (buttonCenterY - hintSize / 2)
                    .coerceIn(edgePadding, screen.height - hintSize - edgePadding)
            }
            CaptureButtonOffScreenDirection.Top -> {
                hintParams.x = (buttonCenterX - hintSize / 2)
                    .coerceIn(edgePadding, screen.width - hintSize - edgePadding)
                hintParams.y = edgePadding
            }
            CaptureButtonOffScreenDirection.Bottom -> {
                hintParams.x = (buttonCenterX - hintSize / 2)
                    .coerceIn(edgePadding, screen.width - hintSize - edgePadding)
                hintParams.y = screen.height - hintSize - edgePadding
            }
        }
        hintView.visibility = android.view.View.VISIBLE
        runCatching { windowManager.updateViewLayout(hintView, hintParams) }
    }

    private fun attachCaptureButtonTouch(view: ComposeView) {
        val dragThresholdPx = CAPTURE_BUTTON_DRAG_THRESHOLD_DP * resources.displayMetrics.density
        var initialButtonX = 0
        var initialButtonY = 0
        var touchX = 0f
        var touchY = 0f
        var dragged = false

        view.isClickable = true
        view.isFocusable = true
        view.setOnTouchListener { _, event ->
            val params = captureButtonParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialButtonX = currentSettings.captureButtonX
                    initialButtonY = currentSettings.captureButtonY
                    touchX = event.rawX
                    touchY = event.rawY
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > dragThresholdPx || abs(dy) > dragThresholdPx) {
                        dragged = true
                    }
                    if (dragged) {
                        val buttonX = initialButtonX + dx.toInt()
                        val buttonY = initialButtonY + dy.toInt()
                        currentSettings = currentSettings.copy(
                            captureButtonX = buttonX,
                            captureButtonY = buttonY,
                        )
                        params.x = buttonX + captureButtonOriginOffsetX
                        params.y = buttonY + captureButtonOriginOffsetY
                        windowManager.updateViewLayout(view, params)
                        updateCaptureButtonDirectionHint()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        return@setOnTouchListener true
                    }
                    val totalDx = abs(event.rawX - touchX)
                    val totalDy = abs(event.rawY - touchY)
                    val isClick = totalDx <= dragThresholdPx && totalDy <= dragThresholdPx
                    if (!isClick) {
                        val buttonX = currentSettings.captureButtonX
                        val buttonY = currentSettings.captureButtonY
                        scope.launch {
                            XvmBlitzApp.instance.container.settingsRepository
                                .updateCaptureButtonPosition(buttonX, buttonY)
                        }
                    } else if (currentSettings.configMode) {
                        scope.launch {
                            XvmBlitzApp.instance.container.settingsRepository.setOverlayVisible(true)
                        }
                    } else {
                        startCaptureAfterHidingOverlay()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun attachSessionSummaryDrag(view: ComposeView) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var initialScaleX = 1f
        var initialScaleY = 1f
        var candidateGesture = PanelGesture.Drag
        var gesture = PanelGesture.None
        var dragging = false
        var longPressTriggered = false
        var longPressJob: Job? = null
        val density = resources.displayMetrics.density
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()

        view.setOnTouchListener { _, event ->
            val params = sessionSummaryParams ?: return@setOnTouchListener false
            val configMode = currentSettings.configMode
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragging = false
                    longPressTriggered = false
                    val preview = previewSessionSummaryScale.value
                    initialScaleX = preview?.scaleX ?: currentSettings.sessionSummaryOverlayScaleX
                    initialScaleY = preview?.scaleY ?: currentSettings.sessionSummaryOverlayScaleY
                    candidateGesture = if (configMode) {
                        val width = if (view.width > 0) {
                            view.width
                        } else {
                            (OverlayBaseSessionSummaryWidthDp * density * initialScaleX).toInt()
                        }
                        val height = if (view.height > 0) {
                            view.height
                        } else {
                            (OverlayBaseSessionSummaryHeightDp * density * initialScaleY).toInt()
                        }
                        resolveCornerResizeGesture(event.x, event.y, width, height, density)
                    } else {
                        PanelGesture.Drag
                    }
                    gesture = if (configMode) PanelGesture.Pending else PanelGesture.None
                    longPressJob?.cancel()
                    longPressJob = scope.launch {
                        delay(longPressTimeoutMs)
                        if (!dragging &&
                            !longPressTriggered &&
                            (gesture == PanelGesture.Pending || gesture == PanelGesture.None)
                        ) {
                            longPressTriggered = true
                            gesture = PanelGesture.None
                            showSessionSummaryContextMenu(view)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        longPressJob?.cancel()
                        dragging = true
                        if (configMode && gesture == PanelGesture.Pending) {
                            gesture = if (candidateGesture == PanelGesture.ResizeBoth) {
                                PanelGesture.ResizeBoth
                            } else {
                                PanelGesture.Drag
                            }
                        } else if (!configMode) {
                            gesture = PanelGesture.Drag
                        }
                    }
                    when (gesture) {
                        PanelGesture.Drag -> {
                            params.x = initialX + dx.toInt()
                            params.y = initialY + dy.toInt()
                            windowManager.updateViewLayout(view, params)
                        }
                        PanelGesture.ResizeBoth -> {
                            previewSessionSummaryScale.value = PanelScalePreview(
                                scaleX = sessionSummaryOverlayScaleXFromWidthDelta(
                                    initialScaleX,
                                    initialScaleY,
                                    dx,
                                    density,
                                ),
                                scaleY = sessionSummaryOverlayScaleYFromHeightDelta(
                                    initialScaleY,
                                    dy,
                                    density,
                                ),
                            )
                        }
                        PanelGesture.Pending, PanelGesture.None,
                        PanelGesture.ResizeHorizontal, PanelGesture.ResizeVertical,
                        -> Unit
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressJob?.cancel()
                    if (!longPressTriggered) {
                        when (gesture) {
                            PanelGesture.Drag -> {
                                if (dragging) {
                                    scope.launch {
                                        XvmBlitzApp.instance.container.settingsRepository
                                            .updateSessionSummaryOverlayPosition(params.x, params.y)
                                    }
                                }
                            }
                            PanelGesture.ResizeBoth,
                            -> {
                                val preview = previewSessionSummaryScale.value
                                scope.launch {
                                    if (preview != null) {
                                        XvmBlitzApp.instance.container.settingsRepository
                                            .updateSessionSummaryOverlayScale(preview.scaleX, preview.scaleY)
                                    }
                                    previewSessionSummaryScale.value = null
                                }
                            }
                            PanelGesture.Pending, PanelGesture.None,
                            PanelGesture.ResizeHorizontal, PanelGesture.ResizeVertical,
                            -> Unit
                        }
                    }
                    gesture = PanelGesture.None
                    candidateGesture = PanelGesture.Drag
                    true
                }
                else -> false
            }
        }
    }

    private fun showSessionSummaryContextMenu(anchor: View) {
        val params = sessionSummaryParams ?: return
        setWindowFocusable(anchor, params, focusable = true)
        val popup = PopupMenu(this, anchor, Gravity.CENTER)
        popup.menu.add(0, MENU_HIDE_SESSION_SUMMARY, 0, "Скрыть")
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == MENU_HIDE_SESSION_SUMMARY) {
                scope.launch {
                    XvmBlitzApp.instance.container.settingsRepository
                        .setSessionSummaryOverlayVisible(false)
                }
                true
            } else {
                false
            }
        }
        popup.setOnDismissListener {
            setWindowFocusable(anchor, params, focusable = false)
        }
        popup.show()
    }

    private fun attachPanelDrag(view: ComposeView, kind: PanelKind) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var downViewX = 0f
        var downViewY = 0f
        var initialScaleX = 1f
        var initialScaleY = 1f
        var candidateGesture = PanelGesture.Drag
        var gesture = PanelGesture.None
        var dragging = false
        var longPressTriggered = false
        var longPressJob: Job? = null
        val density = resources.displayMetrics.density
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()

        view.setOnTouchListener { _, event ->
            val params = when (kind) {
                PanelKind.Allies -> alliesParams
                PanelKind.Enemies -> enemiesParams
            } ?: return@setOnTouchListener false
            val configMode = currentSettings.configMode

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!configMode && hitTesterFor(kind).contains(event.x, event.y)) {
                        return@setOnTouchListener false
                    }
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    downViewX = event.x
                    downViewY = event.y
                    dragging = false
                    longPressTriggered = false
                    val preview = previewPanelScale.value
                    initialScaleX = preview?.scaleX ?: currentSettings.panelScaleX
                    initialScaleY = preview?.scaleY ?: currentSettings.panelScaleY
                    candidateGesture = if (configMode) {
                        resolvePanelGesture(event.x, event.y, view.width, view.height, density)
                    } else {
                        PanelGesture.Drag
                    }
                    gesture = if (configMode) PanelGesture.Pending else PanelGesture.None
                    longPressJob?.cancel()
                    longPressJob = scope.launch {
                        delay(longPressTimeoutMs)
                        if (!longPressTriggered &&
                            (gesture == PanelGesture.Pending || gesture == PanelGesture.None)
                        ) {
                            longPressTriggered = true
                            gesture = PanelGesture.None
                            showPanelContextMenu(view)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        longPressJob?.cancel()
                        dragging = true
                        if (configMode && gesture == PanelGesture.Pending) {
                            gesture = when (candidateGesture) {
                                PanelGesture.ResizeHorizontal ->
                                    if (abs(dx) >= abs(dy)) PanelGesture.ResizeHorizontal else PanelGesture.Drag
                                PanelGesture.ResizeVertical ->
                                    if (abs(dy) >= abs(dx)) PanelGesture.ResizeVertical else PanelGesture.Drag
                                PanelGesture.ResizeBoth -> PanelGesture.ResizeBoth
                                else -> PanelGesture.Drag
                            }
                        } else if (!configMode) {
                            gesture = PanelGesture.Drag
                        }
                    }
                    when (gesture) {
                        PanelGesture.Drag -> {
                            params.x = initialX + dx.toInt()
                            params.y = initialY + dy.toInt()
                            windowManager.updateViewLayout(view, params)
                            placeInviteBar()
                        }
                        PanelGesture.ResizeHorizontal -> {
                            previewPanelScale.value = PanelScalePreview(
                                scaleX = scaleXFromWidthDelta(initialScaleX, dx, density),
                                scaleY = initialScaleY,
                            )
                        }
                        PanelGesture.ResizeVertical -> {
                            previewPanelScale.value = PanelScalePreview(
                                scaleX = initialScaleX,
                                scaleY = scaleYFromHeightDelta(initialScaleY, dy, density),
                            )
                        }
                        PanelGesture.ResizeBoth -> {
                            previewPanelScale.value = PanelScalePreview(
                                scaleX = scaleXFromWidthDelta(initialScaleX, dx, density),
                                scaleY = scaleYFromHeightDelta(initialScaleY, dy, density),
                            )
                        }
                        PanelGesture.Pending, PanelGesture.None -> Unit
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressJob?.cancel()
                    if (event.actionMasked == MotionEvent.ACTION_UP &&
                        !longPressTriggered &&
                        !dragging &&
                        !configMode
                    ) {
                        val tappedId = hitTesterFor(kind).playerAt(downViewX, downViewY)
                        selectedCallPlayerId.value = if (tappedId != null && tappedId == selectedCallPlayerId.value) {
                            null
                        } else {
                            tappedId
                        }
                    }
                    if (!longPressTriggered) {
                        when (gesture) {
                            PanelGesture.Drag -> {
                                if (dragging) {
                                    scope.launch {
                                        val settingsRepository = XvmBlitzApp.instance.container.settingsRepository
                                        when (kind) {
                                            PanelKind.Allies ->
                                                settingsRepository.updateAlliesPosition(params.x, params.y)
                                            PanelKind.Enemies ->
                                                settingsRepository.updateEnemiesPosition(params.x, params.y)
                                        }
                                    }
                                }
                            }
                            PanelGesture.ResizeHorizontal,
                            PanelGesture.ResizeVertical,
                            PanelGesture.ResizeBoth,
                            -> {
                                val preview = previewPanelScale.value
                                scope.launch {
                                    if (preview != null) {
                                        XvmBlitzApp.instance.container.settingsRepository.updatePanelScale(
                                            preview.scaleX,
                                            preview.scaleY,
                                        )
                                    }
                                    previewPanelScale.value = null
                                }
                            }
                            PanelGesture.Pending, PanelGesture.None -> Unit
                        }
                    }
                    gesture = PanelGesture.None
                    candidateGesture = PanelGesture.Drag
                    true
                }
                else -> false
            }
        }
    }

    private fun hitTesterFor(kind: PanelKind): OverlayInteractiveHitTester {
        return when (kind) {
            PanelKind.Allies -> alliesHitTester
            PanelKind.Enemies -> enemiesHitTester
        }
    }

    private fun attachVoiceCallDrag(view: ComposeView) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var initialScaleX = 1f
        var initialScaleY = 1f
        var candidateGesture = PanelGesture.Drag
        var gesture = PanelGesture.None
        var dragging = false
        val density = resources.displayMetrics.density
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        view.setOnTouchListener { _, event ->
            val params = voiceCallParams ?: return@setOnTouchListener false
            val configMode = currentSettings.configMode
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!configMode && voiceCallHitTester.contains(event.x, event.y)) {
                        return@setOnTouchListener false
                    }
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragging = false
                    voiceCallDragging = false
                    val preview = previewVoiceCallScale.value
                    initialScaleX = preview?.scaleX ?: currentSettings.voiceCallScaleX
                    initialScaleY = preview?.scaleY ?: currentSettings.voiceCallScaleY
                    candidateGesture = if (configMode) {
                        val width = if (view.width > 0) {
                            view.width
                        } else {
                            (OverlayBaseVoiceCallWidthDp * density * initialScaleX).toInt()
                        }
                        val height = if (view.height > 0) {
                            view.height
                        } else {
                            (OverlayBaseVoiceCallHeightDp * density * initialScaleY).toInt()
                        }
                        resolveCornerResizeGesture(event.x, event.y, width, height, density)
                    } else {
                        PanelGesture.Drag
                    }
                    gesture = if (configMode) PanelGesture.Pending else PanelGesture.None
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        voiceCallDragging = true
                        if (configMode && gesture == PanelGesture.Pending) {
                            gesture = if (candidateGesture == PanelGesture.ResizeBoth) {
                                PanelGesture.ResizeBoth
                            } else {
                                PanelGesture.Drag
                            }
                        } else if (!configMode) {
                            gesture = PanelGesture.Drag
                        }
                    }
                    when (gesture) {
                        PanelGesture.Drag -> {
                            val screen = currentScreenSizePx()
                            val (clampedX, clampedY) = clampOverlayPosition(
                                x = initialX + dx.toInt(),
                                y = initialY + dy.toInt(),
                                viewWidth = view.width.coerceAtLeast(1),
                                viewHeight = view.height.coerceAtLeast(1),
                                screen = screen,
                            )
                            params.x = clampedX
                            params.y = clampedY
                            windowManager.updateViewLayout(view, params)
                        }
                        PanelGesture.ResizeBoth -> {
                            previewVoiceCallScale.value = PanelScalePreview(
                                scaleX = voiceCallOverlayScaleXFromWidthDelta(
                                    initialScaleX,
                                    initialScaleY,
                                    dx,
                                    density,
                                ),
                                scaleY = voiceCallOverlayScaleYFromHeightDelta(
                                    initialScaleY,
                                    dy,
                                    density,
                                ),
                            )
                        }
                        PanelGesture.Pending, PanelGesture.None,
                        PanelGesture.ResizeHorizontal, PanelGesture.ResizeVertical,
                        -> Unit
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    when (gesture) {
                        PanelGesture.Drag -> {
                            if (dragging) {
                                scope.launch {
                                    XvmBlitzApp.instance.container.settingsRepository
                                        .updateVoiceCallPosition(params.x, params.y)
                                    voiceCallDragging = false
                                }
                            } else {
                                voiceCallDragging = false
                            }
                        }
                        PanelGesture.ResizeBoth,
                        -> {
                            val preview = previewVoiceCallScale.value
                            scope.launch {
                                if (preview != null) {
                                    XvmBlitzApp.instance.container.settingsRepository
                                        .updateVoiceCallScale(preview.scaleX, preview.scaleY)
                                }
                                previewVoiceCallScale.value = null
                                voiceCallDragging = false
                            }
                        }
                        PanelGesture.Pending, PanelGesture.None,
                        PanelGesture.ResizeHorizontal, PanelGesture.ResizeVertical,
                        -> {
                            voiceCallDragging = false
                        }
                    }
                    gesture = PanelGesture.None
                    candidateGesture = PanelGesture.Drag
                    true
                }
                else -> false
            }
        }
    }

    private fun placeIncomingCallBanner() {
        val view = incomingCallView ?: return
        val params = incomingCallParams ?: return
        if (view.visibility != android.view.View.VISIBLE) {
            return
        }
        view.post {
            if (view.visibility != android.view.View.VISIBLE) {
                return@post
            }
            val screen = currentScreenSizePx()
            val density = resources.displayMetrics.density
            val width = view.width.coerceAtLeast(1)
            params.x = ((screen.width - width) / 2).coerceAtLeast(0)
            params.y = (12 * density).toInt()
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun placeVoiceCallWidget() {
        val view = voiceCallView ?: return
        val params = voiceCallParams ?: return
        if (view.visibility != android.view.View.VISIBLE || voiceCallDragging) {
            return
        }
        view.post {
            if (view.visibility != android.view.View.VISIBLE || voiceCallDragging) {
                return@post
            }
            val screen = currentScreenSizePx()
            val density = resources.displayMetrics.density
            val padding = (12 * density).toInt()
            val width = view.width.coerceAtLeast(1)
            val height = view.height.coerceAtLeast(1)
            val storedX = currentSettings.voiceCallX
            val storedY = currentSettings.voiceCallY
            val needsDefault = storedX == Int.MIN_VALUE || storedY == Int.MIN_VALUE
            val targetX = if (needsDefault) {
                (screen.width - width - padding).coerceAtLeast(padding)
            } else {
                params.x
            }
            val targetY = if (needsDefault) {
                padding
            } else {
                params.y
            }
            val (clampedX, clampedY) = clampOverlayPosition(
                x = targetX,
                y = targetY,
                viewWidth = width,
                viewHeight = height,
                screen = screen,
            )
            if (clampedX != params.x || clampedY != params.y) {
                params.x = clampedX
                params.y = clampedY
                runCatching { windowManager.updateViewLayout(view, params) }
            }
            if (needsDefault) {
                scope.launch {
                    XvmBlitzApp.instance.container.settingsRepository
                        .updateVoiceCallPosition(params.x, params.y)
                }
            }
        }
    }

    private fun selectedInvitePlayer(): PlayerSlot? {
        val selectedId = selectedCallPlayerId.value ?: return null
        val player = currentBattle.allies.firstOrNull { slot -> slot.id == selectedId }
            ?: currentBattle.enemies.firstOrNull { slot -> slot.id == selectedId }
            ?: return null
        return player.takeIf { slot -> voiceRowCallAction(slot, currentVoice) == OverlayCallActionKind.Invite }
    }

    private fun placeInviteBar() {
        val view = inviteBarView ?: return
        val params = inviteBarParams ?: return
        if (view.visibility != android.view.View.VISIBLE) {
            return
        }
        view.post {
            if (view.visibility != android.view.View.VISIBLE) {
                return@post
            }
            val selectedId = selectedCallPlayerId.value
            val isAllies = currentBattle.allies.any { slot -> slot.id == selectedId }
            val panelView = if (isAllies) alliesView else enemiesView
            val panelParams = if (isAllies) alliesParams else enemiesParams
            if (panelView == null || panelParams == null) {
                return@post
            }
            val screen = currentScreenSizePx()
            val density = resources.displayMetrics.density
            val gap = (8 * density).toInt()
            val width = view.width.coerceAtLeast(1)
            val height = view.height.coerceAtLeast(1)
            var x = panelParams.x
            var y = panelParams.y + panelView.height + gap
            if (y + height > screen.height) {
                y = panelParams.y - height - gap
            }
            val (clampedX, clampedY) = clampOverlayPosition(
                x = x,
                y = y,
                viewWidth = width,
                viewHeight = height,
                screen = screen,
            )
            if (clampedX != params.x || clampedY != params.y) {
                params.x = clampedX
                params.y = clampedY
                runCatching { windowManager.updateViewLayout(view, params) }
            }
        }
    }

    private fun updateForegroundMicrophone(enabled: Boolean) {
        if (microphoneForeground == enabled) {
            return
        }
        microphoneForeground = enabled
        startAsForeground()
    }

    private fun voiceRowCallAction(player: PlayerSlot, voice: VoiceUiState): OverlayCallActionKind {
        if (player.isMissing) {
            return OverlayCallActionKind.Hidden
        }
        val playerId = player.id ?: return OverlayCallActionKind.Hidden
        if (playerId == voice.selfPlayerId) {
            return OverlayCallActionKind.Hidden
        }
        if (!voice.isLocalPremium) {
            return OverlayCallActionKind.Hidden
        }
        if (playerId in voice.memberIds) {
            return OverlayCallActionKind.Hidden
        }
        if (voice.phase == VoicePhase.InCall && voice.memberIds.size >= voice.maxParticipants) {
            return OverlayCallActionKind.Hidden
        }
        if (voice.outgoingTargetPlayerId == playerId) {
            return OverlayCallActionKind.Hidden
        }
        return OverlayCallActionKind.Invite
    }

    private fun showPanelContextMenu(anchor: View) {
        val params = when (anchor) {
            alliesView -> alliesParams
            enemiesView -> enemiesParams
            else -> null
        } ?: return
        setWindowFocusable(anchor, params, focusable = true)
        val popup = PopupMenu(this, anchor, Gravity.CENTER)
        popup.menu.add(0, MENU_HIDE_STATS, 0, "Скрыть статистику")
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == MENU_HIDE_STATS) {
                hideBattleStatistics()
                true
            } else {
                false
            }
        }
        popup.setOnDismissListener {
            setWindowFocusable(anchor, params, focusable = false)
        }
        popup.show()
    }

    private fun setWindowFocusable(
        view: View,
        params: WindowManager.LayoutParams,
        focusable: Boolean,
    ) {
        params.flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun resolveCornerResizeGesture(
        touchX: Float,
        touchY: Float,
        width: Int,
        height: Int,
        density: Float,
    ): PanelGesture {
        val cornerPx = OverlayResizeHandleDp * density
        val inCorner =
            touchX >= width - cornerPx &&
                touchY >= height - cornerPx
        return if (inCorner) PanelGesture.ResizeBoth else PanelGesture.Drag
    }

    private fun resolvePanelGesture(
        touchX: Float,
        touchY: Float,
        width: Int,
        height: Int,
        density: Float,
    ): PanelGesture {
        val cornerPx = OverlayResizeHandleDp * density
        val edgeThicknessPx = OverlayResizeEdgeThicknessDp * density
        val edgeLengthPx = OverlayResizeEdgeLengthDp * density
        val inCorner =
            touchX >= width - cornerPx &&
                touchY >= height - cornerPx
        if (inCorner) {
            return PanelGesture.ResizeBoth
        }
        val rightCenterTop = (height - edgeLengthPx) / 2f
        val rightCenterBottom = rightCenterTop + edgeLengthPx
        val inRightHandle =
            touchX >= width - edgeThicknessPx &&
                touchY in rightCenterTop..rightCenterBottom
        if (inRightHandle) {
            return PanelGesture.ResizeHorizontal
        }
        val bottomCenterLeft = (width - edgeLengthPx) / 2f
        val bottomCenterRight = bottomCenterLeft + edgeLengthPx
        val inBottomHandle =
            touchY >= height - edgeThicknessPx &&
                touchX in bottomCenterLeft..bottomCenterRight
        if (inBottomHandle) {
            return PanelGesture.ResizeVertical
        }
        return PanelGesture.Drag
    }

    private fun scaleXFromWidthDelta(initialScaleX: Float, widthDelta: Float, density: Float): Float {
        val baseWidthPx = OverlayBasePanelWidthDp * density
        val startWidthPx = baseWidthPx * initialScaleX
        return coerceOverlayScaleX((startWidthPx + widthDelta) / baseWidthPx)
    }

    private fun scaleYFromHeightDelta(
        initialScaleY: Float,
        heightDelta: Float,
        density: Float,
    ): Float {
        val baseHeightPx = OverlayBasePanelHeightDp * density
        val startHeightPx = baseHeightPx * initialScaleY
        return coerceOverlayScaleY((startHeightPx + heightDelta) / baseHeightPx)
    }

    private fun createLayoutParams(x: Int, y: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun removeViews() {
        alliesView?.let { runCatching { windowManager.removeView(it) } }
        enemiesView?.let { runCatching { windowManager.removeView(it) } }
        captureButtonView?.let { runCatching { windowManager.removeView(it) } }
        directionHintView?.let { runCatching { windowManager.removeView(it) } }
        sessionSummaryView?.let { runCatching { windowManager.removeView(it) } }
        incomingCallView?.let { runCatching { windowManager.removeView(it) } }
        voiceCallView?.let { runCatching { windowManager.removeView(it) } }
        inviteBarView?.let { runCatching { windowManager.removeView(it) } }
        alliesView = null
        enemiesView = null
        captureButtonView = null
        directionHintView = null
        sessionSummaryView = null
        incomingCallView = null
        voiceCallView = null
        inviteBarView = null
        alliesParams = null
        enemiesParams = null
        captureButtonParams = null
        directionHintParams = null
        sessionSummaryParams = null
        incomingCallParams = null
        voiceCallParams = null
        inviteBarParams = null
    }

    private data class ScreenSizePx(
        val width: Int,
        val height: Int,
    )

    private fun startAsForeground() {
        val channelId = "xvm_overlay"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Overlay", NotificationManager.IMPORTANCE_LOW),
        )

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggle = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.action_toggle_overlay), toggle)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val hasMicPermission = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val types = if (microphoneForeground && hasMicPermission) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            startForeground(
                NOTIFICATION_ID,
                notification,
                types,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private enum class PanelKind { Allies, Enemies }

    private enum class PanelGesture {
        None,
        Pending,
        Drag,
        ResizeHorizontal,
        ResizeVertical,
        ResizeBoth,
    }

    private data class PanelScalePreview(
        val scaleX: Float,
        val scaleY: Float,
    )

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val MENU_HIDE_STATS = 1
        private const val MENU_HIDE_SESSION_SUMMARY = 2
        const val OVERLAY_HIDE_DELAY_MS = 400L
        private const val CAPTURE_BUTTON_DRAG_THRESHOLD_DP = 24f
        const val ACTION_TOGGLE = "ru.xvmblitz.android.overlay.TOGGLE"
        const val ACTION_CAPTURE = "ru.xvmblitz.android.overlay.CAPTURE"
        const val ACTION_HIDE_FOR_CAPTURE = "ru.xvmblitz.android.overlay.HIDE_FOR_CAPTURE"
        const val ACTION_RESTORE_AFTER_CAPTURE = "ru.xvmblitz.android.overlay.RESTORE_AFTER_CAPTURE"
        const val ACTION_ACCESS_DENIED = "ru.xvmblitz.android.overlay.ACCESS_DENIED"
        const val EXTRA_ACCESS_DENIED_MESSAGE = "access_denied_message"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OverlayService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }

        fun hideForCapture(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java).setAction(ACTION_HIDE_FOR_CAPTURE),
            )
        }

        fun restoreAfterCapture(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java).setAction(ACTION_RESTORE_AFTER_CAPTURE),
            )
        }

        fun showAccessDenied(context: Context, message: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OverlayService::class.java)
                    .setAction(ACTION_ACCESS_DENIED)
                    .putExtra(EXTRA_ACCESS_DENIED_MESSAGE, message),
            )
        }

        fun startCapture(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OverlayService::class.java).setAction(ACTION_CAPTURE),
            )
        }
    }
}
