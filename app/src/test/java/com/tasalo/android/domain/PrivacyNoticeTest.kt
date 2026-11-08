package com.tasalo.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyNoticeTest {

    @Test
    fun `cada bloque tiene titulo y al menos un parrafo con texto`() {
        assertTrue(PrivacyNotice.INTRO.isNotBlank())
        assertTrue(PrivacyNotice.sections.isNotEmpty())
        PrivacyNotice.sections.forEach { s ->
            assertTrue("titulo vacio", s.title.isNotBlank())
            assertTrue("sin parrafos: ${s.title}", s.lines.isNotEmpty())
            s.lines.forEach { assertTrue("parrafo vacio en ${s.title}", it.isNotBlank()) }
        }
    }

    @Test
    fun `los titulos no se repiten`() {
        val titles = PrivacyNotice.sections.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
    }

    @Test
    fun `el texto completo apunta al documento del repo`() {
        assertEquals("$REPO_URL/blob/main/docs/PRIVACIDAD.md", PrivacyNotice.FULL_TEXT_URL)
    }

    @Test
    fun `no cita leyes y no deja marcas de pendiente`() {
        val all = (listOf(PrivacyNotice.INTRO) + PrivacyNotice.sections.flatMap { it.lines }).joinToString("\n")
        assertTrue(!Regex("""\b(Ley|RGPD|GDPR)\b""").containsMatchIn(all))
        assertTrue(!Regex("""TODO|\[\s*\]|XXX|por definir""", RegexOption.IGNORE_CASE).containsMatchIn(all))
    }
}
