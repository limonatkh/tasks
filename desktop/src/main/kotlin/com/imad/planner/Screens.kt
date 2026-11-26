package com.imad.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.roundToInt

// The computer's screens, drawn from the approved design (docs/design-board/desktop). They read the same data and
// call the same Repository actions as the phone.

private val Ink = Color(0xFF1C2340)
private val Muted = Color(0xFF5F6787)

/** Which period a task belongs to on a day (tasks finished before they had one go where their time fell). */
private fun keyOf(t: Task, s: Settings): String =
    t.period.ifEmpty { if (!t.open && t.planned > 0) s.periodAt(LocalDate.parse(t.day), Clock.minuteOfDay(t.planned)) else "" }

private fun dayTasks(d: Desk, day: LocalDate): List<Task> = d.tasks
    .filter { it.day == day.toString() && it.state != "skipped" }
    .sortedWith(compareBy({ !it.running }, { if (it.planned == 0L) Long.MAX_VALUE else it.planned }, { it.created }))

// ---------------------------------------------------------------- Today

@Composable
fun TodayScreen(d: Desk, repo: Repository, act: (suspend () -> Unit) -> Unit, open: (Task) -> Unit) {
    val s = d.settings
    val minute = Clock.minuteOfDay(d.now)
    val periods = s.periodsOn(d.today)
    val nowKey = s.periodAt(d.today, minute)
    val todays = dayTasks(d, d.today)
    val nowPeriod = periods.firstOrNull { it.key == nowKey }
    val nextPeriod = periods.firstOrNull { it.start > minute }
    val line = listOfNotNull(nowPeriod?.name, nextPeriod?.let { "باقي ${untilLabel(minute, it.start)} لل${it.name.removePrefix("ال")}" }).joinToString(" · ")
    val sum = remember(d.tasks, d.goals, d.logs) { Progress.summary(d.tasks, d.goals, d.logs, s, d.today, d.now) }

    Column(Modifier.fillMaxSize()) {
        PageHeader("اليوم", dayLabel(d.today), actions = { LevelChip(sum.total) {} }) {
            DayArc(periods, minute, line, Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (todays.isEmpty()) NourNote(NourMood.Sleep, "ما في مهام اليوم", "افتح نسخة الجوال من الزر تحت القائمة، أو استنّى التزامن.")
                periods.forEach { p ->
                    val list = todays.filter { keyOf(it, s) == p.key }
                    PeriodBlock(p, list, now = p.key == nowKey, d, act, open)
                }
                val loose = todays.filter { t -> periods.none { it.key == keyOf(t, s) } }
                if (loose.isNotEmpty()) {
                    Text("بلا وقت", fontWeight = FontWeight.Bold, color = Muted, modifier = Modifier.padding(horizontal = 8.dp))
                    loose.forEach { TaskRowDesk(it, d, act, open) }
                }
            }
            Column(Modifier.widthIn(min = 320.dp, max = 420.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val next = todays.firstOrNull { it.running } ?: todays.firstOrNull { it.open && keyOf(it, s) == nowKey } ?: todays.firstOrNull { it.open }
                if (next != null) NowCard(next, d, act, open)
                DayNumbers(todays, sum, d)
            }
        }
    }
}

@Composable
private fun PeriodBlock(p: Period, list: List<Task>, now: Boolean, d: Desk, act: (suspend () -> Unit) -> Unit, open: (Task) -> Unit) {
    val accent = periodAccent(p.key)
    val done = list.count { !it.open }
    val box = if (now) Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFFFFF3E4)).padding(12.dp) else Modifier
    Column(box, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(accent))
            Text(p.name, fontFamily = if (now) DisplayFont else null, fontWeight = FontWeight.Bold, fontSize = if (now) 20.sp else 16.sp)
            Text(fmt12(p.start) + if (now) " · الآن" else "", fontSize = 13.sp, color = Muted)
            Spacer(Modifier.weight(1f))
            if (list.isNotEmpty()) Text(if (done == list.size) "${ar(done)}/${ar(list.size)} ✓" else "${ar(done)}/${ar(list.size)}", fontSize = 13.sp,
                fontWeight = FontWeight.Bold, color = if (done == list.size) doneColor() else Muted)
        }
        list.forEach { TaskRowDesk(it, d, act, open) }
    }
}

