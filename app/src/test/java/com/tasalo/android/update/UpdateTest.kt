package com.tasalo.android.update

import com.tasalo.android.util.MarkdownParser
import com.tasalo.android.util.MdBlock
import com.tasalo.android.util.MdDialect
import com.tasalo.android.util.MdSpan
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SemVerTest {
    @Test
    fun parses_tags_and_suffixes() {
        assertEquals(SemVer(0, 2, 0), SemVer.parse("v0.2.0"))
        assertEquals(SemVer(0, 2, 0), SemVer.parse("0.2.0-debug"))
        assertEquals(SemVer(1, 3, 0), SemVer.parse("1.3"))
        assertNull(SemVer.parse("beta"))
    }

    @Test
    fun compares_numerically_not_as_text() {
        assertTrue(SemVer.isNewer("0.10.0", "0.9.0"))
        assertTrue(SemVer.isNewer("v1.0.0", "0.99.99"))
        assertFalse(SemVer.isNewer("0.2.0", "0.2.0"))
        assertFalse(SemVer.isNewer("0.1.0", "0.2.0"))
        assertFalse(SemVer.isNewer("basura", "0.2.0"))
    }
}

class MarkdownParserTest {
    @Test
    fun headings_lists_and_paragraphs() {
        val blocks = MarkdownParser.parse(
            """
            ## 0.2.0
            ### Novedades
            - uno
            - dos
            1. primero

            Texto
            continuo
            ---
            """.trimIndent(),
        )
        assertEquals(MdBlock.Heading(2, listOf(MdSpan("0.2.0"))), blocks[0])
        assertEquals(MdBlock.Heading(3, listOf(MdSpan("Novedades"))), blocks[1])
        assertEquals(MdBlock.Bullet(0, listOf(MdSpan("uno"))), blocks[2])
        assertEquals(MdBlock.Bullet(0, listOf(MdSpan("dos"))), blocks[3])
        assertEquals(MdBlock.Numbered(1, listOf(MdSpan("primero"))), blocks[4])
        assertEquals(MdBlock.Paragraph(listOf(MdSpan("Texto continuo"))), blocks[5])
        assertEquals(MdBlock.Rule, blocks[6])
    }

    @Test
    fun inline_styles_and_links() {
        val spans = MarkdownParser.inline("a **fuerte** y *cursiva* con `code` y [web](https://x.org)")
        assertEquals(MdSpan("fuerte", bold = true), spans.first { it.bold })
        assertEquals(MdSpan("cursiva", italic = true), spans.first { it.italic })
        assertEquals(MdSpan("code", code = true), spans.first { it.code })
        assertEquals(MdSpan("web", url = "https://x.org"), spans.first { it.url != null })
    }

    @Test
    fun nested_bold_inside_link_and_code_blocks() {
        val link = MarkdownParser.inline("[**ver**](https://x.org)").single()
        assertTrue(link.bold)
        assertEquals("https://x.org", link.url)

        val blocks = MarkdownParser.parse("```\nfoo\n  bar\n```")
        assertEquals(MdBlock.Code("foo\n  bar"), blocks.single())
    }

    @Test
    fun telegram_dialect_uses_single_marks_and_keeps_line_breaks() {
        val blocks = MarkdownParser.parse(
            "*Importante*: mantenimiento\nSegunda línea\n# no es título\n- punto\n• otro",
            MdDialect.TELEGRAM,
        )
        assertEquals(MdBlock.Paragraph(listOf(MdSpan("Importante", bold = true), MdSpan(": mantenimiento"))), blocks[0])
        assertEquals(MdBlock.Paragraph(listOf(MdSpan("Segunda línea"))), blocks[1])
        assertEquals(MdBlock.Paragraph(listOf(MdSpan("# no es título"))), blocks[2]) // en Telegram no hay títulos
        assertEquals(MdBlock.Bullet(0, listOf(MdSpan("punto"))), blocks[3])
        assertEquals(MdBlock.Bullet(0, listOf(MdSpan("otro"))), blocks[4])
    }

