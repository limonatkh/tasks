@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.imad.planner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.SnackbarResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

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

/** Everything the screens read, from the database. */
data class Snapshot(
    val tasks: List<Task>, val goals: List<Goal>, val projects: List<Project>, val rules: List<Rule>,
    val logs: List<GoalLog>, val plans: List<Plan>, val months: Map<String, MonthEntry>, val settings: Settings,
    val audioTree: Uri?, val today: LocalDate, val now: Long, val events: List<Event> = emptyList(),
    val closedToday: Boolean = false,
    /** Last important action that can be undone ("" when none). */
    val undoLabel: String = "",
    val undoAt: Long = 0,
) {
    val ruleMap get() = rules.associateBy { it.id }
    val memLast get() = plans.firstOrNull { it.kind == "memorize" && it.configured }?.done ?: 0
}

/** What the screens can ask for. */
class Nav(
    val act: (suspend () -> Unit) -> Unit,
    val openTask: (Task) -> Unit,
    val newTask: (Task) -> Unit,
    val openRule: (Rule) -> Unit,
    val openGoal: (Goal) -> Unit,
    val openPlan: (Plan) -> Unit,
    val newGoal: () -> Unit,
    val play: (Task, Boolean) -> Unit,
    val editMonth: (MonthTarget) -> Unit,
    val openYear: (YearTarget) -> Unit,
    val acceptGoals: (List<String>) -> Unit,
    val report: (String, String) -> Unit,
    val note: (Task) -> Unit,
    val postpone: (Task) -> Unit,
    val groups: () -> Unit,
    val taskMenu: (Task) -> Unit,
    val look: (List<String>, Int?, String?) -> Unit,
    val undo: () -> Unit = {},
    val movePeriod: (Task) -> Unit = {},
    val keepRest: (Task) -> Unit = {},
)

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
    val events by repo.store.events().collectAsState(initial = emptyList())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val metaMap = metas.associate { it.key to it.value }
    val snap = Snapshot(
        tasks, goals, projects, rules, logs, Plans.parse(metaMap["plans"]), Months.parse(metaMap["months"]),
        Settings.from(metaMap), metaMap["quranAudio"]?.let(Uri::parse), Clock.today(), now, events,
        closedToday = metaMap.containsKey("closed:${Clock.today()}"),
        undoLabel = metaMap["undoLabel"].orEmpty(), undoAt = metaMap["undoAt"]?.toLongOrNull() ?: 0,
    )

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editor by remember { mutableStateOf<Task?>(null) }
    var creating by remember { mutableStateOf<Task?>(null) }
    var ruleEditor by remember { mutableStateOf<Rule?>(null) }
    var goalEditor by remember { mutableStateOf<Goal?>(null) }
    var planEditor by remember { mutableStateOf<Plan?>(null) }
    var newGoal by remember { mutableStateOf(false) }
    var monthEdit by remember { mutableStateOf<MonthTarget?>(null) }
    var yearTarget by remember { mutableStateOf<YearTarget?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var prayerOpen by remember { mutableStateOf(false) }
    var placeFor by remember { mutableStateOf<Task?>(null) }
    var periodFor by remember { mutableStateOf<Task?>(null) }
    var keepRestFor by remember { mutableStateOf<Task?>(null) }
    var closeDay by remember { mutableStateOf(false) }
    var restoreText by remember { mutableStateOf<String?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var reportFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var noteFor by remember { mutableStateOf<Task?>(null) }
    var postponeFor by remember { mutableStateOf<Task?>(null) }
    var groupsOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Task?>(null) }

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
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
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
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) act {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            repo.setSetting("quranAudio", uri.toString())
            val n = withContext(Dispatchers.IO) { QuranPlayer.pageFiles(context, uri).size }
            snackbar.showSnackbar("وُجد $n صفحة صوتية في المجلد")
        }
    }
    val play: (Task, Boolean) -> Unit = { t, cont ->
        act {
            val tree = snap.audioTree
            if (tree == null) {
                folderPicker.launch(null)
            } else {
                val pages = repo.reviewPages(t.id, cont)
                if (pages.isEmpty()) snackbar.showSnackbar("لا توجد صفحات للسماع الآن")
                else {
                    if (!t.running) repo.timer(t.id)
                    val st = withContext(Dispatchers.IO) { QuranPlayer.play(context, tree, pages, t.id) }
                    if (!st.playing) snackbar.showSnackbar("لم أجد ملفات الصفحات ${pages.first()}–${pages.last()} في المجلد")
                }
            }
        }
    }

    val nav = Nav(
        act = act, openTask = { editor = it }, newTask = { creating = it }, openRule = { ruleEditor = it },
        openGoal = { goalEditor = it }, openPlan = { planEditor = it }, newGoal = { newGoal = true }, play = play,
        editMonth = { monthEdit = it }, openYear = { yearTarget = it },
        acceptGoals = { ids -> act { repo.acceptGoals(ids) } },
        report = { series, title -> reportFor = series to title },
        note = { noteFor = it }, postpone = { postponeFor = it }, groups = { groupsOpen = true },
        taskMenu = { menuFor = it },
        undo = { act { repo.undo()?.let { snackbar.showSnackbar("تراجعت عن: $it") } } },
        movePeriod = { periodFor = it },
        keepRest = { keepRestFor = it },
        look = { series, color, icon -> act { repo.setLook(series, color, icon) } },
    )

    val today = snap.today
    val emptyTask = { day: LocalDate -> Task(title = "", minutes = 30, day = day.toString()) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text(listOf("مهام اليوم", "خطة بكرا", "الخطة", "التقارير")[tab], style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Text(dayLabel(if (tab == 1) today.plusDays(1) else today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { addMenu = true }) { Icon(Icons.Filled.Add, contentDescription = "إضافة") }
                        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                            DropdownMenuItem(text = { Text("مهمة جديدة") }, onClick = { addMenu = false; creating = emptyTask(if (tab == 1) today.plusDays(1) else today) })
                            DropdownMenuItem(text = { Text("هدف جديد") }, onClick = { addMenu = false; newGoal = true })
                        }
                    }
                    IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Filled.Settings, contentDescription = "الإعدادات") }
                },
            )
        },
        bottomBar = {
            TextTabs(listOf("اليوم", "بكرا", "الخطة", "التقارير"), tab) { tab = it }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> TodayTab(snap, repo, nav, add = { key -> creating = emptyTask(today).copy(period = key) }, askClose = { closeDay = true },
                    closeDay = { mode -> act { val n = repo.closeDay(mode); snackbar.showSnackbar(if (mode == "carry") "ترحّلت $n مهام لبكرا" else "انسكّر اليوم") } },
                    goTomorrow = { tab = 1 })
                1 -> TomorrowTab(snap, repo, nav, add = { creating = emptyTask(today.plusDays(1)) })
                2 -> PlanTab(snap, nav)
                else -> ReportsTab(snap, nav, backup = { exporter.launch("planner-backup-$today.json") }, restore = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) })
            }
        }
    }

    editor?.let { t ->
        val fresh = tasks.firstOrNull { it.id == t.id } ?: t
        TaskEditor(fresh, isNew = false, rule = fresh.ruleId?.let { id -> rules.firstOrNull { it.id == id } }, now = now,
            close = { editor = null },
            save = { task, asDefault, _ -> act { repo.save(task, asDefault); editor = null } },
            saveRule = { r -> act { repo.saveRule(r); editor = null } },
            delete = { act { repo.delete(t.id); editor = null } },
            actions = TaskActions(
                reopen = { act { repo.reopen(t.id); editor = null } },
                toToday = { act { repo.bringToToday(t.id); editor = null } },
                postpone = { editor = null; postponeFor = fresh },
                report = { reportFor = seriesOf(fresh) to fresh.baseTitle },
                note = { noteFor = fresh },
            ),
            routineNow = isRoutine(fresh, snap.ruleMap, snap.settings), colorNow = snap.settings.colors[seriesOf(fresh)] ?: -1,
            saveTag = { r, c -> act { repo.setTag(seriesOf(fresh), r, c) } },
            iconNow = snap.settings.icons[seriesOf(fresh)],
            saveIcon = { i -> act { repo.setLook(listOf(seriesOf(fresh)), null, i) } })
    }
    creating?.let { t ->
        TaskEditor(t, isNew = true, rule = null, now = now,
            close = { creating = null },
            save = { task, _, phases -> act { repo.saveNew(task, phases); creating = null } },
            saveRule = { r -> act { repo.saveRule(r); creating = null } },
            delete = { creating = null }, actions = null)
    }
    ruleEditor?.let { r ->
        RuleEditor(r, close = { ruleEditor = null },
            save = { act { repo.saveRule(it); ruleEditor = null } },
            delete = { act { repo.deleteRule(r.id); ruleEditor = null } })
    }
    goalEditor?.let { g ->
        GoalEditor(g, rules.filter { g.id in Members.goals(it) }, logs.filter { it.goalId == g.id }, today,
            close = { goalEditor = null },
            save = { ng -> act { repo.goal(ng, logValue = true); goalEditor = null } },
            toggleRule = { r, on -> act { repo.saveRule(r.copy(active = on, anchorDay = if (on && !r.active) today.toString() else r.anchorDay)) } },
            editRule = { ruleEditor = it },
            delete = { act { repo.deleteGoal(g.id); goalEditor = null } })
    }
    planEditor?.let { p ->
        PlanEditor(p, snap, close = { planEditor = null },
            save = { np, phases -> act { repo.savePlan(np, phases); planEditor = null } },
            delete = { act { repo.deletePlan(p.id); planEditor = null } },
            pickFolder = { folderPicker.launch(null) })
    }
    if (newGoal) NewGoalSheet(today, close = { newGoal = false },
        saveGoal = { g, r -> act { repo.addGoal(g, r); newGoal = false } },
        savePlan = { p -> act { repo.savePlan(p); newGoal = false } },
        saveRule = { r -> act { repo.saveRule(r); newGoal = false } })
    monthEdit?.let { m ->
        MonthEditSheet(m, snap.months[Months.key(m.key, m.ym.toString())], close = { monthEdit = null },
            save = { v, reviewed -> act { repo.setMonth(m.key, m.ym.toString(), v, reviewed); monthEdit = null } })
    }
    yearTarget?.let { y ->
        YearGoalSheet(y, snap, nav, close = { yearTarget = null },
            acceptAll = { key, yms -> act { repo.acceptMonths(key, yms) } },
            acceptTraining = { ids -> act { repo.acceptGoals(ids) } },
            move = { key -> act { repo.moveToGroup(y.goal?.id, y.plan?.id, null, key) } })
    }
    reportFor?.let { (series, title) -> ReportSheet(series, title, snap, close = { reportFor = null }, note = { text -> act { repo.store.task(series)?.let { repo.addNote(it.id, text) } ?: snap.tasks.lastOrNull { seriesOf(it) == series }?.let { repo.addNote(it.id, text) } } }) }
    noteFor?.let { t -> TextDialog("ملاحظة على «${t.baseTitle}»", "ليش تأخرت أو ما خلصتها؟", close = { noteFor = null }) { text -> act { repo.addNote(t.id, text); noteFor = null } } }
    postponeFor?.let { t -> PostponeChoice(t, recurring = t.ruleId != null, close = { postponeFor = null }) { text, mode -> act { repo.postpone(t.id, text, mode); postponeFor = null } } }
    if (prayerOpen) PrayerSheet(snap.settings, today, close = { prayerOpen = false }, set = { k, v -> act { repo.setSetting(k, v) } })
    placeFor?.let { t ->
        PlaceDialog(t.baseTitle, snap.settings.places[seriesOf(t)], snap.settings.groupPeriods[t.category], close = { placeFor = null }) { v -> act { repo.setPlace(seriesOf(t), v) } }
    }
    periodFor?.let { t -> PeriodPicker(snap.settings.periodsOn(today), Clock.minuteOfDay(now), close = { periodFor = null }) { k -> act { repo.moveToPeriod(t.id, k) } } }
    keepRestFor?.let { t -> KeepRestDialog(t, close = { keepRestFor = null }) { m -> act { repo.stopKeepRest(t.id, m); keepRestFor = null } } }
    if (groupsOpen) GroupsSheet(snap, close = { groupsOpen = false }, save = { list -> act { repo.saveGroups(list) } },
        setPeriod = { g, p -> act { repo.setGroupPeriod(g, p) } })
    menuFor?.let { t ->
        TaskMenu(t, close = { menuFor = null },
            startNow = { act { repo.startNow(t.id); menuFor = null } },
            prayerMode = snap.settings.prayerMode && t.day == today.toString(),
            movePeriod = { menuFor = null; periodFor = t },
            place = { menuFor = null; placeFor = t },
            postpone = if (t.open) ({ menuFor = null; postponeFor = t }) else null,
            details = { menuFor = null; editor = t },
            report = { menuFor = null; reportFor = seriesOf(t) to t.baseTitle },
            note = { menuFor = null; noteFor = t })
    }
    if (settingsOpen) SettingsDialog(snap.settings, close = { settingsOpen = false }, set = { k, v -> act { repo.setSetting(k, v) } }, openGroups = { groupsOpen = true }, openPrayers = { prayerOpen = true },
        test = { Alarms.ring(context, 4999, "تجربة المنبه", "هكذا يرن عند انتهاء مهمة وبدء التالية", null, false, snap.settings.insistent) })
    if (closeDay) CloseDayDialog(snap.tasks.filter { it.day == today.toString() && it.state != "skipped" }, now, close = { closeDay = false },
        finish = { mode -> act { val n = repo.closeDay(mode); closeDay = false; snackbar.showSnackbar(if (mode == "carry") "ترحّلت $n مهام لبكرا" else "انسكّر اليوم") } })
    restoreText?.let { text ->
        RestorePreview(text, close = { restoreText = null }) {
            act {
                repo.restore(text)
                if (snackbar.showSnackbar("تمت الاستعادة", actionLabel = "تراجع", withDismissAction = true) == SnackbarResult.ActionPerformed) {
                    repo.undo(); snackbar.showSnackbar("رجعت بياناتك زي ما كانت قبل الاستعادة")
                }
            }
        }
    }
}

