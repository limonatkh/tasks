@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.imad.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.UUID

// ---------------- Small dialogs ----------------

@Composable
fun TextDialog(title: String, hint: String, allowEmpty: Boolean = false, close: () -> Unit, done: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(hint) }, modifier = Modifier.fillMaxWidth(), minLines = 2) },
        confirmButton = { TextButton(onClick = { done(text) }, enabled = allowEmpty || text.isNotBlank()) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
    )
}

/** Tap on a task in the day list. */
@Composable
fun TaskMenu(
    t: Task, close: () -> Unit, startNow: () -> Unit, details: () -> Unit, report: () -> Unit, note: () -> Unit,
    prayerMode: Boolean = false, movePeriod: () -> Unit = {}, place: () -> Unit = {}, postpone: (() -> Unit)? = null,
) {
    Sheet(t.title, close) {
        TimeLine(t.planned, t.minutes, end = true)
        if (t.open && !t.running) Action("ابدأ فيها الآن", startNow, Modifier.fillMaxWidth())
        if (t.open && !t.running) Muted("المهمة اللي كان وقتها هلأ بتاخد موعد هاي المهمة.")
        if (prayerMode && t.open) Secondary("نقل لفترة ثانية اليوم", movePeriod, Modifier.fillMaxWidth())
        if (prayerMode) Secondary("وقتها بالصلوات (دايماً)", place, Modifier.fillMaxWidth())
        postpone?.let { Secondary("تأجيل لبكرا", it, Modifier.fillMaxWidth()) }
        Secondary("التفاصيل والتعديل", details, Modifier.fillMaxWidth())
        Secondary("التقرير", report, Modifier.fillMaxWidth())
        Secondary("ملاحظة", note, Modifier.fillMaxWidth())
    }
}

fun swipeLabel(mode: String) = when (mode) { "close" -> "إغلاق"; "later" -> "لوقت فاضي اليوم"; else -> "ترحيل لبكرا" }

/** Swipe right to start (when [start] is given), swipe left for the action chosen in the settings. */
@Composable
fun SwipeRow(start: (() -> Unit)?, defer: () -> Unit, deferMode: String, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.EndToStart -> start?.invoke()
            SwipeToDismissBoxValue.StartToEnd -> defer()
            else -> {}
        }
        false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromEndToStart = start != null,
        backgroundContent = {
            val toStart = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                Modifier.fillMaxSize().background(if (toStart) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary, RoundedCornerShape(20.dp)).padding(horizontal = 20.dp),
                contentAlignment = if (toStart) Alignment.CenterEnd else Alignment.CenterStart,
            ) { Text(if (toStart) "ابدأ الآن" else swipeLabel(deferMode), color = Color.White, fontWeight = FontWeight.Bold) }
        },
    ) { content() }
}

/** Ask before postponing; the note is optional. */
@Composable
fun PostponeDialog(t: Task, close: () -> Unit, done: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("تأجيل «${t.baseTitle}» لبكرا؟") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("ملاحظة (اختياري): ليش أجّلتها؟") }, modifier = Modifier.fillMaxWidth(), minLines = 2) },
        confirmButton = { TextButton(onClick = { done(text) }) { Text("تأجيل") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
    )
}

// ---------------- History of one task ----------------

fun eventText(e: Event): String = when (e.type) {
    "start" -> "بدأت " + fmt12(Clock.minuteOfDay(e.at)) + when { e.delayMin > 5 -> " (متأخر ${durLabel(e.delayMin)})"; e.delayMin < -5 -> " (بدري ${durLabel(-e.delayMin)})"; else -> " (بوقتها)" }
    "finish" -> "خلصت " + fmt12(Clock.minuteOfDay(e.at)) + if (e.delayMin > 5) " (بعد الموعد بـ ${durLabel(e.delayMin)})" else ""
    "pause" -> "توقفت مؤقتاً " + fmt12(Clock.minuteOfDay(e.at))
    "postpone" -> "تأجلت" + if (e.text.isNotBlank()) ": ${e.text}" else ""
    "rolled" -> "رُحّلت لليوم التالي: ${e.text}"
    "missed" -> "فاتت ولم تُنجز"
    "extend" -> "تمديد ${e.text}"
    "note" -> "ملاحظة: ${e.text}"
    else -> e.type
}

data class SeriesStats(val done: Int, val missed: Int, val postponed: Int, val avgDelay: Int?, val usualStart: Int?)

fun seriesStats(series: String, s: Snapshot, days: Int = 14): SeriesStats {
    val from = s.today.minusDays(days.toLong()).toString()
    val tasks = s.tasks.filter { seriesOf(it) == series && it.day >= from && it.day < s.today.toString() }
    val ev = s.events.filter { it.series == series && it.day >= from }
    val starts = ev.filter { it.type == "start" }
    return SeriesStats(
        done = tasks.count { it.done && it.phase == 0 },
        missed = tasks.count { it.state == "skipped" && it.phase == 0 },
        postponed = ev.count { it.type == "postpone" },
        avgDelay = starts.takeIf { it.size >= 2 }?.map { it.delayMin }?.average()?.toInt(),
        usualStart = starts.takeIf { it.size >= 2 }?.map { Clock.minuteOfDay(it.at) }?.sorted()?.let { it[it.size / 2] },
    )
}

