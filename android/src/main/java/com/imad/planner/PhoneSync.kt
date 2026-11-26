package com.imad.planner

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.net.URLEncoder
import java.util.UUID

/** The phone's [SyncPrefs]: its own SharedPreferences file, never in the backup or the sync. */
class PhonePrefs(context: Context) : SyncPrefs {
    private val p = context.applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE)
    override fun get(key: String): String? = p.getString(key, null)
    override fun set(key: String, value: String?) { p.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply() }
    override fun all(prefix: String): Map<String, String> = p.all.filterKeys { it.startsWith(prefix) }.mapValues { it.value.toString() }
}

/** Firestore: users/<owner>/rows/<table|id>, one document per row. */
class FirestoreCloud(private val owner: String) : Cloud {
    private val rows = Firebase.firestore.collection("users").document(owner).collection("rows")

    override suspend fun push(rows: List<CloudRow>) {
        val batch = Firebase.firestore.batch()
        rows.forEach { r ->
            batch.set(this.rows.document(docId(r.table, r.id)), mapOf(
                "table" to r.table, "id" to r.id, "data" to r.data, "at" to r.at, "device" to r.device,
            ))
        }
        batch.commit().await()
    }

    override suspend fun pull(since: Long): List<CloudRow> =
        rows.whereGreaterThanOrEqualTo("at", since).get().await().documents.mapNotNull { d ->
            CloudRow(d.getString("table") ?: return@mapNotNull null, d.getString("id") ?: return@mapNotNull null,
                d.getString("data"), d.getLong("at") ?: 0L, d.getString("device") ?: "")
        }

    companion object {
        fun docId(table: String, id: String): String = URLEncoder.encode("$table|$id", "UTF-8")
    }
}

/**
 * The phone's sync: Google sign-in (the person's own account, no password kept by the app), the sync loop while the
 * app is open, and pairing a computer with the code it shows.
 */
object PhoneSync {
    @Volatile private var prefsRef: PhonePrefs? = null
    @Volatile private var pendingRef: Pending? = null

    fun prefs(context: Context): PhonePrefs = prefsRef ?: synchronized(this) { prefsRef ?: PhonePrefs(context).also { prefsRef = it } }
    fun owner(context: Context): String? = prefs(context).get("owner")
    fun pending(context: Context): Pending = pendingRef ?: synchronized(this) {
        pendingRef ?: Pending(prefs(context)) { owner(context) != null }.also { pendingRef = it }
    }
    private fun device(context: Context): String = prefs(context).get("device") ?: ("phone-" + UUID.randomUUID()).also { prefs(context).set("device", it) }

    /** What the settings show: "" when all is fine. */
    val status = mutableStateOf("")

    suspend fun syncNow(context: Context, repo: Repository): SyncResult? {
        val owner = owner(context) ?: return null
        val inner = (repo.store as? TrackingStore)?.inner ?: return null
        return runCatching {
            SyncEngine(inner, pending(context), prefs(context), FirestoreCloud(owner), device(context), repo::exclusive).syncOnce()
        }.onSuccess { prefs(context).set("lastSync", System.currentTimeMillis().toString()); status.value = "" }
            .onFailure { status.value = "ما زبط التزامن: ${it.message ?: it.javaClass.simpleName}" }.getOrNull()
    }

    /** While the app is on screen: sync now, then every 30 seconds. */
    suspend fun loop(context: Context, repo: Repository) {
        while (true) {
            syncNow(context, repo)
            delay(30_000)
        }
    }

    suspend fun signIn(activity: Activity, repo: Repository) {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(activity.getString(R.string.default_web_client_id))
            .build()
        val result = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val google = GoogleIdTokenCredential.createFrom(result.credential.data)
        val user = Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await().user ?: error("ما رجع حساب")
        Firebase.firestore.collection("users").document(user.uid).set(mapOf("joinedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
        val p = prefs(activity)
        p.set("owner", user.uid); p.set("email", user.email ?: "")
        // First time: everything on the phone goes up.
        repo.exclusive { pending(activity).markAll((repo.store as TrackingStore).inner) }
        syncNow(activity, repo)
    }

    /** The computer shows a code; this links that computer to this account. */
    suspend fun pair(context: Context, code: String) {
        val owner = owner(context) ?: error("سجّل دخول أول")
        val db = Firebase.firestore
        val doc = db.collection("pair").document(code.trim().uppercase()).get().await()
        val device = doc.getString("device") ?: error("الرمز غلط أو انتهى، اطلب رمز جديد من الكمبيوتر")
        db.collection("users").document(owner).update("devices", FieldValue.arrayUnion(device)).await()
        doc.reference.update(mapOf("owner" to owner)).await()
    }

    fun signOut(context: Context) {
        Firebase.auth.signOut()
        val p = prefs(context)
        pending(context).clear()
        listOf("owner", "email", "since", "lastSync").forEach { p.set(it, null) }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Settings → التزامن مع الكمبيوتر. */
@Composable
fun SyncSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { Repository.get(context) }
    var owner by remember { mutableStateOf(PhoneSync.owner(context)) }
    var note by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val p = PhoneSync.prefs(context)
    Text("التزامن مع الكمبيوتر", style = MaterialTheme.typography.labelLarge)
    if (owner == null) {
        Muted("سجّل دخول بحساب Google، وبعدها كل تعديل هون بيوصل لبرنامج الكمبيوتر، وكل تعديل هناك بيوصل لهون.")
        Action("تسجيل الدخول بحساب Google", {
            val activity = context.findActivity() ?: return@Action
            scope.launch {
                note = "عم يسجّل…"
                runCatching { PhoneSync.signIn(activity, repo) }
                    .onSuccess { owner = PhoneSync.owner(context); note = "" }
                    .onFailure { note = "ما زبط تسجيل الدخول: ${it.message ?: it.javaClass.simpleName}" }
            }
        }, Modifier.fillMaxWidth())
    } else {
        val last = p.get("lastSync")?.toLongOrNull()
        Muted("مسجّل بـ ⁦${p.get("email").orEmpty()}⁩" + (last?.let { " · آخر تزامن ${fmt12(Clock.minuteOfDay(it))}" } ?: ""))
        Muted("لربط الكمبيوتر: افتح البرنامج عليه، بيطلعلك رمز، واكتبه هون.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(code, { code = it.take(8) }, label = { Text("رمز الكمبيوتر") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Secondary("ربط الكمبيوتر", {
            scope.launch {
                note = "عم يربط…"
                runCatching { PhoneSync.pair(context, code) }
                    .onSuccess { note = "انربط الكمبيوتر ✓"; code = "" }
                    .onFailure { note = it.message ?: "ما زبط الربط" }
            }
        }, Modifier.fillMaxWidth(), enabled = code.trim().length >= 6)
        Secondary("مزامنة هلأ", { scope.launch { PhoneSync.syncNow(context, repo)?.let { note = "انبعت ${it.sent} · وصل ${it.received}" } } }, Modifier.fillMaxWidth())
        Secondary("تسجيل الخروج من التزامن", { PhoneSync.signOut(context); owner = null }, Modifier.fillMaxWidth())
    }
    val problem = PhoneSync.status.value
    if (note.isNotBlank()) Muted(note)
    if (problem.isNotBlank()) Text(problem, color = warmText(), style = MaterialTheme.typography.bodyMedium)
}