@Composable
fun TaskRowDesk(t: Task, d: Desk, act: (suspend () -> Unit) -> Unit, open: (Task) -> Unit) {
    val repo = Repository.instance!!
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable { open(t) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DomainTile(domainOf(t, d.settings), 44.dp, done = !t.open)
        Column(Modifier.weight(1f)) {
            Text(t.title, fontWeight = FontWeight.SemiBold, fontSize = 16.5.sp, color = if (t.open) Ink else Muted,
                textDecoration = if (t.state == "done") TextDecoration.LineThrough else null)
            Text(durLabel(t.minutes) + if (t.running) " · شغّالة" else "", fontSize = 13.sp, color = if (t.running) warmText() else Muted)
        }
        DoneCircle(done = !t.open, label = t.title, onDone = if (t.open) ({ act { repo.finish(t.id) } }) else null)
    }
}

@Composable
private fun NowCard(t: Task, d: Desk, act: (suspend () -> Unit) -> Unit, open: (Task) -> Unit) {
    val repo = Repository.instance!!
    Column(
        Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface).clickable { open(t) }.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            DomainTile(domainOf(t, d.settings), 84.dp)
            Column {
                Text(if (t.running) "شغّالة" else "هلأ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = warmText())
                Text(t.title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp)
                Text(durLabel(t.minutes), fontSize = 14.sp, color = Muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!t.running) Action("ابدأ", { act { repo.startNow(t.id) } }, Modifier.weight(1f))
            else Action("✓ خلصت", { act { repo.finish(t.id) } }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DayNumbers(todays: List<Task>, sum: Progress.Summary, d: Desk) {
    val done = todays.count { it.state == "done" }
    val minutes = todays.sumOf { Maths.spent(it, d.now) } / 60_000
    val points = todays.sumOf { Progress.pointsOf(it, d.now) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("يومك لهلأ", fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("${ar(done)}/${ar(todays.size)}", "مهام خلصت", Modifier.weight(1f))
            Stat(durLabel(minutes.toInt()), "وقت فعلي", Modifier.weight(1f))
            if (d.meta["xp"] != "0") Stat("+${ar(points)}", "نقطة اليوم", Modifier.weight(1f), Color(0xFFFFF6D9))
        }
        if (d.meta["xp"] != "0") Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row { Text("مستوى ${ar(sum.total.level)} · ${sum.total.name}", fontWeight = FontWeight.Bold, color = Brand.violet, fontSize = 14.sp); Spacer(Modifier.weight(1f))
                Text("باقي ${ar(sum.total.left)} نقطة", fontSize = 14.sp, color = Muted) }
            Bar(sum.total.fraction, onBand = false)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier, bg: Color = Color(0xFFEAF0FF)) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(bg).padding(12.dp)) {
        Text(value, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Ink)
        Text(label, fontSize = 13.sp, color = Muted)
    }
}

// ---------------------------------------------------------------- Week, with mouse drag

@Composable
fun WeekScreen(d: Desk, repo: Repository, act: (suspend () -> Unit) -> Unit, open: (Task) -> Unit) {
    var offsetWeeks by remember { mutableIntStateOf(0) }
    val start = PlanMath.startOfWeek(d.today).plusWeeks(offsetWeeks.toLong())
    val days = (0..6).map { start.plusDays(it.toLong()) }
    val keys = Prayer.keys
    val lanes = remember { mutableStateMapOf<Pair<LocalDate, String>, Rect>() }
    var dragging by remember { mutableStateOf<Task?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val target = dragging?.let { lanes.entries.firstOrNull { it.value.contains(pointer) }?.key }

    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize()) {
            PageHeader("التخطيط", "الأسبوع كامل قدامك · اسحب المهام بين الأيام والأوقات", actions = {
                HeaderButton("الأسبوع اللي قبل", { offsetWeeks-- }) { Text("→", color = Color.White, fontSize = 20.sp) }
                Text("${days.first().dayOfMonth} – ${days.last().dayOfMonth} ${monthLabel(java.time.YearMonth.from(days.last()))}", color = Color.White, fontWeight = FontWeight.Bold)
                HeaderButton("الأسبوع الجاي", { offsetWeeks++ }) { Text("←", color = Color.White, fontSize = 20.sp) }
            })
            Row(Modifier.fillMaxSize().padding(20.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                days.forEach { day ->
                    val today = day == d.today
                    val list = dayTasks(d, day)
                    val planned = list.filter { it.open }.sumOf { it.minutes }
                    val free = (d.settings.dayEnd - d.settings.dayStart).coerceAtLeast(1)
                    Column(
                        Modifier.width(170.dp).fillMaxHeight().clip(RoundedCornerShape(20.dp))
                            .background(if (today) MaterialTheme.colorScheme.surface else Color(0xFFFBFCFF))
                            .then(if (today) Modifier.border(2.dp, Brand.sky, RoundedCornerShape(20.dp)) else Modifier)
                            .verticalScroll(rememberScrollState()).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(shortDay(day), fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = if (today) Brand.sky else Ink)
                            Text("${day.dayOfMonth}", fontSize = 13.sp, color = Muted)
                        }
                        Bar((planned.toFloat() / free).coerceIn(0f, 1f), onBand = false)
                        Text(durLabel(planned) + " مخطط", fontSize = 11.5.sp, color = if (planned > free) warmText() else Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
                        keys.forEach { k ->
                            val hot = target == (day to k)
                            Column(
                                Modifier.fillMaxWidth().heightIn(min = 34.dp).clip(RoundedCornerShape(14.dp))
                                    .background(if (hot) Color(0xFFEAF0FF) else Color.Transparent)
                                    .then(if (hot) Modifier.border(2.dp, Brand.sky, RoundedCornerShape(14.dp)) else Modifier)
                                    .onGloballyPositioned { lanes[day to k] = it.boundsInRoot() }.padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(periodAccent(k)))
                                    Text(Prayer.names[k] ?: k, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Muted)
                                }
                                list.filter { keyOf(it, d.settings) == k }.forEach { t ->
                                    WeekChip(t, d, faded = dragging?.id == t.id, open = open,
                                        start = { p -> dragging = t; pointer = p }, move = { pointer += it },
                                        end = {
                                            val to = lanes.entries.firstOrNull { it.value.contains(pointer) }?.key
                                            val moved = dragging
                                            dragging = null
                                            if (to != null && moved != null && (to.first.toString() != moved.day || keyOf(moved, d.settings) != to.second)) act {
                                                if (to.first.toString() != moved.day) repo.moveToDay(moved.id, to.first)
                                                repo.assignPeriod(moved.id, to.second)
                                            }
                                        })
                                }
                            }
                        }
                    }
                }
            }
        }
        dragging?.let { t ->
            val p = pointer - origin
            Box(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset { IntOffset(p.x.roundToInt() - 70, p.y.roundToInt() - 20) }
                .graphicsLayer { rotationZ = -3f }.shadow(14.dp, RoundedCornerShape(12.dp))) {
                ChipBody(t, d, Modifier.width(150.dp))
            }
        }
    }
}

@Composable
private fun WeekChip(t: Task, d: Desk, faded: Boolean, open: (Task) -> Unit, start: (Offset) -> Unit, move: (Offset) -> Unit, end: () -> Unit) {
    var at by remember { mutableStateOf(Offset.Zero) }
    ChipBody(t, d, Modifier.fillMaxWidth().alpha(if (faded) 0.35f else 1f)
        .onGloballyPositioned { at = it.positionInRoot() }
        .clickable { open(t) }
        .then(if (t.open) Modifier.pointerInput(t.id) {
            detectDragGestures(
                onDragStart = { start(at + it) },
                onDrag = { change, amount -> change.consume(); move(amount) },
                onDragEnd = end, onDragCancel = end,
            )
        } else Modifier))
}

@Composable
private fun ChipBody(t: Task, d: Desk, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(4.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(domainOf(t, d.settings).color))
        Column {
            Text(t.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, color = if (t.open) Ink else Muted,
                textDecoration = if (t.state == "done") TextDecoration.LineThrough else null)
            Text(durLabel(t.minutes), fontSize = 11.5.sp, color = Muted)
        }
    }
}

