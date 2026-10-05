package com.tasalo.android.util

/**
 * Markdown completo para los posts del blog (Hive): títulos (también subrayados con === o ---), listas anidadas y
 * de tareas, citas, tablas, bloques de código, imágenes (con enlace y con las medidas de Hive), enlaces (también
 * sueltos, con paréntesis y con título), negrita, cursiva, tachado, menciones @usuario y saltos de línea
 * (en Hive cada salto de línea es un renglón). El HTML ya se convirtió antes con `BlogText.clean`.
 */
object BlogMarkdown {
    private val heading = Regex("""^\s{0,3}(#{1,6})\s+(.*?)(?:\s+#+)?\s*$""")
    private val rule = Regex("""^\s{0,3}([-*_])(\s*\1){2,}\s*$""")
    private val quoteLine = Regex("""^\s{0,3}>\s?(.*)$""")
    private val bulletLine = Regex("""^(\s*)[-*+•]\s+(.*)$""")
    private val numberedLine = Regex("""^(\s*)(\d{1,9})[.)]\s+(.*)$""")
    private val taskItem = Regex("""^\[([ xX])]\s+(.*)$""")
    private val setextOne = Regex("""^\s{0,3}=+\s*$""")
    private val setextTwo = Regex("""^\s{0,3}-{2,}\s*$""")
    private val tableSeparator = Regex("""^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?\s*$""")
    private val bareImage = Regex("""^\s*(https?://\S+?\.(?:png|jpe?g|gif|webp|avif)(?:\?\S*)?)\s*$""", RegexOption.IGNORE_CASE)

    // Grupos: 1 texto, 2 imagen, 3 enlace (imagen con enlace) | 4 texto, 5 imagen (imagen sola).
    // Tras la dirección se admite un título "..." o las medidas de Hive (=300x200).
    private val imageAtom = Regex(
        """\[!\[([^\]]*)]\(\s*<?([^\s)>]+)>?[^)]*\)]\(\s*<?([^\s)>]+)>?[^)]*\)|!\[([^\]]*)]\(\s*<?([^\s)>]+)>?[^)]*\)""",
    )

    private class PendingItem(
        val bullet: Boolean,
        val level: Int,
        val number: Int,
        val checked: Boolean?,
        val text: StringBuilder,
    )

    fun parse(source: String): List<MdBlock> =
        parseLines(source.replace("\r\n", "\n").replace('\r', '\n').lines())

    private fun parseLines(lines: List<String>): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val paragraph = mutableListOf<String>()
        val indents = ArrayList<Int>()
        var item: PendingItem? = null

