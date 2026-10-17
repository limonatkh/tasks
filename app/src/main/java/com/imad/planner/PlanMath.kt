package com.imad.planner

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil

const val MUSHAF_PAGES = 604

/**
 * A yearly quantity plan (Quran memorization, daily wird, listening review, a book…).
 * The app splits it into month / week / day suggestions; the user accepts or edits them.
 */
data class Plan(
    val id: String,
    val title: String,
    /** memorize | read | review | amount */
    val kind: String,
    val unit: String = "صفحة",
    /** Last page/amount of the plan (604 for the whole mushaf). */
    val total: Int = MUSHAF_PAGES,
    /** memorize/amount: last page done (memorized pages are startPage..done). */
    val done: Int = 0,
    /** Daily amount chosen by the user; 0 = use the suggestion. */
    val perDay: Int = 0,
    val endDay: String = LocalDate.now().plusYears(1).toString(),
    val minutesPerUnit: Int = 5,
    /** review: memorized pages are listened to once every [cycleDays] days. */
    val cycleDays: Int = 7,
    /** read/review: next page to start from. */
    val pointer: Int = 1,
    val startPage: Int = 1,
    val accepted: Boolean = false,
    val configured: Boolean = false,
    /** Day whose progress [dayDone] / [dayQuota] refer to. */
    val dayKey: String = "",
    val dayDone: Int = 0,
    val dayQuota: Int = 0,
    /** Group key; empty = the default for its kind. */
    val category: String = "",
    /** cycle: the steps of one round (podcast: research, record, edit…), JSON array of names. */
    val stages: String = "",
    /** cycle: rounds per week (one episode a week = 1). */
    val perWeek: Double = 1.0,
    /** read: finish the mushaf every this many days (0 = use perDay). */
    val khatmaDays: Int = 0,
    /** When the plan started and where it stood then (for ahead / behind). */
    val startDay: String = "",
    val startDone: Int = 0,
    /** Channel plans: content types (JSON) and the month the steps were built for. */
    val content: String = "",
    val contentMonth: String = "",
) {
    /** Steps of one round. Stored as names or as {n: name, m: minutes, d: details}. */
    val stageItems: List<Stage> get() = Stages.parse(stages)
    val stageList: List<String> get() = stageItems.map { it.name }
}

data class Stage(val name: String, val minutes: Int = 0, val details: String = "")

object Stages {
    fun parse(text: String): List<Stage> = runCatching {
        val a = org.json.JSONArray(text)
        (0 until a.length()).map { i ->
            val o = a.optJSONObject(i)
            if (o != null) Stage(o.optString("n"), o.optInt("m"), o.optString("d")) else Stage(a.getString(i))
        }
    }.getOrDefault(emptyList())

    fun encode(list: List<Stage>): String {
        val a = org.json.JSONArray()
        list.forEach { a.put(org.json.JSONObject().put("n", it.name).put("m", it.minutes).put("d", it.details)) }
        return a.toString()
    }

    /** Steps pasted from ChatGPT: one per line, "name | minutes | details". */
    fun fromText(text: String): List<Stage> = text.lines().mapNotNull { line ->
        val clean = line.trim().trimStart('-', '•', '*', ' ').replace(Regex("^\\d+[.)\\-]\\s*"), "")
        if (clean.isBlank()) return@mapNotNull null
        val parts = clean.split('|').map { it.trim() }
        val minutes = parts.getOrNull(1)?.let { Regex("\\d+").find(digitsAscii(it))?.value?.toIntOrNull() } ?: 0
        val hours = parts.getOrNull(1)?.let { it.contains("ساع") || it.contains("hour", true) } == true
        Stage(parts[0].take(80), if (hours && minutes in 1..12) minutes * 60 else minutes, parts.getOrNull(2) ?: "")
    }

    private fun digitsAscii(s: String) = s.map { c -> if (Character.isDigit(c)) ('0' + Character.getNumericValue(c)) else c }.joinToString("")
}

/** One kind of video for a channel: how many a month, how many work sessions each, and this month's titles. */
data class ContentType(val name: String, val perMonth: Int, val sessions: Int, val minutes: Int, val titles: List<String> = emptyList())

