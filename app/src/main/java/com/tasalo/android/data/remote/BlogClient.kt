package com.tasalo.android.data.remote

import com.tasalo.android.data.parse.BlogParser
import com.tasalo.android.diag.DiagnosticLog
import com.tasalo.android.domain.BlogPost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Lee los últimos posts de una cuenta de Hive directamente de un nodo público (JSON-RPC), sin endpoint propio.
 * Si un nodo falla o responde algo inválido se prueba el siguiente.
 */
class BlogClient(
    private val client: OkHttpClient,
    private val nodes: List<String> = DEFAULT_NODES,
    private val limit: Int = 10,
) {
    /** Los posts de `account` (más nuevos primero) o null si ningún nodo respondió bien. Una lista vacía es una respuesta válida. */
    suspend fun fetch(account: String): List<BlogPost>? = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "bridge.get_account_posts")
            put(
                "params",
                buildJsonObject {
                    put("sort", "posts")
                    put("account", account)
                    put("limit", limit)
                },
            )
            put("id", 1)
        }.toString()

        for (node in nodes) {
            try {
                val request = Request.Builder()
                    .url(node)
                    .header("User-Agent", "TASALO-Android")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                val text = client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()
                    } else {
                        DiagnosticLog.w("Blog", "$node respondió ${response.code}")
                        null
                    }
                }
                val posts = text?.let { BlogParser.fromHive(it) }
                if (posts != null) return@withContext posts
                if (text != null) DiagnosticLog.w("Blog", "$node devolvió una respuesta que no es válida")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagnosticLog.w("Blog", "no se pudo consultar $node", e)
            }
        }
        null
    }

    companion object {
        /** Nodos públicos de Hive, por orden de preferencia. */
        val DEFAULT_NODES = listOf(
            "https://api.hive.blog",
            "https://api.deathwing.me",
            "https://hive-api.arcange.eu",
        )
    }
}
