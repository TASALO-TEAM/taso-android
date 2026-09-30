package com.tasalo.android.data

import com.tasalo.android.data.local.CacheStore
import com.tasalo.android.data.parse.Parsers
import com.tasalo.android.domain.FuelSnapshot
import com.tasalo.android.domain.RatesSnapshot
import com.tasalo.android.domain.Snapshot
import com.tasalo.android.domain.YearMath
import com.tasalo.android.domain.YearState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Convierte el caché crudo en modelos de dominio. JSON corrupto = "sin datos" (plan §3.2). */
object Snapshots {

    fun from(raw: CacheStore.Raw, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Snapshot {
        val rates = run {
            val json = raw.latestJson
            val at = raw.latestAt
            if (json == null || at == null) return@run null
            Parsers.latest(json)?.let { RatesSnapshot(it, at) }
        }
        val fuel = run {
            val json = raw.fuelJson
            val at = raw.fuelAt
            if (json == null || at == null) return@run null
            Parsers.fuel(json)?.let { FuelSnapshot(it, at) }
        }
        return Snapshot(rates, fuel, year(raw, now, zone))
    }

    fun year(raw: CacheStore.Raw, now: Instant, zone: ZoneId = ZoneId.systemDefault()): YearState {
        val localNow = LocalDateTime.ofInstant(now, zone)
        val api = raw.yearJson?.let(Parsers::year)
        val at = raw.yearAt
        // Datos de la API de otro día ya no valen para el "quedan X días": se usa el cálculo local.
        val fresh = api != null && at != null && LocalDate.ofInstant(at, zone) == localNow.toLocalDate()
        val p = YearMath.compute(localNow, if (fresh) api else null)
        return YearState(
            year = p.year,
            percent = p.percent,
            daysLeft = p.daysLeft,
            daysPassed = p.daysPassed,
            weeksLeft = p.weeksLeft,
            quote = raw.quote,
            quoteDate = raw.quoteDate,
            fetchedAt = at,
        )
    }
}
