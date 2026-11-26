package com.imad.planner

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
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

// The phone's Room database implements this (KSP generates it); the computer has its own implementation.

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
    @Query("DELETE FROM meta WHERE `key` = :key") suspend fun deleteMeta(key: String)
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
