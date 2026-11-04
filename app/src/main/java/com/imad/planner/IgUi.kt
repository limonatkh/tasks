package com.imad.planner

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A followed account and when it was added (only posts after that show). */
data class IgPerson(val user: String, val addedAt: Long)

/** One post or reel in the feed. state: new | kept | gone. */
data class IgPost(val id: String, val user: String, val code: String, val image: String, val video: String, val at: Long, val caption: String, val state: String = "new")

/**
 * Following chosen people without opening Instagram. Login is on Instagram's own page (no password is kept);
 * when the feed opens on Wi-Fi it asks Instagram, with that login, for each person's latest posts and keeps the
 * ones newer than the moment the person was added.
 */
object Ig {
    const val APP_ID = "936619743392459"
    const val UA = "Mozilla/5.0 (Linux; Android 13; SM-M135F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    fun username(text: String): String? {
        val t = text.trim()
        Regex("instagram\\.com/(?:stories/)?([A-Za-z0-9._]{1,30})", RegexOption.IGNORE_CASE).find(t)?.let { m ->
            val u = m.groupValues[1].lowercase()
            if (u !in reserved) return u
        }
        val plain = t.removePrefix("@").lowercase()
        return plain.takeIf { Regex("^[a-z0-9._]{1,30}$").matches(it) && it !in reserved }
    }

    private val reserved = setOf("p", "reel", "reels", "explore", "accounts", "direct", "stories", "tv", "about", "legal")

    fun parse(text: String?): List<String> = runCatching {
        val a = JSONArray(text ?: "[]"); (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    fun encode(list: List<String>) = JSONArray(list).toString()

    fun people(text: String?): List<IgPerson> = runCatching {
        val a = JSONArray(text ?: "[]"); (0 until a.length()).map { a.getJSONObject(it).let { o -> IgPerson(o.getString("u"), o.optLong("at")) } }
    }.getOrDefault(emptyList())

    fun encodePeople(list: List<IgPerson>) = JSONArray().apply { list.forEach { put(JSONObject().put("u", it.user).put("at", it.addedAt)) } }.toString()

    fun posts(text: String?): List<IgPost> = runCatching {
        val a = JSONArray(text ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it).let { o -> IgPost(o.getString("id"), o.optString("u"), o.optString("c"), o.optString("i"), o.optString("v"), o.optLong("t"), o.optString("cap"), o.optString("s", "new")) } }
    }.getOrDefault(emptyList())

    fun encodePosts(list: List<IgPost>) = JSONArray().apply {
        list.forEach { put(JSONObject().put("id", it.id).put("u", it.user).put("c", it.code).put("i", it.image).put("v", it.video).put("t", it.at).put("cap", it.caption).put("s", it.state)) }
    }.toString()

    fun cookies(): String = CookieManager.getInstance().getCookie("https://www.instagram.com") ?: ""
    fun loggedIn() = cookies().contains("sessionid=")

    fun onWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** The latest posts of one account as Instagram's web page gets them. Null when Instagram refused. */
    fun fetch(user: String): List<IgPost>? {
        val cookie = cookies()
        val csrf = Regex("csrftoken=([^;]+)").find(cookie)?.groupValues?.get(1) ?: ""
        val c = (URL("https://www.instagram.com/api/v1/users/web_profile_info/?username=$user").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("User-Agent", UA); setRequestProperty("Cookie", cookie); setRequestProperty("X-IG-App-ID", APP_ID)
            setRequestProperty("X-CSRFToken", csrf); setRequestProperty("Referer", "https://www.instagram.com/$user/"); setRequestProperty("Accept", "*/*")
        }
        return try {
            if (c.responseCode != 200) return null
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val edges = o.getJSONObject("data").getJSONObject("user").getJSONObject("edge_owner_to_timeline_media").getJSONArray("edges")
            (0 until edges.length()).map { i ->
                val n = edges.getJSONObject(i).getJSONObject("node")
                val cap = n.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")?.optJSONObject(0)?.optJSONObject("node")?.optString("text") ?: ""
                IgPost(n.getString("id"), user, n.optString("shortcode"), n.optString("display_url"), if (n.optBoolean("is_video")) n.optString("video_url") else "",
                    n.optLong("taken_at_timestamp") * 1000, cap.take(300))
            }
        } catch (_: Exception) { null } finally { c.disconnect() }
    }

    /** Pages allowed while logging in: Instagram's (and Facebook's, for "log in with Facebook"). */
    fun loginAllowed(url: String): Boolean {
        val host = Uri.parse(url).host ?: return true
        return host.endsWith("instagram.com") || host.endsWith("facebook.com") || host.endsWith("fbcdn.net") || host.endsWith("cdninstagram.com")
    }
}

/**
 * The feed: new posts and reels from the chosen people only, nothing else. Opening it on Wi-Fi checks for new
 * ones (at most every few hours); Keep moves a post to Saved, Remove hides it for good.
 */
