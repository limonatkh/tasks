package com.imad.planner

import org.json.JSONObject

/**
 * Sync between the phone and the computer, through one cloud folder per person (Firebase Firestore:
 * users/<owner>/rows). Each saved row is one cloud document with the time it last changed. Rules:
 * - Every change on a device is noted at once (see [TrackingStore]) and sent on the next sync.
 * - Same row changed on both devices: the later change wins (by its time).
 * - A deletion is sent as an empty row, so the other device deletes it too.
 * - Settings that belong to one device ([Rows.localMeta]) are never sent.
 */

/** One row as it travels: [data] null means deleted. [at]: when it changed (epoch ms). */
data class CloudRow(val table: String, val id: String, val data: String?, val at: Long, val device: String)

/** The cloud side: phone = Firestore SDK, computer = Firestore REST. */
interface Cloud {
    suspend fun push(rows: List<CloudRow>)
    /** Rows that changed at or after [since] (epoch ms). */
    suspend fun pull(since: Long): List<CloudRow>
}

/** Small device-only memory for the sync (phone: SharedPreferences, computer: a file). */
interface SyncPrefs {
    fun get(key: String): String?
    fun set(key: String, value: String?)
}

/** Changes waiting to be sent: "table|id" → when it changed. Kept in [SyncPrefs] so an app restart loses none. */
class Pending(private val prefs: SyncPrefs) {
    private val map = LinkedHashMap<String, Long>()
    private val lock = Any()

    init {
        runCatching { JSONObject(prefs.get("pending") ?: "{}").let { o -> o.keys().forEach { k -> map[k] = o.getLong(k) } } }
    }

    private fun save() = prefs.set("pending", JSONObject(map as Map<*, *>).toString())

    fun mark(table: String, id: String, at: Long = System.currentTimeMillis()) {
        if (table == Rows.META && Rows.localMeta(id)) return
        synchronized(lock) { map["$table|$id"] = at; save() }
    }

    fun snapshot(): Map<String, Long> = synchronized(lock) { LinkedHashMap(map) }

    /** Forget what was sent, unless it changed again meanwhile. */
    fun sent(done: Map<String, Long>) = synchronized(lock) {
        done.forEach { (k, at) -> if (map[k] == at) map.remove(k) }
        save()
    }

    fun clear() = synchronized(lock) { map.clear(); save() }
    val size get() = synchronized(lock) { map.size }
}

/**
 * The [Store] the app uses: every write goes to [inner] and is noted in [pending]. Rows that arrive from the other
 * device are written to [inner] directly (by [SyncEngine]), so they are not sent back.
 */
class TrackingStore(val inner: Store, private val pending: Pending) : Store by inner {
    private fun mark(table: String, id: String) = pending.mark(table, id)

    override suspend fun put(e: Event) { inner.put(e); mark(Rows.EVENTS, e.id) }
    override suspend fun put(t: Task) { inner.put(t); mark(Rows.TASKS, t.id) }
    override suspend fun putAll(t: List<Task>) { inner.putAll(t); t.forEach { mark(Rows.TASKS, it.id) } }
    override suspend fun put(g: Goal) { inner.put(g); mark(Rows.GOALS, g.id) }
    override suspend fun put(p: Project) { inner.put(p); mark(Rows.PROJECTS, p.id) }
    override suspend fun put(r: Rule) { inner.put(r); mark(Rows.RULES, r.id) }
    override suspend fun put(l: GoalLog) { inner.put(l); mark(Rows.LOGS, l.id) }
    override suspend fun put(m: Meta) { inner.put(m); mark(Rows.META, m.key) }

    override suspend fun delete(id: String) { inner.delete(id); mark(Rows.TASKS, id) }
    override suspend fun deleteRule(id: String) { inner.deleteRule(id); mark(Rows.RULES, id) }
    override suspend fun deleteGoal(id: String) { inner.deleteGoal(id); mark(Rows.GOALS, id) }
    override suspend fun deleteMeta(key: String) { inner.deleteMeta(key); mark(Rows.META, key) }

