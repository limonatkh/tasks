package com.imad.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class TargetsTest {
    @Test fun weeksStartOnFriday() {
        assertEquals(LocalDate.of(2026, 10, 23), Targets.weekStart(LocalDate.of(2026, 10, 29)))
        assertEquals(LocalDate.of(2026, 10, 30), Targets.weekStart(LocalDate.of(2026, 10, 30)))
    }

    @Test fun monthWeeksAreItsFridays() {
        val w = Targets.weeksOf(YearMonth.of(2026, 11))
        assertEquals(4, w.size)
        assertEquals(LocalDate.of(2026, 11, 6), w.first())
        assertTrue(w.all { it.dayOfWeek == DayOfWeek.FRIDAY })
    }

    @Test fun weekDoneAddsTheDays() {
        val start = LocalDate.of(2026, 10, 30)
        var all = Targets.put(emptyMap(), Targets.weekKey(start), "pod", Target("حلقة ٣ و٤", 2))
        all = Targets.put(all, Targets.dayKey(start.plusDays(1)), "pod", Target("تصوير ٣", 1, 1))
        all = Targets.put(all, Targets.dayKey(start.plusDays(3)), "pod", Target("تصوير ٤", 1, 0))
        all = Targets.put(all, Targets.dayKey(start.plusDays(8)), "pod", Target("خارج الأسبوع", 1, 1))
        assertEquals(1, Targets.weekDone(all, start, "pod"))
        assertEquals(2, Targets.weekSpread(all, start, "pod"))
    }

    @Test fun emptyTargetsAreRemoved() {
        val all = Targets.put(Targets.put(emptyMap(), "w:x", "a", Target("x", 1)), "w:x", "a", Target())
        assertTrue(all.isEmpty())
    }
}
