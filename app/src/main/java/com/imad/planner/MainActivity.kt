@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.imad.planner

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
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
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    private lateinit var repo: Repository
    /** Page or action asked by a notification: "month" | "week" | "day" | "startQuran:<task>". */
    private val route = mutableStateOf<String?>(null)

    private fun readRoute(intent: Intent?) {
        intent ?: return
        intent.getStringExtra("open")?.let { route.value = it }
        intent.getStringExtra("startQuran")?.let { route.value = "startQuran:$it" }
        intent.getIntExtra("notification", 0).takeIf { it != 0 }?.let { androidx.core.app.NotificationManagerCompat.from(this).cancel(it) }
        intent.removeExtra("open"); intent.removeExtra("startQuran")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readRoute(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Alarms.channels(this)
        repo = Repository.get(this)
        lifecycleScope.launch { runCatching { repo.initialize() } }
        // A notification's action runs once: not again when Android restores the screen or it is opened from recent apps.
        if (savedInstanceState == null && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) readRoute(intent)
        else intent.removeExtra("startQuran")
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val ui = remember { getSharedPreferences("ui", MODE_PRIVATE) }
                var mono by remember { mutableStateOf(ui.getBoolean("mono", false)) }
                var calm by remember { mutableStateOf(ui.getBoolean("calm", false)) }
                CompositionLocalProvider(LocalThemeChoice provides ThemeChoice(mono, { v -> ui.edit().putBoolean("mono", v).apply(); mono = v },
                    calm, { v -> ui.edit().putBoolean("calm", v).apply(); calm = v })) {
                PlannerTheme(mono) {
                    // The status bar takes the header's colour, with light icons on it.
                    val bar = headerColor()
                    androidx.compose.runtime.SideEffect {
                        @Suppress("DEPRECATION")
                        window.statusBarColor = bar.toArgb()
                        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
                    }
                    // Asked once on this phone; after the right password it never asks again.
                    val prefs = remember { getSharedPreferences("lock", MODE_PRIVATE) }
                    var unlocked by remember { mutableStateOf(prefs.getBoolean("unlocked", false)) }
                    if (unlocked) Planner(repo, route.value) { route.value = null }
                    else LockScreen { prefs.edit().putBoolean("unlocked", true).apply(); unlocked = true }
                }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::repo.isInitialized) lifecycleScope.launch {
            runCatching { repo.refreshDay() }
            runCatching { repo.pomoCheck() }
            runCatching { repo.syncQuran() }
        }
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
    val meta: Map<String, String> = emptyMap(),
) {
    val targets: Map<String, Map<String, Target>> by lazy { Targets.parse(meta["targets"]) }
    val quranToday: QuranReport? by lazy {
        meta["quran:$today"]?.let { runCatching { QuranAdapter.parseReport(org.json.JSONObject(it), 0) }.getOrNull() }
    }
    fun targetOf(t: Task): Target? = targets[Targets.dayKey(LocalDate.parse(t.day))]?.get(seriesOf(t))
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
    val quickMove: (Task) -> Unit = {},
    val details: (Task) -> Unit = {},
    val finish: (Task) -> Unit = {},
    /** Starts a task (second argument: through the play/pause button); a task linked to a Quran ward opens it. */
    val start: (Task, Boolean) -> Unit = { _, _ -> },
    val pomo: () -> Unit = {},
    val monthPlan: () -> Unit = {},
    val weekPlan: () -> Unit = {},
    val dayPlan: (LocalDate) -> Unit = {},
    val quran: (Task) -> Unit = {},
    val syncQuran: () -> Unit = {},
    val sports: () -> Unit = {},
    val continueLater: (Task) -> Unit = {},
    val yearParts: (YearTarget) -> Unit = {},
    val ig: () -> Unit = {},
    /** Drops a yearly plan and opens its settings to build it again ("g:…", "p:…" or "r:<rule>"). */
    val resetYear: (String) -> Unit = {},
    val prayers: () -> Unit = {},
)

@Composable
fun Planner(repo: Repository, route: String? = null, routeDone: () -> Unit = {}) {
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
    // Parsed once per change of the settings, not on every clock tick.
    val metaMap = remember(metas) { metas.associate { it.key to it.value } }
    val parsedPlans = remember(metaMap) { Plans.parse(metaMap["plans"]) }
    val parsedMonths = remember(metaMap) { Months.parse(metaMap["months"]) }
    val parsedSettings = remember(metaMap) { Settings.from(metaMap) }
    val snap = Snapshot(
        tasks, goals, projects, rules, logs, parsedPlans, parsedMonths,
        parsedSettings, metaMap["quranAudio"]?.let(Uri::parse), Clock.today(), now, events,
        closedToday = metaMap.containsKey("closed:${Clock.today()}"),
        undoLabel = metaMap["undoLabel"].orEmpty(), undoAt = metaMap["undoAt"]?.toLongOrNull() ?: 0, meta = metaMap,
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
    var moveFor by remember { mutableStateOf<Task?>(null) }
    var detailsFor by remember { mutableStateOf<Task?>(null) }
    var finishFor by remember { mutableStateOf<Task?>(null) }
    var quranFor by remember { mutableStateOf<Task?>(null) }
    var pomoOpen by remember { mutableStateOf(false) }
    var monthPlan by remember { mutableStateOf(false) }
    var weekPlan by remember { mutableStateOf(false) }
    var dayPlan by remember { mutableStateOf<LocalDate?>(null) }
    var chainFor by remember { mutableStateOf<String?>(null) }
    var sportsOpen by remember { mutableStateOf(false) }
    var ytFor by remember { mutableStateOf<Task?>(null) }
    var splitFor by remember { mutableStateOf<Task?>(null) }
    var contFor by remember { mutableStateOf<Task?>(null) }
    var partsFor by remember { mutableStateOf<YearTarget?>(null) }
    var igOpen by remember { mutableStateOf(false) }
    var watchNew by remember { mutableStateOf(false) }
    var moveDayFor by remember { mutableStateOf<Task?>(null) }
    var closeDay by remember { mutableStateOf(false) }
    var restoreText by remember { mutableStateOf<String?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var reportFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var noteFor by remember { mutableStateOf<Task?>(null) }
    var fixFor by remember { mutableStateOf<Task?>(null) }
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
    // The clock ticks every second only while a timer runs; otherwise twice a minute (the screen redraws on each tick).
    val fast by androidx.compose.runtime.rememberUpdatedState(tasks.any { it.running } || pomoState(snap).running)
    LaunchedEffect(Unit) {
        var ticks = 0
        while (true) {
            delay(1000); ticks++
            if (fast || ticks % 30 == 0) now = System.currentTimeMillis()
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

    val quranPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) act { repo.syncQuran(); snackbar.showSnackbar("انسمح الربط. ابدأ المهمة مرة ثانية.") }
    }
    /**
     * Starting a task linked to a ward opens the ward first. If the Quran app cannot open it, the timer does not start
     * (the planner owns its timer) and the reason is shown, with a way to start without the Quran app.
     */
    val startTask: (Task, Boolean) -> Unit = { t, viaTimer ->
        act {
            val fresh = repo.store.task(t.id) ?: t
            val link = snap.settings.quranLinks[seriesOf(t)]
            val pausing = viaTimer && fresh.running
            val yt = snap.settings.ytLinks[seriesOf(t)]
            val begin: suspend () -> Unit = {
                // A watch task has no timer: it opens the player at the episode and second reached.
                if (yt != null) context.startActivity(PlayerActivity.intent(context, seriesOf(t), t.baseTitle, t.id))
                else if (viaTimer) repo.timer(t.id) else repo.startNow(t.id)
            }
            if (link == null || pausing) begin()
            else {
                val st = if (link.missing) null else QuranAdapter.open(context, link, fresh)
                when (st) {
                    QuranState.OK -> begin()
                    QuranState.NO_PERMISSION -> {
                        if (snackbar.showSnackbar(st.text, actionLabel = "السماح", withDismissAction = true) == SnackbarResult.ActionPerformed)
                            quranPermission.launch(QuranContract.PERMISSION)
                    }
                    else -> {
                        val msg = st?.text ?: "الورد المرتبط انحذف من تطبيق القرآن، اختار ورد ثاني من تفاصيل المهمة."
                        if (snackbar.showSnackbar(msg, actionLabel = "ابدأ بدون القرآن", withDismissAction = true) == SnackbarResult.ActionPerformed) begin()
                    }
                }
            }
        }
    }

    // Pages and actions asked by notifications.
    LaunchedEffect(route) {
        when {
            route == null -> {}
            route == "month" -> monthPlan = true
            route == "week" -> weekPlan = true
            route.startsWith("tab:") -> tab = route.removePrefix("tab:").toIntOrNull() ?: 0
            route == "watch" -> watchNew = true
            route == "ig" -> { tab = 3; igOpen = true }
            route == "goal" -> newGoal = true
            // Used when checking the app on the emulator.
            route == "menu" -> menuFor = repo.store.tasksOn(Clock.today().toString()).firstOrNull { it.open && it.ruleId != null }
            route == "postpone" -> postponeFor = repo.store.tasksOn(Clock.today().toString()).firstOrNull { it.open && it.ruleId != null }
            route == "askclose" -> closeDay = true
            route == "prayers" -> prayerOpen = true
            route == "rule48" -> { repo.store.allRules().firstOrNull { it.weekdays == 0 && it.everyHours >= 48 }?.let { r -> ruleEditor = r } }
            route == "closeday" -> { repo.closeDay("close"); snackbar.showSnackbar("انسكّر يوم ${repo.lastClosed}") }
            route == "reopen" -> { val n = repo.reopenDay(); snackbar.showSnackbar("انفتح اليوم من جديد، رجعت $n مهام") }
            route.startsWith("yeargoal:") -> route.removePrefix("yeargoal:").let { k ->
                yearTarget = if (k.startsWith("g:")) repo.store.goal(k.removePrefix("g:"))?.let { YearTarget(goal = it) }
                else Plans.parse(repo.store.meta("plans")).firstOrNull { it.id == k.removePrefix("p:") }?.let { YearTarget(plan = it) }
            }
            route == "igtest" -> IgWeb.fetchAll(context, listOf("instagram"))["instagram"].let { a ->
                snackbar.showSnackbar(if (a == null) "ما وصل رد" else "رد إنستغرام ${a.code}: " + (if (a.code == 200 && Ig.parsePosts("instagram", a.body) != null) "البوستات وصلت ✓" else a.body.take(80)),
                    duration = androidx.compose.material3.SnackbarDuration.Indefinite)
            }
            route == "day" -> {
                val d = if (Clock.minuteOfDay(System.currentTimeMillis()) < snap.settings.dayStart) Clock.today() else Clock.today().plusDays(1)
                runCatching { repo.prepareDay(d) }; dayPlan = d
            }
            route.startsWith("startQuran:") -> repo.store.task(route.removePrefix("startQuran:"))?.let { startTask(it, false) }
        }
        if (route != null) routeDone()
    }

    // Points and levels, from the saved history only.
    val xpOn = snap.meta["xp"] != "0"
    val summary = remember(tasks, goals, logs, parsedSettings.groups, parsedSettings.icons) { Progress.summary(tasks, goals, logs, parsedSettings, Clock.today()) }
    var progressOpen by remember { mutableStateOf(false) }
    var levelUp by remember { mutableStateOf(false) }
    val seenLevel = metaMap["levelSeen"]?.toIntOrNull()
    LaunchedEffect(summary.total.level, seenLevel, xpOn, metas.isNotEmpty()) {
        if (!xpOn || metas.isEmpty()) return@LaunchedEffect
        // The first time, the level reached by the past is just noted: no party for history.
        if (seenLevel == null) runCatching { repo.setSetting("levelSeen", summary.total.level.toString()) }
        else if (summary.total.level > seenLevel) levelUp = true
    }
    val cheerFor: suspend (String) -> Unit = { id -> repo.store.task(id)?.let { Cheer.fire(Progress.pointsOf(it), it.baseTitle) } }

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
        quickMove = { moveFor = it },
        details = { detailsFor = it },
        finish = { t -> if ((snap.targetOf(t)?.goal ?: 0) > 0) finishFor = t else act { repo.finish(t.id); cheerFor(t.id) } },
        start = { t, viaTimer -> startTask(t, viaTimer) },
        pomo = { pomoOpen = true },
        monthPlan = { monthPlan = true },
        weekPlan = { weekPlan = true },
        dayPlan = { d -> act { repo.prepareDay(d) }; dayPlan = d },
        quran = { quranFor = it },
        sports = { sportsOpen = true },
        continueLater = { contFor = it },
        yearParts = { partsFor = it },
        prayers = { prayerOpen = true },
        resetYear = { key -> act {
            repo.resetYear(key)
            yearTarget = null
            when {
                key.startsWith("g:") -> goalEditor = repo.store.goal(key.removePrefix("g:"))
                key.startsWith("p:") -> planEditor = Plans.parse(repo.store.meta("plans")).firstOrNull { it.id == key.removePrefix("p:") }
                key.startsWith("r:") -> ruleEditor = repo.store.rule(key.removePrefix("r:"))
            }
            snackbar.showSnackbar("انمسحت الخطة القديمة. عبّي إعداداتها من جديد.")
        } },
        ig = { igOpen = true },
        syncQuran = { act { val st = repo.syncQuran(); if (st != QuranState.OK) snackbar.showSnackbar(st.text) } },
        look = { series, color, icon -> act { repo.setLook(series, color, icon) } },
    )

    val today = snap.today
    val emptyTask = { day: LocalDate -> Task(title = "", minutes = 30, day = day.toString()) }

    val tabNames = listOf("اليوم", "التخطيط", "الأهداف", "المراجعة")
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val minute = Clock.minuteOfDay(now)
            val periods = if (snap.settings.prayerMode) snap.settings.periodsOn(today) else emptyList()
            PageHeader(
                title = tabNames[tab],
                sub = when (tab) { 1 -> "بكرا والأسبوع والشهر"; 2 -> "السنة وطريق كل هدف"; else -> dayLabel(today) },
                actions = {
                    if (tab == 0 && xpOn) LevelChip(summary.total) { tab = 2 }
                    if (tab == 3) HeaderButton("إنستغرام بلا ريلز", { igOpen = true }) { Text("الفيد", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    Box {
                        HeaderButton("إضافة", { addMenu = true }) { Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White) }
                        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                            DropdownMenuItem(text = { Text("مهمة جديدة") }, onClick = { addMenu = false; creating = emptyTask(if (tab == 1) today.plusDays(1) else today) })
                            DropdownMenuItem(text = { Text("هدف جديد") }, onClick = { addMenu = false; newGoal = true })
                            DropdownMenuItem(text = { Text("▶ قائمة مشاهدة يوتيوب") }, onClick = { addMenu = false; watchNew = true })
                        }
                    }
                    HeaderButton("الإعدادات", { settingsOpen = true }) { Icon(Icons.Filled.Settings, contentDescription = null, tint = Color.White) }
                },
                below = {
                    if (tab == 0 && periods.isNotEmpty() && minute >= snap.settings.dayStart && minute < snap.settings.dayEnd) {
                        val nowKey = snap.settings.periodAt(today, minute)
                        val i = periods.indexOfFirst { it.key == nowKey }
                        val cur = periods.getOrNull(i)
                        val next = periods.getOrNull(i + 1)
                        val line = listOfNotNull(cur?.name, next?.let { "باقي ${untilLabel(minute, it.start)} لل${it.name.removePrefix("ال")}" }).joinToString(" · ")
                        DayArc(periods, minute, line) { pick, close ->
                            ArcCard(snap, pick, periods, minute, if (xpOn) summary else null, nav,
                                add = { key -> close(); creating = emptyTask(today).copy(period = key) }, close = close)
                        }
                    }
                },
            )
        },
        bottomBar = { JourneyNav(tabNames, tab) { tab = it } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            androidx.compose.animation.Crossfade(tab, animationSpec = androidx.compose.animation.core.tween(if (calmMotion()) 0 else 220), label = "tab") { page ->
                Box(Modifier.fillMaxSize()) {
                    when (page) {
                        0 -> TodayTab(snap, repo, nav, add = { key -> creating = emptyTask(today).copy(period = key) }, askClose = { closeDay = true },
                            closeDay = { mode -> act { val n = repo.closeDay(mode); snackbar.showSnackbar((if (mode == "carry") "ترحّلت $n مهام لليوم اللي بعده" else "انسكّر اليوم") + if (repo.lastClosed != Clock.today()) " (يوم ${repo.lastClosed?.let { dayLabel(it) }})" else "") } },
                            goTomorrow = { tab = 1 })
                        1 -> PlanningTab(snap, nav) { TomorrowTab(snap, repo, nav, add = { creating = emptyTask(today.plusDays(1)) }) }
                        2 -> GoalsTab(snap, nav, if (xpOn) summary else null) { progressOpen = true }
                        else -> ReportsTab(snap, nav, backup = { exporter.launch("planner-backup-$today.json") }, restore = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }, repo = repo)
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp)) { CheerLayer(xpOn) }
        }
    }

    if (progressOpen) ProgressSheet(summary) { progressOpen = false }
    if (levelUp) LevelUpDialog(summary) { levelUp = false; act { repo.setSetting("levelSeen", summary.total.level.toString()) } }

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
                quran = { quranFor = fresh },
                quranLabel = snap.settings.quranLinks[seriesOf(fresh)]?.let { "ورد القرآن: ${it.wardName.ifBlank { QuranContract.modeName(it.mode) }}" } ?: "ربط بورد قرآن",
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
            delete = { creating = null }, actions = null, watchList = { watchNew = true })
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
        saveRule = { r -> act { repo.saveRule(r); newGoal = false } }, watchList = { watchNew = true })
    if (watchNew) WatchListSheet(today, close = { watchNew = false }) { title, url, list, from, total, perDay, end, minutes ->
        act { repo.addWatchList(title, url, list, from, total, perDay, end, minutes); snackbar.showSnackbar("انضافت «$title» لخطة السنة") }
    }
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
    fixFor?.let { t -> FixTimeDialog(t, Maths.spent(t, System.currentTimeMillis()), close = { fixFor = null }) { m -> act { repo.setSpent(t.id, m); fixFor = null; snackbar.showSnackbar("انحفظ الوقت: ${durLabel(m)}") } } }
    noteFor?.let { t -> TextDialog("ملاحظة على «${t.baseTitle}»", "ليش تأخرت أو ما خلصتها؟", close = { noteFor = null }) { text -> act { repo.addNote(t.id, text); noteFor = null } } }
    postponeFor?.let { t -> PostponeChoice(t, recurring = t.ruleId != null, close = { postponeFor = null }) { text, mode -> act { repo.postpone(t.id, text, mode); postponeFor = null } } }
    if (prayerOpen) PrayerSheet(snap.settings, today, close = { prayerOpen = false }, set = { k, v -> act { repo.setSetting(k, v) } })
    placeFor?.let { t ->
        PlaceDialog(t.baseTitle, snap.settings.places[seriesOf(t)], snap.settings.groupPeriods[t.category], close = { placeFor = null }) { v -> act { repo.setPlace(seriesOf(t), v) } }
    }
    periodFor?.let { t -> PeriodPicker(snap.settings.periodsOn(today), Clock.minuteOfDay(now), close = { periodFor = null }) { k -> act { repo.moveToPeriod(t.id, k) } } }
    moveFor?.let { t ->
        QuickMoveDialog(t, snap.settings.periodsOn(LocalDate.parse(t.day)), close = { moveFor = null },
            move = { k -> act { repo.assignPeriod(t.id, k) } }, tomorrow = { postponeFor = t }, more = { menuFor = t })
    }
    detailsFor?.let { t -> DetailsDialog(t, close = { detailsFor = null }) { text -> act { repo.setDetails(t.id, text) } } }
    finishFor?.let { t ->
        FinishAmountDialog(t, snap.targetOf(t) ?: Target(), close = { finishFor = null }) { n ->
            act { repo.addTargetDone(t.day, seriesOf(t), n); repo.finish(t.id); cheerFor(t.id) }
        }
    }
    quranFor?.let { t -> QuranLinkSheet(t, snap.settings.quranLinks[seriesOf(t)], close = { quranFor = null }) { l -> act { repo.setQuranLink(seriesOf(t), l) } } }
    if (pomoOpen) PomodoroSheet(snap, close = { pomoOpen = false }) { a -> act { repo.pomo(a) } }
    if (monthPlan) MonthPlanSheet(snap, close = { monthPlan = false }, chain = { chainFor = it }) { k, sr, t -> act { repo.setTarget(k, sr, t) } }
    if (weekPlan) WeekPlanSheet(snap, close = { weekPlan = false }, WeekActions(
        save = { k, sr, t -> act { repo.setTarget(k, sr, t) } },
        add = { w, sr -> act { repo.addToWeek(w, sr) } },
        remove = { w, sr -> act { repo.removeFromWeek(w, sr) } },
        routine = { sr, on -> act { repo.setWeeklyRoutine(sr, on) } },
        moveSession = { sr, a, b -> act { repo.moveSession(sr, a, b) } },
        prepare = { w -> act { repo.prepareWeek(w) } },
        chain = { chainFor = it },
    ))
    if (sportsOpen) SportsSheet(snap, close = { sportsOpen = false }, ops = SportOps(
        saveRecord = { g -> act { repo.saveRecord(g) } },
        deleteRecord = { id -> act { repo.deleteRecord(id) } },
        pause = { id, on -> act { repo.setPaused(id, on) } },
        icon = { key, v -> act { repo.setLook(listOf(key), null, v) } },
        saveSession = { r, m -> act { repo.saveSession(r, m) } },
        deleteSession = { id -> act { repo.deleteSession(id) } },
        set = { k, v -> act { repo.setSetting(k, v) } },
        weekPlan = { weekPlan = true },
    ))
    // The feed is Instagram's own site without Reels and Explore (its own screen, so back works page by page).
    if (igOpen) {
        val ctx = LocalContext.current
        LaunchedEffect(Unit) { ctx.startActivity(android.content.Intent(ctx, IgFocusActivity::class.java)); igOpen = false }
    }
    ytFor?.let { t -> YtLinkDialog(t, snap.settings.ytLinks[seriesOf(t)], close = { ytFor = null }) { l -> act { repo.setYt(seriesOf(t), l) } } }
    splitFor?.let { t -> SplitDialog(t, close = { splitFor = null }) { n -> act { repo.splitTask(t.id, n) } } }
    contFor?.let { t -> PeriodPicker(snap.settings.periodsOn(today), Clock.minuteOfDay(now), close = { contFor = null }) { k -> act { repo.continueLater(t.id, k) } } }
    partsFor?.let { y ->
        val g = y.goal; val p = y.plan
        YearPartsSheet(g?.name ?: p!!.title, g?.unit ?: p!!.unit, g?.reference ?: p!!.total.toDouble(),
            YearMonth.from(today), snap.settings.let { YearPart.parseAll(snap.meta["yearParts"])[y.key].orEmpty() },
            monthValue = { ym -> snap.months[Months.key(y.key, ym.toString())]?.value },
            close = { partsFor = null }, save = { list -> act { repo.setYearParts(y.key, list) } },
            editMonth = { ym -> monthEdit = MonthTarget(y.key, ym, g?.name ?: p!!.title, g?.unit ?: p!!.unit, snap.months[Months.key(y.key, ym.toString())]?.value ?: 0.0, g?.lower ?: false) })
    }
    chainFor?.let { sr -> GoalChainSheet(snap, sr, close = { chainFor = null }, openWeek = { weekPlan = true }, openMonth = { monthPlan = true }) }
    moveDayFor?.let { t -> MoveDayDialog(t, today, close = { moveDayFor = null }) { d -> act { repo.moveToDay(t.id, d) } } }
    dayPlan?.let { d ->
        DayPlanSheet(snap, d, close = { dayPlan = null }, assign = { id, k -> act { repo.assignPeriod(id, k) } },
            routine = { t, on -> act { repo.setRoutine(seriesOf(t), on); if (on && t.period.isEmpty()) repo.assignPeriod(t.id, snap.settings.periodAt(d, snap.settings.dayStart)) } },
            confirm = { act { val n = repo.confirmDayPlan(d); snackbar.showSnackbar(if (n > 0) "انثبتت الخطة، وترحّلت $n مهام لليوم اللي بعده" else "انثبتت الخطة") } },
            details = { detailsFor = it },
            addSeries = { series -> act { repo.addToDay(d, series) } },
            newTask = { creating = emptyTask(d) })
    }
    keepRestFor?.let { t -> KeepRestDialog(t, close = { keepRestFor = null }) { m -> act { repo.stopKeepRest(t.id, m); keepRestFor = null } } }
    if (groupsOpen) GroupsSheet(snap, close = { groupsOpen = false }, save = { list -> act { repo.saveGroups(list) } },
        setPeriod = { g, p -> act { repo.setGroupPeriod(g, p) } })
    menuFor?.let { t ->
        val prayerDay = snap.settings.prayerMode
        TaskMenu(t, close = { menuFor = null }, ops = TaskOps(
            start = { startTask(t, false) },
            finish = { nav.finish(t) },
            movePeriod = if (prayerDay) ({ moveFor = t }) else null,
            moveDay = { moveDayFor = t },
            postpone = { postponeFor = t },
            duplicate = { act { repo.duplicate(t.id) } },
            split = { splitFor = t },
            youtube = { ytFor = t },
            details = { detailsFor = t },
            edit = { editor = t },
            place = if (prayerDay) ({ placeFor = t }) else null,
            quran = { quranFor = t },
            report = { reportFor = seriesOf(t) to t.baseTitle },
            focus = if (t.open) ({ act { repo.focus(t.id); pomoOpen = true } }) else null,
            fixTime = { fixFor = t },
            note = { noteFor = t },
            close = { act { repo.deferTask(t.id, "close") } },
            delete = { act { repo.delete(t.id) } },
        ), yt = snap.settings.ytLinks[seriesOf(t)], domain = domainOf(t, snap.settings), look = snap.settings.icons[seriesOf(t)],
            goal = (t.goalId ?: t.ruleId?.let { id -> rules.firstOrNull { it.id == id }?.goalId })?.let { gid -> goals.firstOrNull { it.id == gid } }?.let { g ->
                GoalLink(g.name, "${num(g.current)} من ${num(g.reference)}") { goalEditor = g }
            })
    }
    if (settingsOpen) SettingsDialog(snap.settings, close = { settingsOpen = false }, set = { k, v -> act { repo.setSetting(k, v) } }, openGroups = { groupsOpen = true }, openPrayers = { prayerOpen = true }, xpOn = xpOn,
        test = { Alarms.ring(context, 4999, "تجربة المنبه", "هكذا يرن عند انتهاء مهمة وبدء التالية", null, false) })
    if (closeDay) CloseDayDialog(snap.tasks.filter { it.day == today.toString() && it.state != "skipped" }, now, close = { closeDay = false },
        tomorrow = snap.tasks.filter { it.day == today.plusDays(1).toString() },
        finish = { mode -> act { val n = repo.closeDay(mode); closeDay = false; snackbar.showSnackbar((if (mode == "carry") "ترحّلت $n مهام لليوم اللي بعده" else "انسكّر اليوم") + if (repo.lastClosed != Clock.today()) " (يوم ${repo.lastClosed?.let { dayLabel(it) }})" else "") } })
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
    val done = Brand.mint
    /** Needs a look: warm orange text, never red on the planner. */
    val missed = Brand.lanternText
}