object Content {
    fun parse(text: String): List<ContentType> = runCatching {
        val a = org.json.JSONArray(text)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val t = o.optJSONArray("titles")
            ContentType(o.getString("name"), o.optInt("perMonth"), o.optInt("sessions", 1), o.optInt("minutes", 60),
                if (t == null) emptyList() else (0 until t.length()).map { t.getString(it) })
        }
    }.getOrDefault(emptyList())

    fun encode(list: List<ContentType>): String {
        val a = org.json.JSONArray()
        list.forEach { c ->
            a.put(org.json.JSONObject().put("name", c.name).put("perMonth", c.perMonth).put("sessions", c.sessions).put("minutes", c.minutes)
                .put("titles", org.json.JSONArray(c.titles)))
        }
        return a.toString()
    }

    /**
     * The month's work steps: every video's sessions, interleaved so long videos (hero) spread over the month
     * while short ones keep coming. The last session of each video is its publishing.
     */
    fun monthSteps(types: List<ContentType>): List<Stage> {
        data class Video(val type: ContentType, val n: Int, var next: Int = 0)
        val videos = mutableListOf<Video>()
        types.forEach { t -> for (n in 1..t.perMonth.coerceAtLeast(0)) videos += Video(t, n) }
        val out = mutableListOf<Stage>()
        val active = videos.toMutableList()
        while (active.isNotEmpty()) {
            val it = active.iterator()
            while (it.hasNext()) {
                val v = it.next()
                val title = v.type.titles.getOrNull(v.n - 1)?.takeIf { s -> s.isNotBlank() }?.let { s -> "«$s»" } ?: "${v.n}"
                val k = v.next + 1
                val total = v.type.sessions.coerceAtLeast(1)
                out += Stage(if (k == total) "نشر ${v.type.name} $title" else "${v.type.name} $title · جلسة $k/$total", v.type.minutes)
                v.next++
                if (v.next >= total) it.remove()
            }
        }
        return out
    }
}

data class PageRange(val from: Int, val to: Int) {
    val count get() = to - from + 1
}

data class Suggestion(val perDay: Int, val week: Int, val month: Int, val projectedEnd: LocalDate?)

object PlanMath {
    fun remaining(p: Plan): Int = when (p.kind) {
        "memorize", "amount" -> (p.total - p.done).coerceAtLeast(0)
        "cycle" -> if (p.total > 0) (p.total - p.done).coerceAtLeast(0) else Int.MAX_VALUE
        else -> Int.MAX_VALUE
    }

    /** read: daily pages, from the khatma length when it is set. */
    fun readPerDay(p: Plan): Int = when {
        p.khatmaDays > 0 -> ceil(p.total / p.khatmaDays.toDouble()).toInt().coerceAtLeast(1)
        p.perDay > 0 -> p.perDay
        else -> 10
    }

    /** Smallest daily amount that finishes the plan by its end date. */
    fun suggestedPerDay(p: Plan, today: LocalDate): Int {
        val days = (ChronoUnit.DAYS.between(today, LocalDate.parse(p.endDay)) + 1).coerceAtLeast(1)
        return when (p.kind) {
            "memorize", "amount" -> ceil(remaining(p) / days.toDouble()).toInt().coerceAtLeast(if (remaining(p) > 0) 1 else 0)
            "read" -> ceil(p.total / days.toDouble()).toInt().coerceAtLeast(1)
            else -> 0
        }
    }

    fun perDay(p: Plan, today: LocalDate): Int = if (p.perDay > 0) p.perDay else suggestedPerDay(p, today)

    fun suggestion(p: Plan, today: LocalDate): Suggestion {
        val d = perDay(p, today)
        val rem = remaining(p)
        val daysLeftInMonth = ChronoUnit.DAYS.between(today, today.with(TemporalAdjusters.lastDayOfMonth())) + 1
        val week = minOf(rem.toLong(), d * 7L).toInt()
        val month = minOf(rem.toLong(), d * daysLeftInMonth).toInt()
        val end = if (rem == Int.MAX_VALUE || d <= 0) null else today.plusDays((ceil(rem / d.toDouble()).toLong() - 1).coerceAtLeast(0))
        return Suggestion(d, week, month, end)
    }

    /** Pages for today. [memorizedLast] is the last memorized page (used by review). */
    fun today(p: Plan, today: LocalDate, memorizedLast: Int = 0): PageRange? {
        val d = perDay(p, today)
        return when (p.kind) {
            "memorize", "amount" -> {
                if (p.done >= p.total || d <= 0) null
                else PageRange(p.done + 1, minOf(p.total, p.done + d))
            }
            "read" -> {
                val start = wrap(p.pointer, p.total)
                PageRange(start, start + d - 1) // may run past the end; shown as a count from [from]
            }
            "review" -> {
                val first = p.startPage
                if (memorizedLast < first) null
                else {
                    val memorized = memorizedLast - first + 1
                    val chunk = ceil(memorized / p.cycleDays.coerceAtLeast(1).toDouble()).toInt().coerceAtLeast(1)
                    val start = if (p.pointer < first || p.pointer > memorizedLast) first else p.pointer
                    PageRange(start, minOf(memorizedLast, start + chunk - 1))
                }
            }
            else -> null
        }
    }

    /** Plan after today's amount is done. */
    fun advance(p: Plan, range: PageRange, memorizedLast: Int = 0): Plan = when (p.kind) {
        "memorize", "amount" -> p.copy(done = maxOf(p.done, range.to))
        "read" -> p.copy(pointer = wrap(range.to + 1, p.total))
        "review" -> p.copy(pointer = if (range.to >= memorizedLast) p.startPage else range.to + 1)
        else -> p
    }

