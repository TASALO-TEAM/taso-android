package com.tasalo.android.diag

import android.content.Context
import java.io.File
import java.time.Instant

/**
 * Registro de diagnóstico local (beta). Guarda un log circular pequeño y, si la app se cierra
 * por una excepción no controlada, un archivo de "crash" con la traza y los últimos eventos.
 *
 * Nada sale del dispositivo por sí solo: el usuario decide si enviar el reporte por correo
 * (ver [ReportSender]). No se guarda ningún dato personal, solo eventos técnicos.
 *
 * Es seguro llamarlo antes de `init` (no hace nada), lo que permite usarlo en tests JVM.
 */
object DiagnosticLog {
    const val REPORT_EMAIL = "tasaloteam@gmail.com"

    private const val MAX_LOG_BYTES = 64 * 1024
    private const val MAX_CRASHES = 5
    private const val CRASH_TRACE_CHARS = 8_000

    @Volatile private var dir: File? = null
    private val lock = Any()

    fun init(context: Context) {
        val base = File(context.filesDir, "diag")
        File(base, "crashes").mkdirs()
        dir = base
    }

    fun i(tag: String, message: String) = write('I', tag, message, null)

    fun w(tag: String, message: String, error: Throwable? = null) = write('W', tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) = write('E', tag, message, error)

    private fun write(level: Char, tag: String, message: String, error: Throwable?) {
        val base = dir ?: return
        try {
            val suffix = error?.let { " | ${it.javaClass.simpleName}: ${it.message}" }.orEmpty()
            val line = "${Instant.now()} $level/$tag: $message$suffix\n"
            synchronized(lock) {
                val file = File(base, "events.log")
                if (file.length() > MAX_LOG_BYTES) {
                    // Log circular: conserva la segunda mitad, alineada a una línea completa.
                    val kept = file.readText().takeLast(MAX_LOG_BYTES / 2).substringAfter('\n')
                    file.writeText(kept)
                }
                file.appendText(line)
            }
        } catch (_: Throwable) {
            // El log nunca debe provocar un fallo.
        }
    }

    /** Últimas `lines` líneas del log de eventos. */
    fun tail(lines: Int = 60): String {
        val base = dir ?: return ""
        return try {
            synchronized(lock) {
                File(base, "events.log").takeIf { it.exists() }?.readLines()?.takeLast(lines)?.joinToString("\n").orEmpty()
            }
        } catch (_: Throwable) {
            ""
        }
    }

    /** Captura cierres por excepción no controlada (hilo principal, corrutinas, widgets). */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                writeCrash(thread, error)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun writeCrash(thread: Thread, error: Throwable) {
        val base = dir ?: return
        val folder = File(base, "crashes").apply { mkdirs() }
        val trace = error.stackTraceToString().take(CRASH_TRACE_CHARS)
        val text = buildString {
            appendLine("Fecha: ${Instant.now()}")
            appendLine("Hilo: ${thread.name}")
            appendLine()
            appendLine(trace)
            appendLine()
            appendLine("--- últimos eventos ---")
            appendLine(tail(40))
        }
        File(folder, "crash-${System.currentTimeMillis()}.txt").writeText(text)
        folder.listFiles()?.sortedBy { it.name }?.dropLast(MAX_CRASHES)?.forEach { it.delete() }
    }

    fun pendingCrashes(): List<File> =
        File(dir ?: return emptyList(), "crashes").listFiles()?.sortedBy { it.name }.orEmpty()

    fun hasPendingCrashes(): Boolean = pendingCrashes().isNotEmpty()

    fun clearCrashes() {
        pendingCrashes().forEach { it.delete() }
    }
}
