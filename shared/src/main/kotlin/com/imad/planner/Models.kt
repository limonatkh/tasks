package com.imad.planner


import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt

// ---------------- Entities ----------------

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val minutes: Int,
    /** Start time (epoch ms). Set by the scheduler unless [pinned]. 0 = not placed. */
    val planned: Long = 0,
    val day: String,
    /** planned | done | skipped */
    val state: String = "planned",
    /** Accumulated timer ms (excluding the current run). */
    val spent: Long = 0,
    /** Epoch ms when the current run started, 0 if not running. */
    val started: Long = 0,
    val original: Long = 0,
    /** True when the user set the time; the scheduler never moves it. */
    val pinned: Boolean = false,
    val priority: Int = 1,
    val interruptible: Boolean = false,
    /** task | training */
    val kind: String = "task",
    val ruleId: String? = null,
    val goalId: String? = null,
    val projectId: String? = null,
    val sets: Int = 0,
    val workSec: Int = 0,
    val restSec: Int = 0,
    val created: Long = System.currentTimeMillis(),
    val parallelTo: String? = null,
    val overflow: Boolean = false,
    val finishedAt: Long = 0,
    /** quran | reading | work | sport | other */
    val category: String = "other",
    /** 0 none, 1 light, 2 heavy */
    val sport: Int = 0,
    val phase: Int = 0,
    val phaseCount: Int = 1,
    val pct: Int = 100,
    /** Pages (plans) or minutes (other split tasks) assigned to this phase. */
    val units: Int = 0,
    val doneUnits: Int = 0,
    /** "grp:<id>" for split one-off tasks, or a short note. */
    val note: String = "",
    /** Allowed inside the work hours. */
    val atWork: Boolean = false,
    /** Soft preferred start for today (minute of day), -1 = none. */
    val preferMin: Int = -1,
    /** Prayer period the task belongs to on its day ("" = not decided yet). Kept even if the period passes. */
    val period: String = "",
    /** Free text the user writes on the task (double tap). */
    val details: String = "",
    /** Not before this moment (epoch ms; 0 = no limit), e.g. 48 real hours after the last session. */
    val notBefore: Long = 0,
) {
    val running get() = started > 0
    val done get() = state == "done"
    val open get() = state == "planned"
    val isPlan get() = ruleId?.startsWith("plan-") == true
    val baseTitle get() = if (phaseCount > 1) title.substringBeforeLast(" (") else title
}

@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val minutes: Int,
    val everyHours: Int = 24,
    /** bit 0 Monday … bit 6 Sunday. 0 = use [everyHours]. */
    val weekdays: Int = 0,
    /** Learned default start, minute of day. -1 = automatic. */
    val preferredMinute: Int = -1,
    val pinned: Boolean = false,
    val priority: Int = 1,
    val interruptible: Boolean = false,
    val kind: String = "task",
    val goalId: String? = null,
    val sets: Int = 0,
    val workSec: Int = 0,
    val restSec: Int = 0,
    val active: Boolean = true,
    val anchorDay: String,
    val lastDoneDay: String? = null,
    val source: String = "",
    /** main | support */
    val role: String = "main",
    val category: String = "other",
    val sport: Int = 0,
    /** Split over the day, e.g. "75,25". Empty = one block. */
    val phases: String = "",
    /** JSON list of exercises for grouped training sessions. */
    val members: String = "",
    val atWork: Boolean = false,
    /** Repeat counted in real hours from when the last session ended (true), or in calendar days (false). */
    val realHours: Boolean = false,
)

/** History of a task: started, finished, postponed, moved, notes. */
@Entity(tableName = "events")
data class Event(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val taskId: String?,
    /** The recurring rule, split group or task the event belongs to. */
    val series: String,
    val day: String,
    val at: Long = System.currentTimeMillis(),
    /** start | pause | finish | postpone | rolled | missed | extend | note */
    val type: String,
    val text: String = "",
    /** Minutes late against the planned time (start/finish), 0 otherwise. */
    val delayMin: Int = 0,
)

