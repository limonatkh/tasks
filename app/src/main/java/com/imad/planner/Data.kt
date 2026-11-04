package com.imad.planner

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

// ---------------- DAO / DB ----------------

@Dao
interface Store {
    @Query("SELECT * FROM tasks ORDER BY day, planned, created") fun tasks(): Flow<List<Task>>
    @Query("SELECT * FROM goals ORDER BY id") fun goals(): Flow<List<Goal>>
    @Query("SELECT * FROM projects ORDER BY name") fun projects(): Flow<List<Project>>
    @Query("SELECT * FROM rules ORDER BY title") fun rules(): Flow<List<Rule>>
    @Query("SELECT * FROM goal_logs ORDER BY day") fun logs(): Flow<List<GoalLog>>
    @Query("SELECT * FROM meta") fun metas(): Flow<List<Meta>>
    @Query("SELECT * FROM events ORDER BY at") fun events(): Flow<List<Event>>
    @Query("SELECT * FROM events") suspend fun allEvents(): List<Event>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(e: Event)
    @Query("DELETE FROM events") suspend fun clearEvents()

    @Query("SELECT * FROM tasks") suspend fun allTasks(): List<Task>
    @Query("SELECT * FROM tasks WHERE day = :day") suspend fun tasksOn(day: String): List<Task>
    @Query("SELECT * FROM goals") suspend fun allGoals(): List<Goal>
    @Query("SELECT * FROM projects") suspend fun allProjects(): List<Project>
    @Query("SELECT * FROM rules") suspend fun allRules(): List<Rule>
    @Query("SELECT * FROM goal_logs") suspend fun allLogs(): List<GoalLog>
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun task(id: String): Task?
    @Query("SELECT * FROM rules WHERE id = :id") suspend fun rule(id: String): Rule?
    @Query("SELECT * FROM goals WHERE id = :id") suspend fun goal(id: String): Goal?
    @Query("SELECT value FROM meta WHERE `key` = :key") suspend fun meta(key: String): String?
    @Query("SELECT * FROM meta") suspend fun allMeta(): List<Meta>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(t: Task)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAll(t: List<Task>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(g: Goal)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(p: Project)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(r: Rule)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(l: GoalLog)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(m: Meta)

    @Query("DELETE FROM tasks WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM rules WHERE id = :id") suspend fun deleteRule(id: String)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun deleteGoal(id: String)
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM projects") suspend fun clearProjects()
    @Query("DELETE FROM rules") suspend fun clearRules()
    @Query("DELETE FROM goal_logs") suspend fun clearLogs()
}

@Database(entities = [Task::class, Rule::class, Goal::class, GoalLog::class, Project::class, Meta::class, Event::class], version = 5, exportSchema = false)
abstract class PlannerDb : RoomDatabase() {
    abstract fun store(): Store
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "ALTER TABLE tasks ADD COLUMN category TEXT NOT NULL DEFAULT 'other'",
            "ALTER TABLE tasks ADD COLUMN sport INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE tasks ADD COLUMN phase INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE tasks ADD COLUMN phaseCount INTEGER NOT NULL DEFAULT 1",
            "ALTER TABLE tasks ADD COLUMN pct INTEGER NOT NULL DEFAULT 100",
            "ALTER TABLE tasks ADD COLUMN units INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE tasks ADD COLUMN doneUnits INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE tasks ADD COLUMN note TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE rules ADD COLUMN category TEXT NOT NULL DEFAULT 'other'",
            "ALTER TABLE rules ADD COLUMN sport INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE rules ADD COLUMN phases TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE rules ADD COLUMN members TEXT NOT NULL DEFAULT ''",
        ).forEach(db::execSQL)
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN atWork INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN preferMin INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE rules ADD COLUMN atWork INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS `events` (`id` TEXT NOT NULL, `taskId` TEXT, `series` TEXT NOT NULL, `day` TEXT NOT NULL, `at` INTEGER NOT NULL, `type` TEXT NOT NULL, `text` TEXT NOT NULL, `delayMin` INTEGER NOT NULL, PRIMARY KEY(`id`))")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN period TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN details TEXT NOT NULL DEFAULT ''")
    }
}

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
    fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun epoch(day: LocalDate, minute: Int): Long =
        day.atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant().toEpochMilli()
    fun nowMinute(): Int = LocalTime.now(zone).let { it.hour * 60 + it.minute }
}

object Maths {
    fun spent(t: Task, now: Long): Long =
        (t.spent + if (t.started > 0) (now - t.started).coerceAtLeast(0) else 0L)
            .coerceIn(0L, t.minutes * 60_000L)

    fun remaining(t: Task, now: Long): Long = t.minutes * 60_000L - spent(t, now)

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
    val e = Windows.phaseEarliest(t.phase, t.phaseCount, s.shape)
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
        hardEnd = w.hardEnd, sport = t.sport, earliest = e,
        atWork = t.atWork || s.group(t.category)?.atWork == true,
    )
}

// ---------------- Repository ----------------

