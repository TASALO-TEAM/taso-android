package com.tasalo.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlogMarkdownTest {
    private fun text(spans: List<MdSpan>) = spans.joinToString("") { it.text }

    // ---------- Tablas ----------

    @Test
    fun table_with_alignment() {
        val md = "| Moneda | Valor |\n|:--|--:|\n| USD | 400 |\n| EUR | 420 |"
        val table = BlogMarkdown.parse(md).single() as MdBlock.Table
        assertEquals(listOf("Moneda", "Valor"), table.header.map(::text))
        assertEquals(listOf(MdAlign.START, MdAlign.END), table.aligns)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("EUR", "420"), table.rows[1].map(::text))
    }

    @Test
    fun table_without_outer_pipes_and_with_missing_cells() {
        val md = "a | b | c\n--- | --- | ---\n1 | 2"
        val table = BlogMarkdown.parse(md).single() as MdBlock.Table
        assertEquals(3, table.header.size)
        assertEquals(listOf("1", "2", ""), table.rows.single().map(::text))
    }

    @Test
    fun table_cells_keep_inline_formatting() {
        val md = "| A |\n|---|\n| **x** |"
        val table = BlogMarkdown.parse(md).single() as MdBlock.Table
        assertEquals(MdSpan("x", bold = true), table.rows.single().single().single())
    }

    @Test
    fun pipe_text_without_a_separator_is_a_paragraph() {
        assertTrue(BlogMarkdown.parse("a | b").single() is MdBlock.Paragraph)
    }

    // ---------- Citas ----------

    @Test
    fun blockquote_is_a_block_with_its_own_content() {
        val blocks = BlogMarkdown.parse("> Cita **fuerte**\n> segunda línea\n\nFuera")
        val quote = blocks[0] as MdBlock.Quote
        assertEquals("Cita fuerte\nsegunda línea", text((quote.blocks.single() as MdBlock.Paragraph).spans))
        assertEquals("Fuera", text((blocks[1] as MdBlock.Paragraph).spans))
    }

    @Test
    fun nested_blockquote_and_list_inside_a_quote() {
        val quote = BlogMarkdown.parse("> uno\n>\n> - a\n> - b\n>\n> > dentro").single() as MdBlock.Quote
        assertEquals(4, quote.blocks.size)
        assertTrue(quote.blocks[1] is MdBlock.Bullet)
        assertTrue(quote.blocks[3] is MdBlock.Quote)
    }

    // ---------- Enlaces ----------

    @Test
    fun bare_url_becomes_a_link_without_the_final_period() {
        val url = "https://ecency.com/@tasalo"
        assertEquals(
            listOf(MdSpan("Visita "), MdSpan(url, url = url), MdSpan(". Gracias")),
            BlogMarkdown.inline("Visita $url. Gracias"),
        )
    }

    @Test
    fun bare_url_inside_parentheses_does_not_take_the_closing_one() {
        val url = "https://a.test/x"
        assertEquals(
            listOf(MdSpan("(ver "), MdSpan(url, url = url), MdSpan(")")),
            BlogMarkdown.inline("(ver $url)"),
        )
    }

    @Test
    fun link_with_title_and_parentheses_in_the_address() {
        val spans = BlogMarkdown.inline("[Wiki](https://es.wikipedia.org/wiki/Foo_(bar) \"Título\")")
        assertEquals(listOf(MdSpan("Wiki", url = "https://es.wikipedia.org/wiki/Foo_(bar)")), spans)
    }

    @Test
    fun link_inside_bold_and_bold_inside_link() {
        assertEquals(
            listOf(MdSpan("a", bold = true, url = "https://x.test")),
            BlogMarkdown.inline("**[a](https://x.test)**"),
        )
        assertEquals(
            listOf(MdSpan("a", bold = true, url = "https://x.test")),
            BlogMarkdown.inline("[**a**](https://x.test)"),
        )
    }

    @Test
    fun mention_becomes_a_link_but_an_email_does_not() {
        assertEquals(
            listOf(MdSpan("Hola "), MdSpan("@ersusoficial", url = "https://ecency.com/@ersusoficial"), MdSpan("!")),
            BlogMarkdown.inline("Hola @ersusoficial!"),
        )
        assertEquals(listOf(MdSpan("mail a@b.com")), BlogMarkdown.inline("mail a@b.com"))
    }

    @Test
    fun unsafe_link_scheme_is_shown_as_plain_text() {
        assertEquals(listOf(MdSpan("x")), BlogMarkdown.inline("[x](javascript:alert(1))"))
    }

    // ---------- Formato en línea ----------

    @Test
    fun strikethrough_underscore_italic_and_escapes() {
        assertEquals(
            listOf(MdSpan("viejo", strike = true), MdSpan(" y "), MdSpan("cursiva", italic = true)),
            BlogMarkdown.inline("~~viejo~~ y _cursiva_"),
        )
        assertEquals("*no cursiva*", text(BlogMarkdown.inline("\\*no cursiva\\*")))
        assertEquals(listOf(MdSpan("snake_case_name")), BlogMarkdown.inline("snake_case_name"))
    }

    @Test
    fun line_breaks_inside_a_paragraph_are_kept() {
        val paragraph = BlogMarkdown.parse("uno\ndos").single() as MdBlock.Paragraph
        assertEquals("uno\ndos", text(paragraph.spans))
    }

    // ---------- Listas ----------

    @Test
    fun nested_and_task_lists() {
        val blocks = BlogMarkdown.parse("- a\n  - b\n- [x] hecho\n- [ ] pendiente\n1. uno\n2. dos")
        assertEquals(6, blocks.size)
        assertEquals(1, (blocks[1] as MdBlock.Bullet).indent)
        assertEquals(true, (blocks[2] as MdBlock.Bullet).checked)
        assertEquals("hecho", text((blocks[2] as MdBlock.Bullet).spans))
        assertEquals(false, (blocks[3] as MdBlock.Bullet).checked)
        assertEquals(2, (blocks[5] as MdBlock.Numbered).number)
    }

    @Test
    fun indented_line_continues_the_list_item() {
        val bullet = BlogMarkdown.parse("- uno\n  sigue").single() as MdBlock.Bullet
        assertEquals("uno\nsigue", text(bullet.spans))
    }

    // ---------- Títulos, código y separadores ----------

    @Test
    fun setext_headings_and_rule() {
        val blocks = BlogMarkdown.parse("Título\n=====\n\nSub\n---\n\nuno\ndos\n---")
        assertEquals(MdBlock.Heading(1, listOf(MdSpan("Título"))), blocks[0])
        assertEquals(MdBlock.Heading(2, listOf(MdSpan("Sub"))), blocks[1])
        assertTrue(blocks[2] is MdBlock.Paragraph)
        assertEquals(MdBlock.Rule, blocks[3])
    }

    @Test
    fun fenced_code_with_either_marker() {
        assertEquals(listOf<MdBlock>(MdBlock.Code("val a = 1")), BlogMarkdown.parse("```kt\nval a = 1\n```"))
        assertEquals(listOf<MdBlock>(MdBlock.Code("x")), BlogMarkdown.parse("~~~\nx\n~~~"))
    }

    // ---------- Imágenes ----------

    @Test
    fun linked_image_keeps_its_link_and_hive_size_hint_is_ignored() {
        assertEquals(
            listOf<MdBlock>(MdBlock.Image("Video", "https://i.test/t.png", "https://youtu.be/abc")),
            BlogMarkdown.parse("[![Video](https://i.test/t.png)](https://youtu.be/abc)"),
        )
        assertEquals(
            listOf<MdBlock>(MdBlock.Image("", "https://i.test/a.png")),
            BlogMarkdown.parse("![](http://i.test/a.png =300x200)"),
        )
    }

    @Test
    fun several_images_on_one_line_and_a_bare_image_address() {
        assertEquals(2, BlogMarkdown.parse("![a](https://i.test/a.png) ![b](https://i.test/b.png)").size)
        assertEquals(
            listOf<MdBlock>(MdBlock.Image("", "https://img.test/a.jpg?w=100")),
            BlogMarkdown.parse("https://img.test/a.jpg?w=100"),
        )
    }

    @Test
    fun inline_image_inside_text_becomes_its_description() {
        val paragraph = BlogMarkdown.parse("Mira ![logo](https://i.test/l.png) aquí").single() as MdBlock.Paragraph
        assertEquals("Mira logo aquí", text(paragraph.spans))
    }

    // ---------- Enrutado por dialecto ----------

    @Test
    fun full_dialect_uses_the_blog_parser_and_standard_does_not() {
        assertTrue(MarkdownParser.parse("> x", MdDialect.FULL).single() is MdBlock.Quote)
        assertTrue(MarkdownParser.parse("> x", MdDialect.STANDARD).single() is MdBlock.Paragraph)
    }
}
