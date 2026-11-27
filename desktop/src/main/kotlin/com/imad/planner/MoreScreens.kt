package com.imad.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.time.LocalDate

// Step 3 of the computer app: review, settings, adding and editing tasks, and Windows notifications.

private val Muted2 = Color(0xFF5F6787)

// ---------------------------------------------------------------- Review

@Composable
fun ReviewScreen(d: Desk, act: (suspend () -> Unit) -> Unit) {
    val repo = Repository.instance!!
    val ts = d.today.toString()
    val todayList = d.tasks.filter { it.day == ts && it.state != "skipped" }
    val week = (0 until 7).map { d.today.minusDays(it.toLong()).toString() }.toSet()
    val weekTasks = d.tasks.filter { it.day in week }
    val sum = remember(d.tasks, d.goals, d.logs) { Progress.summary(d.tasks, d.goals, d.logs, d.settings, d.today, d.now) }
    // Real minutes this week per domain, from the timers and finished tasks (no estimates).
    val byDomain = weekTasks.groupBy { domainOf(it, d.settings) }.mapValues { (_, l) -> (l.sumOf { Maths.spent(it, d.now) } / 60_000).toInt() }
        .filterValues { it > 0 }.toList().sortedByDescending { it.second }
    val most = byDomain.maxOfOrNull { it.second } ?: 1
    Column(Modifier.fillMaxSize()) {
        PageHeader("المراجعة", "يومك وأسبوعك من سجلّك الحقيقي")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box2(Modifier.weight(1f)) {
                    Title("اليوم")
                    Line("المخطط", durLabel(todayList.sumOf { it.minutes }))
                    Line("المنجز", "${ar(todayList.count { it.done })} من ${ar(todayList.size)}")
                    Line("الوقت الفعلي", durLabel((todayList.sumOf { Maths.spent(it, d.now) } / 60_000).toInt()))
                }
                Box2(Modifier.weight(1f)) {
                    Title("آخر ٧ أيام")
                    Line("المهام المنجزة", ar(weekTasks.count { it.done }))
                    Line("الوقت الفعلي", durLabel((weekTasks.sumOf { Maths.spent(it, d.now) } / 60_000).toInt()))
                    Line("مهام متأخرة من أيام فاتت", ar(d.tasks.count { it.open && it.day < ts }))
                    Line("أسابيع ثابتة", ar(sum.committedWeeks))
                }
                Box2(Modifier.weight(1f)) {
                    Title("وقت كل مجال بهالأسبوع")
                    if (byDomain.isEmpty()) Text("لسا ما في وقت مسجّل هالأسبوع.", color = Muted2)
                    byDomain.forEach { (dom, min) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DomainTile(dom, 32.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row { Text(dom.title, fontWeight = FontWeight.Bold, fontSize = 14.sp); Spacer(Modifier.weight(1f)); Text(durLabel(min), fontSize = 14.sp, color = Muted2) }
                                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEEF1F8))) {
                                    Box(Modifier.fillMaxWidth(min.toFloat() / most).height(8.dp).clip(RoundedCornerShape(4.dp)).background(dom.color))
                                }
                            }
                        }
                    }
                }
            }
            Box2(Modifier.fillMaxWidth()) {
                Title("البيانات")
                Text("بياناتك محفوظة على هالكمبيوتر، وبتتزامن مع الجوال إذا ربطتهم.", color = Muted2)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Action("تصدير نسخة احتياطية", { act { val text = repo.backup(); saveBackupFile(text) } }, Modifier.weight(1f))
                    Secondary("استعادة من ملف", { chooseBackupFile()?.let { text -> act { repo.restore(text) } } }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Box2(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable private fun Title(t: String) = Text(t, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 21.sp)

@Composable
private fun Line(label: String, value: String) = Row {
    Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
    Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
}

/** Windows' save dialog for a backup file (the same format the phone reads). */
fun saveBackupFile(text: String) {
    val d = java.awt.FileDialog(null as java.awt.Frame?, "حفظ نسخة احتياطية", java.awt.FileDialog.SAVE)
    d.file = "planner-backup-${LocalDate.now()}.json"
    d.isVisible = true
    val name = d.file ?: return
    File(d.directory, if (name.endsWith(".json")) name else "$name.json").writeText(text)
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsPanel(d: Desk, act: (suspend () -> Unit) -> Unit, close: () -> Unit) {
    val repo = Repository.instance!!
    val s = d.settings
    Sheet("الإعدادات", close) {
        Text("اليوم", style = MaterialTheme.typography.labelLarge)
        TimeRow("الصحوة", s.dayStart) { v -> if (v < s.dayEnd) act { repo.setSetting("dayStart", v.toString()) } }
        TimeRow("النوم", s.dayEnd) { v -> if (v > s.dayStart) act { repo.setSetting("dayEnd", v.toString()) } }
        Text("الشكل", style = MaterialTheme.typography.labelLarge)
        SwitchRow("حركة أقل", d.meta["deskCalm"] == "1") { on -> act { repo.setSetting("deskCalm", if (on) "1" else "0") } }
        Muted("بتوقف الاحتفالات والحركات على الكمبيوتر.")
        SwitchRow("النقاط والمستويات", d.meta["xp"] != "0") { on -> act { repo.setSetting("xp", if (on) "1" else "0") } }
        Muted("النقاط بتنحسب من سجلّك الحقيقي. إطفاؤها بيخفيها وما بيمسح إشي.")
        Text("التنبيهات", style = MaterialTheme.typography.labelLarge)
        Muted(if (java.awt.SystemTray.isSupported()) "بيطلعلك تنبيه من ويندوز لما يجي وقت مهمة، والبرنامج مفتوح." else "ويندوز ما بيسمح بالتنبيهات هون.")
        Muted("الإصدار ${DesktopVersion.name}")
    }
}

/** A time of day with − and + buttons (15 minutes a step). */
@Composable
private fun TimeRow(label: String, minute: Int, set: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { set((minute - 15).coerceAtLeast(0)) }) { Text("−", fontSize = 20.sp) }
        Text(fmt12(minute), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(90.dp))
        TextButton(onClick = { set((minute + 15).coerceAtMost(24 * 60 - 1)) }) { Text("+", fontSize = 20.sp) }
    }
}

object DesktopVersion { const val name = "1.1.0" }

// ---------------------------------------------------------------- Add / edit a task

/** New task ([t] null) or editing one: name, length, day, prayer period and group. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditor(t: Task?, d: Desk, act: (suspend () -> Unit) -> Unit, close: () -> Unit) {
    val repo = Repository.instance!!
    val s = d.settings
    var title by remember { mutableStateOf(t?.title ?: "") }
    var minutes by remember { mutableStateOf((t?.minutes ?: 30).toString()) }
    var dayOffset by remember { mutableStateOf(t?.let { java.time.temporal.ChronoUnit.DAYS.between(d.today, LocalDate.parse(it.day)).toInt() } ?: 0) }
    var period by remember { mutableStateOf(t?.period ?: s.periodAt(d.today, Clock.minuteOfDay(d.now))) }
    var group by remember { mutableStateOf(t?.category ?: "other") }
    val day = d.today.plusDays(dayOffset.toLong())
    val mins = minutes.toIntOrNull() ?: 0
    val ok = Maths.valid(title.trim(), mins)
    Sheet(if (t == null) "مهمة جديدة" else "تعديل المهمة", close) {
        OutlinedTextField(title, { title = it }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, label = { Text("المدة بالدقائق") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("اليوم", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "اليوم", 1 to "بكرا", 2 to "بعد بكرا").forEach { (o, l) -> FilterChip(selected = dayOffset == o, onClick = { dayOffset = o }, label = { Text(l) }) }
        }
        if (dayOffset !in 0..2) Muted(dayLabel(day))
        Text("الوقت", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            s.periodsOn(day).forEach { p -> FilterChip(selected = period == p.key, onClick = { period = p.key }, label = { Text(p.name) }) }
        }
        Text("المجموعة", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            s.groups.forEach { g -> FilterChip(selected = group == g.key, onClick = { group = g.key }, label = { Text(g.name) }) }
        }
        Action(if (t == null) "أضف" else "احفظ", {
            val base = (t ?: Task(title = title.trim(), minutes = mins, day = day.toString()))
            val task = base.copy(title = title.trim(), minutes = mins, day = day.toString(), period = period, category = group)
            act {
                if (t == null) repo.saveNew(task, emptyList()) else repo.save(task)
                if (task.period.isNotEmpty()) repo.assignPeriod(task.id, task.period)
            }
            close()
        }, Modifier.fillMaxWidth(), enabled = ok)
        if (t != null) TextButton(onClick = { act { repo.delete(t.id) }; close() }) { Text("حذف المهمة", color = MaterialTheme.colorScheme.error) }
    }
}

// ---------------------------------------------------------------- Windows notifications

/**
 * Shows a Windows notification when a task's planned time comes, while the app is open (the phone's alarms in
 * spirit; the computer has no alarm clock). Fed by [DesktopPlatform.rescheduleAlarms].
 */
object Notifier {
    @Volatile private var tasks: List<Task> = emptyList()
    @Volatile private var settings: Settings? = null
    private val shown = HashSet<String>()
    private val tray: java.awt.TrayIcon? by lazy {
        if (!java.awt.SystemTray.isSupported()) return@lazy null
        val img = java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB).apply {
            createGraphics().apply {
                setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
                color = java.awt.Color(0x2F, 0x56, 0xD9); fillRoundRect(0, 0, 32, 32, 10, 10)
                color = java.awt.Color(0xFF, 0x8A, 0x3D); fillRoundRect(9, 7, 14, 19, 7, 7)
                dispose()
            }
        }
        runCatching { java.awt.TrayIcon(img, "رحلة اليوم").also { it.isImageAutoSize = true; java.awt.SystemTray.getSystemTray().add(it) } }.getOrNull()
    }

    fun update(list: List<Task>, s: Settings) { tasks = list; settings = s }

    fun start() {
        Thread({
            while (true) {
                runCatching { tick(System.currentTimeMillis()) }
                Thread.sleep(20_000)
            }
        }, "notifier").apply { isDaemon = true }.start()
    }

    private fun tick(now: Long) {
        if (settings?.alarms == false) return
        tasks.filter { it.open && it.planned in (now - 60_000)..now }.forEach { t ->
            val key = "${t.id}@${t.planned}"
            if (shown.add(key)) tray?.displayMessage("هلأ: ${t.title}", durLabel(t.minutes), java.awt.TrayIcon.MessageType.INFO)
        }
    }
}