class Repository(private val context: Context) {
    val db: PlannerDb = Room.databaseBuilder(context, PlannerDb::class.java, "planner-v2.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
    val store: Store = db.store()
    private val lock = Mutex()

    /**
     * Every change runs one at a time and off the screen's thread: planning the day, the alarms and the JSON work
     * would otherwise freeze scrolling while they run.
     */
    private suspend fun <T> locked(block: suspend () -> T): T = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { lock.withLock { block() } }

    private suspend fun settingsLocked(): Settings = Settings.from(Settings.KEYS.mapNotNull { k -> store.meta(k)?.let { k to it } }.toMap())

    suspend fun setSetting(key: String, value: String) = locked {
        store.put(Meta(key, value))
        replanLocked()
    }

    // ---------- seed ----------

    suspend fun initialize() = locked {
        if (store.meta("seed-v2") == null) {
            db.withTransaction { seedGoalsAndProjects(); store.put(Meta("seed-v2", "1")) }
        }
        if (store.meta("seed-v3") == null) { seedV3(); store.put(Meta("seed-v3", "1")) }
        if (store.meta("seed-v4") == null) { seedV4(); store.put(Meta("seed-v4", "1")) }
        if (store.meta("seed-v5") == null) { seedV5(); store.put(Meta("seed-v5", "1")) }
        // 0.9: the prayer periods become the main way of arranging the day.
        if (store.meta("seed-v6") == null) { store.put(Meta("dayMode", "prayer")); store.put(Meta("seed-v6", "1")) }
        // 0.16: sessions come from the week plan; automatic sport sessions of the coming days are taken off (today's stay).
        if (store.meta("undo")?.isNotEmpty() == true) store.put(Meta("undo", ""))
        // People added in 0.19 keep following, counted from now.
        if (store.meta("igPeople2") == null && store.meta("igPeople") != null)
            store.put(Meta("igPeople2", Ig.encodePeople(Ig.parse(store.meta("igPeople")).map { IgPerson(it, System.currentTimeMillis()) })))
        if (store.meta("safety")?.isNotEmpty() == true) store.put(Meta("safety", ""))
        if (store.meta("seed-v7") == null) {
            val today = Clock.today().toString()
            val sportRules = store.allRules().filter { it.category == "sport" }.map { it.id }.toSet()
            store.allTasks().filter { it.day > today && it.ruleId in sportRules && it.open && !it.running && it.spent == 0L }.forEach { store.delete(it.id) }
            store.put(Meta("seed-v7", "1"))
        }
        ensureDayLocked(Clock.today())
        replanLocked()
    }

    /** Version 0.5: projects become yearly plans that move step by step through the week; plans remember where they started. */
    private suspend fun seedV5() {
        val today = Clock.today().toString()
        val plans = plansLocked().map { p -> if (p.startDay.isEmpty()) p.copy(startDay = today, startDone = p.done) else p }.toMutableList()
        val minutes = mapOf("lemonat" to 90, "history-podcast" to 60, "sports-content" to 60)
        for (pr in store.allProjects()) {
            val id = "proj-${pr.id}"
            if (plans.any { it.id == id }) continue
            plans += Plan(
                id = id, title = pr.name, kind = "cycle", unit = "حلقة", total = 52, done = 0, pointer = 0,
                stages = pr.stages, perWeek = 1.0, minutesPerUnit = minutes[pr.id] ?: 60, category = "work",
                accepted = true, configured = true, startDay = today, startDone = 0,
                endDay = Clock.today().plusYears(1).toString(),
            )
        }
        savePlansLocked(plans)
        for (p in plans.filter { it.kind == "cycle" }) syncPlanRuleLocked(p)
    }

    /** Keeps the daily rule of a plan in line with it (active, group, interval). */
    private suspend fun syncPlanRuleLocked(p: Plan, phases: String? = null) {
        val ruleId = "plan-${p.id}"
        val existing = store.rule(ruleId)
        val every = if (p.kind == "cycle") cycleIntervalHours(p) else 24
        val rule = (existing ?: Rule(id = ruleId, title = p.title, minutes = 15, anchorDay = Clock.today().toString()))
            .copy(active = p.accepted && p.configured, title = p.title, category = Cat.ofPlan(p), interruptible = p.kind == "review",
                phases = phases ?: existing?.phases ?: "", everyHours = every)
        store.put(rule)
    }

    /** cycle: interval from this month's target if the user set one, else from rounds per week. */
    private suspend fun cycleIntervalHours(p: Plan): Int {
        val today = Clock.today()
        val target = Months.parse(store.meta("months"))[Months.key("p:${p.id}", java.time.YearMonth.from(today).toString())]?.value
        val eff = if (target != null) p.copy(perWeek = PlanMath.cyclePerWeekForMonth(p, today, target)) else p
        return PlanMath.cycleIntervalDays(eff) * 24
    }

    // ---------- history ----------

    private suspend fun log(t: Task, type: String, text: String = "", delay: Int = 0) =
        store.put(Event(taskId = t.id, series = seriesOf(t), day = t.day, type = type, text = text, delayMin = delay))

    suspend fun addNote(taskId: String, text: String) = locked {
        val t = store.task(taskId) ?: return@locked
        if (text.isNotBlank()) log(t, "note", text.trim())
    }

    // ---------- groups ----------

    suspend fun saveGroups(list: List<Group>) = locked {
        store.put(Meta("groups", Groups.encode(list)))
        replanLocked()
    }

    /** Moves a goal / plan / recurring task to another group. */
    suspend fun moveToGroup(goalId: String?, planId: String?, ruleId: String?, group: String) = locked {
        goalId?.let { id -> store.goal(id)?.let { store.put(it.copy(category = group)) } }
        planId?.let { id ->
            val plans = plansLocked().map { if (it.id == id) it.copy(category = group) else it }
            savePlansLocked(plans)
            plans.firstOrNull { it.id == id }?.let { syncPlanRuleLocked(it) }
        }
        ruleId?.let { id -> store.rule(id)?.let { store.put(it.copy(category = group)) } }
        store.tasksOn(Clock.today().toString()).filter { it.open && ((ruleId != null && it.ruleId == ruleId) || (planId != null && it.ruleId == "plan-$planId")) }
            .forEach { store.put(it.copy(category = group)) }
        replanLocked()
    }

    private suspend fun seedGoalsAndProjects() {
        val sports = JSONObject(asset("sports-seed.json")).getJSONArray("goals")
        for (i in 0 until sports.length()) {
            val o = sports.getJSONObject(i)
            if (store.goal(o.getString("id")) == null) store.put(
                Goal(
                    id = o.getString("id"), name = o.getString("name"), category = o.getString("category"),
                    current = o.getDouble("current"), reference = o.getDouble("reference"),
                    unit = o.getString("unit"), lower = o.getString("direction") == "lower",
                )
            )
        }
        val projects = JSONObject(asset("projects-seed.json")).getJSONArray("projects")
        for (i in 0 until projects.length()) {
            val o = projects.getJSONObject(i)
            store.put(Project(o.getString("id"), o.getString("name"), o.getJSONArray("stages").toString()))
        }
    }

    /** Version 0.3 additions: arm-wrestling forearms, boxing, reading, Quran plans. */
    private suspend fun seedV3() {
        val today = Clock.today().toString()
        val extra = listOf(
            forearms(today),
            Rule(id = "boxing", title = "بوكسنق", minutes = 60, everyHours = 168, anchorDay = today, category = "sport", sport = 2, kind = "training"),
            Rule(id = "reading-books", title = "قراءة كتاب", minutes = 30, everyHours = 24, anchorDay = today, category = "reading"),
        )
        extra.forEach { if (store.rule(it.id) == null) store.put(it) }
        if (plansLocked().isEmpty()) savePlansLocked(
            listOf(
                Plan(id = "quran-memorize", title = "حفظ القرآن", kind = "memorize", minutesPerUnit = 20, perDay = 1),
                Plan(id = "quran-read", title = "ورد قراءة القرآن", kind = "read", minutesPerUnit = 2, perDay = 10),
                Plan(id = "quran-review", title = "مراجعة الحفظ سماعاً", kind = "review", minutesPerUnit = 1),
            )
        )
    }

    private fun ex(goal: String?, name: String, sets: Int, work: Int, rest: Int) = Exercise(goal, name, sets, work, rest)

    private fun group(id: String, title: String, every: Int, sport: Int, members: List<Exercise>, source: String, today: String): Rule {
        val sh = Members.session(members)
        return Rule(
            id = id, title = title, minutes = sh.minutes, everyHours = every, kind = "training",
            goalId = members.firstOrNull { it.goal != null }?.goal, sets = sh.sets, workSec = sh.workSec, restSec = sh.restSec,
            anchorDay = today, active = false, category = "sport", sport = sport, members = Members.encode(members), source = source,
        )
    }

    private fun forearms(today: String) = group(
        "arm-forearms", "سواعد لمصارعة الذراعين", 72, 2,
        listOf(ex(null, "رفع الرسغ (كبنق)", 4, 45, 120), ex(null, "دوران الساعد (برونيشن)", 4, 45, 120), ex(null, "رفع جانبي للرسغ (رايزنق)", 4, 45, 120)),
        "قيمة مقترحة: أوتار الساعد تحتاج 48–72 ساعة بين الجلسات", today,
    ).copy(active = true)

    /** Sports sessions grouped by place: gym days hold the weights; swimming and running are not scheduled. */
    private fun sportGroups(today: String): List<Rule> {
        val lift = "قيمة مقترحة: كل رفعة مرة بالأسبوع وراحة 3 دقائق بين المجموعات (السكوات احتاج حتى 96 ساعة ليتعافى في دراسة على متمرّنين)"
        return listOf(
            group("gym-a", "النادي A: سكوات + بنش + كيرل", 168, 2,
                listOf(ex("sport-7", "سكوات", 5, 45, 180), ex("sport-4", "بنش برس", 5, 45, 180), ex("sport-1", "باربل كيرل", 4, 45, 120)), lift, today),
            group("gym-b", "النادي B: ديدلفت + عقلة بوزن + لوق", 168, 2,
                listOf(ex("sport-2", "ديدلفت", 5, 45, 180), ex("sport-12", "عقلة بوزن", 5, 30, 180), ex("sport-3", "لوق لفت", 4, 45, 180)), lift, today),
            group("gym-c", "النادي C: كلين آند جيرك + سناتش", 168, 2,
                listOf(ex("sport-5", "كلين آند جيرك", 6, 30, 180), ex("sport-6", "سناتش", 6, 30, 180)), lift, today),
            group("cali-push", "ضغط ومتوازي", 48, 1,
                listOf(ex("sport-8", "ضغط دقيقة", 3, 60, 180), ex("sport-9", "ضغط على القبضة", 3, 60, 180), ex("sport-11", "متوازي دقيقة", 3, 60, 180)),
                "قيمة مقترحة: 48 ساعة بين الجلسات", today),
            group("cali-pull", "عقلة وتعلّق أصابع", 72, 1,
                listOf(ex("sport-10", "عقلة دقيقة", 3, 60, 180), ex("sport-13", "تعلّق أقصى بإصبعين 10 ث", 4, 10, 180), ex("sport-13", "عقلة بإصبعين", 3, 30, 180)),
                "بروتوكول Max Hangs: تعلّق 10 ث، راحة 3 د، 48–72 ساعة بين جلسات الأصابع", today),
            group("rope", "حبل: دبل أندر + أندر سكيبس", 48, 1,
                listOf(ex("sport-14", "دبل أندر", 4, 60, 120), ex("sport-18", "أندر سكيبس", 5, 30, 120)), "قيمة مقترحة", today),
            group("plyo", "قفز: طويل + عمودي + بوكس", 72, 2,
                listOf(ex("sport-16", "وثب طويل", 5, 20, 120), ex("sport-19", "قفز عمودي", 5, 20, 120), ex("sport-20", "بوكس جمب", 5, 20, 120)),
                "تدريب البلايومترك: 48–72 ساعة بين الجلسات", today),
        )
    }

    /** Version 0.4: one session per gym day instead of one per lift, categories for the day shape, staggered start days. */
    private suspend fun seedV4() {
        val today = Clock.today()
        val ts = today.toString()
        val old = store.allRules().filter { it.id.startsWith("train-") || it.id.startsWith("support-") }
        val wasActive = old.any { it.active }
        val tasks = store.allTasks()
        old.forEach { r ->
            store.deleteRule(r.id)
            tasks.filter { it.ruleId == r.id && it.open && it.spent == 0L && !it.running }.forEach { store.delete(it.id) }
        }
        sportGroups(ts).forEach { g -> if (store.rule(g.id) == null) store.put(g.copy(active = wasActive)) }
        store.rule("arm-forearms")?.let { r ->
            val f = forearms(ts)
            store.put(f.copy(active = r.active, anchorDay = r.anchorDay, lastDoneDay = r.lastDoneDay))
        }
        store.rule("boxing")?.let { store.put(it.copy(category = "sport", sport = 2, kind = "training")) }
        store.rule("reading-books")?.let { store.put(it.copy(category = "reading")) }
        store.allRules().filter { it.id.startsWith("plan-") }.forEach { r ->
            store.put(r.copy(category = if (r.id == "plan-quran-review" || r.id.startsWith("plan-quran")) "quran" else "reading", interruptible = r.id == "plan-quran-review"))
        }
        // The memorize plan now always uses the user's daily amount.
        savePlansLocked(plansLocked().map { if (it.kind == "memorize" && it.perDay == 0) it.copy(perDay = 1) else it })
        val sportIds = store.allRules().filter { it.active && it.category == "sport" }.map { it.id }.toSet()
        if (sportIds.isNotEmpty()) restaggerLocked(sportIds)
    }

    private fun asset(name: String) = context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** Spreads the start days of [ids] so no day gets all the sport; other active sport rules stay where they are. */
    private suspend fun restaggerLocked(ids: Set<String>) {
        val today = Clock.today()
        val rules = store.allRules().filter { it.active && it.category == "sport" }
        val items = rules.map { r ->
            val iv = if (r.weekdays != 0) 7 else Scheduler.intervalDays(r.everyHours)
            if (r.id in ids && r.weekdays == 0) Recurring(r.id, iv, r.sport >= 2)
            else {
                val next = Scheduler.nextDue(r.everyHours, r.weekdays, LocalDate.parse(r.anchorDay), r.lastDoneDay?.let(LocalDate::parse), today)
                Recurring(r.id, iv, r.sport >= 2, fixedOffset = ChronoUnit.DAYS.between(today, next).toInt().coerceIn(0, 27))
            }
        }
        val offsets = Scheduler.stagger(items)
        val todayTasks = store.tasksOn(today.toString())
        offsets.forEach { (id, off) ->
            store.rule(id)?.let { store.put(it.copy(anchorDay = today.plusDays(off.toLong()).toString(), lastDoneDay = null)) }
            todayTasks.filter { it.ruleId == id && it.open && !it.running && it.spent == 0L }.forEach { store.delete(it.id) }
        }
    }

    // ---------- plans (stored as JSON in meta) ----------

    private suspend fun plansLocked(): List<Plan> = Plans.parse(store.meta("plans"))
    private suspend fun savePlansLocked(list: List<Plan>) = store.put(Meta("plans", Plans.encode(list)))
    private fun memLastOf(plans: List<Plan>) = plans.firstOrNull { it.kind == "memorize" && it.configured }?.done ?: 0

    /** Saves a plan; an accepted, configured plan gets a daily rule that feeds the schedule. */
    suspend fun savePlan(plan: Plan, phases: String? = null) = locked {
        val today = Clock.today()
        val plans = plansLocked()
        val old = plans.firstOrNull { it.id == plan.id }
        var p = plan.copy(dayKey = old?.dayKey ?: "", dayDone = old?.dayDone ?: 0)
        if (p.startDay.isEmpty() || (old != null && old.done != p.done && p.kind != "read" && p.kind != "review")) p = p.copy(startDay = today.toString(), startDone = p.done)
        if (p.dayKey == today.toString()) p = p.copy(dayQuota = PlanMath.quotaWithMonth(p, today, memLastOf(plans.map { if (it.id == p.id) p else it }), monthTargetLocked(p, today)))
        savePlansLocked(plans.filter { it.id != p.id } + p)
        val ruleId = "plan-${p.id}"
        syncPlanRuleLocked(p, phases)
        store.tasksOn(today.toString()).filter { it.ruleId == ruleId && it.open && !it.running && it.spent == 0L && it.doneUnits == 0 }
            .forEach { store.delete(it.id) }
        ensureDayLocked(today)
        replanLocked()
    }

    suspend fun deletePlan(id: String) = locked {
        savePlansLocked(plansLocked().filter { it.id != id })
        store.deleteRule("plan-$id")
        store.allTasks().filter { it.ruleId == "plan-$id" && it.open && it.spent == 0L && !it.running }.forEach { store.delete(it.id) }
        replanLocked()
    }

    /** Turns on the training sessions of these goals and spreads them over the coming days. */
    suspend fun acceptGoals(ids: Collection<String>) = locked {
        val set = ids.toSet()
        val today = Clock.today().toString()
        val targets = store.allRules().filter { !it.active && Members.goals(it).any { g -> g in set } }
        targets.forEach { store.put(it.copy(active = true, anchorDay = today)) }
        restaggerLocked(targets.filter { it.category == "sport" }.map { it.id }.toSet())
        ensureDayLocked(Clock.today())
        replanLocked()
    }

    suspend fun setMonth(goalKey: String, ym: String, value: Double?, reviewed: Boolean) = locked {
        val m = Months.parse(store.meta("months")).toMutableMap()
        if (value == null && !reviewed) m.remove(Months.key(goalKey, ym)) else m[Months.key(goalKey, ym)] = MonthEntry(value, reviewed)
        store.put(Meta("months", Months.encode(m)))
        // This month's target changes the daily amounts / pace from today on.
        if (goalKey.startsWith("p:") && ym == java.time.YearMonth.from(Clock.today()).toString()) {
            val today = Clock.today()
            val plans = plansLocked()
            val p = plans.firstOrNull { "p:${it.id}" == goalKey } ?: return@locked
            if (p.kind == "cycle") syncPlanRuleLocked(p)
            if (p.dayKey == today.toString()) savePlansLocked(plans.map {
                if (it.id == p.id) it.copy(dayQuota = PlanMath.quotaWithMonth(it, today, memLastOf(plans), monthTargetLocked(it, today))) else it
            })
            store.allTasks().filter { it.ruleId == "plan-${p.id}" && it.day > today.toString() && it.open && it.spent == 0L }.forEach { store.delete(it.id) }
            ensureDayLocked(today)
            replanLocked()
        }
    }

    private suspend fun monthTargetLocked(p: Plan, day: LocalDate): Double? =
        Months.parse(store.meta("months"))[Months.key("p:${p.id}", java.time.YearMonth.from(day).toString())]?.value

    suspend fun acceptMonths(goalKey: String, yms: List<String>) = locked {
        val m = Months.parse(store.meta("months")).toMutableMap()
        yms.forEach { ym -> val k = Months.key(goalKey, ym); m[k] = (m[k] ?: MonthEntry(null, false)).copy(reviewed = true) }
        store.put(Meta("months", Months.encode(m)))
    }

    // ---------- recurrence ----------

    /** Creates the instances of due rules for [day]. Returns what was created. */
    private suspend fun ensureDayLocked(day: LocalDate, only: String? = null): List<Task> {
        val today = Clock.today()
        val ds = day.toString(); val ts = today.toString()
        val all = store.allTasks()
        val plans = plansLocked()
        val mem = memLastOf(plans)
        val created = mutableListOf<Task>()
        val byWeek = store.meta("sportByWeek") != "0"
        for (r in store.allRules().filter { (it.active || it.id == only) && (only == null || it.id == only) }) {
            // Training sessions are placed on days from the week plan, not by their own repeat.
            if (byWeek && only == null && r.category == "sport") continue
            if (all.any { it.ruleId == r.id && it.day == ds }) continue
            val instLast = all.filter { it.ruleId == r.id && it.state != "skipped" && it.day < ds && (it.done || it.day >= ts) }.maxOfOrNull { it.day }
            val last = listOfNotNull(r.lastDoneDay, instLast).maxOrNull()?.let(LocalDate::parse)
            if (only == null && !Scheduler.isDue(r.everyHours, r.weekdays, LocalDate.parse(r.anchorDay), last, day)) continue
            if (day == today) all.filter { it.ruleId == r.id && it.open && it.day < ds && !it.running }
                .forEach { store.put(it.copy(state = "skipped")); log(it, "missed") }
            val plan = if (r.id.startsWith("plan-")) plans.firstOrNull { "plan-${it.id}" == r.id } else null
            val simPlan = plan?.let { PlanMath.project(it, ChronoUnit.DAYS.between(today, day).toInt().coerceAtLeast(0), mem, today) }
            if (r.id.startsWith("plan-") && simPlan == null) continue
            val inst = buildInstances(r, day, simPlan, mem, simPlan?.let { monthTargetLocked(it, day) })
            if (inst.isEmpty()) continue
            store.putAll(inst); created += inst
        }
        return created
    }

    // ---------- scheduling ----------

    suspend fun replan() = locked { replanLocked() }

    // ---------- details, planning, pomodoro, Quran ----------

    suspend fun setDetails(id: String, text: String) = locked {
        val t = store.task(id) ?: return@locked
        store.put(t.copy(details = text.trim()))
    }

    suspend fun setTarget(key: String, series: String, t: Target) = locked {
        val old = targetsLocked()
        val all = Targets.put(old, key, series, t)
        saveTargetsLocked(all)
        // Only a change in the number of sessions touches the tasks.
        if (key.startsWith("d:") && (old[key]?.get(series)?.sessions ?: 0) != t.sessions) {
            syncSessionsLocked(Targets.weekStart(LocalDate.parse(key.removePrefix("d:"))))
            replanLocked()
        }
    }

    // ---------- week plan: goals, sessions, weekly routine ----------

    private suspend fun targetsLocked() = Targets.parse(store.meta("targets"))
    private suspend fun saveTargetsLocked(all: Map<String, Map<String, Target>>) = store.put(Meta("targets", Targets.encode(all)))

    suspend fun addToWeek(start: LocalDate, series: String) = locked {
        val all = targetsLocked()
        if (all[Targets.weekKey(start)]?.containsKey(series) != true) saveTargetsLocked(Targets.put(all, Targets.weekKey(start), series, Target(keep = true)))
    }

    /** Takes a goal out of one week: its targets and the sessions not started yet. */
    suspend fun removeFromWeek(start: LocalDate, series: String) = locked {
        saveTargetsLocked(Targets.removeFromWeek(targetsLocked(), start, series))
        markRoutineHandledLocked(start, series)
        val today = Clock.today()
        for (d in Targets.weekDays(start).filter { !it.isBefore(today) })
            store.tasksOn(d.toString()).filter { it.note == "wk:$series" && it.open && !it.running && it.spent == 0L }.forEach { store.delete(it.id) }
        replanLocked()
    }

    suspend fun setWeeklyRoutine(series: String, on: Boolean) = locked {
        val set = runCatching { org.json.JSONArray(store.meta("weeklyRoutine") ?: "[]").let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList()).toMutableSet()
        if (on) set += series else set -= series
        store.put(Meta("weeklyRoutine", org.json.JSONArray(set.toList()).toString()))
    }

    private suspend fun markRoutineHandledLocked(start: LocalDate, series: String) {
        val a = org.json.JSONArray(store.meta("routineWeeks")?.takeIf { it.isNotBlank() } ?: "[]")
        a.put("$start|$series")
        store.put(Meta("routineWeeks", a.toString()))
    }

    /** Weekly routine goals get this week as a copy of the last one, once (a removed one is not brought back). */
    private suspend fun ensureRoutineWeekLocked(start: LocalDate) {
        val routine = settingsLocked().weeklyRoutine
        if (routine.isEmpty()) return
        val handled = runCatching { org.json.JSONArray(store.meta("routineWeeks") ?: "[]").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } }.getOrDefault(emptySet())
        var all = targetsLocked()
        var changed = false
        for (sr in routine) {
            if ("$start|$sr" in handled) continue
            val next = Targets.copyWeek(all, start, sr)
            if (next != all) { all = next; changed = true }
            markRoutineHandledLocked(start, sr)
        }
        if (changed) saveTargetsLocked(all)
    }

