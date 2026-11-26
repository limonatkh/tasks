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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
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
fun QuickMoveDialog(t: Task, periods: List<Period>, close: () -> Unit, move: (String) -> Unit, tomorrow: () -> Unit, more: () -> Unit = {}) {
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
        confirmButton = { TextButton(onClick = { close(); more() }) { Text("كل الخيارات") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
    )
}

/** Move a task to another day: the next two weeks, one tap. */
@Composable
fun MoveDayDialog(t: Task, today: LocalDate, close: () -> Unit, pick: (LocalDate) -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = close,
        title = { Text("«${t.baseTitle}» لأي يوم؟") },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (0 until 14).map { today.plusDays(it.toLong()) }.filter { it.toString() != t.day }.forEach { d ->
                    FilterChip(selected = false, onClick = { pick(d); close() },
                        label = { Text(when (d) { today -> "اليوم"; today.plusDays(1) -> "بكرا"; else -> "${shortDay(d)} ${d.dayOfMonth}" }) })
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
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

data class PomoState(val end: Long, val left: Long, val task: String = "") {
    fun remaining(now: Long) = if (end > 0) (end - now).coerceAtLeast(0) else left
    val running get() = end > 0
}

fun pomoState(s: Snapshot): PomoState {
    val o = runCatching { JSONObject(s.meta["pomo"] ?: "{}") }.getOrDefault(JSONObject())
    return PomoState(o.optLong("end"), o.optLong("left", Repository.POMO_MS), o.optString("task"))
}

/** [task] set = a focus session of that task: its minutes are already in the task's time. */
data class PomoSession(val at: Long, val minutes: Int, val label: String, val task: String = "")

fun pomoSessions(s: Snapshot, day: LocalDate): List<PomoSession> = runCatching {
    val a = JSONArray(s.meta["pomo:$day"] ?: "[]")
    (0 until a.length()).map { i -> a.getJSONObject(i).let { PomoSession(it.optLong("at"), it.optInt("min"), it.optString("label"), it.optString("task")) } }
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
        val focusOn = st.task.takeIf { it.isNotBlank() }?.let { id -> s.tasks.firstOrNull { it.id == id } }
        Muted(if (focusOn != null) "جلسة تركيز لـ«${focusOn.baseTitle}»: وقتها بينحفظ بالمهمة. لما تخلص بتوقف المهمة مؤقتاً، وما بتنعلّم منجزة."
            else "مؤقت ٢٥ دقيقة لحاله، ما إله دخل بمهام اليوم. لجلسة لمهمة: من قائمة المهمة ← جلسة تركيز.")
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
                Text(p.label.ifBlank { "بدون اسم · اضغط لتسمّيها" } + if (p.task.isNotBlank()) " · (محسوبة بوقت المهمة)" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
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

/** How much of a plan falls in one month (the month plan holds totals for some kinds, so the month before is taken off). */
fun monthAmount(s: Snapshot, p: Plan, ym: YearMonth): Int? {
    fun value(m: YearMonth) = s.months[Months.key("p:${p.id}", m.toString())]?.value ?: planMonthDefault(p, m, s.today, s.memLast)
    val v = value(ym) ?: return null
    val cumulative = p.kind in setOf("memorize", "amount", "cycle")
    if (!cumulative) return v.toInt()
    val before = if (!ym.isAfter(YearMonth.from(s.today))) p.done.toDouble() else value(ym.minusMonths(1)) ?: p.done.toDouble()
    return (v - before).toInt().coerceAtLeast(0)
}

/** Everything that can be put in a week, including goals not being worked on now (not daily routine). */
fun planItemsAll(s: Snapshot): List<PlanItem> {
    val plans = s.plans.map { PlanItem("plan-${it.id}", it.title + if (!it.accepted) " (مقترح)" else "", Cat.ofPlan(it)) }
    val rules = s.rules.filter { r -> !r.id.startsWith("plan-") }
        .map { PlanItem(it.id, it.title + if (!it.active) " (متوقف)" else "", it.category) }
    // Records (a number to reach) can also get a target for a week or a month.
    val goals = s.goals.map { PlanItem("g:${it.id}", it.name + " (رقم)", it.category) }
    return (plans + rules + goals).distinctBy { it.series }
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

/** Text and number of one target; it keeps its own typing state, saves as you type and keeps the target's other fields. */
@Composable
fun TargetFields(id: String, t: Target?, hint: String, save: (Target) -> Unit) {
    var text by remember(id) { mutableStateOf(t?.text ?: "") }
    var goal by remember(id) { mutableStateOf(t?.goal?.takeIf { it > 0 }?.toString() ?: "") }
    val base = t ?: Target()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = text, onValueChange = { text = it; save(base.copy(text = it, goal = digits(goal).toIntOrNull() ?: 0, auto = false)) },
            placeholder = { Text(hint) }, modifier = Modifier.weight(1f), singleLine = true)
        OutlinedTextField(value = goal, onValueChange = { goal = it; save(base.copy(text = text, goal = digits(it).toIntOrNull() ?: 0, auto = false)) },
            placeholder = { Text("عدد") }, modifier = Modifier.width(72.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
}

@Composable
fun MonthPlanSheet(s: Snapshot, close: () -> Unit, chain: (String) -> Unit = {}, save: (String, String, Target) -> Unit) {
    var ym by remember { mutableStateOf(YearMonth.from(s.today)) }
    val weeks = Targets.weeksOf(ym)
    val allItems = planItemsAll(s)
    val written = weeks.flatMap { Targets.seriesOfWeek(s.targets, it) }.toSet()
    val items = (planItems(s) + allItems.filter { it.series in written }).distinctBy { it.series }
    var picking by remember { mutableStateOf(false) }
    var extra by remember { mutableStateOf(listOf<PlanItem>()) }
    Sheet("تخطيط ${monthLabel(ym)}", close) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { ym = ym.minusMonths(1) }) { Text("→ السابق") }
            Box(Modifier.weight(1f))
            TextButton(onClick = { ym = ym.plusMonths(1) }) { Text("التالي ←") }
        }
        Muted("لكل مهمة: هدف كل أسبوع (نص + عدد). الأسبوع من الجمعة للخميس.")
        Secondary("+ هدف ثاني لهالشهر", { picking = true }, Modifier.fillMaxWidth())
        if (items.isEmpty()) Muted("ما في أهداف أو مهام متكررة لتخطيطها. أضف هدف من الخطة.")
        (items + extra).distinctBy { it.series }.forEach { it0 ->
            Panel {
                Text(it0.title, Modifier.clickable { chain(it0.series) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val plan = s.plans.firstOrNull { "plan-${it.id}" == it0.series }
                val amount = plan?.let { monthAmount(s, it, ym) }
                if (plan != null && amount != null && amount > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("هدف الشهر من خطة السنة: $amount ${plan.unit}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = {
                        // Only weeks without a number get one; what was written by hand stays.
                        Targets.split(amount, weeks.size).forEachIndexed { i, n ->
                            val key = Targets.weekKey(weeks[i])
                            val cur = s.targets[key]?.get(it0.series)
                            if ((cur?.goal ?: 0) == 0) save(key, it0.series, (cur ?: Target()).copy(goal = n, text = cur?.text?.ifBlank { null } ?: "$n ${plan.unit}", keep = true, auto = true))
                        }
                    }) { Text("وزّع على الأسابيع") }
                }
                weeks.forEachIndexed { i, w ->
                    val key = Targets.weekKey(w)
                    val t = s.targets[key]?.get(it0.series)
                    Text("الأسبوع ${i + 1} · ${w.format(shortDate)} – ${w.plusDays(6).format(shortDate)}", style = MaterialTheme.typography.labelLarge,
                        color = if (s.today in w..w.plusDays(6)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    TargetFields("$key/${it0.series}", t, "مثلاً: حلقة ٣ و٤ + ٩ ريلز") { save(key, it0.series, it.copy(keep = true)) }
                    if (t != null) ProgressLine(Targets.weekDone(s.targets, w, it0.series), t.goal)
                }
            }
        }
    }
    if (picking) androidx.compose.material3.AlertDialog(
        onDismissRequest = { picking = false },
        title = { Text("أي هدف؟") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                val left = allItems.filter { c -> (items + extra).none { it.series == c.series } }
                if (left.isEmpty()) Text("كل أهدافك ومهامك المتكررة موجودة بهالشهر. لهدف جديد: من + فوق ← هدف جديد.", style = MaterialTheme.typography.bodyLarge)
                left.forEach { c ->
                    Text(c.title, Modifier.fillMaxWidth().clickable { extra = extra + c; picking = false }.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = { picking = false }) { Text("إلغاء") } },
    )
}

class WeekActions(
    val save: (String, String, Target) -> Unit,
    val add: (LocalDate, String) -> Unit,
    val remove: (LocalDate, String) -> Unit,
    val routine: (String, Boolean) -> Unit,
    val moveSession: (String, LocalDate, LocalDate) -> Unit,
    val prepare: (LocalDate) -> Unit,
    val chain: (String) -> Unit,
)

@Composable
fun Stepper(value: Int, enabled: Boolean = true, change: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { change((value - 1).coerceAtLeast(0)) }, enabled = enabled && value > 0) { Text("−", fontSize = 18.sp) }
        Text("$value", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        TextButton(onClick = { change(value + 1) }, enabled = enabled) { Text("+", fontSize = 18.sp) }
    }
}

/**
 * The week: the goals in it (added by hand, from the month, or copied from last week when they repeat), what each one
 * should reach, how many sessions it needs and on which days. It stays editable all week, so a late session can be moved.
 */
@Composable
fun WeekPlanSheet(s: Snapshot, close: () -> Unit, a: WeekActions) {
    var start by remember { mutableStateOf(Targets.weekStart(s.today).let { if (s.today.dayOfWeek == java.time.DayOfWeek.THURSDAY && s.now > 0 && Clock.minuteOfDay(s.now) > s.settings.dayEnd - 120) it.plusWeeks(1) else it }) }
    LaunchedEffect(start) { a.prepare(start) }
    val days = Targets.weekDays(start)
    val all = planItemsAll(s).associateBy { it.series }
    val inWeek = Targets.seriesOfWeek(s.targets, start).toList().sortedBy { all[it]?.title ?: it }
    var open by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<String?>(null) }
    val charts = s.settings.styleOf("week") == "charts"
    fun sessionsOn(d: LocalDate, sr: String) = s.tasks.filter { it.day == d.toString() && it.note == "wk:$sr" && it.state != "skipped" }
    Sheet("خطة الأسبوع", close) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { start = start.minusWeeks(1) }) { Text("→") }
            Text("${start.format(shortDate)} – ${start.plusDays(6).format(shortDate)}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            TextButton(onClick = { start = start.plusWeeks(1) }) { Text("←") }
        }
        if (charts && inWeek.isNotEmpty()) {
            // Sessions per day, coloured by goal.
            val bars = days.map { d -> inWeek.mapIndexed { i, sr -> Seg(groupColor(s, all[sr]?.category ?: "other", i), (s.targets[Targets.dayKey(d)]?.get(sr)?.sessions ?: 0).toFloat()) } }
            WeekBars(days.map { shortDay(it) }, bars, days.indexOf(s.today).takeIf { it >= 0 }, {}, { it.toInt().toString() })
            Muted("الجلسات المخططة بكل يوم")
        }
        Action("+ هدف لهذا الأسبوع", { picking = true }, Modifier.fillMaxWidth())
        if (inWeek.isEmpty()) Muted("ما في أهداف بهالأسبوع. ضيف هدف، أو اكتب أهداف الأسابيع من تخطيط الشهر.")
        inWeek.forEach { sr ->
            val item = all[sr]
            val wk = s.targets[Targets.weekKey(start)]?.get(sr)
            val planned = Targets.sessionsSpread(s.targets, start, sr)
            val doneSessions = days.sumOf { d -> sessionsOn(d, sr).count { it.done } }
            val routine = sr in s.settings.weeklyRoutine
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item?.title ?: sr, Modifier.weight(1f).clickable { a.chain(sr) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (wk?.auto == true) Text("تلقائي ", fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
                    TextButton(onClick = { open = if (open == sr) null else sr }) { Text(if (open == sr) "▲" else "تعديل ▼") }
                }
                wk?.text?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                if (wk != null && wk.goal > 0) MeterRow("الهدف", Targets.weekDone(s.targets, start, sr), wk.goal)
                val wantSessions = maxOf(planned, wk?.sessions ?: 0)
                if (wantSessions > 0) MeterRow("الجلسات", doneSessions, wantSessions, color = MaterialTheme.colorScheme.tertiary)
                if (wk != null && wk.sessions > 0 && planned != wk.sessions) Text("موزّع على الأيام $planned من ${wk.sessions} جلسات",
                    style = MaterialTheme.typography.bodyMedium, color = if (planned < wk.sessions) Status.missed else MaterialTheme.colorScheme.onSurfaceVariant)
                // Late: sessions of past days not done.
                val late = days.filter { it.isBefore(s.today) }.flatMap { d -> sessionsOn(d, sr).filter { it.open }.map { d } }
                if (late.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${late.size} جلسات متأخرة", Modifier.weight(1f), color = Status.missed)
                    TextButton(onClick = { late.forEach { d -> a.moveSession(sr, d, s.today) } }) { Text("انقلها لليوم") }
                }
                if (open == sr) {
                    HorizontalDivider()
                    SwitchRow("يتكرر كل أسبوع (بينسخ حاله لحاله)", routine) { a.routine(sr, it) }
                    Text("هدف الأسبوع", style = MaterialTheme.typography.labelLarge)
                    TargetFields("${Targets.weekKey(start)}/$sr", wk, "مثلاً: حلقة ٣ و٤ + ٩ ريلز") { a.save(Targets.weekKey(start), sr, it.copy(keep = true)) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("جلسات", Modifier.weight(1f))
                        Stepper(wk?.sessions ?: 0) { n -> a.save(Targets.weekKey(start), sr, (wk ?: Target()).copy(sessions = n, keep = true, auto = false)) }
                        var mins by remember("${start}m$sr") { mutableStateOf((wk?.minutes?.takeIf { it > 0 } ?: 60).toString()) }
                        OutlinedTextField(mins, { v -> mins = v; digits(v).toIntOrNull()?.let { m -> a.save(Targets.weekKey(start), sr, (wk ?: Target()).copy(minutes = m, keep = true, auto = false)) } },
                            label = { Text("د/جلسة") }, modifier = Modifier.width(92.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    Text("الأيام", style = MaterialTheme.typography.labelLarge)
                    days.forEach { d ->
                        val key = Targets.dayKey(d)
                        val t = s.targets[key]?.get(sr)
                        val here = sessionsOn(d, sr)
                        val past = d.isBefore(s.today)
                        Column(Modifier.fillMaxWidth().background(if (d == s.today) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(10.dp)).padding(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${shortDay(d)} ${d.dayOfMonth}", Modifier.width(62.dp), style = MaterialTheme.typography.labelLarge,
                                    color = if (d == s.today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text(buildString {
                                    val done = here.count { it.done }; val miss = if (past) here.count { it.open } else 0
                                    if (done > 0) append("✓$done ")
                                    if (miss > 0) append("✗$miss")
                                }, Modifier.weight(1f), fontSize = 13.sp, color = if (past && here.any { it.open }) Status.missed else Status.done)
                                Text("جلسات", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Stepper(t?.sessions ?: 0, enabled = !past) { n -> a.save(key, sr, (t ?: Target()).copy(sessions = n, auto = false)) }
                            }
                            TargetFields("$key/$sr", t, "هدف اليوم") { a.save(key, sr, it) }
                            if (t != null && t.goal > 0 && t.done > 0) ProgressLine(t.done, t.goal)
                        }
                    }
                    Secondary("شيل من هالأسبوع", { removing = sr }, Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (picking) {
        val candidates = all.values.filter { it.series !in inWeek }.sortedBy { it.title }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("أي هدف بدك تحط بهالأسبوع؟") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (candidates.isEmpty()) Muted("كل الأهداف موجودة بالأسبوع.")
                    candidates.forEach { c ->
                        Text(c.title, Modifier.fillMaxWidth().clickable { a.add(start, c.series); open = c.series; picking = false }.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge)
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { picking = false }) { Text("إلغاء") } },
        )
    }
    removing?.let { sr ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("شيل «${all[sr]?.title ?: sr}» من هالأسبوع؟") },
            text = { Text("بتنشال أهدافه وجلساته اللي ما بلّشت هالأسبوع. الهدف نفسه بيضل." + if (sr in s.settings.weeklyRoutine) " بيرجع الأسبوع الجاي لأنه متكرر." else "") },
            confirmButton = { TextButton(onClick = { a.remove(start, sr); removing = null }) { Text("شيل") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("إلغاء") } },
        )
    }
}

/** One goal from year to day: the year plan, this month, its weeks and this week's days. */
@Composable
fun GoalChainSheet(s: Snapshot, series: String, close: () -> Unit, openWeek: () -> Unit, openMonth: () -> Unit) {
    val title = planItemsAll(s).firstOrNull { it.series == series }?.title ?: series
    val plan = s.plans.firstOrNull { "plan-${it.id}" == series }
    val ym = YearMonth.from(s.today)
    Sheet(title, close) {
        if (plan != null) {
            Text("السنة", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            PlanMath.pace(plan, s.today)?.let { (exp, act) -> MeterRow("المفروض ${num(exp)} · إنت", act.toInt(), exp.toInt().coerceAtLeast(1), plan.unit) }
            val month = s.months[Months.key("p:${plan.id}", ym.toString())]?.value ?: planMonthDefault(plan, ym, s.today, s.memLast)
            month?.let { Text("هدف ${monthLabel(ym)}: ${num(it)} ${plan.unit}", style = MaterialTheme.typography.bodyLarge) }
        }
        Text("أسابيع ${monthLabel(ym)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Targets.weeksOf(ym).forEachIndexed { i, w ->
            val t = s.targets[Targets.weekKey(w)]?.get(series)
            Column {
                Text("الأسبوع ${i + 1}: " + (t?.text?.ifBlank { null } ?: "—"), style = MaterialTheme.typography.bodyLarge,
                    color = if (s.today in w..w.plusDays(6)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                if (t != null && t.goal > 0) ProgressLine(Targets.weekDone(s.targets, w, series), t.goal)
            }
        }
        val start = Targets.weekStart(s.today)
        Text("أيام هالأسبوع", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Targets.weekDays(start).forEach { d ->
            val t = s.targets[Targets.dayKey(d)]?.get(series)
            val tasks = s.tasks.filter { it.day == d.toString() && seriesOf(it) == series && it.state != "skipped" }
            if (t != null || tasks.isNotEmpty()) Row {
                Text("${shortDay(d)} ${d.dayOfMonth}", Modifier.width(62.dp))
                Text(listOfNotNull(t?.text?.ifBlank { null }, t?.goal?.takeIf { it > 0 }?.let { "${t.done}/$it" },
                    tasks.takeIf { it.isNotEmpty() }?.let { l -> "${l.count { it.done }}/${l.size} مهام" }).joinToString(" · "), Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Secondary("تخطيط الشهر", { close(); openMonth() }, Modifier.weight(1f))
            Secondary("خطة الأسبوع", { close(); openWeek() }, Modifier.weight(1f))
        }
    }
}

// ---------------- Day planning by dragging ----------------

/**
 * One day: the periods on one side, the tasks still to place on the other. Hold a task and drag it into a period
 * (or tap it, then tap the period). The filled part of each period shows how much of its time is taken.
 */
@Composable
fun DayPlanSheet(s: Snapshot, day: LocalDate, close: () -> Unit, assign: (String, String) -> Unit, confirm: () -> Unit, details: (Task) -> Unit,
                 routine: (Task, Boolean) -> Unit = { _, _ -> }, addSeries: (String) -> Unit = {}, newTask: () -> Unit = {}) {
    var askRoutine by remember { mutableStateOf<Task?>(null) }
    var adding by remember { mutableStateOf(false) }
    val ds = day.toString()
    val tasks = s.tasks.filter { it.day == ds && it.state != "skipped" }
    val rules = s.ruleMap
    val periods = s.settings.periodsOn(day)
    val blocks = s.settings.configFor(day).blocks
    // Tasks are kept together by group (in the order of the groups), so related ones sit next to each other.
    val groupOrder = s.settings.groups.mapIndexed { i, g -> g.key to i }.toMap()
    val byGroup = compareBy<Task>({ groupOrder[it.category] ?: 99 }, { it.title })
    // Everything without a period waits here, routine too (nothing is placed by itself while planning).
    val pool = tasks.filter { it.open && it.period.isEmpty() }.sortedWith(byGroup)
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var dragging by remember { mutableStateOf<Task?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var picked by remember { mutableStateOf<Task?>(null) }
    val drop: (Task, Offset) -> Unit = { t, at ->
        bounds.entries.firstOrNull { it.value.contains(at) }?.key?.let { k -> if (k != t.period) assign(t.id, k) }
    }

    val haptic = LocalHapticFeedback.current
    // The task being moved: where its box was when the drag began, and how far the finger has gone since.
    var boxStart by remember { mutableStateOf(Offset.Zero) }
    var touchStart by remember { mutableStateOf(Offset.Zero) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var locked by remember { mutableStateOf<Task?>(null) }
    var byHandle by remember { mutableStateOf(false) }
    LaunchedEffect(locked) { if (locked != null) { kotlinx.coroutines.delay(2200); locked = null } }

    @Composable
    fun chipBody(t: Task, ghost: Boolean) {
        val tag = tagColor(colorIndexOf(t, s.settings))
        val routine = isRoutine(t, rules, s.settings)
        val look = s.settings.icons[seriesOf(t)]
        val target = s.targets[Targets.dayKey(day)]?.get(seriesOf(t))
        Row(
            Modifier.fillMaxWidth()
                .background(
                    when {
                        ghost -> MaterialTheme.colorScheme.primaryContainer
                        picked?.id == t.id -> MaterialTheme.colorScheme.primaryContainer
                        routine -> MaterialTheme.colorScheme.surfaceContainerHighest
                        else -> MaterialTheme.colorScheme.surface
                    }, RoundedCornerShape(12.dp))
                .border(
                    when { ghost -> 2.dp; locked?.id == t.id -> 2.dp; t.done -> 2.dp; else -> 1.dp },
                    when {
                        ghost -> MaterialTheme.colorScheme.primary
                        locked?.id == t.id -> Status.missed
                        t.done -> Status.done
                        else -> tag ?: MaterialTheme.colorScheme.outlineVariant
                    }, RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.width(26.dp).height(36.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(if (routine) "🔒" else "⠿", fontSize = if (routine) 13.sp else 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (look != null) TaskBadge(look, tag, 22.dp)
            Column(Modifier.weight(1f)) {
                Text(if (look != null && s.settings.nameStyle == "icon") t.title.take(12) else t.title, fontSize = 14.sp, maxLines = 2,
                    color = if (routine) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                target?.text?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1) }
            }
            Text(durLabel(t.minutes), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    fun chip(t: Task, inPeriod: Boolean) {
        var chipOrigin by remember { mutableStateOf(Offset.Zero) }
        var chipSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        val routine = isRoutine(t, rules, s.settings)
        val begin: (Offset) -> Unit = { off ->
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            dragging = t; picked = null
            boxStart = chipOrigin; boxSize = chipSize
            touchStart = chipOrigin + off; dragDelta = Offset.Zero
        }
        val end: () -> Unit = {
            dragging?.let { d -> drop(d, touchStart + dragDelta); haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }
            dragging = null
        }
        var lastTapUp by remember { mutableStateOf(0L) }
        var pressOff by remember { mutableStateOf(false) }
        Box(
            Modifier.fillMaxWidth()
                .onGloballyPositioned { chipOrigin = it.positionInRoot(); chipSize = it.size }
                // While it is being moved, its place stays as a faint outline.
                .graphicsLayer { alpha = if (dragging?.id == t.id) 0.2f else 1f }
                .pointerInput(t.id, routine) {
                    // Tap: pick (or "routine is fixed"). Double tap: details. Double tap and hold the second tap 0.7 s: routine on/off.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        if ((up.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
                        lastTapUp = System.currentTimeMillis()
                        val second = withTimeoutOrNull(350) { awaitFirstDown(requireUnconsumed = false) }
                        if (second == null) {
                            if (routine) locked = t else picked = if (picked?.id == t.id) null else t
                            return@awaitEachGesture
                        }
                        lastTapUp = System.currentTimeMillis()
                        val up2 = withTimeoutOrNull(700) { waitForUpOrCancellation() }
                        if (up2 == null) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            dragging = null
                            askRoutine = t
                            waitForUpOrCancellation()
                        } else details(t)
                    }
                }
                .then(
                    if (routine) Modifier else Modifier
                        .pointerInput(t.id, t.period) {
                            // Hold anywhere on the box, then move it (not when the hold is the second tap of a double tap).
                            detectDragGesturesAfterLongPress(
                                onDragStart = { off ->
                                    pressOff = byHandle || System.currentTimeMillis() - lastTapUp < 500
                                    if (!pressOff) begin(off)
                                },
                                onDrag = { change, amount -> if (!pressOff && !byHandle) { change.consume(); dragDelta += amount } },
                                onDragEnd = { if (!pressOff && !byHandle) end(); pressOff = false },
                                onDragCancel = { if (!pressOff && !byHandle) dragging = null; pressOff = false },
                            )
                        }
                        .pointerInput(t.id, t.period, "handle") {
                            // The handle (on the start side) moves it right away.
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val handleWidth = 34.dp.toPx()
                                // The page is right-to-left, so the handle sits on the right edge of the box.
                                val onHandle = down.position.x > size.width - handleWidth
                                if (!onHandle) return@awaitEachGesture
                                down.consume()
                                byHandle = true
                                begin(down.position)
                                var ok = true
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    dragDelta += ch.position - ch.previousPosition
                                    ch.consume()
                                }
                                if (ok) end()
                                byHandle = false
                            }
                        }
                ),
        ) { chipBody(t, ghost = false) }
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onGloballyPositioned { origin = it.positionInRoot() }) {
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("ترتيب ${if (day == s.today) "اليوم" else if (day == s.today.plusDays(1)) "بكرا" else dayLabel(day)}", style = MaterialTheme.typography.titleLarge)
                            Muted("اسحب من ⠿ لفترة. كبستين: التفاصيل. كبستين ومسك الثانية: روتين أو لا. 🔒 روتين ثابت.")
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
                                        inside.sortedWith(byGroup).forEach { t -> chip(t, true) }
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
                            Text("+ إضافة", Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
                                .clickable { adding = true }.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            if (pool.isEmpty()) Muted("كل المهام انحطت ✓")
                            pool.groupBy { it.category }.forEach { (cat, list) ->
                                val gi = s.settings.groups.indexOfFirst { it.key == cat }.coerceAtLeast(0)
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                    Box(Modifier.width(4.dp).height(18.dp).background(groupColor(s, cat, gi), RoundedCornerShape(2.dp)))
                                    Text("  ${Cat.name(cat)} · ${list.size}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                list.forEach { t -> chip(t, false) }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Action("تثبيت الخطة", { confirm(); close() }, Modifier.weight(1f))
                        Secondary("إغلاق", close, Modifier.weight(1f))
                    }
                    if (pool.isNotEmpty()) Muted("عند التثبيت: المهام اللي ضلت بالقائمة بتترحّل لليوم اللي بعده.")
                }
                locked?.let { t ->
                    Box(Modifier.align(Alignment.TopCenter).padding(top = 70.dp).background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Text("«${t.baseTitle}» روتين ثابت بفترته، ما بيتحرك", color = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                }
                dragging?.let { t ->
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    Box(
                        Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopLeft)
                            .absoluteOffset { IntOffset((boxStart.x + dragDelta.x - origin.x).roundToInt(), (boxStart.y + dragDelta.y - origin.y).roundToInt()) }
                            .width(with(density) { boxSize.width.toDp() })
                            .graphicsLayer { scaleX = 1.05f; scaleY = 1.05f; shadowElevation = 16f; shape = RoundedCornerShape(12.dp); clip = true }
                    ) { chipBody(t, ghost = true) }
                }
            }
        }
    }
    if (adding) {
        // What can go into this day: first what the week plan has for it, then every goal and recurring task.
        val openSeries = tasks.filter { it.open }.map { seriesOf(it) }.toSet()
        val all = planItemsAll(s).filter { !it.series.startsWith("g:") && s.rules.any { r -> r.id == it.series } }
        val dayKey = Targets.dayKey(day); val weekKey = Targets.weekKey(Targets.weekStart(day))
        val planned = (s.targets[dayKey]?.keys.orEmpty() + s.targets[weekKey]?.keys.orEmpty()).toSet()
        val first = all.filter { it.series in planned }; val rest = all.filter { it.series !in planned }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("إضافة لهاليوم") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    Text("+ مهمة جديدة", Modifier.fillMaxWidth().clickable { adding = false; newTask() }.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    @Composable fun line(c: PlanItem) {
                        val there = c.series in openSeries
                        Text(c.title + if (there) " · موجودة باليوم" else "", Modifier.fillMaxWidth().clickable { adding = false; addSeries(c.series) }.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge, color = if (there) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        HorizontalDivider()
                    }
                    if (first.isNotEmpty()) { Text("من خطة الأسبوع", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp)); first.forEach { line(it) } }
                    if (rest.isNotEmpty()) { Text("كل الأهداف والمهام المتكررة", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp)); rest.forEach { line(it) } }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { adding = false }) { Text("إغلاق") } },
        )
    }
    askRoutine?.let { t ->
        val isR = isRoutine(t, rules, s.settings)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askRoutine = null },
            title = { Text(t.title) },
            text = { Text(if (isR) "هاي روتين ثابت بفترته. بدك تبطّلها روتين عشان تقدر تحركها؟" else "بدك تخليها روتين؟ بتضل بمكانها وبتصير ثابتة، وكل مرة بتيجي بنفس الفترة.") },
            confirmButton = { TextButton(onClick = { routine(t, !isR); askRoutine = null }) { Text(if (isR) "بطّل روتين" else "خليها روتين") } },
            dismissButton = { TextButton(onClick = { askRoutine = null }) { Text("إلغاء") } },
        )
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
