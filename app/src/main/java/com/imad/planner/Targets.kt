package com.imad.planner

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** What one task should reach in a week or a day: free text, a number to reach and how much of it is done. */
data class Target(val text: String = "", val goal: Int = 0, val done: Int = 0) {
    val empty get() = text.isBlank() && goal == 0 && done == 0
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
                    Target(t.optString("t"), t.optInt("n"), t.optInt("d"))
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun encode(all: Map<String, Map<String, Target>>): String {
        val o = org.json.JSONObject()
        for ((k, inner) in all) {
            val io = org.json.JSONObject()
            for ((s, t) in inner) io.put(s, org.json.JSONObject().put("t", t.text).put("n", t.goal).put("d", t.done))
            o.put(k, io)
        }
        return o.toString()
    }
}