        fun flushItem() {
            val current = item ?: return
            val spans = inline(flattenImages(current.text.toString()))
            blocks += if (current.bullet) {
                MdBlock.Bullet(current.level, spans, current.checked)
            } else {
                MdBlock.Numbered(current.number, spans, current.level)
            }
            item = null
        }

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MdBlock.Paragraph(inline(flattenImages(paragraph.joinToString("\n"))))
                paragraph.clear()
            }
        }

        fun flush() {
            flushItem()
            flushParagraph()
        }

        /** Cualquier bloque que no sea un elemento de lista cierra la lista (y su sangría). */
        fun endList() {
            flush()
            indents.clear()
        }

        fun levelFor(spaces: Int): Int {
            while (indents.isNotEmpty() && spaces < indents.last()) indents.removeAt(indents.lastIndex)
            if (indents.isEmpty() || spaces > indents.last()) indents.add(spaces)
            return indents.size - 1
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trimEnd()
            val trimmed = line.trimStart()

            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                endList()
                val marker = trimmed.take(3)
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(marker)) {
                    code += lines[i]
                    i++
                }
                i++ // la línea de cierre; si falta, el código llega hasta el final
                if (code.isNotEmpty()) blocks += MdBlock.Code(code.joinToString("\n"))
                continue
            }
            if (line.isBlank()) {
                flush()
                i++
                continue
            }
            if (trimmed.startsWith("<!--")) {
                i++
                continue
            }
            if (item == null && paragraph.size == 1 && (setextOne.matches(line) || setextTwo.matches(line))) {
                val level = if (setextOne.matches(line)) 1 else 2
                blocks += MdBlock.Heading(level, inline(paragraph[0].trim()))
                paragraph.clear()
                i++
                continue
            }
            if (rule.matches(line)) {
                endList()
                blocks += MdBlock.Rule
                i++
                continue
            }
            val head = heading.find(line)
            if (head != null) {
                endList()
                blocks += MdBlock.Heading(head.groupValues[1].length, inline(head.groupValues[2].trim()))
                i++
                continue
            }
            if (isTableStart(lines, i)) {
                endList()
                i = parseTable(lines, i, blocks)
                continue
            }
            if (quoteLine.matches(line)) {
                endList()
                val inner = mutableListOf<String>()
                while (i < lines.size) {
                    val q = quoteLine.matchEntire(lines[i].trimEnd()) ?: break
                    inner += q.groupValues[1]
                    i++
                }
                blocks += MdBlock.Quote(parseLines(inner))
                continue
            }
            val images = imageBlocks(line)
            if (images != null) {
                endList()
                blocks += images
                i++
                continue
            }

            val bullet = bulletLine.matchEntire(line)
            val numbered = if (bullet == null) numberedLine.matchEntire(line) else null
            if (bullet != null || numbered != null) {
                flush()
                val spaces = indentWidth(bullet?.groupValues?.get(1) ?: numbered!!.groupValues[1])
                val level = levelFor(spaces)
                if (bullet != null) {
                    val task = taskItem.matchEntire(bullet.groupValues[2])
                    item = PendingItem(
                        bullet = true,
                        level = level,
                        number = 0,
                        checked = task?.let { it.groupValues[1] != " " },
                        text = StringBuilder(task?.groupValues?.get(2) ?: bullet.groupValues[2]),
                    )
                } else {
                    item = PendingItem(
                        bullet = false,
                        level = level,
                        number = numbered!!.groupValues[2].toIntOrNull() ?: 1,
                        checked = null,
                        text = StringBuilder(numbered.groupValues[3]),
                    )
                }
                i++
                continue
            }

            val current = item
            if (current != null && paragraph.isEmpty() && indentWidth(line.takeWhile { it == ' ' || it == '\t' }) >= 2) {
                // Renglón sangrado debajo de un elemento: sigue siendo el mismo elemento.
                current.text.append('\n').append(trimmed)
            } else {
                if (paragraph.isEmpty()) {
                    flushItem()
                    indents.clear()
                }
                paragraph += trimmed.removeSuffix("\\")
            }
            i++
        }
        flush()
        return blocks
    }

    /** Ancho de la sangría: un tabulador cuenta como 4 espacios. */
    private fun indentWidth(whitespace: String): Int = whitespace.fold(0) { total, c -> total + if (c == '\t') 4 else 1 }

    // ---------- Tablas ----------

    private fun isTableStart(lines: List<String>, i: Int): Boolean {
        if (i + 1 >= lines.size) return false
        val head = lines[i]
        val separator = lines[i + 1]
        if ('|' !in head || '-' !in separator || !tableSeparator.matches(separator)) return false
        return splitRow(head).size == splitRow(separator).size
    }

    private fun splitRow(line: String): List<String> {
        val text = line.trim().removePrefix("|")
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length && text[i + 1] == '|' -> {
                    cell.append('|')
                    i += 2
                }
                c == '|' -> {
                    cells += cell.toString().trim()
                    cell.clear()
                    i++
                }
                else -> {
                    cell.append(c)
                    i++
                }
            }
        }
        // Lo que queda tras el último "|" es una celda solo si no está vacío (la barra final es opcional).
        if (cell.isNotBlank()) cells += cell.toString().trim()
        return cells
    }

    /** Lee la tabla que empieza en `start` y devuelve la línea siguiente a su última fila. */
    private fun parseTable(lines: List<String>, start: Int, blocks: MutableList<MdBlock>): Int {
        val header = splitRow(lines[start])
        val aligns = splitRow(lines[start + 1]).map {
            when {
                it.startsWith(":") && it.endsWith(":") -> MdAlign.CENTER
                it.endsWith(":") -> MdAlign.END
                else -> MdAlign.START
            }
        }
        val columns = header.size
        val rows = mutableListOf<List<List<MdSpan>>>()
        var j = start + 2
        while (j < lines.size && lines[j].isNotBlank() && '|' in lines[j]) {
            val cells = splitRow(lines[j])
            rows += (0 until columns).map { inline(flattenImages(cells.getOrElse(it) { "" })) }
            j++
        }
        blocks += MdBlock.Table(header.map { inline(flattenImages(it)) }, rows, aligns)
        return j
    }

    // ---------- Imágenes ----------

    private fun secure(url: String): String? {
        val u = url.trim()
        return when {
            u.startsWith("https://", ignoreCase = true) -> u
            u.startsWith("http://", ignoreCase = true) -> "https://" + u.substring(7)
            u.startsWith("//") -> "https:$u"
            else -> null
        }
    }

    /** Si el renglón son solo imágenes (o una dirección de imagen suelta), un bloque por imagen; si no, null. */
    private fun imageBlocks(line: String): List<MdBlock>? {
        bareImage.matchEntire(line)?.let { m ->
            return listOfNotNull(secure(m.groupValues[1])?.let { MdBlock.Image("", it) })
        }
        if ("![" !in line) return null
        val atoms = imageAtom.findAll(line).toList()
        if (atoms.isEmpty() || imageAtom.replace(line, "").isNotBlank()) return null
        return atoms.mapNotNull { m ->
            val linked = m.groups[2] != null
            val alt = if (linked) m.groupValues[1] else m.groupValues[4]
            val url = secure(if (linked) m.groupValues[2] else m.groupValues[5]) ?: return@mapNotNull null
            val link = if (linked) m.groupValues[3].takeIf { MarkdownParser.isSafeUrl(it) } else null
            MdBlock.Image(alt.trim(), url, link)
        }
    }

    /** Imágenes dentro de un texto: se muestran como su descripción (o como enlace si llevan uno). */
    private fun flattenImages(text: String): String {
        if ("![" !in text) return text
        return imageAtom.replace(text) { m ->
            if (m.groups[2] != null) {
                "[${m.groupValues[1].ifBlank { "🖼" }}](${m.groupValues[3]})"
            } else {
                m.groupValues[4]
            }
        }
    }

    // ---------- Texto en línea ----------

    private val token = Regex(
        """\\([\\`*_{}\[\]()#+\-.!~>|<])""" + // 1: carácter escapado
            """|`([^`\n]+)`""" + // 2: código
            """|\*\*\*(.+?)\*\*\*""" + // 3: negrita y cursiva
            """|\*\*(.+?)\*\*""" + // 4: negrita
            """|(?<![\p{L}\p{N}])__(.+?)__(?![\p{L}\p{N}])""" + // 5: negrita con guiones bajos
            """|~~(.+?)~~""" + // 6: tachado
            """|\*([^*\s](?:[^*\n]*[^*\s])?)\*""" + // 7: cursiva
            """|(?<![\p{L}\p{N}])_([^_\s](?:[^_\n]*[^_\s])?)_(?![\p{L}\p{N}])""" + // 8: cursiva con guión bajo
            """|\[([^\]]+)]\(\s*<?((?:[^()\s>]|\([^()\s]*\))+)>?(?:\s+(?:"[^"]*"|'[^']*'))?\s*\)""" + // 9 texto, 10 enlace
            """|<(https?://[^>\s]+)>""" + // 11: <enlace>
            """|(https?://[^\s<>\[\]]+)""" + // 12: enlace suelto
            """|(?<![\p{L}\p{N}_@/.:-])@([a-z][a-z0-9-]{2,15}(?:\.[a-z][a-z0-9-]{2,15})*)""", // 13: mención
    )

    fun inline(
        text: String,
        bold: Boolean = false,
        italic: Boolean = false,
        strike: Boolean = false,
        url: String? = null,
    ): List<MdSpan> {
        val spans = mutableListOf<MdSpan>()
        var pos = 0

        fun plain(s: String) {
            if (s.isNotEmpty()) spans += MdSpan(s, bold, italic, url = url, strike = strike)
        }

        for (m in token.findAll(text)) {
            if (m.range.first < pos) continue
            plain(text.substring(pos, m.range.first))
            pos = m.range.last + 1
            val g = m.groups
            when {
                g[1] != null -> plain(g[1]!!.value)
                g[2] != null -> spans += MdSpan(g[2]!!.value, bold, italic, code = true, url = url, strike = strike)
                g[3] != null -> spans += inline(g[3]!!.value, true, true, strike, url)
                g[4] != null -> spans += inline(g[4]!!.value, true, italic, strike, url)
                g[5] != null -> spans += inline(g[5]!!.value, true, italic, strike, url)
                g[6] != null -> spans += inline(g[6]!!.value, bold, italic, true, url)
                g[7] != null -> spans += inline(g[7]!!.value, bold, true, strike, url)
                g[8] != null -> spans += inline(g[8]!!.value, bold, true, strike, url)
                g[9] != null -> {
                    // Enlaces con esquema no permitido (javascript:, intent:, file:…) se muestran como texto plano.
                    val target = g[10]!!.value
                    spans += inline(g[9]!!.value, bold, italic, strike, if (MarkdownParser.isSafeUrl(target)) target else url)
                }
                // Dentro del texto de un enlace no se anidan más enlaces.
                url != null -> plain(m.value)
                g[11] != null -> {
                    val target = g[11]!!.value
                    spans += MdSpan(target, bold, italic, url = target, strike = strike)
                }
                g[12] != null -> {
                    val target = trimUrl(g[12]!!.value)
                    spans += MdSpan(target, bold, italic, url = target, strike = strike)
                    pos = m.range.first + target.length // la puntuación final vuelve a ser texto
                }
                g[13] != null -> {
                    val user = g[13]!!.value
                    spans += MdSpan("@$user", bold, italic, url = "https://ecency.com/@$user", strike = strike)
                }
            }
        }
        if (pos < text.length) plain(text.substring(pos))
        return spans
    }

    /** Quita la puntuación que suele pegarse al final de un enlace suelto, y el ")" sin su "(". */
    private fun trimUrl(raw: String): String {
        var u = raw
        while (u.isNotEmpty()) {
            val last = u.last()
            u = when {
                last in ".,;:!?'\"*_~" -> u.dropLast(1)
                last == ')' && u.count { it == '(' } < u.count { it == ')' } -> u.dropLast(1)
                else -> return u
            }
        }
        return u
    }
}
