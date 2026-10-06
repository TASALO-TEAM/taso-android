package com.tasalo.android.ui.calculator

import com.tasalo.android.domain.Converter
import com.tasalo.android.util.Format

/** Texto de la "factura" de la calculadora, listo para pegar en WhatsApp o Telegram. */
object Receipt {
    /**
     * ```
     * 20 USD · El Toque
     * = 10 400 CUP
     * = 17,5 EUR
     * ```
     * [rows] ya viene en el orden en que se muestra en pantalla; el origen no se repite en la lista.
     */
    fun build(amount: Double, from: String, sourceTitle: String, table: Map<String, Double>, rows: List<String>): String {
        val lines = rows.mapNotNull { code ->
            Converter.convert(amount, from, code, table)?.let { "= ${Format.amount(it)} $code" }
        }
        return buildString {
            append(Format.amount(amount)).append(' ').append(from).append(" · ").append(sourceTitle)
            lines.forEach { append('\n').append(it) }
            append("\nTasas con TASALO")
        }
    }
}