// ---------------- Today: the current task, alone ----------------

fun currentTask(todays: List<Task>): Task? =
    todays.firstOrNull { it.running } ?: todays.firstOrNull { it.open && (it.spent > 0 || it.doneUnits > 0) && it.parallelTo == null }
    ?: todays.filter { it.open && it.parallelTo == null && !it.overflow }.minByOrNull { if (it.planned == 0L) Long.MAX_VALUE else it.planned }

fun todayTasks(s: Snapshot) = s.tasks.filter { it.day == s.today.toString() && it.state != "skipped" }
    .sortedWith(compareBy({ if (it.planned == 0L) Long.MAX_VALUE else it.planned }, { it.created }))

/** Frame colours of a task in the period view. */
object Status {
    val done = Color(0xFF4FA36C)
    val missed = Color(0xFFD0574E)
}

/** done (green), missed (red: its period is over), running (accent) or nothing. */
@Composable
fun statusColor(t: Task, periodOver: Boolean): Color? = when {
    t.done -> Status.done
    t.state == "skipped" -> Status.missed
    t.running -> MaterialTheme.colorScheme.primary
    periodOver && t.open -> Status.missed
    else -> null
}

@Composable
fun TodayTab(s: Snapshot, repo: Repository, nav: Nav, add: (String) -> Unit, askClose: () -> Unit, closeDay: (String) -> Unit, goTomorrow: () -> Unit) {
    if (s.settings.prayerMode) PeriodToday(s, repo, nav, add, askClose, closeDay, goTomorrow)
    else HoursToday(s, repo, nav, { add("") }, askClose, closeDay, goTomorrow)
}

