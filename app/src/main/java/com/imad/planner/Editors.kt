@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.imad.planner

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.UUID

// ---------------- Shared pieces ----------------

@Composable
fun CategoryPicker(category: String, sport: Int, change: (String, Int) -> Unit) {
    Text("النوع", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Cat.keys.forEach { k ->
            FilterChip(selected = category == k, onClick = { change(k, if (k == "sport") maxOf(sport, 1) else 0) }, label = { Text(Cat.name(k)) })
        }
    }
    if (category == "sport") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = sport <= 1, onClick = { change(category, 1) }, label = { Text("خفيف (خلال النهار)") })
        FilterChip(selected = sport >= 2, onClick = { change(category, 2) }, label = { Text("ثقيل (يخلص قبل 9 م)") })
    }
}

/** Any number of parts over the day, with the share of each. */
@Composable
fun PhasesEditor(phases: List<Int>, change: (List<Int>) -> Unit) {
    fun even(n: Int): List<Int> = List(n) { i -> 100 / n + if (i < 100 % n) 1 else 0 }
    Text("تقسيمها خلال النهار", style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Secondary("−", { if (phases.size > 1) change(even(phases.size - 1)) })
        Text(if (phases.size == 1) "مرة وحدة" else "${phases.size} فترات", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Secondary("+", { if (phases.size < 12) change(even(phases.size + 1)) })
    }
    if (phases.size > 1) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            phases.forEachIndexed { i, p ->
                Field(p.toString(), "${i + 1} ٪", { v ->
                    val n = digits(v).toIntOrNull() ?: return@Field
                    val list = phases.toMutableList(); list[i] = n.coerceIn(1, 99)
                    if (list.size == 2) list[1 - i] = 100 - list[i]
                    change(list)
                }, numeric = true, modifier = Modifier.width(92.dp))
            }
        }
        val sum = phases.sum()
        if (sum != 100) Text("المجموع $sum٪ — بيتوزع بالنسبة", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Muted("إذا ما بلّشت فترة لحد ما يجي وقت اللي بعدها، اللي ضايل منها بيترحّل للي بعدها.")
    }
}

fun phasesText(l: List<Int>) = if (l.size > 1) l.joinToString(",") else ""

@Composable
fun RepeatPicker(everyHours: String, weekdays: Int, setHours: (String) -> Unit, setDays: (Int) -> Unit, recovery: Boolean = false) {
    Text("التكرار", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("24", "48", "72", "96", "168").forEach { h ->
            FilterChip(selected = weekdays == 0 && everyHours == h, onClick = { setHours(h); setDays(0) },
                label = { Text(if (h == "168") "مرة بالأسبوع" else "كل $h ساعة") })
        }
    }
    Field(everyHours, if (recovery) "كل كم ساعة (الاستشفاء)" else "أو كل كم ساعة", { setHours(it); setDays(0) }, numeric = true)
    Text("أو أيام محددة", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        weekdayNames.forEachIndexed { i, name ->
            FilterChip(selected = weekdays and (1 shl i) != 0, onClick = { setDays(weekdays xor (1 shl i)) }, label = { Text(name) })
        }
    }
}

// ---------------- Task ----------------

class TaskActions(val reopen: () -> Unit, val toToday: () -> Unit, val postpone: () -> Unit, val report: () -> Unit = {}, val note: () -> Unit = {})

