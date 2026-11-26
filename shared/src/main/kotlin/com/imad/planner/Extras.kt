package com.imad.planner

import org.json.JSONArray
import org.json.JSONObject

/** A YouTube playlist a task plays: which episode (0-based) and second the user reached, and how many there are. */
data class YtLink(val list: String, val index: Int = 0, val pos: Int = 0, val total: Int = 0, val url: String = "", val finished: Boolean = false) {
    companion object {
        /** The playlist id from a link ("...list=PLxxxx...") or the id itself. */
        fun listId(text: String): String? {
            val t = text.trim()
            Regex("[?&]list=([A-Za-z0-9_-]+)").find(t)?.let { return it.groupValues[1] }
            return t.takeIf { Regex("^[A-Za-z0-9_-]{10,}$").matches(it) }
        }

        fun parseAll(text: String?): Map<String, YtLink> {
            if (text.isNullOrBlank()) return emptyMap()
            return runCatching {
                val o = JSONObject(text)
                o.keys().asSequence().associateWith { k ->
                    val e = o.getJSONObject(k)
                    YtLink(e.getString("list"), e.optInt("i"), e.optInt("p"), e.optInt("n"), e.optString("u"), e.optBoolean("f"))
                }
            }.getOrDefault(emptyMap())
        }

        fun encodeAll(all: Map<String, YtLink>): String {
            val o = JSONObject()
            all.forEach { (k, v) -> o.put(k, JSONObject().put("list", v.list).put("i", v.index).put("p", v.pos).put("n", v.total).put("u", v.url).put("f", v.finished)) }
            return o.toString()
        }
    }
}

/**
 * One part of a year plan: a name, how many months it covers, and its target —
 * a number, or a percentage of the year's goal.
 */
data class YearPart(val name: String, val months: Int, val value: Double = 0.0, val percent: Boolean = false) {
    companion object {
        /** Equal parts: 12 → months, 2 → 6 + 6, 3 → 4 + 4 + 4 … */
        fun equal(count: Int): List<YearPart> {
            val n = count.coerceIn(1, 12)
            val base = 12 / n; val rest = 12 % n
            return (0 until n).map { i -> YearPart(if (n == 12) "" else "الجزء ${i + 1}", base + if (i < rest) 1 else 0) }
        }

        /** Which part each month (0 = first month of the plan) belongs to. */
        fun partOfMonth(parts: List<YearPart>, month: Int): Int {
            var m = month
            parts.forEachIndexed { i, p -> if (m < p.months) return i; m -= p.months }
            return parts.lastIndex
        }

        fun parseAll(text: String?): Map<String, List<YearPart>> {
            if (text.isNullOrBlank()) return emptyMap()
            return runCatching {
                val o = JSONObject(text)
                o.keys().asSequence().associateWith { k ->
                    val a = o.getJSONArray(k)
                    (0 until a.length()).map { i -> a.getJSONObject(i).let { YearPart(it.optString("name"), it.optInt("m", 1), it.optDouble("v", 0.0), it.optBoolean("pct")) } }
                }
            }.getOrDefault(emptyMap())
        }

        fun encodeAll(all: Map<String, List<YearPart>>): String {
            val o = JSONObject()
            all.forEach { (k, list) ->
                val a = JSONArray()
                list.forEach { p -> a.put(JSONObject().put("name", p.name).put("m", p.months).put("v", p.value).put("pct", p.percent)) }
                o.put(k, a)
            }
            return o.toString()
        }
    }
}

/** Suggesting a better estimate from the real time of past sessions (the user decides; nothing changes by itself). */
object Estimates {
    const val MIN_SESSIONS = 5

    /**
     * [real] = real minutes of the latest sessions. Timers left running (more than 3x the estimate) and empty ones
     * are left out. Null when there are too few sessions or the difference is small.
     */
    fun suggest(estimate: Int, real: List<Int>): Int? {
        val ok = real.filter { it in 1..(estimate * 3).coerceAtLeast(10) }
        if (ok.size < MIN_SESSIONS || estimate <= 0) return null
        val sorted = ok.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        val diff = median - estimate
        if (kotlin.math.abs(diff) < maxOf(5.0, estimate * 0.2)) return null
        val rounded = ((median + 2) / 5 * 5).coerceAtLeast(5)
        return rounded.takeIf { it != estimate }
    }
}
