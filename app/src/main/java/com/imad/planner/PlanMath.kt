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
)

data class PageRange(val from: Int, val to: Int) {
    val count get() = to - from + 1
}

data class Suggestion(val perDay: Int, val week: Int, val month: Int, val projectedEnd: LocalDate?)

object PlanMath {
    fun remaining(p: Plan): Int = when (p.kind) {
        "memorize", "amount" -> (p.total - p.done).coerceAtLeast(0)
        else -> Int.MAX_VALUE
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

    fun endOfMonth(day: LocalDate): LocalDate = day.with(TemporalAdjusters.lastDayOfMonth())
}
