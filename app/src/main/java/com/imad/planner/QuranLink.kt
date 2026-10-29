package com.imad.planner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

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

/** A task series linked to a ward. The name is for display only; the id is what keeps the link. */
data class QuranLink(val mode: String, val wardId: String, val wardName: String, val missing: Boolean = false)

data class Ward(val id: String, val name: String, val mode: String)

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

object QuranAdapter {
    fun installed(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(QuranContract.PACKAGE, 0); true }.getOrDefault(false)

    /** The permission exists only once the Quran app declares it. */
    private fun permissionDefined(context: Context): Boolean =
        runCatching { context.packageManager.getPermissionInfo(QuranContract.PERMISSION, 0); true }.getOrDefault(false)

    fun hasPermission(context: Context) = context.checkSelfPermission(QuranContract.PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun state(context: Context): QuranState = when {
        !installed(context) -> QuranState.NOT_INSTALLED
        !permissionDefined(context) -> QuranState.NOT_SUPPORTED
        !hasPermission(context) -> QuranState.NO_PERMISSION
        context.packageManager.resolveContentProvider(QuranContract.AUTHORITY, 0) == null -> QuranState.NOT_SUPPORTED
        else -> QuranState.OK
    }

    /** One JSON object from a provider path, or the reason it failed. */
    private fun query(context: Context, path: String, params: Map<String, String> = emptyMap()): Pair<QuranState, JSONObject?> {
        val st = state(context)
        if (st != QuranState.OK) return st to null
        val b = Uri.Builder().scheme("content").authority(QuranContract.AUTHORITY).appendPath(path)
            .appendQueryParameter(QuranContract.EXTRA_PROTOCOL, QuranContract.PROTOCOL_VERSION.toString())
        params.forEach { (k, v) -> b.appendQueryParameter(k, v) }
        return try {
            context.contentResolver.query(b.build(), arrayOf(QuranContract.COLUMN_JSON), null, null, null)?.use { c ->
                if (!c.moveToFirst()) return QuranState.NOT_SUPPORTED to null
                val o = JSONObject(c.getString(c.getColumnIndexOrThrow(QuranContract.COLUMN_JSON)))
                if (o.has("error")) (if (o.optString("error") == "unsupported_version") QuranState.NOT_SUPPORTED else QuranState.ERROR) to null
                else QuranState.OK to o
            } ?: (QuranState.NOT_SUPPORTED to null)
        } catch (_: SecurityException) {
            QuranState.NO_PERMISSION to null
        } catch (_: Exception) {
            QuranState.ERROR to null
        }
    }

    fun wards(context: Context): Pair<QuranState, List<Ward>> {
        val (st, o) = query(context, QuranContract.PATH_WARDS)
        val a = o?.optJSONArray("wards") ?: return st to emptyList()
        return st to (0 until a.length()).map { i -> a.getJSONObject(i).let { Ward(it.getString("ward_id"), it.optString("name"), it.optString("mode", "read")) } }
    }

    fun report(context: Context, day: String): Pair<QuranState, QuranReport?> {
        val (st, o) = query(context, QuranContract.PATH_REPORT, mapOf("day" to day))
        return st to o?.let { parseReport(it, System.currentTimeMillis()) }
    }

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

    /** The intent that opens one ward at its own last position. No page number is sent. */
    fun openIntent(link: QuranLink, taskId: String, occurrence: String): Intent =
        Intent(QuranContract.ACTION_OPEN_WARD).setPackage(QuranContract.PACKAGE)
            .putExtra(QuranContract.EXTRA_PROTOCOL, QuranContract.PROTOCOL_VERSION)
            .putExtra(QuranContract.EXTRA_MODE, link.mode)
            .putExtra(QuranContract.EXTRA_WARD, link.wardId)
            .putExtra(QuranContract.EXTRA_TASK, taskId)
            .putExtra(QuranContract.EXTRA_OCCURRENCE, occurrence)
            .putExtra(QuranContract.EXTRA_REQUEST, UUID.randomUUID().toString())
            .putExtra(QuranContract.EXTRA_CALLER, "com.imad.planner")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the ward; returns the reason when it cannot. */
    fun open(context: Context, link: QuranLink, t: Task): QuranState {
        if (!installed(context)) return QuranState.NOT_INSTALLED
        val i = openIntent(link, t.id, "${t.id}@${t.day}")
        if (context.packageManager.queryIntentActivities(i, 0).isEmpty()) return QuranState.NOT_SUPPORTED
        return try { context.startActivity(i); QuranState.OK } catch (_: Exception) { QuranState.ERROR }
    }

    /** Main number of a ward report for its activity, with its unit. */
    fun mainNumber(mode: String, t: QuranTotals): Pair<Int, String> = when (mode) {
        "memorize" -> t.memorizedAyahs to "آية حفظ"
        "review" -> t.reviewAyahs to "آية مراجعة"
        else -> if (t.readPages > 0) t.readPages to "صفحة" else t.readAyahs to "آية"
    }
}

/** The Quran app says progress changed: pull the report again (the pull is the only source of truth). */
class QuranProgressReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != QuranContract.ACTION_PROGRESS_CHANGED) return
        val pending = goAsync()
        Alarms.scope.launch {
            try { Repository.get(context).syncQuran() } finally { pending.finish() }
        }
    }
}
