package com.tasalo.android.util

import java.net.URI

/**
 * Limpieza del texto de los posts de Hive. Los posts nuevos de Ecency son Markdown, pero los de otras apps
 * (y los antiguos) pueden traer HTML: se convierte a Markdown (tablas, citas, listas, enlaces, imágenes, código,
 * vídeos embebidos como enlace) o se quita (scripts, estilos, etiquetas de maquetación). Los bloques de código
 * Markdown no se tocan, y una `<etiqueta>` que no sea HTML conocido se deja como texto.
 */
object BlogText {
    private val fence = Regex("```.*?```", RegexOption.DOT_MATCHES_ALL)
    private val ci = RegexOption.IGNORE_CASE
    private val dotAll = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    private const val TAGS =
        "p|div|span|a|b|i|u|s|em|strong|br|hr|h[1-6]|ul|ol|li|table|thead|tbody|tfoot|tr|th|td|caption|colgroup|col|" +
            "blockquote|pre|code|center|img|sup|sub|del|ins|strike|details|summary|iframe|video|audio|source|embed|" +
            "font|figure|figcaption|section|article|small|big|mark|cite|dl|dt|dd|abbr|kbd|var|samp|tt|picture|nav|" +
            "header|footer|main|aside|label|noscript|script|style|head|body|html|meta|link|title|svg|math|canvas|" +
            "object|param|track|wbr"

    // Marcas privadas (no pueden aparecer en un post) para guardar bloques mientras se limpia el resto.
    private const val STASH = "\uE000"
    private const val QUOTE_OPEN = "\uE001"
    private const val QUOTE_CLOSE = "\uE002"

    private val comment = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val scriptStyle = Regex("""<(script|style|head|noscript|svg|canvas)\b[^>]*>.*?</\1>""", dotAll)
    private val pre = Regex("""<pre\b[^>]*>(.*?)</pre>""", dotAll)
    private val table = Regex("""<table\b[^>]*>(.*?)</table>""", dotAll)
    private val tableRow = Regex("""<tr\b[^>]*>(.*?)</tr>""", dotAll)
    private val tableCell = Regex("""<(?:th|td)\b[^>]*>(.*?)</(?:th|td)>""", dotAll)
    private val summaryTag = Regex("""<summary\b[^>]*>(.*?)</summary>""", dotAll)
    private val embed = Regex(
        """<(?:iframe|video|audio|source|embed)\b[^>]*?\bsrc\s*=\s*["']([^"']+)["'][^>]*>(?:\s*</(?:iframe|video|audio|embed)>)?""",
        dotAll,
    )
    private val autolink = Regex("""<(https?://[^>\s]+)>""")
    private val linkedImg = Regex("""<a\b[^>]*>\s*(<img\b[^>]*>)\s*</a>""", dotAll)
    private val img = Regex("""<img\b[^>]*?\bsrc\s*=\s*["']([^"']+)["'][^>]*>""", ci)
    private val altAttr = Regex("""\balt\s*=\s*["']([^"']*)["']""", ci)
    private val link = Regex("""<a\b[^>]*?\bhref\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""", dotAll)
    private val heading = Regex("""<h([1-6])\b[^>]*>(.*?)</h\1>""", dotAll)
    private val bold = Regex("""<(?:b|strong)\b[^>]*>(.*?)</(?:b|strong)>""", dotAll)
    private val italic = Regex("""<(?:i|em)\b[^>]*>(.*?)</(?:i|em)>""", dotAll)
    private val strike = Regex("""<(?:del|s|strike)\b[^>]*>(.*?)</(?:del|s|strike)>""", dotAll)
    private val inlineCode = Regex("""<code\b[^>]*>(.*?)</code>""", dotAll)
    private val blockquote = Regex("""<blockquote\b[^>]*>""", ci)
    private val blockquoteEnd = Regex("""</blockquote>""", ci)
    private val orderedList = Regex("""<ol\b[^>]*>(.*?)</ol>""", dotAll)
    private val lineBreak = Regex("""<br\s*/?>""", ci)
    private val rule = Regex("""<hr\b[^>]*>""", ci)
    private val listItem = Regex("""<li\b[^>]*>""", ci)
    private val listItemEnd = Regex("""</li>""", ci)
    private val blockEnd = Regex("""</(?:p|div|ul|ol|center|table|tr|dd|dt|figure|figcaption|section|article|details)>""", ci)
    private val anyTag = Regex("""</?(?:$TAGS)\b[^>\n]*>""", ci)
    private val manyBlankLines = Regex("""\n{3,}""")
    private val whitespace = Regex("""\s+""")
    private val entity = Regex("""&(#\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);""")
    private val stashed = Regex("$STASH(\\d+)$STASH")
    private val youtubeEmbed = Regex("""^https://(?:www\.)?youtube(?:-nocookie)?\.com/embed/([\w-]+)""")
    private val innermostQuote = Regex("$QUOTE_OPEN((?:(?!$QUOTE_OPEN).)*?)$QUOTE_CLOSE", RegexOption.DOT_MATCHES_ALL)

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
        if ('<' !in chunk && '&' !in chunk) return chunk

