package com.imad.planner

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor

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
    /** Preferred window of the day for this kind of task (soft). */
    val windowStart: Int? = null,
    val windowEnd: Int? = null,
    /** The task must end by this minute (heavy training before 21:00). */
    val hardEnd: Int? = null,
    /** 0 = not sport, 1 = light sport, 2 = heavy sport. */
    val sport: Int = 0,
    /** Not before this minute (later phases of a split task). */
    val earliest: Int? = null,
    /** May be placed inside the work hours block. */
    val atWork: Boolean = false,
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
    /** Sport sessions over the daily limit (they belong to another day, not to "no time left"). */
    val capped: List<String> = emptyList(),
)

data class Window(val startSec: Int, val endSec: Int)

data class Config(
    val dayStart: Int,
    val dayEnd: Int,
    val gap: Int = 5,
    val maxSports: Int = 3,
    val maxHeavy: Int = 2,
    /** Minimum minutes between two sport sessions. */
    val sportGap: Int = 180,
    /** Busy blocks (work hours): only items with [Item.atWork] go inside them. */
    val blocks: List<Pair<Int, Int>> = emptyList(),
)

/** Shape of the user's day: wake, sleep and the boundaries of its parts. */
data class DayShape(
    val wake: Int,
    val sleep: Int,
    val morning: Int = 180,
    val workEnd: Int = 15 * 60,
    val sportEnd: Int = 21 * 60,
)

data class Win(val start: Int, val end: Int, val hardEnd: Int?)

object Windows {
    /** Where a task of this category naturally belongs in the day. */
    fun of(category: String, sport: Int, s: DayShape): Win {
        val morningEnd = minOf(s.wake + s.morning, s.sleep)
        return when {
            category == "sport" && sport >= 2 || category == "evening" -> {
                val end = minOf(s.sportEnd, s.sleep)
                Win(minOf(maxOf(s.workEnd, s.wake), end), end, end)
            }
            category == "sport" -> Win(morningEnd, maxOf(morningEnd, s.sleep - 60), null)
            category == "quran" || category == "reading" || category == "morning" -> Win(s.wake, morningEnd, null)
            category == "work" -> Win(morningEnd, maxOf(morningEnd, minOf(s.workEnd, s.sleep)), null)
            else -> Win(morningEnd, s.sleep, null)
        }
    }

    /** Later phases of a split task are spread over the day: phase k not before wake + k/n of the day. */
    fun phaseEarliest(phase: Int, count: Int, s: DayShape): Int? =
        if (phase <= 0 || count <= 1) null else s.wake + phase * (s.sleep - s.wake) / count
}

data class Recurring(val id: String, val intervalDays: Int, val heavy: Boolean, val fixedOffset: Int? = null)

