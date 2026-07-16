package ru.xvmblitz.android.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import ru.xvmblitz.android.XvmBlitzApp

class CaptureRequestActivity : ComponentActivity() {
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            CaptureForegroundService.start(this, result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Захват экрана отменён", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!XvmBlitzApp.instance.container.authRepository.isAuthorized) {
            Toast.makeText(this, "Сначала введите API ключ", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Нужно разрешение «Поверх других окон»", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, CaptureRequestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
        }
    }
}
