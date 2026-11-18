package com.tasalo.android.data

import com.tasalo.android.data.parse.Parsers
import com.tasalo.android.domain.RateGroups
import com.tasalo.android.domain.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RateGroupsTest {
    private val eltoque = """
        {"ok":true,"data":{"eltoque":{
          "CAD":{"rate":700,"group":"other","tier":"market","order":1,"name":"Dólar canadiense","offers":5},
          "BNB":{"rate":500,"group":"crypto","tier":"reference","order":4,"offers":0},
          "ZELLE":{"rate":915,"group":"main","tier":"market","order":3,"name":"Zelle"},
          "BTC":{"rate":792,"group":"crypto","tier":"market","order":1},
          "USD":{"rate":930,"group":"main","tier":"market","order":2},
          "EUR":{"rate":1000,"group":"main","tier":"market","order":1},
          "AUD":{"rate":400,"group":"other","tier":"reference","order":4,"offers":0}
        },"bcc":{"USD":{"rate":520}},"qvapay":{}}}
    """.trimIndent()

    @Test
    fun parse_keeps_catalog_fields_and_sorts_by_group_then_order() {
        val rates = Parsers.latest(eltoque)!![Source.ELTOQUE]!!
        assertEquals(listOf("EUR", "USD", "ZELLE", "BTC", "BNB", "CAD", "AUD"), rates.map { it.currency })
        val zelle = rates.first { it.currency == "ZELLE" }
        assertEquals("Zelle", zelle.name)
        assertEquals("main", zelle.group)
        assertEquals(3, zelle.order)
        assertEquals(0, rates.first { it.currency == "BNB" }.offers)
    }

    @Test
    fun invalid_catalog_values_are_ignored() {
        val json = """{"ok":true,"data":{"eltoque":{"USD":{"rate":1,"group":"raro","tier":"x","order":"no"}}}}"""
        val usd = Parsers.latest(json)!![Source.ELTOQUE]!!.single()
        assertNull(usd.group)
        assertNull(usd.tier)
        assertNull(usd.order)
    }

    @Test
    fun group_splits_market_sections_from_reference() {
        val grouped = RateGroups.group(Parsers.latest(eltoque)!![Source.ELTOQUE]!!)!!
        assertEquals(listOf("main", "crypto", "other"), grouped.sections.map { it.group })
        assertEquals(listOf("EUR", "USD", "ZELLE"), grouped.sections[0].rates.map { it.currency })
        assertEquals(listOf("BNB", "AUD"), grouped.reference.map { it.currency })
    }

    @Test
    fun group_is_null_without_catalog_so_other_sources_look_the_same() {
        val bcc = Parsers.latest(eltoque)!![Source.BCC]!!
        assertNull(RateGroups.group(bcc))
        assertTrue(bcc.all { it.group == null })
    }

    @Test
    fun fallback_order_puts_zelle_after_usd_and_cla_after_mlc() {
        val json = """{"ok":true,"data":{"eltoque":{
            "CLA":{"rate":1},"MLC":{"rate":1},"ZELLE":{"rate":1},"USD":{"rate":1},"EUR":{"rate":1}}}}"""
        assertEquals(
            listOf("EUR", "USD", "ZELLE", "MLC", "CLA"),
            Parsers.latest(json)!![Source.ELTOQUE]!!.map { it.currency },
        )
    }
}