@Composable
fun NightPanel(s: Snapshot, todays: List<Task>, beforeWake: Boolean, closeDay: (String) -> Unit, goTomorrow: () -> Unit) {
    val left = todays.count { it.open }
    if (beforeWake) Panel {
        Text("وقت النوم", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text("يومك بيبدأ الساعة ${fmt12(s.settings.dayStart)}", style = MaterialTheme.typography.bodyLarge)
    } else Panel {
        Text("انتهى اليوم", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text("${todays.count { it.done }} منجزة من ${todays.size}", style = MaterialTheme.typography.bodyLarge)
        if (left > 0 && !s.closedToday) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("ترحيل لبكرا", { closeDay("carry") }, Modifier.weight(1f))
                Secondary("إغلاق اليوم", { closeDay("close") }, Modifier.weight(1f))
            }
        }
        Secondary("خطة بكرا", goTomorrow, Modifier.fillMaxWidth())
    }
}

/** Undo stays on screen for two minutes after the action. */
@Composable
fun UndoRow(s: Snapshot, nav: Nav) {
    if (s.undoLabel.isNotBlank() && s.now - s.undoAt < 120_000) TextButton(onClick = nav.undo, modifier = Modifier.fillMaxWidth()) { Text("↶ تراجع عن: ${s.undoLabel}") }
}

