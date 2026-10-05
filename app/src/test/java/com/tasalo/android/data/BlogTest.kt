package com.tasalo.android.data

import com.tasalo.android.data.parse.BlogParser
import com.tasalo.android.domain.BlogCache
import com.tasalo.android.domain.BlogPost
import com.tasalo.android.util.BlogText
import com.tasalo.android.util.MarkdownParser
import com.tasalo.android.util.MdBlock
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlogTest {

    // ---------- Limpieza de HTML ----------

    @Test
    fun clean_converts_html_to_markdown() {
        val html = """<p>Hola <b>mundo</b></p><img src="https://img.test/a.png"><br>Fin &amp; fin"""
        assertEquals("Hola **mundo**\n\n![](https://img.test/a.png)\n\nFin & fin", BlogText.clean(html))
    }

    @Test
    fun clean_converts_html_links() {
        assertEquals("[Sitio](https://a.test)", BlogText.clean("""<a href="https://a.test">Sitio</a>"""))
    }

    @Test
    fun clean_does_not_touch_code_blocks() {
        val text = "Texto <b>x</b>\n```\n<div>a</div>\n```"
        assertEquals("Texto **x**\n```\n<div>a</div>\n```", BlogText.clean(text))
    }

    @Test
    fun clean_keeps_plain_markdown_as_is() {
        val md = "# Título\n\nUn párrafo con **negrita** y [enlace](https://x.test)."
        assertEquals(md, BlogText.clean(md))
    }

    // ---------- Resumen ----------

    @Test
    fun summary_drops_headings_images_links_and_marks() {
        val md = "# Título\n\nEste es un **post** con [enlace](https://x.test) e imagen ![alt](https://i.test/a.png) y más texto."
        assertEquals("Este es un post con enlace e imagen y más texto.", BlogText.summary(md))
    }

    @Test
    fun summary_cuts_at_a_word_and_adds_ellipsis() {
        assertEquals("uno dos tres cuatro…", BlogText.summary("uno dos tres cuatro cinco seis", max = 20))
    }

    @Test
    fun summary_of_short_text_is_unchanged() {
        assertEquals("Corto.", BlogText.summary("Corto."))
    }

    // ---------- Respuesta de Hive ----------

    private val hive = """
        {"jsonrpc":"2.0","id":1,"result":[
          {"author":"ersusoficial","permlink":"viejo","title":"Viejo","body":"Texto <b>viejo</b>",
           "created":"2026-01-01T10:00:00","json_metadata":{"image":["https://img.test/v.png"]}},
          {"author":"ersusoficial","permlink":"nuevo","title":" Nuevo ","body":"# Nuevo\n\nHola ![x](https://img.test/n.png)",
           "created":"2026-09-30T12:00:00","json_metadata":"{\"image\":[\"http://inseguro.test/a.png\"]}"},
          {"author":"ersusoficial","permlink":"","title":"Sin permlink","body":"x"}
        ]}
    """.trimIndent()

    @Test
    fun fromHive_parses_sorts_and_skips_incomplete_posts() {
        val posts = BlogParser.fromHive(hive)
        assertNotNull(posts)
        assertEquals(listOf("nuevo", "viejo"), posts!!.map { it.permlink })

        val nuevo = posts[0]
        assertEquals("Nuevo", nuevo.title)
        assertEquals("Hola", nuevo.summary)
        assertEquals(Instant.parse("2026-09-30T12:00:00Z"), nuevo.createdAt)
        // La portada declarada es http (no se admite): se usa la primera imagen https del cuerpo.
        assertEquals("https://img.test/n.png", nuevo.cover)
        assertEquals("https://ecency.com/@ersusoficial/nuevo", nuevo.url)

        val viejo = posts[1]
        assertEquals("Texto **viejo**", viejo.body)
        assertEquals("https://img.test/v.png", viejo.cover)
    }

    @Test
    fun fromHive_rejects_invalid_answers_but_accepts_an_empty_account() {
        assertNull(BlogParser.fromHive("no es json"))
        assertNull(BlogParser.fromHive("""{"error":{"code":-32000}}"""))
        assertEquals(emptyList<BlogPost>(), BlogParser.fromHive("""{"result":[]}"""))
    }

    // ---------- Caché local ----------

    @Test
    fun encode_then_decode_returns_the_same_posts() {
        val at = Instant.parse("2026-10-01T00:00:00Z")
        val posts = listOf(
            BlogPost(
                author = "ersusoficial",
                permlink = "a",
                title = "A",
                body = "cuerpo **a**",
                summary = "cuerpo a",
                createdAt = Instant.parse("2026-09-30T12:00:00Z"),
                cover = "https://img.test/a.png",
            ),
            BlogPost("ersusoficial", "b", "B", "b", "b", null, null),
        )
        assertEquals(BlogCache(posts, at), BlogParser.decode(BlogParser.encode(posts, at)))
        assertNull(BlogParser.decode("basura"))
        assertTrue(BlogParser.decode("""{"v":1}""") == null)
    }

    // ---------- Imágenes en el lector Markdown ----------

    @Test
    fun markdown_image_on_its_own_line_is_a_block() {
        assertEquals(
            listOf<MdBlock>(MdBlock.Image("Foto", "https://i.test/a.png")),
            MarkdownParser.parse("![Foto](https://i.test/a.png)"),
        )
        assertEquals(
            listOf<MdBlock>(MdBlock.Image("Foto", "https://i.test/a.png")),
            MarkdownParser.parse("[![Foto](https://i.test/a.png)](https://x.test)"),
        )
    }

    @Test
    fun markdown_image_with_unsafe_scheme_is_dropped() {
        assertTrue(MarkdownParser.parse("![x](http://insegura.test/a.png)").isEmpty())
    }

    @Test
    fun markdown_inline_image_becomes_its_description() {
        val block = MarkdownParser.parse("Mira ![logo](https://i.test/l.png) aquí").single() as MdBlock.Paragraph
        assertEquals("Mira logo aquí", block.spans.joinToString("") { it.text })
    }
}
