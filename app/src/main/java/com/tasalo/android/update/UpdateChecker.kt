package com.tasalo.android.update

import com.tasalo.android.diag.DiagnosticLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int =
        compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)

    companion object {
        private val pattern = Regex("""^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?""")

        /** "v0.2.0", "0.2.0-debug" y "1.3" son válidos; lo que no empiece por número, no. */
        fun parse(text: String): SemVer? {
            val m = pattern.find(text.trim()) ?: return null
            return SemVer(
                m.groupValues[1].toInt(),
                m.groupValues[2].ifEmpty { "0" }.toInt(),
                m.groupValues[3].ifEmpty { "0" }.toInt(),
            )
        }

        fun isNewer(latest: String, current: String): Boolean {
            val l = parse(latest) ?: return false
            val c = parse(current) ?: return false
            return l > c
        }
    }
}

data class UpdateInfo(
    val version: String,
    /** Notas de la versión en Markdown (cuerpo de la Release); null si solo se conoce la versión. */
    val notes: String?,
    val pageUrl: String,
    val apkUrl: String?,
    val apkBytes: Long?,
)

sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Available(val info: UpdateInfo) : UpdateResult
    data class Failed(val reason: String) : UpdateResult
}

/**
 * Consulta la última Release publicada en GitHub. No pide permisos nuevos ni instala nada:
 * el botón "Actualizar" abre la descarga del APK y Android pide la confirmación habitual.
 */
class UpdateChecker(
    private val client: OkHttpClient,
    private val apiBase: String = "https://api.github.com",
    private val webBase: String = "https://github.com",
    private val repo: String = "TASALO-TEAM/taso-android",
) {
    suspend fun check(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        val info = try {
            fromApi()
        } catch (e: Exception) {
            DiagnosticLog.w("Update", "API de GitHub falló", e)
            null
        } ?: try {
            // La API sin autenticar tiene un límite por IP (varias personas detrás de una misma IP lo agotan).
            fromRedirect()
        } catch (e: Exception) {
            DiagnosticLog.w("Update", "consulta alternativa falló", e)
            null
        } ?: return@withContext UpdateResult.Failed("No se pudo consultar GitHub")

        if (SemVer.isNewer(info.version, currentVersion)) UpdateResult.Available(info) else UpdateResult.UpToDate
    }

    private fun fromApi(): UpdateInfo? {
        val request = Request.Builder()
            .url("$apiBase/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "TASALO-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                DiagnosticLog.w("Update", "GitHub API respondió ${response.code}")
                return null
            }
            return parseRelease(response.body?.string().orEmpty(), webBase, repo)
        }
    }

    private fun fromRedirect(): UpdateInfo? {
        val noFollow = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        val request = Request.Builder()
            .url("$webBase/$repo/releases/latest")
            .header("User-Agent", "TASALO-Android")
            .build()
        noFollow.newCall(request).execute().use { response ->
            val location = response.header("Location") ?: return null
            val tag = location.substringAfter("/tag/", "").substringBefore('?').trim()
            if (tag.isEmpty()) return null
            return UpdateInfo(
                version = tag.removePrefix("v"),
                notes = null,
                pageUrl = "$webBase/$repo/releases/tag/$tag",
                apkUrl = "$webBase/$repo/releases/download/$tag/taso-android-$tag.apk",
                apkBytes = null,
            )
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parseRelease(text: String, webBase: String, repo: String): UpdateInfo? {
            val root = try {
                json.parseToJsonElement(text) as? JsonObject
            } catch (e: Exception) {
                null
            } ?: return null
            val tag = (root["tag_name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (tag.isEmpty()) return null
            val apk = (root["assets"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.firstOrNull { (it["name"] as? JsonPrimitive)?.contentOrNull?.endsWith(".apk", true) == true }
            return UpdateInfo(
                version = tag.removePrefix("v"),
                notes = (root["body"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() },
                pageUrl = (root["html_url"] as? JsonPrimitive)?.contentOrNull ?: "$webBase/$repo/releases/tag/$tag",
                apkUrl = (apk?.get("browser_download_url") as? JsonPrimitive)?.contentOrNull,
                apkBytes = (apk?.get("size") as? JsonPrimitive)?.longOrNull,
            )
        }
    }
}
