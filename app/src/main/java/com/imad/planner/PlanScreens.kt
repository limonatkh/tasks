@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.imad.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class MonthTarget(val key: String, val ym: YearMonth, val title: String, val unit: String, val default: Double, val lower: Boolean)

data class YearTarget(val goal: Goal? = null, val plan: Plan? = null) {
    val key get() = goal?.let { "g:${it.id}" } ?: "p:${plan!!.id}"
}

// ---------------- Monthly targets (defaults) ----------------

/** Where a numeric goal should stand at the end of [ym], on a straight line to its yearly target. */
fun goalMonthDefault(g: Goal, ym: YearMonth): Double {
    val target = LocalDate.parse(g.targetDay)
    val end = minOf(ym.atEndOfMonth(), target)
    val v = PlanMath.goalValueAt(g.baseline, g.reference, LocalDate.parse(g.startDay), target, end)
    return Math.round(v * 10) / 10.0
}

/** Memorize / amount: last page by the end of the month. Read: pages in that month. */
fun planMonthDefault(p: Plan, ym: YearMonth, today: LocalDate, mem: Int): Double? {
    if (!p.configured) return null
    val end = ym.atEndOfMonth()
    if (end.isBefore(today)) return null
    val first = maxOf(today, ym.atDay(1))
    val perDay = PlanMath.quota(p, today, mem).coerceAtLeast(0)
    return when (p.kind) {
        "memorize", "amount" -> minOf(p.total, p.done + perDay * (ChronoUnit.DAYS.between(today, end).toInt() + 1)).toDouble()
        "read" -> (perDay * (ChronoUnit.DAYS.between(first, end).toInt() + 1)).toDouble()
        "cycle" -> minOf(if (p.total > 0) p.total.toDouble() else Double.MAX_VALUE, Math.floor((p.done + p.perWeek * (ChronoUnit.DAYS.between(today, end) + 1) / 7.0) * 10) / 10)
        else -> null
    }
}

fun planMonthText(p: Plan, v: Double): String = when (p.kind) {
    "memorize" -> "حتى صفحة ${num(v)}"
    "amount" -> "حتى ${num(v)} ${p.unit}"
    "read" -> "${num(v)} صفحة"
    "cycle" -> "${num(v)} ${p.unit}"
    else -> num(v)
}

fun yearMonths(y: YearTarget, today: LocalDate, mem: Int): List<YearMonth> {
    val start = YearMonth.from(today)
    val end = when {
        y.goal != null -> YearMonth.from(LocalDate.parse(y.goal.targetDay))
        else -> {
            val p = y.plan!!
            val s = PlanMath.suggestion(p, today)
            if (p.kind == "memorize" || p.kind == "amount") YearMonth.from(s.projectedEnd ?: LocalDate.parse(p.endDay)) else start.plusMonths(11)
        }
    }
    val n = (ChronoUnit.MONTHS.between(start, end).toInt() + 1).coerceIn(1, 24)
    return (0 until n).map { start.plusMonths(it.toLong()) }
}

fun unreviewed(y: YearTarget, s: Snapshot): Int {
    if (y.plan != null && y.plan.kind == "review") return 0
    return yearMonths(y, s.today, s.memLast).count { s.months[Months.key(y.key, it.toString())]?.reviewed != true }
}

fun targetsIn(cat: String, s: Snapshot): List<YearTarget> =
    s.goals.filter { Cat.ofGoal(it) == cat }.map { YearTarget(goal = it) } +
        s.plans.filter { Cat.ofPlan(it) == cat }.map { YearTarget(plan = it) }

/** Recurring tasks that are not tied to a goal or plan (boxing, reading a book…). */
fun habitsIn(cat: String, s: Snapshot): List<Rule> =
    s.rules.filter { Cat.ofRule(it) == cat && !it.id.startsWith("plan-") && Members.goals(it).isEmpty() }

// ---------------- Plan tab ----------------

@Composable
fun PlanTab(s: Snapshot, nav: Nav) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("أسبوع", "شهر", "سنة").forEachIndexed { i, label ->
                FilterChip(selected = mode == i, onClick = { mode = i }, label = { Text(label, fontSize = 16.sp) })
            }
            Box(Modifier.weight(1f))
            TextButton(onClick = nav.groups) { Text("المجموعات") }
            TextButton(onClick = nav.newGoal) { Text("+ هدف") }
        }
        when (mode) {
            0 -> WeekView(s, nav)
            1 -> MonthView(s, nav)
            else -> YearView(s, nav)
        }
    }
}

// ---------------- Week: days across, tasks down ----------------

