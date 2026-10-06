package com.tasalo.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorFormatTest {

    @Test
    fun amount_adapts_decimals_to_the_size() {
        assertEquals("20", Format.amount(20.0))
        assertEquals("17,5", Format.amount(17.5))
        assertEquals("10\u00A0400", Format.amount(10400.0))
        assertEquals("0,0192", Format.amount(0.01923))
        assertEquals("0,00038", Format.amount(0.00038))
        assertEquals("0", Format.amount(0.0))
    }

    @Test
    fun parseAmount_accepts_comma_or_dot() {
        assertEquals(20.0, Format.parseAmount("20")!!, 0.0)
        assertEquals(1.5, Format.parseAmount("1,5")!!, 0.0)
        assertEquals(1.5, Format.parseAmount("1.5")!!, 0.0)
    }

    @Test
    fun parseAmount_rejects_garbage() {
        assertNull(Format.parseAmount(""))
        assertNull(Format.parseAmount(","))
        assertNull(Format.parseAmount("1.2.3"))
        assertNull(Format.parseAmount("-5"))
    }
}
