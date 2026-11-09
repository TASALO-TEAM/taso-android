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

/** Notificaciones locales (alertas del bot y pedidos de la tienda). Sin Firebase ni servicios de Google: las crea el RefreshWorker. */
object AlertNotifier {
    const val CHANNEL_ID = "alertas_bot"
    const val ORDERS_CHANNEL_ID = "pedidos_tienda"
    private const val NOTIFICATION_ID = 1001
    private const val ORDERS_NOTIFICATION_ID = 1002

    /** Crea los dos canales (es idempotente). */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Alertas del bot", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisos cuando el bot de TASALO publica una alerta nueva."
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(ORDERS_CHANNEL_ID, "Pedidos de la tienda", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisos del estado de tus pedidos en la Tienda y, si eres del personal, de los pedidos nuevos."
            },
        )
    }

    /** Hay permiso (Android 13+) y las notificaciones de la app no estan bloqueadas en el sistema. */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** true si se mostro (el ultimo id avisado solo se guarda entonces, para reintentar si no hubo permiso). */
    fun show(context: Context, notice: AlertNotice): Boolean =
        post(context, CHANNEL_ID, NOTIFICATION_ID, 0, MainActivity.EXTRA_OPEN_ALERTS, notice, hideOnLockScreen = false)

    /**
     * Igual, para un pedido de la tienda. El texto del personal lleva el nombre del cliente: en la pantalla bloqueada
     * sale una version generica y el detalle se ve al desbloquear.
     */
    fun showOrder(context: Context, notice: AlertNotice): Boolean =
        post(context, ORDERS_CHANNEL_ID, ORDERS_NOTIFICATION_ID, 1, MainActivity.EXTRA_OPEN_TIENDA, notice, hideOnLockScreen = true)

    @SuppressLint("MissingPermission") // canNotify() comprueba el permiso justo antes de notificar.
    private fun post(
        context: Context,
        channelId: String,
        notificationId: Int,
        requestCode: Int,
        openExtra: String,
        notice: AlertNotice,
        hideOnLockScreen: Boolean,
    ): Boolean {
        if (!canNotify(context)) return false
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java)
            .putExtra(openExtra, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // requestCode distinto por tipo: con el mismo, FLAG_UPDATE_CURRENT pisaria los extras del toque del otro aviso.
        val tap = PendingIntent.getActivity(context, requestCode, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(notice.title)
            .setContentText(notice.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (hideOnLockScreen) {
            val generic = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_alert)
                .setContentTitle("Pedidos de la tienda")
                .setContentText("Tienes novedades en los pedidos")
                .build()
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(generic)
        }
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        return true
    }

    /** Pantalla del sistema para silenciar un canal o cambiar su sonido e importancia. */
    fun openSystemSettings(context: Context, channelId: String = CHANNEL_ID) {
        ensureChannel(context)
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
