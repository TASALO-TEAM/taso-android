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

    fun isStale(fetchedAt: Instant?, now: Instant, minutes: Long = 60): Boolean =
        fetchedAt == null || Duration.between(fetchedAt, now).toMinutes() >= minutes
}
