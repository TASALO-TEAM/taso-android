package com.tasalo.android.update

import com.tasalo.android.diag.DiagnosticLog
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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
    /** Archivo `.apk.sha256` publicado junto al APK (Releases antiguas no lo tienen). */
    val sha256Url: String? = null,
)

/** Una Release publicada, para la pestaña "Actualizaciones" de las notificaciones. */
data class ReleaseInfo(
    val version: String,
    val title: String,
    val notes: String?,
    val publishedAt: Instant?,
    val pageUrl: String,
)

sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Available(val info: UpdateInfo) : UpdateResult
    data class Failed(val reason: String) : UpdateResult
}

/**
 * Consulta las Releases publicadas en GitHub. El botón "Actualizar" descarga el APK dentro de la app
 * (ver `UpdateInstaller`); aquí solo se averigua qué hay y dónde.
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

    /** Texto crudo de las últimas Releases (para guardarlo en caché) o null si falla. */
    suspend fun fetchReleasesJson(limit: Int = 10): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$apiBase/repos/$repo/releases?per_page=$limit")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "TASALO-Android")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    DiagnosticLog.w("Update", "lista de Releases respondió ${response.code}")
                    null
                } else {
                    response.body?.string()
                }
            }
        } catch (e: Exception) {
            DiagnosticLog.w("Update", "lista de Releases falló", e)
            null
        }
    }

    /** Descarga el `.sha256` y devuelve la huella en minúsculas, o null si no se pudo. */
    suspend fun fetchSha256(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", "TASALO-Android").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else parseSha256(response.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            DiagnosticLog.w("Update", "no se pudo leer el .sha256", e)
            null
        }
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
            val apk = "$webBase/$repo/releases/download/$tag/taso-android-$tag.apk"
            return UpdateInfo(
                version = tag.removePrefix("v"),
                notes = null,
                pageUrl = "$webBase/$repo/releases/tag/$tag",
                apkUrl = apk,
                apkBytes = null,
                sha256Url = "$apk.sha256",
            )
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        private val sha256Regex = Regex("""\b[0-9a-fA-F]{64}\b""")

        /** Acepta el formato de `sha256sum` ("<hash>  archivo") o un hash suelto. */
        fun parseSha256(text: String): String? = sha256Regex.find(text)?.value?.lowercase()

        private fun obj(text: String): JsonObject? = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            null
        }

        private fun str(o: JsonObject, key: String): String? = (o[key] as? JsonPrimitive)?.contentOrNull

        fun parseRelease(text: String, webBase: String, repo: String): UpdateInfo? {
            val root = obj(text) ?: return null
            return releaseFrom(root, webBase, repo)
        }

        private fun releaseFrom(root: JsonObject, webBase: String, repo: String): UpdateInfo? {
            val tag = str(root, "tag_name")?.trim().orEmpty()
            if (tag.isEmpty()) return null
            val assets = (root["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            val apk = assets.firstOrNull { str(it, "name")?.endsWith(".apk", true) == true }
            val sha = assets.firstOrNull { str(it, "name")?.endsWith(".apk.sha256", true) == true }
            return UpdateInfo(
                version = tag.removePrefix("v"),
                notes = str(root, "body")?.trim()?.takeIf { it.isNotEmpty() },
                pageUrl = str(root, "html_url") ?: "$webBase/$repo/releases/tag/$tag",
                apkUrl = apk?.let { str(it, "browser_download_url") },
                apkBytes = (apk?.get("size") as? JsonPrimitive)?.longOrNull,
                sha256Url = sha?.let { str(it, "browser_download_url") },
            )
        }

        /** Lista de Releases (sin borradores ni pre-lanzamientos), de la más nueva a la más vieja. */
        fun parseReleases(text: String, webBase: String = "https://github.com", repo: String = "TASALO-TEAM/taso-android"): List<ReleaseInfo> {
            val array = try {
                json.parseToJsonElement(text) as? JsonArray
            } catch (e: Exception) {
                null
            } ?: return emptyList()
            return array.mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                if ((o["draft"] as? JsonPrimitive)?.booleanOrNull == true) return@mapNotNull null
                if ((o["prerelease"] as? JsonPrimitive)?.booleanOrNull == true) return@mapNotNull null
                val info = releaseFrom(o, webBase, repo) ?: return@mapNotNull null
                ReleaseInfo(
                    version = info.version,
                    title = str(o, "name")?.takeIf { it.isNotBlank() } ?: "Versión ${info.version}",
                    notes = info.notes,
                    publishedAt = str(o, "published_at")?.let { runCatching { Instant.parse(it) }.getOrNull() },
                    pageUrl = info.pageUrl,
                )
            }
        }
    }
}
