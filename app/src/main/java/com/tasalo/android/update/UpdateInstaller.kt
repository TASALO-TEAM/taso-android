package com.tasalo.android.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.tasalo.android.diag.DiagnosticLog
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Etapas visibles de la actualización dentro de la app. */
sealed interface UpdatePhase {
    data object Idle : UpdatePhase
    data class Downloading(val percent: Int) : UpdatePhase
    data object Verifying : UpdatePhase
    data object Installing : UpdatePhase
    /** Falta el permiso "Instalar apps desconocidas" para TASALO. */
    data object NeedsPermission : UpdatePhase
    data class Failed(val reason: String) : UpdatePhase
}

/** Estado global de la actualización: el receptor del instalador (otro componente) también lo escribe. */
object UpdateFlow {
    val phase = MutableStateFlow<UpdatePhase>(UpdatePhase.Idle)
}

/** Mensaje cuando la verificación del sistema (Play Protect, Auto Blocker de Samsung...) rechaza la instalación. */
private const val VERIFICATION_TEXT =
    "Android rechazó la verificación de la instalación. En Samsung suele deberse a Play Protect o al Auto Blocker " +
        "(Ajustes › Seguridad y privacidad). Revísalos e inténtalo de nuevo, o usa «Descargar con el navegador»."

object Sha256 {
    fun hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Huellas SHA-256 de los certificados de firma de un paquete. */
object CertInfo {
    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(":") { "%02X".format(it) }

    fun fingerprints(info: PackageInfo): Set<String> {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return signatures.orEmpty().map { sha256(it.toByteArray()) }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    /** Huella del certificado con el que está firmada esta instalación (la que se publica en cada Release). */
    fun installedFingerprint(context: Context): String? = runCatching {
        fingerprints(context.packageManager.getPackageInfo(context.packageName, signatureFlags())).firstOrNull()
    }.getOrNull()

    fun archiveFlags(): Int = signatureFlags()
}

/** Descarga el APK dentro de la app, con progreso y solo desde las Releases de nuestro repositorio. */
class UpdateDownloader(private val client: OkHttpClient) {

    suspend fun download(url: String, dest: File, expectedBytes: Long?, onProgress: (Int) -> Unit) =
        withContext(Dispatchers.IO) {
            require(isAllowedUrl(url)) { "URL de descarga no permitida" }
            dest.parentFile?.mkdirs()
            dest.delete()
            val request = Request.Builder().url(url).header("User-Agent", "TASALO-Android").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("GitHub respondió ${response.code}")
                val body = response.body ?: error("Respuesta vacía")
                val total = body.contentLength().takeIf { it > 0 } ?: expectedBytes ?: -1L
                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var done = 0L
                        var last = -1
                        while (true) {
                            ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            done += n
                            if (total > 0) {
                                val percent = (done * 100 / total).toInt().coerceIn(0, 100)
                                if (percent != last) {
                                    last = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }
            }
            if (expectedBytes != null && dest.length() != expectedBytes) {
                dest.delete()
                error("La descarga quedó incompleta")
            }
        }

    companion object {
        private const val ALLOWED_PREFIX = "https://github.com/TASALO-TEAM/taso-android/"

        fun isAllowedUrl(url: String): Boolean = url.startsWith(ALLOWED_PREFIX)
    }
}

/** Comprobaciones antes de instalar. Android vuelve a validar la firma al instalar; esto da un error claro. */
object ApkVerifier {
    /** Devuelve null si todo está bien, o el motivo del rechazo. */
    fun verify(context: Context, file: File, expectedSha256: String?): String? {
        if (expectedSha256 != null && !Sha256.hex(file).equals(expectedSha256, ignoreCase = true)) {
            return "La descarga está dañada (la huella no coincide). Inténtalo de nuevo."
        }
        val pm = context.packageManager
        val flags = CertInfo.archiveFlags()
        val archive = pm.getPackageArchiveInfo(file.path, flags) ?: return "El archivo descargado no es una APK válida."
        if (archive.packageName != context.packageName) return "La APK descargada no es de TASALO."

        val installed = pm.getPackageInfo(context.packageName, flags)
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) {
            return "La APK descargada no es más nueva que la instalada."
        }
        val newCerts = CertInfo.fingerprints(archive)
        val oldCerts = CertInfo.fingerprints(installed)
        // Si el sistema no expone alguna firma, se deja que lo decida el instalador de Android.
        if (newCerts.isNotEmpty() && oldCerts.isNotEmpty() && newCerts.intersect(oldCerts).isEmpty()) {
            return "La firma de la APK no coincide con la de la app instalada."
        }
        return null
    }
}

object ApkInstaller {
    const val ACTION_RESULT = "com.tasalo.android.INSTALL_RESULT"
    const val EXTRA_APK_PATH = "com.tasalo.android.extra.APK_PATH"
    const val EXTRA_ATTEMPT = "com.tasalo.android.extra.ATTEMPT"

