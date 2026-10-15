@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.imad.planner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var repo: Repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Alarms.channels(this)
        repo = Repository.get(this)
        lifecycleScope.launch { runCatching { repo.initialize() } }
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                PlannerTheme { Planner(repo) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::repo.isInitialized) lifecycleScope.launch { runCatching { repo.refreshDay() } }
    }
}

// ---------------- Theme & shared components (same look as version 0.1) ----------------

@Composable
fun PlannerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFFAAC7FF), secondaryContainer = Color(0xFF303B51),
        background = Color(0xFF13161C), surface = Color(0xFF1D222B),
    ) else lightColorScheme(
        primary = Color(0xFF245FE5), secondaryContainer = Color(0xFFEAF0FE),
        background = Color(0xFFF5F7FB),
    )
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 17.sp),
            bodyMedium = TextStyle(fontSize = 16.sp),
            labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}

fun timeLabel(millis: Long): String =
    if (millis == 0L) "بدون موعد"
    else Instant.ofEpochMilli(millis).atZone(Clock.zone).format(DateTimeFormatter.ofPattern("HH:mm"))

fun minuteLabel(m: Int): String = String.format(Locale.US, "%02d:%02d", (m / 60) % 24, m % 60)

fun timerLabel(millis: Long): String {
    val sec = millis.coerceAtLeast(0) / 1000
    return String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60)
}

/** Accepts Arabic-Indic digits and the Arabic decimal separator in number fields. */
fun digits(s: String): String = s.map { c ->
    when {
        Character.isDigit(c) -> ('0' + Character.getNumericValue(c))
        c == '٫' || c == ',' -> '.'
        else -> c
    }
}.joinToString("")

fun num(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else String.format(Locale.US, "%.1f", d)

fun dayLabel(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEEE، d MMMM", Locale("ar")))

@Composable
fun Action(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled) { Text(label) }
}

@Composable
fun Secondary(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled) { Text(label) }
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.Start, content = content)
    }
}

@Composable
fun Field(value: String, label: String, change: (String) -> Unit, numeric: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value, onValueChange = change, label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
    )
}

