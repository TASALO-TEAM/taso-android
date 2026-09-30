package com.tasalo.android.data

import com.tasalo.android.data.local.CacheStore
import com.tasalo.android.domain.QuoteRule
import com.tasalo.android.domain.YearApi
import com.tasalo.android.domain.YearMath
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YearTest {

    @Test
    fun local_progress_matches_the_api_day_count() {
        val p = YearMath.local(LocalDateTime.of(2026, 9, 29, 0, 0))
        assertEquals(2026, p.year)
        assertEquals(272, p.daysPassed)
        assertEquals(93, p.daysLeft)
        assertEquals(14, p.weeksLeft)
        assertEquals(271.0 / 365 * 100, p.percent, 1e-9)
    }

    @Test
    fun leap_year_has_366_days() {
        val p = YearMath.local(LocalDateTime.of(2028, 2, 29, 12, 0))
        assertEquals(60, p.daysPassed)
        assertEquals(306, p.daysLeft)
        assertEquals(59.5 / 366 * 100, p.percent, 1e-9)
    }

    @Test
    fun last_day_of_the_year() {
        val p = YearMath.local(LocalDateTime.of(2026, 12, 31, 23, 0))
        assertEquals(0, p.daysLeft)
        assertEquals(0, p.weeksLeft)
    }

    @Test
    fun api_values_are_preferred_and_missing_ones_fall_back_to_local() {
        val now = LocalDateTime.of(2026, 9, 29, 10, 0)
        val fromApi = YearMath.compute(now, YearApi(2026, 74.4, 93, "x"))
        assertEquals(74.4, fromApi.percent, 0.0)
        assertEquals(93, fromApi.daysLeft)
        assertEquals(272, fromApi.daysPassed)

        val partial = YearMath.compute(now, YearApi(null, null, null, null))
        assertEquals(YearMath.local(now), partial)
        assertEquals(YearMath.local(now), YearMath.compute(now, null))
    }

    @Test
    fun quote_of_today_is_never_replaced() {
        val today = LocalDate.of(2026, 9, 29)
        assertEquals("A" to today, QuoteRule.resolve("A", today, "B", today))
    }

    @Test
    fun quote_changes_on_a_new_day_and_survives_failed_fetches() {
        val today = LocalDate.of(2026, 9, 29)
        val yesterday = today.minusDays(1)
        assertEquals("B" to today, QuoteRule.resolve("A", yesterday, "B", today))
        assertEquals("A" to yesterday, QuoteRule.resolve("A", yesterday, null, today))
        assertEquals(null to null, QuoteRule.resolve(null, null, null, today))
    }

    @Test
    fun year_block_is_never_empty_even_without_cache() {
        val y = Snapshots.year(CacheStore.Raw(), Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)
        assertEquals(2026, y.year)
        assertEquals(93, y.daysLeft)
        assertNull(y.quote)
    }

    @Test
    fun api_data_from_a_previous_day_is_ignored() {
        val raw = CacheStore.Raw(
            yearJson = """{"ok":true,"progress":{"year":2026,"percent":10.0,"days_left":300}}""",
            yearAt = Instant.parse("2026-09-28T12:00:00Z"),
        )
        val y = Snapshots.year(raw, Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)
        assertEquals(93, y.daysLeft)
    }

    @Test
    fun fresh_api_data_wins() {
        val raw = CacheStore.Raw(
            yearJson = """{"ok":true,"progress":{"year":2026,"percent":74.5,"days_left":93}}""",
            yearAt = Instant.parse("2026-09-29T08:00:00Z"),
        )
        val y = Snapshots.year(raw, Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)
        assertEquals(74.5, y.percent, 0.0)
    }

    @Test
    fun corrupt_cache_means_no_data() {
        val raw = CacheStore.Raw(latestJson = "{roto", latestAt = Instant.now(), fuelJson = "[]", fuelAt = Instant.now())
        val snap = Snapshots.from(raw, Instant.now())
        assertNull(snap.rates)
        assertNull(snap.fuel)
    }
}
