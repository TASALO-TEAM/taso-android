package com.tasalo.android.util

import com.tasalo.android.domain.Change
import com.tasalo.android.domain.FuelPrice
import com.tasalo.android.domain.Rate
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formato es-CU: coma decimal, sin separador de miles (plan §6). */
object Format {
    private val locale = Locale("es", "CU")

    private fun decimal(pattern: String): DecimalFormat =
        DecimalFormat(
            pattern,
            DecimalFormatSymbols(locale).apply {
                decimalSeparator = ','
                groupingSeparator = '.'
            },
        ).apply {
            roundingMode = RoundingMode.HALF_UP
            isGroupingUsed = false
        }

    /** 0–2 decimales, sin ceros sobrantes: 750, 857,5, 485,07. */
    fun rate(value: Double): String = decimal("0.##").format(value)

    fun percent(value: Double): String = decimal("0.0").format(value) + "%"

    /**
     * Importes de la calculadora: separador de miles (espacio duro) y decimales según el tamaño, para que una
     * cantidad pequeña en cripto (0,00038 BTC) no se muestre como 0: >= 1 → hasta 2; >= 0,01 → hasta 4; menos → hasta 8.
     */
    fun amount(value: Double): String {
        val abs = kotlin.math.abs(value)
        val pattern = when {
            abs >= 1.0 || abs == 0.0 -> "#,##0.##"
            abs >= 0.01 -> "#,##0.####"
            else -> "#,##0.########"
        }
        return DecimalFormat(
            pattern,
            DecimalFormatSymbols(locale).apply {
                decimalSeparator = ','
                groupingSeparator = '\u00A0'
            },
        ).apply { roundingMode = RoundingMode.HALF_UP }.format(value)
    }

    /**
     * Lo que la persona teclea en el campo de monto: acepta coma o punto como decimal y devuelve null si está
     * vacío o no es un número válido (por ejemplo "1.2.3").
     */
    fun parseAmount(text: String): Double? {
        val clean = text.trim().replace(',', '.')
        if (clean.isEmpty() || clean.count { it == '.' } > 1) return null
        return clean.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    }

    private fun signedPercent(value: Double): String {
        val body = decimal("0.0").format(kotlin.math.abs(value))
        return (if (value < 0) "-" else "+") + body + "%"
    }

    fun arrow(change: Change): String = when (change) {
        Change.UP -> "▲"
        Change.DOWN -> "▼"
        Change.NEUTRAL -> "—"
    }

    /** ▲ 2,5 (+0,3%) — el % solo aparece si hay prev_rate. */
    fun change(r: Rate): String {
        val symbol = arrow(r.change)
        if (r.change == Change.NEUTRAL) return symbol
        val prev = r.prevRate ?: return symbol
        val delta = kotlin.math.abs(r.rate - prev)
        val pct = r.changePct
        return if (pct != null) "$symbol ${rate(delta)} (${signedPercent(pct)})" else "$symbol ${rate(delta)}"
    }

    /** Texto para lectores de pantalla: el cambio nunca se indica solo con color. */
    fun changeDescription(change: Change): String = when (change) {
        Change.UP -> "sube"
        Change.DOWN -> "baja"
        Change.NEUTRAL -> "sin cambios"
    }

    /** Combustible: `min–max` si son distintos; si no, un solo valor. */
    fun fuelRange(price: FuelPrice): String {
        val min = price.min
        val max = price.max
        return when {
            min != null && max != null && min != max -> "${rate(min)}–${rate(max)}"
            max != null -> rate(max)
            min != null -> rate(min)
            else -> "—"
        }
    }

    /** ahora / hace 3 min / hace 2 h / dd/MM/yyyy (>24 h). */
    fun relative(then: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = Duration.between(then, now)
        return when {
            d.isNegative || d.toMinutes() < 1 -> "ahora"
            d.toMinutes() < 60 -> "hace ${d.toMinutes()} min"
            d.toHours() < 24 -> "hace ${d.toHours()} h"
            else -> DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(zone).format(then)
        }
    }

    /** Hora del dato: "14:32" si es de hoy, "30/09 14:32" si no. No miente cuando el widget queda congelado. */
    fun absolute(then: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = then.atZone(zone)
        val sameDay = t.toLocalDate() == now.atZone(zone).toLocalDate()
        return DateTimeFormatter.ofPattern(if (sameDay) "HH:mm" else "dd/MM HH:mm").format(t)
    }

    fun isStale(fetchedAt: Instant?, now: Instant, minutes: Long = 60): Boolean =
        fetchedAt == null || Duration.between(fetchedAt, now).toMinutes() >= minutes
}
