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
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

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
    /** First time the task was placed (for history). */
    val original: Long = 0,
    /** True when the user set the time; the scheduler never moves it. */
    val pinned: Boolean = false,
    val priority: Int = 1,
    /** Light task that can be done during rest windows of a training session. */
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
    /** Set by the scheduler: id of the training session this task runs in parallel with. */
    val parallelTo: String? = null,
    /** Set by the scheduler: does not fit in today's available time. */
    val overflow: Boolean = false,
    val finishedAt: Long = 0,
) {
    val running get() = started > 0
    val done get() = state == "done"
    val open get() = state == "planned"
}

/** A recurring task (also used for training sessions of a goal). Edits saved "as default" land here. */
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
    /** Short source note for suggested values. */
    val source: String = "",
    /** main | support */
    val role: String = "main",
)

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
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM projects") suspend fun clearProjects()
    @Query("DELETE FROM rules") suspend fun clearRules()
    @Query("DELETE FROM goal_logs") suspend fun clearLogs()
}

@Database(entities = [Task::class, Rule::class, Goal::class, GoalLog::class, Project::class, Meta::class], version = 1, exportSchema = false)
abstract class PlannerDb : RoomDatabase() {
    abstract fun store(): Store
}

// ---------------- Settings ----------------

data class Settings(
    val dayStart: Int = 8 * 60,
    val dayEnd: Int = 23 * 60,
    val gap: Int = 5,
    val extend: Int = 5,
    val insistent: Boolean = true,
    val alarms: Boolean = true,
) {
    companion object {
        fun from(m: Map<String, String>) = Settings(
            dayStart = m["dayStart"]?.toIntOrNull() ?: 8 * 60,
            dayEnd = m["dayEnd"]?.toIntOrNull() ?: 23 * 60,
            gap = m["gap"]?.toIntOrNull() ?: 5,
            extend = m["extend"]?.toIntOrNull() ?: 5,
            insistent = m["insistent"]?.let { it == "1" } ?: true,
            alarms = m["alarms"]?.let { it == "1" } ?: true,
        )
    }
}

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

// ---------------- Repository ----------------

class Repository(private val context: Context) {
    val db: PlannerDb = Room.databaseBuilder(context, PlannerDb::class.java, "planner-v2.db").build()
    val store: Store = db.store()
    private val lock = Mutex()

    suspend fun settings(): Settings = Settings.from(store.allMetaMap())

    private suspend fun Store.allMetaMap(): Map<String, String> {
        val keys = listOf("dayStart", "dayEnd", "gap", "extend", "insistent", "alarms")
        return keys.mapNotNull { k -> meta(k)?.let { k to it } }.toMap()
    }

    suspend fun setSetting(key: String, value: String) = lock.withLock {
        store.put(Meta(key, value))
        replanLocked()
    }

    // ---------- seed ----------

    suspend fun initialize() = lock.withLock {
        if (store.meta("seed-v2") == null) {
            db.withTransaction {
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
                seedTraining()
                store.put(Meta("seed-v2", "1"))
            }
        }
        if (store.meta("seed-v3") == null) {
            seedV3()
            store.put(Meta("seed-v3", "1"))
        }
        ensureDayLocked(Clock.today())
        replanLocked()
    }

    /** Version 0.3 additions: arm-wrestling forearms, boxing, reading, Quran plans. */
    private suspend fun seedV3() {
        val today = Clock.today().toString()
        val extra = listOf(
            Rule(id = "arm-forearms", title = "تمارين سواعد (مصارعة الذراعين)", minutes = Scheduler.sessionMinutes(4, 45, 120),
                everyHours = 72, kind = "training", sets = 4, workSec = 45, restSec = 120, anchorDay = today,
                source = "قيمة مقترحة: أوتار الساعد والقبضة تحتاج تعافياً أطول، 48–72 ساعة بين الجلسات"),
            Rule(id = "boxing", title = "بوكسنق", minutes = 60, everyHours = 168, anchorDay = today),
            Rule(id = "reading-books", title = "قراءة كتاب", minutes = 30, everyHours = 24, anchorDay = today),
        )
        extra.forEach { if (store.rule(it.id) == null) store.put(it) }
        if (plansLocked().isEmpty()) savePlansLocked(
            listOf(
                Plan(id = "quran-memorize", title = "حفظ القرآن", kind = "memorize", minutesPerUnit = 20),
                Plan(id = "quran-read", title = "ورد قراءة القرآن", kind = "read", minutesPerUnit = 2, perDay = 10),
                Plan(id = "quran-review", title = "مراجعة الحفظ سماعاً", kind = "review", minutesPerUnit = 1),
            )
        )
    }