    /** Opens a week for planning: routine goals copied, sessions made into tasks. */
    suspend fun prepareWeek(start: LocalDate) = locked {
        ensureRoutineWeekLocked(start)
        syncSessionsLocked(start)
        replanLocked()
    }

    /** What a series is called and which group it belongs to. */
    private suspend fun seriesInfoLocked(series: String): Pair<String, String> {
        store.rule(series)?.let { return it.title to it.category }
        plansLocked().firstOrNull { "plan-${it.id}" == series }?.let { return it.title to Cat.ofPlan(it) }
        return series to "other"
    }

    /**
     * The sessions written on each day of a week become tasks ("wk:<series>"). Missing ones are made; extra ones that
     * were not started are removed. Past days are left as they are, so what was not done stays visible.
     */
    private suspend fun syncSessionsLocked(start: LocalDate) {
        val all = targetsLocked()
        val today = Clock.today()
        for (d in Targets.weekDays(start).filter { !it.isBefore(today) }) {
            val ds = d.toString()
            val dayTargets = all[Targets.dayKey(d)].orEmpty()
            val onDay = store.tasksOn(ds).filter { it.note.startsWith("wk:") && it.state != "skipped" }
            val seriesHere = dayTargets.filterValues { it.sessions > 0 }.keys + onDay.map { it.note.removePrefix("wk:") }
            for (sr in seriesHere) {
                val want = dayTargets[sr]?.sessions ?: 0
                val have = onDay.filter { it.note == "wk:$sr" }
                if (have.size < want) {
                    val (title, cat) = seriesInfoLocked(sr)
                    val week = all[Targets.weekKey(start)]?.get(sr)
                    val text = dayTargets[sr]?.text?.takeIf { it.isNotBlank() }
                    val rule = store.rule(sr)
                    repeat(want - have.size) {
                        store.put(Task(title = title + (text?.let { " · $it" } ?: ""), minutes = week?.minutes?.takeIf { it > 0 } ?: rule?.minutes ?: 60,
                            day = ds, category = cat, note = "wk:$sr",
                            kind = rule?.kind ?: "task", sport = rule?.sport ?: 0, sets = rule?.sets ?: 0, workSec = rule?.workSec ?: 0,
                            restSec = rule?.restSec ?: 0, interruptible = rule?.interruptible ?: false, goalId = rule?.goalId))
                    }
                } else if (have.size > want) {
                    have.filter { it.open && !it.running && it.spent == 0L }.sortedByDescending { it.created }.take(have.size - want).forEach { store.delete(it.id) }
                }
            }
        }
    }

    /** A week session moved to another day: the day counts follow it, so the week plan stays right. */
    private suspend fun shiftSessionLocked(t: Task, to: String) {
        if (!t.note.startsWith("wk:") || t.day == to) return
        val sr = t.note.removePrefix("wk:")
        var all = targetsLocked()
        val from = Targets.dayKey(LocalDate.parse(t.day)); val dest = Targets.dayKey(LocalDate.parse(to))
        val a = all[from]?.get(sr) ?: Target()
        val b = all[dest]?.get(sr) ?: Target()
        all = Targets.put(all, from, sr, a.copy(sessions = (a.sessions - 1).coerceAtLeast(0)))
        all = Targets.put(all, dest, sr, b.copy(sessions = b.sessions + 1))
        saveTargetsLocked(all)
    }

    /** Moves a task to any day. */
    suspend fun moveToDay(id: String, day: LocalDate) = locked {
        val t = store.task(id) ?: return@locked
        if (!t.open) return@locked
        snapshotLocked("نقل «${t.baseTitle}»")
        val now = System.currentTimeMillis()
        shiftSessionLocked(t, day.toString())
        store.put(t.copy(day = day.toString(), spent = Maths.spent(t, now), started = 0, planned = if (t.pinned) Clock.epoch(day, Clock.minuteOfDay(t.planned)) else 0,
            period = "", preferMin = -1, parallelTo = null, overflow = false))
        log(t, "postpone", "لـ ${day}")
        replanLocked()
    }

    /** Moves one planned session of a goal from a day to another (week plan). */
    suspend fun moveSession(series: String, from: LocalDate, to: LocalDate) = locked {
        val t = store.tasksOn(from.toString()).firstOrNull { it.note == "wk:$series" && it.open && !it.running }
        if (t != null) {
            shiftSessionLocked(t, to.toString())
            store.put(t.copy(day = to.toString(), planned = 0, period = "", preferMin = -1, started = 0, spent = Maths.spent(t, System.currentTimeMillis())))
            log(t, "postpone", "جلسة لـ $to")
        } else {
            var all = targetsLocked()
            val a = all[Targets.dayKey(from)]?.get(series) ?: Target(); val b = all[Targets.dayKey(to)]?.get(series) ?: Target()
            all = Targets.put(all, Targets.dayKey(from), series, a.copy(sessions = (a.sessions - 1).coerceAtLeast(0)))
            saveTargetsLocked(Targets.put(all, Targets.dayKey(to), series, b.copy(sessions = b.sessions + 1)))
        }
        syncSessionsLocked(Targets.weekStart(to))
        replanLocked()
    }

    /** A copy of a task on the same day, not started. */
    suspend fun duplicate(id: String) = locked {
        val t = store.task(id) ?: return@locked
        store.put(t.copy(id = UUID.randomUUID().toString(), state = "planned", spent = 0, started = 0, finishedAt = 0, doneUnits = 0,
            ruleId = null, note = if (t.note.startsWith("grp:")) t.note else "ser:${seriesOf(t)}", planned = if (t.pinned) t.planned else 0, created = System.currentTimeMillis()))
        replanLocked()
    }

    /** After finishing a task: how much of its day target was done. */
    suspend fun addTargetDone(day: String, series: String, n: Int) = locked {
        val all = Targets.parse(store.meta("targets"))
        val key = "d:$day"
        val cur = all[key]?.get(series) ?: Target()
        store.put(Meta("targets", Targets.encode(Targets.put(all, key, series, cur.copy(done = (cur.done + n).coerceAtLeast(0))))))
    }