@Composable
fun TaskEditor(
    t: Task, isNew: Boolean, rule: Rule?, now: Long, close: () -> Unit,
    save: (Task, Boolean, List<Int>) -> Unit, saveRule: (Rule) -> Unit, delete: () -> Unit, actions: TaskActions?,
    routineNow: Boolean = false, colorNow: Int = -1, saveTag: ((Boolean, Int) -> Unit)? = null,
    iconNow: String? = null, saveIcon: ((String) -> Unit)? = null,
) {
    var routine by remember { mutableStateOf(routineNow) }
    var color by remember { mutableIntStateOf(colorNow) }
    var icon by remember { mutableStateOf(iconNow ?: "") }
    var recurring by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(t.title) }
    var minutes by remember { mutableStateOf(t.minutes.toString()) }
    var pinnedMinute by remember { mutableIntStateOf(if (t.pinned && t.planned > 0) Clock.minuteOfDay(t.planned) else -1) }
    var day by remember { mutableStateOf(LocalDate.parse(t.day)) }
    var advanced by remember { mutableStateOf(false) }
    var priority by remember { mutableIntStateOf(t.priority) }
    var interruptible by remember { mutableStateOf(t.interruptible) }
    var category by remember { mutableStateOf(t.category) }
    var sport by remember { mutableIntStateOf(t.sport) }
    var atWork by remember { mutableStateOf(t.atWork) }
    var everyHours by remember { mutableStateOf("24") }
    var weekdays by remember { mutableIntStateOf(0) }
    var phases by remember { mutableStateOf(listOf(100)) }
    var asDefault by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val today = Clock.today()

    val m = digits(minutes).toIntOrNull() ?: 0
    val valid = Maths.valid(title, m)

    Sheet(if (isNew) "إضافة مهمة" else "تفاصيل المهمة", close) {
        if (isNew) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !recurring, onClick = { recurring = false }, label = { Text("مرة وحدة") })
            FilterChip(selected = recurring, onClick = { recurring = true }, label = { Text("متكررة") })
        }
        Field(title, "اسم المهمة", { title = it })
        Field(minutes, "المدة بالدقائق", { minutes = it }, numeric = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClockIcon(if (pinnedMinute >= 0) pinnedMinute else null, 22.dp)
            Text(if (pinnedMinute >= 0) "  ${fmt12(pinnedMinute)} (ثابت)" else "  وقت تلقائي", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (pinnedMinute >= 0) TextButton(onClick = { pinnedMinute = -1 }) { Text("تلقائي") }
            else TextButton(onClick = { picking = true }) { Text("تحديد وقت") }
        }
        if (isNew && !recurring) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = day == today, onClick = { day = today }, label = { Text("اليوم") })
            FilterChip(selected = day == today.plusDays(1), onClick = { day = today.plusDays(1) }, label = { Text("بكرا") })
        }
        CategoryPicker(category, sport) { c, sp -> category = c; sport = sp }
        if (isNew && recurring) RepeatPicker(everyHours, weekdays, { everyHours = it }, { weekdays = it }, recovery = category == "sport")
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "إخفاء الخيارات المتقدمة" else "خيارات متقدمة") }
        if (advanced) {
            if (isNew) PhasesEditor(phases) { phases = it }
            Text("الأولوية", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("منخفضة", "عادية", "عالية").forEachIndexed { i, s -> FilterChip(selected = priority == i, onClick = { priority = i }, label = { Text(s) }) }
            }
            if (category != "sport") SwitchRow("تصلح في فترات الراحة (بالتوازي)", interruptible) { interruptible = it }
            SwitchRow("مسموح وقت الدوام", atWork) { atWork = it }
        }
        if (!isNew) {
            if (t.planned > 0 && !t.pinned) Muted("رتّبها التطبيق: ${timeLabel(t.planned)}")
            Muted("الوقت المصروف: ${durLabel((Maths.spent(t, now) / 60_000).toInt())}")
            if (t.units > 0 && t.isPlan) Muted("الحصة: ${t.units} · تم ${t.doneUnits}")
        }
        if (saveTag != null) {
            SwitchRow("مهمة روتينية (لون هادي)", routine) { routine = it }
            Text("لون المهمة", style = MaterialTheme.typography.labelLarge)
            ColorPicker(color) { color = it }
        }
        if (saveIcon != null) {
            Text("رمز المهمة", style = MaterialTheme.typography.labelLarge)
            IconPicker(icon, tagColor(color)) { icon = it }
        }
        if (rule != null) Row(Modifier.fillMaxWidth().clickable { asDefault = !asDefault }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = asDefault, onCheckedChange = { asDefault = it })
            Text("اجعل هذا التعديل هو الافتراضي", style = MaterialTheme.typography.bodyLarge)
        }
        Action(if (isNew) "حفظ المهمة" else "حفظ", {
            if (saveTag != null && (routine != routineNow || color != colorNow)) saveTag(routine, color)
            if (saveIcon != null && icon != (iconNow ?: "")) saveIcon(icon)
            if (isNew && recurring) {
                saveRule(
                    Rule(
                        title = title.trim(), minutes = m, everyHours = (digits(everyHours).toIntOrNull() ?: 24).coerceIn(1, 24 * 60), weekdays = weekdays,
                        preferredMinute = pinnedMinute, pinned = pinnedMinute >= 0, priority = priority,
                        interruptible = interruptible && category != "sport", kind = if (category == "sport") "training" else "task",
                        anchorDay = today.toString(), category = category, sport = if (category == "sport") sport else 0, phases = phasesText(phases),
                        atWork = atWork,
                    )
                )
            } else save(
                t.copy(
                    title = title, minutes = m, day = day.toString(), priority = priority, interruptible = interruptible && category != "sport",
                    pinned = pinnedMinute >= 0, planned = if (pinnedMinute >= 0) Clock.epoch(day, pinnedMinute) else (if (t.pinned) 0 else t.planned),
                    category = category, sport = if (category == "sport") sport else 0, atWork = atWork,
                ), asDefault, phases
            )
        }, Modifier.fillMaxWidth(), enabled = valid)
        if (!isNew && actions != null) {
            when {
                !t.open -> Secondary("إعادة فتح المهمة", actions.reopen, Modifier.fillMaxWidth())
                t.day < today.toString() -> Secondary("نقل لليوم", actions.toToday, Modifier.fillMaxWidth())
                else -> Secondary("تأجيل لليوم التالي", actions.postpone, Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Secondary("التقرير", actions.report, Modifier.weight(1f))
                Secondary("ملاحظة", actions.note, Modifier.weight(1f))
            }
            Secondary("حذف المهمة", { confirmDelete = true }, Modifier.fillMaxWidth())
        }
    }
    if (picking) TimeDialog(if (pinnedMinute >= 0) pinnedMinute else Clock.nowMinute(), { picking = false }) { pinnedMinute = it; picking = false }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("حذف المهمة؟") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; delete() }) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
    )
}

// ---------------- Recurring task ----------------

