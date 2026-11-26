package com.imad.planner

import org.json.JSONObject

/**
 * Link to the separate Quran app, version 1 of the contract (see QURAN_INTEGRATION_V1.md).
 * Every name both apps must agree on lives here, so a change of contract touches only this object.
 */
object QuranContract {
    const val PACKAGE = "ps.imad.quran"
    const val PROTOCOL_VERSION = 1

    // Opening a ward
    const val ACTION_OPEN_WARD = "ps.imad.quran.action.OPEN_WARD"
    const val EXTRA_PROTOCOL = "protocol_version"
    const val EXTRA_MODE = "mode"
    const val EXTRA_WARD = "ward_id"
    const val EXTRA_TASK = "task_id"
    const val EXTRA_OCCURRENCE = "occurrence_id"
    const val EXTRA_REQUEST = "request_id"
    const val EXTRA_CALLER = "caller_package"

    // Reading data
    const val AUTHORITY = "ps.imad.quran.integration"
    const val PERMISSION = "ps.imad.quran.permission.INTEGRATION"
    const val PATH_CAPABILITIES = "capabilities"
    const val PATH_WARDS = "wards"
    const val PATH_REPORT = "report"
    const val PATH_SESSIONS = "sessions"
    const val COLUMN_JSON = "json"

    // Progress changed (sent to this app's package; it only triggers a pull)
    const val ACTION_PROGRESS_CHANGED = "ps.imad.quran.action.PROGRESS_CHANGED"

    val modes = listOf("read", "memorize", "review")
    fun modeName(m: String) = when (m) { "memorize" -> "حفظ"; "review" -> "مراجعة"; else -> "قراءة" }
}


data class Ward(val id: String, val name: String, val mode: String)

/** One stay in the Quran app, from entering to leaving, with what was done in it. */
data class QuranSession(val id: String, val wardId: String, val mode: String, val start: Long, val end: Long, val totals: QuranTotals,
                        val taskId: String = "", val wardName: String = "")

/** Totals of one activity (whole day or one ward). Opening a page is never counted as reading. */
data class QuranTotals(
    val readAyahs: Int = 0, val readPages: Int = 0, val memorizedAyahs: Int = 0,
    val reviewAyahs: Int = 0, val reviewPlays: Int = 0, val listenSeconds: Int = 0,
)

data class WardReport(val wardId: String, val name: String, val mode: String, val totals: QuranTotals)

data class QuranReport(
    val day: String, val version: Long, val generatedAt: Long, val totals: QuranTotals, val wards: List<WardReport>, val syncedAt: Long,
) {
    fun ward(id: String) = wards.firstOrNull { it.wardId == id }
}

/** Why the link cannot work right now. */
enum class QuranState(val text: String) {
    OK(""),
    NOT_INSTALLED("تطبيق القرآن مش مثبّت على الجوال."),
    NOT_SUPPORTED("بانتظار تحديث تطبيق القرآن الداعم للربط."),
    NO_PERMISSION("لازم تسمح لتطبيق المهام يقرأ تقدّم القرآن."),
    ERROR("تعذّر الاتصال بتطبيق القرآن."),
}

/** The Quran app's answers, read the same way on every device. The phone adds the calls that reach the app (QuranLink.kt). */
object QuranAdapter {
    fun totals(o: JSONObject?) = QuranTotals(
        o?.optInt("read_ayahs") ?: 0, o?.optInt("read_pages") ?: 0, o?.optInt("memorized_ayahs") ?: 0,
        o?.optInt("review_ayahs") ?: 0, o?.optInt("review_plays") ?: 0, o?.optInt("listen_seconds") ?: 0,
    )


    fun parseReport(o: JSONObject, syncedAt: Long): QuranReport {
        val a = o.optJSONArray("wards")
        val wards = (0 until (a?.length() ?: 0)).map { i ->
            val w = a!!.getJSONObject(i)
            WardReport(w.getString("ward_id"), w.optString("name"), w.optString("mode", "read"), totals(w.optJSONObject("totals")))
        }
        return QuranReport(o.optString("day"), o.optLong("report_version"), o.optLong("generated_at"), totals(o.optJSONObject("totals")), wards,
            o.optLong("synced_at", syncedAt))
    }


    /** Opens the ward; returns the reason when it cannot. */
    /**
     * The occurrence: task, day and the time it was set for, so two reminders of one task on the same day differ.
     * The Quran app treats it as opaque text.
     */
    fun occurrenceOf(t: Task): String = "${t.id}@${t.day}" + if (t.planned > 0) "@" + "%04d".format(Clock.minuteOfDay(t.planned)) else ""


    /** Main number of a ward report for its activity, with its unit. */
    fun mainNumber(mode: String, t: QuranTotals): Pair<Int, String> = when (mode) {
        "memorize" -> t.memorizedAyahs to "آية حفظ"
        "review" -> t.reviewAyahs to "آية مراجعة"
        else -> if (t.readPages > 0) t.readPages to "صفحة" else t.readAyahs to "آية"
    }
}
