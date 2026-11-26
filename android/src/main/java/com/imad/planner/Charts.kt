package com.imad.planner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

// ---------------- Building blocks (in the spirit of the phone's screen-time pages) ----------------

@Composable
fun ChartCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) { Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } }
}

/** The big number at the top of a card, e.g. "3 س 40 د". */
@Composable
fun BigValue(value: String, unit: String = "", sub: String? = null) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        if (unit.isNotEmpty()) Text(" $unit", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
    }
    sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** Minutes as the big number: "3 س 40 د" or "25 د". */
fun bigMinutes(m: Int): Pair<String, String> = if (m >= 60) "${m / 60} س ${m % 60}" to "د" else "$m" to "د"

data class Seg(val color: Color, val value: Float)

/**
 * Bars for the days of a week, each split in coloured parts, with an optional dashed average line.
 * The first day is on the right (the page reads right to left). Tapping a bar selects that day.
 */
@Composable
fun WeekBars(labels: List<String>, bars: List<List<Seg>>, selected: Int?, onSelect: (Int) -> Unit, scaleLabel: (Float) -> String, average: Float? = null) {
    val total = bars.map { b -> b.sumOf { it.value.toDouble() }.toFloat() }
    val max = (total.maxOrNull() ?: 0f).let { if (it <= 0f) 1f else it * 1.15f }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val avgColor = MaterialTheme.colorScheme.error
    val n = bars.size.coerceAtLeast(1)
    Row(verticalAlignment = Alignment.Top) {
        Canvas(
            Modifier.weight(1f).height(170.dp).pointerInput(n) {
                detectTapGestures { p ->
                    val slot = size.width.toFloat() / n
                    val i = ((size.width - p.x) / slot).toInt().coerceIn(0, n - 1)
                    onSelect(i)
                }
            }
        ) {
            val slot = size.width / n
            val bw = (slot * 0.42f).coerceAtMost(30.dp.toPx())
            listOf(1f, 0.5f).forEach { f ->
                val y = size.height * (1 - f / 1.15f)
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
            bars.forEachIndexed { i, segs ->
                val sum = total[i]
                if (sum <= 0f) return@forEachIndexed
                val cx = size.width - slot * (i + 0.5f)
                val h = size.height * (sum / max)
                val left = cx - bw / 2
                val top = size.height - h
                val path = Path().apply {
                    addRoundRect(RoundRect(left = left, top = top, right = left + bw, bottom = size.height,
                        topLeftCornerRadius = CornerRadius(bw / 2), topRightCornerRadius = CornerRadius(bw / 2),
                        bottomRightCornerRadius = CornerRadius.Zero, bottomLeftCornerRadius = CornerRadius.Zero))
                }
                clipPath(path) {
                    var y = size.height
                    segs.forEach { sg ->
                        val sh = size.height * (sg.value / max)
                        drawRect(sg.color.copy(alpha = if (selected == null || selected == i) 1f else 0.55f), Offset(left, y - sh), Size(bw, sh))
                        y -= sh
                    }
                }
            }
            average?.takeIf { it > 0f }?.let { a ->
                val y = size.height * (1 - a / max)
                drawLine(avgColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            }
        }
        Column(Modifier.width(44.dp).height(170.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(scaleLabel(max / 1.15f), fontSize = 11.sp, color = labelColor)
            Text(scaleLabel(max / 2.3f), fontSize = 11.sp, color = labelColor)
            Text("0", fontSize = 11.sp, color = labelColor)
        }
    }
    Row(Modifier.fillMaxWidth().padding(end = 44.dp)) {
        labels.forEachIndexed { i, l ->
            Box(Modifier.weight(1f).clickable { onSelect(i) }, contentAlignment = Alignment.Center) {
                if (selected == i) Box(Modifier.size(30.dp).background(MaterialTheme.colorScheme.inverseSurface, CircleShape), contentAlignment = Alignment.Center) {
                    Text(l, fontSize = 11.sp, color = MaterialTheme.colorScheme.inverseOnSurface)
                } else Text(l, fontSize = 12.sp, color = labelColor, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
fun LegendRow(color: Color, name: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

/** Days × hours, darker where more work was done (like "peak usage times"). Hours run left to right. */
@Composable
fun HourHeatmap(rows: List<Pair<String, FloatArray>>, color: Color) {
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    // Time reads left to right here, like a clock line, even though the page is right to left.
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            rows.forEach { (label, cells) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.weight(1f).height(22.dp)) {
                        val w = size.width / 24
                        for (h in 0 until 24) {
                            val v = cells[h].coerceIn(0f, 60f) / 60f
                            drawRoundRect(if (v <= 0f) empty else color.copy(alpha = 0.18f + 0.82f * v), Offset(h * w + 1, 0f), Size(w - 2, size.height), CornerRadius(5f))
                        }
                    }
                    Text(label, Modifier.width(40.dp), fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(end = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("0", "6", "12", "18", "24").forEach { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** One labelled horizontal bar (goal progress). */
@Composable
fun MeterRow(title: String, done: Int, goal: Int, unit: String = "", color: Color = MaterialTheme.colorScheme.primary) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text("$done/$goal $unit".trim(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(12.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp))) {
            Box(Modifier.fillMaxWidth(if (goal <= 0) 0f else (done.toFloat() / goal).coerceIn(0f, 1f)).height(12.dp)
                .background(if (goal > 0 && done >= goal) Status.done else color, RoundedCornerShape(6.dp)))
        }
    }
}

// ---------------- Data for the charts ----------------

private val fallback = listOf(Color(0xFF1FC8B3), Color(0xFF3D7BF7), Color(0xFF6B4FD8), Color(0xFFE8916B), Color(0xFF9AA8C8))

fun groupColor(s: Snapshot, key: String, i: Int): Color =
    s.settings.group(key)?.color?.let { tagColor(it) } ?: fallback[i % fallback.size]

/** Work minutes of a task (timer). */
fun workMinutes(t: Task, now: Long) = (Maths.spent(t, now) / 60_000).toInt()

fun dayTasks(s: Snapshot, d: LocalDate) = s.tasks.filter { it.day == d.toString() && !(it.state == "skipped" && it.note in setOf("مكرر", "فوق حد التمارين", "اندمجت مع جلسة بكرا")) }

/** Minutes worked in each hour of a day, from finished and running timers. */
fun hourCells(s: Snapshot, d: LocalDate): FloatArray {
    val cells = FloatArray(24)
    val dayStart = Clock.epoch(d, 0)
    for (t in s.tasks) {
        val spent = Maths.spent(t, s.now)
        if (spent <= 0) continue
        val end = when { t.running -> s.now; t.finishedAt > 0 -> t.finishedAt; else -> continue }
        var a = maxOf(end - spent, dayStart); val b = minOf(end, dayStart + 24 * 3_600_000L)
        while (a < b) {
            val h = ((a - dayStart) / 3_600_000L).toInt().coerceIn(0, 23)
            val hourEnd = dayStart + (h + 1) * 3_600_000L
            val piece = minOf(b, hourEnd) - a
            cells[h] += piece / 60_000f
            a += piece
        }
    }
    return cells
}

// ---------------- Reports as charts ----------------

fun LazyListScope.chartReports(s: Snapshot, nav: Nav) {
    item { WorkTimeCard(s) }
    item { DoneCard(s) }
    item { HeatCard(s) }
    if (s.settings.prayerMode) item { PeriodsCard(s) }
    item { WeekGoalsCard(s, nav) }
    item { PomodoroCard(s) }
}

@Composable
private fun WeekPicker(start: LocalDate, change: (LocalDate) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { change(start.minusWeeks(1)) }) { Text("→") }
        Text("${start.dayOfMonth}/${start.monthValue} – ${start.plusDays(6).dayOfMonth}/${start.plusDays(6).monthValue}", Modifier.weight(1f),
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { change(start.plusWeeks(1)) }) { Text("←") }
    }
}

@Composable
fun WorkTimeCard(s: Snapshot) {
    var start by remember { mutableStateOf(Targets.weekStart(s.today)) }
    val days = Targets.weekDays(start)
    var sel by remember { mutableStateOf(days.indexOf(s.today).takeIf { it >= 0 }) }
    val groups = s.settings.groups
    val byDay = days.map { d ->
        dayTasks(s, d).groupBy { it.category }.mapValues { (_, l) -> l.sumOf { workMinutes(it, s.now) } }
    }
    val bars = byDay.map { m -> m.entries.sortedByDescending { it.value }.map { (k, v) -> Seg(groupColor(s, k, groups.indexOfFirst { it.key == k }.coerceAtLeast(0)), v.toFloat()) } }
    val shown = sel?.let { byDay[it] } ?: byDay.fold(mutableMapOf<String, Int>()) { acc, m -> m.forEach { (k, v) -> acc[k] = (acc[k] ?: 0) + v }; acc }
    val total = shown.values.sum()
    val activeDays = byDay.count { it.values.sum() > 0 }
    ChartCard {
        WeekPicker(start) { start = it; sel = null }
        val (v, u) = bigMinutes(total)
        BigValue(v, u, if (sel != null) "وقت الشغل ${shortDay(days[sel!!])}" else "وقت الشغل هالأسبوع · ${if (activeDays > 0) durLabel(byDay.sumOf { it.values.sum() } / activeDays) else "0 د"} باليوم")
        WeekBars(days.map { shortDay(it) }, bars, sel, { sel = if (sel == it) null else it }, { durLabel(it.toInt()) },
            average = if (activeDays > 0) byDay.sumOf { it.values.sum() }.toFloat() / activeDays else null)
        shown.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(3).forEach { (k, m) ->
            LegendRow(groupColor(s, k, groups.indexOfFirst { it.key == k }.coerceAtLeast(0)), Cat.name(k), durLabel(m))
        }
    }
}

@Composable
fun DoneCard(s: Snapshot) {
    var start by remember { mutableStateOf(Targets.weekStart(s.today)) }
    val days = Targets.weekDays(start)
    var sel by remember { mutableStateOf(days.indexOf(s.today).takeIf { it >= 0 }) }
    val lists = days.map { dayTasks(s, it) }
    val missedColor = Status.missed.copy(alpha = 0.45f)
    val openColor = MaterialTheme.colorScheme.outlineVariant
    val bars = lists.mapIndexed { i, l ->
        val past = days[i].isBefore(s.today)
        listOf(Seg(Status.done, l.count { it.done }.toFloat()), Seg(if (past) missedColor else openColor, l.count { !it.done }.toFloat()))
    }
    val pick = sel?.let { lists[it] } ?: lists.flatten()
    val done = pick.count { it.done }
    ChartCard {
        WeekPicker(start) { start = it; sel = null }
        BigValue("$done", "منجزة", "من ${pick.size} مهمة" + (if (pick.isNotEmpty()) " · ${done * 100 / pick.size}٪ التزام" else "") + (sel?.let { " · ${shortDay(days[it])}" } ?: " هالأسبوع"))
        WeekBars(days.map { shortDay(it) }, bars, sel, { sel = if (sel == it) null else it }, { it.toInt().toString() })
        LegendRow(Status.done, "منجزة", "$done")
        LegendRow(missedColor, "ما انعملت أو لسا", "${pick.size - done}")
    }
}

@Composable
fun HeatCard(s: Snapshot) {
    val days = (6 downTo 0).map { s.today.minusDays(it.toLong()) }
    val rows = days.map { d -> shortDay(d) to hourCells(s, d) }
    val total = rows.sumOf { r -> r.second.sum().toDouble() }.toInt()
    val peak = (0 until 24).maxByOrNull { h -> rows.sumOf { it.second[h].toDouble() } }
    ChartCard {
        Text("أوقات الشغل", style = MaterialTheme.typography.titleMedium)
        val (v, u) = bigMinutes(total)
        BigValue(v, u, if (total > 0 && peak != null) "آخر ٧ أيام · أكثر ساعة بتشتغل فيها ${fmt12(peak * 60)}" else "آخر ٧ أيام")
        HourHeatmap(rows, MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun PeriodsCard(s: Snapshot) {
    val start = s.today.minusDays(6)
    val week = s.tasks.filter { it.day >= start.toString() && it.day <= s.today.toString() && it.period.isNotEmpty() }
    val keys = Prayer.keys
    val bars = keys.map { k -> val l = week.filter { it.period == k }; listOf(Seg(Status.done, l.count { it.done }.toFloat()), Seg(Status.missed.copy(alpha = 0.4f), l.count { !it.done }.toFloat())) }
    var sel by remember { mutableStateOf<Int?>(null) }
    ChartCard {
        Text("الفترات · آخر ٧ أيام", style = MaterialTheme.typography.titleMedium)
        val best = keys.maxByOrNull { k -> week.filter { it.period == k }.let { l -> if (l.isEmpty()) -1.0 else l.count { it.done }.toDouble() / l.size } }
        if (week.isEmpty()) Muted("بيظهر بعد أول يوم بنظام الصلوات.")
        else BigValue(Prayer.names[best] ?: "", "", "أحسن فترة إلك")
        WeekBars(keys.map { Prayer.names.getValue(it) }, bars, sel, { sel = if (sel == it) null else it }, { it.toInt().toString() })
        sel?.let { i -> val l = week.filter { it.period == keys[i] }; LegendRow(Status.done, "منجز بفترة ${Prayer.names[keys[i]]}", "${l.count { it.done }} من ${l.size}") }
    }
}

@Composable
fun WeekGoalsCard(s: Snapshot, nav: Nav) {
    val start = Targets.weekStart(s.today)
    val series = Targets.seriesOfWeek(s.targets, start)
    if (series.isEmpty()) return
    val items = planItemsAll(s).associateBy { it.series }
    ChartCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("أهداف الأسبوع", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = nav.weekPlan) { Text("خطة الأسبوع") }
        }
        series.forEach { sr ->
            val w = s.targets[Targets.weekKey(start)]?.get(sr)
            val title = items[sr]?.title ?: sr
            val planned = Targets.sessionsSpread(s.targets, start, sr).coerceAtLeast(w?.sessions ?: 0)
            val doneSessions = Targets.weekDays(start).sumOf { d -> s.tasks.count { it.day == d.toString() && it.note == "wk:$sr" && it.done } }
            if (w != null && w.goal > 0) MeterRow(title + (w.text.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), Targets.weekDone(s.targets, start, sr), w.goal)
            if (planned > 0) MeterRow("$title · الجلسات", doneSessions, planned, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
fun PomodoroCard(s: Snapshot) {
    val start = Targets.weekStart(s.today)
    val days = Targets.weekDays(start)
    val per = days.map { pomoSessions(s, it) }
    if (per.all { it.isEmpty() }) return
    var sel by remember { mutableStateOf<Int?>(null) }
    val pick = sel?.let { per[it] } ?: per.flatten()
    ChartCard {
        Text("🍎 بومودورو", style = MaterialTheme.typography.titleMedium)
        BigValue("${pick.size}", "جلسة", durLabel(pick.sumOf { it.minutes }) + (sel?.let { " · ${shortDay(days[it])}" } ?: " هالأسبوع"))
        WeekBars(days.map { shortDay(it) }, per.map { listOf(Seg(Color(0xFFD0574E), it.size.toFloat())) }, sel, { sel = if (sel == it) null else it }, { it.toInt().toString() })
        pick.filter { it.label.isNotBlank() }.groupBy { it.label }.entries.sortedByDescending { it.value.size }.take(3)
            .forEach { (l, v) -> LegendRow(Color(0xFFD0574E), l, "${v.size}") }
    }
}

// ---------------- Charts on the plan pages ----------------

/** Planned time per day of the week, coloured by group; tap a day to list it. */
@Composable
fun PlanWeekChart(s: Snapshot, days: List<LocalDate>, proj: Map<LocalDate, List<Projector.Entry>>) {
    var sel by remember { mutableStateOf<Int?>(null) }
    val groups = s.settings.groups
    val per = days.map { d -> proj[d].orEmpty().map { it.task }.filter { it.state != "skipped" } }
    val bars = per.map { l -> l.groupBy { it.category }.entries.sortedByDescending { e -> e.value.sumOf { it.minutes } }
        .map { (k, v) -> Seg(groupColor(s, k, groups.indexOfFirst { it.key == k }.coerceAtLeast(0)), v.sumOf { it.minutes }.toFloat()) } }
    val pick = sel?.let { per[it] } ?: per.flatten()
    ChartCard {
        val (v, u) = bigMinutes(pick.sumOf { it.minutes })
        BigValue(v, u, "مخطط " + (sel?.let { shortDay(days[it]) } ?: "هالأسبوع") + " · ${pick.size} مهمة")
        WeekBars(days.map { shortDay(it) }, bars, sel, { sel = if (sel == it) null else it }, { durLabel(it.toInt()) })
        pick.groupBy { it.category }.entries.sortedByDescending { e -> e.value.sumOf { it.minutes } }.take(3).forEach { (k, l) ->
            LegendRow(groupColor(s, k, groups.indexOfFirst { it.key == k }.coerceAtLeast(0)), Cat.name(k), durLabel(l.sumOf { it.minutes }))
        }
    }
}

/** The weeks of a month: sessions done against planned, and each goal's month progress. */
@Composable
fun PlanMonthChart(s: Snapshot, ym: java.time.YearMonth) {
    val weeks = Targets.weeksOf(ym)
    var sel by remember { mutableStateOf<Int?>(null) }
    val stats = weeks.map { w ->
        val series = Targets.seriesOfWeek(s.targets, w)
        val planned = series.sumOf { maxOf(Targets.sessionsSpread(s.targets, w, it), s.targets[Targets.weekKey(w)]?.get(it)?.sessions ?: 0) }
        val done = Targets.weekDays(w).sumOf { d -> s.tasks.count { it.day == d.toString() && it.note.startsWith("wk:") && it.done } }
        done to planned
    }
    ChartCard {
        val pick = sel?.let { listOf(stats[it]) } ?: stats
        BigValue("${pick.sumOf { it.first }}", "جلسة", "من ${pick.sumOf { it.second }} مخططة · " + (sel?.let { "الأسبوع ${it + 1}" } ?: monthLabel(ym)))
        WeekBars(weeks.indices.map { "أ${it + 1}" }, stats.map { (d, p) -> listOf(Seg(Status.done, d.toFloat()), Seg(MaterialTheme.colorScheme.outlineVariant, (p - d).coerceAtLeast(0).toFloat())) },
            sel, { sel = if (sel == it) null else it }, { it.toInt().toString() })
        s.plans.filter { it.accepted }.forEach { p ->
            PlanMath.pace(p, s.today)?.let { (exp, act) -> MeterRow(p.title + " (المفروض ${num(exp)})", act.toInt(), exp.toInt().coerceAtLeast(1), p.unit) }
        }
    }
}

/** The year: finished tasks per month, and where each plan stands. */
@Composable
fun PlanYearChart(s: Snapshot) {
    val year = s.today.year
    val months = (1..12).map { java.time.YearMonth.of(year, it) }
    var sel by remember { mutableStateOf<Int?>(null) }
    val per = months.map { ym -> s.tasks.filter { it.done && it.day.startsWith(ym.toString()) } }
    ChartCard {
        val pick = sel?.let { per[it] } ?: per.flatten()
        BigValue("${pick.size}", "مهمة منجزة", sel?.let { monthLabel(months[it]) } ?: "سنة $year")
        WeekBars(months.map { "${it.monthValue}" }, per.map { l -> listOf(Seg(Status.done, l.size.toFloat())) }, sel, { sel = if (sel == it) null else it }, { it.toInt().toString() })
        s.plans.filter { it.accepted }.forEach { p ->
            PlanMath.pace(p, s.today)?.let { (exp, act) -> MeterRow(p.title, act.toInt(), exp.toInt().coerceAtLeast(1), p.unit) }
        }
    }
}