@Composable
fun Muted(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun SwitchRow(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { change(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = change)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: Int, close: () -> Unit, pick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = (initial / 60) % 24, initialMinute = initial % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = close,
        confirmButton = { TextButton(onClick = { pick(state.hour * 60 + state.minute) }) { Text("حسناً") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
        text = { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { TimePicker(state = state) } },
    )
}

// ---------------- Main screen ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Planner(repo: Repository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val tasks by repo.store.tasks().collectAsState(initial = emptyList())
    val goals by repo.store.goals().collectAsState(initial = emptyList())
    val projects by repo.store.projects().collectAsState(initial = emptyList())
    val rules by repo.store.rules().collectAsState(initial = emptyList())
    val logs by repo.store.logs().collectAsState(initial = emptyList())
    val metas by repo.store.metas().collectAsState(initial = emptyList())
    val settings = Settings.from(metas.associate { it.key to it.value })

    var tab by remember { mutableIntStateOf(0) }
    var editor by remember { mutableStateOf<Task?>(null) }
    var creating by remember { mutableStateOf<Task?>(null) }
    var ruleEditor by remember { mutableStateOf<Rule?>(null) }
    var goalEditor by remember { mutableStateOf<Goal?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var closeDay by remember { mutableStateOf(false) }
    var restoreText by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val act: (suspend () -> Unit) -> Unit = { block ->
        scope.launch {
            try { block() } catch (e: Exception) {
                snackbar.showSnackbar(e.message?.takeIf { it.any { c -> c in 'ء'..'ي' } } ?: "تعذّر تنفيذ العملية")
            }
        }
    }

    // Live clock; re-plan every minute so unstarted tasks flow forward with time.
    LaunchedEffect(Unit) {
        var ticks = 0
        while (true) {
            delay(1000); now = System.currentTimeMillis(); ticks++
            if (ticks % 60 == 0) runCatching { repo.replan() }
        }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) act {
            val text = repo.backup()
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            snackbar.showSnackbar("تم حفظ النسخة الاحتياطية")
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0
                if (size > 5_000_000) Toast.makeText(context, "حجم الملف كبير", Toast.LENGTH_SHORT).show()
                else restoreText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } catch (_: Exception) {
                Toast.makeText(context, "تعذّر فتح الملف", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val today = Clock.today()
    val todayStr = today.toString()
    val todays = tasks.filter { it.day == todayStr && it.state != "skipped" }.sortedWith(compareBy({ if (it.planned == 0L) Long.MAX_VALUE else it.planned }, { it.created }))
    val late = tasks.filter { it.open && it.day < todayStr }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(listOf("مهام اليوم", "الأسبوع", "الأهداف", "التقارير")[tab], style = MaterialTheme.typography.titleLarge)
                        Text(dayLabel(today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = { IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Filled.Settings, contentDescription = "الإعدادات") } },
            )
        },
        bottomBar = {
            NavigationBar {
                listOf(
                    "اليوم" to Icons.Filled.Today, "الأسبوع" to Icons.Filled.DateRange,
                    "الأهداف" to Icons.Filled.Flag, "التقارير" to Icons.Filled.BarChart,
                ).forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, contentDescription = null) }, label = { Text(label) })
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> todayTab(todays, tasks, late, now, settings, act, repo,
                    open = { editor = it }, add = { creating = it }, closeDay = { closeDay = true })
                1 -> weekTab(tasks, rules, settings, today, open = { editor = it }, openRule = { ruleEditor = it })
                2 -> goalsTab(goals, rules, projects, today, openGoal = { goalEditor = it }, add = { creating = it })
                else -> reportsTab(tasks, today, now, backup = { exporter.launch("planner-backup-$todayStr.json") }, restore = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) })
            }
        }
    }

    editor?.let { t ->
        TaskEditor(t, isNew = false, rule = t.ruleId?.let { id -> rules.firstOrNull { it.id == id } }, settings = settings, now = now,
            close = { editor = null },
            save = { task, asDefault -> act { repo.save(task, asDefault); editor = null } },
            saveRule = { r -> act { repo.saveRule(r); editor = null } },
            delete = { act { repo.delete(t.id); editor = null } },
            actions = TaskActions(
                reopen = { act { repo.reopen(t.id); editor = null } },
                toToday = { act { repo.bringToToday(t.id); editor = null } },
                postpone = { act { repo.postpone(t.id); editor = null } },
            ))
    }
    creating?.let { t ->
        TaskEditor(t, isNew = true, rule = null, settings = settings, now = now,
            close = { creating = null },
            save = { task, _ -> act { repo.save(task); creating = null } },
            saveRule = { r -> act { repo.saveRule(r); creating = null } },
            delete = { creating = null }, actions = null)
    }
    ruleEditor?.let { r ->
        RuleEditor(r, close = { ruleEditor = null },
            save = { act { repo.saveRule(it); ruleEditor = null } },
            delete = { act { repo.deleteRule(r.id); ruleEditor = null } })
    }
    goalEditor?.let { g ->
        GoalEditor(g, rules.filter { it.goalId == g.id }, logs.filter { it.goalId == g.id }, today,
            close = { goalEditor = null },
            save = { ng -> act { repo.goal(ng, logValue = true); goalEditor = null } },
            toggleRule = { r, on -> act { repo.saveRule(r.copy(active = on, anchorDay = if (on && !r.active) todayStr else r.anchorDay)) } },
            editRule = { ruleEditor = it },
            addSupport = {
                ruleEditor = Rule(title = "", minutes = 20, everyHours = 48, kind = "training", goalId = g.id,
                    sets = 3, workSec = 30, restSec = 180, anchorDay = todayStr, role = "support")
            })
    }
    if (settingsOpen) SettingsDialog(settings, close = { settingsOpen = false }, set = { k, v -> act { repo.setSetting(k, v) } },
        test = { Alarms.ring(context, 4999, "تجربة المنبه", "هكذا يرن عند انتهاء مهمة وبدء التالية", null, false, settings.insistent) })
    if (closeDay) CloseDayDialog(todays, now, close = { closeDay = false },
        moveAll = { act { val n = repo.closeDay(); closeDay = false; snackbar.showSnackbar("نُقلت $n مهام للغد") } })
    restoreText?.let { text ->
        AlertDialog(
            onDismissRequest = { restoreText = null },
            title = { Text("استعادة البيانات؟") },
            text = { Text("ستستبدل البيانات الحالية بمحتويات الملف. المؤقتات المستعادة تكون متوقفة.") },
            confirmButton = { TextButton(onClick = { restoreText = null; act { repo.restore(text); snackbar.showSnackbar("تمت الاستعادة") } }) { Text("استعادة") } },
            dismissButton = { TextButton(onClick = { restoreText = null }) { Text("إلغاء") } },
        )
    }
}

// ---------------- Today ----------------

fun currentTask(todays: List<Task>): Task? =
    todays.firstOrNull { it.running } ?: todays.firstOrNull { it.open && it.spent > 0 }
    ?: todays.filter { it.open && it.parallelTo == null && !it.overflow }.minByOrNull { if (it.planned == 0L) Long.MAX_VALUE else it.planned }

