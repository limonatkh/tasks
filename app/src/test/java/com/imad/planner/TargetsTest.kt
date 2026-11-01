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

    @Test fun routineWeekIsCopiedOnceWithItsDays() {
        val prev = LocalDate.of(2026, 10, 23)
        val start = prev.plusWeeks(1)
        var all = Targets.put(emptyMap(), Targets.weekKey(prev), "pod", Target("حلقتين", 2, 2, sessions = 4, minutes = 90))
        all = Targets.put(all, Targets.dayKey(prev.plusDays(1)), "pod", Target("تصوير", 1, 1, sessions = 2))
        val copied = Targets.copyWeek(all, start, "pod")
        val w = copied[Targets.weekKey(start)]!!["pod"]!!
        assertEquals(0, w.done); assertEquals(4, w.sessions); assertTrue(w.auto)
        assertEquals(2, copied[Targets.dayKey(start.plusDays(1))]!!["pod"]!!.sessions)
        // An edited week is not overwritten again.
        val edited = Targets.put(copied, Targets.weekKey(start), "pod", Target("حلقة وحدة", 1, sessions = 2))
        assertEquals("حلقة وحدة", Targets.copyWeek(edited, start, "pod")[Targets.weekKey(start)]!!["pod"]!!.text)
    }

    @Test fun removingClearsTheWeekAndItsDays() {
        val start = LocalDate.of(2026, 10, 30)
        var all = Targets.put(emptyMap(), Targets.weekKey(start), "pod", Target(keep = true))
        all = Targets.put(all, Targets.dayKey(start.plusDays(2)), "pod", Target(sessions = 1))
        assertTrue("pod" in Targets.seriesOfWeek(all, start))
        assertTrue(Targets.removeFromWeek(all, start, "pod").isEmpty())
    }
}
