package com.imad.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmEventsTest {
    private val now = 1_000_000_000_000L
    private val min = 60_000L

    private fun task(id: String, start: Long, minutes: Int, started: Long = 0) =
        Task(id = id, title = id, minutes = minutes, planned = start, day = "2026-10-15", started = started)

    @Test fun endAndNextStartMergeIntoOneAlarm() {
        val a = task("A", now + 10 * min, 30)
        val b = task("B", now + 40 * min, 20)
        val e = Alarms.events(listOf(a, b), now)
        val atB = e.filter { it.at == b.planned }
        assertEquals(1, atB.size)
        assertEquals("حان وقت: B", atB[0].title)
        assertEquals("انتهى وقت: A", atB[0].text)
        // A starts, and B's end rings alone because nothing follows it.
        assertTrue(e.any { it.at == a.planned && it.title == "حان وقت: A" })
        assertTrue(e.any { it.at == b.planned + 20 * min && it.title == "انتهى وقت: B" })
        assertEquals(3, e.size)
    }

    @Test fun runningTaskRingsAtItsEnd() {
        val r = task("R", now - 5 * min, 30, started = now - 5 * min)
        val e = Alarms.events(listOf(r), now)
        assertEquals(1, e.size)
        assertEquals(now + 25 * min, e[0].at)
    }

    @Test fun runningTrainingRingsForRestAndEachSet() {
        val r = Task(id = "H", title = "hang", minutes = 25, day = "d", planned = now, started = now,
            sets = 4, workSec = 10, restSec = 180)
        val e = Alarms.events(listOf(r), now)
        assertEquals(3, e.count { it.title.startsWith("راحة") })
        assertEquals(3, e.count { it.title.startsWith("الجولة") })
    }

    @Test fun parallelAndOverflowTasksDoNotRing() {
        val s = task("S", now + 10 * min, 25)
        val p = task("P", now + 12 * min, 8).copy(parallelTo = "S")
        val o = task("O", now + 600 * min, 30).copy(overflow = true)
        val e = Alarms.events(listOf(s, p, o), now)
        assertTrue(e.none { it.taskId == "P" || it.taskId == "O" })
    }
}
