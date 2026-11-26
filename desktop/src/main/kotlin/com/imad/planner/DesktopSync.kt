package com.imad.planner

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.UUID

/**
 * The computer's side of the sync, over Firebase's web addresses (no Google SDK on Windows):
 * - It signs in to Firebase as an anonymous device (no account, no password on the computer).
 * - Pairing: it shows a code; the phone (signed in with Google) enters it and adds this device to its account.
 * - Then it reads and writes the same cloud rows as the phone (users/<owner>/rows).
 * The project's address and public key come from firebase.properties, made at build time from google-services.json.
 */
object FirebaseConfig {
    private val props by lazy {
        java.util.Properties().apply { FirebaseConfig::class.java.classLoader.getResourceAsStream("firebase.properties")?.use { load(it) } }
    }
    val apiKey: String? get() = props.getProperty("apiKey")?.takeIf { it.isNotBlank() }
    val projectId: String? get() = props.getProperty("projectId")?.takeIf { it.isNotBlank() }
    val ready get() = apiKey != null && projectId != null
}

/** The computer's [SyncPrefs]: %APPDATA%\SmartLifePlanner\sync.json, written a moment after changes. */
class FilePrefs(private val file: File) : SyncPrefs {
    private val map = LinkedHashMap<String, String>()
    @Volatile private var dirty = false

    init {
        runCatching { JSONObject(file.readText()).let { o -> o.keys().forEach { map[it] = o.getString(it) } } }
        Thread({ while (true) { Thread.sleep(1500); flush() } }, "sync-prefs").apply { isDaemon = true }.start()
        Runtime.getRuntime().addShutdownHook(Thread { flush() })
    }

    override fun get(key: String): String? = synchronized(map) { map[key] }
    override fun set(key: String, value: String?) { synchronized(map) { if (value == null) map.remove(key) else map[key] = value }; dirty = true }
    override fun all(prefix: String): Map<String, String> = synchronized(map) { map.filterKeys { it.startsWith(prefix) } }

    fun flush() {
        if (!dirty) return
        dirty = false
        val text = synchronized(map) { JSONObject(map as Map<*, *>).toString() }
        runCatching { saveAtomically(file, text) }
    }
}

/** Plain HTTPS calls with JSON in and out. */
private object Http {
    fun call(method: String, url: String, body: String? = null, token: String? = null, form: Boolean = false): JSONObject? {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") setRequestProperty("X-HTTP-Method-Override", "PATCH")
            connectTimeout = 15_000; readTimeout = 30_000
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", if (form) "application/x-www-form-urlencoded" else "application/json; charset=utf-8")
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        try {
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
            if (code == 404) return null
            if (code !in 200..299) throw IllegalStateException("Firebase $code: " + text.take(300))
            val t = text.trim()
            return when {
                t.isEmpty() -> JSONObject()
                t.startsWith("[") -> JSONObject().put("list", JSONArray(t))
                else -> JSONObject(t)
            }
        } finally { c.disconnect() }
    }
}

/** Anonymous Firebase sign-in for this computer, kept in [prefs] and refreshed when it expires. */
class DeviceAuth(private val prefs: SyncPrefs) {
    private val key get() = FirebaseConfig.apiKey ?: error("التزامن مش جاهز بهالنسخة")
    val uid: String? get() = prefs.get("uid")

    fun token(): String {
        val exp = prefs.get("tokenExp")?.toLongOrNull() ?: 0
        prefs.get("idToken")?.let { if (System.currentTimeMillis() < exp - 60_000) return it }
        val refresh = prefs.get("refresh")
        return if (refresh == null) signUp() else refresh(refresh)
    }

    private fun signUp(): String {
        val r = Http.call("POST", "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$key", """{"returnSecureToken":true}""")!!
        save(r.getString("idToken"), r.getString("refreshToken"), r.getString("localId"), r.optString("expiresIn", "3600").toLong())
        return r.getString("idToken")
    }

    private fun refresh(refresh: String): String {
        val r = Http.call("POST", "https://securetoken.googleapis.com/v1/token?key=$key",
            "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refresh, "UTF-8"), form = true)!!
        save(r.getString("id_token"), r.getString("refresh_token"), r.getString("user_id"), r.optString("expires_in", "3600").toLong())
        return r.getString("id_token")
    }

    private fun save(id: String, refresh: String, uid: String, seconds: Long) {
        prefs.set("idToken", id); prefs.set("refresh", refresh); prefs.set("uid", uid)
        prefs.set("tokenExp", (System.currentTimeMillis() + seconds * 1000).toString())
    }
}

/** Firestore over its REST address: the same documents the phone's SDK writes. */
class RestCloud(private val auth: DeviceAuth, private val owner: String) : Cloud {
    private val base get() = "https://firestore.googleapis.com/v1/projects/${FirebaseConfig.projectId}/databases/(default)/documents"
    private val docBase get() = "projects/${FirebaseConfig.projectId}/databases/(default)/documents"