@Composable
fun RuleEditor(r: Rule, close: () -> Unit, save: (Rule) -> Unit, delete: () -> Unit) {
    var title by remember { mutableStateOf(r.title) }
    var minutes by remember { mutableStateOf(r.minutes.toString()) }
    var everyHours by remember { mutableStateOf(r.everyHours.toString()) }
    var weekdays by remember { mutableIntStateOf(r.weekdays) }
    var preferred by remember { mutableIntStateOf(r.preferredMinute) }
    var pinned by remember { mutableStateOf(r.pinned) }
    var active by remember { mutableStateOf(r.active) }
    var interruptible by remember { mutableStateOf(r.interruptible) }
    var category by remember { mutableStateOf(r.category) }
    var sport by remember { mutableIntStateOf(r.sport) }
    var phases by remember { mutableStateOf(parsePhases(r.phases)) }
    var atWork by remember { mutableStateOf(r.atWork) }
    var picking by remember { mutableStateOf(false) }
    val members = Members.parse(r.members)
    val m = digits(minutes).toIntOrNull() ?: 0
    val isPlan = r.id.startsWith("plan-")
    Sheet("المهمة المتكررة", close) {
        if (!isPlan) Field(title, "الاسم", { title = it }) else Text(r.title, style = MaterialTheme.typography.titleMedium)
        if (!isPlan) Field(minutes, "المدة بالدقائق", { minutes = it }, numeric = true)
        if (members.isNotEmpty()) {
            Text("التمارين", style = MaterialTheme.typography.labelLarge)
            members.forEach { e -> Muted("${e.name}: ${e.sets} جولات × ${e.workSec} ث · راحة ${e.restSec / 60} د") }
        }
        SwitchRow("ضمن الجدول", active) { active = it }
        if (!isPlan) RepeatPicker(everyHours, weekdays, { everyHours = it }, { weekdays = it }, recovery = category == "sport")
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClockIcon(if (preferred >= 0) preferred else null, 22.dp)
            Text(if (preferred >= 0) "  الوقت المفضّل: ${fmt12(preferred)}" else "  وقت تلقائي", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (preferred >= 0) TextButton(onClick = { preferred = -1; pinned = false }) { Text("تلقائي") }
            else TextButton(onClick = { picking = true }) { Text("تحديد") }
        }
        if (preferred >= 0) SwitchRow("موعد ثابت لا يتحرك", pinned) { pinned = it }
        if (!isPlan) CategoryPicker(category, sport) { c, sp -> category = c; sport = sp }
        PhasesEditor(phases) { phases = it }
        if (category != "sport" && !isPlan) SwitchRow("تصلح في فترات الراحة (بالتوازي)", interruptible) { interruptible = it }
        SwitchRow("مسموح وقت الدوام", atWork) { atWork = it }
        if (r.source.isNotEmpty()) Muted(r.source)
        if (category == "sport") Muted("مرور الوقت لا يضمن الاستشفاء؛ عدّل حسب إحساسك.")
        Action("حفظ", {
            save(
                r.copy(
                    title = if (isPlan) r.title else title.trim(), minutes = if (isPlan) r.minutes else m,
                    everyHours = (digits(everyHours).toIntOrNull() ?: r.everyHours).coerceIn(1, 24 * 60),
                    weekdays = weekdays, preferredMinute = preferred, pinned = pinned && preferred >= 0, active = active,
                    interruptible = if (isPlan) r.interruptible else interruptible && category != "sport",
                    category = category, sport = if (category == "sport") sport else 0, phases = phasesText(phases), atWork = atWork,
                    anchorDay = if (active && !r.active) Clock.today().toString() else r.anchorDay,
                )
            )
        }, Modifier.fillMaxWidth(), enabled = isPlan || Maths.valid(title, m))
        if (!isPlan) Secondary("حذف", delete, Modifier.fillMaxWidth())
    }
    if (picking) TimeDialog(if (preferred >= 0) preferred else 17 * 60, { picking = false }) { preferred = it; picking = false }
}

// ---------------- Goal ----------------

@Composable
fun GoalEditor(
    g: Goal, rules: List<Rule>, logs: List<GoalLog>, today: LocalDate, close: () -> Unit, save: (Goal) -> Unit,
    toggleRule: (Rule, Boolean) -> Unit, editRule: (Rule) -> Unit, delete: () -> Unit,
) {
    var current by remember { mutableStateOf(num(g.current)) }
    var reference by remember { mutableStateOf(num(g.reference)) }
    var targetDay by remember { mutableStateOf(g.targetDay) }
    Sheet("تحديث الأداء", close) {
        Text(g.name, style = MaterialTheme.typography.titleLarge)
        Field(current, "الأداء الحالي (${g.unit})", { current = it }, numeric = true)
        Field(reference, "هدف السنة (${g.unit})", { reference = it }, numeric = true)
        Field(targetDay, "تاريخ الهدف (yyyy-mm-dd)", { targetDay = it })
        Muted(if (g.lower) "الزمن الأقل أفضل. النسبة = الهدف ÷ الحالي." else "النسبة = الحالي ÷ الهدف. يمكن تجاوز ١٠٠٪.")
        val c = digits(current).toDoubleOrNull()
        val r = digits(reference).toDoubleOrNull()
        val td = runCatching { LocalDate.parse(targetDay) }.getOrNull()
        val ok = c != null && r != null && c > 0 && r > 0 && td != null && td.isAfter(today)
        if (ok) Text("المطلوب أسبوعياً: ${num(Scheduler.weeklyRateNeeded(c!!, r!!, g.lower, today, td!!))} ${g.unit}", style = MaterialTheme.typography.bodyLarge)
        Action("حفظ الأداء", { if (ok) save(g.copy(current = c!!, reference = r!!, targetDay = td.toString())) }, Modifier.fillMaxWidth(), enabled = ok)
        if (rules.isNotEmpty()) {
            HorizontalDivider()
            Text("التدريب في الجدول", style = MaterialTheme.typography.titleMedium)
            rules.forEach { rule ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SwitchRow(rule.title, rule.active) { toggleRule(rule, it) }
                    Muted(ruleSummary(rule))
                    TextButton(onClick = { editRule(rule) }) { Text("تعديل") }
                }
            }
        }
        if (logs.isNotEmpty()) {
            HorizontalDivider()
            Text("سجل الأداء", style = MaterialTheme.typography.titleMedium)
            logs.takeLast(6).reversed().forEach { Text("${it.day}: ${num(it.value)} ${g.unit}", style = MaterialTheme.typography.bodyLarge) }
        }
        if (!g.id.startsWith("sport-")) Secondary("حذف الهدف", delete, Modifier.fillMaxWidth())
    }
}

