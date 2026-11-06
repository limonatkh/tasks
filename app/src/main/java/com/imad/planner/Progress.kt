package com.imad.planner

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Points, levels and achievements, worked out from what is already saved (tasks, goals, goal logs). Nothing here
 * is stored except which level was last celebrated, so the numbers can never drift from the real history and
 * deleting a task takes its points with it.
 *
 * Points:
 *  - a finished task: 10, plus one per real minute on its timer, at most 1.5× its estimate;
 *    finished without the timer: its estimate (times the part done);
 *  - time on a task that is not finished yet still counts its minutes (the work was done);
 *  - a goal passing a quarter of the way (25, 50, 75, 100 %): 50 each;
 *  - a new personal record in a goal's log: 30.
 */
object Progress {
    const val TASK = 10
    const val MILESTONE = 50
    const val RECORD = 30

    /** Names of the level ranges, from a candle to the morning star. */
    private val names = listOf(1 to "شمعة", 3 to "قنديل", 5 to "فانوس", 8 to "سراج", 12 to "منارة", 17 to "نجم الصبح")

    fun levelName(level: Int): String = names.last { level >= it.first }.second

    /** Points needed to reach level [n] (level 1 is the start). Level n+1 needs 50·n·(n+1). */
    fun need(n: Int, step: Int = 50): Long = step.toLong() * (n - 1) * n

    /** Level for a total: the largest n with need(n) ≤ points. */
    fun level(points: Long, step: Int = 50): Int {
        if (points <= 0) return 1
        var n = floor((1 + sqrt(1.0 + 4.0 * points / step)) / 2).toInt().coerceAtLeast(1)
        while (need(n + 1, step) <= points) n++
        while (n > 1 && need(n, step) > points) n--
        return n
    }

    /** Points of one task as it is now. */
    fun pointsOf(t: Task, now: Long = System.currentTimeMillis()): Int {
        if (t.state == "skipped") return 0
        val spentMin = (Maths.spent(t, now) / 60_000).toInt()
        val cap = ceil(t.minutes * 1.5).toInt().coerceAtLeast(1)
        return when {
            t.done && spentMin > 0 -> TASK + spentMin.coerceAtMost(cap)
            t.done -> TASK + (t.minutes * t.pct.coerceIn(0, 100) / 100)
            else -> spentMin.coerceAtMost(cap)
        }
    }

