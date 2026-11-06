@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.imad.planner

import androidx.compose.runtime.mutableFloatStateOf

import androidx.compose.ui.layout.positionInRoot

import androidx.compose.ui.layout.onGloballyPositioned

import androidx.compose.ui.AbsoluteAlignment

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
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

/** Everything that can be done with one task, the same menu everywhere. */
class TaskOps(
    val start: (() -> Unit)? = null,
    val finish: (() -> Unit)? = null,
    val movePeriod: (() -> Unit)? = null,
    val moveDay: (() -> Unit)? = null,
    val postpone: (() -> Unit)? = null,
    val duplicate: (() -> Unit)? = null,
    val split: (() -> Unit)? = null,
    val youtube: (() -> Unit)? = null,
    val details: (() -> Unit)? = null,
    val edit: (() -> Unit)? = null,
    val place: (() -> Unit)? = null,
    val quran: (() -> Unit)? = null,
    val report: (() -> Unit)? = null,
    val note: (() -> Unit)? = null,
    val fixTime: (() -> Unit)? = null,
    /** A focus session (pomodoro) whose time goes to this task. */
    val focus: (() -> Unit)? = null,
    val close: (() -> Unit)? = null,
    val delete: (() -> Unit)? = null,
)

@Composable
fun TaskMenu(t: Task, close: () -> Unit, ops: TaskOps, yt: YtLink? = null, domain: Domain = Domain.Other, look: String? = null, goal: GoalLink? = null) {
    var confirmDelete by remember { mutableStateOf(false) }
    Sheet("", close) {
        fun go(f: (() -> Unit)?) = { close(); f?.invoke(); Unit }
        var moving by remember { mutableStateOf(false) }
        var more by remember { mutableStateOf(false) }
        // Who it is: its drawing, name and where it sits.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DomainTile(domain, 68.dp, look, done = !t.open)
            Column(Modifier.weight(1f)) {
                Text(t.title, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 30.sp)
                if (yt != null) Muted("الحلقة ${yt.index + 1}" + (if (yt.total > 0) " من ${yt.total}" else "") + (if (yt.pos > 5) " · وقفت عند ${yt.pos / 60}:${"%02d".format(yt.pos % 60)}" else "") + if (t.units > 0) " · اليوم ${t.doneUnits}/${t.units} حلقة" else "")
                else Muted(listOfNotNull(Prayer.names[t.period], durLabel(t.minutes), if (t.done) "منجزة ✓" else null, if (t.running) "شغّالة هلأ" else null).joinToString(" · "))
            }
        }
        if (t.details.isNotBlank()) Muted(t.details)
        // Which goal it feeds, one tap to it.
        goal?.let { g ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.tertiaryContainer).clickable(onClick = go(g.open)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("⛰", fontSize = 18.sp)
                Text("بتخدم هدف «${g.name}»", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(g.line + " ←", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
        // Everyday choices first: start, done.
        if (t.open) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ops.start?.takeIf { !t.running }?.let { Action(if (yt != null) "▶ كمّل الحلقة ${yt.index + 1}" else if (t.spent > 0) "كمّل" else "ابدأ جلسة", go(it), Modifier.weight(1f)) }
            ops.finish?.let { f ->
                val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                androidx.compose.material3.Button(onClick = go(f), modifier = Modifier.heightIn(min = 52.dp).pressScale(press), interactionSource = press, shape = RoundedCornerShape(16.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = doneColor(), contentColor = if (LocalThemeChoice.current.mono) MaterialTheme.colorScheme.surface else Color.White)) {
                    Text("✓ خلصت", fontWeight = FontWeight.Bold)
                }
            }
        }
        // The four things done most to a task, as big tiles.
        if (t.open) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickTile("⇄", if (moving) "نقل ▲" else "نقل", Modifier.weight(1f)) { moving = !moving }
            ops.postpone?.let { QuickTile("☾", "بكرا", Modifier.weight(1f), go(it)) }
            ops.fixTime?.let { QuickTile("±", "الوقت", Modifier.weight(1f), go(it)) }
            ops.edit?.let { QuickTile("✎", "تعديل", Modifier.weight(1f), go(it)) }
        }
        androidx.compose.animation.AnimatedVisibility(moving || !t.open) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (t.open) {
                    ops.movePeriod?.let { FilterChip(false, go(it), label = { Text("فترة ثانية") }) }
                    ops.moveDay?.let { FilterChip(false, go(it), label = { Text("ليوم ثاني…") }) }
                    ops.close?.let { FilterChip(false, go(it), label = { Text("إغلاق بدون إنجاز") }) }
                }
                ops.place?.let { FilterChip(false, go(it), label = { Text("فترتها دايماً") }) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ops.focus?.takeIf { !t.running && yt == null }?.let { FilterChip(false, go(it), label = { Text("جلسة تركيز 🍎") }) }
            ops.details?.let { FilterChip(false, go(it), label = { Text("تفاصيل") }) }
            ops.note?.let { FilterChip(false, go(it), label = { Text("ملاحظة") }) }
            ops.report?.let { FilterChip(false, go(it), label = { Text("السجل والتقرير") }) }
            if (!t.open) ops.fixTime?.let { FilterChip(false, go(it), label = { Text("تصحيح الوقت") }) }
            if (!t.open) ops.edit?.let { FilterChip(false, go(it), label = { Text("تعديل") }) }
        }
        TextButton(onClick = { more = !more }) { Text(if (more) "إعدادات المهمة ▲" else "إعدادات المهمة ▼") }
        androidx.compose.animation.AnimatedVisibility(more) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ops.edit?.let { FilterChip(false, go(it), label = { Text("مدة، مجموعة، لون، رمز، تكرار") }) }
                ops.duplicate?.let { FilterChip(false, go(it), label = { Text("نسخة") }) }
                if (t.open && !t.isPlan) ops.split?.let { FilterChip(false, go(it), label = { Text("قسّم") }) }
                ops.youtube?.let { FilterChip(false, go(it), label = { Text("قائمة يوتيوب") }) }
                ops.quran?.let { FilterChip(false, go(it), label = { Text("ورد القرآن") }) }
                ops.delete?.let { FilterChip(false, { confirmDelete = true }, label = { Text("حذف", color = MaterialTheme.colorScheme.error) }) }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("حذف «${t.baseTitle}»؟") },
        text = { Text("بتقدر ترجعها بزر التراجع.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; close(); ops.delete?.invoke() }) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
    )
}

fun swipeLabel(mode: String) = when (mode) { "close" -> "إغلاق"; "later" -> "لوقت فاضي اليوم"; else -> "ترحيل لبكرا" }

/**
 * Swipe right to start (when [start] is given), swipe left for the action chosen in the settings ([defer], when given).
 * Like the phone app: it needs a clear sideways swipe of about half the row, a direction with no action does not move
 * at all, and the action's colour, icon and word come in gradually as the row slides.
 */
@Composable
fun SwipeRow(start: (() -> Unit)?, defer: (() -> Unit)?, deferMode: String, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                // In RTL the box's values are mirrored: a swipe to the right ends as EndToStart.
                SwipeToDismissBoxValue.EndToStart -> start?.invoke()
                SwipeToDismissBoxValue.StartToEnd -> defer?.invoke()
                else -> {}
            }
            false
        },
        positionalThreshold = { width -> width * 0.45f },
    )
    // Which side is uncovered is read from where the card really is on screen, so RTL can't put the words under the card.
    var baseX by remember { mutableFloatStateOf(0f) }
    var shift by remember { mutableFloatStateOf(0f) }
    SwipeToDismissBox(
        state = state,
        enableDismissFromEndToStart = start != null,
        enableDismissFromStartToEnd = defer != null,
        backgroundContent = {
            val dir = state.dismissDirection
            Box(Modifier.fillMaxSize().onGloballyPositioned { baseX = it.positionInRoot().x }) {
                if (dir == SwipeToDismissBoxValue.Settled || kotlin.math.abs(shift) < 2f) return@Box
                // The colour and word follow where the card really moved (right = start), never the box's own direction,
                // which is not mirrored the same way as its values.
                val toStart = shift > 0
                if ((toStart && start == null) || (!toStart && defer == null)) return@Box
                val color = if (toStart) Brand.lantern else Brand.sky
                val fg = if (toStart) Brand.ink else Color.White
                Box(
                    Modifier.fillMaxSize().background(color, RoundedCornerShape(20.dp)).padding(horizontal = 22.dp),
                    contentAlignment = if (shift > 0) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (toStart) "▶" else "⏭", color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(if (toStart) "ابدأ الآن" else swipeLabel(deferMode), color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { Box(Modifier.onGloballyPositioned { shift = it.positionInRoot().x - baseX }) { content() } }
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
    // Few sessions say little: no firm advice before 5.
    if (total < 5) return "متأخر عن الخطة، بس في $total جلسات بس، قليلة عشان أحكم ليش."
    val rate = if (total > 0) st.done * 100 / total else 100
    return "(مبني على $total جلسة) " + when {
        rate < 70 -> "بتفوت ${100 - rate}٪ من جلساتها. جرّب وقت ثاني أو مدة أقصر" + (st.usualStart?.let { "، مثلاً حوالي ${fmt12(it)} لما بتبلّش عادةً" } ?: "") + "."
        (st.avgDelay ?: 0) > 20 -> "بتبلّش متأخر بمعدّل ${durLabel(st.avgDelay!!)}. حط وقتها أبكر أو ثبّت لها موعد."
        else -> "بتلتزم بالجلسات بس التقدم أبطأ من خطتك. ليش برأيك؟ يا إما الهدف عالي على هالمدة (عدّل الهدف أو أهداف الأشهر)، يا إما الجلسة نفسها بدها تتغير، يا إما ضيف جلسة. للرياضة: التأخر لحاله مش سبب تزيد التدريب."
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
    if (s.settings.prayerMode) item {
        val week = s.tasks.filter { it.day in weekStart..s.today.toString() && it.period.isNotEmpty() && it.day < s.today.toString() }
        Panel {
            Text("الفترات (آخر ٧ أيام)", style = MaterialTheme.typography.titleMedium)
            if (week.isEmpty()) Muted("بيظهر بعد أول يوم كامل بنظام الصلوات.")
            else {
                val rows = Prayer.keys.map { k -> val l = week.filter { it.period == k }; Triple(k, l.count { it.done }, l.size) }.filter { it.third > 0 }
                rows.forEach { (k, done, all) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Prayer.names.getValue(k), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text("$done من $all · ${done * 100 / all}٪", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                val best = rows.maxByOrNull { it.second.toDouble() / it.third }
                val worst = rows.minByOrNull { it.second.toDouble() / it.third }
                if (best != null && worst != null && best.first != worst.first)
                    Muted("أحسن فترة إلك: ${Prayer.names[best.first]}. حط فيها المهام الثقيلة. فترة ${Prayer.names[worst.first]} بتضيع فيها مهام أكثر، خفّف حملها.")
            }
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


/** The goal a task feeds: its name, how far it is ("٦ من ١٢"), and how to open it. */
class GoalLink(val name: String, val line: String, val open: () -> Unit)

/** A big square choice with a sign and a word. */
@Composable
fun QuickTile(sign: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier.heightIn(min = 66.dp).pressScale(press).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(interactionSource = press, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(sign, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