/** The series a task belongs to: its recurring rule, its split group, the goal of a week session ("wk:"), or itself. */
fun seriesOf(t: Task) = t.ruleId ?: t.note.takeIf { it.startsWith("grp:") } ?: t.note.takeIf { it.startsWith("wk:") }?.removePrefix("wk:")
    ?: t.note.takeIf { it.startsWith("ser:") }?.removePrefix("ser:") ?: t.id

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val current: Double,
    val reference: Double,
    val unit: String,
    val lower: Boolean,
    val baseline: Double = current,
    val startDay: String = LocalDate.now().toString(),
    val targetDay: String = LocalDate.now().plusYears(1).toString(),
)

@Entity(tableName = "goal_logs")
data class GoalLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val goalId: String,
    val day: String,
    val value: Double,
)

@Entity(tableName = "projects")
data class Project(@PrimaryKey val id: String, val name: String, val stages: String)

@Entity(tableName = "meta")
data class Meta(@PrimaryKey val key: String, val value: String)

// ---------------- Groups ----------------

/** A group of goals and tasks. [slot]: where in the day it goes (morning | work | evening | any | sport). */
data class Group(val key: String, val name: String, val slot: String, val atWork: Boolean = false, val color: Int = -1)

object Groups {
    val defaults = listOf(
        Group("quran", "القرآن", "morning"), Group("reading", "القراءة", "morning"),
        Group("work", "الشغل والمشاريع", "work"), Group("sport", "الرياضة", "sport"), Group("other", "أخرى", "any"),
    )
    fun parse(text: String?): List<Group> {
        if (text.isNullOrBlank()) return defaults
        return runCatching {
            val a = JSONArray(text)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Group(o.getString("key"), o.getString("name"), o.optString("slot", "any"), o.optBoolean("atWork"), o.optInt("color", -1))
            }
        }.getOrDefault(defaults).ifEmpty { defaults }
    }
    fun encode(list: List<Group>): String {
        val a = JSONArray()
        list.forEach { g -> a.put(JSONObject().put("key", g.key).put("name", g.name).put("slot", g.slot).put("atWork", g.atWork).put("color", g.color)) }
        return a.toString()
    }
    fun slotName(slot: String) = when (slot) {
        "morning" -> "الصبح"; "work" -> "وقت الشغل"; "evening" -> "العصر والمسا"; "sport" -> "حسب شدة التمرين"; else -> "أي وقت"
    }
}

// ---------------- Settings ----------------