@Composable
fun IgFeedScreen(s: Snapshot, close: () -> Unit, repo: Repository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val people = Ig.people(s.meta["igPeople2"])
    val posts = Ig.posts(s.meta["igFeed"])
    var tab by remember { mutableStateOf(0) }
    var settings by remember { mutableStateOf(people.isEmpty()) }
    var login by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var video by remember { mutableStateOf<IgPost?>(null) }
    val refresh: (Boolean) -> Unit = { force ->
        if (!busy) scope.launch {
            busy = true
            status = repo.igRefresh(force)
            busy = false
        }
    }
    LaunchedEffect(Unit) {
        if (people.isNotEmpty() && Ig.loggedIn() && Ig.onWifi(context)) refresh(false)
        else if (people.isNotEmpty() && !Ig.loggedIn()) status = "سجّل دخول من الإعدادات ⚙ عشان يجيب البوستات."
        else if (people.isNotEmpty()) status = "مش على واي فاي، فما حمّلت جديد."
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = close) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") }
                    Text("الفيد", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    FilterChip(tab == 0, { tab = 0 }, label = { Text("الجديد · ${posts.count { it.state == "new" }}") })
                    Text(" ")
                    FilterChip(tab == 1, { tab = 1 }, label = { Text("المحفوظ") })
                    IconButton(onClick = { settings = true }) { Icon(Icons.Filled.Settings, contentDescription = "الإعدادات") }
                }
                if (busy) Text("بجيب الجديد…", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary)
                else if (status.isNotEmpty()) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(status, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (Ig.loggedIn() && people.isNotEmpty()) TextButton(onClick = { refresh(true) }) { Text("حمّل الآن") }
                }
                val shown = posts.filter { if (tab == 0) it.state == "new" else it.state == "kept" }.sortedByDescending { it.at }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (shown.isEmpty()) item {
                        Muted(if (tab == 0) "ما في جديد." else "ما حفظت إشي لسا.")
                    }
                    items(shown, key = { it.id }) { p ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("@${p.user}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                    Text(dayLabel(Clock.dayOf(p.at)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color.Black).clickable(enabled = p.video.isNotEmpty()) { video = p },
                                    contentAlignment = Alignment.Center) {
                                    AsyncImage(model = p.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    if (p.video.isNotEmpty()) Text("▶", color = Color.White, style = MaterialTheme.typography.displayMedium)
                                }
                                if (p.caption.isNotBlank()) Text(p.caption, Modifier.padding(12.dp), maxLines = 4, style = MaterialTheme.typography.bodyMedium)
                                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (p.state != "kept") Action("إبقاء", { scope.launch { repo.igMark(p.id, "kept") } }, Modifier.weight(1f))
                                    Secondary("إزالة", { scope.launch { repo.igMark(p.id, "gone") } }, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (settings) IgSettings(people, loggedIn = Ig.loggedIn(), close = { settings = false }, login = { login = true },
        save = { l -> scope.launch { repo.igSetPeople(l) } })
    if (login) IgLogin(close = { login = false; if (Ig.loggedIn()) refresh(true) })
    video?.let { p -> IgVideo(p, close = { video = null }) }
}

@Composable
private fun IgSettings(people: List<IgPerson>, loggedIn: Boolean, close: () -> Unit, login: () -> Unit, save: (List<IgPerson>) -> Unit) {
    var text by remember { mutableStateOf("") }
    val u = Ig.username(text)
    Sheet("الأشخاص اللي بتابعهم", close) {
        Muted("الصق رابط صفحة الشخص. بيطلعلك من بوستاته وريلزاته الجديد بس، من وقت ما تضيفه.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(text, "https://www.instagram.com/…", { text = it }, modifier = Modifier.weight(1f))
            TextButton(onClick = { u?.let { n -> if (people.none { it.user == n }) save(people + IgPerson(n, System.currentTimeMillis())) }; text = "" }, enabled = u != null) { Text("إضافة") }
        }
        if (text.isNotBlank()) Muted(u?.let { "@$it" } ?: "ما عرفت اسم الحساب من الرابط")
        people.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${p.user}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { save(people - p) }) { Text("شيل", color = MaterialTheme.colorScheme.error) }
            }
        }
        if (loggedIn) {
            Muted("مسجّل دخول ✓")
            TextButton(onClick = { CookieManager.getInstance().removeAllCookies(null) }) { Text("تسجيل خروج") }
        } else Action("تسجيل دخول إنستغرام", login, Modifier.fillMaxWidth())
        Muted("كلمة السر بتنكتب بصفحة إنستغرام نفسها، والتطبيق ما بيشوفها ولا بيحفظها. التحميل بيصير بس لما تفتح الفيد وإنت على واي فاي.")
    }
}

/** Instagram's own login page; it closes itself once logged in. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun IgLogin(close: () -> Unit) {
    var view by remember { mutableStateOf<WebView?>(null) }
    var done by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { view?.destroy() } }
    LaunchedEffect(Unit) { while (!done) { delay(1000); if (Ig.loggedIn()) { done = true; CookieManager.getInstance().flush(); close() } } }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.White)) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("تسجيل دخول إنستغرام", Modifier.weight(1f).padding(8.dp), fontWeight = FontWeight.Bold, color = Color.Black)
                IconButton(onClick = close) { Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = Color.Black) }
            }
            AndroidView(factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = Ig.UA
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = !Ig.loginAllowed(r.url.toString())
                    }
                    loadUrl("https://www.instagram.com/accounts/login/")
                    view = this
                }
            }, modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun IgVideo(p: IgPost, close: () -> Unit) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = close), contentAlignment = Alignment.Center) {
            AndroidView(factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.parse(p.video))
                    setOnPreparedListener { it.isLooping = true; start() }
                }
            }, modifier = Modifier.fillMaxWidth())
        }
    }
}