/** done (green), running (accent), closed without doing (a quiet grey) or nothing. No red on the day's page. */
@Composable
fun statusColor(t: Task, periodOver: Boolean): Color? = when {
    t.done -> Status.done
    t.state == "skipped" -> MaterialTheme.colorScheme.outline
    t.running -> MaterialTheme.colorScheme.primary
    else -> null
}

/** One colour per prayer period, so each part of the day reads as its own block while scrolling. */
@Composable
fun periodAccent(key: String): Color {
    if (LocalThemeChoice.current.mono) return when (key) { "fajr", "asr", "isha" -> Color(0xFF8A8A8A); else -> Color(0xFF4A4A4A) }
    return when (key) {
        "fajr" -> Color(0xFF5B6BC0)
        "dhuhr" -> Color(0xFFE0A030)
        "asr" -> Color(0xFFDB7A3C)
        "maghrib" -> Color(0xFFC0587A)
        "isha" -> Color(0xFF3B4A8C)
        else -> MaterialTheme.colorScheme.outline
    }
}

/** How the day's tasks are shown: by prayer periods, or all in one plain list. Kept on the phone. */
object TodayView {
    var all by mutableStateOf(false)
    fun load(context: android.content.Context) { all = context.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).getBoolean("allList", false) }
    fun set(context: android.content.Context, v: Boolean) { all = v; context.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).edit().putBoolean("allList", v).apply() }
}