data class Settings(
    /** Wake-up time (minute of day). */
    val dayStart: Int = 8 * 60,
    /** Sleep time. */
    val dayEnd: Int = 23 * 60,
    val gap: Int = 5,
    val extend: Int = 5,
    val insistent: Boolean = true,
    val alarms: Boolean = true,
    val morning: Int = 180,
    val workEnd: Int = 15 * 60,
    val sportEnd: Int = 21 * 60,
    val maxSports: Int = 3,
    val maxHeavy: Int = 2,
    val sportGap: Int = 180,
    /** Work hours (job): nothing is scheduled inside except allowed tasks. */
    val jobOn: Boolean = false,
    val jobStart: Int = 9 * 60,
    val jobEnd: Int = 17 * 60,
    /** bit 0 Monday … bit 6 Sunday. */
    val jobDays: Int = 0b0011111 or (1 shl 6),
    val timerStyle: String = "digital",
    val groups: List<Group> = Groups.defaults,
    /** What happens to unfinished tasks when the day ends: carry | close. */
    val closeDefault: String = "carry",
    /** series -> colour index (-1 none). */
    val colors: Map<String, Int> = emptyMap(),
    /** series -> routine on/off (overrides the automatic choice). */
    val routine: Map<String, Boolean> = emptyMap(),
    /** series -> icon key ("i:<name>") or short text ("t:<abbr>"). */
    val icons: Map<String, String> = emptyMap(),
    /** How tasks are shown: name | icon | both. */
    val nameStyle: String = "both",
    /** Swiping a task the other way: carry | close | later. */
    val swipeAction: String = "carry",
    /** How the day is arranged: hours (day parts) | prayer (periods between prayers). */
    val dayMode: String = "prayer",
    /** Pasted monthly prayer sheet (see [Prayer.parse]). */
    val prayers: String = Prayer.SEED,
    /** Minutes kept free before and after each prayer. */
    val prayerBefore: Int = 5,
    val prayerAfter: Int = 15,
    /** prayer key -> minutes added to the sheet's time (can be negative). */
    val prayerOffsets: Map<String, Int> = emptyMap(),
    /** group key -> period key it belongs to in prayer mode. */
    val groupPeriods: Map<String, String> = emptyMap(),
    /** series -> "p:<period>" (flexible in that period) or "a:<prayer>:<minutes>" (that long after the prayer). */
    val places: Map<String, String> = emptyMap(),
    /** Minutes of sleep after fajr (0 = none). */
    val fajrNap: Int = 0,
    /** prayer key -> "before,atAdhan": minutes before (0 = off) and 1/0 for ringing at the prayer time. */
    val prayerAlarms: Map<String, String> = emptyMap(),
    /** Summary notification at wake-up. */
    val morningNote: Boolean = true,
    /** Notification when a period ends with tasks left. */
    val periodNote: Boolean = true,
    /** series -> ward of the Quran app it opens. */
    val quranLinks: Map<String, QuranLink> = emptyMap(),
    /** Goals whose week target repeats by itself every week. */
    val weeklyRoutine: Set<String> = emptySet(),
    /** How reports and plans are shown: charts | lists; [viewPages] overrides it per page. */
    val viewStyle: String = "charts",
    val viewPages: Map<String, String> = emptyMap(),
    /** Training sessions are put on days from the week plan (true) or repeat by themselves (false). */
    val sportByWeek: Boolean = true,
    /** Records not followed for now. */
    val pausedGoals: Set<String> = emptySet(),
    /** How records show: both (picture and name) | icon (picture only). */
    val recordStyle: String = "both",
    /** series -> YouTube playlist it plays, with where the user is in it. */
    val ytLinks: Map<String, YtLink> = emptyMap(),
    /** Record month targets are written by hand only (no automatic suggestion). */
    val manualMonths: Boolean = true,
) {
    fun styleOf(page: String) = viewPages[page] ?: viewStyle
    fun alarmBefore(k: String) = prayerAlarms[k]?.split(",")?.getOrNull(0)?.toIntOrNull() ?: 0
    fun alarmAtAdhan(k: String) = prayerAlarms[k]?.split(",")?.getOrNull(1) == "1"

    /** Period that a minute of the day falls in (before fajr counts as fajr). */
    fun periodAt(day: LocalDate, minute: Int): String =
        periodsOn(day).lastOrNull { minute >= it.start }?.key ?: "fajr"
    private val table: List<PrayerDay> by lazy { Prayer.parse(prayers) }
    val prayerMode get() = dayMode == "prayer"

    /** Today's prayer times with the user's minute corrections. */
    fun prayersOn(day: LocalDate): PrayerDay {
        val p = Prayer.day(day, table)
        fun o(k: String) = prayerOffsets[k] ?: 0
        return p.copy(fajr = p.fajr + o("fajr"), dhuhr = p.dhuhr + o("dhuhr"), asr = p.asr + o("asr"), maghrib = p.maghrib + o("maghrib"), isha = p.isha + o("isha"))
    }

    /** Last day the pasted sheet covers. */
    val prayersUntil: LocalDate? get() = table.lastOrNull()?.date

    fun periodsOn(day: LocalDate): List<Period> = Prayer.periods(prayersOn(day), dayEnd)

    /** Where a task sits in prayer mode: its own choice, else its group's period, else null (by day parts). */
    fun placeOf(t: Task): String? = t.period.takeIf { it.isNotEmpty() }?.let { "p:$it" }
        ?: places[seriesOf(t)] ?: groupPeriods[t.category]?.let { "p:$it" }

    /** Busy minutes around prayers and the sleep after fajr. */
    fun prayerBlocks(day: LocalDate): List<Pair<Int, Int>> {
        if (!prayerMode) return emptyList()
        val p = prayersOn(day)
        val out = Prayer.keys.map { k -> (p.at(k) - prayerBefore) to (p.at(k) + prayerAfter) }.toMutableList()
        if (fajrNap > 0) out += (p.fajr + prayerAfter) to (p.fajr + prayerAfter + fajrNap)
        return out.filter { it.second > dayStart && it.first < dayEnd }
    }
    /** Before waking up or after sleep time: no tasks. */
    fun isNight(minute: Int) = minute < dayStart || minute >= dayEnd

    val shape get() = DayShape(dayStart, dayEnd, morning, workEnd, sportEnd)
    val config get() = Config(dayStart, dayEnd, gap, maxSports, maxHeavy, sportGap)

    fun jobToday(day: LocalDate) = jobOn && jobEnd > jobStart && (jobDays and Scheduler.weekdayBit(day.dayOfWeek)) != 0

    fun configFor(day: LocalDate) = config.copy(blocks = (if (jobToday(day)) listOf(jobStart to jobEnd) else emptyList()) + prayerBlocks(day))

    fun group(key: String): Group? = groups.firstOrNull { it.key == key }

    /** The day part a group goes to; old keys keep their meaning. */
    fun slotOf(category: String): String = when {
        category == "sport" -> "sport"
        else -> group(category)?.slot ?: when (category) { "quran", "reading" -> "morning"; "work" -> "work"; else -> "any" }
    }

    companion object {
        val KEYS = listOf("dayStart", "dayEnd", "gap", "extend", "insistent", "alarms", "morning", "workEnd", "sportEnd", "maxSports", "maxHeavy", "sportGap",
            "jobOn", "jobStart", "jobEnd", "jobDays", "timerStyle", "groups", "closeDefault", "colors", "routine", "icons", "nameStyle", "swipeAction",
            "dayMode", "prayers", "prayerBefore", "prayerAfter", "prayerOffsets", "groupPeriods", "places", "fajrNap",
            "prayerAlarms", "morningNote", "periodNote", "quranLinks", "weeklyRoutine", "viewStyle", "viewPages", "sportByWeek", "pausedGoals", "recordStyle", "ytLinks", "manualMonths", "yearParts")
        fun from(m: Map<String, String>): Settings {
            fun i(k: String, d: Int) = m[k]?.toIntOrNull() ?: d
            return Settings(
                dayStart = i("dayStart", 8 * 60), dayEnd = i("dayEnd", 23 * 60), gap = i("gap", 5), extend = i("extend", 5),
                insistent = m["insistent"]?.let { it == "1" } ?: true, alarms = m["alarms"]?.let { it == "1" } ?: true,
                morning = i("morning", 180), workEnd = i("workEnd", 15 * 60), sportEnd = i("sportEnd", 21 * 60),
                maxSports = i("maxSports", 3), maxHeavy = i("maxHeavy", 2), sportGap = i("sportGap", 180),
                jobOn = m["jobOn"] == "1", jobStart = i("jobStart", 9 * 60), jobEnd = i("jobEnd", 17 * 60),
                jobDays = i("jobDays", 0b1011111), timerStyle = m["timerStyle"] ?: "digital",
                groups = Groups.parse(m["groups"]),
                closeDefault = m["closeDefault"] ?: "carry",
                colors = jsonMap(m["colors"]) { it.toIntOrNull() ?: -1 },
                routine = jsonMap(m["routine"]) { it == "true" },
                icons = jsonMap(m["icons"]) { it }, nameStyle = m["nameStyle"] ?: "both",
                swipeAction = m["swipeAction"] ?: "carry",
                dayMode = m["dayMode"] ?: "prayer", prayers = m["prayers"]?.takeIf { it.isNotBlank() } ?: Prayer.SEED,
                prayerBefore = i("prayerBefore", 5), prayerAfter = i("prayerAfter", 15),
                prayerOffsets = jsonMap(m["prayerOffsets"]) { it.toIntOrNull() ?: 0 },
                groupPeriods = jsonMap(m["groupPeriods"]) { it }, places = jsonMap(m["places"]) { it },
                fajrNap = i("fajrNap", 0),
                prayerAlarms = jsonMap(m["prayerAlarms"]) { it },
                morningNote = m["morningNote"] != "0", periodNote = m["periodNote"] != "0",
                quranLinks = parseLinks(m["quranLinks"]),
                weeklyRoutine = runCatching { org.json.JSONArray(m["weeklyRoutine"] ?: "[]").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } }.getOrDefault(emptySet()),
                viewStyle = m["viewStyle"] ?: "charts", viewPages = jsonMap(m["viewPages"]) { it },
                sportByWeek = m["sportByWeek"] != "0",
                pausedGoals = runCatching { org.json.JSONArray(m["pausedGoals"] ?: "[]").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } }.getOrDefault(emptySet()),
                recordStyle = m["recordStyle"] ?: "both",
                ytLinks = YtLink.parseAll(m["ytLinks"]), manualMonths = m["manualMonths"] != "0",
            ).also { Cat.groups = it.groups }
        }
    }
}

