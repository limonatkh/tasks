package com.imad.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SchedulerTest {
    private val dayStart = 8 * 60
    private val dayEnd = 23 * 60

    @Test fun flexibleTasksFillFromDayStartWithGap() {
        val plan = Scheduler.plan(
            listOf(Item("a", 60, order = 1), Item("b", 30, order = 2)),
            dayStart, dayEnd, earliest = 0, gap = 5
        )
        assertEquals(480, plan.placed["a"]!!.start)
        assertEquals(545, plan.placed["b"]!!.start)
        assertTrue(plan.overflow.isEmpty())
    }

    @Test fun pinnedTaskNeverMovesAndOthersFlowAroundIt() {
        val plan = Scheduler.plan(
            listOf(Item("fixed", 60, fixedStart = 500), Item("x", 30, order = 1)),
            dayStart, dayEnd, earliest = 0, gap = 5
        )
        assertEquals(500, plan.placed["fixed"]!!.start)
        // 08:00–08:30 + 5 gap would touch 08:20 fixed start, so x goes after fixed.
        assertEquals(565, plan.placed["x"]!!.start)
    }

    @Test fun nowPushesFlexibleTasksForward() {
        val plan = Scheduler.plan(listOf(Item("a", 20)), dayStart, dayEnd, earliest = 14 * 60, gap = 5)
        assertEquals(840, plan.placed["a"]!!.start)
    }

    @Test fun preferredTimeIsRespected() {
        val plan = Scheduler.plan(
            listOf(Item("a", 30, order = 1), Item("gym", 60, preferred = 17 * 60, order = 2)),
            dayStart, dayEnd, earliest = 0, gap = 5
        )
        assertEquals(1020, plan.placed["gym"]!!.start)
        assertEquals(480, plan.placed["a"]!!.start)
    }

    @Test fun higherPriorityGoesFirst() {
        val plan = Scheduler.plan(
            listOf(Item("low", 30, priority = 0, order = 1), Item("high", 30, priority = 2, order = 2)),
            dayStart, dayEnd, earliest = 0, gap = 0
        )
        assertTrue(plan.placed["high"]!!.start < plan.placed["low"]!!.start)
    }

    @Test fun overflowIsReportedNotDropped() {
        val plan = Scheduler.plan(listOf(Item("big", 600), Item("big2", 600)), dayStart, dayEnd, 0, 5)
        assertEquals(listOf("big2"), plan.overflow)
        assertTrue(plan.placed["big2"]!!.overflow)
    }

    @Test fun overlappingPinnedTasksAreConflicts() {
        val plan = Scheduler.plan(
            listOf(Item("a", 60, fixedStart = 600), Item("b", 30, fixedStart = 630)),
            dayStart, dayEnd, 0, 5
        )
        assertEquals(1, plan.conflicts.size)
    }

    @Test fun lightTaskRunsInsideTrainingRestWindows() {
        // 4 sets, 10 s hangs, 3 min rest => 3 windows of 3 min = 9 usable minutes.
        val hang = Item("hang", 25, sets = 4, workSec = 10, restSec = 180, order = 1)
        val read = Item("read", 8, interruptible = true, order = 2)
        val deep = Item("code", 60, order = 3)
        val plan = Scheduler.plan(listOf(hang, read, deep), dayStart, dayEnd, 0, 5)
        assertEquals("hang", plan.placed["read"]!!.parallelTo)
        assertNull(plan.placed["code"]!!.parallelTo)
        // read no longer takes its own slot, so code starts right after the session.
        assertEquals(480 + 25 + 5, plan.placed["code"]!!.start)
    }

    @Test fun taskTooLongForRestWindowsStaysSeparate() {
        val hang = Item("hang", 25, sets = 4, workSec = 10, restSec = 180)
        val read = Item("read", 15, interruptible = true)
        val plan = Scheduler.plan(listOf(hang, read), dayStart, dayEnd, 0, 5)
        assertNull(plan.placed["read"]!!.parallelTo)
    }

    @Test fun shortRestsAreNotUsed() {
        val s = Item("s", 20, sets = 5, workSec = 30, restSec = 90)
        assertEquals(0, Scheduler.usableRestSec(s))
    }

    @Test fun restWindowsStartAfterWarmup() {
        val w = Scheduler.restWindows(startMin = 600, minutes = 25, sets = 4, workSec = 10, restSec = 180)
        assertEquals(3, w.size)
        val active = 4 * 10 + 3 * 180
        val warm = 25 * 60 - active
        assertEquals(600 * 60 + warm + 10, w[0].startSec)
        assertEquals(180, w[0].endSec - w[0].startSec)
    }

    @Test fun sessionMinutesCoverSetsAndWarmup() {
        assertEquals(10 + 10, Scheduler.sessionMinutes(4, 10, 180)) // 580 s -> 10 min + 10 warm-up
    }

    @Test fun inProgressTaskKeepsItsSlot() {
        val plan = Scheduler.plan(
            listOf(Item("run", 60, inProgressStart = 600, remaining = 20), Item("next", 30)),
            dayStart, dayEnd, earliest = 610, gap = 5
        )
        assertEquals(600, plan.placed["run"]!!.start)
        assertEquals(625, plan.placed["next"]!!.start)
    }

    // ---- recurrence ----

    private val d0 = LocalDate.of(2026, 10, 15) // Thursday

    @Test fun every48hRespectsLastDone() {
        assertTrue(Scheduler.isDue(48, 0, d0, null, d0))
        assertFalse(Scheduler.isDue(48, 0, d0, d0, d0.plusDays(1)))
        assertTrue(Scheduler.isDue(48, 0, d0, d0, d0.plusDays(2)))
    }

    @Test fun every72hProjection() {
        val p = Scheduler.projection(72, 0, d0, null, d0, 10)
        assertEquals(listOf(d0, d0.plusDays(3), d0.plusDays(6), d0.plusDays(9)), p)
    }

    @Test fun missedSessionStaysDue() {
        // done on day 0, every 48h, not done on day 2 -> still due on day 3
        assertTrue(Scheduler.isDue(48, 0, d0, d0, d0.plusDays(3)))
    }

    @Test fun weeklyDays() {
        val monWed = Scheduler.weekdayBit(java.time.DayOfWeek.MONDAY) or Scheduler.weekdayBit(java.time.DayOfWeek.WEDNESDAY)
        val p = Scheduler.projection(24, monWed, d0, null, d0, 7)
        assertEquals(listOf(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 21)), p)
    }

    @Test fun customHoursRoundUpToDays() {
        assertEquals(2, Scheduler.intervalDays(36))
        assertEquals(1, Scheduler.intervalDays(24))
        assertEquals(4, Scheduler.intervalDays(96))
    }

    // ---- goals ----

    @Test fun achievementHigherIsBetter() {
        assertEquals(60.0 / 90 * 100, Scheduler.achievement(60.0, 90.0, false), 1e-9)
    }

    @Test fun achievementLowerIsBetterTime() {
        // 50 m swim: 55 s now, 41.8 s reference
        assertEquals(41.8 / 55 * 100, Scheduler.achievement(55.0, 41.8, true), 1e-9)
        // 800 m: 200 s vs 201.8 reference -> above 100%
        assertTrue(Scheduler.achievement(200.0, 201.8, true) > 100)
    }

    @Test fun achievementCanExceed100() {
        assertEquals(120.0 / 94 * 100, Scheduler.achievement(120.0, 94.0, false), 1e-9)
    }

    @Test fun expectedAchievementIsLinear() {
        val start = LocalDate.of(2026, 1, 1)
        val target = start.plusDays(100)
        assertEquals(50.0, Scheduler.expectedAchievement(0.0, start, target, start.plusDays(50)), 1e-9)
        assertEquals(40.0, Scheduler.expectedAchievement(40.0, start, target, start), 1e-9)
        assertEquals(100.0, Scheduler.expectedAchievement(40.0, start, target, target.plusDays(5)), 1e-9)
    }

    @Test fun weeklyRateForTimeGoalDecreasesTime() {
        val today = LocalDate.of(2026, 1, 1)
        val rate = Scheduler.weeklyRateNeeded(55.0, 41.8, true, today, today.plusDays(70))
        assertEquals((55.0 - 41.8) / 10, rate, 1e-9)
    }
}