@Composable
fun TodayTab(s: Snapshot, repo: Repository, nav: Nav, add: (String) -> Unit, askClose: () -> Unit, closeDay: (String) -> Unit, goTomorrow: () -> Unit) {
    if (s.settings.prayerMode) PeriodToday(s, repo, nav, add, askClose, closeDay, goTomorrow)
    else HoursToday(s, repo, nav, { add("") }, askClose, closeDay, goTomorrow)
}

@Composable
fun NightPanel(s: Snapshot, todays: List<Task>, beforeWake: Boolean, closeDay: (String) -> Unit, goTomorrow: () -> Unit, reopen: () -> Unit = {}) {
    val left = todays.count { it.open }
    if (beforeWake) Panel {
        NourNote(NourMood.Sleep, "وقت النوم", "يومك بيبدأ الساعة ${fmt12(s.settings.dayStart)}")
    } else Panel {
        NourNote(NourMood.Sleep, "انتهى اليوم", "${todays.count { it.done }} منجزة من ${todays.size}")
        if (left > 0 && !s.closedToday) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("ترحيل لبكرا", { closeDay("carry") }, Modifier.weight(1f))
                Secondary("إغلاق اليوم", { closeDay("close") }, Modifier.weight(1f))
            }
        }
        if (s.closedToday && Clock.nowMinute() < s.settings.dayEnd) {
            Muted("انسكّر اليوم وهو لسا ما خلص؟")
            Secondary("افتح اليوم من جديد", { reopen() }, Modifier.fillMaxWidth())
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
    val all = s.tasks.filter { it.day == ts && !(it.state == "skipped" && it.note in setOf("اندمجت مع جلسة بكرا", "مكرر", "فوق حد التمارين")) }
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
        nav.start(t, false)
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
                beforeWake || afterSleep -> NightPanel(s, todays, beforeWake, closeDay, goTomorrow) { nav.act { repo.reopenDay() } }
                else -> NextPanel(s, nav, todays, periods, nowKey, start) { picked = nowKey; scope.launch { listState.animateScrollToItem(2) } }
            }
        }
        // The prayer sheet is about to run out: said a few days before, not after.
        s.settings.prayersUntil?.let { until ->
            val left = java.time.temporal.ChronoUnit.DAYS.between(s.today, until)
            if (left <= 3) item {
                Text(if (left < 0) "جدول الصلاة خلص، الأوقات تقريبية. اضغط لتحط الجديد." else "جدول الصلاة بيخلص بعد $left أيام. اضغط لتحط الشهر الجاي.",
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.star.copy(alpha = 0.3f).compositeOver(MaterialTheme.colorScheme.surface))
                        .clickable(onClick = nav.prayers).padding(12.dp),
                    style = MaterialTheme.typography.bodyLarge)
            }
        }
        item { UndoRow(s, nav) }
        item { ViewSwitch() }
        if (TodayView.all) {
            // Everything of today in one list, no times and no periods.
            val shown = todays.filter { !HideRoutine.on || !isRoutine(it, rules, s.settings) }
            val openOnes = shown.filter { it.open }
            val doneOnes = shown.filter { !it.open }
            if (openOnes.isEmpty()) item { Muted("ما ضل مهام مفتوحة اليوم ✓") }
            items(openOnes, key = { "a-" + it.id }) { t ->
                SwipeRow(start = if (!t.running) ({ start(t) }) else null, defer = { nav.act { repo.deferTask(t.id, s.settings.swipeAction) } }, deferMode = s.settings.swipeAction) {
                    TaskRow(t, todays, s.now, s = s, status = statusColor(t, false), hideTime = true, onLongClick = { nav.quickMove(t) }, onDoubleClick = { nav.details(t) }, onDone = { nav.finish(t) }) { nav.taskMenu(t) }
                }
            }
            if (doneOnes.isNotEmpty()) {
                item { Text("خلصانة · ${doneOnes.size}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
                items(doneOnes, key = { "ad-" + it.id }) { t -> TaskRow(t, todays, s.now, s = s, status = statusColor(t, false), hideTime = true, onDoubleClick = { nav.details(t) }, onDone = { nav.finish(t) }) { nav.taskMenu(t) } }
            }
        }
        if (!TodayView.all) for (p in periods) {
            val list = all.filter { keyOf(it) == p.key && (!HideRoutine.on || !isRoutine(it, rules, s.settings)) }
            val over = p.end <= minute
            val open = selected == p.key
            item(key = "h-${p.key}") {
                PeriodTitle(p, list, over, nowKey == p.key, open, loads[p.key],
                    click = { picked = if (open) "" else p.key },
                    longClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); add(p.key) },
                    add = if (over) null else ({ add(p.key) }))
            }
            if (open) {
                if (list.isEmpty()) item(key = "e-${p.key}") { Muted("   ما في مهام بهاي الفترة. لتضيف: زر + على اسمها.") }
                items(list, key = { "t-" + it.id }) { t ->
                    val color = statusColor(t, over)
                    // The period's colour runs down beside its tasks, so it is clear where one part of the day ends.
                    PeriodLane(periodAccent(p.key)) {
                        if (t.open) SwipeRow(
                            start = if (!t.running) ({ start(t) }) else null,
                            defer = { nav.act { repo.deferTask(t.id, s.settings.swipeAction) } }, deferMode = s.settings.swipeAction,
                        ) { TaskRow(t, todays, s.now, s = s, status = color, highlight = true, hideTime = !t.pinned, onLongClick = { nav.quickMove(t) }, onDoubleClick = { nav.details(t) }, onDone = { nav.finish(t) }) { nav.taskMenu(t) } }
                        else TaskRow(t, todays, s.now, s = s, status = color, highlight = true, hideTime = !t.pinned, onDoubleClick = { nav.details(t) }, onDone = { nav.finish(t) }) { nav.taskMenu(t) }
                    }
                }
            }
        }
        val loose = if (TodayView.all) emptyList() else todays.filter { it.open && it.period.isEmpty() }
        if (loose.isNotEmpty()) {
            item { Text("ما إلها فترة لسا · ${loose.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
            items(loose, key = { "t-" + it.id }) { t ->
                SwipeRow(start = { start(t) }, defer = { nav.act { repo.deferTask(t.id, s.settings.swipeAction) } }, deferMode = s.settings.swipeAction) {
                    TaskRow(t, todays, s.now, extra = whyNoRoom(t, loads.values.toList(), minute, s.now), s = s, hideTime = true, onLongClick = { nav.quickMove(t) }, onDoubleClick = { nav.details(t) }, onDone = { nav.finish(t) }) { nav.taskMenu(t) }
                }
            }
        }
        val late = s.tasks.filter { it.open && it.day < ts }
        if (late.isNotEmpty()) {
            item { Text("متأخرة من أيام سابقة · ${late.size}", style = MaterialTheme.typography.titleMedium) }
            items(late, key = { "late-" + it.id }) { t -> TaskRow(t, late, s.now, extra = "من ${t.day}", s = s, onLongClick = { nav.taskMenu(t) }) { nav.taskMenu(t) } }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Secondary("رتّب اليوم بالسحب", { nav.dayPlan(s.today) }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Secondary("+ مهمة", { add(selected.takeIf { it.isNotEmpty() } ?: nowKey) }, Modifier.weight(1f))
                    Secondary("إغلاق اليوم", askClose, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Top of the page when nothing is running: the next task of this period, and how long since the last one ended. */
@Composable
fun NextPanel(s: Snapshot, nav: Nav, todays: List<Task>, periods: List<Period>, nowKey: String, start: (Task) -> Unit, showPeriod: () -> Unit) {
    val period = periods.firstOrNull { it.key == nowKey }
    val here = todays.filter { it.open && it.period == nowKey && it.parallelTo == null }
    val next = here.firstOrNull()
    val lastEnd = todays.filter { it.done && it.finishedAt > 0 }.maxOfOrNull { it.finishedAt }
    val idle = lastEnd?.let { ((s.now - it) / 60_000).toInt() }
    Panel {
        if (next != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DomainTile(domainOf(next, s.settings), 64.dp, s.settings.icons[seriesOf(next)]?.takeIf { s.settings.nameStyle != "name" })
                Column(Modifier.weight(1f)) {
                    Text("هلأ · ${period?.name ?: ""}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = warmText())
                    Text(next.title, fontFamily = DisplayFont, fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                    Muted(durLabel(next.minutes) + if (here.size > 1) " · وباقي ${here.size - 1} بهاي الفترة" else "")
                }
                AppleButton(s, nav.pomo)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("ابدأ", { start(next) }, Modifier.weight(1.4f))
                Secondary("مش هلأ", showPeriod, Modifier.weight(1f))
            }
        } else {
            val after = periods.getOrNull(periods.indexOfFirst { it.key == nowKey } + 1)
            val n = after?.let { a -> todays.count { it.open && it.period == a.key } } ?: 0
            NourNote(NourMood.Happy, "خلصت مهام ${period?.name ?: "هاي الفترة"} ✓",
                after?.let { "الجاية: ${it.name} الساعة ${fmt12(it.start)}" + if (n > 0) " · $n مهام" else "" }, 96.dp)
        }
        if (idle != null && next != null && idle >= 1) Muted("صارلك ${durLabel(idle.coerceAtLeast(1))} من آخر مهمة خلّصتها")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PeriodTitle(p: Period, list: List<Task>, over: Boolean, now: Boolean, open: Boolean, load: PeriodLoad?, click: () -> Unit, longClick: () -> Unit,
                add: (() -> Unit)? = null) {
    val done = list.count { it.done }
    val accent = periodAccent(p.key)
    val allDone = list.isNotEmpty() && done == list.size
    // The part of the day you are in gets a warm card; the others are plain lines, so the eye lands on now.
    val bg = when {
        now -> accent.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surface)
        open -> MaterialTheme.colorScheme.surface
        else -> Color.Transparent
    }
    val arrow by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, androidx.compose.animation.core.tween(if (calmMotion()) 0 else 200), label = "arrow")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg).combinedClickable(onClick = click, onLongClick = longClick)
            .padding(horizontal = 12.dp, vertical = if (now) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(if (now) 14.dp else 12.dp).clip(RoundedCornerShape(7.dp)).background(accent))
        Text(p.name, fontFamily = if (now) DisplayFont else BodyFont, fontSize = if (now) 21.sp else 17.sp, fontWeight = FontWeight.Bold,
            color = if (over && !now) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        Text(fmt12(p.start) + if (now) " · الآن" else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f))
        if (list.isNotEmpty()) Text(if (allDone) "${list.size}/${list.size} ✓" else "$done/${list.size}", style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (allDone) FontWeight.Bold else FontWeight.Normal, color = if (allDone) doneColor() else MaterialTheme.colorScheme.onSurfaceVariant)
        if (add != null) Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = add),
            contentAlignment = Alignment.Center,
        ) { Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (LocalThemeChoice.current.mono) MaterialTheme.colorScheme.onSurface else accent) }
        Text("▾", Modifier.graphicsLayer { rotationZ = arrow }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
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
                beforeWake || afterSleep -> NightPanel(s, todays, beforeWake, closeDay, goTomorrow) { nav.act { repo.reopenDay() } }
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
                    start = if (!t.running) ({ nav.start(t, false); scope.launch { delay(150); listState.animateScrollToItem(0) } }) else null,
                    defer = { nav.act { repo.deferTask(t.id, s.settings.swipeAction) } }, deferMode = s.settings.swipeAction,
                ) { TaskRow(t, todays, s.now, s = s, onDoubleClick = { nav.details(t) }, onDone = { nav.finish(t) }) { nav.taskMenu(t) } }
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
    val over = Maths.over(t, now)
    val parallel = todays.filter { it.parallelTo == t.id && it.open }
    val upcoming = !t.running && t.spent == 0L && t.doneUnits == 0 && t.planned > now + 60_000
    val isReview = s.plans.any { it.kind == "review" && t.ruleId == "plan-${it.id}" }
    val isMemorize = s.plans.any { it.kind == "memorize" && t.ruleId == "plan-${it.id}" }
    val members = s.rules.firstOrNull { it.id == seriesOf(t) }?.members?.let(Members::parse).orEmpty()
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when { t.running -> "شغّالة هلأ"; upcoming -> "التالية"; t.spent > 0 -> "متوقفة مؤقتاً"; else -> "هلأ" },
                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                color = warmText(),
            )
            AppleButton(s, nav.pomo)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DomainTile(domainOf(t, s.settings), 56.dp, s.settings.icons[seriesOf(t)]?.takeIf { s.settings.nameStyle != "name" })
            Text(t.title, fontFamily = DisplayFont, fontSize = 25.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).clickable { nav.openTask(t) })
            if (t.running) Nour(NourMood.Awake, size = 52.dp, label = "شغّالة")
        }
        s.plans.firstOrNull { "plan-${it.id}" == t.ruleId && it.kind == "cycle" }?.let { p ->
            PlanMath.stageDetails(p, 0).takeIf { it.isNotBlank() }?.let { Muted(it) }
        }
        if (!s.settings.prayerMode || t.pinned) TimeLine(t.planned, t.minutes, end = true)
        else Muted(listOfNotNull(Prayer.names[t.period]?.let { "فترة $it" }, durLabel(t.minutes)).joinToString(" · "))
        if (t.pinned) Muted("موعد ثابت")
        s.targetOf(t)?.let { tg ->
            if (tg.text.isNotBlank()) Text("هدف اليوم: ${tg.text}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            if (tg.goal > 0) ProgressLine(tg.done, tg.goal)
        }
        if (t.details.isNotBlank()) Text(t.details, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { nav.details(t) })
        s.settings.quranLinks[seriesOf(t)]?.let { l -> Muted("📖 ${QuranContract.modeName(l.mode)} · ${l.wardName}" + (s.quranToday?.ward(l.wardId)?.let { w -> QuranAdapter.mainNumber(l.mode, w.totals).let { (n, u) -> " · اليوم $n $u" } } ?: "")) }
        if (members.isNotEmpty() && !t.running && t.spent == 0L) Muted(members.joinToString(" · ") { "${it.name} ${it.sets}×" })
        if (!upcoming) {
            if (s.settings.timerStyle == "analog" && !timeUp) {
                AnalogTimer(spent, t.minutes * 60_000L)
            } else {
                Text(
                    // Past the estimate the clock keeps counting what is over it.
                    if (timeUp) (if (over >= 60_000) "+" + timerLabel(over) else "انتهى الوقت") else timerLabel(remaining),
                    fontSize = 52.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    color = if (timeUp) ctaColor() else MaterialTheme.colorScheme.onSurface,
                )
                LinearProgressIndicator(progress = { (spent.toFloat() / (t.minutes * 60_000f)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), color = ctaColor(), trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {})
            }
        }
        if (timeUp && over >= 60_000) Muted("تجاوزت التقدير (${durLabel(t.minutes)}) بـ${durLabel((over / 60_000).toInt())}. الوقت الحقيقي كله بينحسب.")
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
            { nav.start(t, true) }, Modifier.fillMaxWidth(),
        )
        if (timeUp) {
            Action(if (isMemorize) "حفظتها ✓" else "أنجزتها ✓", { nav.finish(t) }, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Secondary("تمديد ${s.settings.extend} د", { nav.act { repo.extend(t.id, s.settings.extend) } }, Modifier.weight(1f))
                Secondary("إيقاف وحفظ المتبقي", { nav.keepRest(t) }, Modifier.weight(1f))
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Secondary(if (isMemorize) "حفظتها ✓" else "إنهاء", { nav.finish(t) }, Modifier.weight(1f))
            Secondary("تمديد ${s.settings.extend} د", { nav.act { repo.extend(t.id, s.settings.extend) } }, Modifier.weight(1f))
        }
        if (s.settings.prayerMode && !t.isPlan && Maths.spent(t, now) >= 60_000) Secondary("كمّل الباقي بفترة ثانية", { nav.continueLater(t) }, Modifier.fillMaxWidth())
        s.settings.ytLinks[seriesOf(t)]?.let { l -> Muted("▶ يوتيوب: حلقة ${l.index + 1}" + (if (l.total > 0) " من ${l.total}" else "") + if (l.finished) " · خلصت السلسلة" else "") }
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

/**
 * One row of a task list: the domain's drawing (or the icon chosen for it), the name and a short line, and the round
 * tick at the end. Tap = the task card, long press = move, double tap = details, tick = done.
 */
@Composable
fun TaskRow(t: Task, all: List<Task>, now: Long, extra: String? = null, s: Snapshot? = null, status: Color? = null, highlight: Boolean = false,
            hideTime: Boolean = false, onLongClick: (() -> Unit)? = null, onDoubleClick: (() -> Unit)? = null, onDone: (() -> Unit)? = null, onClick: () -> Unit) {
    val routine = s != null && isRoutine(t, s.ruleMap, s.settings)
    val tag = s?.let { tagColor(colorIndexOf(t, it.settings)) }
    val look = s?.settings?.icons?.get(seriesOf(t))
    val style = s?.settings?.nameStyle ?: "name"
    val showName = look == null || style != "icon"
    val domain = s?.let { domainOf(t, it.settings) } ?: domainOf(t.category, null)
    val finished = !t.open
    val haptic = LocalHapticFeedback.current
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Card(
        modifier = Modifier.fillMaxWidth().pressScale(press)
            .combinedClickable(interactionSource = press, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick, onLongClick = onLongClick, onDoubleClick = onDoubleClick),
        shape = RoundedCornerShape(18.dp),
        border = if (t.running) BorderStroke(2.dp, ctaColor()) else null,
        colors = CardDefaults.cardColors(containerColor = when {
            finished -> MaterialTheme.colorScheme.surface.copy(alpha = 0.7f).compositeOver(MaterialTheme.colorScheme.background)
            routine -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.surface
        }),
    ) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        if (tag != null) Box(Modifier.width(5.dp).fillMaxHeight().background(tag))
        Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp).weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!hideTime) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ClockIcon(if (t.planned > 0) Clock.minuteOfDay(t.planned) else null, 26.dp)
                Text(if (t.planned > 0) fmt12(Clock.minuteOfDay(t.planned)) else "—", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DomainTile(domain, 44.dp, look?.takeIf { style != "name" }, done = finished)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                if (t.running) Text("▶ شغّالة هلأ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = warmText())
                else if (t.open && t.spent > 0) Text("⏸ متوقفة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showName) Text(t.title, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp,
                    color = if (finished) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (t.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                else if (t.phaseCount > 1 || t.isPlan) Text(t.title.substringAfter(": ", t.title).take(24), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val parent = t.parallelTo?.let { id -> all.firstOrNull { it.id == id } }
                val notes = buildList {
                    add(durLabel(t.minutes))
                    if (routine) add("روتين")
                    if (t.pinned && t.open) add("موعد ثابت")
                    if (parent != null && t.open) add("بالتوازي مع ${parent.baseTitle}")
                    if (t.overflow && t.open && !hideTime) add("ما وسعها وقت اليوم")
                    if (t.state == "skipped") add("ما انعملت")
                    if (t.done && t.phaseCount == 1 && t.pct in 1..99) add("منجز جزئياً ${t.pct}٪")
                    s?.targetOf(t)?.let { tg -> if (tg.text.isNotBlank() || tg.goal > 0) add(listOfNotNull(tg.text.ifBlank { null }, tg.goal.takeIf { it > 0 }?.let { "${tg.done}/$it" }).joinToString(" ")) }
                    s?.settings?.quranLinks?.get(seriesOf(t))?.let { l ->
                        add("📖 " + (s.quranToday?.ward(l.wardId)?.let { w -> QuranAdapter.mainNumber(l.mode, w.totals).let { (n, u) -> "$n $u" } } ?: l.wardName))
                    }
                    if (extra != null) add(extra)
                }
                Text(notes.joinToString(" · "), fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (t.details.isNotBlank()) Text(t.details, fontSize = 13.sp, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (t.state == "skipped") Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Text("–", fontSize = 22.sp, color = MaterialTheme.colorScheme.outline) }
            else if (t.done || onDone != null) DoneCircle(t.done, "علّم «${t.baseTitle}» منجزة", onDone?.let { f -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); f() } })
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
            // Once tomorrow is being arranged by hand, a task is only in a period if it was put there.
            val byHand = s.meta["planning:$tomorrow"] != null || s.meta["dayPlanned:$tomorrow"] != null
            fun keyOf(e: Projector.Entry) = e.task.period.ifEmpty {
                if (byHand && !e.virtual) "" else if (e.task.planned > 0) s.settings.periodAt(tomorrow, Clock.minuteOfDay(e.task.planned)) else ""
            }
            ps.map { p -> p to shown.filter { keyOf(it) == p.key } } + listOf(null to shown.filter { keyOf(it).isEmpty() })
        }
        for ((p, list) in groups) {
        if (prayer && (p != null || list.isNotEmpty())) item(key = "tp-" + (p?.key ?: "none")) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p?.name ?: "لسا ما توزّعت", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (p == null) warmText() else MaterialTheme.colorScheme.primary)
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
                    if (t != null) nav.taskMenu(t)
                } else nav.taskMenu(e.task)
            }
            }
        }
        }
    }
}

