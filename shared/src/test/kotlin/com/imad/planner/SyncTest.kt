package com.imad.planner

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Two devices (phone and computer) syncing through a fake cloud. */
class SyncTest {
    private class FakeCloud : Cloud {
        val rows = LinkedHashMap<String, CloudRow>()
        override suspend fun push(rows: List<CloudRow>) { rows.forEach { this.rows["${it.table}|${it.id}"] = it } }
        override suspend fun pull(since: Long) = rows.values.filter { it.at >= since }
    }

    private class Prefs : SyncPrefs {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun set(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
    }

    private class Device(cloud: Cloud, name: String) {
        val inner = MemoryStore()
        val prefs = Prefs()
        val pending = Pending(prefs)
        val store = TrackingStore(inner, pending)
        val sync = SyncEngine(inner, pending, prefs, cloud, name)
    }

    private val cloud = FakeCloud()
    private val phone = Device(cloud, "phone")
    private val pc = Device(cloud, "pc")
    private fun task(id: String, title: String) = Task(id = id, title = title, minutes = 30, day = "2026-11-26")

    @Test fun newTaskReachesTheOtherDevice() = runBlocking {
        phone.store.put(task("a", "قراءة"))
        assertEquals(SyncResult(1, 0), phone.sync.syncOnce())
        assertEquals(SyncResult(0, 1), pc.sync.syncOnce())
        assertEquals("قراءة", pc.inner.task("a")?.title)
        // Nothing is sent back: it came from the cloud.
        assertEquals(0, pc.pending.size)
    }

    @Test fun everyFieldSurvives() = runBlocking {
        val t = task("b", "تمرين").copy(planned = 123, started = 456, ruleId = "r1", period = "asr", details = "سطر", notBefore = 9, parallelTo = "x", overflow = true)
        phone.store.put(t)
        phone.sync.syncOnce(); pc.sync.syncOnce()
        assertEquals(t, pc.inner.task("b"))
    }

    @Test fun laterChangeWins() = runBlocking {
        phone.store.put(task("c", "أول")); phone.sync.syncOnce(); pc.sync.syncOnce()
        val base = System.currentTimeMillis() + 60_000
        phone.pending.mark(Rows.TASKS, "c", base + 1_000); phone.inner.put(task("c", "من الجوال"))
        pc.pending.mark(Rows.TASKS, "c", base + 2_000); pc.inner.put(task("c", "من الكمبيوتر"))
        phone.sync.syncOnce(); pc.sync.syncOnce(); phone.sync.syncOnce()
        assertEquals("من الكمبيوتر", phone.inner.task("c")?.title)
        assertEquals("من الكمبيوتر", pc.inner.task("c")?.title)
    }

    @Test fun deletionReachesTheOtherDevice() = runBlocking {
        phone.store.put(task("d", "مؤقتة")); phone.sync.syncOnce(); pc.sync.syncOnce()
        pc.store.delete("d"); pc.sync.syncOnce(); phone.sync.syncOnce()
        assertNull(phone.inner.task("d"))
    }

    @Test fun deviceOnlySettingsStayHome() = runBlocking {
        phone.store.put(Meta("undo", "{}")); phone.store.put(Meta("xp", "0"))
        phone.sync.syncOnce(); pc.sync.syncOnce()
        assertNull(pc.inner.meta("undo"))
        assertEquals("0", pc.inner.meta("xp"))
    }

    @Test fun firstSignInSendsEverything() = runBlocking {
        phone.inner.put(task("e", "قديمة")); phone.inner.put(Goal("g", "هدف", "other", 1.0, 10.0, "صفحة", false))
        phone.pending.markAll(phone.inner)
        phone.sync.syncOnce(); pc.sync.syncOnce()
        assertEquals("قديمة", pc.inner.task("e")?.title)
        assertEquals("هدف", pc.inner.goal("g")?.name)
    }
}