    class Level(val level: Int, val name: String, val points: Long, val from: Long, val to: Long) {
        val left get() = to - points
        val fraction get() = if (to == from) 0f else ((points - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    }

    fun levelOf(points: Long, step: Int = 50): Level {
        val n = level(points, step)
        return Level(n, levelName(n), points, need(n, step), need(n + 1, step))
    }

    class Badge(val key: String, val title: String, val line: String, val earned: Boolean, val day: String? = null,
                val left: String? = null, val domain: Domain? = null, val order: Double = 0.0, val near: Float = 0f)

    class Summary(
        val total: Level,
        val domains: List<Pair<Domain, Level>>,
        val minutes: Map<Domain, Int>,
        val committedWeeks: Int,
        val committedRun: Int,
        val doneCount: Int,
        val badges: List<Badge>,
        val goalPaths: List<GoalPath>,
    )

    /** A goal as stations on a road: four quarters, which are passed, where it is now. */
    class GoalPath(val id: String, val name: String, val unit: String, val fraction: Float, val now: Double, val target: Double, val lower: Boolean)

    fun goalFraction(g: Goal): Float {
        val span = g.reference - g.baseline
        if (span == 0.0) return if (g.current == g.reference) 1f else 0f
        return ((g.current - g.baseline) / span).toFloat().coerceIn(0f, 1f)
    }

    /** A week counts as kept when at least 70% of what was planned in it got done (and it had at least 5 tasks). */
    fun committed(tasks: List<Task>): Boolean {
        val planned = tasks.filter { it.state != "skipped" || it.note.isEmpty() || it.note == "ما انعملت" }
        if (planned.size < 5) return false
        return planned.count { it.done } >= 0.7 * planned.size
    }

    fun summary(tasks: List<Task>, goals: List<Goal>, logs: List<GoalLog>, settings: Settings, today: LocalDate, now: Long = System.currentTimeMillis()): Summary {
        val byDomain = mutableMapOf<Domain, Long>()
        val minutes = mutableMapOf<Domain, Int>()
        var total = 0L
        for (t in tasks) {
            val p = pointsOf(t, now)
            if (p == 0) continue
            val d = domainOf(t, settings)
            byDomain[d] = (byDomain[d] ?: 0) + p
            total += p
            val m = if (t.done && t.spent == 0L) t.minutes * t.pct.coerceIn(0, 100) / 100 else (Maths.spent(t, now) / 60_000).toInt()
            minutes[d] = (minutes[d] ?: 0) + m
        }
        val badges = mutableListOf<Badge>()

        // Goals: quarters passed, and records in their logs.
        val paths = goals.map { g -> GoalPath(g.id, g.name, g.unit, goalFraction(g), g.current, g.reference, g.lower) }
        val quarterNames = listOf("ربع", "نص", "ثلاث أرباع", "خلصت")
        val logsBy = logs.groupBy { it.goalId }
        for (g in goals) {
            val f = goalFraction(g)
            val d = domainOf(Cat.ofGoal(g), null)
            val passed = (1..4).count { f >= it / 4f - 1e-6f }
            repeat(passed) { q ->
                total += MILESTONE; byDomain[d] = (byDomain[d] ?: 0) + MILESTONE
                val dayReached = logsBy[g.id].orEmpty().sortedBy { it.day }.firstOrNull { l ->
                    val span = g.reference - g.baseline
                    span != 0.0 && (l.value - g.baseline) / span >= (q + 1) / 4.0 - 1e-6
                }?.day
                badges += Badge("g:${g.id}:$q", "${quarterNames[q]} ${g.name}", "${num(g.current)} من ${num(g.reference)} ${g.unit}", true, dayReached, domain = d, order = 3.0)
            }
            if (passed < 4) {
                val nextQ = passed
                val need = g.baseline + (g.reference - g.baseline) * (nextQ + 1) / 4.0
                val gap = kotlin.math.abs(need - g.current)
                badges += Badge("g:${g.id}:$nextQ", "${quarterNames[nextQ]} ${g.name}", "", false, left = "باقي ${num(Math.round(gap * 10) / 10.0)} ${g.unit}", domain = d, order = 3.0, near = ((f - passed / 4f) * 4f).coerceIn(0f, 1f))
            }
            // Personal records: a logged value better than everything before it (the starting value included).
            var best = g.baseline
            for (l in logsBy[g.id].orEmpty().sortedBy { it.day }) {
                val better = if (g.lower) l.value < best else l.value > best
                if (better) {
                    total += RECORD; byDomain[d] = (byDomain[d] ?: 0) + RECORD
                    badges += Badge("r:${g.id}:${l.day}", "رقم جديد", "${num(l.value)} ${g.unit} · ${g.name}", true, l.day, domain = d, order = 4.0)
                    best = l.value
                }
            }
        }

        // Counts of finished tasks.
        val done = tasks.filter { it.done }.sortedWith(compareBy({ it.day }, { it.finishedAt }))
        for ((n, title) in listOf(1 to "أول مهمة", 10 to "١٠ مهام", 50 to "٥٠ مهمة", 100 to "١٠٠ مهمة", 250 to "٢٥٠ مهمة", 500 to "٥٠٠ مهمة", 1000 to "ألف مهمة")) {
            if (done.size >= n) badges += Badge("n:$n", title, "خلّصتها كلها بإيدك", true, done[n - 1].day, order = 1.0)
            else { badges += Badge("n:$n", title, "", false, left = "باقي ${n - done.size}", order = 1.0, near = done.size.toFloat() / n); break }
        }
        // Hours in each domain.
        for ((d, m) in minutes) {
            val h = m / 60
            for (tier in listOf(5, 10, 25, 50, 100, 250, 500)) {
                if (h >= tier) badges += Badge("h:${d.name}:$tier", "$tier ساعة ${d.title}", "وقت حقيقي على المؤقت أو تقديرك", true, domain = d, order = 2.0)
                else { badges += Badge("h:${d.name}:$tier", "$tier ساعة ${d.title}", "", false, left = "باقي ${tier - h} س", domain = d, order = 2.0, near = h.toFloat() / tier); break }
            }
        }
        // Kept weeks: whole weeks that are over.
        val thisWeek = PlanMath.startOfWeek(today)
        val weeks = tasks.groupBy { runCatching { PlanMath.startOfWeek(LocalDate.parse(it.day)) }.getOrNull() }
            .filterKeys { it != null && it < thisWeek }
            .mapKeys { it.key!! }.toSortedMap()
        val kept = weeks.filterValues { committed(it) }.keys.toList()
        var run = 0
        var w = thisWeek.minusWeeks(1)
        while (w in kept) { run++; w = w.minusWeeks(1) }
        for ((n, title) in listOf(1 to "أسبوع ملتزم", 4 to "٤ أسابيع ملتزمة", 12 to "١٢ أسبوع ملتزم", 26 to "نص سنة ملتزم")) {
            if (kept.size >= n) badges += Badge("w:$n", title, "٧٠٪ أو أكثر من خطة الأسبوع", true, kept[n - 1].plusDays(6).toString(), order = 2.5)
            else { badges += Badge("w:$n", title, "", false, left = "باقي ${n - kept.size}", order = 2.5, near = kept.size.toFloat() / n); break }
        }

        val dom = Domain.values().mapNotNull { d -> byDomain[d]?.takeIf { it > 0 }?.let { d to levelOf(it, 25) } }
            .sortedByDescending { it.second.points }
        return Summary(levelOf(total), dom, minutes, kept.size, run, done.size, badges, paths)
    }

    /** Earned ones, newest first. */
    fun earned(s: Summary) = s.badges.filter { it.earned }.sortedWith(compareByDescending<Badge> { it.day ?: "" }.thenByDescending { it.order })

    /** The nearest ones still to get. */
    fun next(s: Summary) = s.badges.filter { !it.earned }.sortedByDescending { it.near }
}