    // ---------- plans (stored as JSON in meta) ----------

    suspend fun plans(): List<Plan> = lock.withLock { plansLocked() }

    private suspend fun plansLocked(): List<Plan> = Plans.parse(store.meta("plans"))

    private suspend fun savePlansLocked(list: List<Plan>) = store.put(Meta("plans", Plans.encode(list)))

    /** Saves a plan; an accepted, configured plan gets a daily rule that feeds the schedule. */
    suspend fun savePlan(plan: Plan) = lock.withLock {
        val list = plansLocked().filter { it.id != plan.id } + plan
        savePlansLocked(list)
        val ruleId = "plan-${plan.id}"
        val existing = store.rule(ruleId)
        val active = plan.accepted && plan.configured
        val rule = (existing ?: Rule(id = ruleId, title = plan.title, minutes = 15, anchorDay = Clock.today().toString()))
            .copy(active = active, title = plan.title)
        store.put(rule)
        // Rebuild today's open instance so it reflects the new numbers.
        store.tasksOn(Clock.today().toString()).filter { it.ruleId == ruleId && it.open && !it.running && it.spent == 0L }
            .forEach { store.delete(it.id) }
        ensureDayLocked(Clock.today())
        replanLocked()
    }

    suspend fun deletePlan(id: String) = lock.withLock {
        savePlansLocked(plansLocked().filter { it.id != id })
        store.deleteRule("plan-$id")
        store.tasksOn(Clock.today().toString()).filter { it.ruleId == "plan-$id" && it.open && it.spent == 0L && !it.running }
            .forEach { store.delete(it.id) }
        replanLocked()
    }

    /** Accepts the yearly plan of sports goals: their main training sessions enter the schedule. */
    suspend fun acceptGoals(ids: List<String>) = lock.withLock {
        val today = Clock.today().toString()
        store.allRules().filter { it.goalId in ids && it.role == "main" && !it.active }
            .forEach { store.put(it.copy(active = true, anchorDay = today)) }
        ensureDayLocked(Clock.today())
        replanLocked()
    }

    private suspend fun memorizedLast(plans: List<Plan>): Int =
        plans.firstOrNull { it.kind == "memorize" && it.configured }?.done ?: 0

