package com.tasalo.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConverterTest {

    private fun rate(currency: String, value: Double) = Rate(currency, value, null, null, Change.NEUTRAL, null)

    private val table = Converter.table(
        listOf(
            rate("USD", 520.0),
            rate("EUR", 585.0),
            rate("BTC", 52_000_000.0),
            rate("CUP", 99.0), // el CUP siempre vale 1, venga lo que venga de la API
            rate("MLC", 0.0), // tasa inválida: se descarta
        ),
    )

    @Test
    fun usd_to_cup() {
        assertEquals(10400.0, Converter.convert(20.0, "USD", "CUP", table)!!, 1e-9)
    }

    @Test
    fun cup_to_usd_is_the_inverse() {
        assertEquals(20.0, Converter.convert(10400.0, "CUP", "USD", table)!!, 1e-9)
    }

    @Test
    fun usd_to_eur_goes_through_cup() {
        assertEquals(20.0 * 520.0 / 585.0, Converter.convert(20.0, "USD", "EUR", table)!!, 1e-9)
    }

    @Test
    fun cup_is_always_one_and_invalid_rates_are_dropped() {
        assertEquals(1.0, table.getValue("CUP"), 0.0)
        assertFalse("MLC" in table)
    }

    @Test
    fun unknown_currency_returns_null() {
        assertNull(Converter.convert(1.0, "USD", "XXX", table))
        assertNotNull(Converter.convert(1.0, "BTC", "USD", table))
    }

    @Test
    fun codes_put_cup_first_then_the_usual_order() {
        assertEquals(listOf("CUP", "EUR", "USD", "BTC"), Converter.codes(table))
    }

    // QvaPay: cada valor es "cuanto de ese metodo por 1 USD"; el pivote es el USD.
    private val qvapay = Converter.table(
        listOf(
            rate("CUP", 980.0),
            rate("ZELLE", 1.02),
            rate("MLC", 1.5),
            rate("USD", 7.0), // el USD siempre vale 1 en QvaPay
            rate("BAD", 0.0), // tasa invalida: se descarta
        ),
        Source.QVAPAY,
    )

    @Test
    fun qvapay_pivots_on_usd_and_cup_is_a_normal_method() {
        assertEquals(1.0, qvapay.getValue("USD"), 0.0)
        assertEquals(19600.0, Converter.convert(20.0, "USD", "CUP", qvapay)!!, 1e-6)
        assertEquals(20.0, Converter.convert(19600.0, "CUP", "USD", qvapay)!!, 1e-9)
        assertFalse("BAD" in qvapay)
    }

    @Test
    fun qvapay_converts_between_methods_through_usd() {
        assertEquals(100.0 / 980.0 * 1.5, Converter.convert(100.0, "CUP", "MLC", qvapay)!!, 1e-9)
    }

    @Test
    fun qvapay_codes_put_usd_first_then_the_method_order() {
        assertEquals(listOf("USD", "CUP", "MLC", "ZELLE"), Converter.codes(qvapay, Source.QVAPAY))
    }
}
