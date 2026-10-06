package com.tasalo.android.domain

/**
 * Conversión entre monedas para la calculadora, usando el CUP como pivote.
 * Cada tasa de la app es cuántos CUP vale una unidad de esa moneda (1 USD = 520 CUP), así que cualquier par
 * se resuelve con una sola fórmula: `monto * tasa(origen) / tasa(destino)`.
 */
object Converter {
    const val CUP = "CUP"

    /** Tabla moneda → CUP por unidad de una fuente. El CUP siempre está (vale 1) y se descartan tasas no válidas. */
    fun table(rates: List<Rate>): Map<String, Double> = buildMap {
        put(CUP, 1.0)
        rates.forEach { if (it.currency !in Currencies.IGNORED && it.rate > 0.0) put(it.currency, it.rate) }
    }

    /** CUP primero y después el orden habitual de la app (EUR, USD, MLC...). */
    fun codes(table: Map<String, Double>): List<String> =
        table.keys.sortedWith(compareBy<String> { if (it == CUP) 0 else 1 }.thenBy { Currencies.orderIndex(it) }.thenBy { it })

    /** Null si alguna de las dos monedas no está en la tabla. */
    fun convert(amount: Double, from: String, to: String, table: Map<String, Double>): Double? {
        val f = table[from] ?: return null
        val t = table[to] ?: return null
        return amount * f / t
    }
}
