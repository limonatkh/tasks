package com.imad.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The cases of the 0.25 review (R01–R07), checked on every build. */
class ReviewTest {
    private val dayStart = 8 * 60
    private val dayEnd = 23 * 60
    private val min = 60_000L

    // R01: a 30-minute task worked for 45 minutes keeps both numbers.
    @Test fun realTimeIsKeptPastTheEstimate() {
        val t = Task(title = "x", minutes = 30, day = "2026-11-06", spent = 45 * min)
        assertEquals(45 * min, Maths.spent(t, 0))
        assertEquals(0L, Maths.remaining(t, 0))
        assertEquals(15 * min, Maths.over(t, 0))
        assertEquals(30, t.minutes)
    }

    @Test fun pausedTimeIsNotCounted() {
        // 10 min saved, then running for 5 more: 15, nothing for the pause in between.
        val t = Task(title = "x", minutes = 30, day = "2026-11-06", spent = 10 * min, started = 1_000_000L)
        assertEquals(15 * min, Maths.spent(t, 1_000_000L + 5 * min))
    }

    // R02: 48 real hours after Monday 22:00 is Wednesday 22:00, not Wednesday morning.
    private fun at(y: Int, m: Int, d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(y, m, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun realHoursWaitTheWholeTime() {
        val doneMon = at(2026, 11, 9, 22)
        val ready = Scheduler.readyAt(48, doneMon)!!
        assertEquals(at(2026, 11, 11, 22), ready)
        val mon = LocalDate.of(2026, 11, 9)
        // Tuesday: not due. Wednesday with the day ending 23:00: a 45-minute session still fits after 22:00.
        assertFalse(Scheduler.dueByHours(ready, 45, at(2026, 11, 10, 23), mon, LocalDate.of(2026, 11, 10)))
        assertTrue(Scheduler.dueByHours(ready, 45, at(2026, 11, 11, 23), mon, LocalDate.of(2026, 11, 11)))
        // A 90-minute session does not fit before 23:00 on Wednesday: it waits for Thursday.
        assertFalse(Scheduler.dueByHours(ready, 90, at(2026, 11, 11, 23), mon, LocalDate.of(2026, 11, 11)))
        assertTrue(Scheduler.dueByHours(ready, 90, at(2026, 11, 12, 23), mon, LocalDate.of(2026, 11, 12)))
    }

    @Test fun calendarDaysStayAsTheyWere() {
        val mon = LocalDate.of(2026, 11, 9)
        assertTrue(Scheduler.isDue(48, 0, mon, mon, LocalDate.of(2026, 11, 11)))
        assertFalse(Scheduler.isDue(48, 0, mon, mon, LocalDate.of(2026, 11, 10)))
    }

    @Test fun aRestingSessionStartsAfterItsWait() {
        // Wait ends at 18:00: the session is not placed before that.
        val plan = Scheduler.plan(listOf(Item("gym", 60, earliest = 18 * 60, windowStart = 18 * 60)), dayStart, dayEnd, 0, 5)
        assertTrue(plan.placed["gym"]!!.start >= 18 * 60)
    }

    // R04: little time left; the important task is not pushed out by an earlier-preferring optional one.
    @Test fun priorityBeforePreferredTime() {
        val low = Item("low", 60, preferred = 8 * 60, priority = 0, order = 1)
        val high = Item("high", 30, preferred = 8 * 60 + 30, priority = 2, order = 2)
        val plan = Scheduler.plan(listOf(low, high), dayStart, 9 * 60, 0, 0)
        assertFalse(plan.placed["high"]!!.overflow)
        assertTrue("low" in plan.overflow)
    }

    @Test fun equalPriorityKeepsTheDaysOrder() {
        val a = Item("a", 30, preferred = 10 * 60, order = 2)
        val b = Item("b", 30, preferred = 9 * 60, order = 1)
        val plan = Scheduler.plan(listOf(a, b), dayStart, dayEnd, 0, 5)
        assertEquals(9 * 60, plan.placed["b"]!!.start)
        assertEquals(10 * 60, plan.placed["a"]!!.start)
    }

    // R05: "this period only" — when the period is full the task waits unplaced instead of moving out.
    @Test fun strictPeriodIsNotLeft() {
        val block = Item("block", 120, fixedStart = 8 * 60)
        val strict = Item("strict", 30, windowStart = 8 * 60, windowEnd = 10 * 60, strict = true)
        val soft = Item("soft", 30, windowStart = 8 * 60, windowEnd = 10 * 60)
        val plan = Scheduler.plan(listOf(block, strict, soft), dayStart, dayEnd, 0, 0)
        assertTrue("strict" in plan.overflow)
        assertFalse(plan.placed["soft"]!!.overflow)
        assertTrue(plan.placed["soft"]!!.start >= 10 * 60)
    }

    // R06: two light tasks in the same training's rests never share the same seconds.
    @Test fun twoParallelTasksGetDifferentRestSeconds() {
        // 5 sets, 10 s work, 4 min rest => 4 windows of 4 min.
        val hang = Item("hang", 30, sets = 5, workSec = 10, restSec = 240, order = 1)
        val a = Item("a", 4, interruptible = true, order = 2)
        val b = Item("b", 4, interruptible = true, order = 3)
        val plan = Scheduler.plan(listOf(hang, a, b), dayStart, dayEnd, 0, 5)
        assertEquals("hang", plan.placed["a"]!!.parallelTo)
        assertEquals("hang", plan.placed["b"]!!.parallelTo)
        assertNotEquals(plan.placed["a"]!!.start, plan.placed["b"]!!.start)
    }

    @Test fun restsAreNotCountedTwice() {
        // 9 usable minutes: an 8-minute task fits, a second 8-minute one does not.
        val hang = Item("hang", 25, sets = 4, workSec = 10, restSec = 180, order = 1)
        val a = Item("a", 8, interruptible = true, order = 2)
        val b = Item("b", 8, interruptible = true, order = 3)
        val plan = Scheduler.plan(listOf(hang, a, b), dayStart, dayEnd, 0, 5)
        assertEquals("hang", plan.placed["a"]!!.parallelTo)
        assertEquals(null, plan.placed["b"]!!.parallelTo)
    }

    @Test fun aTaskAllowedOnlyLaterIsNotPutInAMorningSession() {
        val hang = Item("hang", 25, sets = 4, workSec = 10, restSec = 180, preferred = 9 * 60, order = 1)
        val listen = Item("listen", 5, interruptible = true, earliest = 15 * 60, order = 2)
        val plan = Scheduler.plan(listOf(hang, listen), dayStart, dayEnd, 0, 5)
        assertEquals(null, plan.placed["listen"]!!.parallelTo)
        assertTrue(plan.placed["listen"]!!.start >= 15 * 60)
    }
}

class EstimatesTest {
    @Test fun suggestsAfterFiveSessions() {
        assertEquals(45, Estimates.suggest(30, listOf(44, 46, 45, 43, 47)))
        assertEquals(null, Estimates.suggest(30, listOf(44, 46, 45, 43)))
    }

    @Test fun smallDifferencesAreLeftAlone() {
        assertEquals(null, Estimates.suggest(30, listOf(32, 33, 31, 34, 30)))
    }

    @Test fun forgottenTimersDoNotCount() {
        // Two sessions of 5 hours (timer left on) are dropped; four normal ones are not enough.
        assertEquals(null, Estimates.suggest(30, listOf(300, 310, 31, 30, 29, 32)))
    }

    @Test fun shorterTooWhenItIsShorter() {
        assertEquals(20, Estimates.suggest(40, listOf(20, 18, 22, 19, 21)))
    }
}