fun parseLinks(text: String?): Map<String, QuranLink> =
    if (text.isNullOrBlank()) emptyMap() else runCatching {
        val o = JSONObject(text)
        o.keys().asSequence().associateWith { k -> o.getJSONObject(k).let { QuranLink(it.optString("mode", "read"), it.getString("ward"), it.optString("name"), it.optBoolean("missing")) } }
    }.getOrDefault(emptyMap())

fun <T> jsonMap(text: String?, conv: (String) -> T): Map<String, T> =
    if (text.isNullOrBlank()) emptyMap() else runCatching { JSONObject(text).let { o -> o.keys().asSequence().associateWith { conv(o.get(it).toString()) } } }.getOrDefault(emptyMap())

/** Daily (or 5+ days a week) recurring tasks count as routine unless the user says otherwise. */
fun isRoutine(t: Task, rules: Map<String, Rule>, s: Settings): Boolean {
    s.routine[seriesOf(t)]?.let { return it }
    val r = t.ruleId?.let { rules[it] } ?: return false
    if (r.id.startsWith("plan-")) return r.everyHours <= 24
    return if (r.weekdays != 0) Integer.bitCount(r.weekdays) >= 5 else r.everyHours <= 24
}

/** Colour of a task: its own, else its group's. */
fun colorIndexOf(t: Task, s: Settings): Int = s.colors[seriesOf(t)]?.takeIf { it >= 0 } ?: s.group(t.category)?.color ?: -1