// ---------------- Reports ----------------

@Composable
fun ReportsTab(s: Snapshot, nav: Nav, backup: () -> Unit, restore: () -> Unit, repo: Repository? = null) {
    val context = LocalContext.current
    val ts = s.today.toString()
    val todayList = s.tasks.filter { it.day == ts && it.state != "skipped" }
    val week = (0 until 7).map { s.today.minusDays(it.toLong()).toString() }.toSet()
    val weekTasks = s.tasks.filter { it.day in week }
    val hints = remember(s.tasks, s.rules, s.meta["estSkip"]) { estimateHints(s) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (hints.isNotEmpty()) item {
            Panel {
                Text("اقتراحات للتقدير", style = MaterialTheme.typography.titleMedium)
                Muted("من وقتك الحقيقي بآخر الجلسات. ما بيتغير إشي إلا إذا وافقت.")
                hints.forEach { h ->
                    Text("«${h.rule.title}»: آخر ${h.sessions} جلسات أخذت بالعادة ${durLabel(h.suggested)}، والتقدير ${durLabel(h.rule.minutes)}.", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Action("خليها ${durLabel(h.suggested)}", { nav.act { repo?.setEstimate(h.rule.id, h.suggested) } }, Modifier.weight(1f))
                        Secondary("لأ", { nav.act { repo?.skipEstimate(h.rule.id, h.suggested) } }, Modifier.weight(1f))
                    }
                }
            }
        }
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
        if (s.settings.styleOf("reports") == "charts") chartReports(s, nav)
        item { QuranReportPanel(s, nav.syncQuran) }
        item { PomodoroReport(s, s.today) { i, label -> nav.act { Repository.get(context).pomoLabel(ts, i, label) } } }
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


private const val LOCK_HASH = "a710fd3fa7929d26dc93a2ce85b6a413456ff65c3637037886588e7f6d3b59f9"

private fun sha256(t: String) = java.security.MessageDigest.getInstance("SHA-256").digest(t.toByteArray()).joinToString("") { "%02x".format(it) }

@Composable
fun LockScreen(open: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(headerColor()).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.surface).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Nour(if (wrong) NourMood.Dim else NourMood.Awake, size = 120.dp, label = "نور")
            Text("أهلا فيك", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(value = text, onValueChange = { text = it; wrong = false }, singleLine = true, label = { Text("كلمة المرور") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), isError = wrong,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            if (wrong) Text("كلمة المرور غلط", color = MaterialTheme.colorScheme.error)
            Action("فتح", { if (sha256(text.trim()) == LOCK_HASH) open() else wrong = true }, Modifier.fillMaxWidth())
        }
    }
}


/** The real time worked, set by hand (estimate stays as it was). */
@Composable
fun FixTimeDialog(t: Task, spentMs: Long, close: () -> Unit, save: (Int) -> Unit) {
    var text by remember { mutableStateOf(((spentMs + 30_000) / 60_000).toString()) }
    val m = digits(text).toIntOrNull()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = close,
        title = { Text("تصحيح وقت «${t.baseTitle}»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Muted("قديش اشتغلت عليها فعلاً؟ التقدير (${durLabel(t.minutes)}) بيضل زي ما هو.")
                Field(text, "الوقت الحقيقي بالدقائق", { text = it }, numeric = true)
            }
        },
        confirmButton = { TextButton(onClick = { m?.let(save) }, enabled = m != null && m in 0..1440) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
    )
}


/**
 * What is left of the day against what is still planned in it: free minutes of the periods still to come (prayers,
 * work and the like taken out, each minute once) and the remaining time of open tasks (a task done alongside a
 * training is not counted again).
 */
@Composable
fun CapacityLine(s: Snapshot, todays: List<Task>, loads: List<PeriodLoad>, minute: Int, arrange: () -> Unit) {
    val ahead = loads.filter { it.period.end > minute }
    val free = ahead.sumOf { it.free }
    val open = todays.filter { it.open && it.parallelTo == null && (it.period.isEmpty() || ahead.any { l -> l.period.key == it.period }) }
    val need = open.sumOf { ((Maths.remaining(it, s.now) + 59_999) / 60_000).toInt() }
    if (need == 0) return
    val short = need - free
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (short > 0) Status.missed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("باقي اليوم: متاح ${durLabel(free)} · المطلوب ${durLabel(need)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        when {
            short > 0 -> {
                Text("ناقصك ${durLabel(short)}. اختار شو يتأجّل أو يصغر.", style = MaterialTheme.typography.bodyMedium, color = Status.missed)
                Text("رتّب باقي اليوم", Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = arrange).padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            free - need < 15 -> Muted("مليان تقريباً: ما في مجال لأي تأخير.")
            else -> Muted("بيضل فاضي ${durLabel(free - need)}.")
        }
    }
}

/** Why a task did not get a period today, in a few words. */
fun whyNoRoom(t: Task, loads: List<PeriodLoad>, minute: Int, now: Long): String {
    val need = ((Maths.remaining(t, now) + 59_999) / 60_000).toInt()
    val best = loads.filter { it.period.end > minute }.maxByOrNull { it.balance }
    return when {
        best == null -> "اليوم خلص. اسحبها لبكرا."
        best.balance >= need -> "في مكان بفترة ${best.period.name} (فاضي ${durLabel(best.balance)}): اضغط مطوّلاً وانقلها."
        else -> "بدها ${durLabel(need)}، وأكبر فراغ باقي ${durLabel(best.balance.coerceAtLeast(0))} (${best.period.name}). صغّرها أو أجّلها."
    }
}


class EstimateHint(val rule: Rule, val suggested: Int, val sessions: Int)

/** Recurring tasks whose real time keeps differing from their estimate (the latest 8 finished sessions). */
fun estimateHints(s: Snapshot): List<EstimateHint> {
    val skip = runCatching { org.json.JSONObject(s.meta["estSkip"] ?: "{}") }.getOrDefault(org.json.JSONObject())
    val done = s.tasks.filter { it.done && it.spent > 0 && it.ruleId != null }.groupBy { it.ruleId!! }
    return s.rules.filter { it.active && !it.id.startsWith("plan-") && it.sets <= 1 }.mapNotNull { r ->
        val last = done[r.id].orEmpty().sortedByDescending { it.finishedAt }.take(8)
        val m = Estimates.suggest(r.minutes, last.map { ((it.spent + 30_000) / 60_000).toInt() }) ?: return@mapNotNull null
        if (skip.optInt(r.id, -1) == m) null else EstimateHint(r, m, last.size)
    }
}


/** "بالمواقيت" or "كل المهام", one tap; routine can still be hidden. */
@Composable
fun ViewSwitch() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { TodayView.load(context) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Segmented(listOf("بالمواقيت", "كل المهام"), if (TodayView.all) 1 else 0, Modifier.weight(1f)) { TodayView.set(context, it == 1) }
        FilterChip(selected = HideRoutine.on, onClick = { HideRoutine.on = !HideRoutine.on }, label = { Text("بلا روتين", fontSize = 14.sp) })
    }
}