    private suspend fun seedTraining() {
        val root = JSONObject(asset("training-seed.json"))
        val sources = root.getJSONObject("sources")
        val list = root.getJSONArray("goals")
        val today = Clock.today().toString()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            val goal = store.goal(o.getString("goal")) ?: continue
            val sets = o.getInt("sets"); val work = o.getInt("workSec"); val rest = o.getInt("restSec")
            store.put(
                Rule(
                    id = "train-${goal.id}", title = "تدريب: ${goal.name}",
                    minutes = Scheduler.sessionMinutes(sets, work, rest),
                    everyHours = o.getInt("recoveryHours"), kind = "training", goalId = goal.id,
                    sets = sets, workSec = work, restSec = rest, active = false, anchorDay = today,
                    source = sources.optString(o.getString("source")), priority = 1,
                )
            )
            val support = o.optJSONArray("support") ?: continue
            for (j in 0 until support.length()) {
                val s = support.getJSONObject(j)
                val ss = s.getInt("sets"); val sw = s.getInt("workSec"); val sr = s.getInt("restSec")
                store.put(
                    Rule(
                        id = "support-${goal.id}-$j", title = s.getString("title"),
                        minutes = Scheduler.sessionMinutes(ss, sw, sr),
                        everyHours = s.getInt("recoveryHours"), kind = "training", goalId = goal.id,
                        sets = ss, workSec = sw, restSec = sr, active = false, anchorDay = today,
                        source = sources.optString(s.getString("source")), role = "support",
                    )
                )
            }
        }
    }

    private fun asset(name: String) = context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    // ---------- recurrence ----------

    /** Creates today's instances of due rules; earlier unfinished instances of the same rule are rolled over. */
    private suspend fun ensureDayLocked(day: LocalDate) {
        val dayStr = day.toString()
        val existing = store.allTasks()
        for (r in store.allRules().filter { it.active }) {
            val last = r.lastDoneDay?.let(LocalDate::parse)
            if (!Scheduler.isDue(r.everyHours, r.weekdays, LocalDate.parse(r.anchorDay), last, day)) continue
            if (existing.any { it.ruleId == r.id && it.day == dayStr }) continue
            var instance = instanceOf(r, day)
            if (r.id.startsWith("plan-")) {
                val plans = plansLocked()
                val plan = plans.firstOrNull { "plan-${it.id}" == r.id } ?: continue
                val range = PlanMath.today(plan, day, memorizedLast(plans)) ?: continue
                instance = instance.copy(title = PlanMath.title(plan, range), minutes = PlanMath.minutes(plan, range),
                    interruptible = plan.kind == "review")
            }
            existing.filter { it.ruleId == r.id && it.open && it.day < dayStr && !it.running }
                .forEach { store.put(it.copy(state = "skipped")) }
            store.put(instance)
        }
    }

    private fun instanceOf(r: Rule, day: LocalDate) = Task(
        title = r.title, minutes = r.minutes, day = day.toString(), ruleId = r.id, goalId = r.goalId,
        kind = r.kind, sets = r.sets, workSec = r.workSec, restSec = r.restSec, priority = r.priority,
        interruptible = r.interruptible,
        pinned = r.pinned && r.preferredMinute >= 0,
        planned = if (r.pinned && r.preferredMinute >= 0) Clock.epoch(day, r.preferredMinute) else 0,
    )

    // ---------- scheduling ----------

    suspend fun replan() = lock.withLock { replanLocked() }

    suspend fun refreshDay() = lock.withLock {
        ensureDayLocked(Clock.today())
        replanLocked()
    }

    private suspend fun replanLocked() {
        val today = Clock.today()
        val s = settings()
        val now = System.currentTimeMillis()
        val tasks = store.tasksOn(today.toString())
        val rules = store.allRules().associateBy { it.id }
        val nowMin = Clock.minuteOfDay(now)
        val items = tasks.filter { it.open }.map { t ->
            val inProgress = t.running || t.spent > 0
            Item(
                id = t.id, minutes = t.minutes,
                fixedStart = if (t.pinned && t.planned > 0 && !inProgress) Clock.minuteOfDay(t.planned) else null,
                preferred = t.ruleId?.let { rules[it] }?.preferredMinute?.takeIf { it >= 0 && !t.pinned },
                priority = t.priority, interruptible = t.interruptible,
                sets = t.sets, workSec = t.workSec, restSec = t.restSec, order = t.created,
                inProgressStart = when {
                    t.running -> nowMin - (Maths.spent(t, now) / 60_000).toInt()
                    t.spent > 0 -> nowMin
                    else -> null
                },
                remaining = if (inProgress) ((Maths.remaining(t, now) + 59_999) / 60_000).toInt() else null,
            )
        }
        val plan = Scheduler.plan(items, s.dayStart, s.dayEnd, earliest = nowMin, gap = s.gap)
        val updated = tasks.map { t ->
            val p = plan.placed[t.id] ?: return@map t.copy(parallelTo = null, overflow = false)
            val start = if (t.pinned && !t.running && t.spent == 0L) t.planned else Clock.epoch(today, p.start)
            t.copy(
                planned = if (t.running || t.spent > 0) t.planned.takeIf { it > 0 } ?: start else start,
                original = if (t.original == 0L) start else t.original,
                parallelTo = p.parallelTo, overflow = p.overflow,
            )
        }
        store.putAll(updated)
        Alarms.reschedule(context, updated, s)
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
                        title = task.title.trim(), minutes = task.minutes, priority = task.priority,
                        interruptible = task.interruptible, pinned = task.pinned,
                        preferredMinute = if (task.pinned && task.planned > 0) Clock.minuteOfDay(task.planned) else -1,
                        sets = task.sets, workSec = task.workSec, restSec = task.restSec,
                    )
                )
            }
        }
        replanLocked()
    }

    suspend fun saveRule(rule: Rule) = lock.withLock {
        store.put(rule)
        val today = Clock.today()
        // Keep today's open instance in line with the rule.
        store.tasksOn(today.toString()).filter { it.ruleId == rule.id && it.open && !it.running && it.spent == 0L }
            .forEach { if (rule.active) store.put(instanceOf(rule, today).copy(id = it.id, created = it.created)) else store.delete(it.id) }
        ensureDayLocked(today)
        replanLocked()
    }

    suspend fun deleteRule(id: String) = lock.withLock {
        store.deleteRule(id)
        store.tasksOn(Clock.today().toString()).filter { it.ruleId == id && it.open && it.spent == 0L && !it.running }
            .forEach { store.delete(it.id) }
        replanLocked()
    }

    /** Start / pause toggle. Starting a task pauses any other running task. */
    suspend fun timer(id: String) = lock.withLock {
        val now = System.currentTimeMillis()
        val t = store.task(id) ?: return@withLock
        if (t.running) {
            store.put(t.copy(spent = Maths.spent(t, now), started = 0))
        } else {
            if (!t.open) return@withLock
            store.allTasks().filter { it.running && it.id != id }
                .forEach { store.put(it.copy(spent = Maths.spent(it, now), started = 0)) }
            if (Maths.remaining(t, now) <= 0) return@withLock
            store.put(t.copy(started = now, day = Clock.today().toString(), planned = if (t.spent == 0L) now else t.planned))
        }
        replanLocked()
    }

    suspend fun finish(id: String) = lock.withLock {
        val now = System.currentTimeMillis()
        val t = store.task(id) ?: return@withLock
        store.put(t.copy(state = "done", spent = Maths.spent(t, now), started = 0, finishedAt = now))
        t.ruleId?.let { rid -> store.rule(rid)?.let { store.put(it.copy(lastDoneDay = t.day.coerceAtLeast(it.lastDoneDay ?: ""))) } }
        if (t.ruleId?.startsWith("plan-") == true) {
            val plans = plansLocked()
            val plan = plans.firstOrNull { "plan-${it.id}" == t.ruleId }
            val last = memorizedLast(plans)
            val range = plan?.let { PlanMath.today(it, LocalDate.parse(t.day), last) }
            if (plan != null && range != null) savePlansLocked(plans.map { if (it.id == plan.id) PlanMath.advance(plan, range, last) else it })
        }
        replanLocked()
    }

    /** Pages of today's open review task (for the player). */
    suspend fun reviewPages(): List<Int> = lock.withLock {
        val plans = plansLocked()
        val p = plans.firstOrNull { it.kind == "review" } ?: return@withLock emptyList()
        val r = PlanMath.today(p, Clock.today(), memorizedLast(plans)) ?: return@withLock emptyList()
        (r.from..r.to).toList()
    }

    suspend fun reopen(id: String) = lock.withLock {
        val t = store.task(id) ?: return@withLock
        store.put(t.copy(state = "planned", finishedAt = 0))
        replanLocked()
    }

    suspend fun extend(id: String, minutes: Int) = lock.withLock {
        val t = store.task(id) ?: return@withLock
        store.put(t.copy(minutes = (t.minutes + minutes).coerceAtMost(10080)))
        replanLocked()
    }

    /** Moves the task to tomorrow; the scheduler places it there automatically. */
    suspend fun postpone(id: String) = lock.withLock {
        val now = System.currentTimeMillis()
        val t = store.task(id) ?: return@withLock
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

    /** Brings an overdue task into today (automatic placement). */
    suspend fun bringToToday(id: String) = lock.withLock {
        val t = store.task(id) ?: return@withLock
        store.put(t.copy(day = Clock.today().toString(), pinned = false, planned = 0))
        replanLocked()
    }

    suspend fun unpin(id: String) = lock.withLock {
        val t = store.task(id) ?: return@withLock
        store.put(t.copy(pinned = false))
        replanLocked()
    }

    suspend fun delete(id: String) = lock.withLock {
        store.delete(id)
        replanLocked()
    }

    /** Moves every unfinished task of today to tomorrow. */
    suspend fun closeDay(): Int = lock.withLock {
        val now = System.currentTimeMillis()
        val today = Clock.today()
        val open = store.tasksOn(today.toString()).filter { it.open }
        val tomorrow = today.plusDays(1)
        open.forEach { t ->
            if (t.ruleId != null && t.spent == 0L) store.put(t.copy(state = "skipped", started = 0))
            else store.put(t.copy(day = tomorrow.toString(), spent = Maths.spent(t, now), started = 0, planned = 0, pinned = false))
        }
        replanLocked()
        open.size
    }

    // ---------- goals ----------

    suspend fun goal(g: Goal, logValue: Boolean) = lock.withLock {
        val old = store.goal(g.id)
        store.put(g)
        if (logValue && (old == null || old.current != g.current)) store.put(GoalLog(goalId = g.id, day = Clock.today().toString(), value = g.current))
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
                    .put("finishedAt", t.finishedAt)
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
            )
        }
        val logs = JSONArray()
        store.allLogs().forEach { l -> logs.put(JSONObject().put("id", l.id).put("goalId", l.goalId).put("day", l.day).put("value", l.value)) }
        val meta = JSONObject()
        listOf("dayStart", "dayEnd", "gap", "extend", "insistent", "alarms", "plans", "seed-v3").forEach { k -> store.meta(k)?.let { meta.put(k, it) } }
        JSONObject().put("schema", 2).put("tasks", tasks).put("goals", goals).put("projects", projects)
            .put("rules", rules).put("logs", logs).put("settings", meta).toString(2)
    }

    /** Accepts schema 2 backups and schema 1 backups from the first version of the app. */
    suspend fun restore(text: String) = lock.withLock {
        val root = JSONObject(text)
        val schema = root.optInt("schema", 1)
        require(schema in 1..2) { "صيغة غير معروفة" }
        fun JSONObject.str(k: String): String? = if (isNull(k) || !has(k)) null else getString(k)
        db.withTransaction {
            store.clearTasks(); store.clearGoals(); store.clearProjects(); store.clearRules(); store.clearLogs()
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
                        // Version 1 tasks with a time were set by the user, so keep them pinned.
                        pinned = if (schema == 1) o.optLong("planned") > 0 else o.optBoolean("pinned"),
                        priority = o.optInt("priority", 1), interruptible = o.optBoolean("interruptible"),
                        kind = o.optString("kind", "task"), ruleId = o.str("ruleId"), goalId = o.str("goalId"),
                        projectId = o.str("projectId"), sets = o.optInt("sets"), workSec = o.optInt("workSec"),
                        restSec = o.optInt("restSec"), created = o.optLong("created", System.currentTimeMillis()),
                        finishedAt = o.optLong("finishedAt"),
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
            val rules = root.optJSONArray("rules")
            if (rules == null) seedTraining() else for (i in 0 until rules.length()) {
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
                    )
                )
            }
            val logs = root.optJSONArray("logs") ?: JSONArray()
            for (i in 0 until logs.length()) {
                val o = logs.getJSONObject(i)
                store.put(GoalLog(o.getString("id"), o.getString("goalId"), o.getString("day"), o.getDouble("value")))
            }
            root.optJSONObject("settings")?.let { s -> s.keys().forEach { k -> store.put(Meta(k, s.getString(k))) } }
            store.put(Meta("seed-v2", "1"))
            if (root.optJSONObject("settings")?.has("seed-v3") != true) seedV3()
            store.put(Meta("seed-v3", "1"))
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
