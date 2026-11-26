package com.imad.planner

import org.json.JSONObject

/**
 * Every saved row as JSON, for the sync between devices: all fields of each table, so nothing is lost on the way.
 * Reading is forgiving (a field added in a later version gets its default), so devices on different versions
 * can still exchange rows.
 */
object Rows {
    const val TASKS = "tasks"
    const val RULES = "rules"
    const val GOALS = "goals"
    const val LOGS = "goal_logs"
    const val PROJECTS = "projects"
    const val META = "meta"
    const val EVENTS = "events"
    val tables = listOf(TASKS, RULES, GOALS, LOGS, PROJECTS, META, EVENTS)

    /** Settings that belong to one device only, or are too big and useless elsewhere: never synced. */
    fun localMeta(key: String) = key in setOf("undo", "undoLabel", "undoAt", "igFeed", "igFetchedAt") || key.startsWith("sync:")

    private fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
    private fun Any?.orNull(): Any = this ?: JSONObject.NULL

    fun task(t: Task): JSONObject = JSONObject()
        .put("id", t.id).put("title", t.title).put("minutes", t.minutes).put("planned", t.planned).put("day", t.day)
        .put("state", t.state).put("spent", t.spent).put("started", t.started).put("original", t.original).put("pinned", t.pinned)
        .put("priority", t.priority).put("interruptible", t.interruptible).put("kind", t.kind).put("ruleId", t.ruleId.orNull())
        .put("goalId", t.goalId.orNull()).put("projectId", t.projectId.orNull()).put("sets", t.sets).put("workSec", t.workSec)
        .put("restSec", t.restSec).put("created", t.created).put("parallelTo", t.parallelTo.orNull()).put("overflow", t.overflow)
        .put("finishedAt", t.finishedAt).put("category", t.category).put("sport", t.sport).put("phase", t.phase)
        .put("phaseCount", t.phaseCount).put("pct", t.pct).put("units", t.units).put("doneUnits", t.doneUnits).put("note", t.note)
        .put("atWork", t.atWork).put("preferMin", t.preferMin).put("period", t.period).put("details", t.details).put("notBefore", t.notBefore)

    fun task(o: JSONObject) = Task(
        id = o.getString("id"), title = o.getString("title"), minutes = o.getInt("minutes"), planned = o.optLong("planned"),
        day = o.getString("day"), state = o.optString("state", "planned"), spent = o.optLong("spent"), started = o.optLong("started"),
        original = o.optLong("original"), pinned = o.optBoolean("pinned"), priority = o.optInt("priority", 1),
        interruptible = o.optBoolean("interruptible"), kind = o.optString("kind", "task"), ruleId = o.str("ruleId"),
        goalId = o.str("goalId"), projectId = o.str("projectId"), sets = o.optInt("sets"), workSec = o.optInt("workSec"),
        restSec = o.optInt("restSec"), created = o.optLong("created"), parallelTo = o.str("parallelTo"), overflow = o.optBoolean("overflow"),
        finishedAt = o.optLong("finishedAt"), category = o.optString("category", "other"), sport = o.optInt("sport"),
        phase = o.optInt("phase"), phaseCount = o.optInt("phaseCount", 1), pct = o.optInt("pct", 100), units = o.optInt("units"),
        doneUnits = o.optInt("doneUnits"), note = o.optString("note"), atWork = o.optBoolean("atWork"),
        preferMin = o.optInt("preferMin", -1), period = o.optString("period"), details = o.optString("details"), notBefore = o.optLong("notBefore"),
    )

    fun rule(r: Rule): JSONObject = JSONObject()
        .put("id", r.id).put("title", r.title).put("minutes", r.minutes).put("everyHours", r.everyHours).put("weekdays", r.weekdays)
        .put("preferredMinute", r.preferredMinute).put("pinned", r.pinned).put("priority", r.priority).put("interruptible", r.interruptible)
        .put("kind", r.kind).put("goalId", r.goalId.orNull()).put("sets", r.sets).put("workSec", r.workSec).put("restSec", r.restSec)
        .put("active", r.active).put("anchorDay", r.anchorDay).put("lastDoneDay", r.lastDoneDay.orNull()).put("source", r.source)
        .put("role", r.role).put("category", r.category).put("sport", r.sport).put("phases", r.phases).put("members", r.members)
        .put("atWork", r.atWork).put("realHours", r.realHours)

