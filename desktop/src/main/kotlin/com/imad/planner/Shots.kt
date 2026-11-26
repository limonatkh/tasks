package com.imad.planner

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files

/**
 * Draws the computer's screens into PNG files without opening a window, so CI can show what the app looks like.
 * Runs on a throwaway store in a temporary folder with a few sample tasks: it never touches anyone's data file.
 * Usage: gradle :desktop:shots (writes PNG files to desktop/build/shots).
 */
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "build/shots" }).apply { mkdirs() }
    val tmp = Files.createTempDirectory("planner-shots").toFile()
    val store = DesktopStore()
    val base = DesktopPlatform()
    val platform = object : Platform by base { override val filesDir: File = tmp }
    val repo = Repository(store, platform).also { Repository.instance = it }
    val today = Clock.today()
    runBlocking {
        repo.initialize()
        repo.refreshDay()
        val samples = listOf(
            Triple("قراءة كتاب", 30, "reading"), Triple("ورد القرآن", 20, "quran"), Triple("تمرين", 25, "sport"),
            Triple("شغل: تقرير", 90, "work"), Triple("بودكاست: البحث", 60, "other"),
        )
        val keys = Prayer.keys
        samples.forEachIndexed { i, (title, min, cat) ->
            for (dayOffset in listOf(0L, 1L, 2L)) {
                val day = today.plusDays(dayOffset)
                store.put(Task(title = title, minutes = min, day = day.toString(), category = cat, period = keys[(i + dayOffset.toInt()) % keys.size],
                    state = if (dayOffset == 0L && i == 0) "done" else "planned"))
            }
        }
        repo.replan()
    }
    val desk = runBlocking {
        val metas = store.allMeta().associate { it.key to it.value }
        Desk(store.allTasks(), store.allGoals(), store.allRules(), store.allLogs(), metas, Settings.from(metas), today, System.currentTimeMillis())
    }
    val firstOpen = desk.tasks.firstOrNull { it.day == today.toString() && it.open }
    fun shot(name: String, tab: Int, panel: Task? = null) {
        val scene = ImageComposeScene(1440, 900, Density(1f)) {
            Shell(desk, repo, act = {}, ready = true, tab = tab, setTab = {}, panelFor = panel, setPanel = {}, openBackup = {})
        }
        try {
            scene.render(0)
            scene.render(500_000_000)
            val img = scene.render(1_000_000_000)
            File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            println("shot $name")
        } finally { scene.close() }
    }
    shot("1-today", 0)
    shot("2-week", 1)
    shot("3-goals", 2)
    shot("4-task-panel", 0, firstOpen)
    shot("5-review", 3)
    kotlin.system.exitProcess(0)
}
