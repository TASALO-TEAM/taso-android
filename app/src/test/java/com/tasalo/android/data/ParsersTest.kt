package com.tasalo.android.data

import com.tasalo.android.data.parse.Parsers
import com.tasalo.android.domain.Change
import com.tasalo.android.domain.Fuel
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
    fun latest_fixture_parses_the_three_sources_in_preferred_order() {
        val m = Parsers.latest(fixture("latest.json"))!!
        assertEquals(listOf("EUR", "USD", "MLC", "BTC", "TRX", "USDT"), m[Source.ELTOQUE]!!.map { it.currency })
        assertEquals(listOf("EUR", "USD", "CAD", "GBP", "CHF", "JPY", "MXN"), m[Source.CADECA]!!.map { it.currency })
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
    fun cup_is_dropped_from_cadeca() {
        val cadeca = Parsers.latest(fixture("latest.json"))!![Source.CADECA]!!
        assertFalse(cadeca.any { it.currency == "CUP" })
        val usd = cadeca.first { it.currency == "USD" }
        assertEquals(657.66, usd.buy!!, 0.0)
        assertEquals(684.78, usd.sell!!, 0.0)
    }

    @Test
    fun eltoque_endpoint_fixture_exposes_rates_object() {
        val rates = Parsers.ratesObject(fixture("eltoque.json"))
        assertNotNull(rates)
        assertTrue(rates!!.containsKey("USD"))
    }

    // ---------- casos borde (plan §9) ----------

    @Test
    fun cadeca_without_rate_uses_sell_then_buy() {
        val json = """{"ok":true,"data":{"cadeca":{
            "USD":{"buy":115,"sell":120},
            "EUR":{"buy":130,"sell":null}}}}"""
        val cadeca = Parsers.latest(json)!![Source.CADECA]!!.associateBy { it.currency }
        assertEquals(120.0, cadeca.getValue("USD").rate, 0.0)
        assertEquals(130.0, cadeca.getValue("EUR").rate, 0.0)
    }

    @Test
    fun cadeca_with_buy_and_sell_both_null_is_discarded() {
        val json = """{"ok":true,"data":{"cadeca":{"USD":{"rate":null,"buy":null,"sell":null,"change":"up"}}}}"""
        assertTrue(Parsers.latest(json)!![Source.CADECA]!!.isEmpty())
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

    // ---------- /tasas/fuel ----------

    @Test
    fun fuel_fixture_is_ordered_and_uses_buy_as_min_and_sell_as_max() {
        val fuel = Parsers.fuel(fixture("fuel.json"))!!
        assertEquals(listOf("B-94", "B-90", "B-83", "Petroleo"), fuel.map { it.key })
        val b94 = fuel.first { it.key == "B-94" }
        assertEquals(2500.0, b94.min!!, 0.0)
        assertEquals(5100.0, b94.max!!, 0.0)
    }

    @Test
    fun fuel_requires_rates_and_ignores_unknown_keys() {
        assertNull(Parsers.fuel("""{"source":"fuel"}"""))
        val fuel = Parsers.fuel("""{"rates":{"Gas_LP":{"rate":2000},"Otro":{"rate":1}}}""")!!
        assertEquals(listOf("Gas_LP"), fuel.map { it.key })
        assertEquals("CUP/balón", Fuel.META.getValue("Gas_LP").unit)
        assertEquals("CUP/L", Fuel.META.getValue("B-90").unit)
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
}