// ---------------------------------------------------------------- Goals

@Composable
fun GoalsScreen(d: Desk) {
    val sum = remember(d.tasks, d.goals, d.logs) { Progress.summary(d.tasks, d.goals, d.logs, d.settings, d.today, d.now) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("الأهداف", "السنة وطريق كل هدف")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (d.meta["xp"] != "0") ProgressBand(sum) {}
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Card(Modifier.weight(1f)) { Text("المجالات", fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 21.sp); DomainLevels(sum) }
                Card(Modifier.weight(1f)) {
                    Text("إنجازات حقيقية", fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    val shown = Progress.earned(sum).take(2) + Progress.next(sum).take(2)
                    if (shown.isEmpty()) Text("لسا ما في إنجازات. بتطلع لحالها من شغلك الحقيقي.", color = Muted)
                    shown.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { BadgeTile(it, Modifier.weight(1f)) } } }
                }
                Card(Modifier.weight(1f)) {
                    Text("طريق كل هدف", fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    if (sum.goalPaths.isEmpty()) Text("ما في أهداف بأرقام لسا.", color = Muted)
                    sum.goalPaths.forEach { GoalRoad(it) }
                }
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

// ---------------------------------------------------------------- Task panel

@Composable
fun TaskPanel(t: Task, d: Desk, repo: Repository, act: (suspend () -> Unit) -> Unit, close: () -> Unit) {
    val dom = domainOf(t, d.settings)
    val goal = t.goalId?.let { id -> d.goals.firstOrNull { it.id == id } }
    Sheet("", close) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            DomainTile(dom, 110.dp, done = !t.open)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(dom.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = dom.color)
                Text(t.title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 31.sp)
                val p = d.settings.periodsOn(LocalDate.parse(t.day)).firstOrNull { it.key == keyOf(t, d.settings) }
                Text(listOfNotNull(p?.name, durLabel(t.minutes), t.day.takeIf { it != d.today.toString() }).joinToString(" · "), fontSize = 14.sp, color = Muted)
            }
        }
        if (goal != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFF4F7FE)).padding(14.dp)) {
            Text("هدف: ${goal.name} · ${num(goal.current)} من ${num(goal.reference)} ${goal.unit}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        if (t.open) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!t.running) Action("ابدأ جلسة", { act { repo.startNow(t.id) } }, Modifier.weight(2f))
                OutlinedButton(onClick = { act { repo.finish(t.id) }; close() }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Text("✓ خلصت", color = doneColor(), fontWeight = FontWeight.Bold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("بكرا", Modifier.weight(1f)) { act { repo.moveToDay(t.id, LocalDate.parse(t.day).plusDays(1)) }; close() }
                Tile("لوقت بعدين", Modifier.weight(1f)) { act { repo.deferTask(t.id, "later") }; close() }
                Tile("تكرار", Modifier.weight(1f)) { act { repo.duplicate(t.id) }; close() }
            }
            Text("التعديل الكامل والملاحظات جايين بالخطوة الجاية من نسخة الكمبيوتر.", fontSize = 13.sp, color = Muted)
        } else {
            Text(if (t.state == "done") "خلصت هالمهمة." else "هالمهمة متخطّاة.", color = Muted)
            TextButton(onClick = { act { repo.reopen(t.id) } }) { Text("رجّعها مفتوحة") }
        }
    }
}

@Composable
private fun Tile(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(64.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFEAF0FF)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
    }
}
