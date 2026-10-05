package com.tasalo.android.util

data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val url: String? = null,
    val strike: Boolean = false,
)

/** Alineación de una columna de tabla. */
enum class MdAlign { START, CENTER, END }

sealed interface MdBlock {
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    /** `checked` solo en listas de tareas (`- [x]`): true = hecha, false = pendiente. */
    data class Bullet(val indent: Int, val spans: List<MdSpan>, val checked: Boolean? = null) : MdBlock
    data class Numbered(val number: Int, val spans: List<MdSpan>, val indent: Int = 0) : MdBlock
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class Code(val text: String) : MdBlock
    /** Imagen en su propia línea (`![alt](https://...)`); solo se admiten direcciones https. */
    data class Image(val alt: String, val url: String, val link: String? = null) : MdBlock
    /** Cita (`> texto`): puede contener cualquier otro bloque, también otras citas. */
    data class Quote(val blocks: List<MdBlock>) : MdBlock
    /** Tabla: cabecera, filas (todas con tantas celdas como la cabecera) y alineación de cada columna. */
    data class Table(
        val header: List<List<MdSpan>>,
        val rows: List<List<List<MdSpan>>>,
        val aligns: List<MdAlign>,
    ) : MdBlock
    data object Rule : MdBlock
}

/**
 * Dialecto del texto:
 * - STANDARD: Markdown habitual (notas de versión, posts del blog): `**negrita**`, `*cursiva*`, `# títulos`.
 * - TELEGRAM: Markdown legacy de Telegram, que es lo que escribe el admin con /msapp en el bot:
 *   `*negrita*`, `_cursiva_`, `` `código` ``, `[texto](url)`. No hay títulos y los saltos de línea cuentan.
 * - FULL: Markdown completo de los posts del blog (tablas, citas, listas de tareas, enlaces sueltos, tachado...);
 *   ver `BlogMarkdown`.
 */
enum class MdDialect { STANDARD, TELEGRAM, FULL }

/**
 * Markdown mínimo para las notas de versión y los mensajes del equipo: títulos, listas, negrita,
 * cursiva, `código`, enlaces, bloques de código y separadores. Sin dependencias; lo no soportado se muestra como texto.
 */
object MarkdownParser {
    private val heading = Regex("""^(#{1,6})\s+(.*)$""")
    private val bullet = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val telegramBullet = Regex("""^(\s*)[-*+•]\s+(.*)$""")
    private val numbered = Regex("""^\s*(\d+)[.)]\s+(.*)$""")
    private val rule = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    // Imágenes: en su propia línea son un bloque; dentro de un texto se sustituyen por su descripción.
    private val imageLine = Regex("""^\s*!\[([^\]]*)]\((\S+?)(?:\s+"[^"]*")?\)\s*$""")
    private val linkedImageLine = Regex("""^\s*\[!\[([^\]]*)]\((\S+?)\)]\((\S+?)\)\s*$""")
    private val inlineImage = Regex("""!\[([^\]]*)]\((\S+?)(?:\s+"[^"]*")?\)""")
    private val token = Regex("""`([^`]+)`|\*\*([^*]+)\*\*|__([^_]+)__|\*([^*\s][^*]*)\*|\[([^\]]+)]\(([^)\s]+)\)""")

    // Telegram: *negrita*, _cursiva_ (no dentro de una palabra: snake_case se queda tal cual), `código`, enlaces.
    private val telegramToken = Regex(
        """`([^`]+)`|\*([^*\n]+)\*|(?<![\p{L}\p{N}])_([^_\n]+)_(?![\p{L}\p{N}])|\[([^\]]+)]\(([^)\s]+)\)""",
    )

