package com.tasalo.android.data

import com.tasalo.android.data.parse.Parsers
import com.tasalo.android.domain.Change
import java.time.Instant
import com.tasalo.android.domain.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText()

    // ---------- /tasas/latest (fixture real capturado en la Fase 0) ----------

    @Test
    fun latest_fixture_parses_the_sources_in_preferred_order() {
        val m = Parsers.latest(fixture("latest.json"))!!
        assertEquals(listOf("EUR", "USD", "MLC", "BTC", "TRX", "USDT"), m[Source.ELTOQUE]!!.map { it.currency })
        assertEquals(
            listOf("EUR", "USD", "CAD", "GBP", "CHF", "RUB", "AUD", "JPY", "MXN").toSet(),
            m[Source.BCC]!!.map { it.currency }.toSet(),
        )
    }

    @Test
    fun latest_fixture_values_and_changes() {
        val eltoque = Parsers.latest(fixture("latest.json"))!![Source.ELTOQUE]!!.associateBy { it.currency }
        assertEquals(750.0, eltoque.getValue("USD").rate, 0.0)
        assertEquals(Change.NEUTRAL, eltoque.getValue("USD").change)
        assertEquals(Change.UP, eltoque.getValue("MLC").change)
        assertEquals(485.07, eltoque.getValue("MLC").prevRate!!, 0.0)
    }

    @Test
    fun cup_is_dropped_from_bcc() {
        val json = """{"ok":true,"data":{"bcc":{"CUP":{"rate":1},"USD":{"rate":500}}}}"""
        val bcc = Parsers.latest(json)!![Source.BCC]!!
        assertFalse(bcc.any { it.currency == "CUP" })
        assertEquals(500.0, bcc.single().rate, 0.0)
    }

    @Test
    fun eltoque_endpoint_fixture_exposes_rates_object() {
        val rates = Parsers.ratesObject(fixture("eltoque.json"))
        assertNotNull(rates)
        assertTrue(rates!!.containsKey("USD"))
    }

    // ---------- casos borde (plan §9) ----------

    @Test
    fun source_without_rate_uses_sell_then_buy() {
        val json = """{"ok":true,"data":{"bcc":{
            "USD":{"buy":115,"sell":120},
            "EUR":{"buy":130,"sell":null}}}}"""
        val bcc = Parsers.latest(json)!![Source.BCC]!!.associateBy { it.currency }
        assertEquals(120.0, bcc.getValue("USD").rate, 0.0)
        assertEquals(130.0, bcc.getValue("EUR").rate, 0.0)
    }

    @Test
    fun source_with_buy_and_sell_both_null_is_discarded() {
        val json = """{"ok":true,"data":{"bcc":{"USD":{"rate":null,"buy":null,"sell":null,"change":"up"}}}}"""
        assertTrue(Parsers.latest(json)!![Source.BCC]!!.isEmpty())
    }

    @Test
    fun legacy_cadeca_key_is_ignored() {
        val json = """{"ok":true,"data":{"eltoque":{"USD":{"rate":500}},"cadeca":{"USD":{"buy":115,"sell":120}}}}"""
        val m = Parsers.latest(json)!!
        assertEquals(Source.entries.toSet(), m.keys)
        assertEquals(500.0, m.getValue(Source.ELTOQUE).single().rate, 0.0)
        assertNull(Source.fromId("cadeca"))
    }

    @Test
    fun numbers_as_strings_with_comma_are_accepted() {
        val json = """{"ok":true,"data":{"eltoque":{"USD":{"rate":"365,5","prev_rate":"1.234,5"}}}}"""
        val usd = Parsers.latest(json)!![Source.ELTOQUE]!!.single()
        assertEquals(365.5, usd.rate, 0.0)
        assertEquals(1234.5, usd.prevRate!!, 0.0)
    }

    @Test
    fun empty_or_null_or_missing_sources_are_empty_lists() {
        val json = """{"ok":true,"data":{"eltoque":{},"bcc":null}}"""
        val m = Parsers.latest(json)!!
        Source.entries.forEach { assertTrue(m.getValue(it).isEmpty()) }
    }

    @Test
    fun unknown_change_is_neutral_and_null_prev_rate_is_ok() {
        val json = """{"ok":true,"data":{"eltoque":{"USD":{"rate":1,"change":"sideways","prev_rate":null}}}}"""
        val usd = Parsers.latest(json)!![Source.ELTOQUE]!!.single()
        assertEquals(Change.NEUTRAL, usd.change)
        assertNull(usd.prevRate)
        assertNull(usd.changePct)
    }

    @Test
    fun weird_binance_and_extra_fields_do_not_break_parsing() {
        val json = """{"ok":true,"extra":{"a":1},"data":{
            "eltoque":{"USD":{"rate":750,"surprise":[1,2,3]}},
            "binance":{"X":"weird","Y":null,"Z":[1]}}}"""
        val m = Parsers.latest(json)!!
        assertEquals(1, m[Source.ELTOQUE]!!.size)
    }

    @Test
    fun latest_rejects_not_ok_missing_data_and_corrupt_json() {
        assertNull(Parsers.latest("""{"ok":false,"data":{}}"""))
        assertNull(Parsers.latest("""{"ok":true}"""))
        assertNull(Parsers.latest("""{"ok":true,"data":null}"""))
        assertNull(Parsers.latest("{esto no es json"))
        assertNull(Parsers.latest(""))
    }

    // ---------- /year/state ----------

    @Test
    fun year_fixture_uses_progress_and_quote_only() {
        val y = Parsers.year(fixture("year_state.json"))!!
        assertEquals(2026, y.year)
        assertEquals(93, y.daysLeft)
        assertEquals(74.42717867837075, y.percent!!, 1e-9)
        assertTrue(y.quote!!.startsWith("La educaci"))
    }

    @Test
    fun year_requires_ok_true() {
        assertNull(Parsers.year("""{"ok":false}"""))
        assertNull(Parsers.year("basura"))
        val y = Parsers.year("""{"ok":true}""")!!
        assertNull(y.percent)
        assertNull(y.quote)
    }

    // ---------- /app/messages (Alertas) ----------

    @Test
    fun messages_fixture_is_parsed_newest_first_with_its_format() {
        val messages = Parsers.messages(fixture("app_messages.json"))!!
        assertEquals(listOf(3L, 2L), messages.map { it.id })
        assertEquals("Mantenimiento esta noche", messages[0].title)
        assertEquals("telegram", messages[0].format)
        assertTrue(messages[0].body.startsWith("*Esta noche*"))
        assertEquals("markdown", messages[1].format)
        assertEquals(Instant.parse("2026-10-02T14:30:00.123456Z"), messages[0].createdAt)
    }

    @Test
    fun messages_discard_invalid_entries_and_default_the_format() {
        val json = """{"ok":true,"data":[
            {"id":1,"title":"Sin cuerpo","body":""},
            {"title":"Sin id","body":"x"},
            {"id":5,"title":"Válido","body":"texto","format":"html","created_at":"fecha rota"},
            "basura"]}"""
        val messages = Parsers.messages(json)!!
        assertEquals(1, messages.size)
        assertEquals("telegram", messages[0].format) // formato desconocido -> telegram
        assertNull(messages[0].createdAt)
    }

    @Test
    fun messages_require_ok_and_a_data_list() {
        assertNull(Parsers.messages("""{"ok":false,"data":[]}"""))
        assertNull(Parsers.messages("""{"ok":true,"data":{}}"""))
        assertNull(Parsers.messages("no es json"))
        assertTrue(Parsers.messages("""{"ok":true,"data":[]}""")!!.isEmpty())
    }

    @Test
    fun instant_accepts_offsets_zulu_and_naive_dates() {
        val expected = Instant.parse("2026-10-02T14:30:00Z")
        assertEquals(expected, Parsers.instant("2026-10-02T14:30:00+00:00"))
        assertEquals(expected, Parsers.instant("2026-10-02T14:30:00Z"))
        assertEquals(expected, Parsers.instant("2026-10-02T14:30:00"))
        assertEquals(expected, Parsers.instant("2026-10-02T10:30:00-04:00"))
        assertNull(Parsers.instant("mañana"))
        assertNull(Parsers.instant(null))
    }
}
