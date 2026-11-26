package com.imad.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Everything the computer's screens read, from the store (the phone has the same idea in MainActivity.Snapshot). */
class Desk(
    val tasks: List<Task>, val goals: List<Goal>, val rules: List<Rule>, val logs: List<GoalLog>,
    val meta: Map<String, String>, val settings: Settings, val today: LocalDate, val now: Long,
) {
    val ruleMap by lazy { rules.associateBy { it.id } }
}

fun main(args: Array<String>) = application {
    val store = remember { MemoryStore() }
    val platform = remember { DesktopPlatform() }
    val repo = remember { Repository(store, platform).also { Repository.instance = it } }
    Window(
        onCloseRequest = ::exitApplication, title = "رحلة اليوم",
        state = rememberWindowState(width = 1440.dp, height = 900.dp),
    ) {
        App(repo, store, File(platform.filesDir, "data.json"), args.firstOrNull())
    }
}

@OptIn(FlowPreview::class)
@Composable
fun App(repo: Repository, store: MemoryStore, dataFile: File, startTab: String? = null) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var ready by remember { mutableStateOf(false) }

    // Start: the saved file first, then the same start-up the phone runs (seeds on first use, today's plan).
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (dataFile.exists()) runCatching { repo.load(dataFile.readText()) }.onFailure { snackbar.showSnackbar("ما قدرت أقرأ ملف البيانات: ${it.message}") }
            runCatching { repo.initialize() }
            runCatching { repo.refreshDay() }
        }
        ready = true
        // The start-up itself changed things (seeds, today's plan): write them once now…
        withContext(Dispatchers.IO) { runCatching { saveAtomically(dataFile, repo.backup()) } }
        // …then every change is written to the file a moment later (one write for a burst of changes).
        store.version.drop(1).debounce(800).collect {
            withContext(Dispatchers.IO) { runCatching { saveAtomically(dataFile, repo.backup()) } }
        }
    }

    val tasks by store.tasks().collectAsState(emptyList())
    val goals by store.goals().collectAsState(emptyList())
    val rules by store.rules().collectAsState(emptyList())
    val logs by store.logs().collectAsState(emptyList())
    val metas by store.metas().collectAsState(emptyList())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val metaMap = remember(metas) { metas.associate { it.key to it.value } }
    val settings = remember(metaMap) { Settings.from(metaMap) }
    val desk = Desk(tasks, goals, rules, logs, metaMap, settings, Clock.today(), now)

    val act: (suspend () -> Unit) -> Unit = { block ->
        scope.launch { runCatching { block() }.onFailure { snackbar.showSnackbar(it.message ?: "صار خطأ") } }
    }
    val openBackup: () -> Unit = {
        val text = chooseBackupFile()
        if (text != null) act { repo.restore(text); snackbar.showSnackbar("انفتحت النسخة الاحتياطية") }
    }

    var tab by remember { mutableIntStateOf(startTab?.toIntOrNull() ?: 0) }
    var panelFor by remember { mutableStateOf<Task?>(null) }
    Shell(desk, repo, act, ready, tab, { tab = it }, panelFor, { panelFor = it }, openBackup) {
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(24.dp))
    }
}

/** The window's content: theme, right-to-left, the rail, the chosen screen and the task panel. Also used for pictures (Shots.kt). */
@Composable
fun Shell(
    desk: Desk, repo: Repository, act: (suspend () -> Unit) -> Unit, ready: Boolean,
    tab: Int, setTab: (Int) -> Unit, panelFor: Task?, setPanel: (Task?) -> Unit, openBackup: () -> Unit,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
    PlannerTheme {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalThemeChoice provides ThemeChoice(mono = false, set = {}, calm = desk.meta["calm"] == "1"),
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Row(Modifier.fillMaxSize()) {
                    Rail(tab, setTab, openBackup)
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (!ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Nour(NourMood.Awake, size = 120.dp) }
                        else when (tab) {
                            0 -> TodayScreen(desk, repo, act, open = { setPanel(it) })
                            1 -> WeekScreen(desk, repo, act, open = { setPanel(it) })
                            2 -> GoalsScreen(desk)
                            else -> NotYet("المراجعة", "شاشة المراجعة جاية بالخطوة الجاية من نسخة الكمبيوتر.")
                        }
                    }
                }
                overlay()
            }
            panelFor?.let { t0 ->
                val t = desk.tasks.firstOrNull { it.id == t0.id } ?: t0
                TaskPanel(t, desk, repo, act, close = { setPanel(null) })
            }
        }
    }
}

/** Writes through a temporary file, so a crash mid-write never leaves half a file. */
fun saveAtomically(f: File, text: String) {
    val tmp = File(f.parentFile, f.name + ".tmp")
    tmp.writeText(text)
    java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
}

/** Windows' own open dialog, for a backup file made on the phone (Settings → النسخ الاحتياطي). */
fun chooseBackupFile(): String? {
    val d = java.awt.FileDialog(null as java.awt.Frame?, "افتح نسخة احتياطية من الجوال", java.awt.FileDialog.LOAD)
    d.setFilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
    d.isVisible = true
    val name = d.file ?: return null
    return runCatching { File(d.directory, name).readText() }.getOrNull()
}

/** The side rail: the app mark, the four drawn sections, and the backup button at the bottom. */
@Composable
fun Rail(selected: Int, select: (Int) -> Unit, openBackup: () -> Unit) {
    val labels = listOf("اليوم", "التخطيط", "الأهداف", "المراجعة")
    Column(
        Modifier.width(104.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(headerColor()), contentAlignment = Alignment.Center) {
            Nour(NourMood.Awake, size = 44.dp)
        }
        Spacer(Modifier.height(14.dp))
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Column(
                Modifier.width(84.dp).clip(RoundedCornerShape(16.dp)).clickable { select(i) }
                    .semantics { role = Role.Tab; this.selected = on }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier.width(64.dp).height(44.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) { NavIcon(i, on) }
                Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "فتح نسخة\nمن الجوال", fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = openBackup)
                .semantics { role = Role.Button }.padding(8.dp),
        )
    }
}

/** A section that is not on the computer yet, said plainly. */
@Composable
fun NotYet(title: String, text: String) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(title, "")
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { NourNote(NourMood.Awake, title, text) }
    }
}
