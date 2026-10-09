package com.tasalo.android.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** Los días del historial se agrupan por fecha local de Cuba (con su horario de verano), no por la del dispositivo. */
val CUBA_ZONE: ZoneId = ZoneId.of("America/Havana")

/** Hora de Cuba a la que se toma el precio de cada día. */
const val DAILY_HOUR = 7

/** Precio de un día (el de las 7:00 a. m. de Cuba o la lectura más cercana). */
data class PricePoint(val date: LocalDate, val rate: Double)

/** Lectura suelta de la API (hay una cada 15 minutos). */
data class RawPoint(val at: Instant, val rate: Double)

/** Una fila de la lista diaria; `rate == null` significa "sin dato" (no se interpola). */
data class DayRow(val date: LocalDate, val rate: Double?)

/** Resumen de un periodo: primer y último día con dato, máximo y mínimo. */
data class PeriodStats(val first: PricePoint, val last: PricePoint, val min: PricePoint, val max: PricePoint) {
    val delta: Double get() = last.rate - first.rate
    val pct: Double? get() = first.rate.takeIf { it != 0.0 }?.let { delta / it * 100 }
    val change: Change
        get() = when {
            delta > EPS -> Change.UP
            delta < -EPS -> Change.DOWN
            else -> Change.NEUTRAL
        }

    private companion object {
        const val EPS = 1e-9
    }
}

object PriceHistory {
    /** Un punto por fecha de Cuba: la lectura más cercana a las 7:00 de ese mismo día. Salida ordenada por fecha. */
    fun daily(raw: List<RawPoint>, zone: ZoneId = CUBA_ZONE): List<PricePoint> =
        raw.groupBy { it.at.atZone(zone).toLocalDate() }
            .map { (date, list) ->
                val target = date.atTime(DAILY_HOUR, 0).atZone(zone).toInstant()
                val best = list.minBy { abs(Duration.between(target, it.at).seconds) }
                PricePoint(date, best.rate)
            }
            .sortedBy { it.date }

    /** Puntos con fecha entre `from` y `to` (ambas incluidas). */
    fun between(points: List<PricePoint>, from: LocalDate, to: LocalDate): List<PricePoint> =
        points.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }

    /** Últimos `days` días hasta `today` (incluido). */
    fun lastDays(points: List<PricePoint>, days: Int, today: LocalDate): List<PricePoint> =
        between(points, today.minusDays((days - 1).toLong()), today)

    fun stats(points: List<PricePoint>): PeriodStats? {
        if (points.isEmpty()) return null
        val sorted = points.sortedBy { it.date }
        return PeriodStats(
            first = sorted.first(),
            last = sorted.last(),
            min = sorted.minBy { it.rate },
            max = sorted.maxBy { it.rate },
        )
    }

    /** Hoy y `days - 1` días hacia atrás; los días sin lectura salen con `rate = null`. */
    fun rows(points: List<PricePoint>, days: Int, today: LocalDate): List<DayRow> {
        val byDate = points.associate { it.date to it.rate }
        return (0 until days).map { i ->
            val date = today.minusDays(i.toLong())
            DayRow(date, byDate[date])
        }
    }

    /** Tendencia de una serie (primer vs. último punto); sirve para el color del fondo de la tarjeta. */
    fun trend(points: List<PricePoint>): Change = stats(points)?.change ?: Change.NEUTRAL

    /** Texto simple para guardar series en el móvil: `clave,fecha,precio` por línea. */
    fun encode(series: Map<String, List<PricePoint>>): String =
        series.entries.joinToString("\n") { (key, points) ->
            points.joinToString("\n") { "$key,${it.date},${it.rate}" }
        }.trim()

    fun decode(text: String): Map<String, List<PricePoint>> {
        val out = LinkedHashMap<String, MutableList<PricePoint>>()
        for (line in text.lineSequence()) {
            val parts = line.split(',')
            if (parts.size != 3) continue
            val date = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: continue
            val rate = parts[2].toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
            out.getOrPut(parts[0]) { mutableListOf() }.add(PricePoint(date, rate))
        }
        return out.mapValues { (_, list) -> list.sortedBy { it.date } }
    }
}