    fun parse(source: String, dialect: MdDialect = MdDialect.STANDARD): List<MdBlock> {
        if (dialect == MdDialect.FULL) return BlogMarkdown.parse(source)
        val telegram = dialect == MdDialect.TELEGRAM
        val bulletRegex = if (telegram) telegramBullet else bullet
        val blocks = mutableListOf<MdBlock>()
        val paragraph = mutableListOf<String>()
        var inCode = false
        val code = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MdBlock.Paragraph(inline(paragraph.joinToString(" "), dialect = dialect))
                paragraph.clear()
            }
        }

        for (raw in source.replace("\r\n", "\n").lines()) {
            var line = raw.trimEnd()
            if (line.trimStart().startsWith("```")) {
                if (inCode) {
                    blocks += MdBlock.Code(code.joinToString("\n"))
                    code.clear()
                } else {
                    flushParagraph()
                }
                inCode = !inCode
                continue
            }
            if (inCode) {
                code += raw
                continue
            }
            if (line.trimStart().startsWith("<!--")) continue
            if (!telegram) {
                val image = imageLine.matchEntire(line) ?: linkedImageLine.matchEntire(line)
                if (image != null) {
                    flushParagraph()
                    val url = image.groupValues[2].trim()
                    if (url.lowercase().startsWith("https://")) blocks += MdBlock.Image(image.groupValues[1], url)
                    continue
                }
                line = inlineImage.replace(line) { it.groupValues[1] }
            }
            when {
                line.isBlank() -> flushParagraph()
                !telegram && rule.matches(line) -> { flushParagraph(); blocks += MdBlock.Rule }
                !telegram && heading.matches(line) -> {
                    flushParagraph()
                    val m = heading.find(line)!!
                    blocks += MdBlock.Heading(m.groupValues[1].length, inline(m.groupValues[2].trim()))
                }
                bulletRegex.matches(line) -> {
                    flushParagraph()
                    val m = bulletRegex.find(line)!!
                    blocks += MdBlock.Bullet(m.groupValues[1].length / 2, inline(m.groupValues[2], dialect = dialect))
                }
                numbered.matches(line) -> {
                    flushParagraph()
                    val m = numbered.find(line)!!
                    blocks += MdBlock.Numbered(m.groupValues[1].toInt(), inline(m.groupValues[2], dialect = dialect))
                }
                // En Telegram cada línea es un renglón propio; en Markdown estándar las líneas seguidas forman un párrafo.
                telegram -> blocks += MdBlock.Paragraph(inline(line.trim(), dialect = dialect))
                else -> paragraph += line.trim()
            }
        }
        if (inCode && code.isNotEmpty()) blocks += MdBlock.Code(code.joinToString("\n"))
        flushParagraph()
        return blocks
    }

    fun isSafeUrl(url: String): Boolean {
        val u = url.trim().lowercase()
        return u.startsWith("https://") || u.startsWith("http://") || u.startsWith("tg://") || u.startsWith("tg:")
    }

    fun inline(
        text: String,
        bold: Boolean = false,
        italic: Boolean = false,
        url: String? = null,
        dialect: MdDialect = MdDialect.STANDARD,
    ): List<MdSpan> {
        val telegram = dialect == MdDialect.TELEGRAM
        val spans = mutableListOf<MdSpan>()
        var pos = 0
        for (m in (if (telegram) telegramToken else token).findAll(text)) {
            if (m.range.first > pos) spans += MdSpan(text.substring(pos, m.range.first), bold, italic, url = url)
            val g = m.groupValues
            if (telegram) {
                when {
                    g[1].isNotEmpty() -> spans += MdSpan(g[1], bold, italic, code = true, url = url)
                    g[2].isNotEmpty() -> spans += inline(g[2], true, italic, url, dialect)
                    g[3].isNotEmpty() -> spans += inline(g[3], bold, true, url, dialect)
                    // Enlaces con esquema no permitido (javascript:, intent:, file:…) se muestran como texto plano.
                    g[4].isNotEmpty() -> spans += inline(g[4], bold, italic, if (isSafeUrl(g[5])) g[5] else url, dialect)
                }
            } else {
                when {
                    g[1].isNotEmpty() -> spans += MdSpan(g[1], bold, italic, code = true, url = url)
                    g[2].isNotEmpty() -> spans += inline(g[2], true, italic, url)
                    g[3].isNotEmpty() -> spans += inline(g[3], true, italic, url)
                    g[4].isNotEmpty() -> spans += inline(g[4], bold, true, url)
                    g[5].isNotEmpty() -> spans += inline(g[5], bold, italic, if (isSafeUrl(g[6])) g[6] else url)
                }
            }
            pos = m.range.last + 1
        }
        if (pos < text.length) spans += MdSpan(text.substring(pos), bold, italic, url = url)
        return spans
    }
}
