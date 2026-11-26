package com.imad.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayShapeTest {
    private val shape = DayShape(wake = 6 * 60, sleep = 23 * 60) // morning 06–09, work 09–15, heavy sport 15–21
    private val cfg = Config(dayStart = 6 * 60, dayEnd = 23 * 60, gap = 5, maxSports = 3, maxHeavy = 2, sportGap = 180)

    private fun item(id: String, minutes: Int, category: String, sport: Int = 0, phase: Int = 0, phases: Int = 1, order: Long = 0): Item {
        val w = Windows.of(category, sport, shape)
        val e = Windows.phaseEarliest(phase, phases, shape)
        return Item(
            id, minutes, order = order, sport = sport, hardEnd = w.hardEnd, earliest = e,
            windowStart = if (e != null) maxOf(w.start, e) else w.start,
            windowEnd = if (e != null && e >= w.end) shape.sleep else w.end,
        )
    }

    @Test fun morningIsForQuranNotForGym() {
        val plan = Scheduler.plan(
            listOf(item("gym", 60, "sport", 2, order = 1), item("hifz", 30, "quran", order = 2), item("code", 120, "work", order = 3)),
            cfg, now = 6 * 60,
        )
        assertEquals(6 * 60, plan.placed["hifz"]!!.start)
        assertEquals(9 * 60, plan.placed["code"]!!.start)
        assertEquals(15 * 60, plan.placed["gym"]!!.start)
    }

    @Test fun heavySportMustEndByNine() {
        // At 20:30 a 60-minute gym session cannot end by 21:00 -> overflow (goes to the next day).
        val plan = Scheduler.plan(listOf(item("gym", 60, "sport", 2)), cfg, now = 20 * 60 + 30)
        assertTrue(plan.placed["gym"]!!.overflow)
    }

    @Test fun lightSportCanBeDuringTheDay() {
        val plan = Scheduler.plan(listOf(item("push", 20, "sport", 1)), cfg, now = 6 * 60)
        assertEquals(9 * 60, plan.placed["push"]!!.start)
    }

    @Test fun sportSessionsAreHoursApart() {
        val plan = Scheduler.plan(
            listOf(item("gym", 60, "sport", 2, order = 1), item("plyo", 20, "sport", 2, order = 2)),
            cfg, now = 6 * 60,
        )
        val a = plan.placed["gym"]!!; val b = plan.placed["plyo"]!!
        val gap = if (b.start >= a.end) b.start - a.end else a.start - b.end
        assertTrue("gap was $gap", gap >= 180)
    }

    @Test fun tooManySportsMoveToTheNextDay() {
        val items = (1..5).map { item("s$it", 15, "sport", 1, order = it.toLong()) }
        val plan = Scheduler.plan(items, cfg, now = 6 * 60)
        assertEquals(2, plan.overflow.size) // max 3 a day
    }

    @Test fun secondPhaseWaitsForItsPartOfTheDay() {
        val plan = Scheduler.plan(
            listOf(item("r1", 20, "quran", phase = 0, phases = 2), item("r2", 10, "quran", phase = 1, phases = 2)),
            cfg, now = 6 * 60,
        )
        assertEquals(6 * 60, plan.placed["r1"]!!.start)
        assertTrue(plan.placed["r2"]!!.start >= 6 * 60 + (17 * 60) / 2)
    }

    @Test fun phaseEarliestSplitsTheDay() {
        assertNull(Windows.phaseEarliest(0, 2, shape))
        assertEquals(6 * 60 + 17 * 60 / 2, Windows.phaseEarliest(1, 2, shape))
    }

    // ---- stagger ----

    @Test fun weeklyGymSessionsGetDifferentDays() {
        val off = Scheduler.stagger(listOf(Recurring("a", 7, true), Recurring("b", 7, true), Recurring("c", 7, true)))
        assertEquals(3, off.values.toSet().size)
    }

    @Test fun staggerAvoidsAllOnDayOne() {
        val items = (1..8).map { Recurring("r$it", if (it % 2 == 0) 2 else 3, it <= 3) }
        val off = Scheduler.stagger(items)
        val perDay = IntArray(28)
        items.forEach { r -> var d = off[r.id]!!; while (d < 28) { perDay[d]++; d += r.intervalDays } }
        assertTrue("max ${perDay.max()}", perDay.max() <= 4)
        assertTrue(off.values.count { it == 0 } <= 3)
    }

    @Test fun fixedOffsetsAreRespected() {
        val off = Scheduler.stagger(listOf(Recurring("box", 7, true, fixedOffset = 0), Recurring("gym", 7, true)))
        assertEquals(null, off["box"])
        assertTrue(off["gym"] != 0)
    }

    // ---- phases ----

    @Test fun splitByPercent() {
        assertEquals(listOf(30, 10), Scheduler.allocate(40, listOf(Share(75, true, 0), Share(25, true, 0))))
    }

    @Test fun skippedPhaseRollsIntoTheNext() {
        assertEquals(listOf(0, 40), Scheduler.allocate(40, listOf(Share(75, false, 0), Share(25, true, 0))))
    }

    @Test fun partlyDoneFirstPhaseLeavesTheRestToTheSecond() {
        assertEquals(listOf(20, 20), Scheduler.allocate(40, listOf(Share(75, false, 20), Share(25, true, 0))))
    }

    @Test fun continuingPastTheFirstShareShrinksTheSecond() {
        assertEquals(listOf(36, 4), Scheduler.allocate(40, listOf(Share(75, false, 36), Share(25, true, 0))))
        assertEquals(listOf(40, 0), Scheduler.allocate(40, listOf(Share(75, false, 40), Share(25, true, 0))))
    }

    @Test fun threePhasesRoundToTheTotal() {
        val a = Scheduler.allocate(10, listOf(Share(34, true, 0), Share(33, true, 0), Share(33, true, 0)))
        assertEquals(10, a.sum())
    }

    // ---- plans ----

    private val today = LocalDate.of(2026, 10, 17)

    @Test fun memorizeQuotaIsTheUsersDailyAmount() {
        val p = Plan("m", "حفظ", "memorize", done = 404, perDay = 1, configured = true)
        assertEquals(1, PlanMath.quota(p, today, 404))
        assertEquals("حفظ: صفحة 405", PlanMath.phaseTitle(p, 0, 1, 404))
        // Not confirmed -> the same page again.
        assertEquals("حفظ: صفحة 405", PlanMath.phaseTitle(p, 0, 1, 404))
        val after = PlanMath.applyUnits(p, 1, 404)
        assertEquals("حفظ: صفحة 406", PlanMath.phaseTitle(after, 0, 1, 405))
    }

    @Test fun reviewUsesDailyPagesAndWrapsInsideMemorized() {
        val p = Plan("v", "مراجعة", "review", perDay = 40, pointer = 31, configured = true)
        assertEquals(40, PlanMath.quota(p, today, 60))
        val pages = PlanMath.reviewPages(p, 0, 40, 60)
        assertEquals(31, pages.first()); assertEquals(60, pages[29]); assertEquals(1, pages[30]); assertEquals(10, pages.last())
        assertEquals(11, PlanMath.applyUnits(p, 40, 60).pointer)
    }

    @Test fun reviewSecondPhaseStartsWhereTheFirstStops() {
        val p = Plan("v", "مراجعة", "review", perDay = 40, pointer = 1, configured = true)
        assertEquals("سماع المحفوظ: صفحات 31–40", PlanMath.phaseTitle(p, 30, 10, 100))
    }

    @Test fun projectAdvancesDays() {
        val p = Plan("r", "ورد", "read", perDay = 10, pointer = 1, configured = true)
        assertEquals(21, PlanMath.project(p, 2, 0, today).pointer)
    }

    @Test fun weekStartsSaturday() {
        assertEquals(LocalDate.of(2026, 10, 17), PlanMath.startOfWeek(LocalDate.of(2026, 10, 17)))
        assertEquals(LocalDate.of(2026, 10, 17), PlanMath.startOfWeek(LocalDate.of(2026, 10, 23)))
    }
}
