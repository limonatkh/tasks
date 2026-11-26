package com.imad.planner

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * A [Store] kept in memory, with the same tables and sort orders as the phone's Room database (the computer uses it,
 * and the tests). Every change bumps
 * [version]; the computer then writes its whole data file (desktop Main.kt), so nothing is lost on exit.
 */
class MemoryStore : Store {
    private val lock = Any()
    private val tasks = LinkedHashMap<String, Task>()
    private val goals = LinkedHashMap<String, Goal>()
    private val projects = LinkedHashMap<String, Project>()
    private val rules = LinkedHashMap<String, Rule>()
    private val logs = LinkedHashMap<String, GoalLog>()
    private val metas = LinkedHashMap<String, Meta>()
    private val events = LinkedHashMap<String, Event>()

    private val _version = MutableStateFlow(0L)
    /** Goes up by one on every change. */
    val version = _version.asStateFlow()

    private inline fun <T> read(block: () -> T): T = synchronized(lock) { block() }
    private inline fun write(block: () -> Unit) { synchronized(lock) { block() }; _version.value = _version.value + 1 }

    private fun sortedTasks() = read { tasks.values.sortedWith(compareBy({ it.day }, { it.planned }, { it.created })) }

    override fun tasks(): Flow<List<Task>> = version.map { sortedTasks() }
    override fun goals(): Flow<List<Goal>> = version.map { read { goals.values.sortedBy { it.id } } }
    override fun projects(): Flow<List<Project>> = version.map { read { projects.values.sortedBy { it.name } } }
    override fun rules(): Flow<List<Rule>> = version.map { read { rules.values.sortedBy { it.title } } }
    override fun logs(): Flow<List<GoalLog>> = version.map { read { logs.values.sortedBy { it.day } } }
    override fun metas(): Flow<List<Meta>> = version.map { read { metas.values.toList() } }
    override fun events(): Flow<List<Event>> = version.map { read { events.values.sortedBy { it.at } } }

    override suspend fun allEvents(): List<Event> = read { events.values.toList() }
    override suspend fun put(e: Event) = write { events[e.id] = e }
    override suspend fun clearEvents() = write { events.clear() }

    override suspend fun allTasks(): List<Task> = read { tasks.values.toList() }
    override suspend fun tasksOn(day: String): List<Task> = read { tasks.values.filter { it.day == day } }
    override suspend fun allGoals(): List<Goal> = read { goals.values.toList() }
    override suspend fun allProjects(): List<Project> = read { projects.values.toList() }
    override suspend fun allRules(): List<Rule> = read { rules.values.toList() }
    override suspend fun allLogs(): List<GoalLog> = read { logs.values.toList() }
    override suspend fun task(id: String): Task? = read { tasks[id] }
    override suspend fun rule(id: String): Rule? = read { rules[id] }
    override suspend fun goal(id: String): Goal? = read { goals[id] }
    override suspend fun meta(key: String): String? = read { metas[key]?.value }
    override suspend fun deleteMeta(key: String) = write { metas.remove(key) }
    override suspend fun allMeta(): List<Meta> = read { metas.values.toList() }

    override suspend fun put(t: Task) = write { tasks[t.id] = t }
    override suspend fun putAll(t: List<Task>) = write { t.forEach { tasks[it.id] = it } }
    override suspend fun put(g: Goal) = write { goals[g.id] = g }
    override suspend fun put(p: Project) = write { projects[p.id] = p }
    override suspend fun put(r: Rule) = write { rules[r.id] = r }
    override suspend fun put(l: GoalLog) = write { logs[l.id] = l }
    override suspend fun put(m: Meta) = write { metas[m.key] = m }

    override suspend fun delete(id: String) = write { tasks.remove(id) }
    override suspend fun deleteRule(id: String) = write { rules.remove(id) }
    override suspend fun deleteGoal(id: String) = write { goals.remove(id) }
    override suspend fun clearTasks() = write { tasks.clear() }
    override suspend fun clearGoals() = write { goals.clear() }
    override suspend fun clearProjects() = write { projects.clear() }
    override suspend fun clearRules() = write { rules.clear() }
    override suspend fun clearLogs() = write { logs.clear() }
}
