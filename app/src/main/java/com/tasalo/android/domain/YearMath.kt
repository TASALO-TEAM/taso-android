package com.tasalo.android.domain

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.ceil

data class YearProgress(
    val year: Int,
    val percent: Double,
    val daysLeft: Int,
    val daysPassed: Int,
    val weeksLeft: Int,
)

/** Datos de progreso del año que aporta la API (todos opcionales). */
data class YearApi(
    val year: Int?,
    val percent: Double?,
    val daysLeft: Int?,
    val quote: String?,
)

object YearMath {

    /** Cálculo 100 % local: el bloque del año nunca queda vacío (plan §2.8). */
    fun local(now: LocalDateTime): YearProgress {
        val date = now.toLocalDate()
        val total = date.lengthOfYear()
        val dayOfYear = date.dayOfYear
        val elapsed = (dayOfYear - 1) + now.toLocalTime().toSecondOfDay() / 86_400.0
        val daysLeft = total - dayOfYear
        return YearProgress(
            year = date.year,
            percent = (elapsed / total * 100).coerceIn(0.0, 100.0),
            daysLeft = daysLeft,
            daysPassed = dayOfYear,
            weeksLeft = ceil(daysLeft / 7.0).toInt(),
        )
    }

    /** Prefiere percent/days_left de la API cuando existen (coincide con el comando /y del bot). */
    fun compute(now: LocalDateTime, api: YearApi?): YearProgress {
        val base = local(now)
        if (api == null) return base
        val total = now.toLocalDate().lengthOfYear()
        val daysLeft = api.daysLeft?.coerceIn(0, total) ?: base.daysLeft
        return base.copy(
            percent = api.percent?.coerceIn(0.0, 100.0) ?: base.percent,
            daysLeft = daysLeft,
            daysPassed = total - daysLeft,
            weeksLeft = ceil(daysLeft / 7.0).toInt(),
        )
    }
}

/** Frase del día cacheada por fecha (plan §2.7): el servidor puede devolver otra en cada llamada. */
object QuoteRule {
    fun resolve(
        storedQuote: String?,
        storedDate: LocalDate?,
        fetchedQuote: String?,
        today: LocalDate,
    ): Pair<String?, LocalDate?> = when {
        storedQuote != null && storedDate == today -> storedQuote to storedDate
        fetchedQuote != null -> fetchedQuote to today
        else -> storedQuote to storedDate
    }
}