    override suspend fun push(rows: List<CloudRow>) {
        val writes = JSONArray()
        rows.forEach { r ->
            val fields = JSONObject()
                .put("table", sv(r.table)).put("id", sv(r.id))
                .put("data", if (r.data == null) JSONObject().put("nullValue", JSONObject.NULL) else sv(r.data))
                .put("at", JSONObject().put("integerValue", r.at.toString())).put("device", sv(r.device))
            writes.put(JSONObject().put("update", JSONObject().put("name", "$docBase/users/$owner/rows/${docId(r.table, r.id)}").put("fields", fields)))
        }
        Http.call("POST", "$base:commit", JSONObject().put("writes", writes).toString(), auth.token())
    }

    override suspend fun pull(since: Long): List<CloudRow> {
        val q = JSONObject().put("structuredQuery", JSONObject()
            .put("from", JSONArray().put(JSONObject().put("collectionId", "rows")))
            .put("where", JSONObject().put("fieldFilter", JSONObject()
                .put("field", JSONObject().put("fieldPath", "at")).put("op", "GREATER_THAN_OR_EQUAL")
                .put("value", JSONObject().put("integerValue", since.toString())))))
        val list = Http.call("POST", "$base/users/$owner:runQuery", q.toString(), auth.token())?.optJSONArray("list") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val f = list.getJSONObject(i).optJSONObject("document")?.optJSONObject("fields") ?: return@mapNotNull null
            CloudRow(str(f, "table") ?: return@mapNotNull null, str(f, "id") ?: return@mapNotNull null, str(f, "data"),
                f.optJSONObject("at")?.optString("integerValue")?.toLongOrNull() ?: 0L, str(f, "device") ?: "")
        }
    }

    companion object {
        fun sv(s: String) = JSONObject().put("stringValue", s)
        fun str(f: JSONObject, k: String): String? = f.optJSONObject(k)?.let { if (it.has("stringValue")) it.getString("stringValue") else null }
        /** Same document names as the phone (FirestoreCloud.docId). */
        fun docId(table: String, id: String): String = URLEncoder.encode("$table|$id", "UTF-8")
    }
}

/**
 * The computer's sync state and actions, used by the sync panel and the loop in Main.kt.
 * Pairing replaces what is on the computer with what is in the account (the phone's data).
 */
class DesktopSync(private val repo: Repository, private val inner: Store, val prefs: FilePrefs, val pending: Pending) {
    private val auth = DeviceAuth(prefs)
    val owner: String? get() = prefs.get("owner")
    private val device: String get() = prefs.get("device") ?: ("pc-" + UUID.randomUUID()).also { prefs.set("device", it) }

    /** Starts pairing: a new code, written to the cloud for the phone to find. */
    fun newCode(): String {
        val abc = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        val code = (1..6).map { abc[rnd.nextInt(abc.length)] }.joinToString("")
        val token = auth.token()
        val base = "https://firestore.googleapis.com/v1/projects/${FirebaseConfig.projectId}/databases/(default)/documents"
        val fields = JSONObject().put("device", RestCloud.sv(auth.uid!!)).put("createdAt", JSONObject().put("integerValue", System.currentTimeMillis().toString()))
        Http.call("PATCH", "$base/pair/$code?currentDocument.exists=false", JSONObject().put("fields", fields).toString(), token)
        prefs.set("code", code)
        return code
    }

    /** True once the phone accepted the code; the computer then takes the account's data. */
    suspend fun checkPaired(): Boolean {
        val code = prefs.get("code") ?: return false
        val base = "https://firestore.googleapis.com/v1/projects/${FirebaseConfig.projectId}/databases/(default)/documents"
        val doc = Http.call("GET", "$base/pair/$code", token = auth.token()) ?: return false
        val owner = doc.optJSONObject("fields")?.let { RestCloud.str(it, "owner") } ?: return false
        repo.exclusive {
            inner.clearTasks(); inner.clearGoals(); inner.clearRules(); inner.clearProjects(); inner.clearLogs(); inner.clearEvents()
            inner.allMeta().forEach { inner.deleteMeta(it.key) }
        }
        pending.clear()
        prefs.set("since", null); prefs.set("code", null); prefs.set("owner", owner)
        syncNow()
        return true
    }

    suspend fun syncNow(): SyncResult? {
        val owner = owner ?: return null
        val r = SyncEngine(inner, pending, prefs, RestCloud(auth, owner), device, repo::exclusive).syncOnce()
        prefs.set("lastSync", System.currentTimeMillis().toString())
        return r
    }

    fun unpair() {
        pending.clear()
        listOf("owner", "since", "lastSync", "code").forEach { prefs.set(it, null) }
    }
}
