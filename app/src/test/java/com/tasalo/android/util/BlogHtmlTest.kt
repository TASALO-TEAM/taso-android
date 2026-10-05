package com.tasalo.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BlogHtmlTest {
    @Test
    fun html_table_becomes_a_markdown_table() {
        val html = "<table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td><b>2</b></td></tr></table>"
        assertEquals("| A | B |\n| --- | --- |\n| 1 | **2** |", BlogText.clean(html))
    }

    @Test
    fun html_blockquote_becomes_a_markdown_quote() {
        assertEquals("> Hola\n>\n> Mundo", BlogText.clean("<blockquote><p>Hola</p><p>Mundo</p></blockquote>"))
    }

    @Test
    fun html_lists_are_converted() {
        assertEquals("1. uno\n2. dos", BlogText.clean("<ol><li>uno</li><li>dos</li></ol>"))
        assertEquals("- a\n- b", BlogText.clean("<ul><li>a</li><li>b</li></ul>"))
    }

    @Test
    fun html_pre_becomes_a_code_block_and_keeps_its_text() {
        assertEquals("```\na < b\n```", BlogText.clean("<pre><code>a &lt; b</code></pre>"))
    }

    @Test
    fun youtube_iframe_becomes_a_link_to_the_video() {
        val html = """<iframe src="https://www.youtube.com/embed/abc123" width="560"></iframe>"""
        assertEquals("[▶ Ver en youtube.com](https://www.youtube.com/watch?v=abc123)", BlogText.clean(html))
    }

    @Test
    fun scripts_and_styles_are_removed_with_their_content() {
        assertEquals("Hola mundo", BlogText.clean("Hola<script>alert(1)</script> mundo"))
        assertEquals("Texto", BlogText.clean("<style>p { color: red }</style>Texto"))
    }

    @Test
    fun image_keeps_its_description_and_protocol_relative_addresses_get_https() {
        assertEquals(
            "![Foto](https://img.test/a.png)",
            BlogText.clean("""<img src="//img.test/a.png" alt="Foto">"""),
        )
    }

    @Test
    fun strikethrough_and_inline_code() {
        assertEquals("~~x~~ y `z`", BlogText.clean("<del>x</del> y <code>z</code>"))
    }

    @Test
    fun unknown_tags_are_left_as_text() {
        assertEquals("ver <nuevo> aquí", BlogText.clean("ver <nuevo> aquí"))
        assertEquals("ver <nuevo> & aquí", BlogText.clean("ver <nuevo> &amp; aquí"))
    }

    @Test
    fun summary_ignores_table_rows() {
        val markdown = "Antes\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\nDespués"
        assertEquals("Antes Después", BlogText.summary(markdown))
    }
}