    /** Pages in a read range, wrapped around the end of the mushaf. */
    fun pages(range: PageRange, total: Int): List<Int> = (range.from..range.to).map { wrap(it, total) }

    fun wrap(page: Int, total: Int): Int = ((page - 1).mod(total.coerceAtLeast(1))) + 1

    fun title(p: Plan, r: PageRange): String = when (p.kind) {
        "memorize" -> if (r.count == 1) "حفظ: صفحة ${r.from}" else "حفظ: صفحات ${r.from}–${r.to}"
        "read" -> "ورد القرآن: ${r.count} صفحات (من ص ${r.from})"
        "review" -> "مراجعة سماع: صفحات ${r.from}–${r.to}"
        else -> "${p.title}: ${p.unit} ${r.from}–${r.to}"
    }

    fun minutes(p: Plan, r: PageRange): Int = (r.count * p.minutesPerUnit).coerceIn(5, 600)

    // ---------- sports goals: yearly target split into month / week ----------

    /** Value the goal should reach on [day], moving linearly from [baseline] at [start] to [reference] at [target]. */
    fun goalValueAt(baseline: Double, reference: Double, start: LocalDate, target: LocalDate, day: LocalDate): Double {
        val total = ChronoUnit.DAYS.between(start, target).coerceAtLeast(1)
        val passed = ChronoUnit.DAYS.between(start, day).coerceIn(0, total)
        return baseline + (reference - baseline) * passed / total
    }

    fun endOfWeek(day: LocalDate): LocalDate = day.plusDays(6L - ((day.dayOfWeek.value + 1) % 7)) // week ends Friday

    fun startOfWeek(day: LocalDate): LocalDate = endOfWeek(day).minusDays(6) // Saturday

    // ---------- daily amounts, used for the schedule ----------

    private fun memorized(p: Plan, memLast: Int) = (memLast - p.startPage + 1).coerceAtLeast(0)

    /** The amount for one day. Memorize/amount: the user's daily number. Review: the user's pages or the cycle share. */
    fun quota(p: Plan, day: LocalDate, memLast: Int): Int {
        if (!p.configured) return 0
        return when (p.kind) {
            "memorize", "amount" -> minOf(remaining(p), if (p.perDay > 0) p.perDay else suggestedPerDay(p, day))
            // A day's wird never crosses page 604: the next khatma starts fresh the day after.
            "read" -> minOf(readPerDay(p), p.total - p.pointer.coerceIn(1, p.total) + 1)
            "cycle" -> if (p.stageList.isEmpty() || remaining(p) <= 0) 0 else maxOf(1, ceil(p.perWeek * p.stageList.size / 7.0).toInt())
            "review" -> {
                val mem = memorized(p, memLast)
                when {
                    mem <= 0 -> 0
                    p.perDay > 0 -> minOf(p.perDay, mem)
                    else -> ceil(mem / p.cycleDays.coerceAtLeast(1).toDouble()).toInt().coerceAtLeast(1)
                }
            }
            else -> 0
        }
    }

    /** Plan after [n] units are done. */
    fun applyUnits(p: Plan, n: Int, memLast: Int): Plan = when (p.kind) {
        "memorize", "amount" -> p.copy(done = minOf(p.total, p.done + n))
        "read" -> {
            val next = p.pointer + n
            if (next > p.total) p.copy(pointer = 1, done = p.done + 1) else p.copy(pointer = next)
        }
        "cycle" -> {
            val size = p.stageList.size.coerceAtLeast(1)
            val idx = p.pointer.coerceAtLeast(0) + n
            p.copy(pointer = idx % size, done = p.done + idx / size)
        }
        "review" -> {
            val mem = memorized(p, memLast)
            if (mem <= 0) p else {
                val first = p.startPage
                val idx = (p.pointer.coerceIn(first, memLast) - first + n).mod(mem)
                p.copy(pointer = first + idx)
            }
        }
        else -> p
    }

    /** Memorized pages to listen to, starting [offset] pages after the pointer, wrapping inside the memorized range. */
    fun reviewPages(p: Plan, offset: Int, count: Int, memLast: Int): List<Int> {
        val mem = memorized(p, memLast)
        if (mem <= 0 || count <= 0) return emptyList()
        val first = p.startPage
        val start = p.pointer.coerceIn(first, memLast) - first + offset
        return (0 until count).map { first + (start + it).mod(mem) }
    }