        val code = mutableListOf<String>()
        var t = comment.replace(chunk, "")
        t = scriptStyle.replace(t, "")
        t = pre.replace(t) { m ->
            code += "```\n" + decodeEntities(anyTag.replace(m.groupValues[1], "")).trim('\n') + "\n```"
            "\n\n$STASH${code.size - 1}$STASH\n\n"
        }
        t = table.replace(t) { tableToMarkdown(it.groupValues[1]) }
        t = summaryTag.replace(t) { "\n\n**${stripTags(it.groupValues[1])}**\n\n" }
        t = embed.replace(t) { embedLink(it.groupValues[1]) }
        t = autolink.replace(t) { "[${it.groupValues[1]}](${it.groupValues[1]})" }
        t = linkedImg.replace(t) { it.groupValues[1] }
        t = img.replace(t) { "\n\n" + imageMarkdown(it) + "\n\n" }
        t = link.replace(t) { linkMarkdown(it) }
        t = heading.replace(t) { "\n\n" + "#".repeat(it.groupValues[1].toInt()) + " " + it.groupValues[2].trim() + "\n\n" }
        t = inlineFormatting(t)
        t = blockquote.replace(t, "\n\n$QUOTE_OPEN")
        t = blockquoteEnd.replace(t, "$QUOTE_CLOSE\n\n")
        t = orderedList.replace(t) { m ->
            var n = 0
            listItem.replace(m.groupValues[1]) { "\n${++n}. " }
        }
        t = listItem.replace(t, "\n- ")
        t = listItemEnd.replace(t, "")
        t = lineBreak.replace(t, "\n")
        t = rule.replace(t, "\n\n---\n\n")
        t = blockEnd.replace(t, "\n\n")
        t = anyTag.replace(t, "")
        t = decodeEntities(t)
        t = applyQuotes(t)
        t = stashed.replace(t) { code[it.groupValues[1].toInt()] }
        return manyBlankLines.replace(t, "\n\n")
    }

    private fun inlineFormatting(text: String): String {
        var t = bold.replace(text) { "**${it.groupValues[1]}**" }
        t = italic.replace(t) { "*${it.groupValues[1]}*" }
        t = strike.replace(t) { "~~${it.groupValues[1]}~~" }
        return inlineCode.replace(t) { "`${it.groupValues[1]}`" }
    }

    private fun stripTags(text: String): String = decodeEntities(anyTag.replace(text, "")).trim()

    private fun absolute(src: String): String? {
        val s = src.trim()
        return when {
            s.startsWith("https://", ignoreCase = true) -> s
            s.startsWith("http://", ignoreCase = true) -> "https://" + s.substring(7)
            s.startsWith("//") -> "https:$s"
            else -> null
        }
    }

    private fun imageMarkdown(m: MatchResult): String {
        val src = absolute(m.groupValues[1]) ?: return ""
        val alt = altAttr.find(m.value)?.groupValues?.get(1).orEmpty().replace("]", "").replace("\n", " ")
        return "![$alt]($src)"
    }

    private fun linkMarkdown(m: MatchResult): String {
        val label = m.groupValues[2].trim().ifEmpty { m.groupValues[1] }
        return "[$label](${m.groupValues[1]})"
    }

    /** Vídeos y contenido embebido no se pueden mostrar dentro del post: se dejan como enlace al original. */
    private fun embedLink(source: String): String {
        val url = absolute(source) ?: return ""
        val watch = youtubeEmbed.find(url)?.let { "https://www.youtube.com/watch?v=${it.groupValues[1]}" } ?: url
        val host = runCatching { URI(watch).host }.getOrNull()?.removePrefix("www.") ?: "enlace"
        return "\n\n[▶ Ver en $host]($watch)\n\n"
    }

    private fun tableToMarkdown(html: String): String {
        val rows = tableRow.findAll(html)
            .map { row -> tableCell.findAll(row.groupValues[1]).map { cleanCell(it.groupValues[1]) }.toList() }
            .filter { it.isNotEmpty() }
            .toList()
        if (rows.isEmpty()) return ""
        val columns = rows.maxOf { it.size }
        fun line(cells: List<String>) = "| " + (0 until columns).joinToString(" | ") { cells.getOrElse(it) { "" } } + " |"
        return buildString {
            append("\n\n").append(line(rows[0])).append('\n')
            append("|").append(" --- |".repeat(columns))
            rows.drop(1).forEach { append('\n').append(line(it)) }
            append("\n\n")
        }
    }

    /** Una celda cabe en una sola línea: el formato en línea se conserva, el resto de etiquetas se quita. */
    private fun cleanCell(html: String): String {
        var t = lineBreak.replace(html, " ")
        t = linkedImg.replace(t) { it.groupValues[1] }
        t = img.replace(t) { imageMarkdown(it) }
        t = link.replace(t) { linkMarkdown(it) }
        t = inlineFormatting(t)
        t = blockEnd.replace(t, " ")
        t = anyTag.replace(t, "")
        return whitespace.replace(t, " ").trim().replace("|", "\\|")
    }

    private fun applyQuotes(text: String): String {
        var t = text
        repeat(8) {
            val next = innermostQuote.replace(t) { m ->
                val lines = m.groupValues[1].trim('\n', ' ').lines()
                "\n\n" + lines.joinToString("\n") { if (it.isBlank()) ">" else "> ${it.trimEnd()}" } + "\n\n"
            }
            if (next == t) return@repeat
            t = next
        }
        return t.replace(QUOTE_OPEN, "").replace(QUOTE_CLOSE, "")
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
    private val summaryTable = Regex("""(?m)^\s*\|.*$""")
    private val summaryImage = Regex("""!\[[^\]]*]\([^)]*\)""")
    private val summaryLink = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val summaryMarker = Regex("""(?m)^\s{0,3}(?:>|[-*+]|\d+[.)])\s+""")
    private val summaryRule = Regex("""(?m)^\s*([-*_])(\s*\1){2,}\s*$""")
    private val summaryEmphasis = Regex("""[*`~]+""")
    private val summaryUrl = Regex("""https?://\S+""")

    /** Resumen de hasta `max` caracteres: sin títulos, tablas, imágenes, enlaces ni marcas de Markdown; corta en una palabra. */
    fun summary(markdown: String, max: Int = 140): String {
        var t = fence.replace(markdown, " ")
        t = summaryHeading.replace(t, " ")
        t = summaryTable.replace(t, " ")
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
