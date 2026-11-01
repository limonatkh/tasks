@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.imad.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.util.UUID

/** The picture of a record: the user's choice, else the one it started with; "-" means none. */
fun recordIcon(s: Settings, g: Goal): String? =
    s.icons["goal:${g.id}"]?.let { if (it == "-") null else it } ?: SportIcons.defaults[g.id]?.let { "s:$it" }

fun recordType(category: String) = when (category) {
    "Strength" -> "قوة"; "Calisthenics" -> "وزن الجسم"; "Speed" -> "سرعة وقفز"; "" -> "أخرى"; else -> category
}

class SportOps(
    val saveRecord: (Goal) -> Unit,
    val deleteRecord: (String) -> Unit,
    val pause: (String, Boolean) -> Unit,
    val icon: (String, String) -> Unit,
    val saveSession: (Rule, List<Exercise>) -> Unit,
    val deleteSession: (String) -> Unit,
    val set: (String, String) -> Unit,
    val weekPlan: () -> Unit,
)

/** Records and training sessions in one place, everything editable. */
@Composable
fun SportsSheet(s: Snapshot, close: () -> Unit, ops: SportOps) {
    var tab by remember { mutableStateOf(0) }
    var editRecord by remember { mutableStateOf<Goal?>(null) }
    var quick by remember { mutableStateOf<Goal?>(null) }
    var editSession by remember { mutableStateOf<Rule?>(null) }
    val sessions = s.rules.filter { it.category == "sport" && !it.id.startsWith("plan-") }
    val trained = sessions.flatMap { Members.goals(it) }.toSet()
    val st = s.settings
    Sheet("الرياضة", close) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(tab == 0, { tab = 0 }, label = { Text("الأرقام · ${s.goals.size}") })
            FilterChip(tab == 1, { tab = 1 }, label = { Text("الحصص · ${sessions.size}") })
        }
        if (tab == 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("العرض:", style = MaterialTheme.typography.bodyMedium)
                FilterChip(st.recordStyle == "both", { ops.set("recordStyle", "both") }, label = { Text("صورة واسم") })
                FilterChip(st.recordStyle == "icon", { ops.set("recordStyle", "icon") }, label = { Text("صورة بس") })
            }
            Action("+ رقم جديد", {
                editRecord = Goal("rec-" + UUID.randomUUID().toString().take(8), "", "Strength", 0.0, 0.0, "kg", false,
                    startDay = s.today.toString(), targetDay = s.today.plusYears(1).toString())
            }, Modifier.fillMaxWidth())
            val (active, paused) = s.goals.partition { it.id !in st.pausedGoals }
            active.groupBy { recordType(it.category) }.forEach { (type, list) ->
                Text(type, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (st.recordStyle == "icon") FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    list.forEach { g -> RecordTile(s, g, g.id in trained, { editRecord = g }, { quick = g }) }
                } else list.forEach { g -> RecordRow(s, g, g.id in trained, { editRecord = g }, { quick = g }) }
            }
            if (paused.isNotEmpty()) {
                Text("موقوفة", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                paused.forEach { g -> Box(Modifier.alpha(0.55f)) { RecordRow(s, g, g.id in trained, { editRecord = g }, { quick = g }) } }
            }
        } else {
            SwitchRow("الحصص بتنحط على الأيام من خطة الأسبوع", st.sportByWeek) { ops.set("sportByWeek", if (it) "1" else "0") }
            Muted(if (st.sportByWeek) "ضيف الحصة بخطة الأسبوع، وحدد كم جلسة وبأي أيام." else "كل حصة بتتكرر لحالها حسب تكرارها.")
            if (st.sportByWeek) Secondary("خطة الأسبوع", { close(); ops.weekPlan() }, Modifier.fillMaxWidth())
            Action("+ حصة جديدة", {
                editSession = Rule(id = "sess-" + UUID.randomUUID().toString().take(8), title = "", minutes = 45, everyHours = 72, kind = "training",
                    category = "sport", sport = 1, anchorDay = s.today.toString(), active = true)
            }, Modifier.fillMaxWidth())
            sessions.forEach { r ->
                val members = Members.parse(r.members)
                Panel(Modifier.clickable { editSession = r }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TaskBadge(st.icons[r.id], tagColor(colorIndexOf(Task(title = r.title, minutes = 1, day = "", ruleId = r.id, category = r.category), st)), 28.dp)
                        Text(r.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(if (r.sport >= 2) "ثقيلة" else "خفيفة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    members.forEach { e ->
                        val g = e.goal?.let { id -> s.goals.firstOrNull { it.id == id } }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            g?.let { recordIcon(st, it) }?.let { TaskBadge(it, null, 20.dp) }
                            Text("${e.name}: ${e.sets} × ${e.workSec} ث · راحة ${e.restSec / 60} د", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Muted(durLabel(r.minutes) + if (!st.sportByWeek) " · " + ruleSummary(r) else "")
                }
            }
        }
    }
    editRecord?.let { g -> RecordEditor(s, g, isNew = s.goals.none { it.id == g.id }, close = { editRecord = null }, ops = ops) }
    quick?.let { g -> QuickRecordDialog(g, close = { quick = null }) { v -> ops.saveRecord(g.copy(current = v)) } }
    editSession?.let { r -> SessionEditor(s, r, isNew = s.rules.none { it.id == r.id }, close = { editSession = null }, ops = ops) }
}

@Composable
private fun RecordRow(s: Snapshot, g: Goal, trained: Boolean, open: () -> Unit, quick: () -> Unit) {
    val pct = Scheduler.achievement(g.current, g.reference, g.lower)
    Panel(Modifier.clickable(onClick = open)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            recordIcon(s.settings, g)?.let { TaskBadge(it, MaterialTheme.colorScheme.primary, 34.dp) }
            Column(Modifier.weight(1f)) {
                Text(g.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${num(g.current)} ← ${num(g.reference)} ${g.unit}" + if (g.lower) " · الأقل أحسن" else "", style = MaterialTheme.typography.bodyMedium)
                if (!trained) Text("متابعة بس (ما إله حصة)", fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
            }
            TextButton(onClick = quick) { Text("رقم جديد") }
        }
        MeterRow("${num(pct)}٪", pct.toInt().coerceAtLeast(0), 100)
    }
}

@Composable
private fun RecordTile(s: Snapshot, g: Goal, trained: Boolean, open: () -> Unit, quick: () -> Unit) {
    val pct = Scheduler.achievement(g.current, g.reference, g.lower)
    Column(
        Modifier.width(96.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).clickable(onClick = open).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val icon = recordIcon(s.settings, g)
        if (icon != null) TaskBadge(icon, MaterialTheme.colorScheme.primary, 44.dp) else Text(g.name.take(10), fontWeight = FontWeight.Bold)
        Text("${num(g.current)}/${num(g.reference)}", fontSize = 12.sp)
        Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth((pct / 100).toFloat().coerceIn(0f, 1f)).height(6.dp).background(if (pct >= 100) Status.done else MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
        }
        TextButton(onClick = quick) { Text("+ رقم", fontSize = 12.sp) }
    }
}

@Composable
fun QuickRecordDialog(g: Goal, close: () -> Unit, save: (Double) -> Unit) {
    var v by remember { mutableStateOf(num(g.current)) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("رقم جديد: ${g.name}") },
        text = { Column { Muted("آخر رقم ${num(g.current)} ${g.unit}"); Field(v, g.unit, { v = it }, numeric = true) } },
        confirmButton = { TextButton(onClick = { digits(v).toDoubleOrNull()?.takeIf { it > 0 }?.let { save(it); close() } }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
    )
}

@Composable
private fun RecordEditor(s: Snapshot, g: Goal, isNew: Boolean, close: () -> Unit, ops: SportOps) {
    var name by remember { mutableStateOf(g.name) }
    var type by remember { mutableStateOf(g.category) }
    var unit by remember { mutableStateOf(g.unit) }
    var lower by remember { mutableStateOf(g.lower) }
    var current by remember { mutableStateOf(if (isNew) "" else num(g.current)) }
    var reference by remember { mutableStateOf(if (isNew) "" else num(g.reference)) }
    var targetDay by remember { mutableStateOf(g.targetDay) }
    var icon by remember { mutableStateOf(recordIcon(s.settings, g)) }
    var confirmDelete by remember { mutableStateOf(false) }
    val paused = g.id in s.settings.pausedGoals
    val logs = s.logs.filter { it.goalId == g.id }
    Sheet(if (isNew) "رقم جديد" else "تعديل الرقم", close) {
        Field(name, "الاسم", { name = it })
        Text("النوع", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Strength", "Calisthenics", "Speed").forEach { t -> FilterChip(type == t, { type = t }, label = { Text(recordType(t)) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(current, "الحالي", { current = it }, numeric = true, modifier = Modifier.weight(1f))
            Field(reference, "الهدف", { reference = it }, numeric = true, modifier = Modifier.weight(1f))
            Field(unit, "الوحدة", { unit = it }, modifier = Modifier.weight(0.8f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("kg", "تكرار", "ثانية", "متر", "مرة").forEach { u -> FilterChip(unit == u, { unit = u }, label = { Text(u) }) }
        }
        SwitchRow("الأقل أحسن (زي الزمن)", lower) { lower = it }
        Field(targetDay, "تاريخ الهدف (yyyy-mm-dd)", { targetDay = it })
        Text("الصورة", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(42.dp).background(if (icon == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                .clickable { icon = null }, contentAlignment = Alignment.Center) { Text("—") }
            SportIcons.all.forEach { (k, label) ->
                Box(Modifier.size(42.dp).background(if (icon == "s:$k") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .clickable { icon = "s:$k" }, contentAlignment = Alignment.Center) { SportIcon(k, MaterialTheme.colorScheme.onSurface, 30.dp) }
            }
        }
        val c = digits(current).toDoubleOrNull(); val r = digits(reference).toDoubleOrNull()
        val td = runCatching { LocalDate.parse(targetDay) }.getOrNull()
        val ok = name.isNotBlank() && c != null && r != null && c > 0 && r > 0 && td != null
        Action("حفظ", {
            if (ok) {
                ops.saveRecord(g.copy(name = name.trim(), category = type, unit = unit.trim(), lower = lower, current = c!!, reference = r!!, targetDay = td.toString(),
                    baseline = if (isNew) c else g.baseline))
                ops.icon("goal:${g.id}", icon ?: "-")
                close()
            }
        }, Modifier.fillMaxWidth(), enabled = ok)
        if (!isNew) {
            Secondary(if (paused) "رجّع متابعته" else "إيقاف مؤقت (بيضل سجله)", { ops.pause(g.id, !paused); close() }, Modifier.fillMaxWidth())
            Secondary("حذف", { confirmDelete = true }, Modifier.fillMaxWidth())
            if (logs.isNotEmpty()) {
                HorizontalDivider()
                Text("السجل", style = MaterialTheme.typography.titleMedium)
                logs.takeLast(8).reversed().forEach { Text("${it.day}: ${num(it.value)} ${g.unit}") }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("حذف «${g.name}»؟") },
        text = { Text("بينحذف الرقم وسجله. التمارين المرتبطة فيه بتضل بحصصها بدون رقم.") },
        confirmButton = { TextButton(onClick = { ops.deleteRecord(g.id); confirmDelete = false; close() }) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
    )
}

@Composable
private fun SessionEditor(s: Snapshot, r: Rule, isNew: Boolean, close: () -> Unit, ops: SportOps) {
    var title by remember { mutableStateOf(r.title) }
    var heavy by remember { mutableStateOf(r.sport >= 2) }
    var active by remember { mutableStateOf(r.active) }
    var every by remember { mutableStateOf((r.everyHours / 24).coerceAtLeast(1).toString()) }
    var icon by remember { mutableStateOf(s.settings.icons[r.id]) }
    val list = remember { mutableStateListOf<Exercise>().apply { addAll(Members.parse(r.members)) } }
    var linkFor by remember { mutableStateOf<Int?>(null) }
    var moveFor by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val others = s.rules.filter { it.category == "sport" && it.id != r.id && !it.id.startsWith("plan-") }
    Sheet(if (isNew) "حصة جديدة" else "تعديل الحصة", close) {
        Field(title, "اسم الحصة", { title = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!heavy, { heavy = false }, label = { Text("خفيفة") })
            FilterChip(heavy, { heavy = true }, label = { Text("ثقيلة (بتخلص قبل الموعد المحدد)") })
        }
        if (!s.settings.sportByWeek) Field(every, "كل كم يوم", { every = it }, numeric = true)
        SwitchRow("شغّالة", active) { active = it }
        Text("التمارين", style = MaterialTheme.typography.titleMedium)
        list.forEachIndexed { i, e ->
            val g = e.goal?.let { id -> s.goals.firstOrNull { it.id == id } }
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(e.name, { v -> list[i] = e.copy(name = v) }, Modifier.weight(1f), singleLine = true, label = { Text("التمرين") })
                    TextButton(onClick = { if (i > 0) { val x = list.removeAt(i); list.add(i - 1, x) } }, enabled = i > 0) { Text("↑") }
                    TextButton(onClick = { if (i < list.size - 1) { val x = list.removeAt(i); list.add(i + 1, x) } }, enabled = i < list.size - 1) { Text("↓") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumBox(e.sets, "جولات", Modifier.weight(1f)) { list[i] = e.copy(sets = it.coerceIn(1, 30)) }
                    NumBox(e.workSec, "ثواني شغل", Modifier.weight(1f)) { list[i] = e.copy(workSec = it.coerceIn(5, 3600)) }
                    NumBox(e.restSec, "ثواني راحة", Modifier.weight(1f)) { list[i] = e.copy(restSec = it.coerceIn(0, 1800)) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(false, { linkFor = i }, label = { Text(g?.let { "رقمه: ${it.name}" } ?: "اربطه برقم") })
                    if (others.isNotEmpty()) FilterChip(false, { moveFor = i }, label = { Text("انقله لحصة ثانية") })
                    FilterChip(false, { list.removeAt(i) }, label = { Text("شيل", color = MaterialTheme.colorScheme.error) })
                }
            }
        }
        Secondary("+ تمرين", { list.add(Exercise(null, "", 3, 45, 120)) }, Modifier.fillMaxWidth())
        val sh = Members.session(list)
        if (list.isNotEmpty()) Muted("مدة الحصة تقريباً ${durLabel(sh.minutes)} (مع ١٠ د إحماء)")
        Text("الصورة", style = MaterialTheme.typography.labelLarge)
        IconPicker(icon, null) { icon = it }
        Action("حفظ", {
            ops.saveSession(r.copy(title = title.trim(), sport = if (heavy) 2 else 1, active = active,
                everyHours = (digits(every).toIntOrNull() ?: 3).coerceIn(1, 30) * 24), list.filter { it.name.isNotBlank() })
            ops.icon(r.id, icon ?: "")
            close()
        }, Modifier.fillMaxWidth(), enabled = title.isNotBlank())
        if (!isNew) Secondary("حذف الحصة", { confirmDelete = true }, Modifier.fillMaxWidth())
    }
    linkFor?.let { i ->
        AlertDialog(
            onDismissRequest = { linkFor = null },
            title = { Text("أي رقم بيتدرّب عليه؟") },
            text = {
                Column {
                    Text("بدون رقم", Modifier.fillMaxWidth().clickable { list[i] = list[i].copy(goal = null); linkFor = null }.padding(vertical = 10.dp))
                    s.goals.forEach { g ->
                        Row(Modifier.fillMaxWidth().clickable { list[i] = list[i].copy(goal = g.id, name = list[i].name.ifBlank { g.name }); linkFor = null }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            recordIcon(s.settings, g)?.let { TaskBadge(it, null, 22.dp) }
                            Text(g.name)
                        }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { linkFor = null }) { Text("إلغاء") } },
        )
    }
    moveFor?.let { i ->
        AlertDialog(
            onDismissRequest = { moveFor = null },
            title = { Text("لأي حصة؟") },
            text = {
                Column {
                    others.forEach { o ->
                        Text(o.title, Modifier.fillMaxWidth().clickable {
                            val e = list.removeAt(i)
                            ops.saveSession(o, Members.parse(o.members) + e)
                            if (!isNew) ops.saveSession(r, list.toList())
                            moveFor = null
                        }.padding(vertical = 10.dp))
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { moveFor = null }) { Text("إلغاء") } },
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("حذف «${r.title}»؟") },
        text = { Text("بتنحذف الحصة وجلساتها اللي ما بلّشت. الأرقام بتضل.") },
        confirmButton = { TextButton(onClick = { ops.deleteSession(r.id); confirmDelete = false; close() }) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
    )
}

@Composable
private fun NumBox(value: Int, label: String, modifier: Modifier, change: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(text, { v -> text = v; digits(v).toIntOrNull()?.let(change) }, modifier, singleLine = true, label = { Text(label, fontSize = 11.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}
