package com.tasalo.android.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tasalo.android.MainActivity
import com.tasalo.android.R
import com.tasalo.android.domain.AlertNotice

/** Notificacion local de alertas nuevas del bot. Sin Firebase ni servicios de Google: la crea el RefreshWorker. */
object AlertNotifier {
    const val CHANNEL_ID = "alertas_bot"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Alertas del bot", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Avisos cuando el bot de TASALO publica una alerta nueva."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Hay permiso (Android 13+) y las notificaciones de la app no estan bloqueadas en el sistema. */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** true si se mostro (el ultimo id avisado solo se guarda entonces, para reintentar si no hubo permiso). */
    @SuppressLint("MissingPermission") // canNotify() comprueba el permiso justo antes de notificar.
    fun show(context: Context, notice: AlertNotice): Boolean {
        if (!canNotify(context)) return false
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_ALERTS, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(notice.title)
            .setContentText(notice.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }

    /** Pantalla del sistema para silenciar el canal o cambiar su sonido e importancia. */
    fun openSystemSettings(context: Context) {
        ensureChannel(context)
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
