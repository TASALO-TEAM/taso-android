package com.tasalo.android.util

data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val url: String? = null,
)

sealed interface MdBlock {
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class Bullet(val indent: Int, val spans: List<MdSpan>) : MdBlock
    data class Numbered(val number: Int, val spans: List<MdSpan>) : MdBlock
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class Code(val text: String) : MdBlock
    data object Rule : MdBlock
}

/**
 * Markdown mínimo para las notas de versión: títulos, listas, negrita, cursiva, `código`,
 * enlaces, bloques de código y separadores. Sin dependencias; lo no soportado se muestra como texto.
 */
object MarkdownParser {
    private val heading = Regex("""^(#{1,6})\s+(.*)$""")
    private val bullet = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val numbered = Regex("""^\s*(\d+)[.)]\s+(.*)$""")
    private val rule = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    private val token = Regex("""`([^`]+)`|\*\*([^*]+)\*\*|__([^_]+)__|\*([^*\s][^*]*)\*|\[([^\]]+)]\(([^)\s]+)\)""")

    fun parse(source: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val paragraph = mutableListOf<String>()
        var inCode = false
        val code = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MdBlock.Paragraph(inline(paragraph.joinToString(" ")))
                paragraph.clear()
            }
        }

        for (raw in source.replace("\r\n", "\n").lines()) {
            val line = raw.trimEnd()
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
            when {
                line.isBlank() -> flushParagraph()
                rule.matches(line) -> { flushParagraph(); blocks += MdBlock.Rule }
                heading.matches(line) -> {
                    flushParagraph()
                    val m = heading.find(line)!!
                    blocks += MdBlock.Heading(m.groupValues[1].length, inline(m.groupValues[2].trim()))
                }
                bullet.matches(line) -> {
                    flushParagraph()
                    val m = bullet.find(line)!!
                    blocks += MdBlock.Bullet(m.groupValues[1].length / 2, inline(m.groupValues[2]))
                }
                numbered.matches(line) -> {
                    flushParagraph()
                    val m = numbered.find(line)!!
                    blocks += MdBlock.Numbered(m.groupValues[1].toInt(), inline(m.groupValues[2]))
                }
                else -> paragraph += line.trim()
            }
        }
        if (inCode && code.isNotEmpty()) blocks += MdBlock.Code(code.joinToString("\n"))
        flushParagraph()
        return blocks
    }

    fun inline(text: String, bold: Boolean = false, italic: Boolean = false, url: String? = null): List<MdSpan> {
        val spans = mutableListOf<MdSpan>()
        var pos = 0
        for (m in token.findAll(text)) {
            if (m.range.first > pos) spans += MdSpan(text.substring(pos, m.range.first), bold, italic, url = url)
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> spans += MdSpan(g[1], bold, italic, code = true, url = url)
                g[2].isNotEmpty() -> spans += inline(g[2], true, italic, url)
                g[3].isNotEmpty() -> spans += inline(g[3], true, italic, url)
                g[4].isNotEmpty() -> spans += inline(g[4], bold, true, url)
                g[5].isNotEmpty() -> spans += inline(g[5], bold, italic, g[6])
            }
            pos = m.range.last + 1
        }
        if (pos < text.length) spans += MdSpan(text.substring(pos), bold, italic, url = url)
        return spans
    }
}