    private fun isXiaomiFamily(): Boolean {
        val maker = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return listOf("xiaomi", "redmi", "poco").any { maker.contains(it) || brand.contains(it) }
    }

    /**
     * Instala el APK con una sesión de PackageInstaller. En Android 12+ pide no mostrar confirmación
     * (se respeta cuando se cumplen las condiciones del sistema: la app se actualiza a sí misma, etc.);
     * en versiones anteriores Android muestra su diálogo de confirmación.
     */
    fun install(context: Context, apk: File, userAction: Boolean = false, attempt: Int = 0) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // En Xiaomi/Redmi/POCO (HyperOS/MIUI) la instalación sin confirmación se aborta con
            // "Permission denied" (reporte 0.3.0, Android 16): ahí se deja que Android muestre su confirmación.
            // Si el sistema rechazó la instalación silenciosa (p. ej. verificación en Samsung), el reintento
            // pasa userAction = true y Android muestra su confirmación normal.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (userAction) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                } else if (!isXiaomiFamily()) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("taso-android.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            // Intent explícito: obligatorio para PendingIntent mutables en Android 14+.
            val intent = Intent(context, InstallResultReceiver::class.java)
                .setAction(ACTION_RESULT)
                .putExtra(EXTRA_APK_PATH, apk.path)
                .putExtra(EXTRA_ATTEMPT, attempt)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
        DiagnosticLog.i("Update", "sesión de instalación $sessionId enviada (API ${Build.VERSION.SDK_INT}, intento ${attempt + 1}${if (userAction) ", con confirmación" else ""})")
    }
}

/** Recibe el resultado del instalador del sistema. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm == null) {
                    fail("Android no mostró la confirmación de instalación.", status, message)
                    return
                }
                try {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    DiagnosticLog.e("Update", "no se pudo abrir la confirmación del sistema", e)
                    fail("No se pudo abrir la confirmación de instalación.", status, message)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                DiagnosticLog.i("Update", "instalación completada")
                UpdateFlow.phase.value = UpdatePhase.Idle
            }
            else -> {
                val apk = intent.getStringExtra(ApkInstaller.EXTRA_APK_PATH)?.let(::File)
                val attempt = intent.getIntExtra(ApkInstaller.EXTRA_ATTEMPT, 0)
                val verification = status == PackageInstaller.STATUS_FAILURE_ABORTED &&
                    message.orEmpty().contains("VERIFICATION_FAILURE")
                if (verification && attempt == 0 && apk?.exists() == true) {
                    // Reporte 0.6.1 (Samsung, Android 16): la verificación del sistema rechazó la instalación
                    // silenciosa. Se reintenta una vez pidiendo la confirmación normal de Android.
                    DiagnosticLog.w("Update", "verificación del sistema rechazó la instalación; se reintenta con confirmación: ${message.orEmpty()}")
                    val pending = goAsync()
                    Thread {
                        try {
                            ApkInstaller.install(context.applicationContext, apk, userAction = true, attempt = 1)
                        } catch (e: Exception) {
                            DiagnosticLog.e("Update", "el reintento de instalación falló", e)
                            UpdateFlow.phase.value = UpdatePhase.Failed(VERIFICATION_TEXT)
                        } finally {
                            pending.finish()
                        }
                    }.start()
                } else if (verification) {
                    fail(VERIFICATION_TEXT, status, message)
                } else {
                    fail(friendly(status), status, message)
                }
            }
        }
    }

    private fun fail(reason: String, status: Int, message: String?) {
        DiagnosticLog.w("Update", "instalación falló (estado $status): ${message.orEmpty()}")
        UpdateFlow.phase.value = UpdatePhase.Failed(reason)
    }

    private fun friendly(status: Int): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED ->
            "Android canceló la instalación (en Xiaomi/Redmi puede ocurrir si se rechaza el aviso de seguridad). " +
                "Inténtalo de nuevo y pulsa Instalar, o usa «Descargar con el navegador»."
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "El sistema bloqueó la instalación (puede ser una restricción del fabricante)."
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "La APK entra en conflicto con la app instalada (firma o versión)."
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "La APK no es compatible con este dispositivo."
        PackageInstaller.STATUS_FAILURE_INVALID -> "La APK no es válida."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "No hay espacio suficiente para instalar."
        else -> "No se pudo instalar la actualización."
    }
}
