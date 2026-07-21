package ru.xvmblitz.android.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) {
            return
        }
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmIntent)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, "Обновление установлено", Toast.LENGTH_LONG).show()
            }
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                Toast.makeText(
                    context,
                    "Несовместимая подпись пакета. Удалите старое приложение и установите APK заново.",
                    Toast.LENGTH_LONG,
                ).show()
            }
            else -> {
                val details = message?.takeIf { it.isNotBlank() }
                Toast.makeText(
                    context,
                    details ?: "Не удалось установить обновление (код $status)",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "ru.xvmblitz.android.update.INSTALL_STATUS"
    }
}