// ---------------- Plans (Quran, books…) ----------------

@Composable
fun PlanEditor(p: Plan, s: Snapshot, close: () -> Unit, save: (Plan, String) -> Unit, delete: () -> Unit, pickFolder: () -> Unit) {
    val rule = s.rules.firstOrNull { it.id == "plan-${p.id}" }
    var title by remember { mutableStateOf(p.title) }
    var unit by remember { mutableStateOf(p.unit) }
    var total by remember { mutableStateOf(p.total.toString()) }
    var done by remember { mutableStateOf(p.done.toString()) }
    var pointer by remember { mutableStateOf(p.pointer.toString()) }
    var perDay by remember { mutableStateOf(if (p.perDay > 0) p.perDay.toString() else "") }
    var cycle by remember { mutableStateOf(p.cycleDays.toString()) }
    var perUnit by remember { mutableStateOf(p.minutesPerUnit.toString()) }
    var phases by remember { mutableStateOf(parsePhases(rule?.phases ?: "")) }
    var khatma by remember { mutableStateOf(if (p.khatmaDays > 0) p.khatmaDays.toString() else "") }
    var stageItems by remember { mutableStateOf(p.stageItems) }
    var types by remember { mutableStateOf(Content.parse(p.content)) }
    val isChannel = p.content.isNotBlank()
    var perWeek by remember { mutableStateOf(num(p.perWeek)) }
    var group by remember { mutableStateOf(Cat.ofPlan(p)) }
    val today = s.today

    fun n(x: String) = digits(x).toIntOrNull()
    val candidate = p.copy(
        title = title.trim(), unit = unit.trim().ifEmpty { "صفحة" },
        total = (n(total) ?: p.total).coerceIn(1, 100_000),
        done = (n(done) ?: 0).coerceIn(0, n(total) ?: p.total),
        perDay = (n(perDay) ?: 0).coerceIn(0, 1000),
        cycleDays = (n(cycle) ?: 7).coerceIn(1, 365),
        minutesPerUnit = (n(perUnit) ?: p.minutesPerUnit).coerceIn(1, 240),
        configured = true,
        khatmaDays = (n(khatma) ?: 0).coerceIn(0, 3650),
        stages = when {
            isChannel -> Stages.encode(Content.monthSteps(types))
            p.kind == "cycle" -> Stages.encode(stageItems.filter { it.name.isNotBlank() })
            else -> p.stages
        },
        content = if (isChannel) Content.encode(types) else p.content,
        contentMonth = if (isChannel) java.time.YearMonth.from(s.today).toString() else p.contentMonth,
        perWeek = digits(perWeek).toDoubleOrNull()?.coerceIn(0.1, 14.0) ?: p.perWeek,
        category = group,
        pointer = if (p.kind == "cycle") p.pointer else (n(pointer) ?: 1).coerceIn(1, MUSHAF_PAGES),
    )
    val mem = s.plans.firstOrNull { it.kind == "memorize" && it.configured }?.done ?: 0
    Sheet(p.title.ifEmpty { "خطة جديدة" }, close) {
        when (p.kind) {
            "memorize" -> {
                Field(done, "آخر صفحة حفظتها", { done = it }, numeric = true)
                Field(perDay, "كم صفحة تحفظ باليوم", { perDay = it }, numeric = true)
                Field(perUnit, "دقائق الحفظ لكل صفحة", { perUnit = it }, numeric = true)
                Muted("الصفحة التالية بتطلع بس لما تأكد إنك حفظت اللي قبلها.")
            }
            "read" -> {
                Field(pointer, "الصفحة اللي بتبدأ منها", { pointer = it }, numeric = true)
                Field(khatma, "ختمة كل كم يوم", { khatma = it }, numeric = true)
                if ((n(khatma) ?: 0) == 0) Field(perDay, "أو: عدد الصفحات يومياً", { perDay = it }, numeric = true)
                Field(perUnit, "دقائق لكل صفحة", { perUnit = it }, numeric = true)
                Muted("الورد بيمشي بالترتيب على صفحات المصحف الـ604. لما يوصل 604 بتبدأ ختمة جديدة من الصفحة 1." + if (p.done > 0) " ختمت ${p.done} مرة." else "")
            }
            "cycle" -> {
                Field(title, "الاسم", { title = it })
                if (isChannel) ContentEditor(types) { types = it }
                else {
                    StagesEditor(stageItems, n(perUnit) ?: 60) { stageItems = it }
                    ChatGptImport(title, 7) { stageItems = it }
                    Field(perWeek, "كم دورة كاملة بالأسبوع", { perWeek = it }, numeric = true)
                    Field(perUnit, "دقائق المرحلة الافتراضية", { perUnit = it }, numeric = true)
                    Field(total, "هدف السنة (عدد الحلقات/الإصدارات، 1 = مرة وحدة)", { total = it }, numeric = true)
                    Field(done, "المنجز حتى الآن", { done = it }, numeric = true)
                    Muted("كل يوم بتطلع المرحلة الجاية: يوم تحضير، يوم تصوير، يوم نشر… ولما تكمل الدورة بتنحسب وحدة.")
                }
            }
            "review" -> {
                Text(if (mem > 0) "المحفوظ حالياً: صفحات 1–$mem" else "حدّد خطة الحفظ أولاً حتى تُعرف صفحات المراجعة.", style = MaterialTheme.typography.bodyLarge)
                Field(perDay, "كم صفحة تسمع باليوم", { perDay = it }, numeric = true)
                if ((n(perDay) ?: 0) == 0) Field(cycle, "أو: أسمع كل المحفوظ مرة كل كم يوم", { cycle = it }, numeric = true)
                Field(pointer, "الصفحة التالية للسماع", { pointer = it }, numeric = true)
                Field(perUnit, "دقائق السماع لكل صفحة", { perUnit = it }, numeric = true)
                Secondary(if (s.audioTree != null) "تغيير مجلد الصفحات الصوتية" else "اختيار مجلد الصفحات الصوتية", pickFolder, Modifier.fillMaxWidth())
                Muted("اسم كل ملف فيه رقم الصفحة، مثل 001.mp3. وين ما توقف بيكمّل من بعدها.")
            }
            else -> {
                Field(title, "اسم الخطة", { title = it })
                Field(unit, "الوحدة (صفحة، درس…)", { unit = it })
                Field(total, "المجموع", { total = it }, numeric = true)
                Field(done, "المنجز حتى الآن", { done = it }, numeric = true)
                Field(perDay, "يومياً", { perDay = it }, numeric = true)
                Field(perUnit, "دقائق لكل وحدة", { perUnit = it }, numeric = true)
            }
        }
        Text("المجموعة", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Cat.keys.filter { it != "sport" }.forEach { k -> FilterChip(selected = group == k, onClick = { group = k }, label = { Text(Cat.name(k)) }) }
        }
        PhasesEditor(phases) { phases = it }
        HorizontalDivider()
        val q = PlanMath.quota(candidate, today, if (p.kind == "review") mem else candidate.done)
        val alloc = Scheduler.allocate(q, phases.map { Share(it, true, 0) })
        Text("اليوم: $q ${candidate.unit}" + if (phases.size > 1) " (" + alloc.joinToString(" + ") + ")" else "", style = MaterialTheme.typography.bodyLarge)
        if (p.kind == "memorize" || p.kind == "amount") {
            val sg = PlanMath.suggestion(candidate, today)
            Text("هذا الأسبوع: ${sg.week} · هذا الشهر: ${sg.month}", style = MaterialTheme.typography.bodyLarge)
            sg.projectedEnd?.let { Text("الانتهاء المتوقع: $it", style = MaterialTheme.typography.bodyLarge) }
        }
        if (p.kind == "cycle") Text("اليوم: ${PlanMath.phaseTitle(candidate, 0, q, 0)}", style = MaterialTheme.typography.bodyLarge)
        val valid = (p.kind != "amount" && p.kind != "cycle") || (candidate.title.isNotEmpty() && (p.kind != "cycle" || candidate.stageList.isNotEmpty()))
        Action(if (p.accepted) "حفظ" else "قبول الخطة", { save(candidate.copy(accepted = true), phasesText(phases)) }, Modifier.fillMaxWidth(), enabled = valid)
        if (p.accepted) Secondary("إيقاف الخطة", { save(candidate.copy(accepted = false), phasesText(phases)) }, Modifier.fillMaxWidth())
        if ((p.kind == "amount" || p.kind == "cycle") && p.title.isNotEmpty()) Secondary("حذف الخطة", delete, Modifier.fillMaxWidth())
    }
}

