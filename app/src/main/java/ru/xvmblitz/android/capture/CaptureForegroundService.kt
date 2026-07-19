package ru.xvmblitz.android.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import ru.xvmblitz.android.R
import ru.xvmblitz.android.XvmBlitzApp
import ru.xvmblitz.android.data.api.BattleStatisticsDto
import ru.xvmblitz.android.overlay.OverlayService
import ru.xvmblitz.android.util.AppAlertNotifier
import ru.xvmblitz.android.util.CaptureAccessGuard
import ru.xvmblitz.android.util.HttpErrorMessages

class CaptureForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }

        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()

        scope.launch {
            val container = XvmBlitzApp.instance.container
            val apiKey = container.authRepository.getApiKeyOrNull()
            if (apiKey.isNullOrBlank()) {
                notifyAccessDenied(AppAlertNotifier.DEFAULT_API_KEY_MESSAGE)
                stopSelf()
                return@launch
            }

            try {
                CaptureEvents.emit(CaptureEvents.Result.Loading)
                OverlayService.hideForCapture(applicationContext)
                delay(OverlayService.OVERLAY_HIDE_DELAY_MS)

                ScreenCaptureSession(applicationContext, resultCode, data).use { session ->
                    val recognized = recognizeWithRetries(
                        session = session,
                        upload = { bytes ->
                            val body = bytes.toRequestBody(JPEG_MEDIA_TYPE)
                            val part = MultipartBody.Part.createFormData(
                                "file",
                                "battleScreenshot.jpg",
                                body,
                            )
                            withTimeout(STATISTICS_REQUEST_TIMEOUT) {
                                container.statisticsApi.getBattleStatistics(apiKey, part)
                            }
                        },
                    )

                    when (recognized) {
                        is RecognizeResult.Success -> {
                            container.battleStatisticsStore.publish(recognized.battle)
                            container.settingsRepository.setOverlayVisible(true)
                            OverlayService.start(applicationContext)
                            OverlayService.restoreAfterCapture(applicationContext)
                            CaptureEvents.emit(CaptureEvents.Result.Success)
                        }
                        is RecognizeResult.AccessDenied -> notifyAccessDenied(recognized.message)
                        is RecognizeResult.Failed -> notifyAccessDenied(recognized.message)
                    }
                }
            } catch (exception: TimeoutCancellationException) {
                notifyAccessDenied(STATISTICS_FAILED_MESSAGE)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                val denied = CaptureAccessGuard.classifyError(exception)
                if (denied != null) {
                    notifyAccessDenied(denied.message)
                } else {
                    notifyAccessDenied(
                        (exception as? HttpException)
                            ?.let(HttpErrorMessages::fromHttpException)
                            ?: exception.message
                            ?: STATISTICS_FAILED_MESSAGE,
                    )
                }
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun recognizeWithRetries(
        session: ScreenCaptureSession,
        upload: suspend (ByteArray) -> BattleStatisticsDto,
    ): RecognizeResult {
        var lastErrorMessage: String? = null

        for (attemptDelayMs in CAPTURE_ATTEMPT_DELAYS_MS) {
            delay(attemptDelayMs)

            val screenshot = try {
                session.captureJpeg()
            } catch (_: TimeoutCancellationException) {
                lastErrorMessage = STATISTICS_FAILED_MESSAGE
                continue
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                lastErrorMessage = STATISTICS_FAILED_MESSAGE
                continue
            }

            try {
                return RecognizeResult.Success(upload(screenshot))
            } catch (_: TimeoutCancellationException) {
                lastErrorMessage = STATISTICS_FAILED_MESSAGE
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                val httpException = exception as? HttpException
                if (httpException?.code() == 429) {
                    return RecognizeResult.AccessDenied(
                        HttpErrorMessages.fromHttpException(httpException)
                            ?: AppAlertNotifier.QUOTA_EXHAUSTED_MESSAGE,
                    )
                }
                lastErrorMessage = httpException
                    ?.let(HttpErrorMessages::fromHttpException)
                    ?: exception.message
                    ?: STATISTICS_FAILED_MESSAGE
            }
        }

        return RecognizeResult.Failed(lastErrorMessage ?: STATISTICS_FAILED_MESSAGE)
    }

    private suspend fun notifyAccessDenied(message: String) {
        OverlayService.showAccessDenied(applicationContext, message)
        OverlayService.restoreAfterCapture(applicationContext)
        CaptureEvents.emit(CaptureEvents.Result.Error(message))
    }

    private fun startAsForeground() {
        val channelId = "xvm_capture"
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(channelId, "Capture", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private sealed interface RecognizeResult {
        data class Success(val battle: BattleStatisticsDto) : RecognizeResult
        data class AccessDenied(val message: String) : RecognizeResult
        data class Failed(val message: String) : RecognizeResult
    }

    companion object {
        private const val NOTIFICATION_ID = 1002
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        private const val STATISTICS_FAILED_MESSAGE = "Не удалось получить статистику"
        private val STATISTICS_REQUEST_TIMEOUT = 30.seconds
        private val CAPTURE_ATTEMPT_DELAYS_MS = listOf(1_000L, 1_500L, 2_000L)
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, CaptureForegroundService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