private data class WeekRow(val key: String, val title: String, val category: String, val cells: Map<LocalDate, List<Task>>, val rule: Rule?, val sample: Task)

@Composable
fun WeekView(s: Snapshot, nav: Nav) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val start = PlanMath.startOfWeek(s.today).plusWeeks(offset.toLong())
    val days = (0 until 7).map { start.plusDays(it.toLong()) }
    val proj = remember(s.tasks, s.rules, s.plans, s.settings, s.today, offset) {
        Projector.project(start, 7, s.today, s.tasks, s.rules, s.plans, s.settings)
    }
    val rows = remember(proj) {
        val map = linkedMapOf<String, MutableMap<LocalDate, MutableList<Task>>>()
        val samples = mutableMapOf<String, Task>()
        proj.forEach { (d, entries) ->
            entries.forEach { e ->
                val t = e.task
                val key = t.ruleId ?: t.note.takeIf { it.startsWith("grp:") } ?: t.id
                map.getOrPut(key) { mutableMapOf() }.getOrPut(d) { mutableListOf() } += t
                samples.putIfAbsent(key, t)
            }
        }
        map.map { (k, cells) ->
            val sample = samples.getValue(k)
            val rule = s.rules.firstOrNull { it.id == sample.ruleId }
            WeekRow(k, rule?.title ?: sample.baseTitle, sample.category, cells, rule, sample)
        }.sortedBy { r -> r.cells.values.flatten().minOfOrNull { if (it.planned == 0L) Long.MAX_VALUE else Clock.minuteOfDay(it.planned).toLong() } ?: 0L }
    }
    var selected by remember { mutableStateOf<String?>(null) }
    val open = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { offset-- }) { Text("‹ السابق") }
                Text("${start.dayOfMonth}/${start.monthValue} – ${start.plusDays(6).dayOfMonth}/${start.plusDays(6).monthValue}",
                    Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { offset++ }) { Text("التالي ›") }
            }
        }
        item { WeekHeader(days, s.today) }
        for (cat in Cat.keys) {
            val inCat = rows.filter { Cat.known(it.category) == cat }
            if (inCat.isEmpty()) continue
            val expanded = open[cat] ?: true
            item(key = "h-$cat") { CategoryHeader(Cat.name(cat), inCat.size, expanded) { open[cat] = !expanded } }
            if (expanded) for (r in inCat) {
                item(key = "r-${r.key}") {
                    Column {
                        WeekRowView(r, days, s.today, selected == r.key) { selected = if (selected == r.key) null else r.key }
                        if (selected == r.key) WeekDetail(r, days, nav)
                    }
                }
            }
        }
        if (rows.isEmpty()) item { Muted("لا مهام في هذا الأسبوع.") }
    }
}

