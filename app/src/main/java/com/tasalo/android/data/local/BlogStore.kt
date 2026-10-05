package com.tasalo.android.data.local

import com.tasalo.android.data.parse.BlogParser
import com.tasalo.android.diag.DiagnosticLog
import com.tasalo.android.domain.BlogCache
import com.tasalo.android.domain.BlogPost
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Últimos posts del blog guardados en un archivo propio (no en el DataStore del caché: los cuerpos pesan
 * bastante y ese archivo se lee entero cada vez que cambia algo). Escritura atómica: tmp + renombrar.
 */
class BlogStore(private val dir: File) {

    private fun fileFor(account: String) = File(dir, "blog_${account.filter { it.isLetterOrDigit() || it == '-' || it == '.' }}.json")

    suspend fun load(account: String): BlogCache? = withContext(Dispatchers.IO) {
        try {
            val file = fileFor(account)
            if (file.exists()) BlogParser.decode(file.readText()) else null
        } catch (e: Exception) {
            DiagnosticLog.w("Blog", "no se pudo leer el blog guardado", e)
            null
        }
    }

    suspend fun save(account: String, posts: List<BlogPost>, at: Instant) = withContext(Dispatchers.IO) {
        try {
            val file = fileFor(account)
            val tmp = File(dir, file.name + ".tmp")
            tmp.writeText(BlogParser.encode(posts, at))
            if (!tmp.renameTo(file)) {
                file.writeText(BlogParser.encode(posts, at))
                tmp.delete()
            }
        } catch (e: Exception) {
            DiagnosticLog.w("Blog", "no se pudo guardar el blog", e)
        }
    }
}
