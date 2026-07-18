package ru.xvmblitz.android.capture

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import ru.xvmblitz.android.XvmBlitzApp
import ru.xvmblitz.android.overlay.OverlayService
import ru.xvmblitz.android.util.AppAlertNotifier

class CaptureRequestActivity : ComponentActivity() {
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            CaptureForegroundService.start(this, result.resultCode, result.data!!)
        } else {
            OverlayService.restoreAfterCapture(this)
            Toast.makeText(this, "Захват экрана отменён", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val apiKey = XvmBlitzApp.instance.container.authRepository.getApiKeyOrNull()
        if (apiKey.isNullOrBlank()) {
            OverlayService.showAccessDenied(
                this,
                AppAlertNotifier.DEFAULT_API_KEY_MESSAGE,
            )
            OverlayService.restoreAfterCapture(this)
            finish()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            OverlayService.restoreAfterCapture(this)
            Toast.makeText(
                this,
                "Нужно разрешение «Поверх других окон»",
                Toast.LENGTH_LONG,
            ).show()
            finish()
            return
        }

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    companion object {
        private const val REQUEST_CODE_START_CAPTURE = 4101

        fun start(context: Context) {
            val intent = Intent(context, CaptureRequestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                context.startActivity(intent)
                return
            }

            val options = ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                }
            }.toBundle()

            runCatching {
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    REQUEST_CODE_START_CAPTURE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    options,
                )
                pendingIntent.send(context, 0, null, null, null, null, options)
            }.recoverCatching {
                context.startActivity(intent, options)
            }.getOrElse {
                context.startActivity(intent)
            }
        }
    }
}