@Composable
private fun WeekHeader(days: List<LocalDate>, today: LocalDate) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f))
        days.forEach { d ->
            Column(Modifier.width(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(shortDay(d), fontSize = 12.sp, fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
                    color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${d.dayOfMonth}", fontSize = 12.sp, color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WeekRowView(r: WeekRow, days: List<LocalDate>, today: LocalDate, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).background(bg, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(r.title, Modifier.weight(1f).padding(horizontal = 6.dp), fontSize = 15.sp, maxLines = 2)
        days.forEach { d ->
            Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                val list = r.cells[d]
                if (list != null) {
                    val t = list.first()
                    val color = when {
                        list.all { it.done } -> MaterialTheme.colorScheme.tertiary
                        d.isBefore(today) -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.primary
                    }
                    ClockIcon(if (t.planned > 0) Clock.minuteOfDay(t.planned) else null, if (selected) 24.dp else 20.dp, color)
                }
            }
        }
    }
}

@Composable
private fun WeekDetail(r: WeekRow, days: List<LocalDate>, nav: Nav) {
    Card(Modifier.fillMaxWidth().padding(top = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { d ->
                r.cells[d]?.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(dayLabel(d), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TimeLine(t.planned, t.minutes)
                    }
                }
            }
            r.rule?.let { Muted(ruleSummary(it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (r.rule != null) Action("تعديل المهمة المتكررة", { nav.openRule(r.rule) })
                else Action("تعديل", { nav.openTask(r.sample) })
            }
        }
    }
}

fun ruleSummary(r: Rule): String = buildList {
    add(if (r.weekdays != 0) weekdayNames.filterIndexed { i, _ -> r.weekdays and (1 shl i) != 0 }.joinToString("، ") else "كل ${r.everyHours} ساعة")
    add(durLabel(r.minutes))
    add(if (r.preferredMinute >= 0) (if (r.pinned) "ثابت " else "يفضّل ") + fmt12(r.preferredMinute) else "وقت تلقائي")
    if (parsePhases(r.phases).size > 1) add("على ${parsePhases(r.phases).size} فترات")
    if (!r.active) add("متوقفة")
}.joinToString(" · ")

// ---------------- Month: the result each task should reach ----------------

@Composable
fun MonthView(s: Snapshot, nav: Nav) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val ym = YearMonth.from(s.today).plusMonths(offset.toLong())
    val monthStart = ym.atDay(1); val monthEnd = ym.atEndOfMonth()
    val projFrom = maxOf(s.today.plusDays(1), monthStart)
    val projDays = if (monthEnd.isBefore(projFrom)) 0 else ChronoUnit.DAYS.between(projFrom, monthEnd).toInt() + 1
    val proj = remember(s.tasks, s.rules, s.plans, s.settings, s.today, offset) {
        if (projDays > 0) Projector.project(projFrom, projDays, s.today, s.tasks, s.rules, s.plans, s.settings) else emptyMap()
    }
    fun sessions(ruleIds: Set<String>): Pair<Int, Int> {
        val done = s.tasks.count { it.ruleId in ruleIds && it.done && it.day >= monthStart.toString() && it.day <= monthEnd.toString() && it.phase == 0 }
        val todayOpen = if (!s.today.isBefore(monthStart) && !s.today.isAfter(monthEnd)) s.tasks.count { it.ruleId in ruleIds && it.day == s.today.toString() && it.open && it.phase == 0 } else 0
        val planned = proj.values.sumOf { l -> l.count { it.task.ruleId in ruleIds && it.task.phase == 0 && it.task.open } }
        return (done + todayOpen + planned) to done
    }
    val open = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (offset > 0) offset-- }, enabled = offset > 0) { Text("‹ السابق") }
                Text(monthLabel(ym), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { offset++ }) { Text("التالي ›") }
            }
        }
        for (cat in Cat.keys) {
            val targets = targetsIn(cat, s).filter { !(it.plan?.kind == "review") }
            val habits = habitsIn(cat, s).filter { it.active }
            val reviewPlans = s.plans.filter { Cat.ofPlan(it) == cat && it.kind == "review" && it.accepted }
            val count = targets.size + habits.size + reviewPlans.size
            if (count == 0) continue
            val expanded = open[cat] ?: true
            item(key = "mh-$cat") { CategoryHeader(Cat.name(cat), count, expanded) { open[cat] = !expanded } }
            if (!expanded) continue
            for (y in targets) item(key = "m-${y.key}") { MonthTargetCard(y, ym, s, nav, sessions(trainingRuleIds(y, s))) }
            for (r in habits) item(key = "mr-${r.id}") {
                val (planned, done) = sessions(setOf(r.id))
                Panel(Modifier.clickable { nav.openRule(r) }) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium)
                    Text("الجلسات هذا الشهر: $planned · منجز $done", style = MaterialTheme.typography.bodyLarge)
                }
            }
            for (p in reviewPlans) item(key = "mv-${p.id}") {
                val (planned, done) = sessions(setOf("plan-${p.id}"))
                Panel(Modifier.clickable { nav.openPlan(p) }) {
                    Text(p.title, style = MaterialTheme.typography.titleMedium)
                    Text("أيام السماع: $planned · منجز $done", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

fun trainingRuleIds(y: YearTarget, s: Snapshot): Set<String> =
    if (y.goal != null) s.rules.filter { y.goal.id in Members.goals(it) }.map { it.id }.toSet() else setOf("plan-${y.plan!!.id}")

@Composable
private fun MonthTargetCard(y: YearTarget, ym: YearMonth, s: Snapshot, nav: Nav, sessions: Pair<Int, Int>) {
    val entry = s.months[Months.key(y.key, ym.toString())]
    val g = y.goal; val p = y.plan
    val default = if (g != null) goalMonthDefault(g, ym) else planMonthDefault(p!!, ym, s.today, s.memLast)
    val value = entry?.value ?: default
    val title = g?.name ?: p!!.title
    val unit = g?.unit ?: p!!.unit
    Panel(Modifier.clickable {
        if (default != null) nav.editMonth(MonthTarget(y.key, ym, title, unit, default, g?.lower ?: false))
        else p?.let(nav.openPlan)
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (default != null) ReviewBadge(entry?.reviewed == true)
        }
        if (value != null) Text(
            "هدف الشهر: " + if (g != null) "${num(value)} ${g.unit}" else planMonthText(p!!, value),
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
        )
        else Muted(if (p?.configured == false) "حدّد وين وصلت أولاً" else "لا هدف لهذا الشهر")
        if (g != null) Muted("الحالي: ${num(g.current)} ${g.unit} · السنة: ${num(g.reference)}")
        if (p != null && (p.kind == "memorize" || p.kind == "amount")) Muted("الحالي: ${p.done} / ${p.total}")
        if (sessions.first > 0) Muted("الجلسات: ${sessions.first} · منجز ${sessions.second}")
    }
}

// ---------------- Year ----------------

@Composable
fun YearView(s: Snapshot, nav: Nav) {
    val open = remember { mutableStateMapOf<String, Boolean>() }
    val inactiveSport = s.rules.filter { !it.active && it.category == "sport" && Members.goals(it).isNotEmpty() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Panel {
                Text("الخطة السنوية", style = MaterialTheme.typography.titleMedium)
                Muted("اضغط على هدف لتشوف خطط أشهره. «مقترح» يعني لسا ما راجعته.")
                Action("+ هدف جديد", nav.newGoal, Modifier.fillMaxWidth())
            }
        }
        for (cat in Cat.keys) {
            val targets = targetsIn(cat, s)
            val habits = habitsIn(cat, s)
            val count = targets.size + habits.size
            if (count == 0) continue
            val pending = targets.sumOf { unreviewed(it, s) }
            val expanded = open[cat] ?: true
            item(key = "yh-$cat") { CategoryHeader(Cat.name(cat), count, expanded, extra = if (pending > 0) "$pending مقترح" else null) { open[cat] = !expanded } }
            if (!expanded) continue
            if (cat == "sport" && inactiveSport.isNotEmpty()) item(key = "ys-on") {
                Panel {
                    Text("تدريبات بانتظار تفعيلك: ${inactiveSport.size}", style = MaterialTheme.typography.bodyLarge)
                    Muted(inactiveSport.joinToString("، ") { it.title })
                    Action("تفعيلها وتوزيعها على الأيام", { nav.acceptGoals(inactiveSport.flatMap { Members.goals(it) }) }, Modifier.fillMaxWidth())
                }
            }
            for (y in targets) item(key = "y-${y.key}") { YearTargetCard(y, s, nav) }
            for (r in habits) item(key = "yr-${r.id}") {
                Panel(Modifier.clickable { nav.openRule(r) }) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium)
                    Muted(ruleSummary(r))
                }
            }
        }
    }
}