// ---------------- Helpers ----------------

object Clock {
    val zone: ZoneId get() = ZoneId.systemDefault()
    fun today(): LocalDate = LocalDate.now(zone)
    fun minuteOfDay(ms: Long): Int = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }
    fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun epoch(day: LocalDate, minute: Int): Long =
        day.atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant().toEpochMilli()
    fun nowMinute(): Int = LocalTime.now(zone).let { it.hour * 60 + it.minute }
}

object Maths {
    /** The real time worked on a task. It can pass the estimate (the task's minutes); the estimate stays as it was. */
    fun spent(t: Task, now: Long): Long =
        (t.spent + if (t.started > 0) (now - t.started).coerceAtLeast(0) else 0L).coerceAtLeast(0L)

    /** Time left of the estimate (0 once passed). */
    fun remaining(t: Task, now: Long): Long = (t.minutes * 60_000L - spent(t, now)).coerceAtLeast(0L)

    /** How far past the estimate the task went (0 when within it). */
    fun over(t: Task, now: Long): Long = (spent(t, now) - t.minutes * 60_000L).coerceAtLeast(0L)

    fun valid(title: String, minutes: Int) = title.trim().isNotEmpty() && minutes in 1..10080

    fun achievement(g: Goal) = Scheduler.achievement(g.current, g.reference, g.lower)
}

object Cat {
    /** Current groups (set whenever settings are read). */
    @Volatile var groups: List<Group> = Groups.defaults
    val keys: List<String> get() = groups.map { it.key }
    fun name(k: String) = groups.firstOrNull { it.key == k }?.name ?: Groups.defaults.firstOrNull { it.key == k }?.name ?: "أخرى"
    fun known(k: String) = if (k in keys) k else "other".takeIf { it in keys } ?: keys.first()
    fun ofGoal(g: Goal) = when (g.category) {
        "Strength", "Calisthenics", "Speed" -> known("sport")
        else -> known(g.category)
    }
    fun ofPlan(p: Plan) = known(p.category.ifEmpty { when (p.kind) { "amount" -> "reading"; "cycle" -> "work"; else -> "quran" } })
    fun ofRule(r: Rule) = known(r.category)
}