@Composable
fun ReportSheet(series: String, title: String, s: Snapshot, close: () -> Unit, note: (String) -> Unit) {
    val tasks = s.tasks.filter { seriesOf(it) == series }.sortedByDescending { it.day }
    val ev = s.events.filter { it.series == series }
    val st = seriesStats(series, s, 30)
    var text by remember { mutableStateOf("") }
    Sheet("تقرير: $title", close) {
        Panel {
            Text("آخر ٣٠ يوم", style = MaterialTheme.typography.titleMedium)
            Text("انعملت ${st.done} · فاتت ${st.missed} · تأجلت ${st.postponed}", style = MaterialTheme.typography.bodyLarge)
            st.avgDelay?.let { Text(if (it > 5) "بتبلّش متأخر بمعدّل ${durLabel(it)}" else "بتبلّش عادةً بوقتها", style = MaterialTheme.typography.bodyLarge) }
            st.usualStart?.let { Muted("عادةً بتبلّش الساعة ${fmt12(it)}") }
        }
        val days = (tasks.map { it.day } + ev.map { it.day }).distinct().sortedDescending().take(40)
        if (days.isEmpty()) Muted("لا يوجد سجل بعد.")
        days.forEach { d ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(dayLabel(LocalDate.parse(d)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                tasks.filter { it.day == d }.forEach { t ->
                    Muted("${t.title} · " + when { t.done -> "منجزة"; t.state == "skipped" -> "لم تُنجز"; t.running -> "جارية"; else -> "مخططة " + timeLabel(t.planned) })
                }
                ev.filter { it.day == d }.sortedBy { it.at }.forEach { e -> Text("• " + eventText(e), style = MaterialTheme.typography.bodyMedium) }
            }
            HorizontalDivider()
        }
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("أضف ملاحظة") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Action("حفظ الملاحظة", { note(text); text = "" }, Modifier.fillMaxWidth(), enabled = text.isNotBlank())
    }
}

// ---------------- Ahead / behind ----------------

private data class PaceRow(val title: String, val series: String?, val ahead: Boolean, val text: String, val advice: String)

private fun advice(ahead: Boolean, st: SeriesStats?, title: String): String {
    if (ahead) return "متقدم. بتقدر تخفف أو ترفع الهدف."
    if (st == null) return "متأخر عن الخطة."
    val total = st.done + st.missed
    val rate = if (total > 0) st.done * 100 / total else 100
    return when {
        rate < 70 -> "بتفوت ${100 - rate}٪ من جلساتها. جرّب وقت ثاني أو مدة أقصر" + (st.usualStart?.let { "، مثلاً حوالي ${fmt12(it)} لما بتبلّش عادةً" } ?: "") + "."
        (st.avgDelay ?: 0) > 20 -> "بتبلّش متأخر بمعدّل ${durLabel(st.avgDelay!!)}. حط وقتها أبكر أو ثبّت لها موعد."
        else -> "بتلتزم بالجلسات بس التقدم أبطأ من الخطة. زيد الحصة أو عدد الجلسات."
    }
}

fun LazyListScope.progressReport(s: Snapshot, nav: Nav) {
    val rows = mutableListOf<PaceRow>()
    for (g in s.goals) {
        val trainings = s.rules.filter { g.id in Members.goals(it) && it.active }
        if (trainings.isEmpty() && s.logs.none { it.goalId == g.id && it.day > g.startDay }) continue
        val base = Scheduler.achievement(g.baseline, g.reference, g.lower)
        val expected = Scheduler.expectedAchievement(base, LocalDate.parse(g.startDay), LocalDate.parse(g.targetDay), s.today)
        val actual = Maths.achievement(g)
        val diff = actual - expected
        val ahead = diff >= 0
        val st = trainings.firstOrNull()?.let { seriesStats(it.id, s) }
        rows += PaceRow(g.name, trainings.firstOrNull()?.id, ahead,
            (if (ahead) "متقدم ${num(diff)}٪" else "متأخر ${num(-diff)}٪") + " · المفروض ${num(expected)}٪ وإنت ${num(actual)}٪" +
                (if (g.lower) " · هدف كل ما قلّ أحسن" else "") + (if (actual > 100) " · فوق ١٠٠٪ يعني تجاوزت الهدف" else ""),
            advice(ahead, st, g.name))
    }
    for (p in s.plans.filter { it.accepted }) {
        val (expected, actual) = PlanMath.pace(p, s.today) ?: continue
        val diff = actual - expected
        val ahead = diff >= -0.5
        val st = seriesStats("plan-${p.id}", s)
        rows += PaceRow(p.title, "plan-${p.id}", ahead,
            (if (ahead) "متقدم ${num(diff)}" else "متأخر ${num(-diff)}") + " ${p.unit} · المفروض ${num(expected)} وإنت ${num(actual)}", advice(ahead, st, p.title))
    }
    val weekStart = s.today.minusDays(6).toString()
    val weekTasks = s.tasks.filter { it.day in weekStart..s.today.toString() && (it.done || it.state == "skipped") }
    val moved = s.events.count { it.type == "postpone" && it.day >= weekStart }
    item {
        Panel {
            Text("الالتزام (آخر ٧ أيام)", style = MaterialTheme.typography.titleMedium)
            if (weekTasks.isEmpty()) Muted("لسا ما في أيام مسكّرة.")
            else {
                val done = weekTasks.count { it.done }
                Text("${done} منجزة من ${weekTasks.size} مهمة انحسمت (${(done * 100 / weekTasks.size)}٪) · $moved تأجيل", style = MaterialTheme.typography.bodyLarge)
            }
            Muted("الالتزام = قديش عملت من المهام المجدولة. التقدم تحت = قديش قرّبت من الهدف نفسه. ممكن تكون ملتزم وتقدمك بطيء لو الخطة خفيفة، أو العكس.")
        }
    }
    item {
        Panel {
            Text("التقدم", style = MaterialTheme.typography.titleMedium)
            if (rows.isEmpty()) Muted("بيظهر هون لما تبلّش تسجّل إنجاز.")
            rows.sortedBy { it.ahead }.forEach { r ->
                Column(Modifier.fillMaxWidth().clickable(enabled = r.series != null) { r.series?.let { nav.report(it, r.title) } }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(r.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(r.text, style = MaterialTheme.typography.bodyMedium, color = if (r.ahead) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                    Muted(r.advice)
                }
                HorizontalDivider()
            }
        }
    }
    val weekAgo = s.today.minusDays(7).toString()
    val notes = s.events.filter { (it.type == "note" || (it.type == "postpone" && it.text.isNotBlank())) && it.day >= weekAgo }.sortedByDescending { it.at }
    item {
        Panel {
            Text("ملاحظات الأسبوع", style = MaterialTheme.typography.titleMedium)
            if (notes.isEmpty()) Muted("سجّل ملاحظة على أي مهمة (ليش تأخرت أو ما خلصتها) وبتطلع هون.")
            notes.forEach { e ->
                val title = s.tasks.firstOrNull { it.id == e.taskId }?.baseTitle ?: ""
                Column(Modifier.fillMaxWidth().clickable { nav.report(e.series, title) }) {
                    Text("$title · ${dayLabel(LocalDate.parse(e.day))}", style = MaterialTheme.typography.labelLarge)
                    Muted(eventText(e))
                }
            }
        }
    }
}

// ---------------- Groups ----------------

@Composable
fun GroupsSheet(s: Snapshot, close: () -> Unit, save: (List<Group>) -> Unit, setPeriod: (String, String) -> Unit = { _, _ -> }) {
    val list = remember { mutableStateListOf<Group>().apply { addAll(s.settings.groups) } }
    val used = (s.goals.map { Cat.ofGoal(it) } + s.plans.map { Cat.ofPlan(it) } + s.rules.map { it.category }).toSet()
    Sheet("المجموعات", close) {
        Muted("كل مجموعة إلها وقت باليوم بتنرتب فيه مهامها. بتقدر تنقل أي هدف لمجموعة ثانية من صفحته.")
        list.forEachIndexed { i, g ->
            Panel {
                Field(g.name, "الاسم", { list[i] = g.copy(name = it) })
                if (g.key != "sport") FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("morning", "work", "evening", "any").forEach { slot ->
                        FilterChip(selected = g.slot == slot, onClick = { list[i] = g.copy(slot = slot) }, label = { Text(Groups.slotName(slot)) })
                    }
                } else Muted("الرياضة بتترتب حسب شدة التمرين.")
                if (s.settings.prayerMode) {
                    Text("فترتها بنظام الصلوات", style = MaterialTheme.typography.labelLarge)
                    val cur = s.settings.groupPeriods[g.key]
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = cur == null, onClick = { setPeriod(g.key, "") }, label = { Text("حسب الوقت فوق") })
                        Prayer.keys.forEach { k -> FilterChip(selected = cur == k, onClick = { setPeriod(g.key, k) }, label = { Text(Prayer.periodNames.getValue(k)) }) }
                    }
                }
                SwitchRow("مسموح وقت الدوام", g.atWork) { list[i] = g.copy(atWork = it) }
                Text("لون المجموعة", style = MaterialTheme.typography.labelLarge)
                ColorPicker(g.color) { list[i] = g.copy(color = it) }
                if (Groups.defaults.none { it.key == g.key }) {
                    if (g.key in used) Muted("فيها أهداف؛ انقلها قبل الحذف.")
                    else TextButton(onClick = { list.removeAt(i) }) { Text("حذف المجموعة") }
                }
            }
        }
        Secondary("+ مجموعة جديدة", { list.add(Group("g-" + UUID.randomUUID().toString().take(6), "مجموعة جديدة", "any")) }, Modifier.fillMaxWidth())
        Action("حفظ", { save(list.filter { it.name.isNotBlank() }); close() }, Modifier.fillMaxWidth())
    }
}
