package com.tasalo.android.domain

/**
 * Conversión entre monedas para la calculadora, usando un pivote.
 * En El Toque, BCC y CADECA cada tasa es cuántos CUP vale una unidad de esa moneda (1 USD = 520 CUP), así que
 * cualquier par se resuelve con una sola fórmula: `monto * tasa(origen) / tasa(destino)` con el CUP como pivote.
 * En QvaPay cada valor es cuánto de un método de pago se obtiene por 1 USD; el pivote es el USD y la tabla guarda
 * el valor en USD de una unidad de cada método (1 / tasa), de modo que la misma fórmula sirve.
 */
object Converter {
    const val CUP = "CUP"
    const val USD = "USD"

    /**
     * Tabla código -> valor de una unidad en el pivote de la fuente. El pivote siempre está (vale 1) y se descartan
     * tasas no válidas. En QvaPay, "CUP" es un método de pago más (Banco CUP) y el pivote es el USD.
     */
    fun table(rates: List<Rate>, source: Source = Source.ELTOQUE): Map<String, Double> =
        if (source == Source.QVAPAY) qvapayTable(rates) else cupTable(rates)

    private fun cupTable(rates: List<Rate>): Map<String, Double> = buildMap {
        put(CUP, 1.0)
        rates.forEach { if (it.currency !in Currencies.IGNORED && it.rate > 0.0) put(it.currency, it.rate) }
    }

    private fun qvapayTable(rates: List<Rate>): Map<String, Double> = buildMap {
        put(USD, 1.0)
        rates.forEach { if (it.currency != USD && it.rate > 0.0) put(it.currency, 1.0 / it.rate) }
    }

    /** Pivote primero y después el orden habitual de la fuente (EUR, USD, MLC... o los métodos de QvaPay). */
    fun codes(table: Map<String, Double>, source: Source = Source.ELTOQUE): List<String> =
        if (source == Source.QVAPAY) {
            table.keys.sortedWith(
                compareBy<String> { if (it == USD) 0 else 1 }.thenBy { QvaPay.orderIndex(it) }.thenBy { it },
            )
        } else {
            table.keys.sortedWith(
                compareBy<String> { if (it == CUP) 0 else 1 }.thenBy { Currencies.orderIndex(it) }.thenBy { it },
            )
        }

    /** Null si alguna de las dos monedas no está en la tabla. */
    fun convert(amount: Double, from: String, to: String, table: Map<String, Double>): Double? {
        val f = table[from] ?: return null
        val t = table[to] ?: return null
        return amount * f / t
    }
}
