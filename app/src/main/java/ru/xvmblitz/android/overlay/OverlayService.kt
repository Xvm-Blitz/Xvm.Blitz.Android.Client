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
import ru.xvmblitz.android.data.settings.AppSettings
import ru.xvmblitz.android.domain.BattleStatisticsStore
import ru.xvmblitz.android.domain.BattleUiState
import ru.xvmblitz.android.ui.MainActivity
import ru.xvmblitz.android.ui.theme.XvmBlitzTheme
import ru.xvmblitz.android.util.AppAlertNotifier
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
    private var alliesParams: WindowManager.LayoutParams? = null
    private var enemiesParams: WindowManager.LayoutParams? = null
    private var captureButtonParams: WindowManager.LayoutParams? = null
    private var directionHintParams: WindowManager.LayoutParams? = null
    private var sessionSummaryParams: WindowManager.LayoutParams? = null
    private var collectJob: Job? = null
    private var currentSettings = AppSettings()
    private var currentBattle = BattleUiState()
    private var hiddenForCapture = false
    private var captureButtonOriginOffsetX = 0
    private var captureButtonOriginOffsetY = 0
    private val previewPanelScale = MutableStateFlow<PanelScalePreview?>(null)
    private val previewSessionSummaryScale = MutableStateFlow<PanelScalePreview?>(null)
    private val fabErrorPulse = MutableStateFlow(0)
    private val fabErrorMessage = MutableStateFlow<String?>(null)
    private val captureButtonOffScreenDirection =
        MutableStateFlow<CaptureButtonOffScreenDirection?>(null)
    private var fabErrorHideJob: Job? = null
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
                    renderPanels()
                }
            }
            launch {
                container.sessionSummaryStore.overlay.collectLatest {
                    sessionSummaryView?.invalidate()
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
                    ?: AppAlertNotifier.DEFAULT_API_KEY_MESSAGE
                signalFabAccessDenied(message)
            }
            ACTION_STOP -> stopSelf()
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
        val useExample = settings.configMode && summary.battlesText == "—"
        SessionSummaryOverlayContent(
            battlesText = if (useExample) "12 б." else summary.battlesText,
            winRateText = if (useExample) "58.3%" else summary.winRateText,
            damageText = if (useExample) "1840 ур." else summary.damageText,
            scaleX = scaleX,
            scaleY = scaleY,
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
        )
    }

    @Composable
    private fun EnemiesOverlayContent() {
        val settings by XvmBlitzApp.instance.container.settingsRepository.settings.collectAsState(initial = currentSettings)
        val battle by XvmBlitzApp.instance.container.battleStatisticsStore.state.collectAsState()
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
            return
        }

        val showPanels = currentSettings.overlayVisible &&
            (currentBattle.hasBattle || currentSettings.configMode)
        val showCaptureButton = currentSettings.overlayVisible && !showPanels
        val showSessionSummary = currentSettings.overlayVisible &&
            (currentSettings.sessionSummaryOverlayVisible || currentSettings.configMode)
        alliesView?.visibility =
            if (showPanels) android.view.View.VISIBLE else android.view.View.GONE
        enemiesView?.visibility =
            if (showPanels) android.view.View.VISIBLE else android.view.View.GONE
        captureButtonView?.visibility =
            if (showCaptureButton) android.view.View.VISIBLE else android.view.View.GONE
        sessionSummaryView?.visibility =
            if (showSessionSummary) android.view.View.VISIBLE else android.view.View.GONE
        if (showSessionSummary) {
            updateSessionSummaryOverlayLayout(adjustScale = true)
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
                        resolvePanelGesture(event.x, event.y, width, height, density)
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
                        }
                        PanelGesture.ResizeHorizontal -> {
                            previewSessionSummaryScale.value = PanelScalePreview(
                                scaleX = sessionSummaryOverlayScaleXFromWidthDelta(
                                    initialScaleX,
                                    initialScaleY,
                                    dx,
                                    density,
                                ),
                                scaleY = initialScaleY,
                            )
                        }
                        PanelGesture.ResizeVertical -> {
                            previewSessionSummaryScale.value = PanelScalePreview(
                                scaleX = initialScaleX,
                                scaleY = sessionSummaryOverlayScaleYFromHeightDelta(
                                    initialScaleY,
                                    dy,
                                    density,
                                ),
                            )
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
                        PanelGesture.Pending, PanelGesture.None -> Unit
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
                            PanelGesture.ResizeHorizontal,
                            PanelGesture.ResizeVertical,
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
        var initialScaleX = 1f
        var initialScaleY = 1f
        var candidateGesture = PanelGesture.Drag
        var gesture = PanelGesture.None
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
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
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
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        longPressJob?.cancel()
                        if (configMode && gesture == PanelGesture.Pending) {
                            gesture = when (candidateGesture) {
                                PanelGesture.ResizeHorizontal ->
                                    if (abs(dx) >= abs(dy)) PanelGesture.ResizeHorizontal else PanelGesture.Drag
                                PanelGesture.ResizeVertical ->
                                    if (abs(dy) >= abs(dx)) PanelGesture.ResizeVertical else PanelGesture.Drag
                                PanelGesture.ResizeBoth -> PanelGesture.ResizeBoth
                                else -> PanelGesture.Drag
                            }
                        }
                    }
                    when (gesture) {
                        PanelGesture.Drag -> {
                            params.x = initialX + dx.toInt()
                            params.y = initialY + dy.toInt()
                            windowManager.updateViewLayout(view, params)
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
                    if (!longPressTriggered) {
                        when (gesture) {
                            PanelGesture.Drag -> {
                                if (configMode) {
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
        alliesView = null
        enemiesView = null
        captureButtonView = null
        directionHintView = null
        sessionSummaryView = null
        alliesParams = null
        enemiesParams = null
        captureButtonParams = null
        directionHintParams = null
        sessionSummaryParams = null
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
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.action_toggle_overlay), toggle)
            .addAction(0, getString(R.string.action_stop_overlay), stop)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
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
        const val ACTION_STOP = "ru.xvmblitz.android.overlay.STOP"
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
