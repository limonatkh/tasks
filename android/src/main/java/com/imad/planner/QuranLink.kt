package com.imad.planner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

// QuranContract, the data classes, QuranState and the pure QuranAdapter functions are in shared/ (Quran.kt).

fun QuranAdapter.installed(context: Context): Boolean =
    runCatching { context.packageManager.getPackageInfo(QuranContract.PACKAGE, 0); true }.getOrDefault(false)


/** The permission exists only once the Quran app declares it. */
private fun QuranAdapter.permissionDefined(context: Context): Boolean =
    runCatching { context.packageManager.getPermissionInfo(QuranContract.PERMISSION, 0); true }.getOrDefault(false)


fun QuranAdapter.hasPermission(context: Context) = context.checkSelfPermission(QuranContract.PERMISSION) == PackageManager.PERMISSION_GRANTED


fun QuranAdapter.state(context: Context): QuranState = when {
    !installed(context) -> QuranState.NOT_INSTALLED
    !permissionDefined(context) -> QuranState.NOT_SUPPORTED
    !hasPermission(context) -> QuranState.NO_PERMISSION
    context.packageManager.resolveContentProvider(QuranContract.AUTHORITY, 0) == null -> QuranState.NOT_SUPPORTED
    else -> QuranState.OK
}


/** One JSON object from a provider path, or the reason it failed. */
private fun QuranAdapter.query(context: Context, path: String, params: Map<String, String> = emptyMap()): Pair<QuranState, JSONObject?> {
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


fun QuranAdapter.wards(context: Context): Pair<QuranState, List<Ward>> {
    val (st, o) = query(context, QuranContract.PATH_WARDS)
    val a = o?.optJSONArray("wards") ?: return st to emptyList()
    return st to (0 until a.length()).map { i -> a.getJSONObject(i).let { Ward(it.getString("ward_id"), it.optString("name"), it.optString("mode", "read")) } }
}


fun QuranAdapter.report(context: Context, day: String): Pair<QuranState, QuranReport?> {
    val (st, o) = query(context, QuranContract.PATH_REPORT, mapOf("day" to day))
    return st to o?.let { parseReport(it, System.currentTimeMillis()) }
}


/** The day's closed sessions (each sent when the user leaves the Quran app). */
fun QuranAdapter.sessions(context: Context, day: String): Pair<QuranState, List<QuranSession>> {
    val (st, o) = query(context, QuranContract.PATH_SESSIONS, mapOf("day" to day))
    val a = o?.optJSONArray("sessions") ?: return st to emptyList()
    return st to (0 until a.length()).mapNotNull { i ->
        val s = a.getJSONObject(i)
        val id = s.optString("session_id").ifBlank { return@mapNotNull null }
        QuranSession(id, s.optString("ward_id"), s.optString("mode", "read"), s.optLong("started_at"), s.optLong("ended_at"),
            totals(s), s.optString("task_id"), s.optString("name"))
    }
}


/** The intent that opens one ward at its own last position. No page number is sent. */
fun QuranAdapter.openIntent(link: QuranLink, taskId: String, occurrence: String): Intent =
    Intent(QuranContract.ACTION_OPEN_WARD).setPackage(QuranContract.PACKAGE)
        .putExtra(QuranContract.EXTRA_PROTOCOL, QuranContract.PROTOCOL_VERSION)
        .putExtra(QuranContract.EXTRA_MODE, link.mode)
        .putExtra(QuranContract.EXTRA_WARD, link.wardId)
        .putExtra(QuranContract.EXTRA_TASK, taskId)
        .putExtra(QuranContract.EXTRA_OCCURRENCE, occurrence)
        .putExtra(QuranContract.EXTRA_REQUEST, UUID.randomUUID().toString())
        .putExtra(QuranContract.EXTRA_CALLER, "com.imad.planner")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)


fun QuranAdapter.open(context: Context, link: QuranLink, t: Task): QuranState {
    if (!installed(context)) return QuranState.NOT_INSTALLED
    // The Quran app protects the open screen with the same permission as its data.
    if (permissionDefined(context) && !hasPermission(context)) return QuranState.NO_PERMISSION
    val i = openIntent(link, t.id, occurrenceOf(t))
    if (context.packageManager.queryIntentActivities(i, 0).isEmpty()) return QuranState.NOT_SUPPORTED
    return try { context.startActivity(i); QuranState.OK } catch (_: SecurityException) { QuranState.NO_PERMISSION } catch (_: Exception) { QuranState.ERROR }
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
