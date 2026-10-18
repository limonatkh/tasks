package com.imad.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PrayerTest {
    private val zone = ZoneId.of("Asia/Gaza")
    private val table = Prayer.parse(Prayer.SEED)

    @Test fun seedHasTheWholeSheet() {
        assertEquals(30, table.size)
        val d = table.first { it.date == LocalDate.of(2026, 10, 12) }
        assertEquals(4 * 60 + 45, d.fajr)
        assertEquals(6 * 60 + 43, d.sunrise)
        assertEquals(19 * 60 + 33, d.isha)
    }

    @Test fun pastedLinesWithOrWithoutSecondFajr() {
        val a = Prayer.parse("2026-12-01 04:20 04:50 06:20 11:35 14:25 16:45 18:05\nnot a line\n2026-12-02 04:21 06:21 11:35 14:25 16:45 18:05")
        assertEquals(2, a.size)
        assertEquals(6 * 60 + 20, a[0].sunrise)
        assertEquals(6 * 60 + 21, a[1].sunrise)
    }

    @Test fun calculationIsCloseToTheSheet() {
        // Without any correction the Egyptian method should land within a few minutes of the Gaza sheet.
        val d = LocalDate.of(2026, 10, 18)
        val c = Prayer.calculate(d, zone)
        val t = table.first { it.date == d }
        // The sheet prints two fajr times 30 minutes apart; the calculation lands near the second one, so fajr is only checked loosely.
        assertTrue(kotlin.math.abs(c.fajr - t.fajr) <= 36)
        for (sel in listOf<(PrayerDay) -> Int>({ it.dhuhr }, { it.asr }, { it.maghrib }, { it.isha }))
            assertTrue("calc ${sel(c)} sheet ${sel(t)}", kotlin.math.abs(sel(c) - sel(t)) <= 6)
    }

    @Test fun afterTheSheetEndsTimesAreApproximatedAndCorrected() {
        val d = LocalDate.of(2026, 11, 11)
        val p = Prayer.day(d, table, zone)
        assertEquals("approx", p.source)
        val last = table.last()
        assertTrue(kotlin.math.abs(p.maghrib - last.maghrib) <= 3)
        assertTrue(kotlin.math.abs(p.fajr - last.fajr) <= 3)
    }

    @Test fun periodsEndAtSleep() {
        val p = table.first()
        val ps = Prayer.periods(p, 23 * 60)
        assertEquals(5, ps.size)
        assertEquals(p.fajr, ps[0].start); assertEquals(p.dhuhr, ps[0].end)
        assertEquals(23 * 60, ps.last().end)
    }
}
