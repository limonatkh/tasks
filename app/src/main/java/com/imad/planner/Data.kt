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

fun seriesOf(t: Task) = t.ruleId ?: t.note.takeIf { it.startsWith("grp:") } ?: t.id

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

@Database(entities = [Task::class, Rule::class, Goal::class, GoalLog::class, Project::class, Meta::class, Event::class], version = 3, exportSchema = false)
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
) {
    /** Before waking up or after sleep time: no tasks. */
    fun isNight(minute: Int) = minute < dayStart || minute >= dayEnd

    val shape get() = DayShape(dayStart, dayEnd, morning, workEnd, sportEnd)
    val config get() = Config(dayStart, dayEnd, gap, maxSports, maxHeavy, sportGap)

    fun jobToday(day: LocalDate) = jobOn && jobEnd > jobStart && (jobDays and Scheduler.weekdayBit(day.dayOfWeek)) != 0

    fun configFor(day: LocalDate) = config.copy(blocks = if (jobToday(day)) listOf(jobStart to jobEnd) else emptyList())

    fun group(key: String): Group? = groups.firstOrNull { it.key == key }

    /** The day part a group goes to; old keys keep their meaning. */
    fun slotOf(category: String): String = when {
        category == "sport" -> "sport"
        else -> group(category)?.slot ?: when (category) { "quran", "reading" -> "morning"; "work" -> "work"; else -> "any" }
    }

    companion object {
        val KEYS = listOf("dayStart", "dayEnd", "gap", "extend", "insistent", "alarms", "morning", "workEnd", "sportEnd", "maxSports", "maxHeavy", "sportGap",
            "jobOn", "jobStart", "jobEnd", "jobDays", "timerStyle", "groups", "closeDefault", "colors", "routine", "icons", "nameStyle")
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
            ).also { Cat.groups = it.groups }
        }
    }
}

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
fun itemOf(t: Task, rules: Map<String, Rule>, s: Settings, nowMs: Long?, nowMin: Int?): Item {
    val inProgress = nowMs != null && (t.running || t.spent > 0)
    val w = Windows.of(s.slotOf(t.category), t.sport, s.shape)
    val e = Windows.phaseEarliest(t.phase, t.phaseCount, s.shape)
    return Item(
        id = t.id, minutes = t.minutes,
        fixedStart = if (t.pinned && t.planned > 0 && !inProgress) Clock.minuteOfDay(t.planned) else null,
        preferred = if (t.pinned) null else t.preferMin.takeIf { it >= 0 }
            ?: if (t.phaseCount == 1) t.ruleId?.let { rules[it] }?.preferredMinute?.takeIf { it >= 0 } else null,
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
    val db: PlannerDb = Room.databaseBuilder(context, PlannerDb::class.java, "planner-v2.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    val store: Store = db.store()
    private val lock = Mutex()

    private suspend fun settingsLocked(): Settings = Settings.from(Settings.KEYS.mapNotNull { k -> store.meta(k)?.let { k to it } }.toMap())

    suspend fun setSetting(key: String, value: String) = lock.withLock {
        store.put(Meta(key, value))
        replanLocked()
    }

    // ---------- seed ----------

    suspend fun initialize() = lock.withLock {
        if (store.meta("seed-v2") == null) {
            db.withTransaction { seedGoalsAndProjects(); store.put(Meta("seed-v2", "1")) }
        }
        if (store.meta("seed-v3") == null) { seedV3(); store.put(Meta("seed-v3", "1")) }
        if (store.meta("seed-v4") == null) { seedV4(); store.put(Meta("seed-v4", "1")) }
        if (store.meta("seed-v5") == null) { seedV5(); store.put(Meta("seed-v5", "1")) }
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

    suspend fun addNote(taskId: String, text: String) = lock.withLock {
        val t = store.task(taskId) ?: return@withLock
        if (text.isNotBlank()) log(t, "note", text.trim())
    }

    // ---------- groups ----------

    suspend fun saveGroups(list: List<Group>) = lock.withLock {
        store.put(Meta("groups", Groups.encode(list)))
        replanLocked()
    }

    /** Moves a goal / plan / recurring task to another group. */
    suspend fun moveToGroup(goalId: String?, planId: String?, ruleId: String?, group: String) = lock.withLock {
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
    suspend fun savePlan(plan: Plan, phases: String? = null) = lock.withLock {
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

    suspend fun deletePlan(id: String) = lock.withLock {
        savePlansLocked(plansLocked().filter { it.id != id })
        store.deleteRule("plan-$id")
        store.allTasks().filter { it.ruleId == "plan-$id" && it.open && it.spent == 0L && !it.running }.forEach { store.delete(it.id) }
        replanLocked()
    }

    /** Turns on the training sessions of these goals and spreads them over the coming days. */
    suspend fun acceptGoals(ids: Collection<String>) = lock.withLock {
        val set = ids.toSet()
        val today = Clock.today().toString()
        val targets = store.allRules().filter { !it.active && Members.goals(it).any { g -> g in set } }
        targets.forEach { store.put(it.copy(active = true, anchorDay = today)) }
        restaggerLocked(targets.filter { it.category == "sport" }.map { it.id }.toSet())
        ensureDayLocked(Clock.today())
        replanLocked()
    }

    suspend fun setMonth(goalKey: String, ym: String, value: Double?, reviewed: Boolean) = lock.withLock {
        val m = Months.parse(store.meta("months")).toMutableMap()
        if (value == null && !reviewed) m.remove(Months.key(goalKey, ym)) else m[Months.key(goalKey, ym)] = MonthEntry(value, reviewed)
        store.put(Meta("months", Months.encode(m)))
        // This month's target changes the daily amounts / pace from today on.
        if (goalKey.startsWith("p:") && ym == java.time.YearMonth.from(Clock.today()).toString()) {
            val today = Clock.today()
            val plans = plansLocked()
            val p = plans.firstOrNull { "p:${it.id}" == goalKey } ?: return@withLock
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

    suspend fun acceptMonths(goalKey: String, yms: List<String>) = lock.withLock {
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
        for (r in store.allRules().filter { (it.active || it.id == only) && (only == null || it.id == only) }) {
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

    suspend fun replan() = lock.withLock { replanLocked() }

    suspend fun refreshDay() = lock.withLock {
        autoCloseLocked()
        ensureDayLocked(Clock.today())
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
    suspend fun materialize(day: LocalDate) = lock.withLock {
        ensureDayLocked(day)
        replanLocked()
    }

    suspend fun materializeRule(ruleId: String, day: LocalDate): String? = lock.withLock {
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
        val tasks = store.tasksOn(ts)
        val rules = store.allRules().associateBy { it.id }
        val items = tasks.filter { it.open }.map { itemOf(it, rules, s, now, nowMin) }
        val plan = Scheduler.plan(items, s.configFor(today), nowMin)
        val tomorrow = today.plusDays(1).toString()
        val updated = tasks.map { t ->
            val p = plan.placed[t.id] ?: return@map t.copy(parallelTo = null, overflow = false)
            val inProgress = t.running || t.spent > 0 || t.doneUnits > 0
            if (t.id in plan.capped && !t.pinned && !inProgress) {
                // Over today's sport limit: it moves to tomorrow and its series shifts with it.
                return@map t.copy(day = tomorrow, planned = 0, parallelTo = null, overflow = false)
            }
            val start = if (t.pinned && !t.running && t.spent == 0L) t.planned else Clock.epoch(today, p.start)
            t.copy(
                planned = if (t.running || t.spent > 0) t.planned.takeIf { it > 0 } ?: start else start,
                original = if (t.original == 0L) start else t.original,
                parallelTo = p.parallelTo, overflow = p.overflow,
            )
        }
        store.putAll(updated)
        for ((old, new) in tasks.zip(updated)) if (old.open && new.day != old.day) log(old, "rolled", "فوق حد التمارين باليوم")
        Alarms.reschedule(context, updated.filter { it.day == ts }, s)
    }

    // ---------- task actions ----------

    suspend fun save(task: Task, asDefault: Boolean = false) = lock.withLock {
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
    suspend fun saveNew(task: Task, phases: List<Int>) = lock.withLock {
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

    suspend fun saveRule(rule: Rule) = lock.withLock {
        val old = store.rule(rule.id)
        store.put(rule)
        val today = Clock.today()
        store.allTasks().filter { it.ruleId == rule.id && it.day >= today.toString() && it.open && !it.running && it.spent == 0L && it.doneUnits == 0 }
            .forEach { store.delete(it.id) }
        if (rule.active && rule.category == "sport" && (old == null || !old.active)) restaggerLocked(setOf(rule.id))
        ensureDayLocked(today)
        replanLocked()
    }

    suspend fun deleteRule(id: String) = lock.withLock {
        store.deleteRule(id)
        store.allTasks().filter { it.ruleId == id && it.open && it.spent == 0L && !it.running }.forEach { store.delete(it.id) }
        replanLocked()
    }

    /** Start / pause toggle. Starting a task pauses any other running task. */
    suspend fun timer(id: String) = lock.withLock {
        val now = System.currentTimeMillis()
        val t = store.task(id) ?: return@withLock
        if (t.running) {
            store.put(t.copy(spent = Maths.spent(t, now), started = 0)); log(t, "pause")
        } else {
            if (!t.open) return@withLock
            startLocked(t, now)
        }
        replanLocked()
    }

    private suspend fun startLocked(t: Task, now: Long) {
        store.allTasks().filter { it.running && it.id != t.id }
            .forEach { store.put(it.copy(spent = Maths.spent(it, now), started = 0)); log(it, "pause") }
        if (Maths.remaining(t, now) <= 0) return
        if (t.spent == 0L) log(t, "start", delay = if (t.planned > 0) ((now - t.planned) / 60_000).toInt() else 0)
        store.put(t.copy(started = now, day = Clock.today().toString(), planned = if (t.spent == 0L) now else t.planned))
    }

    /** Starts [id] right now; the task that was due now takes its place in the day. */
    suspend fun startNow(id: String) = lock.withLock {
        val now = System.currentTimeMillis()
        val t = store.task(id) ?: return@withLock
        if (!t.open) return@withLock
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

    suspend fun finish(id: String) = lock.withLock {
        val now = System.currentTimeMillis()
        val t = store.task(id) ?: return@withLock
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
    suspend fun reviewPageDone(taskId: String) = lock.withLock {
        val t = store.task(taskId) ?: return@withLock
        val plans = plansLocked()
        if (plans.none { "plan-${it.id}" == t.ruleId }) return@withLock
        val mem = memLastOf(plans)
        store.put(t.copy(doneUnits = t.doneUnits + 1))
        savePlansLocked(plans.map { p ->
            if ("plan-${p.id}" != t.ruleId) p
            else PlanMath.applyUnits(p, 1, mem).let { np -> if (np.dayKey == t.day) np.copy(dayDone = np.dayDone + 1) else np }
        })
        replanLocked()
    }

    /** Pages to play for a review task: its share, or (continue) everything left for today. */
    suspend fun reviewPages(taskId: String, cont: Boolean): List<Int> = lock.withLock {
        val t = store.task(taskId) ?: return@withLock emptyList()
        val plans = plansLocked()
        val p = plans.firstOrNull { "plan-${it.id}" == t.ruleId } ?: return@withLock emptyList()
        val count = if (cont) (p.dayQuota - p.dayDone).coerceAtLeast(0) else (t.units - t.doneUnits).coerceAtLeast(0)
        PlanMath.reviewPages(p, 0, count, memLastOf(plans))
    }

    suspend fun reopen(id: String) = lock.withLock {
        val t = store.task(id) ?: return@withLock
        store.put(t.copy(state = "planned", finishedAt = 0))
        replanLocked()
    }

    suspend fun extend(id: String, minutes: Int) = lock.withLock {
        val t = store.task(id) ?: return@withLock
        store.put(t.copy(minutes = (t.minutes + minutes).coerceAtMost(10080)))
        log(t, "extend", "+$minutes د")
        replanLocked()
    }

    /** Moves the task to tomorrow; the scheduler places it there automatically. */
    suspend fun postpone(id: String, reason: String = "") = lock.withLock {
        val now = System.currentTimeMillis()
        val t = store.task(id) ?: return@withLock
        log(t, "postpone", reason.trim())
        val tomorrow = LocalDate.parse(t.day).coerceAtLeast(Clock.today()).plusDays(1)
        store.put(
            t.copy(
                day = tomorrow.toString(), spent = Maths.spent(t, now), started = 0,
                planned = if (t.pinned) Clock.epoch(tomorrow, Clock.minuteOfDay(t.planned)) else 0,
                parallelTo = null, overflow = false,
            )
        )
        replanLocked()
    }

    suspend fun bringToToday(id: String) = lock.withLock {
        val t = store.task(id) ?: return@withLock
        store.put(t.copy(day = Clock.today().toString(), pinned = false, planned = 0, state = "planned"))
        replanLocked()
    }

    suspend fun delete(id: String) = lock.withLock {
        store.delete(id)
        replanLocked()
    }

    /** Moves every unfinished task of today to tomorrow. */
    /**
     * Ends a day. carry: unfinished tasks move to the next day (and their series shift after them).
     * close: the plan stays as it is; unfinished tasks are recorded as not done.
     */
    suspend fun closeDay(mode: String, day: LocalDate = Clock.today()): Int = lock.withLock {
        val n = closeDayLocked(day, mode)
        replanLocked()
        n
    }

    private suspend fun closeDayLocked(day: LocalDate, mode: String): Int {
        val now = System.currentTimeMillis()
        val ds = day.toString()
        val next = day.plusDays(1)
        val open = store.tasksOn(ds).filter { it.open }
        var plans = plansLocked()
        val mem = memLastOf(plans)
        for (t0 in open) {
            val t = if (t0.running) t0.copy(spent = Maths.spent(t0, now), started = 0) else t0
            if (mode == "carry") {
                // If tomorrow already has this rule's own task, it steps aside for this one.
                t.ruleId?.let { rid -> store.tasksOn(next.toString()).filter { it.ruleId == rid && it.open && it.spent == 0L && it.doneUnits == 0 }.forEach { store.delete(it.id) } }
                store.put(t.copy(day = next.toString(), planned = if (t.pinned && t.planned > 0) Clock.epoch(next, Clock.minuteOfDay(t.planned)) else 0,
                    parallelTo = null, overflow = false, preferMin = -1))
                log(t, "postpone", "ترحيل إجباري لليوم التالي")
            } else {
                store.put(t.copy(state = "skipped", note = if (t.note.startsWith("grp:")) t.note else "أُغلقت بدون إنجاز", finishedAt = now))
                log(t, "missed", "أُغلق اليوم وهي غير منجزة" + if (t.isPlan && t.units > t.doneUnits) " (${t.units - t.doneUnits} ما انعملت)" else "")
                // The plan keeps its dates: the series counts this day as its turn.
                t.ruleId?.let { rid -> store.rule(rid)?.let { r -> if (r.weekdays == 0) store.put(r.copy(lastDoneDay = maxOf(ds, r.lastDoneDay ?: ""))) } }
                if (t.isPlan) plans = plans.map { p ->
                    if ("plan-${p.id}" != t.ruleId || p.kind in setOf("memorize", "amount")) p
                    else PlanMath.applyUnits(p, (t.units - t.doneUnits).coerceAtLeast(0), mem)
                }
            }
        }
        savePlansLocked(plans)
        store.put(Meta("closed:$ds", mode))
        return open.size
    }

    suspend fun isClosed(day: LocalDate): Boolean = store.meta("closed:$day") != null

    /** Colour and icon for several series at once (a goal's tasks, set from the year plan). */
    suspend fun setLook(series: List<String>, color: Int?, icon: String?) = lock.withLock {
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
    suspend fun setTag(series: String, routine: Boolean, color: Int) = lock.withLock {
        val r = JSONObject(store.meta("routine")?.takeIf { it.isNotBlank() } ?: "{}").put(series, routine)
        val c = JSONObject(store.meta("colors")?.takeIf { it.isNotBlank() } ?: "{}").put(series, color)
        store.put(Meta("routine", r.toString())); store.put(Meta("colors", c.toString()))
    }

    // ---------- goals ----------

    suspend fun goal(g: Goal, logValue: Boolean) = lock.withLock {
        val old = store.goal(g.id)
        store.put(g)
        if (logValue && (old == null || old.current != g.current)) store.put(GoalLog(goalId = g.id, day = Clock.today().toString(), value = g.current))
    }

    /** A new goal from the user, with an optional recurring session that feeds the schedule. */
    suspend fun addGoal(g: Goal, session: Rule?) = lock.withLock {
        store.put(g)
        store.put(GoalLog(goalId = g.id, day = Clock.today().toString(), value = g.current))
        if (session != null) {
            store.put(session)
            if (session.category == "sport" && session.active) restaggerLocked(setOf(session.id))
            ensureDayLocked(Clock.today())
        }
        replanLocked()
    }

    suspend fun deleteGoal(id: String) = lock.withLock {
        store.deleteGoal(id)
        store.allRules().filter { it.goalId == id && it.members.isBlank() }.forEach { r ->
            store.deleteRule(r.id)
            store.allTasks().filter { it.ruleId == r.id && it.open && it.spent == 0L }.forEach { store.delete(it.id) }
        }
        replanLocked()
    }

    // ---------- backup ----------

    suspend fun backup(): String = lock.withLock {
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
                    .put("atWork", t.atWork).put("preferMin", t.preferMin)
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
        (Settings.KEYS + listOf("plans", "months", "seed-v3", "seed-v4", "seed-v5")).forEach { k -> store.meta(k)?.let { meta.put(k, it) } }
        val events = JSONArray()
        store.allEvents().forEach { e ->
            events.put(JSONObject().put("id", e.id).put("taskId", e.taskId ?: JSONObject.NULL).put("series", e.series).put("day", e.day)
                .put("at", e.at).put("type", e.type).put("text", e.text).put("delayMin", e.delayMin))
        }
        JSONObject().put("schema", 4).put("tasks", tasks).put("goals", goals).put("projects", projects)
            .put("rules", rules).put("logs", logs).put("events", events).put("settings", meta).toString(2)
    }

    /** Accepts backups of every version of the app. */
    suspend fun restore(text: String) = lock.withLock {
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
                        atWork = o.optBoolean("atWork"), preferMin = o.optInt("preferMin", -1),
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
        ensureDayLocked(Clock.today())
        replanLocked()
    }

    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) { instance ?: Repository(context.applicationContext).also { instance = it } }
    }
}
