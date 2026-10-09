package com.tasalo.android.data

import com.tasalo.android.data.parse.HistoryParsers
import com.tasalo.android.domain.Change
import com.tasalo.android.domain.PriceHistory
import com.tasalo.android.domain.PricePoint
import com.tasalo.android.domain.RawPoint
import com.tasalo.android.domain.Source
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {
    private fun raw(iso: String, rate: Double) = RawPoint(Instant.parse(iso), rate)

    @Test
    fun elPrecioDelDiaEsElMasCercanoALas7DeCuba() {
        // En octubre Cuba está en horario de verano (UTC-4): las 7:00 son las 11:00 UTC.
        val points = PriceHistory.daily(
            listOf(
                raw("2026-10-08T10:00:00Z", 500.0),
                raw("2026-10-08T11:20:00Z", 510.0),
                raw("2026-10-08T20:00:00Z", 520.0),
            ),
        )
        assertEquals(listOf(PricePoint(LocalDate.of(2026, 10, 8), 510.0)), points)
    }

    @Test
    fun losDiasSeAgrupanPorFechaLocalDeCuba() {
        // 03:30 UTC del 8 son las 23:30 del 7 en Cuba: cuenta para el día 7, no para el 8.
        val points = PriceHistory.daily(listOf(raw("2026-10-08T03:30:00Z", 505.0), raw("2026-10-08T11:00:00Z", 510.0)))
        assertEquals(listOf(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 8)), points.map { it.date })
        assertEquals(listOf(505.0, 510.0), points.map { it.rate })
    }

    @Test
    fun enInviernoLas7SonLas12UTC() {
        // Enero: UTC-5. Entre 11:00Z (6:00 Cuba) y 12:10Z (7:10 Cuba) gana la segunda.
        val points = PriceHistory.daily(listOf(raw("2026-01-15T11:00:00Z", 400.0), raw("2026-01-15T12:10:00Z", 410.0)))
        assertEquals(410.0, points.single().rate, 0.0)
    }

    @Test
    fun losDiasSinLecturaSalenSinDatoYNoSeInterpolan() {
        val today = LocalDate.of(2026, 10, 8)
        val points = listOf(PricePoint(today, 520.0), PricePoint(today.minusDays(2), 515.0))
        val rows = PriceHistory.rows(points, 4, today)
        assertEquals(listOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(3)), rows.map { it.date })
        assertEquals(listOf(520.0, null, 515.0, null), rows.map { it.rate })
    }

    @Test
    fun estadisticasDelPeriodo() {
        val d = LocalDate.of(2026, 10, 1)
        val stats = PriceHistory.stats(
            listOf(PricePoint(d, 500.0), PricePoint(d.plusDays(1), 530.0), PricePoint(d.plusDays(2), 490.0), PricePoint(d.plusDays(3), 520.0)),
        )!!
        assertEquals(500.0, stats.first.rate, 0.0)
        assertEquals(520.0, stats.last.rate, 0.0)
        assertEquals(530.0, stats.max.rate, 0.0)
        assertEquals(490.0, stats.min.rate, 0.0)
        assertEquals(20.0, stats.delta, 1e-9)
        assertEquals(4.0, stats.pct!!, 1e-9)
        assertEquals(Change.UP, stats.change)
    }

    @Test
    fun historialCortoNoRompeNada() {
        assertNull(PriceHistory.stats(emptyList()))
        val one = PriceHistory.stats(listOf(PricePoint(LocalDate.of(2026, 10, 8), 535.0)))!!
        assertEquals(Change.NEUTRAL, one.change)
        assertEquals(0.0, one.delta, 0.0)
        val rows = PriceHistory.rows(emptyList(), 7, LocalDate.of(2026, 10, 8))
        assertEquals(7, rows.size)
        assertTrue(rows.all { it.rate == null })
    }

    @Test
    fun guardarYLeerSeriesConservaLosDatos() {
        val d = LocalDate.of(2026, 10, 1)
        val series = mapOf("ELTOQUE:USD" to listOf(PricePoint(d, 500.5), PricePoint(d.plusDays(1), 501.0)))
        assertEquals(series, PriceHistory.decode(PriceHistory.encode(series)))
        assertTrue(PriceHistory.decode("basura\n,,\nA,no-fecha,3").isEmpty())
    }

    @Test
    fun resumenYSerieDiariaSeLeenConTolerancia() {
        val summary = HistoryParsers.summary(
            """{"ok":true,"tz":"America/Havana","data":{"eltoque":{"USD":[{"date":"2026-10-07","rate":515},{"date":"2026-10-08","rate":"520,5"},{"date":"mala","rate":1}]},"qvapay":{"CUP":[{"date":"2026-10-08","rate":535}]},"otra":{}}}""",
        )!!
        assertEquals(listOf(515.0, 520.5), summary["ELTOQUE:USD"]!!.map { it.rate })
        assertEquals(1, summary["QVAPAY:CUP"]!!.size)

        val daily = HistoryParsers.daily("""{"ok":true,"data":[{"date":"2026-10-08","rate":520},{"date":"2026-10-07","rate":null}]}""")
        assertEquals(1, daily!!.size)
        assertNull(HistoryParsers.daily("no es json"))
        assertNull(HistoryParsers.summary("""{"ok":false}"""))
    }

    @Test
    fun lecturasSueltasDelEndpointActual() {
        val text = """{"ok":true,"count":3,"data":[
            {"source":"qvapay","currency":"CUP","buy_rate":530,"sell_rate":540,"fetched_at":"2026-10-08T11:00:00+00:00"},
            {"source":"qvapay","currency":"CUP","buy_rate":null,"sell_rate":null,"fetched_at":"2026-10-08T11:15:00+00:00"},
            {"source":"qvapay","currency":"CUP","buy_rate":null,"sell_rate":541,"fetched_at":"2026-10-08T11:30:00"}]}"""
        val qva = HistoryParsers.raw(text, Source.QVAPAY)!!
        assertEquals(2, qva.size)
        assertEquals(535.0, qva[0].rate, 0.0)
        assertEquals(541.0, qva[1].rate, 0.0)
        val eltoque = HistoryParsers.raw(text, Source.ELTOQUE)!!
        assertEquals(540.0, eltoque[0].rate, 0.0)
        assertNotNull(PriceHistory.daily(eltoque))
    }
}