    /** Creates the day's tasks and proposes a period for each one that has none (the user can move them). */
    suspend fun prepareDay(day: LocalDate) = locked {
        ensureDayLocked(day)
        val s = settingsLocked()
        val ds = day.toString()
        val rules = store.allRules().associateBy { it.id }
        if (day != Clock.today() && s.prayerMode && store.meta("dayPlanned:$ds") == null && store.meta("planning:$ds") == null) {
            // A day planned ahead: only routine tasks get a period; everything else waits in the list for the user.
            val tasks = store.tasksOn(ds).filter { it.open }
            val routine = tasks.filter { isRoutine(it, rules, s) }
            val plan = Scheduler.plan(routine.map { itemOf(it, rules, s, null, null, day) }, s.configFor(day), s.dayStart)
            store.putAll(tasks.mapNotNull { t ->
                when {
                    !isRoutine(t, rules, s) && !t.pinned && t.spent == 0L -> if (t.period.isNotEmpty()) t.copy(period = "") else null
                    t.period.isEmpty() -> plan.placed[t.id]?.takeIf { !it.overflow }?.let { p -> t.copy(period = s.periodAt(day, p.start)) }
                    else -> null
                }
            })
        }
        // While the user is arranging this day, nothing is put into a period by itself.
        store.put(Meta("planning:$ds", "1"))
        replanLocked()
    }

    /** Puts a task in a period ("" = back to the list of tasks not placed yet). */
    suspend fun assignPeriod(id: String, key: String) = locked {
        val t = store.task(id) ?: return@locked
        val s = settingsLocked()
        val day = LocalDate.parse(t.day)
        val start = s.periodsOn(day).firstOrNull { it.key == key }?.start
        val nowMin = if (day == Clock.today()) Clock.minuteOfDay(System.currentTimeMillis()) else 0
        store.put(t.copy(period = key, pinned = false, preferMin = start?.let { maxOf(it + s.prayerAfter, nowMin) } ?: -1))
        replanLocked()
    }

    /** The day is planned: what was left in the list (and is not routine) goes to the next day. */
    suspend fun confirmDayPlan(day: LocalDate): Int = locked {
        val s = settingsLocked()
        val rules = store.allRules().associateBy { it.id }
        val left = store.tasksOn(day.toString()).filter { it.open && !it.running && it.spent == 0L && it.period.isEmpty() && !isRoutine(it, rules, s) }
        left.forEach { endTaskLocked(it, "carry", "ما انحطت بخطة اليوم") }
        store.put(Meta("dayPlanned:$day", "1"))
        replanLocked()
        left.size
    }

    /** Pomodoro: a 25-minute timer that is not one of the day's tasks. State: "end" while running, "left" while paused. */
    suspend fun pomo(action: String) = locked {
        val now = System.currentTimeMillis()
        val st = JSONObject(store.meta("pomo")?.takeIf { it.isNotBlank() } ?: "{}")
        val end = st.optLong("end"); val left = st.optLong("left", POMO_MS)
        when (action) {
            "start" -> if (end == 0L) { st.put("end", now + left).put("startedAt", if (left == POMO_MS) now else st.optLong("startedAt", now)) }
            "pause" -> if (end > 0) { st.put("left", (end - now).coerceAtLeast(0)).put("end", 0L) }
            "reset" -> {
                val used = POMO_MS - (if (end > 0) end - now else left)
                if (used >= 5 * 60_000L) addPomoLocked(st.optLong("startedAt", now), (used / 60_000).toInt())
                st.put("end", 0L).put("left", POMO_MS)
            }
        }
        store.put(Meta("pomo", st.toString()))
        Alarms.pomoAlarm(context, st.optLong("end"))
    }

    /** Called when the pomodoro time is over (alarm or app opened). */
    suspend fun pomoCheck() = locked {
        val st = JSONObject(store.meta("pomo")?.takeIf { it.isNotBlank() } ?: return@locked)
        val end = st.optLong("end")
        if (end == 0L || end > System.currentTimeMillis()) return@locked
        addPomoLocked(st.optLong("startedAt", end - POMO_MS), (POMO_MS / 60_000).toInt())
        store.put(Meta("pomo", st.put("end", 0L).put("left", POMO_MS).toString()))
    }

    private suspend fun addPomoLocked(startedAt: Long, minutes: Int) {
        val day = Clock.dayOf(startedAt).toString()
        val a = org.json.JSONArray(store.meta("pomo:$day")?.takeIf { it.isNotBlank() } ?: "[]")
        a.put(JSONObject().put("at", startedAt).put("min", minutes).put("label", ""))
        store.put(Meta("pomo:$day", a.toString()))
    }

    suspend fun pomoLabel(day: String, index: Int, label: String) = locked {
        val a = org.json.JSONArray(store.meta("pomo:$day")?.takeIf { it.isNotBlank() } ?: return@locked)
        if (index in 0 until a.length()) a.getJSONObject(index).put("label", label.trim())
        store.put(Meta("pomo:$day", a.toString()))
    }

    suspend fun setQuranLink(series: String, link: QuranLink?) = locked {
        val o = JSONObject(store.meta("quranLinks")?.takeIf { it.isNotBlank() } ?: "{}")
        if (link == null) o.remove(series) else o.put(series, JSONObject().put("mode", link.mode).put("ward", link.wardId).put("name", link.wardName))
        store.put(Meta("quranLinks", o.toString()))
    }

    /**
     * Pulls today's report from the Quran app. The newest report replaces the stored one (never added on top),
     * so syncing again never doubles anything. Ward names are refreshed so a renamed ward keeps its link.
     */
    suspend fun syncQuran(): QuranState {
        val today = Clock.today().toString()
        val (st, rep) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { QuranAdapter.report(context, today) }
        val wards = if (st == QuranState.OK) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { QuranAdapter.wards(context) } else st to emptyList()
        locked {
            store.put(Meta("quranState", st.name))
            if (rep != null) {
                val old = store.meta("quran:$today")?.let { runCatching { QuranAdapter.parseReport(JSONObject(it), 0) }.getOrNull() }
                if (old == null || rep.version >= old.version) store.put(Meta("quran:$today", reportJson(rep)))
                store.put(Meta("quranSyncAt", System.currentTimeMillis().toString()))
            }
            if (wards.first == QuranState.OK && wards.second.isNotEmpty()) {
                val o = JSONObject(store.meta("quranLinks")?.takeIf { it.isNotBlank() } ?: "{}")
                var changed = false
                for (k in o.keys().asSequence().toList()) {
                    val l = o.getJSONObject(k)
                    val w = wards.second.firstOrNull { it.id == l.getString("ward") }
                    if (w != null && w.name != l.optString("name")) { l.put("name", w.name); changed = true }
                    if (w == null && !l.optBoolean("missing")) { l.put("missing", true); changed = true }
                    if (w != null && l.optBoolean("missing")) { l.remove("missing"); changed = true }
                }
                if (changed) store.put(Meta("quranLinks", o.toString()))
            }
        }
        return st
    }

    private fun reportJson(r: QuranReport): String {
        fun t(x: QuranTotals) = JSONObject().put("read_ayahs", x.readAyahs).put("read_pages", x.readPages).put("memorized_ayahs", x.memorizedAyahs)
            .put("review_ayahs", x.reviewAyahs).put("review_plays", x.reviewPlays).put("listen_seconds", x.listenSeconds)
        val a = org.json.JSONArray()
        r.wards.forEach { w -> a.put(JSONObject().put("ward_id", w.wardId).put("name", w.name).put("mode", w.mode).put("totals", t(w.totals))) }
        return JSONObject().put("day", r.day).put("report_version", r.version).put("generated_at", r.generatedAt)
            .put("synced_at", r.syncedAt).put("totals", t(r.totals)).put("wards", a).toString()
    }

    /**
     * One session per training (rule and part) on a day. Older versions moved sessions over the sport limit to the
     * next day again and again, so copies piled up; the extra untouched copies are dropped here.
     * A session the user chose to keep next to tomorrow's own ("keep") is left alone.
     */
    private suspend fun dedupeLocked(day: String) {
        val open = store.tasksOn(day).filter { it.open && !it.running && it.spent == 0L && it.doneUnits == 0 && it.ruleId != null && it.note != "keep" }
        val drop = open.groupBy { it.ruleId to it.phase }.values.filter { it.size > 1 }
            .flatMap { g -> g.sortedWith(compareBy({ if (it.period.isNotEmpty() || it.pinned) 0 else 1 }, { it.created })).drop(1) }
        if (drop.isNotEmpty()) store.putAll(drop.map { it.copy(state = "skipped", note = if (it.note.startsWith("grp:")) it.note else "مكرر") })
    }

    // ---------- people followed without opening Instagram ----------

    suspend fun setIgPeople(list: List<String>) = locked { store.put(Meta("igPeople", Ig.encode(list.distinct()))) }

    suspend fun igSeen(person: String) = locked {
        val key = "igSeen:${Clock.today()}"
        val l = Ig.parse(store.meta(key))
        store.put(Meta(key, Ig.encode(if (person in l) l - person else l + person)))
    }

    suspend fun igSetPeople(list: List<IgPerson>) = locked { store.put(Meta("igPeople2", Ig.encodePeople(list.distinctBy { it.user }))) }

    suspend fun igMark(id: String, state: String) = locked {
        store.put(Meta("igFeed", Ig.encodePosts(Ig.posts(store.meta("igFeed")).map { if (it.id == id) it.copy(state = state) else it })))
    }

