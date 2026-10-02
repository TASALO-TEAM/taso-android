package com.tasalo.android.diag

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.pm.PackageInfoCompat
import java.time.Instant
import java.util.Locale

/** Arma el texto del reporte: datos técnicos del dispositivo + fallos + últimos eventos. */
object ReportBuilder {
    private const val MAX_CHARS = 40_000

    fun versionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"

    fun subject(context: Context): String =
        "TASALO Android ${versionName(context)} · reporte (${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE})"

    fun deviceInfo(context: Context): String {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val cfg = context.resources.configuration
        val dm = context.resources.displayMetrics
        val night = when (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> "oscuro"
            Configuration.UI_MODE_NIGHT_NO -> "claro"
            else -> "?"
        }
        return buildString {
            appendLine("App: ${info?.versionName ?: "?"} (código ${info?.let { PackageInfoCompat.getLongVersionCode(it) } ?: "?"})")
            appendLine("Paquete: ${context.packageName}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), parche ${Build.VERSION.SECURITY_PATCH}")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}")
            appendLine("Pantalla: ${dm.widthPixels}x${dm.heightPixels} px, ${dm.densityDpi} dpi, ancho mínimo ${cfg.smallestScreenWidthDp} dp")
            appendLine("Escala de fuente: ${cfg.fontScale}, tema del sistema: $night")
            appendLine("Idioma: ${Locale.getDefault()}")
            appendLine("Launcher: ${launcher(context)}")
        }
    }

    private fun launcher(context: Context): String = runCatching {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull() ?: "?"

    /** Motivos por los que el sistema terminó el proceso (incluye cierres nativos, ANR y falta de memoria). */
    fun exitReasons(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "(no disponible antes de Android 11)"
        return runCatching { exitReasonsApi30(context) }.getOrElse { "(error al leerlos)" }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun exitReasonsApi30(context: Context): String {
        val am = context.getSystemService(ActivityManager::class.java) ?: return "(sin ActivityManager)"
        val list = am.getHistoricalProcessExitReasons(context.packageName, 0, 12)
            .filterNot { isBenign(it.reason, it.importance) }
            .take(6)
        if (list.isEmpty()) return "(ninguno relevante)"
        return list.joinToString("\n") {
            "${Instant.ofEpochMilli(it.timestamp)} ${reasonName(it.reason)} importancia=${it.importance} ${it.description.orEmpty()}".trim()
        }
    }

    /** Salidas normales (el usuario cerró la app, actualización, el sistema liberó memoria de una app en caché). */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun isBenign(reason: Int, importance: Int): Boolean = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF,
        ApplicationExitInfo.REASON_USER_REQUESTED,
        ApplicationExitInfo.REASON_USER_STOPPED,
        15, 16 -> true // PACKAGE_STATE_CHANGE / PACKAGE_UPDATED (Android 14+)
        ApplicationExitInfo.REASON_LOW_MEMORY -> importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
        else -> false
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVO"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "POCA_MEMORIA"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "FALLO_INICIO"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESO_RECURSOS"
        ApplicationExitInfo.REASON_SIGNALED -> "SEÑAL"
        ApplicationExitInfo.REASON_EXIT_SELF -> "SALIDA_PROPIA"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "CERRADA_POR_USUARIO"
        ApplicationExitInfo.REASON_USER_STOPPED -> "DETENIDA_POR_USUARIO"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCIA_CAIDA"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "CAMBIO_PERMISOS"
        else -> "MOTIVO_$reason"
    }

    fun build(context: Context): String {
        val text = buildString {
            appendLine("Reporte de diagnóstico de TASALO Android")
            appendLine("(Puedes revisar y editar este texto antes de enviarlo. No contiene datos personales.)")
            appendLine()
            appendLine("=== Dispositivo ===")
            appendLine(deviceInfo(context))
            appendLine("=== Salidas recientes del proceso (sistema) ===")
            appendLine(exitReasons(context))
            appendLine()
            val crashes = DiagnosticLog.pendingCrashes()
            appendLine("=== Fallos detectados (${crashes.size}) ===")
            crashes.forEach { file ->
                appendLine("--- ${file.name} ---")
                appendLine(runCatching { file.readText() }.getOrDefault("(ilegible)"))
            }
            appendLine("=== Eventos recientes ===")
            appendLine(DiagnosticLog.tail(80))
        }
        return if (text.length > MAX_CHARS) text.take(MAX_CHARS) + "\n…(recortado)" else text
    }
}

object ReportSender {
    /**
     * Abre la app de correo del usuario con el reporte ya escrito para tasaloteam@gmail.com.
     * No requiere permisos ni credenciales: el usuario revisa y pulsa Enviar.
     * Devuelve false si no hay app de correo; en ese caso el texto queda copiado al portapapeles.
     */
    fun send(context: Context): Boolean {
        val body = ReportBuilder.build(context)
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(DiagnosticLog.REPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, ReportBuilder.subject(context))
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return try {
            context.startActivity(intent)
            DiagnosticLog.i("Reporte", "abierto el correo con el reporte")
            true
        } catch (e: ActivityNotFoundException) {
            DiagnosticLog.w("Reporte", "no hay app de correo", e)
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("Reporte TASALO", body))
            false
        }
    }
}