    /** Title of a phase that covers [count] units, [offset] units after where the plan stands. */
    fun phaseTitle(p: Plan, offset: Int, count: Int, memLast: Int): String = when (p.kind) {
        "memorize" -> {
            val a = p.done + 1 + offset
            val b = minOf(p.total, a + count - 1)
            if (count <= 1) "حفظ: صفحة $a" else "حفظ: صفحات $a–$b"
        }
        "read" -> {
            val a = p.pointer.coerceIn(1, p.total) + offset
            val b = minOf(p.total, a + count - 1)
            if (a > p.total) "ورد القرآن" else "ورد القرآن: صفحات $a–$b"
        }
        "cycle" -> {
            val st = p.stageList
            if (st.isEmpty()) p.title else (0 until count.coerceAtLeast(1)).joinToString(" + ") { st[(p.pointer.coerceAtLeast(0) + offset + it) % st.size] }.let { "${p.title}: $it" }
        }
        "review" -> {
            val pg = reviewPages(p, offset, count, memLast)
            when {
                pg.isEmpty() -> "سماع المحفوظ"
                pg.last() >= pg.first() -> "سماع المحفوظ: صفحات ${pg.first()}–${pg.last()}"
                else -> "سماع المحفوظ: $count صفحة من ص ${pg.first()}"
            }
        }
        else -> {
            val a = p.done + 1 + offset
            "${p.title}: ${p.unit} $a–${a + count - 1}"
        }
    }

    /** The day's amount when the user set this month's target: what is left, spread over the days left. */
    fun quotaWithMonth(p: Plan, day: LocalDate, memLast: Int, monthTarget: Double?): Int {
        if (monthTarget == null || !p.configured) return quota(p, day, memLast)
        val end = day.with(TemporalAdjusters.lastDayOfMonth())
        val daysLeft = (ChronoUnit.DAYS.between(day, end) + 1).toInt().coerceAtLeast(1)
        return when (p.kind) {
            "memorize", "amount" -> minOf(remaining(p), ceil((monthTarget - p.done).coerceAtLeast(0.0) / daysLeft).toInt())
            "read" -> minOf(ceil(monthTarget / day.lengthOfMonth()).toInt().coerceAtLeast(1), p.total - p.pointer.coerceIn(1, p.total) + 1)
            else -> quota(p, day, memLast)
        }
    }

    /** cycle: rounds per week needed to reach this month's target. */
    fun cyclePerWeekForMonth(p: Plan, day: LocalDate, monthTarget: Double): Double {
        val end = day.with(TemporalAdjusters.lastDayOfMonth())
        val weeks = ((ChronoUnit.DAYS.between(day, end) + 1) / 7.0).coerceAtLeast(1.0 / 7)
        return ((monthTarget - p.done) / weeks).coerceIn(0.0, 14.0)
    }

    /** cycle: days between steps for its rounds per week. */
    fun cycleIntervalDays(p: Plan): Int {
        val perWeek = p.perWeek * p.stageList.size.coerceAtLeast(1)
        return if (perWeek <= 0) 7 else (7.0 / perWeek).toInt().coerceIn(1, 7)
    }

    /** Where the plan should be today on a straight line from its start, and where it is. */
    fun pace(p: Plan, today: LocalDate): Pair<Double, Double>? {
        if (!p.configured || p.startDay.isEmpty() || p.kind !in setOf("memorize", "amount", "cycle")) return null
        val days = ChronoUnit.DAYS.between(LocalDate.parse(p.startDay), today).toInt()
        if (days <= 0) return null
        val perDay = when (p.kind) {
            "cycle" -> p.perWeek / 7.0
            else -> (if (p.perDay > 0) p.perDay else suggestedPerDay(p, today)).toDouble()
        }
        return (p.startDone + perDay * days) to p.done.toDouble()
    }

    /** Minutes for [count] units starting [offset] after the pointer (cycle steps can each have their own length). */
    fun unitMinutes(p: Plan, offset: Int, count: Int): Int {
        if (p.kind != "cycle") return count * p.minutesPerUnit
        val st = p.stageItems
        if (st.isEmpty()) return count * p.minutesPerUnit
        return (0 until count).sumOf { st[(p.pointer.coerceAtLeast(0) + offset + it) % st.size].minutes.takeIf { m -> m > 0 } ?: p.minutesPerUnit }
    }

    /** Details of the next step of a cycle plan. */
    fun stageDetails(p: Plan, offset: Int): String =
        p.stageItems.takeIf { it.isNotEmpty() }?.let { it[(p.pointer.coerceAtLeast(0) + offset) % it.size].details } ?: ""

    /** Plan state after [days] days, assuming each day's amount gets done. */
    fun project(p: Plan, days: Int, memLast: Int, today: LocalDate): Plan {
        var q = p
        for (i in 0 until days) q = applyUnits(q, quota(q, today.plusDays(i.toLong()), memLast), memLast)
        return q
    }

    fun endOfMonth(day: LocalDate): LocalDate = day.with(TemporalAdjusters.lastDayOfMonth())
}