data class Share(val pct: Int, val open: Boolean, val doneUnits: Int)

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

    /** Old signature, no sport limits. */
    fun plan(items: List<Item>, dayStart: Int, dayEnd: Int, earliest: Int, gap: Int): DayPlan =
        plan(items, Config(dayStart, dayEnd, gap, maxSports = 99, maxHeavy = 99, sportGap = 0), earliest)

    /**
     * Plans one day.
     * @param now no flexible task starts before this (dayStart, or "now" for today)
     */
    fun plan(items: List<Item>, cfg: Config, now: Int): DayPlan {
        val first = place(items, cfg, now)
        // Move light tasks into the rest windows of training sessions.
        val sessions = items.filter { it.isTraining && first.placed[it.id]?.overflow == false }
            .sortedBy { first.placed[it.id]!!.start }
        val candidates = items.filter {
            it.interruptible && !it.isTraining && it.fixedStart == null && it.inProgressStart == null && it.sport == 0
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
        val second = place(items.filter { it.id !in absorbed }, cfg, now)
        val placed = second.placed.toMutableMap()
        for ((cid, sid) in absorbed) {
            val sp = placed[sid] ?: continue
            val sItem = items.first { it.id == sid }
            val w = restWindows(sp.start, sItem.minutes, sItem.sets, sItem.workSec, sItem.restSec)
                .firstOrNull { it.endSec - it.startSec >= MIN_FILL_GAP_MIN * 60 }
            val start = w?.startSec?.div(60) ?: sp.start
            placed[cid] = Placed(cid, start, sp.end, parallelTo = sid, overflow = sp.overflow)
        }
        return DayPlan(placed, second.overflow, second.conflicts, second.capped)
    }

    private fun place(items: List<Item>, cfg: Config, now: Int): DayPlan {
        val busy = mutableListOf<Placed>()
        val placed = linkedMapOf<String, Placed>()
        val conflicts = mutableListOf<Pair<String, String>>()
        val overflow = mutableListOf<String>()
        val capped = mutableListOf<String>()
        val sportOf = items.associate { it.id to it.sport }

        fun overlapCheck(p: Placed) {
            for (b in busy) if (p.start < b.end && b.start < p.end) conflicts += b.id to p.id
        }
        fun sports() = busy.count { (sportOf[it.id] ?: 0) > 0 }
        fun heavies() = busy.count { (sportOf[it.id] ?: 0) >= 2 }

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
        // 3. Flexible tasks, in the order of where they belong in the day.
        val floor = maxOf(cfg.dayStart, now)
        val flexible = items.filter { it.inProgressStart == null && it.fixedStart == null }
            .sortedWith(
                compareBy<Item> { it.preferred ?: it.windowStart ?: it.earliest ?: cfg.dayStart }
                    .thenByDescending { it.priority }
                    .thenBy { it.order }
            )
        val blockers = cfg.blocks.mapIndexed { i, (a, b) -> Placed("__block$i", a, b) }
        for (it in flexible) {
            val around = if (it.atWork) busy else busy + blockers
            if (it.sport > 0 && (sports() >= cfg.maxSports || (it.sport >= 2 && heavies() >= cfg.maxHeavy))) {
                overflow += it.id; capped += it.id
                placed[it.id] = Placed(it.id, floor, floor + it.minutes, overflow = true)
                continue
            }
            val lo = maxOf(floor, it.earliest ?: floor)
            val limit = minOf(cfg.dayEnd, it.hardEnd ?: cfg.dayEnd)
            val gapFor: (Placed) -> Int = { b ->
                if (it.sport > 0 && (sportOf[b.id] ?: 0) > 0) maxOf(cfg.gap, cfg.sportGap) else cfg.gap
            }
            var start: Int? = null
            if (it.preferred != null) start = findSlot(around, maxOf(lo, it.preferred), it.minutes, limit, gapFor)
            if (start == null && it.windowStart != null)
                start = findSlot(around, maxOf(lo, it.windowStart), it.minutes, minOf(limit, it.windowEnd ?: limit), gapFor)
            if (start == null) start = findSlot(around, lo, it.minutes, limit, gapFor)
            if (start == null) {
                overflow += it.id
                placed[it.id] = Placed(it.id, lo, lo + it.minutes, overflow = true)
            } else {
                val p = Placed(it.id, start, start + it.minutes)
                placed[it.id] = p; busy += p
            }
        }
        return DayPlan(placed, overflow, conflicts, capped)
    }

    /** Earliest start >= [from] where [minutes] fit with the gap around other tasks and end by [end]. */
    fun findSlot(busy: List<Placed>, from: Int, minutes: Int, end: Int, gapFor: (Placed) -> Int): Int? {
        var t = from
        val sorted = busy.sortedBy { it.start }
        var moved = true
        while (moved) {
            moved = false
            for (b in sorted) {
                val g = gapFor(b)
                if (t < b.end + g && t + minutes + g > b.start) {
                    t = b.end + g
                    moved = true
                }
            }
        }
        return if (t + minutes <= end) t else null
    }

    fun findSlot(busy: List<Placed>, from: Int, minutes: Int, gap: Int, dayEnd: Int): Int? =
        findSlot(busy, from, minutes, dayEnd) { gap }

    // ---------- Spreading sport sessions over days ----------

    /**
     * Picks a start-day offset for each recurring session so the days are evenly loaded
     * (heavy sessions weigh more). Items with [Recurring.fixedOffset] are kept where they are.
     */
    fun stagger(items: List<Recurring>, horizon: Int = 28): Map<String, Int> {
        val load = IntArray(horizon)
        val heavy = IntArray(horizon)
        fun apply(off: Int, r: Recurring) {
            var d = off
            val step = r.intervalDays.coerceAtLeast(1)
            while (d < horizon) { load[d]++; if (r.heavy) heavy[d]++; d += step }
        }
        items.filter { it.fixedOffset != null }.forEach { apply(it.fixedOffset!!.coerceIn(0, horizon - 1), it) }
        val out = linkedMapOf<String, Int>()
        val free = items.filter { it.fixedOffset == null }
            .sortedWith(compareByDescending<Recurring> { it.heavy }.thenByDescending { it.intervalDays }.thenBy { it.id })
        for (r in free) {
            val step = r.intervalDays.coerceIn(1, horizon)
            var best = 0
            var bestCost = Double.MAX_VALUE
            for (off in 0 until step) {
                var cost = off * 0.1
                var d = off
                while (d < horizon) {
                    cost += (load[d] + 1).let { it * it }.toDouble()
                    if (r.heavy) cost += 3.0 * (heavy[d] + 1).let { it * it }
                    d += step
                }
                if (cost < bestCost) { bestCost = cost; best = off }
            }
            out[r.id] = best
            apply(best, r)
        }
        return out
    }

    // ---------- Splitting a daily amount over phases ----------

    /**
     * Units for each phase of a split task. Finished phases keep what they did; open phases share
     * what is left of [quota] by their percentages (largest remainder rounding).
     */
    fun allocate(quota: Int, shares: List<Share>): List<Int> {
        val result = shares.map { it.doneUnits }.toMutableList()
        val rem = (quota - shares.sumOf { it.doneUnits }).coerceAtLeast(0)
        val open = shares.indices.filter { shares[it].open }
        if (open.isEmpty() || rem == 0) return result
        val total = open.sumOf { shares[it].pct.coerceAtLeast(1) }.toDouble()
        val raw = open.map { rem * shares[it].pct.coerceAtLeast(1) / total }
        val base = raw.map { floor(it).toInt() }.toMutableList()
        var left = rem - base.sum()
        val order = raw.indices.sortedWith(compareByDescending<Int> { raw[it] - floor(raw[it]) }.thenBy { it })
        for (i in order) { if (left <= 0) break; base[i]++; left-- }
        open.forEachIndexed { k, i -> result[i] += base[k] }
        return result
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