@Composable
private fun YearTargetCard(y: YearTarget, s: Snapshot, nav: Nav) {
    val g = y.goal; val p = y.plan
    val pending = unreviewed(y, s)
    Panel(Modifier.clickable { nav.openYear(y) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g?.name ?: p!!.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (pending > 0) Text("$pending مقترح", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        if (g != null) {
            val pct = Maths.achievement(g)
            Text("${num(g.current)} / ${num(g.reference)} ${g.unit}", style = MaterialTheme.typography.bodyLarge)
            LinearProgressIndicator(progress = { (pct / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Muted("${num(pct)}٪" + if (g.lower) " · الزمن الأقل أفضل" else "")
        } else if (p != null) {
            if (!p.configured) Text("اضغط لتحديد وين وصلت", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
            else if (p.kind == "memorize" || p.kind == "amount") {
                Text("${p.done} / ${p.total} ${p.unit}", style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(progress = { (p.done.toFloat() / p.total.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                PlanMath.suggestion(p, s.today).projectedEnd?.let { Muted("الانتهاء المتوقع: $it") }
            } else if (p.kind == "cycle") {
                Text("${p.done} / ${p.total} ${p.unit}", style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(progress = { (p.done.toFloat() / p.total.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Muted("المرحلة الجاية: ${PlanMath.phaseTitle(p, 0, 1, 0).substringAfter(": ")} · ${num(p.perWeek)} دورة بالأسبوع")
            } else Muted(if (p.kind == "read") "${PlanMath.quota(p, s.today, s.memLast)} صفحة يومياً" + if (p.done > 0) " · ختمت ${p.done} مرة" else "" else "${PlanMath.quota(p, s.today, s.memLast)} صفحة سماع يومياً")
            if (!p.accepted) Text("الخطة متوقفة", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A goal's yearly plan as months, each a suggested default until reviewed. */
@Composable
fun YearGoalSheet(y: YearTarget, s: Snapshot, nav: Nav, close: () -> Unit, acceptAll: (String, List<String>) -> Unit, acceptTraining: (List<String>) -> Unit, move: (String) -> Unit) {
    val g = y.goal?.let { gg -> s.goals.firstOrNull { it.id == gg.id } ?: gg }
    val p = y.plan?.let { pp -> s.plans.firstOrNull { it.id == pp.id } ?: pp }
    val target = YearTarget(g, p)
    val months = yearMonths(target, s.today, s.memLast)
    Sheet(g?.name ?: p!!.title, close) {
        if (g != null) {
            Text("الحالي ${num(g.current)} · هدف السنة ${num(g.reference)} ${g.unit} · حتى ${g.targetDay}", style = MaterialTheme.typography.bodyLarge)
            val training = s.rules.filter { g.id in Members.goals(it) }
            training.forEach { r -> Muted("${r.title}: ${ruleSummary(r)}") }
            if (training.any { !it.active }) Action("تفعيل التدريب وتوزيعه على الأيام", { acceptTraining(listOf(g.id)) }, Modifier.fillMaxWidth())
        }
        if (p != null) {
            if (!p.configured) Text("حدّد وين وصلت حتى تنحسب الأشهر.", style = MaterialTheme.typography.bodyLarge)
            if (p.kind == "review") Muted("المراجعة بتمشي على المحفوظ يومياً، ما إلها هدف شهري.")
        }
        if (!(p != null && (p.kind == "review" || !p.configured))) {
            Text("خطط الأشهر", style = MaterialTheme.typography.titleMedium)
            months.forEach { ym ->
                val entry = s.months[Months.key(target.key, ym.toString())]
                val default = if (g != null) goalMonthDefault(g, ym) else planMonthDefault(p!!, ym, s.today, s.memLast)
                if (default != null) {
                    val v = entry?.value ?: default
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                            nav.editMonth(MonthTarget(target.key, ym, g?.name ?: p!!.title, g?.unit ?: p!!.unit, default, g?.lower ?: false))
                        },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(monthLabel(ym), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(if (g != null) "${num(v)} ${g.unit}" else planMonthText(p!!, v), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        ReviewBadge(entry?.reviewed == true)
                    }
                    HorizontalDivider()
                }
            }
            val pendingMonths = months.filter { s.months[Months.key(target.key, it.toString())]?.reviewed != true }.map { it.toString() }
            if (pendingMonths.isNotEmpty()) Action("قبول كل المقترحات · ${pendingMonths.size}", { acceptAll(target.key, pendingMonths) }, Modifier.fillMaxWidth())
        }
        Text("المجموعة", style = MaterialTheme.typography.labelLarge)
        val current = if (g != null) Cat.ofGoal(g) else Cat.ofPlan(p!!)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Cat.keys.forEach { k -> FilterChip(selected = current == k, onClick = { if (k != current) move(k) }, label = { Text(Cat.name(k)) }) }
        }
        val allSeries = if (g != null) s.rules.filter { g.id in Members.goals(it) }.map { it.id } else listOf("plan-${p!!.id}")
        if (allSeries.isNotEmpty()) {
            val currentColor = s.settings.colors[allSeries.first()] ?: -1
            Text("لون مهامه (بتطلع بهاللون وين ما ظهرت)", style = MaterialTheme.typography.labelLarge)
            ColorPicker(currentColor) { nav.look(allSeries, it, null) }
            Text("رمز مهامه", style = MaterialTheme.typography.labelLarge)
            IconPicker(s.settings.icons[allSeries.first()], tagColor(currentColor)) { nav.look(allSeries, null, it) }
        }
        val series = allSeries.firstOrNull()
        if (series != null) Secondary("تقرير الإنجاز", { nav.report(series, g?.name ?: p!!.title) }, Modifier.fillMaxWidth())
        Secondary("إعدادات الهدف", { close(); if (g != null) nav.openGoal(g) else nav.openPlan(p!!) }, Modifier.fillMaxWidth())
    }
}

@Composable
fun MonthEditSheet(m: MonthTarget, entry: MonthEntry?, close: () -> Unit, save: (Double?, Boolean) -> Unit) {
    var value by remember { mutableStateOf(num(entry?.value ?: m.default)) }
    Sheet("${m.title} · ${monthLabel(m.ym)}", close) {
        Text("المقترح: ${num(m.default)} ${m.unit}", style = MaterialTheme.typography.bodyLarge)
        Field(value, "هدف هذا الشهر (${m.unit})", { value = it }, numeric = true)
        if (m.lower) Muted("الزمن الأقل أفضل")
        val v = digits(value).toDoubleOrNull()
        Action(if (v == null || v == m.default) "قبول المقترح" else "حفظ التعديل", {
            save(if (v == null || v == m.default) null else v, true)
        }, Modifier.fillMaxWidth())
        if (entry != null) Secondary("رجوع للمقترح بدون مراجعة", { save(null, false) }, Modifier.fillMaxWidth())
    }
}
