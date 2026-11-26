package com.imad.planner

/** What [Repository] needs from the device it runs on. The phone: AndroidPlatform (Data.kt). */
interface Platform {
    /** Where undo and safety copies are written. */
    val filesDir: java.io.File
    /** A bundled text file (seed data). */
    fun asset(name: String): String
    /** Runs [block] as one database transaction. */
    suspend fun <T> transaction(block: suspend () -> T): T
    fun pomoAlarm(end: Long)
    fun rescheduleAlarms(tasks: List<Task>, settings: Settings)
    fun updateWidget(todays: List<Task>, settings: Settings)
    /** The Quran app (phone only; elsewhere the answer is QuranState.NOT_INSTALLED). */
    fun quranReport(day: String): Pair<QuranState, QuranReport?>
    fun quranWards(): Pair<QuranState, List<Ward>>
    fun quranSessions(day: String): Pair<QuranState, List<QuranSession>>
    /** Instagram's answer per account, fetched with the signed-in page (phone only; elsewhere empty). */
    suspend fun igFetchAll(users: List<String>): Map<String, IgAnswer>
}
