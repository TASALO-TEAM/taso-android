package com.tasalo.android.domain

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Monedas de la calculadora de efectivo, con los billetes que circulan de cada una (de menor a mayor). */
enum class CashCurrency(val code: String, val denominations: List<Long>) {
    CUP("CUP", listOf(1L, 3L, 5L, 10L, 20L, 50L, 100L, 200L, 500L, 1000L, 2000L, 5000L, 10000L, 20000L)),
    USD("USD", listOf(1L, 2L, 5L, 10L, 20L, 50L, 100L)),
    EUR("EUR", listOf(5L, 10L, 20L, 50L, 100L, 200L, 500L)),
}

/**
 * Lógica pura de la calculadora de efectivo (sin Android). Las cantidades se guardan como texto, por moneda y
 * billete, en un mapa `"CUP:20000" -> "5"`: así la casilla vacía se distingue de un 0 escrito y todo cabe en un
 * `rememberSaveable`.
 */
object CashCalc {
    const val MAX_DIGITS = 6

    fun key(currency: CashCurrency, denomination: Long): String = "${currency.code}:$denomination"

    /** Solo dígitos ASCII y como máximo [MAX_DIGITS]. */
    fun sanitize(input: String): String = input.filter { it in '0'..'9' }.take(MAX_DIGITS)

    /** Vacío o ilegible = 0. */
    fun count(text: String?): Long = text?.let(::sanitize)?.toLongOrNull() ?: 0L

    fun rowValue(denomination: Long, text: String?): Long = denomination * count(text)

    fun total(currency: CashCurrency, counts: Map<String, String>): Long =
        currency.denominations.sumOf { rowValue(it, counts[key(currency, it)]) }

    /** Para `rememberSaveable`: `"CUP:20000=5;USD:1=2"`. Las casillas vacías no se guardan. */
    fun encode(counts: Map<String, String>): String =
        counts.filterValues { it.isNotEmpty() }.entries.joinToString(";") { "${it.key}=${it.value}" }

    fun decode(saved: String): Map<String, String> =
        saved.split(';').mapNotNull { part ->
            val i = part.lastIndexOf('=')
            if (i <= 0) null else part.substring(0, i) to sanitize(part.substring(i + 1))
        }.filter { it.second.isNotEmpty() }.toMap()
}

/** Texto plano del conteo, con aspecto de recibo (50 columnas) para guardarlo en un `.txt`. */
object CashReceipt {
    const val WIDTH = 50
    private val EQ = "=".repeat(WIDTH)
    private val DASH = "-".repeat(WIDTH)

    /** Miles con espacio: 20 000. */
    fun group(value: Long): String = value.toString().reversed().chunked(3).joinToString(" ").reversed()

    private fun center(text: String): String = " ".repeat(maxOf(0, (WIDTH - text.length) / 2)) + text

    private fun line(left: String, right: String): String = left + right.padStart(maxOf(1, WIDTH - left.length))

    private fun row(bill: String, quantity: String, amount: String): String =
        bill.padEnd(14) + quantity.padStart(12) + amount.padStart(24)

    private fun block(currency: CashCurrency, counts: Map<String, String>): List<String> =
        buildList {
            add("MONEDA: ${currency.code}")
            add(DASH)
            add(row("BILLETE", "CANTIDAD", "IMPORTE"))
            add(DASH)
            for (d in currency.denominations) {
                val q = CashCalc.count(counts[CashCalc.key(currency, d)])
                add(row(group(d), group(q), group(d * q)))
            }
            add(DASH)
            add(line("TOTAL ${currency.code}:", "${group(CashCalc.total(currency, counts))} ${currency.code}"))
        }

    /**
     * Una moneda (individual) o varias; con [summary] se añade el «RESUMEN FINAL» con el total de cada una.
     * [now] se recibe de fuera para poder probarlo con una fecha fija.
     */
    fun build(
        currencies: List<CashCurrency>,
        counts: Map<String, String>,
        now: LocalDateTime,
        summary: Boolean,
    ): String {
        val out = mutableListOf(
            EQ,
            center("TASALO"),
            center("CONTEO DE EFECTIVO"),
            EQ,
            "Fecha: ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT))}",
            "Hora: ${now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))}",
            DASH,
        )
        currencies.forEach { currency ->
            out += ""
            out += block(currency, counts)
        }
        out += ""
        out += EQ
        if (summary) {
            out += "RESUMEN FINAL"
            out += DASH
            currencies.forEach {
                out += line("Total ${it.code}:", "${group(CashCalc.total(it, counts))} ${it.code}")
            }
            out += EQ
        }
        out += ""
        out += "Documento generado por TASALO"
        return out.joinToString("\n") + "\n"
    }
}
