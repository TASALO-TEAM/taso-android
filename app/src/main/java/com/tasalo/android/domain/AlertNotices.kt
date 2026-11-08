package com.tasalo.android.domain

/** Lo que muestra la notificacion local: titulo, texto y la alerta mas nueva que cubre (para no repetirla). */
data class AlertNotice(val title: String, val text: String, val newestId: Long)

/**
 * Decide que alertas del bot se avisan en el telefono y con que texto. Puro (sin Android) para probarlo en la JVM.
 * Sin duplicados: solo cuentan las alertas mas nuevas que la ultima ya avisada y que la ultima ya vista en la app.
 */
object AlertNotices {
    private const val PREVIEW_MAX = 140

    fun pending(messages: List<AppMessage>, lastNotifiedId: Long, lastSeenId: Long): List<AppMessage> {
        val floor = maxOf(lastNotifiedId, lastSeenId)
        return messages.filter { it.id > floor }.sortedByDescending { it.id }
    }

    /** null = nada que avisar. Una alerta: su titulo y un adelanto; varias: un resumen con el titulo de la mas reciente. */
    fun notice(pending: List<AppMessage>): AlertNotice? {
        val newest = pending.maxByOrNull { it.id } ?: return null
        return if (pending.size == 1) {
            AlertNotice(newest.title, preview(newest.body), newest.id)
        } else {
            AlertNotice("${pending.size} alertas nuevas", newest.title, newest.id)
        }
    }

    /** Quita las marcas de Markdown (el cuerpo puede venir en el formato de Telegram), junta lineas y recorta. */
    fun preview(body: String, max: Int = PREVIEW_MAX): String {
        val plain = body
            .replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "\$1")
            .replace(Regex("""[*_`~]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return if (plain.length <= max) plain else plain.take(max - 1).trimEnd() + "\u2026"
    }
}
