package ru.xvmblitz.android.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import ru.xvmblitz.android.R
import ru.xvmblitz.android.ui.MainActivity

object AppAlertNotifier {
    private const val CHANNEL_ID = "xvm_alerts"
    private const val NOTIFICATION_ID = 2001

    fun showAuthRequired(context: Context, message: String = DEFAULT_AUTH_MESSAGE) {
        ensureChannel(context)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_AUTH, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alerts_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    const val DEFAULT_AUTH_MESSAGE = "Необходимо войти через Lesta OpenID"
    const val QUOTA_EXHAUSTED_MESSAGE = "Квота запросов превышена. Дождитесь обновления периода или войдите снова через Lesta OpenID"
    const val REQUEST_DENIED_MESSAGE = "Не удалось выполнить запрос"

    fun fallbackMessageForStatus(statusCode: Int): String {
        return when (statusCode) {
            401, 403 -> DEFAULT_AUTH_MESSAGE
            400 -> "Некорректный запрос"
            402, 429 -> QUOTA_EXHAUSTED_MESSAGE
            else -> REQUEST_DENIED_MESSAGE
        }
    }
}