data class Exercise(val goal: String?, val name: String, val sets: Int, val workSec: Int, val restSec: Int)

data class SessionShape(val minutes: Int, val sets: Int, val workSec: Int, val restSec: Int)

object Members {
    fun parse(text: String): List<Exercise> {
        if (text.isBlank()) return emptyList()
        return runCatching {
            val a = JSONArray(text)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Exercise(if (o.isNull("goal")) null else o.optString("goal"), o.getString("name"), o.getInt("sets"), o.getInt("workSec"), o.getInt("restSec"))
            }
        }.getOrDefault(emptyList())
    }

    fun encode(list: List<Exercise>): String {
        val a = JSONArray()
        list.forEach { e ->
            a.put(JSONObject().put("goal", e.goal ?: JSONObject.NULL).put("name", e.name).put("sets", e.sets).put("workSec", e.workSec).put("restSec", e.restSec))
        }
        return a.toString()
    }

    fun session(list: List<Exercise>, warmup: Int = 10): SessionShape {
        if (list.isEmpty()) return SessionShape(warmup, 0, 0, 0)
        val sec = list.sumOf { it.sets * it.workSec + (it.sets - 1).coerceAtLeast(0) * it.restSec }
        val sets = list.sumOf { it.sets }
        return SessionShape(
            minutes = ceil(sec / 60.0).toInt() + warmup + 2 * (list.size - 1),
            sets = sets,
            workSec = if (sets > 0) list.sumOf { it.sets * it.workSec } / sets else 0,
            restSec = list.minOf { it.restSec },
        )
    }

    fun goals(r: Rule): Set<String> = (parse(r.members).mapNotNull { it.goal } + listOfNotNull(r.goalId)).toSet()
}

fun parsePhases(s: String): List<Int> {
    val l = s.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
    return if (l.size > 1) l else listOf(100)
}

fun phaseSuffix(k: Int, n: Int) = if (n > 1) " (${k + 1}/$n)" else ""

data class MonthEntry(val value: Double?, val reviewed: Boolean)

object Months {
    fun key(goalKey: String, ym: String) = "$goalKey|$ym"
    fun parse(text: String?): Map<String, MonthEntry> {
        if (text.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(text)
            o.keys().asSequence().associateWith { k ->
                val e = o.getJSONObject(k)
                MonthEntry(if (e.isNull("v") || !e.has("v")) null else e.getDouble("v"), e.optBoolean("r"))
            }
        }.getOrDefault(emptyMap())
    }
    fun encode(m: Map<String, MonthEntry>): String {
        val o = JSONObject()
        m.forEach { (k, e) -> o.put(k, JSONObject().put("v", e.value ?: JSONObject.NULL).put("r", e.reviewed)) }
        return o.toString()
    }
}

/** Builds the task(s) of a recurring rule for one day (several when the rule is split into phases). */
fun buildInstances(r: Rule, day: LocalDate, plan: Plan?, memLast: Int, monthTarget: Double? = null, idOf: (Int) -> String = { UUID.randomUUID().toString() }): List<Task> {
    val phases = parsePhases(r.phases)
    val n = phases.size
    val pin = r.pinned && r.preferredMinute >= 0
    val base = Task(
        title = r.title, minutes = r.minutes, day = day.toString(), ruleId = r.id, goalId = r.goalId, kind = r.kind,
        sets = r.sets, workSec = r.workSec, restSec = r.restSec, priority = r.priority, interruptible = r.interruptible,
        category = r.category, sport = r.sport, atWork = r.atWork,
    )
    if (plan != null) {
        val quota = PlanMath.quotaWithMonth(plan, day, memLast, monthTarget)
        if (quota <= 0) return emptyList()
        val alloc = Scheduler.allocate(quota, phases.map { Share(it, true, 0) })
        var off = 0
        val out = mutableListOf<Task>()
        phases.indices.forEach { k ->
            val u = alloc[k]
            if (u <= 0) return@forEach
            out += base.copy(
                id = idOf(k), phase = k, phaseCount = n, pct = phases[k], units = u,
                minutes = PlanMath.unitMinutes(plan, off, u).coerceIn(5, 900),
                title = PlanMath.phaseTitle(plan, off, u, memLast) + phaseSuffix(k, n),
                pinned = pin && k == 0, planned = if (pin && k == 0) Clock.epoch(day, r.preferredMinute) else 0,
            )
            off += u
        }
        return out
    }
    return phases.indices.map { k ->
        val mins = (r.minutes * phases[k] / 100.0).roundToInt().coerceAtLeast(5)
        base.copy(
            id = idOf(k), phase = k, phaseCount = n, pct = phases[k], minutes = mins, units = mins,
            title = r.title + phaseSuffix(k, n),
            pinned = pin && k == 0, planned = if (pin && k == 0) Clock.epoch(day, r.preferredMinute) else 0,
        )
    }
}