fun LazyListScope.todayTab(
    todays: List<Task>, all: List<Task>, late: List<Task>, now: Long, settings: Settings,
    act: (suspend () -> Unit) -> Unit, repo: Repository,
    open: (Task) -> Unit, add: (Task) -> Unit, closeDay: () -> Unit,
) {
    val doneCount = todays.count { it.done }
    val remainingMin = todays.filter { it.open && it.parallelTo == null }.sumOf { (Maths.remaining(it, now) / 60_000).toInt() }
    item {
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${todays.size} مهام", style = MaterialTheme.typography.bodyLarge)
                Text("$doneCount منجزة", style = MaterialTheme.typography.bodyLarge)
                Text("$remainingMin د متبقية", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    val current = currentTask(todays)
    if (current != null) item { CurrentPanel(current, todays, now, settings, act, repo, open) }

    val overflow = todays.filter { it.open && it.overflow }
    if (overflow.isNotEmpty()) item {
        Panel {
            Text("لا يتسع لها اليوم", style = MaterialTheme.typography.titleMedium)
            overflow.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${t.title} · ${t.minutes} دقيقة", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { act { repo.postpone(t.id) } }) { Text("للغد") }
                }
            }
        }
    }

    item {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("جدول اليوم", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Action("+ إضافة", { add(Task(title = "", minutes = 30, day = Clock.today().toString())) })
        }
    }
    if (todays.isEmpty()) item {
        Panel {
            Text("لا توجد مهام لهذا اليوم.", style = MaterialTheme.typography.bodyLarge)
            Muted("أضف اسم المهمة ومدتها، والتطبيق يرتّب وقتها.")
        }
    }
    items(todays, key = { it.id }) { t -> TaskCard(t, all, now) { open(t) } }
    if (todays.isNotEmpty()) item { Action("+ إضافة مهمة", { add(Task(title = "", minutes = 30, day = Clock.today().toString())) }, Modifier.fillMaxWidth()) }

    if (late.isNotEmpty()) item {
        var show by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Secondary(if (show) "إخفاء المهام المتأخرة" else "عرض المهام المتأخرة · ${late.size}", { show = !show }, Modifier.fillMaxWidth())
            if (show) late.forEach { t ->
                TaskCard(t, all, now, extra = "من ${t.day}") { open(t) }
            }
        }
    }
    item { Secondary("إغلاق اليوم", closeDay, Modifier.fillMaxWidth()) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CurrentPanel(t: Task, todays: List<Task>, now: Long, settings: Settings, act: (suspend () -> Unit) -> Unit, repo: Repository, open: (Task) -> Unit) {
    val spent = Maths.spent(t, now)
    val remaining = t.minutes * 60_000L - spent
    val timeUp = remaining <= 0
    val parallel = todays.filter { it.parallelTo == t.id && it.open }
    Panel {
        Text(
            if (t.running) "مهمة جارية · ${t.minutes} دقيقة" else "المهمة الحالية",
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        )
        Text(t.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clickable { open(t) })
        if (t.planned > 0) Muted("${timeLabel(t.planned)} – ${timeLabel(t.planned + t.minutes * 60_000L)}" + if (t.pinned) " · موعد ثابت" else "")
        Text(
            if (timeUp) "انتهى الوقت" else timerLabel(remaining),
            fontSize = 44.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            color = if (timeUp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        LinearProgressIndicator(progress = { (spent.toFloat() / (t.minutes * 60_000f)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        if (t.sets > 1 && t.restSec > 0) {
            val phase = trainingPhase(t, spent)
            Text(phase, style = MaterialTheme.typography.bodyLarge)
        }
        if (parallel.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("بالتوازي أثناء الراحة", style = MaterialTheme.typography.labelLarge)
                    parallel.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${p.title} · ${p.minutes} دقيقة", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = { act { repo.finish(p.id) } }) { Text("إنهاء") }
                        }
                    }
                }
            }
        }
        if (!timeUp) Action(
            when { t.running -> "إيقاف مؤقت"; t.spent > 0 -> "متابعة"; else -> "بدء" },
            { act { repo.timer(t.id) } }, Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (timeUp) Action("إنهاء", { act { repo.finish(t.id) } }, Modifier.weight(1f))
            else Secondary("إنهاء", { act { repo.finish(t.id) } }, Modifier.weight(1f))
            Secondary("تمديد ${settings.extend} دقائق", { act { repo.extend(t.id, settings.extend) } }, Modifier.weight(1f))
        }
        Secondary("تأجيل للغد", { act { repo.postpone(t.id) } }, Modifier.fillMaxWidth())
    }
}

fun trainingPhase(t: Task, spentMs: Long): String {
    val windows = Scheduler.restWindows(0, t.minutes, t.sets, t.workSec, t.restSec)
    val sec = (spentMs / 1000).toInt()
    if (windows.isEmpty()) return ""
    val firstWork = windows.first().startSec - t.workSec
    if (sec < firstWork) return "إحماء · الجولة 1 من ${t.sets} بعد ${(firstWork - sec) / 60 + 1} د"
    windows.forEachIndexed { i, w ->
        if (sec < w.startSec) return "الجولة ${i + 1} من ${t.sets}"
        if (sec < w.endSec) return "راحة · ${w.endSec - sec} ث للجولة ${i + 2}"
    }
    val lastEnd = windows.last().endSec + t.workSec
    return if (sec < lastEnd) "الجولة ${t.sets} من ${t.sets}" else "انتهت الجولات"
}

@Composable
fun TaskCard(t: Task, all: List<Task>, now: Long, extra: String? = null, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                when { t.done -> Icons.Filled.CheckCircle; t.running -> Icons.Filled.PlayCircle; t.spent > 0 -> Icons.Filled.PauseCircle; else -> Icons.Filled.RadioButtonUnchecked },
                contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Muted("${timeLabel(t.planned)} · ${t.minutes} دقيقة")
                val parent = t.parallelTo?.let { id -> all.firstOrNull { it.id == id } }
                val status = buildList {
                    add(when { t.done -> "منجزة"; t.running -> "جارية"; t.spent > 0 -> "متوقفة مؤقتاً"; else -> "بانتظار البدء" })
                    if (t.pinned && t.open) add("موعد ثابت")
                    if (parent != null && t.open) add("بالتوازي مع ${parent.title}")
                    if (t.overflow && t.open) add("لا يتسع اليوم")
                    if (extra != null) add(extra)
                }.joinToString(" · ")
                Text(status, style = MaterialTheme.typography.bodyMedium, color = if (t.overflow && t.open) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ---------------- Week ----------------

fun LazyListScope.weekTab(tasks: List<Task>, rules: List<Rule>, settings: Settings, today: LocalDate, open: (Task) -> Unit, openRule: (Rule) -> Unit) {
    val week = (0 until 7).map { today.plusDays(it.toLong()) }
    val lastWeek = (1..7).map { today.minusDays(it.toLong()).toString() }.toSet()
    val past = tasks.filter { it.day in lastWeek }
    val plannedMin = past.filter { it.state != "skipped" }.sumOf { it.minutes }
    val doneMin = past.filter { it.done }.sumOf { (it.spent / 60_000).toInt() }
    val lateCount = tasks.count { it.open && it.day < today.toString() }
    item {
        Panel {
            Text("مراجعة الأسبوع", style = MaterialTheme.typography.titleMedium)
            Text("المخطط في آخر ٧ أيام: $plannedMin دقيقة", style = MaterialTheme.typography.bodyLarge)
            Text("الوقت الفعلي: $doneMin دقيقة", style = MaterialTheme.typography.bodyLarge)
            Text("المهام المتأخرة: $lateCount", style = MaterialTheme.typography.bodyLarge)
        }
    }
    for (d in week) {
        val ds = d.toString()
        val real = tasks.filter { it.day == ds && it.state != "skipped" }
        val virtual = if (d == today) emptyList() else rules.filter { r ->
            r.active && real.none { it.ruleId == r.id } &&
                Scheduler.projection(r.everyHours, r.weekdays, LocalDate.parse(r.anchorDay), r.lastDoneDay?.let(LocalDate::parse), today, 7).contains(d)
        }
        val shown: List<Pair<Task, Boolean>> = if (d == today) real.map { it to true } else {
            val items = real.filter { it.open }.map {
                Item(it.id, it.minutes, fixedStart = if (it.pinned && it.planned > 0) Clock.minuteOfDay(it.planned) else null,
                    priority = it.priority, interruptible = it.interruptible, sets = it.sets, workSec = it.workSec, restSec = it.restSec, order = it.created)
            } + virtual.map {
                Item("rule:${it.id}", it.minutes, fixedStart = if (it.pinned && it.preferredMinute >= 0) it.preferredMinute else null,
                    preferred = it.preferredMinute.takeIf { m -> m >= 0 }, priority = it.priority, interruptible = it.interruptible,
                    sets = it.sets, workSec = it.workSec, restSec = it.restSec)
            }
            val plan = Scheduler.plan(items, settings.dayStart, settings.dayEnd, settings.dayStart, settings.gap)
            fun at(id: String) = plan.placed[id]?.let { Clock.epoch(d, it.start) } ?: 0L
            (real.map { it.copy(planned = if (it.open) at(it.id) else it.planned, parallelTo = plan.placed[it.id]?.parallelTo) to true } +
                virtual.map { r -> Task(id = "rule:${r.id}", title = r.title, minutes = r.minutes, day = ds, planned = at("rule:${r.id}"), ruleId = r.id,
                    parallelTo = plan.placed["rule:${r.id}"]?.parallelTo, pinned = r.pinned) to false })
                .sortedBy { it.first.planned }
        }
        item(key = "day-$ds") {
            Text(if (d == today) "اليوم · ${dayLabel(d)}" else dayLabel(d), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        }
        if (shown.isEmpty()) item(key = "empty-$ds") { Muted("لا توجد مهام مجدولة.") }
        items(shown, key = { "$ds-${it.first.id}" }) { (t, real) ->
            val all = shown.map { it.first }
            TaskCard(t, all, System.currentTimeMillis(), extra = if (!real) "متكررة" else null) {
                if (real) open(t) else rules.firstOrNull { it.id == t.ruleId }?.let(openRule)
            }
        }
    }
    val recurring = rules.filter { it.kind != "training" || it.active }
    if (recurring.isNotEmpty()) {
        item { Text("المهام المتكررة", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        items(recurring, key = { "rule-${it.id}" }) { r ->
            Card(Modifier.fillMaxWidth().clickable { openRule(r) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Muted(ruleSummary(r))
                }
            }
        }
    }
}

val weekdayNames = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")

fun ruleSummary(r: Rule): String = buildList {
    add(if (r.weekdays != 0) weekdayNames.filterIndexed { i, _ -> r.weekdays and (1 shl i) != 0 }.joinToString("، ") else "كل ${r.everyHours} ساعة")
    add("${r.minutes} دقيقة")
    add(if (r.preferredMinute >= 0) (if (r.pinned) "ثابت " else "يفضّل ") + minuteLabel(r.preferredMinute) else "وقت تلقائي")
    if (!r.active) add("متوقفة")
}.joinToString(" · ")

// ---------------- Goals ----------------

@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.goalsTab(goals: List<Goal>, rules: List<Rule>, projects: List<Project>, today: LocalDate, openGoal: (Goal) -> Unit, add: (Task) -> Unit) {
    item { Text("الأهداف الرياضية · ${goals.size}", style = MaterialTheme.typography.titleMedium) }
    items(goals, key = { it.id }) { g ->
        val pct = Maths.achievement(g)
        val baseline = Scheduler.achievement(g.baseline, g.reference, g.lower)
        val expected = Scheduler.expectedAchievement(baseline, LocalDate.parse(g.startDay), LocalDate.parse(g.targetDay), today)
        val active = rules.filter { it.goalId == g.id && it.active }
        Card(Modifier.fillMaxWidth().clickable { openGoal(g) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(g.name, style = MaterialTheme.typography.titleMedium)
                Text("${num(g.current)} / ${num(g.reference)} ${g.unit}", style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(progress = { (pct / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth()) {
                    Text("${num(pct)}٪ من الرقم المرجعي", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    val diff = pct - expected
                    Text(
                        if (diff >= 0) "متقدم ${num(diff)}٪" else "متأخر ${num(-diff)}٪", style = MaterialTheme.typography.bodyMedium,
                        color = if (diff >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
                if (g.lower) Muted("الزمن الأقل أفضل")
                if (active.isNotEmpty()) Muted(active.joinToString(" · ") { "${it.title}: كل ${it.everyHours} ساعة" })
            }
        }
    }
    item { Text("المشاريع", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
    items(projects, key = { it.id }) { p ->
        val stages = runCatching { JSONArray(p.stages).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())
        Panel {
            Text(p.name, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stages.forEach { s ->
                    Secondary(s, { add(Task(title = "${p.name}: $s", minutes = 60, day = today.toString(), projectId = p.id)) })
                }
            }
            Muted("اضغط مرحلة لإضافتها كمهمة.")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalEditor(
    g: Goal, rules: List<Rule>, logs: List<GoalLog>, today: LocalDate, close: () -> Unit, save: (Goal) -> Unit,
    toggleRule: (Rule, Boolean) -> Unit, editRule: (Rule) -> Unit, addSupport: () -> Unit,
) {
    var current by remember { mutableStateOf(num(g.current)) }
    var reference by remember { mutableStateOf(num(g.reference)) }
    Sheet("تحديث الأداء", close) {
        Text(g.name, style = MaterialTheme.typography.titleLarge)
        Field(current, "الأداء الحالي (${g.unit})", { current = it }, numeric = true)
        Field(reference, "الرقم المرجعي (${g.unit})", { reference = it }, numeric = true)
        Muted(if (g.lower) "الزمن الأقل أفضل. النسبة = المرجعي ÷ الحالي." else "النسبة = الحالي ÷ المرجعي. يمكن تجاوز ١٠٠٪.")
        val c = digits(current).toDoubleOrNull()
        val r = digits(reference).toDoubleOrNull()
        if (c != null && r != null && c > 0 && r > 0) {
            val rate = Scheduler.weeklyRateNeeded(c, r, g.lower, today, LocalDate.parse(g.targetDay))
            Text("المطلوب أسبوعياً حتى ${g.targetDay}: ${num(rate)} ${g.unit}", style = MaterialTheme.typography.bodyLarge)
        }
        Action("حفظ الأداء", { if (c != null && r != null && c > 0 && r > 0) save(g.copy(current = c, reference = r)) }, Modifier.fillMaxWidth(), enabled = c != null && r != null && c > 0 && r > 0)
        HorizontalDivider()
        Text("التدريب في الجدول", style = MaterialTheme.typography.titleMedium)
        rules.sortedBy { it.role }.forEach { rule ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SwitchRow(rule.title + if (rule.role == "support") " (مساند)" else "", rule.active) { toggleRule(rule, it) }
                Muted("كل ${rule.everyHours} ساعة · ${rule.sets}×${rule.workSec} ث · راحة ${rule.restSec} ث · ${rule.minutes} دقيقة")
                TextButton(onClick = { editRule(rule) }) { Text("تعديل") }
            }
        }
        rules.firstOrNull { it.source.isNotEmpty() }?.let { Muted("قيم مقترحة قابلة للتعديل — ${it.source}") }
        Secondary("+ تمرين مساند", addSupport, Modifier.fillMaxWidth())
        if (logs.isNotEmpty()) {
            HorizontalDivider()
            Text("سجل الأداء", style = MaterialTheme.typography.titleMedium)
            logs.takeLast(6).reversed().forEach { Text("${it.day}: ${num(it.value)} ${g.unit}", style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

// ---------------- Reports ----------------

fun LazyListScope.reportsTab(tasks: List<Task>, today: LocalDate, now: Long, backup: () -> Unit, restore: () -> Unit) {
    val todayTasks = tasks.filter { it.day == today.toString() && it.state != "skipped" }
    val week = (0 until 7).map { today.minusDays(it.toLong()).toString() }.toSet()
    val weekTasks = tasks.filter { it.day in week }
    item {
        Panel {
            Text("اليوم", style = MaterialTheme.typography.titleMedium)
            Text("المخطط: ${todayTasks.sumOf { it.minutes }} دقيقة", style = MaterialTheme.typography.bodyLarge)
            Text("المنجز: ${todayTasks.count { it.done }} من ${todayTasks.size}", style = MaterialTheme.typography.bodyLarge)
            Text("الوقت الفعلي: ${todayTasks.sumOf { Maths.spent(it, now) } / 60_000} دقيقة", style = MaterialTheme.typography.bodyLarge)
        }
    }
    item {
        Panel {
            Text("آخر ٧ أيام", style = MaterialTheme.typography.titleMedium)
            Text("المهام المنجزة: ${weekTasks.count { it.done }}", style = MaterialTheme.typography.bodyLarge)
            Text("الوقت الفعلي: ${weekTasks.sumOf { Maths.spent(it, now) } / 60_000} دقيقة", style = MaterialTheme.typography.bodyLarge)
            Text("المهام المتأخرة: ${tasks.count { it.open && it.day < today.toString() }}", style = MaterialTheme.typography.bodyLarge)
        }
    }
    item {
        Panel {
            Text("البيانات", style = MaterialTheme.typography.titleMedium)
            Action("تصدير نسخة احتياطية", backup, Modifier.fillMaxWidth())
            Secondary("استعادة من ملف JSON", restore, Modifier.fillMaxWidth())
            Muted("المهام والأهداف محفوظة على هذا الجهاز. الاستعادة تقبل نسخ الإصدار الأول أيضاً.")
        }
    }
}

// ---------------- Dialogs ----------------

@Composable
fun Sheet(title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = close) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") }
                    }
                    content()
                }
            }
        }
    }
}

class TaskActions(val reopen: () -> Unit, val toToday: () -> Unit, val postpone: () -> Unit)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditor(
    t: Task, isNew: Boolean, rule: Rule?, settings: Settings, now: Long, close: () -> Unit,
    save: (Task, Boolean) -> Unit, saveRule: (Rule) -> Unit, delete: () -> Unit, actions: TaskActions?,
) {
    // 0 = task, 1 = recurring, 2 = training
    var type by remember { mutableIntStateOf(if (t.kind == "training") 2 else 0) }
    var title by remember { mutableStateOf(t.title) }
    var minutes by remember { mutableStateOf(t.minutes.toString()) }
    var pinnedMinute by remember { mutableIntStateOf(if (t.pinned && t.planned > 0) Clock.minuteOfDay(t.planned) else -1) }
    var tomorrow by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var priority by remember { mutableIntStateOf(t.priority) }
    var interruptible by remember { mutableStateOf(t.interruptible) }
    var everyHours by remember { mutableStateOf("24") }
    var weekdays by remember { mutableIntStateOf(0) }
    var sets by remember { mutableStateOf(if (t.sets > 0) t.sets.toString() else "3") }
    var work by remember { mutableStateOf(if (t.workSec > 0) t.workSec.toString() else "30") }
    var rest by remember { mutableStateOf(if (t.restSec > 0) t.restSec.toString() else "180") }
    var asDefault by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val m = digits(minutes).toIntOrNull() ?: 0
    val valid = Maths.valid(title, m)
    val isTraining = type == 2
    val setsN = digits(sets).toIntOrNull() ?: 0
    val workN = digits(work).toIntOrNull() ?: 0
    val restN = digits(rest).toIntOrNull() ?: 0

    Sheet(if (isNew) "إضافة مهمة" else "تفاصيل المهمة", close) {
        if (isNew) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("مهمة سريعة", "متكررة", "جلسة تدريب").forEachIndexed { i, s ->
                    FilterChip(selected = type == i, onClick = {
                        type = i
                        if (i == 2) { advanced = true; if (minutes == "30") minutes = Scheduler.sessionMinutes(setsN, workN, restN).toString() }
                        if (i == 1) advanced = true
                    }, label = { Text(s) })
                }
            }
        }
        Field(title, "اسم المهمة", { title = it })
        Field(minutes, "المدة بالدقائق", { minutes = it }, numeric = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (pinnedMinute >= 0) "الموعد: ${minuteLabel(pinnedMinute)} (ثابت)" else "الموعد: تلقائي", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (pinnedMinute >= 0) TextButton(onClick = { pinnedMinute = -1 }) { Text("إزالة الموعد") }
            else TextButton(onClick = { picking = true }) { Text("تحديد موعد") }
        }
        if (isNew && type == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !tomorrow, onClick = { tomorrow = false }, label = { Text("اليوم") })
                FilterChip(selected = tomorrow, onClick = { tomorrow = true }, label = { Text("غداً") })
            }
        }
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "إخفاء الخيارات المتقدمة" else "خيارات متقدمة") }
        if (advanced) {
            Text("الأولوية", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("منخفضة", "عادية", "عالية").forEachIndexed { i, s -> FilterChip(selected = priority == i, onClick = { priority = i }, label = { Text(s) }) }
            }
            if (!isTraining) SwitchRow("تصلح في فترات الراحة (بالتوازي)", interruptible) { interruptible = it }
            if (isNew && type != 0) {
                Text("التكرار", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("24", "48", "72", "96").forEach { h ->
                        FilterChip(selected = weekdays == 0 && everyHours == h, onClick = { everyHours = h; weekdays = 0 }, label = { Text("كل $h ساعة") })
                    }
                }
                Field(everyHours, "أو كل كم ساعة", { everyHours = it; weekdays = 0 }, numeric = true)
                Text("أو أيام محددة", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    weekdayNames.forEachIndexed { i, name ->
                        FilterChip(selected = weekdays and (1 shl i) != 0, onClick = { weekdays = weekdays xor (1 shl i) }, label = { Text(name) })
                    }
                }
            }
            if (isTraining) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(sets, "الجولات", { sets = it }, numeric = true, modifier = Modifier.weight(1f))
                    Field(work, "مدة الجولة ث", { work = it }, numeric = true, modifier = Modifier.weight(1f))
                    Field(rest, "الراحة ث", { rest = it }, numeric = true, modifier = Modifier.weight(1f))
                }
                Muted("المدة الدنيا مع ١٠ د إحماء: ${Scheduler.sessionMinutes(setsN, workN, restN)} دقيقة. الراحة ٣ د فأكثر تُستغل لمهام خفيفة.")
            }
        }
        if (!isNew) {
            if (t.planned > 0 && !t.pinned) Muted("رتّبها التطبيق: ${timeLabel(t.planned)}")
            if (t.original > 0) Muted("الموعد الأصلي: ${timeLabel(t.original)}")
            Muted("الوقت المصروف: ${Maths.spent(t, now) / 60_000} دقيقة")
        }
        if (rule != null) {
            Row(Modifier.fillMaxWidth().clickable { asDefault = !asDefault }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = asDefault, onCheckedChange = { asDefault = it })
                Text("اجعل هذا التعديل هو الافتراضي", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Action(if (isNew) "حفظ المهمة" else "حفظ", {
            val day = if (tomorrow) Clock.today().plusDays(1) else LocalDate.parse(t.day)
            if (isNew && type != 0) {
                val minutesFinal = if (isTraining) maxOf(m, Scheduler.sessionMinutes(setsN, workN, restN, warmupMin = 0)) else m
                saveRule(
                    Rule(
                        title = title.trim(), minutes = minutesFinal,
                        everyHours = (digits(everyHours).toIntOrNull() ?: 24).coerceIn(1, 24 * 60), weekdays = weekdays,
                        preferredMinute = pinnedMinute, pinned = pinnedMinute >= 0, priority = priority,
                        interruptible = interruptible && !isTraining, kind = if (isTraining) "training" else "task",
                        sets = if (isTraining) setsN else 0, workSec = if (isTraining) workN else 0, restSec = if (isTraining) restN else 0,
                        anchorDay = Clock.today().toString(), goalId = t.goalId,
                    )
                )
            } else save(
                t.copy(
                    title = title, minutes = m, day = day.toString(), priority = priority, interruptible = interruptible && !isTraining,
                    pinned = pinnedMinute >= 0, planned = if (pinnedMinute >= 0) Clock.epoch(day, pinnedMinute) else (if (t.pinned) 0 else t.planned),
                    sets = if (isTraining) setsN else t.sets, workSec = if (isTraining) workN else t.workSec, restSec = if (isTraining) restN else t.restSec,
                ), asDefault
            )
        }, Modifier.fillMaxWidth(), enabled = valid)
        if (!isNew && actions != null) {
            when {
                !t.open -> Secondary("إعادة فتح المهمة", actions.reopen, Modifier.fillMaxWidth())
                t.day < Clock.today().toString() -> Secondary("نقل لليوم", actions.toToday, Modifier.fillMaxWidth())
                else -> Secondary("تأجيل للغد", actions.postpone, Modifier.fillMaxWidth())
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

@OptIn(ExperimentalLayoutApi::class)
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
    var sets by remember { mutableStateOf(r.sets.toString()) }
    var work by remember { mutableStateOf(r.workSec.toString()) }
    var rest by remember { mutableStateOf(r.restSec.toString()) }
    var picking by remember { mutableStateOf(false) }
    val training = r.kind == "training"
    val m = digits(minutes).toIntOrNull() ?: 0
    Sheet(if (r.title.isEmpty()) "تمرين مساند" else "المهمة المتكررة", close) {
        Field(title, "الاسم", { title = it })
        Field(minutes, "المدة بالدقائق", { minutes = it }, numeric = true)
        SwitchRow("ضمن الجدول", active) { active = it }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("24", "48", "72", "96").forEach { h ->
                FilterChip(selected = weekdays == 0 && everyHours == h, onClick = { everyHours = h; weekdays = 0 }, label = { Text("كل $h ساعة") })
            }
        }
        Field(everyHours, if (training) "كل كم ساعة (الاستشفاء)" else "كل كم ساعة", { everyHours = it; weekdays = 0 }, numeric = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            weekdayNames.forEachIndexed { i, name ->
                FilterChip(selected = weekdays and (1 shl i) != 0, onClick = { weekdays = weekdays xor (1 shl i) }, label = { Text(name) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (preferred >= 0) "الوقت المفضّل: ${minuteLabel(preferred)}" else "الوقت: تلقائي", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (preferred >= 0) TextButton(onClick = { preferred = -1; pinned = false }) { Text("تلقائي") }
            else TextButton(onClick = { picking = true }) { Text("تحديد") }
        }
        if (preferred >= 0) SwitchRow("موعد ثابت لا يتحرك", pinned) { pinned = it }
        if (training) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(sets, "الجولات", { sets = it }, numeric = true, modifier = Modifier.weight(1f))
                Field(work, "الجولة ث", { work = it }, numeric = true, modifier = Modifier.weight(1f))
                Field(rest, "الراحة ث", { rest = it }, numeric = true, modifier = Modifier.weight(1f))
            }
            if (r.source.isNotEmpty()) Muted(r.source)
            Muted("مرور الوقت لا يضمن الاستشفاء؛ عدّل حسب إحساسك.")
        } else SwitchRow("تصلح في فترات الراحة (بالتوازي)", interruptible) { interruptible = it }
        Action("حفظ", {
            save(
                r.copy(
                    title = title.trim(), minutes = m, everyHours = (digits(everyHours).toIntOrNull() ?: r.everyHours).coerceIn(1, 24 * 60),
                    weekdays = weekdays, preferredMinute = preferred, pinned = pinned && preferred >= 0, active = active,
                    interruptible = interruptible && !training,
                    sets = digits(sets).toIntOrNull() ?: r.sets, workSec = digits(work).toIntOrNull() ?: r.workSec,
                    restSec = digits(rest).toIntOrNull() ?: r.restSec,
                    anchorDay = if (active && !r.active) Clock.today().toString() else r.anchorDay,
                )
            )
        }, Modifier.fillMaxWidth(), enabled = Maths.valid(title, m))
        if (r.title.isNotEmpty()) Secondary("حذف", delete, Modifier.fillMaxWidth())
    }
    if (picking) TimeDialog(if (preferred >= 0) preferred else 17 * 60, { picking = false }) { preferred = it; picking = false }
}

@Composable
fun SettingsDialog(s: Settings, close: () -> Unit, set: (String, String) -> Unit, test: () -> Unit) {
    val context = LocalContext.current
    var pick by remember { mutableStateOf<String?>(null) }
    var gap by remember { mutableStateOf(s.gap.toString()) }
    var extend by remember { mutableStateOf(s.extend.toString()) }
    Sheet("الإعدادات", close) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("بداية اليوم: ${minuteLabel(s.dayStart)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { pick = "dayStart" }) { Text("تغيير") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("نهاية اليوم: ${minuteLabel(s.dayEnd)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { pick = "dayEnd" }) { Text("تغيير") }
        }
        Field(gap, "فاصل بين المهام (دقائق)", { gap = it; digits(it).toIntOrNull()?.let { v -> if (v in 0..120) set("gap", v.toString()) } }, numeric = true)
        Field(extend, "مدة التمديد (دقائق)", { extend = it; digits(it).toIntOrNull()?.let { v -> if (v in 1..240) set("extend", v.toString()) } }, numeric = true)
        HorizontalDivider()
        SwitchRow("المنبه عند انتهاء وبدء المهام", s.alarms) { set("alarms", if (it) "1" else "0") }
        SwitchRow("يرن حتى أوقفه", s.insistent) { set("insistent", if (it) "1" else "0") }
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
        Muted("الإصدار 0.2.0")
    }
    pick?.let { key ->
        TimeDialog(if (key == "dayStart") s.dayStart else s.dayEnd, { pick = null }) { v ->
            val ok = if (key == "dayStart") v < s.dayEnd else v > s.dayStart
            if (ok) set(key, v.toString()) else Toast.makeText(context, "بداية اليوم يجب أن تسبق نهايته", Toast.LENGTH_SHORT).show()
            pick = null
        }
    }
}

@Composable
fun CloseDayDialog(todays: List<Task>, now: Long, close: () -> Unit, moveAll: () -> Unit) {
    val open = todays.filter { it.open }
    Sheet("إغلاق اليوم", close) {
        Text("المخطط: ${todays.size} مهام · ${todays.sumOf { it.minutes }} دقيقة", style = MaterialTheme.typography.bodyLarge)
        Text("المنجز: ${todays.count { it.done }}", style = MaterialTheme.typography.bodyLarge)
        Text("المتبقي: ${open.size}", style = MaterialTheme.typography.bodyLarge)
        Text("الوقت الفعلي: ${todays.sumOf { Maths.spent(it, now) } / 60_000} دقيقة", style = MaterialTheme.typography.bodyLarge)
        if (open.isNotEmpty()) {
            HorizontalDivider()
            open.forEach { Text("• ${it.title}", style = MaterialTheme.typography.bodyLarge) }
            Action("نقل غير المنجز للغد", moveAll, Modifier.fillMaxWidth())
            Muted("المهام المتكررة غير المبدوءة لا تُنقل؛ تظهر في موعدها التالي.")
        }
    }
}
