package com.tasalo.android.util

import com.tasalo.android.domain.Change
import com.tasalo.android.domain.FuelPrice
import com.tasalo.android.domain.Rate
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    @Test
    fun rate_uses_up_to_two_decimals_without_trailing_zeros() {
        assertEquals("750", Format.rate(750.0))
        assertEquals("857,5", Format.rate(857.5))
        assertEquals("485,07", Format.rate(485.07))
        assertEquals("0,23", Format.rate(0.2306))
        assertEquals("5100", Format.rate(5100.0))
    }

    @Test
    fun percent_has_one_decimal() {
        assertEquals("74,4%", Format.percent(74.42717867837075))
    }

    @Test
    fun change_shows_arrow_delta_and_signed_percent() {
        val up = Rate("MLC", 500.0, null, null, Change.UP, 485.07)
        assertEquals("▲ 14,93 (+3,1%)", Format.change(up))
        val down = Rate("USD", 749.0, null, null, Change.DOWN, 750.0)
        assertEquals("▼ 1 (-0,1%)", Format.change(down))
    }

    @Test
    fun change_neutral_or_without_prev_rate_is_just_the_arrow() {
        assertEquals("—", Format.change(Rate("USD", 750.0, null, null, Change.NEUTRAL, 750.0)))
        assertEquals("▲", Format.change(Rate("USD", 750.0, null, null, Change.UP, null)))
    }

    @Test
    fun change_has_accessible_description() {
        assertEquals("sube", Format.changeDescription(Change.UP))
        assertEquals("baja", Format.changeDescription(Change.DOWN))
        assertEquals("sin cambios", Format.changeDescription(Change.NEUTRAL))
    }

    @Test
    fun fuel_range_shows_min_max_only_when_different() {
        assertEquals("2500–5100", Format.fuelRange(FuelPrice("B-94", 2500.0, 5100.0, Change.NEUTRAL)))
        assertEquals("3000", Format.fuelRange(FuelPrice("B-90", 3000.0, 3000.0, Change.NEUTRAL)))
    }

    @Test
    fun relative_time() {
        val now = Instant.parse("2026-09-29T20:00:00Z")
        assertEquals("ahora", Format.relative(now.minusSeconds(30), now))
        assertEquals("hace 3 min", Format.relative(now.minusSeconds(3 * 60), now))
        assertEquals("hace 2 h", Format.relative(now.minusSeconds(2 * 3600), now))
        assertEquals("28/09/2026", Format.relative(now.minusSeconds(30 * 3600), now, ZoneOffset.UTC))
    }

    @Test
    fun absolute_time_shows_hour_today_and_date_otherwise() {
        val now = Instant.parse("2026-09-29T20:00:00Z")
        assertEquals("19:32", Format.absolute(Instant.parse("2026-09-29T19:32:00Z"), now, ZoneOffset.UTC))
        assertEquals("28/09 23:05", Format.absolute(Instant.parse("2026-09-28T23:05:00Z"), now, ZoneOffset.UTC))
    }

    @Test
    fun stale_after_sixty_minutes_or_without_data() {
        val now = Instant.parse("2026-09-29T20:00:00Z")
        assertFalse(Format.isStale(now.minusSeconds(59 * 60), now))
        assertTrue(Format.isStale(now.minusSeconds(60 * 60), now))
        assertTrue(Format.isStale(null, now))
    }
}

class UrlValidatorTest {
    @Test
    fun accepts_https_and_adds_trailing_slash() {
        assertEquals("https://tasalo.duckdns.org/", UrlValidator.normalize("https://tasalo.duckdns.org"))
        assertEquals("https://a.example/api/", UrlValidator.normalize("  https://a.example/api  "))
    }

    @Test
    fun rejects_http_other_schemes_and_empty_hosts() {
        assertNull(UrlValidator.normalize("http://tasalo.duckdns.org:8040"))
        assertNull(UrlValidator.normalize("ftp://x.example"))
        assertNull(UrlValidator.normalize("https://"))
        assertNull(UrlValidator.normalize("tasalo.duckdns.org"))
        assertNull(UrlValidator.normalize(""))
    }
}
