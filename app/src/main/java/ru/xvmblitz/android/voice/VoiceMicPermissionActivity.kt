package ru.xvmblitz.android.voice

import android.Manifest
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import ru.xvmblitz.android.XvmBlitzApp

class VoiceMicPermissionActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        XvmBlitzApp.instance.container.voiceRuntimeService.onMicrophonePermissionResult(granted)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    companion object {
        private const val REQUEST_CODE = 4201

        fun start(context: Context) {
            val intent = Intent(context, VoiceMicPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
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
                    REQUEST_CODE,
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