    override suspend fun clearTasks() { val ids = inner.allTasks().map { it.id }; inner.clearTasks(); ids.forEach { mark(Rows.TASKS, it) } }
    override suspend fun clearGoals() { val ids = inner.allGoals().map { it.id }; inner.clearGoals(); ids.forEach { mark(Rows.GOALS, it) } }
    override suspend fun clearRules() { val ids = inner.allRules().map { it.id }; inner.clearRules(); ids.forEach { mark(Rows.RULES, it) } }
    override suspend fun clearProjects() { val ids = inner.allProjects().map { it.id }; inner.clearProjects(); ids.forEach { mark(Rows.PROJECTS, it) } }
    override suspend fun clearLogs() { val ids = inner.allLogs().map { it.id }; inner.clearLogs(); ids.forEach { mark(Rows.LOGS, it) } }
    override suspend fun clearEvents() { val ids = inner.allEvents().map { it.id }; inner.clearEvents(); ids.forEach { mark(Rows.EVENTS, it) } }

}

/** Marks every row that exists now as changed, so the first sync sends everything (first sign-in on the phone). */
suspend fun Pending.markAll(store: Store) {
    val now = System.currentTimeMillis()
    store.allTasks().forEach { mark(Rows.TASKS, it.id, now) }
    store.allRules().forEach { mark(Rows.RULES, it.id, now) }
    store.allGoals().forEach { mark(Rows.GOALS, it.id, now) }
    store.allLogs().forEach { mark(Rows.LOGS, it.id, now) }
    store.allProjects().forEach { mark(Rows.PROJECTS, it.id, now) }
    store.allMeta().forEach { mark(Rows.META, it.key, now) }
    store.allEvents().forEach { mark(Rows.EVENTS, it.id, now) }
}

/** What one sync did, said in numbers. */
data class SyncResult(val sent: Int, val received: Int)

/**
 * Sends the pending changes, then takes the other device's changes. Runs inside the Repository's lock ([exclusive])
 * so it never interleaves with a change being made on screen.
 */
class SyncEngine(
    private val inner: Store,
    private val pending: Pending,
    private val prefs: SyncPrefs,
    private val cloud: Cloud,
    val device: String,
    private val exclusive: suspend (suspend () -> Unit) -> Unit = { it() },
) {
    /** Rows are read again a little before the last pull, in case the two devices' clocks differ a bit. */
    private val overlapMs = 10 * 60_000L

    suspend fun syncOnce(): SyncResult {
        var sent = 0
        var received = 0
        exclusive {
            val todo = pending.snapshot()
            if (todo.isNotEmpty()) {
                val rows = todo.map { (key, at) ->
                    val table = key.substringBefore('|'); val id = key.substringAfter('|')
                    CloudRow(table, id, Rows.read(inner, table, id), at, device)
                }
                rows.chunked(400).forEach { cloud.push(it) }
                pending.sent(todo)
                sent = rows.size
            }
            val since = prefs.get("since")?.toLongOrNull() ?: 0L
            val rows = cloud.pull((since - overlapMs).coerceAtLeast(0))
            val waiting = pending.snapshot()
            var newest = since
            for (r in rows.sortedBy { it.at }) {
                newest = maxOf(newest, r.at)
                if (r.device == device) continue
                if (r.table == Rows.META && Rows.localMeta(r.id)) continue
                // Changed here too, and later than there: ours wins and will be sent.
                val mine = waiting["${r.table}|${r.id}"]
                if (mine != null && mine >= r.at) continue
                val current = Rows.read(inner, r.table, r.id)
                if (current == r.data) continue
                Rows.write(inner, r.table, r.id, r.data)
                received++
            }
            prefs.set("since", newest.toString())
        }
        return SyncResult(sent, received)
    }
}
