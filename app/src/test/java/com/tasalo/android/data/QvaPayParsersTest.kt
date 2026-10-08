package com.tasalo.android.data

import com.tasalo.android.data.parse.Parsers
import com.tasalo.android.domain.Change
import com.tasalo.android.domain.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QvaPayParsersTest {
    private val json = """{"ok":true,"data":{"qvapay":{
        "ZELLE":{"rate":1.02,"buy":1.02,"sell":null,"change":"neutral","prev_rate":null},
        "CUP":{"rate":981.54,"buy":978.31,"sell":984.78,"change":"up","prev_rate":979.2},
        "MLC":{"rate":1.45,"buy":1.4,"sell":1.5,"change":"down","prev_rate":1.46}
    }}}"""

    @Test
    fun qvapay_keeps_cup_and_orders_by_method() {
        val list = Parsers.latest(json)!![Source.QVAPAY]!!
        assertEquals(listOf("CUP", "MLC", "ZELLE"), list.map { it.currency })
    }

    @Test
    fun qvapay_reads_average_buy_sell_and_change() {
        val cup = Parsers.latest(json)!![Source.QVAPAY]!!.first { it.currency == "CUP" }
        assertEquals(981.54, cup.rate, 0.0)
        assertEquals(978.31, cup.buy!!, 0.0)
        assertEquals(984.78, cup.sell!!, 0.0)
        assertEquals(Change.UP, cup.change)
        assertEquals(979.2, cup.prevRate!!, 0.0)
    }

    @Test
    fun qvapay_with_only_one_side_still_parses() {
        val zelle = Parsers.latest(json)!![Source.QVAPAY]!!.first { it.currency == "ZELLE" }
        assertEquals(1.02, zelle.rate, 0.0)
        assertNull(zelle.sell)
    }

    @Test
    fun cup_is_still_dropped_in_the_other_sources() {
        val text = """{"ok":true,"data":{"eltoque":{"CUP":{"rate":1.0},"USD":{"rate":520.0}}}}"""
        val eltoque = Parsers.latest(text)!![Source.ELTOQUE]!!
        assertEquals(listOf("USD"), eltoque.map { it.currency })
    }

    @Test
    fun an_api_without_qvapay_gives_an_empty_list() {
        val text = """{"ok":true,"data":{"eltoque":{"USD":{"rate":520.0}}}}"""
        assertTrue(Parsers.latest(text)!![Source.QVAPAY]!!.isEmpty())
    }
}
