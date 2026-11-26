package com.imad.planner

import java.io.File

/**
 * The computer's side of [Platform].
 * - Files live in %APPDATA%\SmartLifePlanner (data.json, undo and safety copies).
 * - Alarms and the widget are not on the computer yet (Windows notifications come in a later step).
 * - The Quran app and Instagram's signed-in page exist only on the phone.
 */
class DesktopPlatform : Platform {
    override val filesDir: File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "SmartLifePlanner").apply { mkdirs() }

    /** The phone's seed files are packed into the app as resources (desktop/build.gradle.kts). */
    override fun asset(name: String): String =
        (javaClass.classLoader.getResourceAsStream(name) ?: error("missing resource $name")).bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** Repository already runs one change at a time (its own lock), and the store is in memory: nothing more to do. */
    override suspend fun <T> transaction(block: suspend () -> T): T = block()

    override fun pomoAlarm(end: Long) {}
    override fun rescheduleAlarms(tasks: List<Task>, settings: Settings) {}
    override fun updateWidget(todays: List<Task>, settings: Settings) {}

    override fun quranReport(day: String): Pair<QuranState, QuranReport?> = QuranState.NOT_INSTALLED to null
    override fun quranWards(): Pair<QuranState, List<Ward>> = QuranState.NOT_INSTALLED to emptyList()
    override fun quranSessions(day: String): Pair<QuranState, List<QuranSession>> = QuranState.NOT_INSTALLED to emptyList()
    override suspend fun igFetchAll(users: List<String>): Map<String, IgAnswer> = emptyMap()
}