    fun rule(o: JSONObject) = Rule(
        id = o.getString("id"), title = o.getString("title"), minutes = o.getInt("minutes"), everyHours = o.optInt("everyHours", 24),
        weekdays = o.optInt("weekdays"), preferredMinute = o.optInt("preferredMinute", -1), pinned = o.optBoolean("pinned"),
        priority = o.optInt("priority", 1), interruptible = o.optBoolean("interruptible"), kind = o.optString("kind", "task"),
        goalId = o.str("goalId"), sets = o.optInt("sets"), workSec = o.optInt("workSec"), restSec = o.optInt("restSec"),
        active = o.optBoolean("active", true), anchorDay = o.getString("anchorDay"), lastDoneDay = o.str("lastDoneDay"),
        source = o.optString("source"), role = o.optString("role", "main"), category = o.optString("category", "other"),
        sport = o.optInt("sport"), phases = o.optString("phases"), members = o.optString("members"), atWork = o.optBoolean("atWork"),
        realHours = o.optBoolean("realHours"),
    )

    fun goal(g: Goal): JSONObject = JSONObject()
        .put("id", g.id).put("name", g.name).put("category", g.category).put("current", g.current).put("reference", g.reference)
        .put("unit", g.unit).put("lower", g.lower).put("baseline", g.baseline).put("startDay", g.startDay).put("targetDay", g.targetDay)

    fun goal(o: JSONObject) = Goal(
        id = o.getString("id"), name = o.getString("name"), category = o.optString("category"), current = o.getDouble("current"),
        reference = o.getDouble("reference"), unit = o.optString("unit"), lower = o.optBoolean("lower"),
        baseline = o.optDouble("baseline", o.getDouble("current")), startDay = o.getString("startDay"), targetDay = o.getString("targetDay"),
    )

    fun log(l: GoalLog): JSONObject = JSONObject().put("id", l.id).put("goalId", l.goalId).put("day", l.day).put("value", l.value)
    fun log(o: JSONObject) = GoalLog(o.getString("id"), o.getString("goalId"), o.getString("day"), o.getDouble("value"))

    fun project(p: Project): JSONObject = JSONObject().put("id", p.id).put("name", p.name).put("stages", p.stages)
    fun project(o: JSONObject) = Project(o.getString("id"), o.getString("name"), o.optString("stages"))

    fun meta(m: Meta): JSONObject = JSONObject().put("key", m.key).put("value", m.value)
    fun meta(o: JSONObject) = Meta(o.getString("key"), o.getString("value"))

    fun event(e: Event): JSONObject = JSONObject()
        .put("id", e.id).put("taskId", e.taskId.orNull()).put("series", e.series).put("day", e.day).put("at", e.at)
        .put("type", e.type).put("text", e.text).put("delayMin", e.delayMin)

    fun event(o: JSONObject) = Event(
        id = o.getString("id"), taskId = o.str("taskId"), series = o.getString("series"), day = o.getString("day"),
        at = o.optLong("at"), type = o.getString("type"), text = o.optString("text"), delayMin = o.optInt("delayMin"),
    )

    /** The current JSON text of one row, or null when it does not exist (deleted). */
    suspend fun read(store: Store, table: String, id: String): String? = when (table) {
        TASKS -> store.task(id)?.let { task(it).toString() }
        RULES -> store.rule(id)?.let { rule(it).toString() }
        GOALS -> store.goal(id)?.let { goal(it).toString() }
        LOGS -> store.allLogs().firstOrNull { it.id == id }?.let { log(it).toString() }
        PROJECTS -> store.allProjects().firstOrNull { it.id == id }?.let { project(it).toString() }
        META -> store.meta(id)?.let { meta(Meta(id, it)).toString() }
        EVENTS -> store.allEvents().firstOrNull { it.id == id }?.let { event(it).toString() }
        else -> null
    }

    /** Writes one row that came from another device (data null = it was deleted there). */
    suspend fun write(store: Store, table: String, id: String, data: String?) {
        val o = data?.let { JSONObject(it) }
        when (table) {
            TASKS -> if (o == null) store.delete(id) else store.put(task(o))
            RULES -> if (o == null) store.deleteRule(id) else store.put(rule(o))
            GOALS -> if (o == null) store.deleteGoal(id) else store.put(goal(o))
            META -> if (o == null) store.deleteMeta(id) else store.put(meta(o))
            // These tables have no single-row delete; a deletion there only ever comes with clearing (a restore).
            LOGS -> if (o != null) store.put(log(o))
            PROJECTS -> if (o != null) store.put(project(o))
            EVENTS -> if (o != null) store.put(event(o))
        }
    }
}
