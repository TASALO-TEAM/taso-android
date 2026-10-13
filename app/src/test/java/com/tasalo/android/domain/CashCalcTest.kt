package com.tasalo.android.domain

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CashCalcTest {
    private val cup = CashCurrency.CUP
    private val usd = CashCurrency.USD
    private val eur = CashCurrency.EUR
    private val at = LocalDateTime.of(2026, 10, 13, 14, 35)

    private fun counts(vararg pairs: Pair<String, String>): Map<String, String> = mapOf(*pairs)

    @Test
    fun denominations_match_the_notes_in_circulation() {
        assertEquals(listOf(1L, 3L, 5L, 10L, 20L, 50L, 100L, 200L, 500L, 1000L, 2000L, 5000L, 10000L, 20000L), cup.denominations)
        assertEquals(listOf(1L, 2L, 5L, 10L, 20L, 50L, 100L), usd.denominations)
        assertEquals(listOf(5L, 10L, 20L, 50L, 100L, 200L, 500L), eur.denominations)
    }

    @Test
    fun row_value_is_quantity_times_denomination_and_empty_is_zero() {
        assertEquals(100_000L, CashCalc.rowValue(20_000L, "5"))
        assertEquals(0L, CashCalc.rowValue(20_000L, ""))
        assertEquals(0L, CashCalc.rowValue(20_000L, null))
    }

    @Test
    fun input_keeps_only_ascii_digits_and_at_most_six() {
        assertEquals("123456", CashCalc.sanitize("12a3456789"))
        assertEquals("", CashCalc.sanitize("-,. "))
        assertEquals("", CashCalc.sanitize("\u0663\u0664"))
        // 999 999 billetes de 20 000 siguen cabiendo en un Long.
        assertEquals(19_999_980_000L, CashCalc.rowValue(20_000L, "9999999"))
    }

    @Test
    fun total_sums_only_the_chosen_currency() {
        val c = counts("CUP:1" to "5", "CUP:3" to "2", "CUP:10" to "4", "USD:1" to "2", "EUR:5" to "1")
        assertEquals(51L, CashCalc.total(cup, c))
        assertEquals(2L, CashCalc.total(usd, c))
        assertEquals(5L, CashCalc.total(eur, c))
        assertEquals(0L, CashCalc.total(cup, emptyMap()))
    }

    @Test
    fun the_same_denomination_in_two_currencies_does_not_mix() {
        val c = counts(CashCalc.key(usd, 5L) to "3", CashCalc.key(eur, 5L) to "7")
        assertEquals(15L, CashCalc.total(usd, c))
        assertEquals(35L, CashCalc.total(eur, c))
    }

    @Test
    fun encode_and_decode_round_trip_and_skip_empty_values() {
        val c = counts("CUP:20000" to "5", "USD:1" to "2", "EUR:5" to "")
        val saved = CashCalc.encode(c)
        assertEquals(mapOf("CUP:20000" to "5", "USD:1" to "2"), CashCalc.decode(saved))
        assertEquals(emptyMap<String, String>(), CashCalc.decode(""))
        assertEquals(emptyMap<String, String>(), CashCalc.decode("basura;=3;CUP:1="))
    }

    @Test
    fun group_uses_a_space_for_thousands() {
        assertEquals("0", CashReceipt.group(0))
        assertEquals("999", CashReceipt.group(999))
        assertEquals("20 000", CashReceipt.group(20_000))
        assertEquals("1 234 567", CashReceipt.group(1_234_567))
    }

    @Test
    fun individual_receipt_has_one_currency_and_no_final_summary() {
        val c = counts("CUP:1" to "5", "CUP:3" to "2", "CUP:10" to "4", "USD:1" to "2")
        val text = CashReceipt.build(listOf(cup), c, at, summary = false)
        val lines = text.trimEnd('\n').split('\n')
        assertEquals("=".repeat(50), lines.first())
        assertTrue(lines.all { it.length <= CashReceipt.WIDTH })
        assertTrue("Fecha: 2026-10-13" in lines)
        assertTrue("Hora: 14:35" in lines)
        assertTrue("MONEDA: CUP" in lines)
        assertTrue("1".padEnd(14) + "5".padStart(12) + "5".padStart(24) in lines)
        assertTrue("5".padEnd(14) + "0".padStart(12) + "0".padStart(24) in lines) // los billetes en 0 también salen
        assertTrue("TOTAL CUP:" + "51 CUP".padStart(40) in lines)
        assertTrue(lines.none { it.contains("USD") || it.contains("RESUMEN") })
        assertEquals("Documento generado por TASALO", lines.last())
    }

    @Test
    fun total_receipt_has_every_currency_and_a_final_summary() {
        val c = counts("CUP:1" to "5", "CUP:3" to "2", "CUP:10" to "4", "USD:1" to "2", "EUR:5" to "1")
        val text = CashReceipt.build(CashCurrency.entries, c, at, summary = true)
        val lines = text.trimEnd('\n').split('\n')
        assertTrue(listOf("MONEDA: CUP", "MONEDA: USD", "MONEDA: EUR").all { it in lines })
        val summary = lines.drop(lines.indexOf("RESUMEN FINAL"))
        assertEquals("-".repeat(50), summary[1])
        assertEquals("Total CUP:" + "51 CUP".padStart(40), summary[2])
        assertEquals("Total USD:" + "2 USD".padStart(40), summary[3])
        assertEquals("Total EUR:" + "5 EUR".padStart(40), summary[4])
        assertEquals("=".repeat(50), summary[5])
        assertTrue(text.endsWith("Documento generado por TASALO\n"))
    }

    @Test
    fun large_totals_are_grouped_in_the_receipt() {
        val c = counts("CUP:20000" to "100")
        val text = CashReceipt.build(listOf(cup), c, at, summary = false)
        assertTrue(text.contains("TOTAL CUP:" + "2 000 000 CUP".padStart(40)))
    }
}