/** Prayer mode: no clock times, the day is the five periods and their tasks. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PeriodToday(s: Snapshot, repo: Repository, nav: Nav, add: (String) -> Unit, askClose: () -> Unit, closeDay: (String) -> Unit, goTomorrow: () -> Unit) {
    val ts = s.today.toString()
    val all = s.tasks.filter { it.day == ts && !(it.state == "skipped" && it.note == "اندمجت مع جلسة بكرا") }
        .sortedWith(compareBy({ !it.running }, { if (it.planned == 0L) Long.MAX_VALUE else it.planned }, { it.created }))
    val todays = all.filter { it.state != "skipped" }
    val minute = Clock.minuteOfDay(s.now)
    val beforeWake = minute < s.settings.dayStart
    val afterSleep = minute >= s.settings.dayEnd || s.closedToday
    val periods = s.settings.periodsOn(s.today)
    val nowKey = s.settings.periodAt(s.today, minute)
    val active = todays.firstOrNull { it.running } ?: todays.firstOrNull { it.open && (it.spent > 0 || it.doneUnits > 0) && it.parallelTo == null }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = picked ?: nowKey
    val toTop: () -> Unit = { scope.launch { delay(150); listState.animateScrollToItem(0) } }
    val start: (Task) -> Unit = { t ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        nav.act { repo.startNow(t.id) }
        toTop()
    }
    val loads = periodLoads(s, todays).associateBy { it.period.key }
    // Tasks finished before they had a period (older versions) are shown where their time fell.
    val keyOf: (Task) -> String = { t -> t.period.ifEmpty { if (!t.open && t.planned > 0) s.settings.periodAt(s.today, Clock.minuteOfDay(t.planned)) else "" } }
    val rules = s.ruleMap
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            when {
                active != null -> CurrentPanel(active, todays, s, repo, nav)
                beforeWake || afterSleep -> NightPanel(s, todays, beforeWake, closeDay, goTomorrow)
                else -> NextPanel(s, todays, periods, nowKey, start) { picked = nowKey; scope.launch { listState.animateScrollToItem(2) } }
            }
        }
        item { UndoRow(s, nav) }
        item { HideRoutineRow() }
        for (p in periods) {
            val list = all.filter { keyOf(it) == p.key && (!HideRoutine.on || !isRoutine(it, rules, s.settings)) }
            val over = p.end <= minute
            val open = selected == p.key
            item(key = "h-${p.key}") {
                PeriodTitle(p, list, over, nowKey == p.key, open, loads[p.key],
                    click = { picked = if (open) "" else p.key },
                    longClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); add(p.key) })
            }
            if (open) {
                if (list.isEmpty()) item(key = "e-${p.key}") { Muted("   ما في مهام بهاي الفترة. اضغط مطوّلاً على اسمها لتضيف.") }
                items(list, key = { "t-" + it.id }) { t ->
                    val color = statusColor(t, over)
                    if (t.open) SwipeRow(
                        start = if (!t.running) ({ start(t) }) else null,
                        defer = { nav.act { repo.deferTask(t.id, s.settings.swipeAction) } }, deferMode = s.settings.swipeAction,
                    ) { TaskRow(t, todays, s.now, s = s, status = color, highlight = true, hideTime = !t.pinned) { nav.taskMenu(t) } }
                    else TaskRow(t, todays, s.now, s = s, status = color, highlight = true, hideTime = !t.pinned) { nav.taskMenu(t) }
                }
            }
        }
        val loose = todays.filter { it.open && it.period.isEmpty() }
        if (loose.isNotEmpty()) {
            item { Text("ما وسعها اليوم · ${loose.size}", style = MaterialTheme.typography.titleMedium, color = Status.missed) }
            items(loose, key = { "t-" + it.id }) { t ->
                SwipeRow(start = { start(t) }, defer = { nav.act { repo.deferTask(t.id, s.settings.swipeAction) } }, deferMode = s.settings.swipeAction) {
                    TaskRow(t, todays, s.now, extra = "اضغط عليها وانقلها لفترة", s = s, hideTime = true) { nav.taskMenu(t) }
                }
            }
        }
        val late = s.tasks.filter { it.open && it.day < ts }
        if (late.isNotEmpty()) {
            item { Text("متأخرة من أيام سابقة · ${late.size}", style = MaterialTheme.typography.titleMedium) }
            items(late, key = { "late-" + it.id }) { t -> TaskRow(t, late, s.now, extra = "من ${t.day}", s = s) { nav.openTask(t) } }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Secondary("+ مهمة", { add(selected.takeIf { it.isNotEmpty() } ?: nowKey) }, Modifier.weight(1f))
                Secondary("إغلاق اليوم", askClose, Modifier.weight(1f))
            }
        }
    }
}

/** Top of the page when nothing is running: the next task of this period, and how long since the last one ended. */
@Composable
fun NextPanel(s: Snapshot, todays: List<Task>, periods: List<Period>, nowKey: String, start: (Task) -> Unit, showPeriod: () -> Unit) {
    val period = periods.firstOrNull { it.key == nowKey }
    val here = todays.filter { it.open && it.period == nowKey && it.parallelTo == null }
    val next = here.firstOrNull()
    val lastEnd = todays.filter { it.done && it.finishedAt > 0 }.maxOfOrNull { it.finishedAt }
    val idle = lastEnd?.let { ((s.now - it) / 60_000).toInt() }
    Panel {
        Text("فترة ${period?.name ?: ""}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (next != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TaskBadge(s.settings.icons[seriesOf(next)], tagColor(colorIndexOf(next, s.settings)), 30.dp)
                Column(Modifier.weight(1f)) {
                    Text(next.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Muted(durLabel(next.minutes) + if (here.size > 1) " · وباقي ${here.size - 1} بهاي الفترة" else "")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("ابدأ", { start(next) }, Modifier.weight(1f))
                Secondary("اختار غيرها", showPeriod, Modifier.weight(1f))
            }
        } else {
            Text("خلصت مهام هاي الفترة ✓", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Status.done)
            val after = periods.getOrNull(periods.indexOfFirst { it.key == nowKey } + 1)
            if (after != null) {
                val n = todays.count { it.open && it.period == after.key }
                Muted("الجاية: ${after.name} الساعة ${fmt12(after.start)}" + if (n > 0) " · $n مهام" else "")
            }
        }
        if (idle != null && next != null) Text(
            "صارلك ${durLabel(idle.coerceAtLeast(1))} من آخر مهمة خلّصتها",
            style = MaterialTheme.typography.bodyLarge,
            color = if (idle >= 15) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PeriodTitle(p: Period, list: List<Task>, over: Boolean, now: Boolean, open: Boolean, load: PeriodLoad?, click: () -> Unit, longClick: () -> Unit) {
    val done = list.count { it.done }
    val missed = list.count { it.state == "skipped" || (over && it.open) }
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = click, onLongClick = longClick),
        colors = CardDefaults.cardColors(containerColor = when {
            open -> MaterialTheme.colorScheme.primaryContainer
            now -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(p.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(fmt12(p.start), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (now) Text("الآن", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Box(Modifier.weight(1f))
            if (list.isNotEmpty()) Text(
                buildString {
                    append("$done/${list.size}")
                    if (missed > 0) append(" · $missed ما انعملت")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (missed > 0) Status.missed else if (done == list.size) Status.done else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(if (open) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!over && load != null && load.balance < 0) Text(
            "الفترة ناقصها ${durLabel(-load.balance)}: انقل مهمة لفترة ثانية",
            Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp), style = MaterialTheme.typography.bodyMedium, color = Status.missed,
        )
    }
}

// ---------------- Hours mode (the older way) ----------------

@Composable
fun HoursToday(s: Snapshot, repo: Repository, nav: Nav, add: () -> Unit, askClose: () -> Unit, closeDay: (String) -> Unit, goTomorrow: () -> Unit) {
    val todays = todayTasks(s)
    val left = todays.count { it.open }
    val minute = Clock.minuteOfDay(s.now)
    val beforeWake = minute < s.settings.dayStart
    val afterSleep = minute >= s.settings.dayEnd || s.closedToday
    val running = todays.firstOrNull { it.running }
    val current = if (running != null || (!beforeWake && !afterSleep)) currentTask(todays) else null
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            when {
                current != null -> CurrentPanel(current, todays, s, repo, nav)
                beforeWake || afterSleep -> NightPanel(s, todays, beforeWake, closeDay, goTomorrow)
                else -> Panel {
                    Text("لا توجد مهمة الآن", style = MaterialTheme.typography.titleLarge)
                    Muted(if (todays.isEmpty()) "أضف مهمة والتطبيق يرتّب وقتها." else "خلصت مهام اليوم.")
                }
            }
        }
        item { UndoRow(s, nav) }
        item {
            Secondary((if (expanded) "▲ " else "▼ ") + if (left > 0) "باقي مهام اليوم · $left" else "مهام اليوم", {
                expanded = !expanded
                if (expanded) scope.launch { delay(50); listState.animateScrollToItem(2) }
            }, Modifier.fillMaxWidth())
        }
        if (expanded) {
            item { HideRoutineRow() }
            items(todays.filter { !HideRoutine.on || !isRoutine(it, s.ruleMap, s.settings) }, key = { "t-" + it.id }) { t ->
                if (t.open) SwipeRow(
                    start = if (!t.running) ({ nav.act { repo.startNow(t.id) }; scope.launch { delay(150); listState.animateScrollToItem(0) } }) else null,
                    defer = { nav.act { repo.deferTask(t.id, s.settings.swipeAction) } }, deferMode = s.settings.swipeAction,
                ) { TaskRow(t, todays, s.now, s = s) { nav.taskMenu(t) } }
                else TaskRow(t, todays, s.now, s = s, status = statusColor(t, false)) { nav.taskMenu(t) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Secondary("+ مهمة", add, Modifier.weight(1f))
                    Secondary("إغلاق اليوم", askClose, Modifier.weight(1f))
                }
            }
        } else item { Secondary("+ إضافة مهمة", add, Modifier.fillMaxWidth()) }
    }
}

/** Remembers the "hide routine" switch of the day list while the app is open. */
object HideRoutine { var on by mutableStateOf(false) }

@Composable
fun HideRoutineRow() { SwitchRow("إخفاء الروتين", HideRoutine.on) { HideRoutine.on = it } }

@Composable
fun CurrentPanel(t: Task, todays: List<Task>, s: Snapshot, repo: Repository, nav: Nav) {
    val now = s.now
    val spent = Maths.spent(t, now)
    val remaining = t.minutes * 60_000L - spent
    val timeUp = remaining <= 0
    val parallel = todays.filter { it.parallelTo == t.id && it.open }
    val upcoming = !t.running && t.spent == 0L && t.doneUnits == 0 && t.planned > now + 60_000
    val isReview = s.plans.any { it.kind == "review" && t.ruleId == "plan-${it.id}" }
    val isMemorize = s.plans.any { it.kind == "memorize" && t.ruleId == "plan-${it.id}" }
    val members = t.ruleId?.let { id -> s.rules.firstOrNull { it.id == id } }?.members?.let(Members::parse).orEmpty()
    Panel {
        Text(
            when { t.running -> "جارية الآن"; upcoming -> "التالية"; t.spent > 0 -> "متوقفة مؤقتاً"; else -> "الآن" },
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TaskBadge(s.settings.icons[seriesOf(t)], tagColor(colorIndexOf(t, s.settings)), 32.dp)
            Text(t.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { nav.openTask(t) })
        }
        s.plans.firstOrNull { "plan-${it.id}" == t.ruleId && it.kind == "cycle" }?.let { p ->
            PlanMath.stageDetails(p, 0).takeIf { it.isNotBlank() }?.let { Muted(it) }
        }
        TimeLine(t.planned, t.minutes, end = true)
        if (t.pinned) Muted("موعد ثابت")
        if (members.isNotEmpty() && !t.running && t.spent == 0L) Muted(members.joinToString(" · ") { "${it.name} ${it.sets}×" })
        if (!upcoming) {
            if (s.settings.timerStyle == "analog" && !timeUp) {
                AnalogTimer(spent, t.minutes * 60_000L)
            } else {
                Text(
                    if (timeUp) "انتهى الوقت" else timerLabel(remaining),
                    fontSize = 52.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    color = if (timeUp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                LinearProgressIndicator(progress = { (spent.toFloat() / (t.minutes * 60_000f)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
            }
        }
        if (t.sets > 1 && t.restSec > 0 && (t.running || t.spent > 0)) Text(trainingPhase(t, spent, members), style = MaterialTheme.typography.bodyLarge)
        if (isReview) ReviewControls(t, s, nav)
        if (parallel.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("بالتوازي أثناء الراحة", style = MaterialTheme.typography.labelLarge)
                    parallel.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${p.title} · ${durLabel(p.minutes)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = { nav.act { repo.finish(p.id) } }) { Text("إنهاء") }
                        }
                        if (s.plans.any { it.kind == "review" && p.ruleId == "plan-${it.id}" }) ReviewControls(p, s, nav)
                    }
                }
            }
        }
        if (!timeUp && !isReview) Action(
            when { t.running -> "إيقاف مؤقت"; t.spent > 0 -> "متابعة"; upcoming -> "ابدأ الآن"; else -> "بدء" },
            { nav.act { repo.timer(t.id) } }, Modifier.fillMaxWidth(),
        )
        if (timeUp) {
            Action(if (isMemorize) "حفظتها ✓" else "أنجزتها ✓", { nav.act { repo.finish(t.id) } }, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Secondary("تمديد ${s.settings.extend} د", { nav.act { repo.extend(t.id, s.settings.extend) } }, Modifier.weight(1f))
                Secondary("إيقاف وحفظ المتبقي", { nav.keepRest(t) }, Modifier.weight(1f))
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Secondary(if (isMemorize) "حفظتها ✓" else "إنهاء", { nav.act { repo.finish(t.id) } }, Modifier.weight(1f))
            Secondary("تمديد ${s.settings.extend} د", { nav.act { repo.extend(t.id, s.settings.extend) } }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Secondary("تأجيل", { nav.postpone(t) }, Modifier.weight(1f))
            Secondary("ملاحظة", { nav.note(t) }, Modifier.weight(1f))
            Secondary("التقرير", { nav.report(seriesOf(t), t.baseTitle) }, Modifier.weight(1f))
        }
    }
}

/** Which exercise / set / rest the running training is in. */
fun trainingPhase(t: Task, spentMs: Long, members: List<Exercise>): String {
    val windows = Scheduler.restWindows(0, t.minutes, t.sets, t.workSec, t.restSec)
    val sec = (spentMs / 1000).toInt()
    if (windows.isEmpty()) return ""
    fun setName(i: Int): String {
        if (members.isEmpty()) return "الجولة ${i + 1} من ${t.sets}"
        var k = i
        for (e in members) {
            if (k < e.sets) return "${e.name} · الجولة ${k + 1} من ${e.sets}"
            k -= e.sets
        }
        return "الجولة ${i + 1}"
    }
    val firstWork = windows.first().startSec - t.workSec
    if (sec < firstWork) return "إحماء · بعده ${setName(0)}"
    windows.forEachIndexed { i, w ->
        if (sec < w.startSec) return setName(i)
        if (sec < w.endSec) return "راحة · ${w.endSec - sec} ث · بعدها ${setName(i + 1)}"
    }
    val lastEnd = windows.last().endSec + t.workSec
    return if (sec < lastEnd) setName(t.sets - 1) else "انتهت الجولات"
}

@Composable
fun ReviewControls(t: Task, s: Snapshot, nav: Nav) {
    val context = LocalContext.current
    val st by QuranPlayer.state.collectAsState()
    val p = s.plans.firstOrNull { "plan-${it.id}" == t.ruleId }
    val left = t.units - t.doneUnits
    val dayLeft = p?.let { (it.dayQuota - it.dayDone).coerceAtLeast(0) } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("سمعت ${t.doneUnits} من ${t.units} صفحة", style = MaterialTheme.typography.bodyLarge)
        if (st.playing && st.taskId == t.id) {
            Text("يُشغَّل: صفحة ${st.page}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Secondary("التالي", { QuranPlayer.next(context) }, Modifier.weight(1f))
                Secondary("إيقاف", { QuranPlayer.stop(context) }, Modifier.weight(1f))
            }
        } else if (left > 0) {
            Action(if (t.doneUnits == 0) "تشغيل الصفحات" else "متابعة من صفحة ${p?.pointer ?: ""}", { nav.play(t, false) }, Modifier.fillMaxWidth())
        } else {
            Text("خلصت حصة هذه الفترة", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            if (dayLeft > 0) Secondary("تكميل باقي اليوم · $dayLeft صفحة", { nav.play(t, true) }, Modifier.fillMaxWidth())
        }
        if (s.audioTree == null) Muted("أول تشغيل بيطلب منك تختار مجلد الصفحات الصوتية.")
        if (st.missing.isNotEmpty() && st.taskId == t.id) Muted("لا يوجد ملف للصفحات: ${st.missing.take(10).joinToString("، ")}")
    }
}

/** One row of a task list: start clock, title, duration circles. */
@Composable
fun TaskRow(t: Task, all: List<Task>, now: Long, extra: String? = null, s: Snapshot? = null, status: Color? = null, highlight: Boolean = false,
            hideTime: Boolean = false, onClick: () -> Unit) {
    val routine = s != null && isRoutine(t, s.ruleMap, s.settings)
    val tag = s?.let { tagColor(colorIndexOf(t, it.settings)) }
    val look = s?.settings?.icons?.get(seriesOf(t))
    val style = s?.settings?.nameStyle ?: "name"
    val showBadge = look != null && style != "name"
    val showName = !showBadge || style != "icon"
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = status?.let { BorderStroke(2.dp, it) },
        colors = CardDefaults.cardColors(containerColor = when {
            status != null && (t.done || !t.open) -> status.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surface)
            routine -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surface
        }),
    ) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        if (tag != null) Box(Modifier.width(6.dp).fillMaxHeight().background(tag))
        Row(Modifier.padding(14.dp).weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!hideTime) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ClockIcon(if (t.planned > 0) Clock.minuteOfDay(t.planned) else null, 30.dp)
                Text(if (t.planned > 0) fmt12(Clock.minuteOfDay(t.planned)) else "—", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (t.done) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
                    else if (t.running) Icon(Icons.Filled.PlayCircle, null, tint = MaterialTheme.colorScheme.primary)
                    else if (t.spent > 0) Icon(Icons.Filled.PauseCircle, null, tint = MaterialTheme.colorScheme.primary)
                    if (showBadge) TaskBadge(look, tag)
                    if (showName) Text(t.title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    else if (t.phaseCount > 1 || t.isPlan) Text(t.title.substringAfter(": ", t.title).take(24), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val parent = t.parallelTo?.let { id -> all.firstOrNull { it.id == id } }
                val notes = buildList {
                    if (routine) add("روتين")
                    if (t.pinned && t.open) add("موعد ثابت")
                    if (parent != null && t.open) add("بالتوازي مع ${parent.baseTitle}")
                    if (t.overflow && t.open && !hideTime) add("ما وسعها وقت اليوم")
                    if (t.state == "skipped") add("ما انعملت")
                    if (extra != null) add(extra)
                }
                if (notes.isNotEmpty()) Muted(notes.joinToString(" · "))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DurationIcon(t.minutes, 20.dp)
                Text(durLabel(t.minutes), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }
    }
}

// ---------------- Tomorrow ----------------

@Composable
fun TomorrowTab(s: Snapshot, repo: Repository, nav: Nav, add: () -> Unit) {
    val tomorrow = s.today.plusDays(1)
    val entries = remember(s.tasks, s.rules, s.plans, s.settings, s.today) {
        Projector.project(tomorrow, 1, s.today, s.tasks, s.rules, s.plans, s.settings)[tomorrow].orEmpty()
    }
    val all = entries.map { it.task }
    var hideRoutine by remember { mutableStateOf(false) }
    val rules = s.ruleMap
    val shown = entries.filter { !hideRoutine || !isRoutine(it.task, rules, s.settings) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Panel {
                SwitchRow("إخفاء الروتين", hideRoutine) { hideRoutine = it }
                Text("${entries.size} مهام · ${durLabel(all.sumOf { it.minutes })}", style = MaterialTheme.typography.bodyLarge)
                if (entries.any { it.virtual }) {
                    Muted("هذا اقتراح التطبيق لبكرا. ثبّته أو عدّل أي مهمة.")
                    Action("تثبيت خطة بكرا", { nav.act { repo.materialize(tomorrow) } }, Modifier.fillMaxWidth())
                } else if (entries.isNotEmpty()) Muted("الخطة مثبّتة. الأوقات بتترتب تلقائياً بكرا.")
                Secondary("+ مهمة لبكرا", add, Modifier.fillMaxWidth())
            }
        }
        if (entries.isEmpty()) item { Muted("لا شيء لبكرا بعد.") }
        val prayer = s.settings.prayerMode
        val groups: List<Pair<Period?, List<Projector.Entry>>> = if (!prayer) listOf(null to shown) else {
            val ps = s.settings.periodsOn(tomorrow)
            fun keyOf(e: Projector.Entry) = e.task.period.ifEmpty { if (e.task.planned > 0) s.settings.periodAt(tomorrow, Clock.minuteOfDay(e.task.planned)) else "" }
            ps.map { p -> p to shown.filter { keyOf(it) == p.key } } + listOf(null to shown.filter { keyOf(it).isEmpty() })
        }
        for ((p, list) in groups) {
        if (prayer && (p != null || list.isNotEmpty())) item(key = "tp-" + (p?.key ?: "none")) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p?.name ?: "ما وسعها اليوم", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (p == null) Status.missed else MaterialTheme.colorScheme.primary)
                if (p != null) Text(fmt12(p.start), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f))
                Text("${list.size} · ${durLabel(list.sumOf { it.task.minutes })}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(list, key = { it.task.id }) { e ->
            val mode = s.settings.swipeAction.let { if (it == "later") "carry" else it }
            SwipeRow(start = null, deferMode = mode, defer = {
                val ruleId = e.task.ruleId
                nav.act {
                    val id = if (e.virtual && ruleId != null) repo.materializeRule(ruleId, tomorrow) else e.task.id
                    if (id != null) repo.deferTask(id, mode)
                }
            }) {
            TaskRow(e.task, all, s.now, extra = if (e.virtual) "مقترح" else null, s = s, hideTime = prayer && !e.task.pinned) {
                val ruleId = e.task.ruleId
                if (e.virtual && ruleId != null) nav.act {
                    val id = repo.materializeRule(ruleId, tomorrow)
                    val t = id?.let { repo.store.task(it) }
                    if (t != null) nav.openTask(t)
                } else nav.openTask(e.task)
            }
            }
        }
        }
    }
}

// ---------------- Reports ----------------

@Composable
fun ReportsTab(s: Snapshot, nav: Nav, backup: () -> Unit, restore: () -> Unit) {
    val ts = s.today.toString()
    val todayList = s.tasks.filter { it.day == ts && it.state != "skipped" }
    val week = (0 until 7).map { s.today.minusDays(it.toLong()).toString() }.toSet()
    val weekTasks = s.tasks.filter { it.day in week }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Panel {
                Text("اليوم", style = MaterialTheme.typography.titleMedium)
                Text("المخطط: ${durLabel(todayList.sumOf { it.minutes })}", style = MaterialTheme.typography.bodyLarge)
                Text("المنجز: ${todayList.count { it.done }} من ${todayList.size}", style = MaterialTheme.typography.bodyLarge)
                Text("الوقت الفعلي: ${durLabel((todayList.sumOf { Maths.spent(it, s.now) } / 60_000).toInt())}", style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            Panel {
                Text("آخر ٧ أيام", style = MaterialTheme.typography.titleMedium)
                Text("المهام المنجزة: ${weekTasks.count { it.done }}", style = MaterialTheme.typography.bodyLarge)
                Text("الوقت الفعلي: ${durLabel((weekTasks.sumOf { Maths.spent(it, s.now) } / 60_000).toInt())}", style = MaterialTheme.typography.bodyLarge)
                Text("المهام المتأخرة: ${s.tasks.count { it.open && it.day < ts }}", style = MaterialTheme.typography.bodyLarge)
            }
        }
        progressReport(s, nav)
        item {
            Panel {
                Text("البيانات", style = MaterialTheme.typography.titleMedium)
                Action("تصدير نسخة احتياطية", backup, Modifier.fillMaxWidth())
                Secondary("استعادة من ملف JSON", restore, Modifier.fillMaxWidth())
                Muted("المهام والأهداف محفوظة على هذا الجهاز. الاستعادة تقبل نسخ كل الإصدارات.")
            }
        }
    }
}