/** A task inside a period: a thin line of the period's colour beside it. */
@Composable
fun PeriodLane(accent: Color, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Box(Modifier.padding(start = 3.dp).width(3.dp).fillMaxHeight().background(accent.copy(alpha = 0.55f), RoundedCornerShape(2.dp)))
        Box(Modifier.padding(start = 9.dp).weight(1f)) { content() }
    }
}


/**
 * The card under the arc. For "now": where the day stands (done, real time, points today), what is running or next,
 * with start. For a picked moment: its period, how far it is from now, its tasks (each opens its card), and adding one there.
 */
@Composable
fun ColumnScope.ArcCard(s: Snapshot, pick: ArcPick, periods: List<Period>, minute: Int, sum: Progress.Summary?, nav: Nav,
                        add: (String) -> Unit, close: () -> Unit) {
    val todays = todayTasks(s)
    val p = periods.lastOrNull { pick.minute >= it.start } ?: periods.first()
    val here = todays.filter { it.period == p.key }
    val accent = periodAccent(p.key)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(accent))
        Text(if (pick.now) "هلأ · ${p.name}" else p.name, Modifier.weight(1f), fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        val rel = when {
            pick.now -> fmt12(minute)
            pick.minute > minute -> "بعد ${durLabel(pick.minute - minute)}"
            pick.minute < minute -> "من ${durLabel(minute - pick.minute)}"
            else -> "هلأ"
        }
        Text("${fmt12(pick.minute)} · $rel".takeIf { !pick.now } ?: rel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = close, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Close, contentDescription = "إغلاق", modifier = Modifier.size(18.dp)) }
    }
    Text("${fmt12(p.start)} – ${fmt12(p.end)}" + if (here.isNotEmpty()) " · ${here.count { it.done }} من ${here.size} منجزة" else " · فاضية",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (pick.now) {
        // The whole day in three numbers.
        val done = todays.count { it.done }
        val realMin = todays.sumOf { Maths.spent(it, s.now) } / 60_000
        val pts = todays.sumOf { Progress.pointsOf(it, s.now) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArcStat("$done/${todays.size}", "منجزة اليوم", Modifier.weight(1f))
            ArcStat(durLabel(realMin.toInt()), "وقت حقيقي", Modifier.weight(1f))
            if (sum != null) ArcStat("+$pts", "نقاط اليوم", Modifier.weight(1f))
        }
        if (sum != null) {
            Bar(sum.total.fraction, onBand = false)
            Muted("مستوى ${ar(sum.total.level)} · ${sum.total.name} · باقي ${ar(sum.total.left)} نقطة")
        }
        val running = todays.firstOrNull { it.running }
        val next = running ?: todays.firstOrNull { it.open && it.period == p.key } ?: todays.firstOrNull { it.open }
        next?.let { t ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DomainTile(domainOf(t, s.settings), 40.dp, s.settings.icons[seriesOf(t)]?.takeIf { s.settings.nameStyle != "name" })
                Column(Modifier.weight(1f).clickable { nav.taskMenu(t) }) {
                    Text(if (t.running) "شغّالة هلأ" else "الجاية", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = warmText())
                    Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                if (!t.running) Action("ابدأ", { close(); nav.start(t, false) })
            }
        } ?: Muted("ما ضل ولا مهمة مفتوحة اليوم ✓")
    } else {
        here.take(4).forEach { t ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { nav.taskMenu(t) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DomainTile(domainOf(t, s.settings), 34.dp, s.settings.icons[seriesOf(t)]?.takeIf { s.settings.nameStyle != "name" }, done = !t.open)
                Text(t.title, Modifier.weight(1f), maxLines = 1, color = if (t.open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (t.done) "✓" else durLabel(t.minutes), fontSize = 13.sp, color = if (t.done) doneColor() else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (here.size > 4) Muted("و${here.size - 4} كمان")
        val free = (p.end - maxOf(p.start, minute)).coerceAtLeast(0) - here.filter { it.open }.sumOf { Maths.remaining(it, s.now).toInt() / 60_000 }
        if (p.end > minute) Muted(if (free > 0) "فيها وقت فاضي تقريباً ${durLabel(free)}" else "مليانة")
        Secondary("+ مهمة بال${p.name.removePrefix("ال")}", { add(p.key) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ArcStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
