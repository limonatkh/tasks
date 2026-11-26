package com.imad.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class V5Test {
    private val today = LocalDate.of(2026, 10, 17)
    private val cfg = Config(6 * 60, 23 * 60, 5, 3, 2, 180, blocks = listOf(9 * 60 to 17 * 60))

    @Test fun workHoursBlockKeepsTasksOut() {
        val plan = Scheduler.plan(listOf(Item("code", 60, windowStart = 9 * 60, windowEnd = 15 * 60)), cfg, 6 * 60)
        // Before work: 06:00–07:00 fits (window is soft).
        assertTrue(plan.placed["code"]!!.end <= 9 * 60 || plan.placed["code"]!!.start >= 17 * 60)
    }

    @Test fun allowedTasksGoInsideWorkHours() {
        val plan = Scheduler.plan(listOf(Item("listen", 20, windowStart = 10 * 60, windowEnd = 12 * 60, atWork = true)), cfg, 6 * 60)
        assertEquals(10 * 60, plan.placed["listen"]!!.start)
    }

    @Test fun blockedTaskGoesAfterWork() {
        val plan = Scheduler.plan(listOf(Item("a", 200)), cfg, 8 * 60)
        assertEquals(17 * 60 + 5, plan.placed["a"]!!.start)
    }

    @Test fun wirdStopsAt604AndRestarts() {
        val p = Plan("r", "ورد", "read", perDay = 20, pointer = 595, configured = true)
        assertEquals(10, PlanMath.quota(p, today, 0))
        assertEquals("ورد القرآن: صفحات 595–604", PlanMath.phaseTitle(p, 0, 10, 0))
        val next = PlanMath.applyUnits(p, 10, 0)
        assertEquals(1, next.pointer)
        assertEquals(1, next.done) // one khatma finished
        assertEquals("ورد القرآن: صفحات 1–20", PlanMath.phaseTitle(next, 0, 20, 0))
    }

    @Test fun khatmaDaysSetTheDailyPages() {
        val p = Plan("r", "ورد", "read", khatmaDays = 30, pointer = 1, configured = true)
        assertEquals(21, PlanMath.quota(p, today, 0)) // 604 / 30 rounded up
    }

    @Test fun podcastCycleGoesStepByStep() {
        val p = Plan("pod", "بودكاست", "cycle", stages = """["بحث","تحضير","تسجيل","مونتاج"]""", pointer = 0, total = 52, done = 0, perWeek = 1.0, configured = true)
        assertEquals(1, PlanMath.quota(p, today, 0))
        assertEquals("بودكاست: بحث", PlanMath.phaseTitle(p, 0, 1, 0))
        var q = p
        repeat(4) { q = PlanMath.applyUnits(q, 1, 0) }
        assertEquals(0, q.pointer)
        assertEquals(1, q.done) // one episode
        assertEquals(1, PlanMath.cycleIntervalDays(p)) // 4 steps a week -> every day
    }

    @Test fun monthTargetDrivesDailyAmount() {
        val p = Plan("m", "حفظ", "memorize", done = 100, perDay = 1, configured = true)
        // 15 days left in October (17..31): reach 130 -> 2 pages a day
        assertEquals(2, PlanMath.quotaWithMonth(p, today, 100, 130.0))
        assertEquals(1, PlanMath.quotaWithMonth(p, today, 100, null))
    }

    @Test fun cyclePaceFromMonthTarget() {
        val p = Plan("pod", "بودكاست", "cycle", stages = """["a","b"]""", done = 2, configured = true)
        assertEquals(2.0 / (15 / 7.0), PlanMath.cyclePerWeekForMonth(p, today, 4.0), 1e-9)
    }

    @Test fun paceShowsBehind() {
        val p = Plan("m", "حفظ", "memorize", done = 105, perDay = 1, configured = true, startDay = today.minusDays(10).toString(), startDone = 100)
        val (expected, actual) = PlanMath.pace(p, today)!!
        assertEquals(110.0, expected, 1e-9)
        assertEquals(105.0, actual, 1e-9)
    }

    @Test fun manyPhasesSplitEvenly() {
        val a = Scheduler.allocate(60, List(6) { Share(100 / 6, true, 0) })
        assertEquals(60, a.sum())
        assertTrue(a.all { it == 10 })
    }
}