// ---------------- New goal (from anywhere) ----------------

@Composable
fun NewGoalSheet(today: LocalDate, close: () -> Unit, saveGoal: (Goal, Rule?) -> Unit, savePlan: (Plan) -> Unit, saveRule: (Rule) -> Unit) {
    var type by remember { mutableIntStateOf(0) } // 0 number, 1 amount, 2 habit
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("sport") }
    var sport by remember { mutableIntStateOf(2) }
    var current by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var lower by remember { mutableStateOf(false) }
    var months by remember { mutableStateOf("12") }
    var withSession by remember { mutableStateOf(true) }
    var everyHours by remember { mutableStateOf("72") }
    var weekdays by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableStateOf("45") }
    var total by remember { mutableStateOf("") }
    var perDay by remember { mutableStateOf("") }
    var stageItems by remember { mutableStateOf(listOf(Stage("", 60))) }
    var perWeekText by remember { mutableStateOf("1") }
    var once by remember { mutableStateOf(false) }
    var daysText by remember { mutableStateOf("7") }
    var types by remember { mutableStateOf(listOf(ContentType("هيلب", 4, 1, 60), ContentType("هب", 4, 2, 60), ContentType("هيرو", 2, 7, 90))) }
    Sheet("هدف جديد", close) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("رقم أوصله", "كمية أخلّصها", "مشروع بمراحل", "عادة متكررة", "قناة محتوى").forEachIndexed { i, s ->
                FilterChip(selected = type == i, onClick = { type = i }, label = { Text(s) })
            }
        }
        Field(name, "اسم الهدف", { name = it })
        if (type == 0 || type == 3 || type == 2) CategoryPicker(category, sport) { c, sp -> category = c; sport = sp }
        val months12 = (digits(months).toIntOrNull() ?: 12).coerceIn(1, 60)
        when (type) {
            0 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(current, "الحالي", { current = it }, numeric = true, modifier = Modifier.weight(1f))
                    Field(target, "الهدف", { target = it }, numeric = true, modifier = Modifier.weight(1f))
                    Field(unit, "الوحدة", { unit = it }, modifier = Modifier.weight(1f))
                }
                SwitchRow("الأقل أفضل (زمن)", lower) { lower = it }
                Field(months, "خلال كم شهر", { months = it }, numeric = true)
                SwitchRow("أضف جلسة متكررة بالجدول", withSession) { withSession = it }
                if (withSession) {
                    Field(minutes, "مدة الجلسة بالدقائق", { minutes = it }, numeric = true)
                    RepeatPicker(everyHours, weekdays, { everyHours = it }, { weekdays = it }, recovery = category == "sport")
                }
                val c = digits(current).toDoubleOrNull(); val t = digits(target).toDoubleOrNull()
                val ok = name.isNotBlank() && c != null && t != null && c > 0 && t > 0
                Action("حفظ الهدف", {
                    val id = "goal-" + UUID.randomUUID().toString().take(8)
                    val g = Goal(id, name.trim(), category, c!!, t!!, unit.trim(), lower, baseline = c, startDay = today.toString(), targetDay = today.plusMonths(months12.toLong()).toString())
                    val rule = if (withSession) Rule(
                        id = "rule-$id", title = name.trim(), minutes = (digits(minutes).toIntOrNull() ?: 45).coerceIn(5, 600),
                        everyHours = (digits(everyHours).toIntOrNull() ?: 72).coerceIn(1, 24 * 60), weekdays = weekdays, goalId = id,
                        anchorDay = today.toString(), category = category, sport = if (category == "sport") sport else 0,
                        kind = if (category == "sport") "training" else "task",
                    ) else null
                    saveGoal(g, rule)
                }, Modifier.fillMaxWidth(), enabled = ok)
            }
            1 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(total, "المجموع", { total = it }, numeric = true, modifier = Modifier.weight(1f))
                    Field(unit, "الوحدة (صفحة…)", { unit = it }, modifier = Modifier.weight(1f))
                }
                Field(perDay, "كم يومياً", { perDay = it }, numeric = true)
                val tt = digits(total).toIntOrNull()
                Action("حفظ الهدف", {
                    savePlan(Plan(id = UUID.randomUUID().toString(), title = name.trim(), kind = "amount", unit = unit.trim().ifEmpty { "صفحة" },
                        total = tt!!, perDay = (digits(perDay).toIntOrNull() ?: 0), minutesPerUnit = 2,
                        endDay = today.plusMonths(months12.toLong()).toString(), accepted = true, configured = true, category = "reading"))
                }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && tt != null && tt > 0)
            }
            2 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !once, onClick = { once = false }, label = { Text("بيتكرر") })
                    FilterChip(selected = once, onClick = { once = true }, label = { Text("مرة وحدة") })
                }
                if (once) Field(daysText, "خلال كم يوم", { daysText = it }, numeric = true)
                StagesEditor(stageItems, 60) { stageItems = it }
                ChatGptImport(name, if (once) digits(daysText).toIntOrNull() ?: 7 else 0) { stageItems = it }
                if (!once) {
                    Field(perWeekText, "كم دورة كاملة بالأسبوع", { perWeekText = it }, numeric = true)
                    Field(total, "هدف السنة (عدد الحلقات)", { total = it }, numeric = true)
                }
                val st = stageItems.filter { it.name.isNotBlank() }
                val days = (digits(daysText).toIntOrNull() ?: 7).coerceIn(1, 365)
                Action("حفظ المشروع", {
                    savePlan(Plan(id = UUID.randomUUID().toString(), title = name.trim(), kind = "cycle", unit = if (once) "مرة" else "حلقة",
                        total = if (once) 1 else digits(total).toIntOrNull() ?: 52, pointer = 0, stages = Stages.encode(st),
                        perWeek = if (once) 7.0 / days else digits(perWeekText).toDoubleOrNull()?.coerceIn(0.1, 14.0) ?: 1.0,
                        minutesPerUnit = 60, category = if (category == "sport") "work" else category,
                        endDay = if (once) today.plusDays(days.toLong()).toString() else today.plusMonths(months12.toLong()).toString(), accepted = true, configured = true))
                }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && st.isNotEmpty())
            }
            4 -> {
                ContentEditor(types) { types = it }
                Action("حفظ القناة", {
                    savePlan(Plan(id = UUID.randomUUID().toString(), title = name.trim(), kind = "cycle", unit = "شهر", total = 0, pointer = 0,
                        stages = Stages.encode(Content.monthSteps(types)), content = Content.encode(types),
                        contentMonth = java.time.YearMonth.from(today).toString(), perWeek = 7.0 / today.lengthOfMonth(),
                        minutesPerUnit = 60, category = "work", accepted = true, configured = true))
                }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && types.any { it.perMonth > 0 && it.name.isNotBlank() })
            }
            else -> {
                Field(minutes, "المدة بالدقائق", { minutes = it }, numeric = true)
                RepeatPicker(everyHours, weekdays, { everyHours = it }, { weekdays = it }, recovery = category == "sport")
                Action("حفظ", {
                    saveRule(Rule(title = name.trim(), minutes = (digits(minutes).toIntOrNull() ?: 30).coerceIn(5, 600),
                        everyHours = (digits(everyHours).toIntOrNull() ?: 24).coerceIn(1, 24 * 60), weekdays = weekdays,
                        anchorDay = today.toString(), category = category, sport = if (category == "sport") sport else 0,
                        kind = if (category == "sport") "training" else "task"))
                }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
            }
        }
    }
}

