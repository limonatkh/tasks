package com.imad.planner

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/**
 * Pure scheduling logic (no Android). All times are minutes from the start of the day.
 */
data class Item(
    val id: String,
    val minutes: Int,
    /** Minute of day the user pinned the task to; it never moves without the user. */
    val fixedStart: Int? = null,
    /** Learned default start; the task is placed as close after it as possible. */
    val preferred: Int? = null,
    val priority: Int = 1,
    /** Light task that can be done in the rest windows of a training session. */
    val interruptible: Boolean = false,
    val sets: Int = 0,
    val workSec: Int = 0,
    val restSec: Int = 0,
    val order: Long = 0,
    /** Effective start of a running/paused task (already in progress). */
    val inProgressStart: Int? = null,
    /** Minutes still left for a task in progress. */
    val remaining: Int? = null,
) {
    val isTraining get() = sets > 1 && restSec > 0
}

data class Placed(
    val id: String,
    val start: Int,
    val end: Int,
    val parallelTo: String? = null,
    val overflow: Boolean = false,
)

data class DayPlan(
    val placed: Map<String, Placed>,
    val overflow: List<String>,
    val conflicts: List<Pair<String, String>>,
)

data class Window(val startSec: Int, val endSec: Int)

object Scheduler {
    /** Rest windows shorter than this are not used for other tasks (switching cost). */
    const val MIN_FILL_GAP_MIN = 3

    /** Rest windows of a training session that starts at [startMin], in seconds of day. Warm-up sits at the start. */
    fun restWindows(startMin: Int, minutes: Int, sets: Int, workSec: Int, restSec: Int): List<Window> {
        if (sets < 2 || restSec <= 0) return emptyList()
        val active = sets * workSec + (sets - 1) * restSec
        val warm = (minutes * 60 - active).coerceAtLeast(0)
        val base = startMin * 60 + warm
        return (0 until sets - 1).map { i ->
            val s = base + (i + 1) * workSec + i * restSec
            Window(s, s + restSec)
        }
    }

    fun usableRestSec(it: Item): Int =
        restWindows(0, it.minutes, it.sets, it.workSec, it.restSec)
            .map { w -> w.endSec - w.startSec }
            .filter { s -> s >= MIN_FILL_GAP_MIN * 60 }
            .sum()

    /** Smallest session length that fits the sets: work + rests, rounded up, plus [warmupMin]. */
    fun sessionMinutes(sets: Int, workSec: Int, restSec: Int, warmupMin: Int = 10): Int {
        if (sets <= 0) return warmupMin
        val sec = sets * workSec + (sets - 1).coerceAtLeast(0) * restSec
        return ceil(sec / 60.0).toInt() + warmupMin
    }

    /**
     * Plans one day.
     * @param earliest no flexible task starts before this (dayStart, or "now" for today)
     * @param gap free minutes kept between consecutive tasks
     */
    fun plan(items: List<Item>, dayStart: Int, dayEnd: Int, earliest: Int, gap: Int): DayPlan {
        val first = place(items, dayStart, dayEnd, earliest, gap)
        // Move light tasks into the rest windows of training sessions.
        val sessions = items.filter { it.isTraining && first.placed[it.id]?.overflow == false }
            .sortedBy { first.placed[it.id]!!.start }
        val candidates = items.filter {
            it.interruptible && !it.isTraining && it.fixedStart == null && it.inProgressStart == null
        }.sortedWith(compareByDescending<Item> { it.priority }.thenBy { it.order })
        val absorbed = mutableMapOf<String, String>()
        for (s in sessions) {
            var capacity = usableRestSec(s)
            for (c in candidates) {
                if (c.id in absorbed) continue
                val need = c.minutes * 60
                if (need <= capacity) {
                    absorbed[c.id] = s.id
                    capacity -= need
                }
            }
        }
        if (absorbed.isEmpty()) return first
        val second = place(items.filter { it.id !in absorbed }, dayStart, dayEnd, earliest, gap)
        val placed = second.placed.toMutableMap()
        for ((cid, sid) in absorbed) {
            val sp = placed[sid] ?: continue
            val sItem = items.first { it.id == sid }
            val w = restWindows(sp.start, sItem.minutes, sItem.sets, sItem.workSec, sItem.restSec)
                .firstOrNull { it.endSec - it.startSec >= MIN_FILL_GAP_MIN * 60 }
            val start = w?.startSec?.div(60) ?: sp.start
            placed[cid] = Placed(cid, start, sp.end, parallelTo = sid, overflow = sp.overflow)
        }
        return DayPlan(placed, second.overflow, second.conflicts)
    }