    /**
     * Asks Instagram for each person's latest posts (one after another, slowly) and keeps the new ones made after the
     * person was added. Not more often than every 4 hours unless asked.
     */
    suspend fun igRefresh(force: Boolean): String {
        val people = Ig.people(store.meta("igPeople2"))
        if (people.isEmpty()) return ""
        val last = store.meta("igFetchedAt")?.toLongOrNull() ?: 0L
        val now = System.currentTimeMillis()
        if (!force && now - last < 4 * 3_600_000L) return "آخر تحميل الساعة ${fmt12(Clock.minuteOfDay(last))}"
        var failed = 0
        val got = mutableListOf<IgPost>()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            for ((i, p) in people.withIndex()) {
                if (i > 0) kotlinx.coroutines.delay(1500)
                val list = Ig.fetch(p.user)
                if (list == null) failed++ else got += list.filter { it.at >= p.addedAt }
            }
        }
        var added = 0
        locked {
            val old = Ig.posts(store.meta("igFeed"))
            val known = old.map { it.id }.toSet()
            val fresh = got.filter { it.id !in known }
            added = fresh.size
            store.put(Meta("igFeed", Ig.encodePosts((old + fresh).sortedByDescending { it.at }.take(400))))
            store.put(Meta("igFetchedAt", now.toString()))
        }
        return when {
            failed == people.size -> "إنستغرام ما ردّ. ممكن لازم تسجّل دخول من جديد."
            added == 0 -> "ما في جديد" + if (failed > 0) " ($failed حساب ما ردّ)" else ""
            else -> "وصل $added جديد" + if (failed > 0) " ($failed حساب ما ردّ)" else ""
        }
    }

    // ---------- YouTube playlists ----------

    suspend fun setYt(series: String, link: YtLink?) = locked {
        val all = YtLink.parseAll(store.meta("ytLinks")).toMutableMap()
        if (link == null) all.remove(series) else all[series] = link
        store.put(Meta("ytLinks", YtLink.encodeAll(all)))
    }

    /**
     * A playlist to watch: a yearly plan counted in episodes (one task a day with the episodes to watch),
     * linked to the playlist so the player opens where the user stopped.
     */
    suspend fun addWatchList(title: String, url: String, list: String, fromEpisode: Int, total: Int, perDay: Int, endDay: LocalDate, minutesPer: Int) {
        val id = "yt-" + UUID.randomUUID().toString().take(8)
        val plan = Plan(id = id, title = title, kind = "amount", unit = "حلقة", total = total.coerceAtLeast(fromEpisode), done = (fromEpisode - 1).coerceAtLeast(0),
            perDay = perDay.coerceAtLeast(1), endDay = endDay.toString(), minutesPerUnit = minutesPer.coerceIn(1, 240), accepted = true, configured = true, category = "other")
        savePlan(plan)
        setYt("plan-$id", YtLink(list, (fromEpisode - 1).coerceAtLeast(0), 0, total, url))
    }

    /**
     * An episode was watched to its end (or the user said the rest is not needed): the plan moves one episode on,
     * the task counts it, and the task is done when today's episodes are all watched.
     */
    suspend fun episodeDone(taskId: String?, series: String, nextIndex: Int, total: Int) = locked {
        val all = YtLink.parseAll(store.meta("ytLinks")).toMutableMap()
        all[series]?.let { l -> all[series] = l.copy(index = nextIndex, pos = 0, total = if (total > 0) total else l.total, finished = total > 0 && nextIndex >= total) }
        store.put(Meta("ytLinks", YtLink.encodeAll(all)))
        val t = taskId?.let { store.task(it) } ?: return@locked
        val plans = plansLocked()
        val plan = plans.firstOrNull { "plan-${it.id}" == t.ruleId }
        val now = System.currentTimeMillis()
        val done = t.doneUnits + 1
        if (plan != null) {
            val mem = memLastOf(plans)
            savePlansLocked(plans.map { p -> if (p.id != plan.id) p else PlanMath.applyUnits(p, 1, mem).let { np -> if (np.dayKey == t.day) np.copy(dayDone = np.dayDone + 1) else np } })
        }
        val finished = done >= t.units.coerceAtLeast(1)
        store.put(t.copy(doneUnits = done, state = if (finished) "done" else t.state, finishedAt = if (finished) now else t.finishedAt, started = 0))
        log(t, if (finished) "finish" else "note", "حلقة ${nextIndex}")
        replanLocked()
    }

    // ---------- splitting a task ----------

    /** Split into [n] equal parts on the same day; each part can then go to its own period. */
    suspend fun splitTask(id: String, n: Int) = locked {
        val t = store.task(id) ?: return@locked
        if (!t.open || n < 2 || t.isPlan) return@locked
        snapshotLocked("تقسيم «${t.baseTitle}»")
        val now = System.currentTimeMillis()
        val spentMin = (Maths.spent(t, now) / 60_000).toInt()
        val left = (t.minutes - spentMin).coerceAtLeast(n * 5)
        val each = left / n
        val series = seriesOf(t)
        store.put(t.copy(minutes = spentMin + each + left % n, started = 0, spent = Maths.spent(t, now), title = t.baseTitle + " · ١/$n"))
        for (k in 2..n) store.put(t.copy(id = UUID.randomUUID().toString(), title = t.baseTitle + " · $k/$n", minutes = each, spent = 0, started = 0,
            ruleId = null, note = "ser:$series", planned = 0, pinned = false, created = now + k))
        replanLocked()
    }

    /**
     * Part of a task is done and the rest goes to another period: this part is finished with the share of the
     * whole it took (25 of 60 minutes ≈ 42 %), and the rest becomes a new task of the remaining minutes.
     */
    suspend fun continueLater(id: String, period: String) = locked {
        val t = store.task(id) ?: return@locked
        if (!t.open || t.isPlan) return@locked
        snapshotLocked("تكملة «${t.baseTitle}» بعدين")
        val now = System.currentTimeMillis()
        val spent = Maths.spent(t, now)
        val spentMin = ((spent + 59_999) / 60_000).toInt().coerceAtLeast(1)
        val rest = (t.minutes - spentMin).coerceAtLeast(5)
        val pct = (spentMin * 100 / (spentMin + rest)).coerceIn(1, 99)
        val s = settingsLocked()
        store.put(t.copy(state = "done", started = 0, spent = spent, finishedAt = now, minutes = spentMin, pct = pct,
            note = if (t.note.isEmpty() && t.ruleId == null) "" else t.note))
        log(t, "finish", "جزء: $pct٪")
        val start = s.periodsOn(LocalDate.parse(t.day)).firstOrNull { it.key == period }?.start
        store.put(t.copy(id = UUID.randomUUID().toString(), title = t.baseTitle + " · الباقي", minutes = rest, spent = 0, started = 0,
            state = "planned", ruleId = null, note = "ser:${seriesOf(t)}", period = period, planned = 0, pinned = false,
            preferMin = start?.let { maxOf(it + s.prayerAfter, Clock.minuteOfDay(now)) } ?: -1, created = now))
        replanLocked()
    }

    // ---------- year parts ----------

    suspend fun setYearParts(key: String, parts: List<YearPart>) = locked {
        val all = YearPart.parseAll(store.meta("yearParts")).toMutableMap()
        if (parts.isEmpty()) all.remove(key) else all[key] = parts
        store.put(Meta("yearParts", YearPart.encodeAll(all)))
    }

    // ---------- records and training sessions ----------

    /** Saves a record (new or edited). A new current value is logged. */
    suspend fun saveRecord(g: Goal) = locked {
        val old = store.goal(g.id)
        store.put(g)
        if (old == null || old.current != g.current) store.put(GoalLog(goalId = g.id, day = Clock.today().toString(), value = g.current))
    }

    suspend fun deleteRecord(id: String) = locked {
        store.deleteGoal(id)
        // Exercises that pointed to it stay in their sessions, just without a record.
        store.allRules().filter { r -> Members.parse(r.members).any { it.goal == id } || r.goalId == id }.forEach { r ->
            val members = Members.parse(r.members).map { if (it.goal == id) it.copy(goal = null) else it }
            store.put(r.copy(members = Members.encode(members), goalId = if (r.goalId == id) members.firstOrNull { it.goal != null }?.goal else r.goalId))
        }
    }

    suspend fun setPaused(id: String, paused: Boolean) = locked {
        val set = runCatching { org.json.JSONArray(store.meta("pausedGoals") ?: "[]").let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList()).toMutableSet()
        if (paused) set += id else set -= id
        store.put(Meta("pausedGoals", org.json.JSONArray(set.toList()).toString()))
    }

    /** Saves a training session with its exercises; its length and sets follow from them. */
    suspend fun saveSession(r: Rule, members: List<Exercise>) = locked {
        val sh = Members.session(members)
        store.put(r.copy(members = Members.encode(members), minutes = if (members.isEmpty()) r.minutes else sh.minutes, sets = sh.sets, workSec = sh.workSec,
            restSec = sh.restSec, goalId = members.firstOrNull { it.goal != null }?.goal ?: r.goalId, kind = "training", category = "sport"))
        // Planned sessions not started yet take the new shape.
        store.allTasks().filter { (it.ruleId == r.id || it.note == "wk:${r.id}") && it.open && !it.running && it.spent == 0L && it.day >= Clock.today().toString() }
            .forEach { t -> store.put(t.copy(minutes = if (members.isEmpty()) t.minutes else sh.minutes, sets = sh.sets, workSec = sh.workSec, restSec = sh.restSec, sport = r.sport)) }
        replanLocked()
    }

    suspend fun deleteSession(id: String) = locked {
        store.deleteRule(id)
        store.allTasks().filter { (it.ruleId == id || it.note == "wk:$id") && it.open && !it.running && it.spent == 0L }.forEach { store.delete(it.id) }
        replanLocked()
    }

    /** Routine on or off for a task's series (from a long press in day planning); the task stays where it is. */
    suspend fun setRoutine(series: String, on: Boolean) = locked {
        val r = JSONObject(store.meta("routine")?.takeIf { it.isNotBlank() } ?: "{}").put(series, on)
        store.put(Meta("routine", r.toString()))
    }

    suspend fun refreshWidget() = locked {
        PlannerWidget.update(context, store.tasksOn(Clock.today().toString()), settingsLocked())
    }

    suspend fun refreshDay() = locked {
        autoCloseLocked()
        ensureDayLocked(Clock.today())
        Targets.weekStart(Clock.today()).let { ensureRoutineWeekLocked(it); syncSessionsLocked(it) }
        replanLocked()
    }

    /** Days that ended without the user choosing get the default from the settings. */
    private suspend fun autoCloseLocked() {
        val today = Clock.today()
        val s = settingsLocked()
        val days = store.allTasks().filter { it.open && !it.running && it.day < today.toString() }.map { it.day }.distinct().sorted()
        for (d in days) if (store.meta("closed:$d") == null) closeDayLocked(LocalDate.parse(d), s.closeDefault)
    }

    /** Creates tomorrow's (or any future day's) tasks so they can be edited. */
    suspend fun materialize(day: LocalDate) = locked {
        ensureDayLocked(day)
        replanLocked()
    }

    suspend fun materializeRule(ruleId: String, day: LocalDate): String? = locked {
        val created = ensureDayLocked(day, ruleId)
        replanLocked()
        created.firstOrNull()?.id ?: store.allTasks().firstOrNull { it.ruleId == ruleId && it.day == day.toString() }?.id
    }

    /** An earlier phase that was not started when the next phase's time comes is closed; what is left moves on. */
    private suspend fun rollPhasesLocked(today: LocalDate, nowMin: Int, shape: DayShape, now: Long) {
        val tasks = store.tasksOn(today.toString()).filter { it.phaseCount > 1 }
        val groups = tasks.groupBy { it.ruleId ?: it.note }
        for ((_, g) in groups) {
            val sorted = g.sortedBy { it.phase }
            for (t0 in sorted) {
                val t = store.task(t0.id) ?: continue
                if (!t.open || t.running) continue
                val next = sorted.firstOrNull { it.phase > t.phase }?.let { store.task(it.id) }?.takeIf { it.open } ?: continue
                val e = Windows.phaseEarliest(next.phase, next.phaseCount, shape) ?: continue
                if (nowMin < e) continue
                store.put(t.copy(state = if (t.spent == 0L && t.doneUnits == 0) "skipped" else "done", finishedAt = now))
                if (!t.isPlan) {
                    val add = (t.minutes - (t.spent / 60_000).toInt()).coerceAtLeast(0)
                    store.put(next.copy(minutes = (next.minutes + add).coerceAtMost(10080), units = next.units + add))
                }
            }
        }
    }

    /** Keeps today's Quran/plan tasks in line with progress: amounts, titles and pages. */
    private suspend fun refreshPlanTasksLocked(today: LocalDate, now: Long) {
        val ts = today.toString()
        var plans = plansLocked()
        val mem = memLastOf(plans)
        var changed = false
        // Channel plans get a new month of steps when the month changes.
        val ym = java.time.YearMonth.from(today).toString()
        plans = plans.map { p ->
            if (p.content.isNotBlank() && p.contentMonth != ym) {
                changed = true
                val types = Content.parse(p.content).map { it.copy(titles = emptyList()) }
                p.copy(stages = Stages.encode(Content.monthSteps(types)), pointer = 0, contentMonth = ym, content = Content.encode(types),
                    perWeek = 7.0 / today.lengthOfMonth())
            } else p
        }
        plans = plans.map { p ->
            if (p.configured && p.dayKey != ts) { changed = true; p.copy(dayKey = ts, dayDone = 0, dayQuota = PlanMath.quotaWithMonth(p, today, mem, monthTargetLocked(p, today))) } else p
        }
        if (changed) savePlansLocked(plans)
        val tasks = store.tasksOn(ts).filter { it.isPlan }
        for (p in plans) {
            val group = tasks.filter { it.ruleId == "plan-${p.id}" }.sortedBy { it.phase }
            if (group.isEmpty()) continue
            val alloc = Scheduler.allocate(p.dayQuota, group.map { Share(it.pct, it.open, it.doneUnits) })
            var off = 0
            val upd = mutableListOf<Task>()
            group.forEachIndexed { i, t ->
                if (!t.open) return@forEachIndexed
                val u = alloc[i]
                val left = (u - t.doneUnits).coerceAtLeast(0)
                if (left == 0 && !t.running && t.spent == 0L) {
                    upd += t.copy(units = u, state = if (t.doneUnits > 0 || p.dayDone > 0) "done" else "skipped", finishedAt = now)
                    return@forEachIndexed
                }
                val spentMin = ceil(Maths.spent(t, now) / 60_000.0).toInt()
                upd += t.copy(
                    units = u,
                    title = PlanMath.phaseTitle(p, off, left.coerceAtLeast(1), mem) + phaseSuffix(t.phase, t.phaseCount),
                    minutes = maxOf(PlanMath.unitMinutes(p, off, left.coerceAtLeast(1)).coerceIn(5, 900), spentMin, 1),
                )
                off += left
            }
            if (upd.isNotEmpty()) store.putAll(upd)
        }
    }

    private suspend fun replanLocked() {
        val today = Clock.today()
        val ts = today.toString()
        val s = settingsLocked()
        val now = System.currentTimeMillis()
        val nowMin = Clock.minuteOfDay(now)
        rollPhasesLocked(today, nowMin, s.shape, now)
        refreshPlanTasksLocked(today, now)
        val tomorrow = today.plusDays(1).toString()
        dedupeLocked(ts); dedupeLocked(tomorrow)
        val tomorrowRules = store.tasksOn(tomorrow).filter { it.open && it.spent == 0L && it.ruleId != null }.mapNotNull { it.ruleId }.toSet()
        val tasks = store.tasksOn(ts)
        val rules = store.allRules().associateBy { it.id }
        val items = tasks.filter { it.open }.map { itemOf(it, rules, s, now, nowMin, today) }
        val plan = Scheduler.plan(items, s.configFor(today), nowMin)
        val updated = tasks.map { t ->
            val p = plan.placed[t.id] ?: return@map t.copy(parallelTo = null, overflow = false)
            val inProgress = t.running || t.spent > 0 || t.doneUnits > 0
            if (t.id in plan.capped && !t.pinned && !inProgress) {
                // Over today's sport limit: it moves to tomorrow and its series shifts with it,
                // unless tomorrow already has a session of the same training (then it is not piled up a second time).
                if (t.ruleId != null && t.ruleId in tomorrowRules)
                    return@map t.copy(state = "skipped", note = if (t.note.startsWith("grp:")) t.note else "فوق حد التمارين", parallelTo = null, overflow = false)
                return@map t.copy(day = tomorrow, planned = 0, parallelTo = null, overflow = false, period = "")
            }
            val start = if (t.pinned && !t.running && t.spent == 0L) t.planned else Clock.epoch(today, p.start)
            t.copy(
                planned = if (t.running || t.spent > 0) t.planned.takeIf { it > 0 } ?: start else start,
                original = if (t.original == 0L) start else t.original,
                parallelTo = p.parallelTo, overflow = p.overflow,
            )
        }
        // Prayer mode: each task of today gets its period once and keeps it, done or not.
        val byHand = store.meta("dayPlanned:$ts") != null || store.meta("planning:$ts") != null
        val final = if (!s.prayerMode) updated else updated.map { t ->
            if (t.day != ts || t.period.isNotEmpty() || !t.open) return@map t
            if (byHand && !isRoutine(t, rules, s) && !t.running && t.spent == 0L) return@map t
            val place = s.placeOf(t)
            val key = when {
                place != null && place.startsWith("p:") -> place.removePrefix("p:")
                place != null && place.startsWith("a:") -> place.split(":").getOrElse(1) { "asr" }
                t.running || t.spent > 0 -> s.periodAt(today, if (t.planned > 0) Clock.minuteOfDay(t.planned) else nowMin)
                t.overflow || t.planned == 0L -> ""
                else -> s.periodAt(today, Clock.minuteOfDay(t.planned))
            }
            t.copy(period = key)
        }
        store.putAll(final)
        for ((old, new) in tasks.zip(final)) if (old.open && new.day != old.day) log(old, "rolled", "فوق حد التمارين باليوم")
        Alarms.reschedule(context, final.filter { it.day == ts }, s)
    }

    // ---------- task actions ----------

    suspend fun save(task: Task, asDefault: Boolean = false) = locked {
        require(Maths.valid(task.title, task.minutes))
        val old = store.task(task.id)
        val spentNow = old?.let { Maths.spent(it, System.currentTimeMillis()) } ?: task.spent
        require(task.minutes * 60_000L >= spentNow) { "المدة أقل من الوقت المصروف" }
        store.put(task.copy(title = task.title.trim()))
        if (asDefault && task.ruleId != null) {
            store.rule(task.ruleId)?.let { r ->
                store.put(
                    r.copy(
                        title = if (task.phaseCount > 1 || task.isPlan) r.title else task.title.trim(),
                        minutes = if (task.phaseCount > 1 || task.isPlan) r.minutes else task.minutes,
                        priority = task.priority, interruptible = task.interruptible, pinned = task.pinned,
                        preferredMinute = if (task.pinned && task.planned > 0) Clock.minuteOfDay(task.planned) else -1,
                        sets = task.sets, workSec = task.workSec, restSec = task.restSec,
                        category = task.category, sport = task.sport,
                    )
                )
            }
        }
        replanLocked()
    }

    /** New one-off task; split into phases over the day when [phases] has more than one part. */
    suspend fun saveNew(task: Task, phases: List<Int>) = locked {
        require(Maths.valid(task.title, task.minutes))
        val n = phases.size
        if (n <= 1) store.put(task.copy(title = task.title.trim(), units = task.minutes))
        else {
            val grp = "grp:" + UUID.randomUUID()
            phases.forEachIndexed { k, p ->
                val m = (task.minutes * p / 100.0).roundToInt().coerceAtLeast(5)
                store.put(
                    task.copy(
                        id = UUID.randomUUID().toString(), title = task.title.trim() + phaseSuffix(k, n), minutes = m, units = m,
                        phase = k, phaseCount = n, pct = p, note = grp,
                        pinned = task.pinned && k == 0, planned = if (k == 0) task.planned else 0,
                    )
                )
            }
        }
        replanLocked()
    }

    suspend fun saveRule(rule: Rule) = locked {
        val old = store.rule(rule.id)
        store.put(rule)
        val today = Clock.today()
        store.allTasks().filter { it.ruleId == rule.id && it.day >= today.toString() && it.open && !it.running && it.spent == 0L && it.doneUnits == 0 }
            .forEach { store.delete(it.id) }
        if (rule.active && rule.category == "sport" && (old == null || !old.active)) restaggerLocked(setOf(rule.id))
        ensureDayLocked(today)
        replanLocked()
    }

    suspend fun deleteRule(id: String) = locked {
        store.deleteRule(id)
        store.allTasks().filter { it.ruleId == id && it.open && it.spent == 0L && !it.running }.forEach { store.delete(it.id) }
        replanLocked()
    }

    /** Start / pause toggle. Starting a task pauses any other running task. */
    suspend fun timer(id: String) = locked {
        val now = System.currentTimeMillis()
        val t = store.task(id) ?: return@locked
        if (t.running) {
            store.put(t.copy(spent = Maths.spent(t, now), started = 0)); log(t, "pause")
        } else {
            if (!t.open) return@locked
            startLocked(t, now)
        }
        replanLocked()
    }

    private suspend fun startLocked(t0: Task, now: Long) {
        store.allTasks().filter { it.running && it.id != t0.id }
            .forEach { store.put(it.copy(spent = Maths.spent(it, now), started = 0)); log(it, "pause") }
        // Time already used up: give it a few more minutes instead of silently refusing to start.
        val t = if (Maths.remaining(t0, now) <= 0) t0.copy(minutes = ((t0.spent + 59_999) / 60_000).toInt() + 10).also { log(it, "extend", "+10 د عند البدء") } else t0
        if (t.spent == 0L) log(t, "start", delay = if (t.planned > 0) ((now - t.planned) / 60_000).toInt() else 0)
        val today = Clock.today().toString()
        store.put(t.copy(started = now, day = today, planned = if (t.spent == 0L) now else t.planned, period = if (t.day == today) t.period else ""))
    }

    /** Starts [id] right now; the task that was due now takes its place in the day. */
    suspend fun startNow(id: String) = locked {
        val now = System.currentTimeMillis()
        val t = store.task(id) ?: return@locked
        if (!t.open) return@locked
        val todays = store.tasksOn(Clock.today().toString()).filter { it.open && it.id != id && it.parallelTo == null }
        val displaced = todays.firstOrNull { it.running }
            ?: todays.filter { it.spent == 0L && it.planned > 0 }.minByOrNull { it.planned }
        if (displaced != null && !displaced.pinned && t.planned > 0) {
            store.put(displaced.copy(preferMin = Clock.minuteOfDay(t.planned)))
            log(displaced, "postpone", "بدأت «${t.baseTitle}» بداله")
        }
        startLocked(store.task(id) ?: t, now)
        replanLocked()
    }

    suspend fun finish(id: String) = locked {
        val now = System.currentTimeMillis()
        val t = store.task(id) ?: return@locked
        snapshotLocked("إنهاء «${t.baseTitle}»")
        if (t.isPlan) {
            val plans = plansLocked()
            val mem = memLastOf(plans)
            val delta = (t.units - t.doneUnits).coerceAtLeast(0)
            store.put(t.copy(state = "done", spent = Maths.spent(t, now), started = 0, finishedAt = now, doneUnits = maxOf(t.units, t.doneUnits)))
            savePlansLocked(plans.map { p ->
                if ("plan-${p.id}" != t.ruleId) p
                else PlanMath.applyUnits(p, delta, mem).let { np -> if (np.dayKey == t.day) np.copy(dayDone = np.dayDone + delta) else np }
            })
        } else {
            store.put(t.copy(state = "done", spent = Maths.spent(t, now), started = 0, finishedAt = now))
        }
        t.ruleId?.let { rid -> store.rule(rid)?.let { store.put(it.copy(lastDoneDay = maxOf(t.day, it.lastDoneDay ?: ""))) } }
        log(t, "finish", delay = if (t.planned > 0) ((now - t.planned - t.minutes * 60_000L) / 60_000).toInt() else 0)
        replanLocked()
    }

    /** One page of the review was heard (called by the player). */
    suspend fun reviewPageDone(taskId: String) = locked {
        val t = store.task(taskId) ?: return@locked
        val plans = plansLocked()
        if (plans.none { "plan-${it.id}" == t.ruleId }) return@locked
        val mem = memLastOf(plans)
        store.put(t.copy(doneUnits = t.doneUnits + 1))
        savePlansLocked(plans.map { p ->
            if ("plan-${p.id}" != t.ruleId) p
            else PlanMath.applyUnits(p, 1, mem).let { np -> if (np.dayKey == t.day) np.copy(dayDone = np.dayDone + 1) else np }
        })
        replanLocked()
    }

    /** Pages to play for a review task: its share, or (continue) everything left for today. */
    suspend fun reviewPages(taskId: String, cont: Boolean): List<Int> = locked {
        val t = store.task(taskId) ?: return@locked emptyList()
        val plans = plansLocked()
        val p = plans.firstOrNull { "plan-${it.id}" == t.ruleId } ?: return@locked emptyList()
        val count = if (cont) (p.dayQuota - p.dayDone).coerceAtLeast(0) else (t.units - t.doneUnits).coerceAtLeast(0)
        PlanMath.reviewPages(p, 0, count, memLastOf(plans))
    }

    suspend fun reopen(id: String) = locked {
        val t = store.task(id) ?: return@locked
        store.put(t.copy(state = "planned", finishedAt = 0))
        replanLocked()
    }

    suspend fun extend(id: String, minutes: Int) = locked {
        val t = store.task(id) ?: return@locked
        store.put(t.copy(minutes = (t.minutes + minutes).coerceAtMost(10080)))
        log(t, "extend", "+$minutes د")
        replanLocked()
    }

    /**
     * Moves the task to tomorrow; the scheduler places it there automatically.
     * For a recurring task [mode] says what happens with tomorrow's own session:
     * merge (one longer session), keep (both), skip (drop this one, the series keeps its dates),
     * move (this one replaces tomorrow's and the series shifts, like carrying at day end).
     */
    suspend fun postpone(id: String, reason: String = "", mode: String = "move") = locked {
        val now = System.currentTimeMillis()
        val t = store.task(id) ?: return@locked
        snapshotLocked("تأجيل «${t.baseTitle}»")
        val rule = t.ruleId?.let { store.rule(it) }
        if (rule != null && mode != "move") {
            postponeRecurringLocked(t, rule, reason.trim(), mode, now)
            replanLocked()
            return@locked
        }
        log(t, "postpone", reason.trim())
        val tomorrow = LocalDate.parse(t.day).coerceAtLeast(Clock.today()).plusDays(1)
        shiftSessionLocked(t, tomorrow.toString())
        store.put(
            t.copy(
                day = tomorrow.toString(), spent = Maths.spent(t, now), started = 0,
                planned = if (t.pinned) Clock.epoch(tomorrow, Clock.minuteOfDay(t.planned)) else 0,
                parallelTo = null, overflow = false, period = "",
            )
        )
        replanLocked()
    }

    private suspend fun postponeRecurringLocked(t0: Task, r: Rule, reason: String, mode: String, now: Long) {
        val t = t0.copy(spent = Maths.spent(t0, now), started = 0, parallelTo = null, overflow = false)
        if (mode == "skip") {
            store.put(t.copy(state = "skipped", note = if (t.note.startsWith("grp:")) t.note else "تخطّيت الجلسة", finishedAt = now))
            log(t, "missed", listOf("تخطّي الجلسة المؤجلة", reason).filter { it.isNotBlank() }.joinToString(" · "))
            if (r.weekdays == 0) store.put(r.copy(lastDoneDay = maxOf(t.day, r.lastDoneDay ?: "")))
            return
        }
        val tomorrow = LocalDate.parse(t.day).coerceAtLeast(Clock.today()).plusDays(1)
        val ts = tomorrow.toString()
        // Tomorrow's own session is created first, so moving this one does not take its place.
        if (store.tasksOn(ts).none { it.ruleId == r.id } && dueOnLocked(r, tomorrow, t.id)) ensureDayLocked(tomorrow, r.id)
        val own = store.tasksOn(ts).filter { it.ruleId == r.id && it.open && it.id != t.id }.minByOrNull { it.phase }
        val left = ((t.minutes * 60_000L - t.spent).coerceAtLeast(0) / 60_000).toInt().coerceAtLeast(5)
        if (mode == "merge" && own != null) {
            store.put(own.copy(minutes = (own.minutes + left).coerceAtMost(10080), units = own.units + (t.units - t.doneUnits).coerceAtLeast(0)))
            store.put(t.copy(state = "skipped", note = if (t.note.startsWith("grp:")) t.note else "اندمجت مع جلسة بكرا", finishedAt = now, minutes = maxOf(1, (t.spent / 60_000).toInt()), units = t.doneUnits))
            log(t, "postpone", listOf("دمج مع جلسة بكرا (+${left} د)", reason).filter { it.isNotBlank() }.joinToString(" · "))
            if (r.weekdays == 0) store.put(r.copy(lastDoneDay = maxOf(t.day, r.lastDoneDay ?: "")))
        } else {
            store.put(t.copy(day = ts, planned = if (t.pinned) Clock.epoch(tomorrow, Clock.minuteOfDay(t.planned)) else 0, preferMin = -1, period = "",
                note = if (t.note.isEmpty()) "keep" else t.note))
            log(t, "postpone", listOf("لبكرا مع جلسة بكرا", reason).filter { it.isNotBlank() }.joinToString(" · "))
            if (r.weekdays == 0) store.put(r.copy(lastDoneDay = maxOf(t0.day, r.lastDoneDay ?: "")))
        }
    }

    /** Would the rule have its own session on [day] if [except] did not exist? */
    private suspend fun dueOnLocked(r: Rule, day: LocalDate, except: String): Boolean {
        if (!r.active) return false
        val ds = day.toString(); val ts = Clock.today().toString()
        val instLast = store.allTasks().filter { it.id != except && it.ruleId == r.id && it.state != "skipped" && it.day < ds && (it.done || it.day >= ts) }.maxOfOrNull { it.day }
        val last = listOfNotNull(r.lastDoneDay, instLast).maxOrNull()?.let(LocalDate::parse)
        return Scheduler.isDue(r.everyHours, r.weekdays, LocalDate.parse(r.anchorDay), last, day)
    }

    /** Time is up but the work is not: pause and keep [leftMin] more minutes for later today. */
    suspend fun stopKeepRest(id: String, leftMin: Int) = locked {
        val now = System.currentTimeMillis()
        val t = store.task(id) ?: return@locked
        snapshotLocked("إيقاف «${t.baseTitle}»")
        val spent = Maths.spent(t, now)
        val spentMin = ((spent + 59_999) / 60_000).toInt()
        store.put(t.copy(started = 0, spent = spent, minutes = (spentMin + leftMin.coerceIn(1, 600)).coerceAtMost(10080),
            preferMin = Clock.minuteOfDay(now) + 30, pinned = false))
        log(t, "pause", "انوقفت وباقي $leftMin د")
        replanLocked()
    }

    /** Moves an open task of today into a prayer period (today only). */
    suspend fun moveToPeriod(id: String, key: String) = locked {
        val t = store.task(id) ?: return@locked
        val s = settingsLocked()
        val p = s.periodsOn(LocalDate.parse(t.day)).firstOrNull { it.key == key } ?: return@locked
        snapshotLocked("نقل «${t.baseTitle}»")
        val now = System.currentTimeMillis()
        store.put(t.copy(preferMin = maxOf(p.start + s.prayerAfter, if (t.day == Clock.today().toString()) Clock.minuteOfDay(now) else 0),
            pinned = false, started = 0, spent = Maths.spent(t, now), period = key))
        log(t, "postpone", "لفترة ${p.name}")
        replanLocked()
    }

    /** Where a series sits in prayer mode ("" = by its group). */
    suspend fun setPlace(series: String, place: String) = locked {
        val o = JSONObject(store.meta("places")?.takeIf { it.isNotBlank() } ?: "{}")
        if (place.isEmpty()) o.remove(series) else o.put(series, place)
        store.put(Meta("places", o.toString()))
        replanLocked()
    }

    suspend fun setGroupPeriod(group: String, period: String) = locked {
        val o = JSONObject(store.meta("groupPeriods")?.takeIf { it.isNotBlank() } ?: "{}")
        if (period.isEmpty()) o.remove(group) else o.put(group, period)
        store.put(Meta("groupPeriods", o.toString()))
        replanLocked()
    }

    // ---------- undo ----------

    /** Keeps the whole state before an important action so it can be undone once. */
    private suspend fun snapshotLocked(label: String) {
        java.io.File(context.filesDir, "undo.json").writeText(backupLocked())
        store.put(Meta("undoLabel", label))
        store.put(Meta("undoAt", System.currentTimeMillis().toString()))
    }

    /** From the period-end notification: the period's open tasks go to the next period, or to tomorrow. */
    suspend fun movePeriodTasks(from: String, toTomorrow: Boolean) = locked {
        val today = Clock.today()
        val s = settingsLocked()
        val open = store.tasksOn(today.toString()).filter { it.open && !it.running && it.period == from }
        if (open.isEmpty()) return@locked
        snapshotLocked(if (toTomorrow) "تأجيل مهام فترة ${Prayer.names[from]}" else "نقل مهام فترة ${Prayer.names[from]}")
        if (toTomorrow) open.forEach { endTaskLocked(it, "carry", "") }
        else {
            val periods = s.periodsOn(today)
            val next = periods.getOrNull(periods.indexOfFirst { it.key == from } + 1)
            if (next == null) open.forEach { endTaskLocked(it, "carry", "") }
            else open.forEach { t ->
                store.put(t.copy(period = next.key, preferMin = next.start + s.prayerAfter))
                log(t, "postpone", "لفترة ${next.name}")
            }
        }
        replanLocked()
    }

    suspend fun undo(): String? = locked {
        val f = java.io.File(context.filesDir, "undo.json")
        val text = (if (f.exists()) f.readText() else store.meta("undo"))?.takeIf { it.isNotBlank() } ?: return@locked null
        val label = store.meta("undoLabel") ?: ""
        restoreLocked(text)
        f.delete(); store.put(Meta("undo", "")); store.put(Meta("undoLabel", ""))
        replanLocked()
        label
    }

    suspend fun bringToToday(id: String) = locked {
        val t = store.task(id) ?: return@locked
        store.put(t.copy(day = Clock.today().toString(), pinned = false, planned = 0, state = "planned", period = ""))
        replanLocked()
    }

    suspend fun delete(id: String) = locked {
        store.task(id)?.let { snapshotLocked("حذف «${it.baseTitle}»") }
        store.delete(id)
        replanLocked()
    }

    /** Moves every unfinished task of today to tomorrow. */
    /**
     * Ends a day. carry: unfinished tasks move to the next day (and their series shift after them).
     * close: the plan stays as it is; unfinished tasks are recorded as not done.
     */
    suspend fun closeDay(mode: String, day: LocalDate = Clock.today()): Int = locked {
        val n = closeDayLocked(day, mode)
        replanLocked()
        n
    }

    private suspend fun closeDayLocked(day: LocalDate, mode: String): Int {
        val ds = day.toString()
        val open = store.tasksOn(ds).filter { it.open }
        for (t in open) endTaskLocked(t, mode, "أُغلق اليوم وهي غير منجزة")
        store.put(Meta("closed:$ds", mode))
        return open.size
    }

    /**
     * Takes one unfinished task out of its day.
     * carry: to the next day (its series follows). close: not done, the plan keeps its dates.
     */
    private suspend fun endTaskLocked(t0: Task, mode: String, why: String) {
        val now = System.currentTimeMillis()
        val ds = t0.day
        val next = LocalDate.parse(ds).plusDays(1)
        val t = if (t0.running) t0.copy(spent = Maths.spent(t0, now), started = 0) else t0
        if (mode == "carry") {
            shiftSessionLocked(t, next.toString())
            // If the next day already has this rule's own task, it steps aside for this one.
            t.ruleId?.let { rid -> store.tasksOn(next.toString()).filter { it.ruleId == rid && it.open && it.spent == 0L && it.doneUnits == 0 }.forEach { store.delete(it.id) } }
            store.put(t.copy(day = next.toString(), planned = if (t.pinned && t.planned > 0) Clock.epoch(next, Clock.minuteOfDay(t.planned)) else 0,
                parallelTo = null, overflow = false, preferMin = -1, period = ""))
            log(t, "postpone", "ترحيل لليوم التالي")
        } else {
            store.put(t.copy(state = "skipped", note = if (t.note.startsWith("grp:")) t.note else "أُغلقت بدون إنجاز", finishedAt = now, started = 0))
            log(t, "missed", why + if (t.isPlan && t.units > t.doneUnits) " (${t.units - t.doneUnits} ما انعملت)" else "")
            t.ruleId?.let { rid -> store.rule(rid)?.let { r -> if (r.weekdays == 0) store.put(r.copy(lastDoneDay = maxOf(ds, r.lastDoneDay ?: ""))) } }
            if (t.isPlan) {
                val plans = plansLocked()
                val mem = memLastOf(plans)
                savePlansLocked(plans.map { p ->
                    if ("plan-${p.id}" != t.ruleId || p.kind in setOf("memorize", "amount")) p
                    else PlanMath.applyUnits(p, (t.units - t.doneUnits).coerceAtLeast(0), mem)
                })
            }
        }
    }

    /** Swipe action on one task: carry to the next day, close it, or move it to a free time later the same day. */
    suspend fun deferTask(id: String, mode: String) = locked {
        val t = store.task(id) ?: return@locked
        if (!t.open) return@locked
        snapshotLocked(if (mode == "close") "إغلاق «${t.baseTitle}»" else "تأجيل «${t.baseTitle}»")
        when (mode) {
            "later" -> {
                val now = System.currentTimeMillis()
                val from = maxOf(if (t.planned > 0) Clock.minuteOfDay(t.planned) + t.minutes else 0, Clock.minuteOfDay(now))
                store.put(t.copy(preferMin = from, started = 0, spent = Maths.spent(t, now), pinned = false))
                log(t, "postpone", "لوقت فاضي لاحقاً بنفس اليوم")
            }
            else -> endTaskLocked(t, mode, "انسكّرت بدون إنجاز")
        }
        replanLocked()
    }

    suspend fun isClosed(day: LocalDate): Boolean = store.meta("closed:$day") != null

    /** Colour and icon for several series at once (a goal's tasks, set from the year plan). */
    suspend fun setLook(series: List<String>, color: Int?, icon: String?) = locked {
        if (color != null) {
            val c = JSONObject(store.meta("colors")?.takeIf { it.isNotBlank() } ?: "{}")
            series.forEach { c.put(it, color) }
            store.put(Meta("colors", c.toString()))
        }
        if (icon != null) {
            val i = JSONObject(store.meta("icons")?.takeIf { it.isNotBlank() } ?: "{}")
            series.forEach { if (icon.isEmpty()) i.remove(it) else i.put(it, icon) }
            store.put(Meta("icons", i.toString()))
        }
    }

    /** Routine flag and colour of a task's series. */
    suspend fun setTag(series: String, routine: Boolean, color: Int) = locked {
        val r = JSONObject(store.meta("routine")?.takeIf { it.isNotBlank() } ?: "{}").put(series, routine)
        val c = JSONObject(store.meta("colors")?.takeIf { it.isNotBlank() } ?: "{}").put(series, color)
        store.put(Meta("routine", r.toString())); store.put(Meta("colors", c.toString()))
    }

    // ---------- goals ----------

    suspend fun goal(g: Goal, logValue: Boolean) = locked {
        val old = store.goal(g.id)
        store.put(g)
        if (logValue && (old == null || old.current != g.current)) store.put(GoalLog(goalId = g.id, day = Clock.today().toString(), value = g.current))
    }

    /** A new goal from the user, with an optional recurring session that feeds the schedule. */
    suspend fun addGoal(g: Goal, session: Rule?) = locked {
        store.put(g)
        store.put(GoalLog(goalId = g.id, day = Clock.today().toString(), value = g.current))
        if (session != null) {
            store.put(session)
            if (session.category == "sport" && session.active) restaggerLocked(setOf(session.id))
            ensureDayLocked(Clock.today())
        }
        replanLocked()
    }

    suspend fun deleteGoal(id: String) = locked {
        store.deleteGoal(id)
        store.allRules().filter { it.goalId == id && it.members.isBlank() }.forEach { r ->
            store.deleteRule(r.id)
            store.allTasks().filter { it.ruleId == r.id && it.open && it.spent == 0L }.forEach { store.delete(it.id) }
        }
        replanLocked()
    }

    // ---------- backup ----------

    suspend fun backup(): String = locked { backupLocked() }

    private suspend fun backupLocked(): String {
        val tasks = JSONArray()
        store.allTasks().forEach { t ->
            tasks.put(
                JSONObject().put("id", t.id).put("title", t.title).put("minutes", t.minutes).put("planned", t.planned)
                    .put("day", t.day).put("state", t.state).put("spent", Maths.spent(t, System.currentTimeMillis()))
                    .put("original", t.original).put("pinned", t.pinned).put("priority", t.priority)
                    .put("interruptible", t.interruptible).put("kind", t.kind).put("ruleId", t.ruleId ?: JSONObject.NULL)
                    .put("goalId", t.goalId ?: JSONObject.NULL).put("projectId", t.projectId ?: JSONObject.NULL)
                    .put("sets", t.sets).put("workSec", t.workSec).put("restSec", t.restSec).put("created", t.created)
                    .put("finishedAt", t.finishedAt).put("category", t.category).put("sport", t.sport).put("phase", t.phase)
                    .put("phaseCount", t.phaseCount).put("pct", t.pct).put("units", t.units).put("doneUnits", t.doneUnits).put("note", t.note)
                    .put("atWork", t.atWork).put("preferMin", t.preferMin).put("period", t.period).put("details", t.details)
            )
        }
        val goals = JSONArray()
        store.allGoals().forEach { g ->
            goals.put(
                JSONObject().put("id", g.id).put("name", g.name).put("category", g.category).put("current", g.current)
                    .put("reference", g.reference).put("unit", g.unit).put("lower", g.lower).put("baseline", g.baseline)
                    .put("startDay", g.startDay).put("targetDay", g.targetDay)
            )
        }
        val projects = JSONArray()
        store.allProjects().forEach { p -> projects.put(JSONObject().put("id", p.id).put("name", p.name).put("stages", JSONArray(p.stages))) }
        val rules = JSONArray()
        store.allRules().forEach { r ->
            rules.put(
                JSONObject().put("id", r.id).put("title", r.title).put("minutes", r.minutes).put("everyHours", r.everyHours)
                    .put("weekdays", r.weekdays).put("preferredMinute", r.preferredMinute).put("pinned", r.pinned)
                    .put("priority", r.priority).put("interruptible", r.interruptible).put("kind", r.kind)
                    .put("goalId", r.goalId ?: JSONObject.NULL).put("sets", r.sets).put("workSec", r.workSec)
                    .put("restSec", r.restSec).put("active", r.active).put("anchorDay", r.anchorDay)
                    .put("lastDoneDay", r.lastDoneDay ?: JSONObject.NULL).put("source", r.source).put("role", r.role)
                    .put("category", r.category).put("sport", r.sport).put("phases", r.phases).put("members", r.members).put("atWork", r.atWork)
            )
        }
        val logs = JSONArray()
        store.allLogs().forEach { l -> logs.put(JSONObject().put("id", l.id).put("goalId", l.goalId).put("day", l.day).put("value", l.value)) }
        val meta = JSONObject()
        (Settings.KEYS + listOf("plans", "months", "seed-v3", "seed-v4", "seed-v5", "seed-v6", "targets", "routineWeeks", "igPeople2")).forEach { k -> store.meta(k)?.let { meta.put(k, it) } }
        store.allMeta().filter { it.key.startsWith("pomo:") || it.key.startsWith("dayPlanned:") || it.key.startsWith("quran:") }.forEach { meta.put(it.key, it.value) }
        val events = JSONArray()
        store.allEvents().forEach { e ->
            events.put(JSONObject().put("id", e.id).put("taskId", e.taskId ?: JSONObject.NULL).put("series", e.series).put("day", e.day)
                .put("at", e.at).put("type", e.type).put("text", e.text).put("delayMin", e.delayMin))
        }
        return JSONObject().put("schema", 4).put("createdAt", System.currentTimeMillis()).put("tasks", tasks).put("goals", goals).put("projects", projects)
            .put("rules", rules).put("logs", logs).put("events", events).put("settings", meta).toString(2)
    }

    /** Accepts backups of every version of the app. The current data is kept first so the restore can be undone. */
    suspend fun restore(text: String) = locked {
        val root = JSONObject(text)
        require(root.optInt("schema", 1) in 1..4) { "صيغة غير معروفة" }
        val safety = backupLocked()
        java.io.File(context.filesDir, "safety.json").writeText(safety)
        restoreLocked(text)
        java.io.File(context.filesDir, "undo.json").writeText(safety); store.put(Meta("undoLabel", "الاستعادة"))
        ensureDayLocked(Clock.today())
        replanLocked()
    }

    private suspend fun restoreLocked(text: String) {
        val root = JSONObject(text)
        val schema = root.optInt("schema", 1)
        require(schema in 1..4) { "صيغة غير معروفة" }
        fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
        db.withTransaction {
            store.clearTasks(); store.clearGoals(); store.clearProjects(); store.clearRules(); store.clearLogs(); store.clearEvents()
            val tasks = root.optJSONArray("tasks") ?: JSONArray()
            for (i in 0 until tasks.length()) {
                val o = tasks.getJSONObject(i)
                val minutes = o.getInt("minutes").coerceIn(1, 10080)
                store.put(
                    Task(
                        id = o.getString("id"), title = o.getString("title"), minutes = minutes,
                        planned = o.optLong("planned"), day = o.getString("day"),
                        state = o.optString("state", "planned").let { if (it == "done" || it == "skipped") it else "planned" },
                        spent = o.optLong("spent").coerceIn(0L, minutes * 60_000L), started = 0,
                        original = o.optLong("original"),
                        pinned = if (schema == 1) o.optLong("planned") > 0 else o.optBoolean("pinned"),
                        priority = o.optInt("priority", 1), interruptible = o.optBoolean("interruptible"),
                        kind = o.optString("kind", "task"), ruleId = o.str("ruleId"), goalId = o.str("goalId"),
                        projectId = o.str("projectId"), sets = o.optInt("sets"), workSec = o.optInt("workSec"),
                        restSec = o.optInt("restSec"), created = o.optLong("created", System.currentTimeMillis()),
                        finishedAt = o.optLong("finishedAt"), category = o.optString("category", "other"),
                        sport = o.optInt("sport"), phase = o.optInt("phase"), phaseCount = o.optInt("phaseCount", 1),
                        pct = o.optInt("pct", 100), units = o.optInt("units"), doneUnits = o.optInt("doneUnits"), note = o.optString("note"),
                        atWork = o.optBoolean("atWork"), preferMin = o.optInt("preferMin", -1), period = o.optString("period"), details = o.optString("details"),
                    )
                )
            }
            val goals = root.optJSONArray("goals") ?: JSONArray()
            for (i in 0 until goals.length()) {
                val o = goals.getJSONObject(i)
                store.put(
                    Goal(
                        id = o.getString("id"), name = o.getString("name"), category = o.optString("category"),
                        current = o.getDouble("current"), reference = o.getDouble("reference"), unit = o.optString("unit"),
                        lower = o.optBoolean("lower"), baseline = o.optDouble("baseline", o.getDouble("current")),
                        startDay = o.optString("startDay", Clock.today().toString()),
                        targetDay = o.optString("targetDay", Clock.today().plusYears(1).toString()),
                    )
                )
            }
            val projects = root.optJSONArray("projects") ?: JSONArray()
            for (i in 0 until projects.length()) {
                val o = projects.getJSONObject(i)
                store.put(Project(o.getString("id"), o.getString("name"), o.optJSONArray("stages")?.toString() ?: "[]"))
            }
            val rules = root.optJSONArray("rules") ?: JSONArray()
            for (i in 0 until rules.length()) {
                val o = rules.getJSONObject(i)
                store.put(
                    Rule(
                        id = o.getString("id"), title = o.getString("title"), minutes = o.getInt("minutes"),
                        everyHours = o.optInt("everyHours", 24), weekdays = o.optInt("weekdays"),
                        preferredMinute = o.optInt("preferredMinute", -1), pinned = o.optBoolean("pinned"),
                        priority = o.optInt("priority", 1), interruptible = o.optBoolean("interruptible"),
                        kind = o.optString("kind", "task"), goalId = o.str("goalId"), sets = o.optInt("sets"),
                        workSec = o.optInt("workSec"), restSec = o.optInt("restSec"), active = o.optBoolean("active", true),
                        anchorDay = o.optString("anchorDay", Clock.today().toString()), lastDoneDay = o.str("lastDoneDay"),
                        source = o.optString("source"), role = o.optString("role", "main"),
                        category = o.optString("category", "other"), sport = o.optInt("sport"),
                        phases = o.optString("phases"), members = o.optString("members"), atWork = o.optBoolean("atWork"),
                    )
                )
            }
            val logs = root.optJSONArray("logs") ?: JSONArray()
            for (i in 0 until logs.length()) {
                val o = logs.getJSONObject(i)
                store.put(GoalLog(o.getString("id"), o.getString("goalId"), o.getString("day"), o.getDouble("value")))
            }
            val settings = root.optJSONObject("settings")
            settings?.let { s -> s.keys().forEach { k -> store.put(Meta(k, s.getString(k))) } }
            if (settings?.has("plans") != true) store.put(Meta("plans", "[]"))
            store.put(Meta("seed-v2", "1"))
            if (settings?.has("seed-v3") != true) { seedV3(); store.put(Meta("seed-v3", "1")) }
            if (settings?.has("seed-v4") != true) { seedV4(); store.put(Meta("seed-v4", "1")) }
            val events = root.optJSONArray("events") ?: JSONArray()
            for (i in 0 until events.length()) {
                val o = events.getJSONObject(i)
                store.put(Event(o.getString("id"), o.str("taskId"), o.getString("series"), o.getString("day"), o.getLong("at"), o.getString("type"), o.optString("text"), o.optInt("delayMin")))
            }
            if (settings?.has("seed-v5") != true) { seedV5(); store.put(Meta("seed-v5", "1")) }
        }
    }

    companion object {
        const val POMO_MS = 25 * 60_000L
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) { instance ?: Repository(context.applicationContext).also { instance = it } }
    }
}