// ---------------- Settings ----------------

@Composable
fun SettingsDialog(s: Settings, close: () -> Unit, set: (String, String) -> Unit, openGroups: () -> Unit, test: () -> Unit) {
    val context = LocalContext.current
    var soundName by remember { mutableStateOf(soundTitle(context)) }
    val soundPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { res ->
        @Suppress("DEPRECATION")
        val uri = res.data?.getParcelableExtra<Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (res.resultCode == android.app.Activity.RESULT_OK) { Alarms.setSound(context, uri); soundName = soundTitle(context) }
    }
    var pick by remember { mutableStateOf<String?>(null) }
    var gap by remember { mutableStateOf(s.gap.toString()) }
    var extend by remember { mutableStateOf(s.extend.toString()) }
    var morning by remember { mutableStateOf((s.morning / 60.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }) }
    var maxSports by remember { mutableStateOf(s.maxSports.toString()) }
    var sportGap by remember { mutableStateOf((s.sportGap / 60).toString()) }
    @Composable fun timeRow(label: String, key: String, value: Int) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClockIcon(value, 22.dp)
            Text("  $label: ${fmt12(value)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { pick = key }) { Text("تغيير") }
        }
    }
    Sheet("الإعدادات", close) {
        Text("شكل اليوم", style = MaterialTheme.typography.titleMedium)
        timeRow("الصحوة", "dayStart", s.dayStart)
        timeRow("النوم", "dayEnd", s.dayEnd)
        Field(morning, "ساعات الصباح للقرآن والقراءة", { morning = it; digits(it).toDoubleOrNull()?.let { v -> if (v in 0.5..8.0) set("morning", (v * 60).toInt().toString()) } }, numeric = true)
        timeRow("نهاية وقت الشغل والمشاريع", "workEnd", s.workEnd)
        timeRow("التمارين الثقيلة بتكون خالصة قبل", "sportEnd", s.sportEnd)
        Secondary("المجموعات وأوقاتها", openGroups, Modifier.fillMaxWidth())
        HorizontalDivider()
        Text("الدوام", style = MaterialTheme.typography.titleMedium)
        SwitchRow("عندي دوام بالنهار", s.jobOn) { set("jobOn", if (it) "1" else "0") }
        if (s.jobOn) {
            timeRow("بداية الدوام", "jobStart", s.jobStart)
            timeRow("نهاية الدوام", "jobEnd", s.jobEnd)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                weekdayNames.forEachIndexed { i, name ->
                    FilterChip(selected = s.jobDays and (1 shl i) != 0, onClick = { set("jobDays", (s.jobDays xor (1 shl i)).toString()) }, label = { Text(name) })
                }
            }
            Muted("ما بينحط شي وقت الدوام إلا المهام أو المجموعات اللي عليها «مسموح وقت الدوام».")
        }
        HorizontalDivider()
        Text("آخر اليوم", style = MaterialTheme.typography.titleMedium)
        Muted("وقت النوم بيطلعلك إشعار بزرّين. إذا ما اخترت، هاد الافتراضي:")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = s.closeDefault != "close", onClick = { set("closeDefault", "carry") }, label = { Text("ترحيل لبكرا") })
            FilterChip(selected = s.closeDefault == "close", onClick = { set("closeDefault", "close") }, label = { Text("إغلاق بدون ترحيل") })
        }
        HorizontalDivider()
        Text("السحب لليسار على مهمة", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = s.swipeAction == "carry", onClick = { set("swipeAction", "carry") }, label = { Text("ترحيل لبكرا") })
            FilterChip(selected = s.swipeAction == "close", onClick = { set("swipeAction", "close") }, label = { Text("إغلاق") })
            FilterChip(selected = s.swipeAction == "later", onClick = { set("swipeAction", "later") }, label = { Text("لوقت فاضي اليوم") })
        }
        Muted(when (s.swipeAction) {
            "close" -> "بتنسكّر بدون إنجاز، وهدفها بيرجع بموعدها الجاي بالجدول."
            "later" -> "بتنتقل لأول وقت فاضي بعدين بنفس اليوم. بشاشة بكرا بتترحّل لليوم اللي بعده."
            else -> "بتنتقل لليوم اللي بعده، والمهام الشبيهة بعدها بتتأخر."
        })
        Muted("السحب لليمين: ابدأ فيها الآن.")
        HorizontalDivider()
        Text("عرض المهام", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = s.nameStyle == "name", onClick = { set("nameStyle", "name") }, label = { Text("الاسم") })
            FilterChip(selected = s.nameStyle == "both", onClick = { set("nameStyle", "both") }, label = { Text("الرمز والاسم") })
            FilterChip(selected = s.nameStyle == "icon", onClick = { set("nameStyle", "icon") }, label = { Text("الرمز بدل الاسم") })
        }
        HorizontalDivider()
        Text("مؤقت المهمة", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = s.timerStyle != "analog", onClick = { set("timerStyle", "digital") }, label = { Text("رقمي") })
            FilterChip(selected = s.timerStyle == "analog", onClick = { set("timerStyle", "analog") }, label = { Text("عقارب") })
        }
        Field(maxSports, "أقصى عدد تمارين باليوم", { maxSports = it; digits(it).toIntOrNull()?.let { v -> if (v in 1..6) set("maxSports", v.toString()) } }, numeric = true)
        Field(sportGap, "ساعات بين تمرين وتمرين", { sportGap = it; digits(it).toIntOrNull()?.let { v -> if (v in 0..8) set("sportGap", (v * 60).toString()) } }, numeric = true)
        Field(gap, "فاصل بين المهام (دقائق)", { gap = it; digits(it).toIntOrNull()?.let { v -> if (v in 0..120) set("gap", v.toString()) } }, numeric = true)
        Field(extend, "مدة التمديد (دقائق)", { extend = it; digits(it).toIntOrNull()?.let { v -> if (v in 1..240) set("extend", v.toString()) } }, numeric = true)
        HorizontalDivider()
        SwitchRow("المنبه عند انتهاء وبدء المهام", s.alarms) { set("alarms", if (it) "1" else "0") }
        SwitchRow("يرن حتى أوقفه", s.insistent) { set("insistent", if (it) "1" else "0") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("صوت تنبيه المهام: $soundName", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = {
                soundPicker.launch(Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_NOTIFICATION or android.media.RingtoneManager.TYPE_ALARM)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "صوت تنبيه المهام")
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Alarms.soundUri(context)))
            }) { Text("اختيار") }
        }
        Muted("منفصل عن منبه التصحاية؛ الافتراضي صوت إشعار قصير.")
        Secondary("تجربة المنبه", test, Modifier.fillMaxWidth())
        if (!Alarms.canExact(context)) {
            Text("المنبه الدقيق غير مسموح، قد يتأخر الرنين.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            Secondary("السماح بالمنبه الدقيق", {
                if (Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
            }, Modifier.fillMaxWidth())
        }
        Secondary("إعدادات الإشعارات", {
            context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
        }, Modifier.fillMaxWidth())
        Secondary("استثناء من توفير البطارية", {
            context.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }, Modifier.fillMaxWidth())
        Muted("الإصدار 0.7.1")
    }
    pick?.let { key ->
        val initial = when (key) { "dayStart" -> s.dayStart; "dayEnd" -> s.dayEnd; "workEnd" -> s.workEnd; "jobStart" -> s.jobStart; "jobEnd" -> s.jobEnd; else -> s.sportEnd }
        TimeDialog(initial, { pick = null }) { v ->
            val ok = when (key) {
                "dayStart" -> v < s.dayEnd
                "dayEnd" -> v > s.dayStart
                else -> v in s.dayStart..s.dayEnd
            }
            if (ok) set(key, v.toString()) else Toast.makeText(context, "الوقت لازم يكون بين الصحوة والنوم", Toast.LENGTH_SHORT).show()
            pick = null
        }
    }
}

