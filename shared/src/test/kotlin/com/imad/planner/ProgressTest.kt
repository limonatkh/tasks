package com.imad.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressTest {
    private val s = Settings.from(emptyMap())
    private val today = LocalDate.of(2026, 11, 6)

    private fun task(day: String, minutes: Int = 30, state: String = "planned", spentMin: Int = 0, category: String = "other", pct: Int = 100) =
        Task(title = "x", minutes = minutes, day = day, state = state, spent = spentMin * 60_000L, category = category, pct = pct)

    @Test fun levels() {
        assertEquals(1, Progress.level(0))
        assertEquals(1, Progress.level(99))
        assertEquals(2, Progress.level(100))
        assertEquals(2, Progress.level(299))
        assertEquals(3, Progress.level(300))
        assertEquals(7, Progress.level(2100))
        assertEquals(6, Progress.level(2099))
        assertEquals(100L, Progress.need(2))
        assertEquals("شمعة", Progress.levelName(1))
        assertEquals("فانوس", Progress.levelName(7))
        assertEquals("نجم الصبح", Progress.levelName(30))
    }

    @Test fun taskPoints() {
        // Finished with the timer: 10 + real minutes.
        assertEquals(10 + 25, Progress.pointsOf(task("2026-11-06", 30, "done", 25)))
        // Real time is capped at 1.5× the estimate.
        assertEquals(10 + 45, Progress.pointsOf(task("2026-11-06", 30, "done", 200)))
        // Finished without the timer: the estimate, times the part done.
        assertEquals(10 + 30, Progress.pointsOf(task("2026-11-06", 30, "done")))
        assertEquals(10 + 15, Progress.pointsOf(task("2026-11-06", 30, "done", pct = 50)))
        // Not finished: only the minutes worked.
        assertEquals(12, Progress.pointsOf(task("2026-11-06", 30, "planned", 12)))
        // Closed without doing: nothing.
        assertEquals(0, Progress.pointsOf(task("2026-11-06", 30, "skipped", 20)))
    }

    @Test fun domainsAndTotals() {
        val tasks = listOf(task("2026-11-06", 60, "done", 60, "quran"), task("2026-11-06", 30, "done", 30, "sport"))
        val sum = Progress.summary(tasks, emptyList(), emptyList(), s, today)
        assertEquals(70L + 40L, sum.total.points)
        assertEquals(Domain.Quran, sum.domains.first().first)
        assertEquals(60, sum.minutes[Domain.Quran])
        assertTrue(Progress.earned(sum).any { it.key == "n:1" })
        assertFalse(Progress.earned(sum).any { it.key == "n:10" })
        assertEquals("n:10", Progress.next(sum).firstOrNull { it.key.startsWith("n:") }?.key)
    }

    @Test fun keptWeeks() {
        // The week of 24–30 Oct (Saturday start): 8 of 10 done = kept. 31 Oct–6 Nov is this week, not counted yet.
        val last = (0 until 10).map { task("2026-10-${24 + it % 7}", state = if (it < 8) "done" else "planned") }
        val sum = Progress.summary(last, emptyList(), emptyList(), s, today)
        assertEquals(1, sum.committedWeeks)
        val weak = (0 until 10).map { task("2026-10-${24 + it % 7}", state = if (it < 6) "done" else "planned") }
        assertEquals(0, Progress.summary(weak, emptyList(), emptyList(), s, today).committedWeeks)
    }

    @Test fun goalMilestonesAndRecords() {
        val g = Goal("pull", "عقلة", "sport", current = 15.0, reference = 20.0, unit = "عقلة", lower = false, baseline = 0.0)
        val logs = listOf(GoalLog(goalId = "pull", day = "2026-10-01", value = 5.0), GoalLog(goalId = "pull", day = "2026-10-10", value = 4.0),
            GoalLog(goalId = "pull", day = "2026-11-03", value = 15.0))
        val sum = Progress.summary(emptyList(), listOf(g), logs, s, today)
        // 75% → three quarters passed; two records (5, then 15; 4 was not better).
        assertEquals(3 * Progress.MILESTONE + 2 * Progress.RECORD, sum.total.points.toInt())
        assertEquals(0.75f, sum.goalPaths.first().fraction, 0.001f)
        assertEquals("2026-11-03", Progress.earned(sum).first { it.key == "g:pull:2" }.day)
        // Lower is better (a sprint time).
        val sprint = Goal("run", "١٠٠ م", "sport", current = 13.0, reference = 12.0, unit = "ث", lower = true, baseline = 14.0)
        assertEquals(0.5f, Progress.goalFraction(sprint), 0.001f)
    }
}