    @Test
    fun telegram_italic_ignores_underscores_inside_words() {
        val spans = MarkdownParser.inline("usa snake_case_name y _cursiva_", dialect = MdDialect.TELEGRAM)
        assertEquals(MdSpan("cursiva", italic = true), spans.single { it.italic })
        assertTrue(spans.any { it.text.contains("snake_case_name") })
    }

    @Test
    fun telegram_code_links_and_unsafe_links() {
        val spans = MarkdownParser.inline("`x=1` y [web](https://x.org) y [mala](javascript:void)", dialect = MdDialect.TELEGRAM)
        assertEquals(MdSpan("x=1", code = true), spans.first { it.code })
        assertEquals(MdSpan("web", url = "https://x.org"), spans.first { it.url != null })
        assertNull(spans.first { it.text == "mala" }.url)
    }

    @Test
    fun standard_dialect_is_unchanged() {
        // En Markdown estándar `*x*` sigue siendo cursiva y `**x**` negrita.
        assertEquals(MdSpan("x", italic = true), MarkdownParser.inline("*x*").single())
        assertEquals(MdSpan("x", bold = true), MarkdownParser.inline("**x**").single())
    }

    @Test
    fun unsafe_link_schemes_are_shown_as_plain_text() {
        assertTrue(MarkdownParser.isSafeUrl("https://x.org"))
        assertTrue(MarkdownParser.isSafeUrl("tg://resolve?domain=x"))
        assertFalse(MarkdownParser.isSafeUrl("javascript:alert(1)"))
        assertFalse(MarkdownParser.isSafeUrl("intent://scan#Intent;end"))
        assertFalse(MarkdownParser.isSafeUrl("file:///sdcard/x"))
        val span = MarkdownParser.inline("[toca](javascript:void)").single()
        assertEquals("toca", span.text)
        assertNull(span.url)
    }

    @Test
    fun html_comments_and_crlf_are_handled() {
        val blocks = MarkdownParser.parse("<!-- oculto -->\r\n- a\r\n- b")
        assertEquals(2, blocks.size)
    }
}

class UpdateCheckerTest {
    private lateinit var server: MockWebServer
    private val repo = "TASALO-TEAM/taso-android"

