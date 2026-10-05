package com.tasalo.android.domain

import java.time.Instant

/** Cuentas de Hive que muestra la pestaña Blog, en el orden de sus pestañas (la primera es la que se abre). */
val BLOG_ACCOUNTS = listOf("tasalo", "ersusoficial")

fun blogAccountLabel(handle: String): String = "@$handle"

/** Un post del blog (Hive). `body` ya es Markdown estándar, sin HTML. */
data class BlogPost(
    val author: String,
    val permlink: String,
    val title: String,
    val body: String,
    /** Resumen de ~140 caracteres sacado del cuerpo, sin Markdown ni imágenes. */
    val summary: String,
    val createdAt: Instant?,
    /** Portada (solo https) o null si el post no tiene imágenes. */
    val cover: String?,
) {
    /** Dirección del post completo en Ecency. */
    val url: String get() = "https://ecency.com/@$author/$permlink"
}

/** Los últimos posts guardados en el móvil y cuándo se descargaron. */
data class BlogCache(val posts: List<BlogPost>, val fetchedAt: Instant)