@Composable
fun CloseDayDialog(todays: List<Task>, now: Long, close: () -> Unit, finish: (String) -> Unit) {
    val open = todays.filter { it.open }
    Sheet("إغلاق اليوم", close) {
        Text("المخطط: ${todays.size} مهام · ${durLabel(todays.sumOf { it.minutes })}", style = MaterialTheme.typography.bodyLarge)
        Text("المنجز: ${todays.count { it.done }}", style = MaterialTheme.typography.bodyLarge)
        Text("المتبقي: ${open.size}", style = MaterialTheme.typography.bodyLarge)
        Text("الوقت الفعلي: ${durLabel((todays.sumOf { Maths.spent(it, now) } / 60_000).toInt())}", style = MaterialTheme.typography.bodyLarge)
        if (open.isNotEmpty()) {
            HorizontalDivider()
            open.forEach { Text("• ${it.title}", style = MaterialTheme.typography.bodyLarge) }
            Action("ترحيل لبكرا", { finish("carry") }, Modifier.fillMaxWidth())
            Muted("بتنتقل لبكرا إجبارياً وبتاخد مكان الشبيهة إلها، وهديك بتتأخر لبعده.")
            Secondary("إغلاق بدون ترحيل", { finish("close") }, Modifier.fillMaxWidth())
            Muted("الجدول بيضل زي ما هو، وبتنسجّل بالتقرير إنها ما انعملت.")
        } else Action("إغلاق اليوم", { finish("close") }, Modifier.fillMaxWidth())
    }
}

fun soundTitle(context: android.content.Context): String =
    Alarms.soundUri(context)?.let { runCatching { android.media.RingtoneManager.getRingtone(context, it)?.getTitle(context) }.getOrNull() } ?: "صامت"
