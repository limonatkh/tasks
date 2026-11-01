package com.imad.planner

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** What one task should reach in a week or a day: free text, a number to reach and how much of it is done. */
data class Target(
    val text: String = "", val goal: Int = 0, val done: Int = 0,
    /** Sessions (tasks) this needs: for a week, how many in total; for a day, how many that day. */
    val sessions: Int = 0,
    /** Minutes of one session (week level). */
    val minutes: Int = 0,
    /** Kept in the week even when nothing is written yet (added by hand). */
    val keep: Boolean = false,
    /** Copied automatically from the previous week (a weekly routine goal). */
    val auto: Boolean = false,
) {
    val empty get() = text.isBlank() && goal == 0 && done == 0 && sessions == 0 && !keep
    val fraction get() = if (goal <= 0) 0f else (done.toFloat() / goal).coerceIn(0f, 1f)
}

/**
 * Month → weeks → days planning. Weeks run Friday to Thursday; a week belongs to the month its Friday is in.
 * Stored as one map: "w:<friday>" or "d:<day>" → (series → target).
 */
object Targets {
    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY))

    fun weekDays(start: LocalDate): List<LocalDate> = (0 until 7).map { start.plusDays(it.toLong()) }

    /** The Fridays of [ym]: each starts one week of that month. */
    fun weeksOf(ym: YearMonth): List<LocalDate> {
        var f = ym.atDay(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        val out = mutableListOf<LocalDate>()
        while (YearMonth.from(f) == ym) { out += f; f = f.plusWeeks(1) }
        return out
    }

    fun weekKey(start: LocalDate) = "w:$start"
    fun dayKey(day: LocalDate) = "d:$day"

    /** Done in a week = what was done on its days (the week's own counter is added for manual corrections). */
    fun weekDone(all: Map<String, Map<String, Target>>, start: LocalDate, series: String): Int =
        (all[weekKey(start)]?.get(series)?.done ?: 0) + weekDays(start).sumOf { all[dayKey(it)]?.get(series)?.done ?: 0 }

    /** Sum of the day goals already written for a week (to see what is still not spread over the days). */
    fun weekSpread(all: Map<String, Map<String, Target>>, start: LocalDate, series: String): Int =
        weekDays(start).sumOf { all[dayKey(it)]?.get(series)?.goal ?: 0 }

    /** Sessions written on the days of a week for one series. */
    fun sessionsSpread(all: Map<String, Map<String, Target>>, start: LocalDate, series: String): Int =
        weekDays(start).sumOf { all[dayKey(it)]?.get(series)?.sessions ?: 0 }

    /** Series that are part of a week: written for the week itself or for one of its days. */
    fun seriesOfWeek(all: Map<String, Map<String, Target>>, start: LocalDate): Set<String> =
        (listOf(weekKey(start)) + weekDays(start).map { dayKey(it) }).flatMap { all[it]?.keys.orEmpty() }.toSet()

    /**
     * A weekly routine goal: the new week starts as a copy of the previous one (its days too), marked automatic,
     * with nothing done yet. Weeks that already have it are left as they are.
     */
    fun copyWeek(all: Map<String, Map<String, Target>>, start: LocalDate, series: String): Map<String, Map<String, Target>> {
        if (all[weekKey(start)]?.containsKey(series) == true) return all
        val prev = start.minusWeeks(1)
        val w = all[weekKey(prev)]?.get(series) ?: return all
        var out = put(all, weekKey(start), series, w.copy(done = 0, auto = true, keep = true))
        for (d in weekDays(prev)) {
            val t = all[dayKey(d)]?.get(series) ?: continue
            out = put(out, dayKey(d.plusWeeks(1)), series, t.copy(done = 0, auto = true))
        }
        return out
    }

    /** Takes a series out of a week (the week and its days). */
    fun removeFromWeek(all: Map<String, Map<String, Target>>, start: LocalDate, series: String): Map<String, Map<String, Target>> {
        var out = put(all, weekKey(start), series, Target())
        for (d in weekDays(start)) out = put(out, dayKey(d), series, Target())
        return out
    }

    fun put(all: Map<String, Map<String, Target>>, key: String, series: String, t: Target): Map<String, Map<String, Target>> {
        val inner = all[key].orEmpty().toMutableMap()
        if (t.empty) inner.remove(series) else inner[series] = t
        val out = all.toMutableMap()
        if (inner.isEmpty()) out.remove(key) else out[key] = inner
        return out
    }

    fun parse(text: String?): Map<String, Map<String, Target>> {
        if (text.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = org.json.JSONObject(text)
            o.keys().asSequence().associateWith { k ->
                val inner = o.getJSONObject(k)
                inner.keys().asSequence().associateWith { s ->
                    val t = inner.getJSONObject(s)
                    Target(t.optString("t"), t.optInt("n"), t.optInt("d"), t.optInt("s"), t.optInt("m"), t.optBoolean("k"), t.optBoolean("a"))
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun encode(all: Map<String, Map<String, Target>>): String {
        val o = org.json.JSONObject()
        for ((k, inner) in all) {
            val io = org.json.JSONObject()
            for ((s, t) in inner) io.put(s, org.json.JSONObject().put("t", t.text).put("n", t.goal).put("d", t.done)
                .put("s", t.sessions).put("m", t.minutes).put("k", t.keep).put("a", t.auto))
            o.put(k, io)
        }
        return o.toString()
    }
}