    private fun place(items: List<Item>, dayStart: Int, dayEnd: Int, earliest: Int, gap: Int): DayPlan {
        val busy = mutableListOf<Placed>()
        val placed = linkedMapOf<String, Placed>()
        val conflicts = mutableListOf<Pair<String, String>>()
        val overflow = mutableListOf<String>()

        fun overlapCheck(p: Placed) {
            for (b in busy) if (p.start < b.end && b.start < p.end) conflicts += b.id to p.id
        }

        // 1. Tasks already in progress keep their slot.
        for (it in items.filter { it.inProgressStart != null }) {
            val start = it.inProgressStart!!
            val p = Placed(it.id, start, start + (it.remaining ?: it.minutes))
            overlapCheck(p); busy += p; placed[it.id] = p
        }
        // 2. Pinned tasks.
        for (it in items.filter { it.inProgressStart == null && it.fixedStart != null }.sortedBy { it.fixedStart }) {
            val p = Placed(it.id, it.fixedStart!!, it.fixedStart + it.minutes)
            overlapCheck(p); busy += p; placed[it.id] = p
        }
        // 3. Flexible tasks: learned-default times first, then by priority and creation order.
        val flexible = items.filter { it.inProgressStart == null && it.fixedStart == null }
            .sortedWith(
                compareBy<Item> { it.preferred ?: Int.MAX_VALUE }
                    .thenByDescending { it.priority }
                    .thenBy { it.order }
            )
        val floor = maxOf(dayStart, earliest)
        for (it in flexible) {
            var start = findSlot(busy, maxOf(floor, it.preferred ?: floor), it.minutes, gap, dayEnd)
            if (start == null && it.preferred != null) start = findSlot(busy, floor, it.minutes, gap, dayEnd)
            if (start == null) {
                val after = maxOf(floor, (busy.maxOfOrNull { b -> b.end } ?: floor) + gap)
                val p = Placed(it.id, after, after + it.minutes, overflow = true)
                overflow += it.id; placed[it.id] = p; busy += p
            } else {
                val p = Placed(it.id, start, start + it.minutes)
                placed[it.id] = p; busy += p
            }
        }
        return DayPlan(placed, overflow, conflicts)
    }

    /** Earliest start >= [from] where [minutes] fit with [gap] around other tasks and end by [dayEnd]. */
    fun findSlot(busy: List<Placed>, from: Int, minutes: Int, gap: Int, dayEnd: Int): Int? {
        var t = from
        val sorted = busy.sortedBy { it.start }
        var moved = true
        while (moved) {
            moved = false
            for (b in sorted) {
                if (t < b.end + gap && t + minutes + gap > b.start) {
                    t = b.end + gap
                    moved = true
                }
            }
        }
        return if (t + minutes <= dayEnd) t else null
    }

    // ---------- Recurrence ----------

    /** weekdays bit 0 = Monday ... bit 6 = Sunday. */
    fun weekdayBit(d: DayOfWeek) = 1 shl (d.value - 1)

    fun intervalDays(everyHours: Int) = ceil(everyHours.coerceAtLeast(1) / 24.0).toInt().coerceAtLeast(1)

    /**
     * Next day the rule is due on or after [from].
     * Interval rules count from the last completed day (so recovery is respected), or from [anchor] if never done.
     */
    fun nextDue(everyHours: Int, weekdays: Int, anchor: LocalDate, lastDone: LocalDate?, from: LocalDate): LocalDate {
        if (weekdays != 0) {
            var d = maxOf(from, anchor)
            if (lastDone != null && !d.isAfter(lastDone)) d = lastDone.plusDays(1)
            repeat(8) {
                if (weekdays and weekdayBit(d.dayOfWeek) != 0) return d
                d = d.plusDays(1)
            }
            return d
        }
        val base = lastDone?.plusDays(intervalDays(everyHours).toLong()) ?: anchor
        return maxOf(base, if (lastDone == null) anchor else base)
    }

    fun isDue(everyHours: Int, weekdays: Int, anchor: LocalDate, lastDone: LocalDate?, day: LocalDate): Boolean {
        if (day.isBefore(anchor)) return false
        if (lastDone != null && !day.isAfter(lastDone)) return false
        if (weekdays != 0) return weekdays and weekdayBit(day.dayOfWeek) != 0
        val next = nextDue(everyHours, weekdays, anchor, lastDone, anchor)
        return !day.isBefore(next)
    }

    /** Projected due days within [from, from+days), assuming each occurrence gets done on its day. */
    fun projection(everyHours: Int, weekdays: Int, anchor: LocalDate, lastDone: LocalDate?, from: LocalDate, days: Int): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var done = lastDone
        var d = from
        val end = from.plusDays(days.toLong())
        while (d.isBefore(end)) {
            if (isDue(everyHours, weekdays, anchor, done, d)) {
                out += d; done = d
            }
            d = d.plusDays(1)
        }
        return out
    }

    // ---------- Goals ----------

    /** % of the reference reached. For "lower is better" goals the ratio is inverted. Can exceed 100. */
    fun achievement(current: Double, reference: Double, lower: Boolean): Double {
        if (current <= 0 || reference <= 0) return 0.0
        return if (lower) reference / current * 100 else current / reference * 100
    }

    /** Where the goal should be today if progress were linear from baseline (at start) to 100% (at target). */
    fun expectedAchievement(baselinePct: Double, start: LocalDate, target: LocalDate, today: LocalDate): Double {
        val total = ChronoUnit.DAYS.between(start, target).coerceAtLeast(1)
        val passed = ChronoUnit.DAYS.between(start, today).coerceIn(0, total)
        return baselinePct + (100 - baselinePct).coerceAtLeast(0.0) * passed / total
    }

    /** Value needed per week (in the goal's unit) to reach the reference by the target date. */
    fun weeklyRateNeeded(current: Double, reference: Double, lower: Boolean, today: LocalDate, target: LocalDate): Double {
        val weeks = (ChronoUnit.DAYS.between(today, target) / 7.0).coerceAtLeast(1.0 / 7)
        val diff = if (lower) current - reference else reference - current
        return (diff / weeks).coerceAtLeast(0.0)
    }
}