    private val releaseJson = """
        {"tag_name":"v0.3.0","html_url":"https://github.com/$repo/releases/tag/v0.3.0",
         "body":"## Novedades\n- algo",
         "assets":[{"name":"notas.txt","size":1,"browser_download_url":"https://x/notas.txt"},
                   {"name":"taso-android-v0.3.0.apk","size":2280740,"browser_download_url":"https://x/app.apk"}]}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun base() = server.url("/").toString().trimEnd('/')

    private fun checker() = UpdateChecker(OkHttpClient(), apiBase = base(), webBase = base(), repo = repo)

    private fun route(handler: (String) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handler(request.path.orEmpty())
        }
    }

    @Test
    fun parse_release_picks_the_apk_asset_and_notes() {
        val info = UpdateChecker.parseRelease(releaseJson, "https://github.com", repo)!!
        assertEquals("0.3.0", info.version)
        assertEquals("https://x/app.apk", info.apkUrl)
        assertEquals(2280740L, info.apkBytes)
        assertTrue(info.notes!!.startsWith("## Novedades"))
    }

    @Test
    fun parse_release_rejects_garbage() {
        assertNull(UpdateChecker.parseRelease("no es json", "https://github.com", repo))
        assertNull(UpdateChecker.parseRelease("""{"name":"sin tag"}""", "https://github.com", repo))
    }

    @Test
    fun newer_release_is_available() = runBlocking {
        route { if (it == "/repos/$repo/releases/latest") MockResponse().setBody(releaseJson) else MockResponse().setResponseCode(404) }
        val result = checker().check("0.2.0")
        assertTrue(result is UpdateResult.Available)
        assertEquals("0.3.0", (result as UpdateResult.Available).info.version)
    }

    @Test
    fun same_version_is_up_to_date() = runBlocking {
        route { MockResponse().setBody(releaseJson) }
        assertEquals(UpdateResult.UpToDate, checker().check("0.3.0"))
        assertEquals(UpdateResult.UpToDate, checker().check("0.4.0"))
    }

    @Test
    fun api_rate_limit_falls_back_to_the_releases_redirect() = runBlocking {
        route {
            when (it) {
                "/repos/$repo/releases/latest" -> MockResponse().setResponseCode(403)
                "/$repo/releases/latest" ->
                    MockResponse().setResponseCode(302).setHeader("Location", "${base()}/$repo/releases/tag/v0.3.0")
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = checker().check("0.2.0") as UpdateResult.Available
        assertEquals("0.3.0", result.info.version)
        assertNull(result.info.notes)
        assertTrue(result.info.apkUrl!!.endsWith("/releases/download/v0.3.0/taso-android-v0.3.0.apk"))
    }

    @Test
    fun total_failure_is_reported_not_thrown() = runBlocking {
        route { MockResponse().setResponseCode(500) }
        assertTrue(checker().check("0.2.0") is UpdateResult.Failed)
        assertNotNull(UpdateChecker.parseRelease(releaseJson, "https://github.com", repo))
    }
}

class UpdateInstallTest {
    @Test
    fun checksum_file_formats_are_parsed() {
        val hash = "a".repeat(32) + "B".repeat(32)
        assertEquals(hash.lowercase(), UpdateChecker.parseSha256("$hash  taso-android-v0.3.0.apk\n"))
        assertEquals(hash.lowercase(), UpdateChecker.parseSha256(hash))
        assertNull(UpdateChecker.parseSha256("no es un hash"))
        assertNull(UpdateChecker.parseSha256("abc123"))
    }

    @Test
    fun downloads_are_only_allowed_from_our_releases() {
        val ok = "https://github.com/TASALO-TEAM/taso-android/releases/download/v0.3.0/taso-android-v0.3.0.apk"
        assertTrue(UpdateDownloader.isAllowedUrl(ok))
        assertFalse(UpdateDownloader.isAllowedUrl("https://github.com/otro/repo/releases/download/v1/app.apk"))
        assertFalse(UpdateDownloader.isAllowedUrl("http://github.com/TASALO-TEAM/taso-android/releases/x.apk"))
        assertFalse(UpdateDownloader.isAllowedUrl("https://evil.example/TASALO-TEAM/taso-android/x.apk"))
    }

    @Test
    fun release_json_exposes_the_sha256_asset() {
        val json = """
            {"tag_name":"v0.3.0","html_url":"https://github.com/x","body":"notas",
             "assets":[{"name":"taso-android-v0.3.0.apk","size":10,"browser_download_url":"https://github.com/a.apk"},
                       {"name":"taso-android-v0.3.0.apk.sha256","size":90,"browser_download_url":"https://github.com/a.apk.sha256"}]}
        """.trimIndent()
        val info = UpdateChecker.parseRelease(json, "https://github.com", "TASALO-TEAM/taso-android")!!
        assertEquals("https://github.com/a.apk", info.apkUrl)
        assertEquals("https://github.com/a.apk.sha256", info.sha256Url)
    }

    @Test
    fun release_list_skips_drafts_and_prereleases_and_keeps_order() {
        val json = """
            [{"tag_name":"v0.3.0","name":"TASALO Android v0.3.0","body":"nuevo","published_at":"2026-10-02T10:00:00Z","html_url":"https://github.com/r3"},
             {"tag_name":"v0.3.0-rc1","prerelease":true,"body":"x"},
             {"tag_name":"v0.2.9","draft":true,"body":"x"},
             {"tag_name":"v0.2.3","body":"viejo","published_at":"2026-10-01T10:00:00Z"}]
        """.trimIndent()
        val list = UpdateChecker.parseReleases(json)
        assertEquals(listOf("0.3.0", "0.2.3"), list.map { it.version })
        assertEquals("TASALO Android v0.3.0", list[0].title)
        assertEquals("Versión 0.2.3", list[1].title)
        assertNotNull(list[0].publishedAt)
        assertTrue(UpdateChecker.parseReleases("basura").isEmpty())
    }
}