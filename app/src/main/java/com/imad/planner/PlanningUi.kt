@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.imad.planner

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// ---------------- Small dialogs on a task ----------------

/** Long press on a task: move it to another period (or tomorrow) in one tap. */
@Composable
fun QuickMoveDialog(t: Task, periods: List<Period>, close: () -> Unit, move: (String) -> Unit, tomorrow: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("«${t.baseTitle}» لوين؟") },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                periods.forEach { p ->
                    FilterChip(selected = t.period == p.key, onClick = { move(p.key); close() }, label = { Text(p.name, fontSize = 17.sp) })
                }
                FilterChip(selected = false, onClick = { close(); tomorrow() }, label = { Text("بكرا", fontSize = 17.sp) })
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
    )
}

/** Double tap on a task: its details text. */
@Composable
fun DetailsDialog(t: Task, close: () -> Unit, save: (String) -> Unit) {
    var text by remember { mutableStateOf(t.details) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(t.title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("تفاصيل المهمة") }, modifier = Modifier.fillMaxWidth(), minLines = 3) },
        confirmButton = { TextButton(onClick = { save(text); close() }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
    )
}

/** Finishing a task that has a number for today: how much of it was done. */
@Composable
fun FinishAmountDialog(t: Task, target: Target, close: () -> Unit, done: (Int) -> Unit) {
    var n by remember { mutableStateOf((target.goal - target.done).coerceAtLeast(0).toString()) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("خلصت «${t.baseTitle}»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (target.text.isNotBlank()) Text("هدف اليوم: ${target.text}", style = MaterialTheme.typography.bodyLarge)
                Muted("انعمل لحد هلأ ${target.done} من ${target.goal}")
                Field(n, "قديش خلصت هالمرة؟", { n = it }, numeric = true)
            }
        },
        confirmButton = { TextButton(onClick = { done(digits(n).toIntOrNull() ?: 0); close() }) { Text("إنهاء") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
    )
}

// ---------------- Pomodoro ----------------

data class PomoState(val end: Long, val left: Long) {
    fun remaining(now: Long) = if (end > 0) (end - now).coerceAtLeast(0) else left
    val running get() = end > 0
}

fun pomoState(s: Snapshot): PomoState {
    val o = runCatching { JSONObject(s.meta["pomo"] ?: "{}") }.getOrDefault(JSONObject())
    return PomoState(o.optLong("end"), o.optLong("left", Repository.POMO_MS))
}

data class PomoSession(val at: Long, val minutes: Int, val label: String)

fun pomoSessions(s: Snapshot, day: LocalDate): List<PomoSession> = runCatching {
    val a = JSONArray(s.meta["pomo:$day"] ?: "[]")
    (0 until a.length()).map { i -> a.getJSONObject(i).let { PomoSession(it.optLong("at"), it.optInt("min"), it.optString("label")) } }
}.getOrDefault(emptyList())

/** The small apple in the corner of the top card. */
@Composable
fun AppleButton(s: Snapshot, open: () -> Unit) {
    val st = pomoState(s)
    Row(Modifier.clickable(onClick = open).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("🍎", fontSize = 22.sp)
        if (st.running || st.left < Repository.POMO_MS) Text(" " + timerLabel(st.remaining(s.now)).removePrefix("00:"), fontSize = 14.sp,
            color = if (st.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PomodoroSheet(s: Snapshot, close: () -> Unit, act: (String) -> Unit) {
    val st = pomoState(s)
    val left = st.remaining(s.now)
    val today = pomoSessions(s, s.today)
    Sheet("🍎 بومودورو", close) {
        Muted("مؤقت ٢٥ دقيقة لحاله، ما إله دخل بمهام اليوم.")
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AnalogTimer(Repository.POMO_MS - left, Repository.POMO_MS, 200.dp) }
        Text(timerLabel(left), fontSize = 44.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (st.running) Action("إيقاف مؤقت", { act("pause") }, Modifier.weight(1f))
            else Action(if (left < Repository.POMO_MS) "متابعة" else "ابدأ", { act("start") }, Modifier.weight(1f))
            Secondary("من جديد", { act("reset") }, Modifier.weight(1f))
        }
        if (today.isNotEmpty()) Muted("اليوم: ${today.size} جلسة · ${durLabel(today.sumOf { it.minutes })}. بتقدر تسمّيهن من تقرير اليوم.")
    }
}

/** Today's pomodoro sessions in the day report, each can be named. */
@Composable
fun PomodoroReport(s: Snapshot, day: LocalDate, rename: (Int, String) -> Unit) {
    val list = pomoSessions(s, day)
    if (list.isEmpty()) return
    var editing by remember { mutableStateOf<Int?>(null) }
    Panel {
        Text("🍎 بومودورو اليوم: ${list.size} · ${durLabel(list.sumOf { it.minutes })}", style = MaterialTheme.typography.titleMedium)
        list.forEachIndexed { i, p ->
            Row(Modifier.fillMaxWidth().clickable { editing = i }, verticalAlignment = Alignment.CenterVertically) {
                Text(fmt12(Clock.minuteOfDay(p.at)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
                Text(p.label.ifBlank { "بدون اسم · اضغط لتسمّيها" }, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                    color = if (p.label.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                Text(durLabel(p.minutes), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    editing?.let { i -> TextDialog("اسم الجلسة", "شو عملت فيها؟", close = { editing = null }) { rename(i, it); editing = null } }
}

// ---------------- Month and week planning ----------------

data class PlanItem(val series: String, val title: String, val category: String)

/** What can get weekly and daily targets: accepted plans and recurring sessions that are not daily routine. */
fun planItems(s: Snapshot): List<PlanItem> {
    val plans = s.plans.filter { it.accepted }.map { PlanItem("plan-${it.id}", it.title, Cat.ofPlan(it)) }
    val rules = s.rules.filter { r ->
        r.active && !r.id.startsWith("plan-") && s.settings.routine[r.id] != true &&
            !(if (r.weekdays != 0) Integer.bitCount(r.weekdays) >= 5 else r.everyHours <= 24)
    }.map { PlanItem(it.id, it.title, it.category) }
    return (plans + rules).distinctBy { it.series }
}

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale("ar"))

@Composable
fun ProgressLine(done: Int, goal: Int) {
    if (goal <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(progress = { (done.toFloat() / goal).coerceIn(0f, 1f) }, modifier = Modifier.weight(1f).height(8.dp),
            color = if (done >= goal) Status.done else MaterialTheme.colorScheme.primary)
        Text("$done/$goal", style = MaterialTheme.typography.bodyMedium)
    }
}

/** Text and number of one target; it keeps its own typing state and saves as you type. */
@Composable
fun TargetFields(id: String, t: Target?, hint: String, save: (Target) -> Unit) {
    var text by remember(id) { mutableStateOf(t?.text ?: "") }
    var goal by remember(id) { mutableStateOf(t?.goal?.takeIf { it > 0 }?.toString() ?: "") }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = text, onValueChange = { text = it; save(Target(it, digits(goal).toIntOrNull() ?: 0, t?.done ?: 0)) },
            placeholder = { Text(hint) }, modifier = Modifier.weight(1f), singleLine = true)
        OutlinedTextField(value = goal, onValueChange = { goal = it; save(Target(text, digits(it).toIntOrNull() ?: 0, t?.done ?: 0)) },
            placeholder = { Text("عدد") }, modifier = Modifier.width(72.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
}

@Composable
fun MonthPlanSheet(s: Snapshot, close: () -> Unit, save: (String, String, Target) -> Unit) {
    var ym by remember { mutableStateOf(YearMonth.from(s.today)) }
    val weeks = Targets.weeksOf(ym)
    val items = planItems(s)
    Sheet("تخطيط ${monthLabel(ym)}", close) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { ym = ym.minusMonths(1) }) { Text("→ السابق") }
            Box(Modifier.weight(1f))
            TextButton(onClick = { ym = ym.plusMonths(1) }) { Text("التالي ←") }
        }
        Muted("لكل مهمة: هدف كل أسبوع (نص + عدد). الأسبوع من الجمعة للخميس.")
        if (items.isEmpty()) Muted("ما في أهداف أو مهام متكررة لتخطيطها. أضف هدف من الخطة.")
        items.forEach { it0 ->
            Panel {
                Text(it0.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                weeks.forEachIndexed { i, w ->
                    val key = Targets.weekKey(w)
                    val t = s.targets[key]?.get(it0.series)
                    Text("الأسبوع ${i + 1} · ${w.format(shortDate)} – ${w.plusDays(6).format(shortDate)}", style = MaterialTheme.typography.labelLarge,
                        color = if (s.today in w..w.plusDays(6)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    TargetFields("$key/${it0.series}", t, "مثلاً: حلقة ٣ و٤ + ٩ ريلز") { save(key, it0.series, it) }
                    if (t != null) ProgressLine(Targets.weekDone(s.targets, w, it0.series), t.goal)
                }
            }
        }
    }
}

@Composable
fun WeekPlanSheet(s: Snapshot, close: () -> Unit, save: (String, String, Target) -> Unit) {
    var start by remember { mutableStateOf(Targets.weekStart(s.today).let { if (s.today.dayOfWeek == java.time.DayOfWeek.THURSDAY) it.plusWeeks(1) else it }) }
    val days = Targets.weekDays(start)
    val items = planItems(s)
    var open by remember { mutableStateOf<String?>(null) }
    Sheet("خطة الأسبوع", close) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { start = start.minusWeeks(1) }) { Text("→") }
            Text("${start.format(shortDate)} – ${start.plusDays(6).format(shortDate)}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            TextButton(onClick = { start = start.plusWeeks(1) }) { Text("←") }
        }
        Muted("اضغط على مهمة ووزّع هدف أسبوعها على الأيام.")
        items.forEach { it0 ->
            val wk = s.targets[Targets.weekKey(start)]?.get(it0.series)
            val done = Targets.weekDone(s.targets, start, it0.series)
            val spread = Targets.weekSpread(s.targets, start, it0.series)
            Panel(Modifier.clickable { open = if (open == it0.series) null else it0.series }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it0.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (open == it0.series) "▲" else "▼")
                }
                Text(wk?.text?.takeIf { it.isNotBlank() }?.let { "هدف الأسبوع: $it" } ?: "ما في هدف أسبوع (من تخطيط الشهر أو هون)", style = MaterialTheme.typography.bodyMedium)
                if (wk != null && wk.goal > 0) {
                    ProgressLine(done, wk.goal)
                    if (spread < wk.goal) Muted("موزّع على الأيام $spread من ${wk.goal}")
                }
                if (open == it0.series) {
                    TargetFields("${Targets.weekKey(start)}/${it0.series}", wk, "هدف الأسبوع") { save(Targets.weekKey(start), it0.series, it) }
                    HorizontalDivider()
                    days.forEach { d ->
                        val key = Targets.dayKey(d)
                        val t = s.targets[key]?.get(it0.series)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column(Modifier.width(54.dp)) {
                                Text(shortDay(d), style = MaterialTheme.typography.labelLarge, color = if (d == s.today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.bodySmall)
                            }
                            Box(Modifier.weight(1f)) { TargetFields("$key/${it0.series}", t, "هدف اليوم") { save(key, it0.series, it) } }
                        }
                        if (t != null && t.goal > 0 && t.done > 0) ProgressLine(t.done, t.goal)
                    }
                }
            }
        }
    }
}

// ---------------- Day planning by dragging ----------------

/**
 * One day: the periods on one side, the tasks still to place on the other. Hold a task and drag it into a period
 * (or tap it, then tap the period). The filled part of each period shows how much of its time is taken.
 */
@Composable
fun DayPlanSheet(s: Snapshot, day: LocalDate, close: () -> Unit, assign: (String, String) -> Unit, confirm: () -> Unit, details: (Task) -> Unit) {
    val ds = day.toString()
    val tasks = s.tasks.filter { it.day == ds && it.state != "skipped" }
    val rules = s.ruleMap
    val periods = s.settings.periodsOn(day)
    val blocks = s.settings.configFor(day).blocks
    val pool = tasks.filter { it.open && it.period.isEmpty() && !isRoutine(it, rules, s.settings) }
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var dragging by remember { mutableStateOf<Task?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var picked by remember { mutableStateOf<Task?>(null) }
    val drop: (Task, Offset) -> Unit = { t, at ->
        bounds.entries.firstOrNull { it.value.contains(at) }?.key?.let { k -> if (k != t.period) assign(t.id, k) }
    }

    @Composable
    fun chip(t: Task, inPeriod: Boolean) {
        var chipOrigin by remember { mutableStateOf(Offset.Zero) }
        val tag = tagColor(colorIndexOf(t, s.settings))
        val routine = isRoutine(t, rules, s.settings)
        val look = s.settings.icons[seriesOf(t)]
        val target = s.targets[Targets.dayKey(day)]?.get(seriesOf(t))
        var handleOrigin by remember { mutableStateOf(Offset.Zero) }
        Row(
            Modifier.fillMaxWidth()
                .background(if (picked?.id == t.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .border(if (t.done) 2.dp else 1.dp, if (t.done) Status.done else tag ?: MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .onGloballyPositioned { chipOrigin = it.positionInRoot() }
                .then(if (routine) Modifier else Modifier.pointerInput(t.id, t.period) {
                    // Hold anywhere on the task, then drag (a quick swipe scrolls the list instead).
                    detectDragGesturesAfterLongPress(
                        onDragStart = { off -> dragging = t; dragPos = chipOrigin + off; picked = null },
                        onDrag = { change, amount -> change.consume(); dragPos += amount },
                        onDragEnd = { dragging?.let { drop(it, dragPos) }; dragging = null },
                        onDragCancel = { dragging = null },
                    )
                })
                .combinedClickable(enabled = !routine, onClick = { picked = if (picked?.id == t.id) null else t }, onDoubleClick = { details(t) })
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The handle drags right away, without holding first.
            if (!routine) Box(
                Modifier.width(26.dp).height(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                    .onGloballyPositioned { handleOrigin = it.positionInRoot() }
                    .pointerInput(t.id, t.period) {
                        detectDragGestures(
                            onDragStart = { off -> dragging = t; dragPos = handleOrigin + off; picked = null },
                            onDrag = { change, amount -> change.consume(); dragPos += amount },
                            onDragEnd = { dragging?.let { drop(it, dragPos) }; dragging = null },
                            onDragCancel = { dragging = null },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) { Text("⠿", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (look != null) TaskBadge(look, tag, 22.dp)
            Column(Modifier.weight(1f)) {
                Text(if (look != null && s.settings.nameStyle == "icon") t.title.take(12) else t.title, fontSize = 14.sp, maxLines = 2,
                    color = if (routine) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                target?.text?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1) }
            }
            Text(durLabel(t.minutes), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onGloballyPositioned { origin = it.positionInRoot() }) {
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("ترتيب ${if (day == s.today) "اليوم" else if (day == s.today.plusDays(1)) "بكرا" else dayLabel(day)}", style = MaterialTheme.typography.titleLarge)
                            Muted("امسك المهمة واسحبها لفترة، أو اضغطها وبعدين اضغط الفترة.")
                        }
                        IconButton(onClick = close) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") }
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Periods
                        Column(Modifier.weight(0.6f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            periods.forEach { p ->
                                val inside = tasks.filter { it.period == p.key }
                                val free = periodFree(p, blocks, s.settings, day)
                                val used = inside.sumOf { it.minutes }
                                val frac = if (free <= 0) 1f else (used.toFloat() / free).coerceIn(0f, 1f)
                                val over = used > free
                                Box(
                                    Modifier.fillMaxWidth().heightIn(min = (60 + free / 4).coerceAtMost(220).dp)
                                        .border(if (over) 2.dp else 1.dp, if (over) Status.missed else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp))
                                        .onGloballyPositioned { bounds[p.key] = it.boundsInRoot() }
                                        .clickable { picked?.let { assign(it.id, p.key); picked = null } },
                                ) {
                                    // The filled part: how much of the period's free time the tasks take.
                                    Box(Modifier.matchParentSize().padding(2.dp)) {
                                        Box(Modifier.fillMaxWidth().fillMaxHeight(frac).background(
                                            (if (over) Status.missed else MaterialTheme.colorScheme.primary).copy(alpha = 0.18f), RoundedCornerShape(12.dp)))
                                    }
                                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                            Text("  ${fmt12(p.start)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Box(Modifier.weight(1f))
                                            Text("${durLabel(used)} / ${durLabel(free)}", fontSize = 12.sp, color = if (over) Status.missed else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        inside.forEach { t -> chip(t, true) }
                                    }
                                }
                            }
                        }
                        // Tasks to place
                        Column(
                            Modifier.weight(0.4f).fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
                                .onGloballyPositioned { bounds[""] = it.boundsInRoot() }
                                .clickable { picked?.let { assign(it.id, ""); picked = null } }
                                .verticalScroll(rememberScrollState()).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("المهام · ${pool.size}", fontWeight = FontWeight.Bold)
                            if (pool.isEmpty()) Muted("كل المهام انحطت ✓")
                            pool.forEach { t -> chip(t, false) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Action("تثبيت الخطة", { confirm(); close() }, Modifier.weight(1f))
                        Secondary("إغلاق", close, Modifier.weight(1f))
                    }
                    if (pool.isNotEmpty()) Muted("عند التثبيت: المهام اللي ضلت بالقائمة بتترحّل لليوم اللي بعده.")
                }
                dragging?.let { t ->
                    Box(
                        // Screen coordinates are not mirrored, so the offset must not be either (the page is right-to-left).
                        Modifier.absoluteOffset { IntOffset((dragPos.x - origin.x - 60.dp.toPx()).roundToInt(), (dragPos.y - origin.y - 24.dp.toPx()).roundToInt()) }
                            .width(170.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)).padding(10.dp)
                    ) { Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1) }
                }
            }
        }
    }
}

/** Minutes of a period not taken by prayers and other blocks (overlaps counted once). */
fun periodFree(p: Period, blocks: List<Pair<Int, Int>>, st: Settings, day: LocalDate): Int {
    val from = maxOf(p.start, st.dayStart)
    val to = minOf(p.end, st.dayEnd)
    if (to <= from) return 0
    val busy = blocks.map { (a, b) -> maxOf(a, from) to minOf(b, to) }.filter { it.second > it.first }.sortedBy { it.first }
    var taken = 0; var edge = from
    for ((a, b) in busy) { val s0 = maxOf(a, edge); if (b > s0) { taken += b - s0; edge = b } }
    return (to - from - taken).coerceAtLeast(0)
}

// ---------------- Quran ward link ----------------

@Composable
fun QuranLinkSheet(t: Task, current: QuranLink?, close: () -> Unit, save: (QuranLink?) -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<QuranState?>(null) }
    var wards by remember { mutableStateOf<List<Ward>>(emptyList()) }
    var mode by remember { mutableStateOf(current?.mode ?: "read") }
    var chosen by remember { mutableStateOf(current?.wardId) }
    var reload by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { reload++ }
    LaunchedEffect(reload) {
        val (st, list) = withContext(Dispatchers.IO) { QuranAdapter.wards(context) }
        state = st; wards = list
    }
    Sheet("ربط «${t.baseTitle}» بورد قرآن", close) {
        if (current != null) {
            Text("مربوطة بـ: ${QuranContract.modeName(current.mode)} · ${current.wardName.ifBlank { current.wardId }}", style = MaterialTheme.typography.bodyLarge)
            if (current.missing) Text("الورد هاد انحذف من تطبيق القرآن. اختار ورد ثاني.", color = Status.missed)
        }
        Text("نوع النشاط", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuranContract.modes.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(QuranContract.modeName(m)) }) }
        }
        when (state) {
            null -> Muted("بفحص تطبيق القرآن…")
            QuranState.OK -> {
                val list = wards.filter { it.mode == mode }
                if (list.isEmpty()) Muted("ما في أوراد ${QuranContract.modeName(mode)} بتطبيق القرآن. اعمل ورد هناك أول.")
                list.forEach { w ->
                    Card(
                        Modifier.fillMaxWidth().clickable { chosen = w.id },
                        border = if (chosen == w.id) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) { Text(w.name, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge) }
                }
            }
            QuranState.NO_PERMISSION -> {
                Muted(state!!.text)
                Action("السماح", { permission.launch(QuranContract.PERMISSION) }, Modifier.fillMaxWidth())
            }
            else -> Muted(state!!.text)
        }
        val w = wards.firstOrNull { it.id == chosen && it.mode == mode }
        Action("حفظ الربط", { w?.let { save(QuranLink(mode, it.id, it.name)); close() } }, Modifier.fillMaxWidth(), enabled = w != null)
        if (current != null) Secondary("إلغاء الربط", { save(null); close() }, Modifier.fillMaxWidth())
        Muted("لما تبدأ المهمة أو تضغط تذكيرها، بيفتح الورد عند آخر مكان وقفت فيه. فتح التطبيق ما بيعتبر إنجاز.")
    }
}

/** Today's Quran progress as the Quran app reported it. */
@Composable
fun QuranReportPanel(s: Snapshot, sync: () -> Unit) {
    val links = s.settings.quranLinks
    val st = s.meta["quranState"]?.let { runCatching { QuranState.valueOf(it) }.getOrNull() }
    val r = s.quranToday
    if (links.isEmpty() && r == null) return
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("القرآن اليوم", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = sync) { Text("تحديث") }
        }
        if (r == null) Muted(st?.takeIf { it != QuranState.OK }?.text ?: "لسا ما وصل تقرير من تطبيق القرآن.")
        else {
            val t = r.totals
            if (t.readPages + t.readAyahs > 0) Text("القراءة المؤكدة: " + listOfNotNull(t.readPages.takeIf { it > 0 }?.let { "$it صفحة" }, t.readAyahs.takeIf { it > 0 }?.let { "$it آية" }).joinToString(" · "))
            if (t.memorizedAyahs > 0) Text("الحفظ الجديد: ${t.memorizedAyahs} آية")
            if (t.reviewAyahs > 0 || t.listenSeconds > 0) Text("المراجعة المسموعة: ${t.reviewAyahs} آية مختلفة · ${t.reviewPlays} مرة تشغيل · ${durLabel(t.listenSeconds / 60)}")
            if (t == QuranTotals()) Muted("ما في نشاط مسجّل اليوم.")
            r.wards.forEach { w ->
                val (n, unit) = QuranAdapter.mainNumber(w.mode, w.totals)
                Muted("${w.name} (${QuranContract.modeName(w.mode)}): $n $unit")
            }
            s.meta["quranSyncAt"]?.toLongOrNull()?.let { Muted("آخر مزامنة: ${fmt12(Clock.minuteOfDay(it))}") }
            if (st != null && st != QuranState.OK) Muted(st.text + " (هاد آخر تقرير محفوظ)")
        }
    }
}
