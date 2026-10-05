package com.tasalo.android.util

/**
 * Limpieza del texto de los posts de Hive. Los posts nuevos son Markdown estándar, pero los antiguos
 * pueden traer HTML (`<p>`, `<img>`, `<a>`...): se convierte a Markdown o se quita, sin tocar los bloques de código.
 */
object BlogText {
    private val fence = Regex("```.*?```", RegexOption.DOT_MATCHES_ALL)
    private val comment = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val autolink = Regex("""<(https?://[^>\s]+)>""")

    private val dotAll = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val linkedImg = Regex("""<a\b[^>]*>\s*(<img\b[^>]*>)\s*</a>""", dotAll)
    private val img = Regex("""<img\b[^>]*?\bsrc\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
    private val link = Regex("""<a\b[^>]*?\bhref\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""", dotAll)
    private val heading = Regex("""<h([1-6])\b[^>]*>(.*?)</h\1>""", dotAll)
    private val bold = Regex("""<(?:b|strong)\b[^>]*>(.*?)</(?:b|strong)>""", dotAll)
    private val italic = Regex("""<(?:i|em)\b[^>]*>(.*?)</(?:i|em)>""", dotAll)
    private val lineBreak = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val rule = Regex("""<hr\s*/?>""", RegexOption.IGNORE_CASE)
    private val listItem = Regex("""<li\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val blockEnd = Regex("""</(?:p|div|li|ul|ol|blockquote|center|table|tr)>""", RegexOption.IGNORE_CASE)
    private val anyTag = Regex("""</?[a-zA-Z][^>\n]*>""")
    private val manyBlankLines = Regex("""\n{3,}""")
    private val entity = Regex("""&(#\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);""")

    /** Devuelve el cuerpo como Markdown estándar: HTML convertido o quitado; el código no se toca. */
    fun clean(body: String): String {
        val text = body.replace("\r\n", "\n")
        val out = StringBuilder()
        var last = 0
        for (m in fence.findAll(text)) {
            out.append(cleanHtml(text.substring(last, m.range.first)))
            out.append(m.value)
            last = m.range.last + 1
        }
        out.append(cleanHtml(text.substring(last)))
        return out.toString().trim()
    }

    private fun cleanHtml(chunk: String): String {
        var t = comment.replace(chunk, "")
        t = autolink.replace(t) { "[${it.groupValues[1]}](${it.groupValues[1]})" }
        t = linkedImg.replace(t) { it.groupValues[1] }
        t = img.replace(t) { "\n\n![](${it.groupValues[1]})\n\n" }
        t = link.replace(t) {
            val label = it.groupValues[2].trim().ifEmpty { it.groupValues[1] }
            "[$label](${it.groupValues[1]})"
        }
        t = heading.replace(t) { "\n\n" + "#".repeat(it.groupValues[1].toInt()) + " " + it.groupValues[2].trim() + "\n\n" }
        t = bold.replace(t) { "**${it.groupValues[1]}**" }
        t = italic.replace(t) { "*${it.groupValues[1]}*" }
        t = lineBreak.replace(t, "\n")
        t = rule.replace(t, "\n\n---\n\n")
        t = listItem.replace(t, "\n- ")
        t = blockEnd.replace(t, "\n\n")
        t = anyTag.replace(t, "")
        t = decodeEntities(t)
        return manyBlankLines.replace(t, "\n\n")
    }

    private fun decodeEntities(text: String): String = entity.replace(text) { m ->
        when (val e = m.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "nbsp" -> " "
            "hellip" -> "…"
            "ndash" -> "–"
            "mdash" -> "—"
            else -> when {
                e.startsWith("#x") || e.startsWith("#X") -> e.drop(2).toIntOrNull(16)?.let(::codePoint) ?: m.value
                e.startsWith("#") -> e.drop(1).toIntOrNull()?.let(::codePoint) ?: m.value
                else -> m.value
            }
        }
    }

    private fun codePoint(n: Int): String? = if (Character.isValidCodePoint(n)) String(Character.toChars(n)) else null

    private val summaryHeading = Regex("""(?m)^\s{0,3}#{1,6}\s.*$""")
    private val summaryImage = Regex("""!\[[^\]]*]\([^)]*\)""")
    private val summaryLink = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val summaryMarker = Regex("""(?m)^\s{0,3}(?:>|[-*+]|\d+[.)])\s+""")
    private val summaryRule = Regex("""(?m)^\s*([-*_])(\s*\1){2,}\s*$""")
    private val summaryEmphasis = Regex("""[*`~]+""")
    private val summaryUrl = Regex("""https?://\S+""")
    private val whitespace = Regex("""\s+""")

    /** Resumen de hasta `max` caracteres: sin títulos, imágenes, enlaces ni marcas de Markdown; corta en una palabra. */
    fun summary(markdown: String, max: Int = 140): String {
        var t = fence.replace(markdown, " ")
        t = summaryHeading.replace(t, " ")
        t = summaryImage.replace(t, " ")
        t = summaryLink.replace(t) { it.groupValues[1] }
        t = summaryRule.replace(t, " ")
        t = summaryMarker.replace(t, "")
        t = summaryEmphasis.replace(t, "")
        t = summaryUrl.replace(t, "")
        t = whitespace.replace(t, " ").trim()
        if (t.length <= max) return t
        val cut = t.substring(0, max)
        val space = cut.lastIndexOf(' ')
        val base = if (space > max / 2) cut.substring(0, space) else cut
        return base.trimEnd(' ', ',', '.', ';', ':', '-') + "…"
    }

    private val firstImageRegex = Regex("""!\[[^\]]*]\((https://[^)\s]+)""")

    /** Primera imagen https del cuerpo ya limpio, para usarla de portada si el post no declara ninguna. */
    fun firstImage(markdown: String): String? = firstImageRegex.find(markdown)?.groupValues?.get(1)
}
