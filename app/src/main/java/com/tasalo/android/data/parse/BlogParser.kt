package com.tasalo.android.data.parse

import com.tasalo.android.domain.BlogCache
import com.tasalo.android.domain.BlogPost
import com.tasalo.android.util.BlogText
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Posts del blog. Parseo tolerante, como `Parsers`: un JSON raro devuelve null o se salta ese post, nunca lanza.
 * Aquí también está el formato compacto con el que se guardan en el móvil (la respuesta de Hive pesa mucho más).
 */
object BlogParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun obj(text: String): JsonObject? =
        try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            null
        }

    private fun str(o: JsonObject, key: String): String? = (o[key] as? JsonPrimitive)?.contentOrNull

    private fun isHttps(url: String): Boolean = url.trim().lowercase().startsWith("https://")

    /**
     * Respuesta JSON-RPC de `bridge.get_account_posts` -> posts, los más nuevos primero.
     * Devuelve null si la respuesta no es válida (error del nodo, no es JSON...); lista vacía si la cuenta no tiene posts.
     */
    fun fromHive(text: String): List<BlogPost>? {
        val root = obj(text) ?: return null
        val result = root["result"] as? JsonArray ?: return null
        return result.mapNotNull { (it as? JsonObject)?.let(::post) }
            .sortedByDescending { it.createdAt ?: Instant.EPOCH }
    }

    private fun post(o: JsonObject): BlogPost? {
        val author = str(o, "author")?.trim().orEmpty()
        val permlink = str(o, "permlink")?.trim().orEmpty()
        val title = str(o, "title")?.trim().orEmpty()
        if (author.isEmpty() || permlink.isEmpty() || title.isEmpty()) return null

        val body = BlogText.clean(str(o, "body").orEmpty())
        val cover = declaredImages(o).firstOrNull(::isHttps) ?: BlogText.firstImage(body)
        return BlogPost(
            author = author,
            permlink = permlink,
            title = title,
            body = body,
            summary = BlogText.summary(body),
            createdAt = Parsers.instant(str(o, "created")),
            cover = cover?.trim(),
        )
    }

    /** `json_metadata` llega como objeto o como texto con JSON, según el nodo; `image` es la lista de portadas. */
    private fun declaredImages(o: JsonObject): List<String> {
        val meta = when (val m = o["json_metadata"]) {
            is JsonObject -> m
            is JsonPrimitive -> m.contentOrNull?.let(::obj)
            else -> null
        }
        return (meta?.get("image") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
    }

    // ---------- Caché local ----------

    fun encode(posts: List<BlogPost>, fetchedAt: Instant): String = buildJsonObject {
        put("v", 1)
        put("at", fetchedAt.toEpochMilli())
        put(
            "posts",
            buildJsonArray {
                posts.forEach { p ->
                    add(
                        buildJsonObject {
                            put("author", p.author)
                            put("permlink", p.permlink)
                            put("title", p.title)
                            put("body", p.body)
                            put("summary", p.summary)
                            put("created", p.createdAt?.toString())
                            put("cover", p.cover)
                        },
                    )
                }
            },
        )
    }.toString()

    /** Lo guardado por `encode`; null si el archivo está corrupto o es de otro formato. */
    fun decode(text: String): BlogCache? {
        val root = obj(text) ?: return null
        val at = (root["at"] as? JsonPrimitive)?.longOrNull ?: return null
        val array = root["posts"] as? JsonArray ?: return null
        val posts = array.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val author = str(o, "author") ?: return@mapNotNull null
            val permlink = str(o, "permlink") ?: return@mapNotNull null
            val title = str(o, "title") ?: return@mapNotNull null
            BlogPost(
                author = author,
                permlink = permlink,
                title = title,
                body = str(o, "body").orEmpty(),
                summary = str(o, "summary").orEmpty(),
                createdAt = Parsers.instant(str(o, "created")),
                cover = str(o, "cover")?.takeIf(::isHttps),
            )
        }
        return BlogCache(posts, Instant.ofEpochMilli(at))
    }
}