/** Scheduler item for a task. [nowMs]/[nowMin] are null for future days. */
fun itemOf(t: Task, rules: Map<String, Rule>, s: Settings, nowMs: Long?, nowMin: Int?, day: LocalDate = LocalDate.parse(t.day)): Item {
    val inProgress = nowMs != null && (t.running || t.spent > 0)
    val w0 = Windows.of(s.slotOf(t.category), t.sport, s.shape)
    val place = if (s.prayerMode) s.placeOf(t) else null
    var afterPrayer: Int? = null
    val w = when {
        place == null -> w0
        place.startsWith("p:") -> s.periodsOn(day).firstOrNull { it.key == place.removePrefix("p:") }
            ?.let { p -> Win(p.start + s.prayerAfter, maxOf(p.start + s.prayerAfter, p.end - s.prayerBefore), w0.hardEnd) } ?: w0
        place.startsWith("a:") -> {
            val parts = place.split(":")
            afterPrayer = s.prayersOn(day).at(parts.getOrElse(1) { "asr" }) + (parts.getOrNull(2)?.toIntOrNull() ?: 0)
            w0
        }
        else -> w0
    }
    val e0 = Windows.phaseEarliest(t.phase, t.phaseCount, s.shape)
    // A task that must wait (real hours of rest) starts no earlier than that moment on its day.
    val nb = if (t.notBefore > 0 && Clock.dateOf(t.notBefore) == day) Clock.minuteOfDay(t.notBefore) else null
    val e = listOfNotNull(e0, nb).maxOrNull()
    return Item(
        id = t.id, minutes = t.minutes,
        fixedStart = if (t.pinned && t.planned > 0 && !inProgress) Clock.minuteOfDay(t.planned) else null,
        preferred = if (t.pinned) null else t.preferMin.takeIf { it >= 0 }
            ?: afterPrayer ?: if (t.phaseCount == 1) t.ruleId?.let { rules[it] }?.preferredMinute?.takeIf { it >= 0 } else null,
        priority = t.priority, interruptible = t.interruptible,
        sets = t.sets, workSec = t.workSec, restSec = t.restSec, order = t.created,
        inProgressStart = if (inProgress) when {
            t.running -> nowMin!! - (Maths.spent(t, nowMs!!) / 60_000).toInt()
            else -> nowMin!!
        } else null,
        remaining = if (inProgress) ((Maths.remaining(t, nowMs!!) + 59_999) / 60_000).toInt() else null,
        windowStart = if (e != null) maxOf(w.start, e) else w.start,
        windowEnd = if (e != null && e >= w.end) s.dayEnd else w.end,
        hardEnd = w.hardEnd, sport = t.sport, earliest = e, strict = place?.startsWith("p:") == true,
        atWork = t.atWork || s.group(t.category)?.atWork == true,
    )
}


/** A task series linked to a ward. The name is for display only; the id is what keeps the link. */
data class QuranLink(val mode: String, val wardId: String, val wardName: String, val missing: Boolean = false)

/** A number as people read it: no ".0" for whole numbers, one decimal otherwise. */
fun num(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else String.format(java.util.Locale.US, "%.1f", d)
