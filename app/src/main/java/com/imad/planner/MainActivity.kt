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
    val audioTree: Uri?, val today: LocalDate, val now: Long,
) {
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
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val metaMap = metas.associate { it.key to it.value }
    val snap = Snapshot(
        tasks, goals, projects, rules, logs, Plans.parse(metaMap["plans"]), Months.parse(metaMap["months"]),
        Settings.from(metaMap), metaMap["quranAudio"]?.let(Uri::parse), Clock.today(), now,
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
    var daySheet by remember { mutableStateOf(false) }
    var closeDay by remember { mutableStateOf(false) }
    var restoreText by remember { mutableStateOf<String?>(null) }
    var addMenu by remember { mutableStateOf(false) }

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
                        Text(listOf("مهام اليوم", "خطة بكرا", "الخطة", "التقارير")[tab], style = MaterialTheme.typography.titleLarge)
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
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                listOf(
                    "اليوم" to Icons.Filled.Today, "بكرا" to Icons.Filled.DateRange,
                    "الخطة" to Icons.Filled.Flag, "التقارير" to Icons.Filled.BarChart,
                ).forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> TodayTab(snap, repo, nav, showList = { daySheet = true }, add = { creating = emptyTask(today) })
                1 -> TomorrowTab(snap, repo, nav, add = { creating = emptyTask(today.plusDays(1)) })
                2 -> PlanTab(snap, nav)
                else -> ReportsTab(snap, backup = { exporter.launch("planner-backup-$today.json") }, restore = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) })
            }
        }
    }

    if (daySheet) DaySheet(snap, nav, close = { daySheet = false }, add = { creating = emptyTask(today) }, closeDay = { daySheet = false; closeDay = true })
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
                postpone = { act { repo.postpone(t.id); editor = null } },
            ))
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
            acceptTraining = { ids -> act { repo.acceptGoals(ids) } })
    }
    if (settingsOpen) SettingsDialog(snap.settings, close = { settingsOpen = false }, set = { k, v -> act { repo.setSetting(k, v) } },
        test = { Alarms.ring(context, 4999, "تجربة المنبه", "هكذا يرن عند انتهاء مهمة وبدء التالية", null, false, snap.settings.insistent) })
    if (closeDay) CloseDayDialog(snap.tasks.filter { it.day == today.toString() && it.state != "skipped" }, now, close = { closeDay = false },
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

// ---------------- Today: the current task, alone ----------------

fun currentTask(todays: List<Task>): Task? =
    todays.firstOrNull { it.running } ?: todays.firstOrNull { it.open && (it.spent > 0 || it.doneUnits > 0) && it.parallelTo == null }
    ?: todays.filter { it.open && it.parallelTo == null && !it.overflow }.minByOrNull { if (it.planned == 0L) Long.MAX_VALUE else it.planned }

fun todayTasks(s: Snapshot) = s.tasks.filter { it.day == s.today.toString() && it.state != "skipped" }
    .sortedWith(compareBy({ if (it.planned == 0L) Long.MAX_VALUE else it.planned }, { it.created }))

@Composable
fun TodayTab(s: Snapshot, repo: Repository, nav: Nav, showList: () -> Unit, add: () -> Unit) {
    val todays = todayTasks(s)
    val current = currentTask(todays)
    val left = todays.count { it.open }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            if (current != null) CurrentPanel(current, todays, s, repo, nav)
            else Panel {
                Text("لا توجد مهمة الآن", style = MaterialTheme.typography.titleLarge)
                Muted(if (todays.isEmpty()) "أضف مهمة والتطبيق يرتّب وقتها." else "خلصت مهام اليوم.")
            }
        }
        item { Secondary(if (left > 0) "باقي مهام اليوم · $left" else "مهام اليوم", showList, Modifier.fillMaxWidth()) }
        item { Secondary("+ إضافة مهمة", add, Modifier.fillMaxWidth()) }
    }
}

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
        Text(t.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { nav.openTask(t) })
        TimeLine(t.planned, t.minutes, end = true)
        if (t.pinned) Muted("موعد ثابت")
        if (members.isNotEmpty() && !t.running && t.spent == 0L) Muted(members.joinToString(" · ") { "${it.name} ${it.sets}×" })
        if (!upcoming) {
            Text(
                if (timeUp) "انتهى الوقت" else timerLabel(remaining),
                fontSize = 44.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                color = if (timeUp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            LinearProgressIndicator(progress = { (spent.toFloat() / (t.minutes * 60_000f)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (timeUp) Action("إنهاء", { nav.act { repo.finish(t.id) } }, Modifier.weight(1f))
            else Secondary(if (isMemorize) "حفظتها ✓" else "إنهاء", { nav.act { repo.finish(t.id) } }, Modifier.weight(1f))
            Secondary("تمديد ${s.settings.extend} د", { nav.act { repo.extend(t.id, s.settings.extend) } }, Modifier.weight(1f))
        }
        Secondary("تأجيل للغد", { nav.act { repo.postpone(t.id) } }, Modifier.fillMaxWidth())
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
fun TaskRow(t: Task, all: List<Task>, now: Long, extra: String? = null, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ClockIcon(if (t.planned > 0) Clock.minuteOfDay(t.planned) else null, 30.dp)
                Text(if (t.planned > 0) fmt12(Clock.minuteOfDay(t.planned)) else "—", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (t.done) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
                    else if (t.running) Icon(Icons.Filled.PlayCircle, null, tint = MaterialTheme.colorScheme.primary)
                    else if (t.spent > 0) Icon(Icons.Filled.PauseCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Text(t.title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
                val parent = t.parallelTo?.let { id -> all.firstOrNull { it.id == id } }
                val notes = buildList {
                    if (t.pinned && t.open) add("موعد ثابت")
                    if (parent != null && t.open) add("بالتوازي مع ${parent.baseTitle}")
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

@Composable
fun DaySheet(s: Snapshot, nav: Nav, close: () -> Unit, add: () -> Unit, closeDay: () -> Unit) {
    val todays = todayTasks(s)
    val ts = s.today.toString()
    val rolled = s.tasks.filter { it.day == ts && it.state == "skipped" && it.note == "رُحّلت لليوم التالي" }
    val late = s.tasks.filter { it.open && it.day < ts }
    var showLate by remember { mutableStateOf(false) }
    Sheet("مهام اليوم", close) {
        Muted("${todays.size} مهام · ${todays.count { it.done }} منجزة · ${durLabel(todays.filter { it.open }.sumOf { (Maths.remaining(it, s.now) / 60_000).toInt() })} متبقية")
        if (todays.isEmpty()) Text("لا توجد مهام لهذا اليوم.", style = MaterialTheme.typography.bodyLarge)
        todays.forEach { t -> TaskRow(t, todays, s.now) { nav.openTask(t) } }
        if (rolled.isNotEmpty()) Muted("رُحّلت لبكرا لأنها ما وسعت اليوم: " + rolled.joinToString("، ") { it.baseTitle })
        if (late.isNotEmpty()) {
            Secondary(if (showLate) "إخفاء المهام المتأخرة" else "المهام المتأخرة · ${late.size}", { showLate = !showLate }, Modifier.fillMaxWidth())
            if (showLate) late.forEach { t -> TaskRow(t, late, s.now, extra = "من ${t.day}") { nav.openTask(t) } }
        }
        Action("+ إضافة مهمة", add, Modifier.fillMaxWidth())
        Secondary("إغلاق اليوم", closeDay, Modifier.fillMaxWidth())
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Panel {
                Text("${entries.size} مهام · ${durLabel(all.sumOf { it.minutes })}", style = MaterialTheme.typography.bodyLarge)
                if (entries.any { it.virtual }) {
                    Muted("هذا اقتراح التطبيق لبكرا. ثبّته أو عدّل أي مهمة.")
                    Action("تثبيت خطة بكرا", { nav.act { repo.materialize(tomorrow) } }, Modifier.fillMaxWidth())
                } else if (entries.isNotEmpty()) Muted("الخطة مثبّتة. الأوقات بتترتب تلقائياً بكرا.")
                Secondary("+ مهمة لبكرا", add, Modifier.fillMaxWidth())
            }
        }
        if (entries.isEmpty()) item { Muted("لا شيء لبكرا بعد.") }
        items(entries, key = { it.task.id }) { e ->
            TaskRow(e.task, all, s.now, extra = if (e.virtual) "مقترح" else null) {
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

// ---------------- Reports ----------------

@Composable
fun ReportsTab(s: Snapshot, backup: () -> Unit, restore: () -> Unit) {
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
