package com.imad.planner

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** One chosen account as Instagram describes it. */
class IgAccount(val user: String, val id: String, val pic: String, val name: String)
/** One story frame: an image, or a video with its cover image. */
class IgStory(val user: String, val image: String, val video: String, val at: Long)
/** One photo post (videos and reels are left out): its pictures, words and time. */
class IgItem(val user: String, val code: String, val images: List<String>, val at: Long, val caption: String)

/**
 * Instagram the calm way, in two parts:
 *  - «حساباتي»: stories and photo posts of only the accounts you add (a link or a name), drawn by the planner.
 *    They are asked for from inside Instagram's own page, signed in with your own login, the same calls the site makes.
 *  - «الرسائل»: Instagram's own messages page. Its home feed, Reels and Explore are closed: any way into them goes
 *    back to the messages.
 * The password goes to Instagram only; the app keeps nothing but the sign-in cookie.
 */
class IgFocusActivity : ComponentActivity() {
    private var web: WebView? = null
    private var tab by mutableIntStateOf(0)
    private var pageProgress by mutableIntStateOf(0)
    private var stopped by mutableIntStateOf(0)
    private var pageReady by mutableStateOf(false)
    private var loggedIn by mutableStateOf(Ig.loggedIn())

    // What came back for the chosen accounts.
    private val accounts = mutableStateMapOf<String, IgAccount>()
    private val posts = mutableStateListOf<IgItem>()
    private val stories = mutableStateMapOf<String, List<IgStory>>()
    private val problems = mutableStateMapOf<String, String>()
    private var loading by mutableStateOf(false)
    private var lastLoad by mutableStateOf(0L)

    private val inbox = "https://www.instagram.com/direct/inbox/"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = Repository.get(this)
        val w = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            @Suppress("DEPRECATION")
            settings.saveFormData = false
            settings.userAgentString = Ig.UA
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            setBackgroundColor(android.graphics.Color.WHITE)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(Bridge(), "PlannerFocus")
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, p: Int) {
                    this@IgFocusActivity.pageProgress = p
                    if (p > 30) view.evaluateJavascript(FOCUS_JS, null)
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    val u = r.url.toString()
                    if (!(u.startsWith("http://") || u.startsWith("https://"))) return true
                    if (blocked(r.url.path)) { stopped++; v.loadUrl(inbox); return true }
                    return !Ig.loginAllowed(u)
                }
                override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) {
                    if (blocked(android.net.Uri.parse(url).path)) { v.stopLoading(); v.loadUrl(inbox); stopped++ }
                }
                override fun onPageFinished(v: WebView, url: String) {
                    v.evaluateJavascript(FOCUS_JS, null)
                    CookieManager.getInstance().flush()
                    loggedIn = Ig.loggedIn()
                    pageReady = url.startsWith("https://www.instagram.com/")
                }
            }
            val start = intent.getStringExtra("url")?.takeIf { it.startsWith("https://www.instagram.com/") }
            if (start != null && blocked(android.net.Uri.parse(start).path)) { stopped++; loadUrl(inbox) }
            else loadUrl(start ?: if (Ig.loggedIn()) inbox else "https://www.instagram.com/accounts/login/")
            if (start != null || !Ig.loggedIn()) tab = 1
        }
        web = w
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    tab == 1 && w.canGoBack() -> w.goBack()
                    tab == 1 && loggedIn -> tab = 0
                    else -> finish()
                }
            }
        })
        val ui = getSharedPreferences("ui", MODE_PRIVATE)
        setContent {
            CompositionLocalProvider(LocalThemeChoice provides ThemeChoice(ui.getBoolean("mono", false), {}, ui.getBoolean("calm", false), {})) {
                PlannerTheme(ui.getBoolean("mono", false)) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        val scope = rememberCoroutineScope()
                        var people by remember { mutableStateOf<List<IgPerson>>(emptyList()) }
                        LaunchedEffect(Unit) { people = Ig.people(repo.store.meta("igPeople2")) }
                        val save: (List<IgPerson>) -> Unit = { list ->
                            people = list
                            scope.launch { repo.setSetting("igPeople2", Ig.encodePeople(list)) }
                        }
                        // Load once the page is up and signed in; again on demand.
                        LaunchedEffect(pageReady, loggedIn, people.size) {
                            if (pageReady && loggedIn && people.isNotEmpty() && !loading &&
                                (lastLoad == 0L || people.any { it.user !in accounts && it.user !in problems })) load(people.map { it.user })
                        }
                        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            Column(
                                Modifier.fillMaxWidth().background(headerColor(), RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                                    .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text("إنستغرام الهادي", fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Color.White)
                                        Text(if (tab == 0) "بس الحسابات اللي اخترتها" else "الرسائل · بلا فيد ولا ريلز ولا إكسبلور" + if (stopped > 0) " · وقّفت $stopped" else "",
                                            fontSize = 13.sp, color = Color(0xFFDCE4FF))
                                    }
                                    if (tab == 0) HeaderButton("تحديث", { if (!loading) load(people.map { it.user }) }) { Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White) }
                                    HeaderButton("رجوع للمخطط", { finish() }) { Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White) }
                                }
                                Segmented(listOf("حساباتي", "الرسائل"), tab) { i ->
                                    if (i == 1) { if (tab == 1 || w.url?.contains("/direct/") != true) w.loadUrl(if (loggedIn) inbox else "https://www.instagram.com/accounts/login/") }
                                    tab = i
                                }
                            }
                            if (tab == 1 && pageProgress in 1..99 || tab == 0 && loading)
                                LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = ctaColor())
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                // The page stays alive under the list: it is what asks Instagram for the chosen accounts.
                                AndroidView(factory = { w }, modifier = Modifier.fillMaxSize())
                                if (tab == 0) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                    MyAccounts(people, save, openWeb = { url -> w.loadUrl(url); tab = 1 })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Ask Instagram (from inside its page) for each account's profile and posts, then their stories together. */
    private fun load(users: List<String>) {
        val w = web ?: return
        if (users.isEmpty()) return
        loading = true
        problems.clear()
        pending.clear()
        w.evaluateJavascript(fetchJs(users), null)
    }

    private val pending = mutableMapOf<String, IgAccount>()

    inner class Bridge {
        @JavascriptInterface fun stopped() { runOnUiThread { stopped++ } }

        @JavascriptInterface fun got(kind: String, user: String, code: Int, body: String) {
            runOnUiThread {
                when (kind) {
                    "p" -> if (code == 200) parseProfile(user, body) else problems[user] = Ig.why(code)
                    "s" -> if (code == 200) parseStories(body)
                    "end" -> { loading = false; lastLoad = System.currentTimeMillis() }
                }
            }
        }
    }

    private fun parseProfile(user: String, body: String) {
        runCatching {
            val u = JSONObject(body).getJSONObject("data").getJSONObject("user")
            accounts[user] = IgAccount(user, u.optString("id"), u.optString("profile_pic_url_hd").ifBlank { u.optString("profile_pic_url") }, u.optString("full_name"))
            val edges = u.getJSONObject("edge_owner_to_timeline_media").getJSONArray("edges")
            val mine = (0 until edges.length()).mapNotNull { i ->
                val n = edges.getJSONObject(i).getJSONObject("node")
                if (n.optBoolean("is_video") || n.optString("product_type") == "clips") return@mapNotNull null
                val kids = n.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
                val images = if (kids != null) (0 until kids.length()).mapNotNull { k ->
                    kids.getJSONObject(k).getJSONObject("node").takeIf { !it.optBoolean("is_video") }?.optString("display_url")
                } else listOf(n.optString("display_url"))
                val cap = n.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")?.optJSONObject(0)?.optJSONObject("node")?.optString("text") ?: ""
                IgItem(user, n.optString("shortcode"), images.filter { it.isNotBlank() }, n.optLong("taken_at_timestamp") * 1000, cap)
            }.filter { it.images.isNotEmpty() }
            posts.removeAll { it.user == user }
            posts.addAll(mine)
            posts.sortByDescending { it.at }
        }.onFailure { problems[user] = if (body.contains("login", true)) "إنستغرام بدّه تسجيل دخول" else "ما فهمت رد إنستغرام" }
    }

    private fun parseStories(body: String) {
        runCatching {
            val o = JSONObject(body)
            val reels = mutableListOf<JSONObject>()
            o.optJSONObject("reels")?.let { m -> m.keys().forEach { k -> m.optJSONObject(k)?.let(reels::add) } }
            o.optJSONArray("reels_media")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let(reels::add) }
            stories.clear()
            for (r in reels) {
                val user = r.optJSONObject("user")?.optString("username")?.lowercase() ?: continue
                val items = r.optJSONArray("items") ?: JSONArray()
                stories[user] = (0 until items.length()).mapNotNull { i ->
                    val it = items.getJSONObject(i)
                    val img = it.optJSONObject("image_versions2")?.optJSONArray("candidates")?.optJSONObject(0)?.optString("url") ?: return@mapNotNull null
                    val vid = it.optJSONArray("video_versions")?.optJSONObject(0)?.optString("url").orEmpty()
                    IgStory(user, img, vid, it.optLong("taken_at") * 1000)
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun MyAccounts(people: List<IgPerson>, save: (List<IgPerson>) -> Unit, openWeb: (String) -> Unit) {
        var text by remember { mutableStateOf("") }
        var wrong by remember { mutableStateOf(false) }
        var manage by remember { mutableStateOf(people.isEmpty()) }
        var viewing by remember { mutableStateOf<String?>(null) }
        val withStories = people.map { it.user }.filter { stories[it].orEmpty().isNotEmpty() }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!loggedIn) item {
                Panel {
                    NourNote(NourMood.Awake, "سجّل دخول مرة وحدة", "بصفحة إنستغرام نفسها. بعدها بتجيك ستوريات ومنشورات الحسابات اللي بتختارها.", 90.dp)
                    Action("تسجيل الدخول", { openWeb("https://www.instagram.com/accounts/login/") }, Modifier.fillMaxWidth())
                }
            }
            item {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الحسابات · ${people.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        androidx.compose.material3.TextButton(onClick = { manage = !manage }) { Text(if (manage) "تم" else "تعديل") }
                    }
                    if (manage) {
                        OutlinedTextField(
                            value = text, onValueChange = { text = it; wrong = false }, singleLine = true, isError = wrong,
                            label = { Text("رابط الحساب أو اسمه") }, placeholder = { Text("instagram.com/... أو @name") },
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                        )
                        if (wrong) Text("ما عرفت الحساب من هاد. الصق رابط صفحته أو اكتب اسمه.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        Action("إضافة", {
                            val names = text.split(Regex("[\\s,،]+")).mapNotNull { Ig.username(it) }.distinct()
                            if (names.isEmpty()) wrong = true
                            else {
                                save(people + names.filter { n -> people.none { it.user == n } }.map { IgPerson(it, System.currentTimeMillis()) })
                                text = ""
                            }
                        }, Modifier.fillMaxWidth())
                    }
                    if (people.isEmpty()) Muted("لسا ما في حسابات. ضيف أول حساب وبتطلع ستورياته ومنشوراته هون، وبس هو.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        people.forEach { p ->
                            InputChip(selected = false, onClick = { if (manage) save(people - p) else openWeb("https://www.instagram.com/${p.user}/") },
                                label = { Text("@" + p.user) },
                                avatar = accounts[p.user]?.pic?.let { url -> { AsyncImage(url, null, Modifier.size(22.dp).clip(CircleShape), contentScale = ContentScale.Crop) } },
                                trailingIcon = if (manage) ({ Icon(Icons.Filled.Close, contentDescription = "شيل ${p.user}", modifier = Modifier.size(16.dp)) }) else null)
                        }
                    }
                    problems.forEach { (u, why) -> Text("@$u: $why", fontSize = 13.sp, color = warmText()) }
                }
            }
            if (people.isNotEmpty() && loggedIn) item {
                if (withStories.isEmpty()) Muted(if (loading) "بجيب الستوريات…" else "ما في ستوري جديدة عند حساباتك هلأ.")
                else LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(withStories) { u ->
                        Column(Modifier.width(76.dp).clickable { viewing = u }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(70.dp).border(3.dp, ctaColor(), CircleShape).padding(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                accounts[u]?.pic?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            }
                            Text(u, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (people.isNotEmpty() && loggedIn && posts.isEmpty() && !loading) item {
                NourNote(NourMood.Sleep, "ما في منشورات", "يا إما الحسابات ما نزّلت صور، يا إما إنستغرام ما ردّ. جرّب التحديث.", 90.dp)
            }
            items(posts.toList(), key = { it.user + it.code }) { p -> PostCard(p, openWeb) }
        }
        viewing?.let { u -> StoryViewer(withStories, u) { viewing = null } }
    }

    @Composable
    private fun PostCard(p: IgItem, openWeb: (String) -> Unit) {
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    accounts[p.user]?.pic?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Column(Modifier.weight(1f)) {
                    Text(p.user, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(ago(p.at), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.TextButton(onClick = { openWeb("https://www.instagram.com/p/${p.code}/") }) { Text("افتح") }
            }
            if (p.images.size == 1) AsyncImage(p.images[0], null, Modifier.fillMaxWidth().aspectRatio(0.8f).clickable { openWeb("https://www.instagram.com/p/${p.code}/") }, contentScale = ContentScale.Crop)
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(p.images) { img -> AsyncImage(img, null, Modifier.fillParentMaxWidth(0.92f).aspectRatio(0.8f), contentScale = ContentScale.Crop) }
            }
            if (p.caption.isNotBlank()) Text(p.caption, Modifier.padding(12.dp), maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        }
    }

    /**
     * Full-screen stories, account after account. Pictures stay 5 s, videos play to their end. Tap the left side for
     * the next one, the right side for the one before (as the page reads in Arabic); × closes.
     */
    @Composable
    private fun StoryViewer(users: List<String>, first: String, close: () -> Unit) {
        var ui by remember { mutableIntStateOf(users.indexOf(first).coerceAtLeast(0)) }
        var si by remember { mutableIntStateOf(0) }
        val list = stories[users.getOrNull(ui)].orEmpty()
        val s = list.getOrNull(si)
        fun next() { if (si + 1 < list.size) si++ else if (ui + 1 < users.size) { ui++; si = 0 } else close() }
        fun prev() { if (si > 0) si-- else if (ui > 0) { ui--; si = 0 } }
        Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(ui, si) {
                    detectTapGestures { pos -> if (pos.x < size.width / 2) next() else prev() }
                }) {
                    if (s == null) { LaunchedEffect(Unit) { close() }; return@Box }
                    if (s.video.isNotBlank()) {
                        AndroidView(factory = { c ->
                            android.widget.VideoView(c).apply { setOnCompletionListener { next() }; setOnErrorListener { _, _, _ -> next(); true } }
                        }, update = { v -> if (v.tag != s.video) { v.tag = s.video; v.setVideoURI(android.net.Uri.parse(s.video)); v.start() } },
                            modifier = Modifier.fillMaxSize().align(Alignment.Center))
                    } else {
                        AsyncImage(s.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        LaunchedEffect(ui, si) { delay(5000); next() }
                    }
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            list.indices.forEach { i ->
                                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= si) Color.White else Color.White.copy(alpha = 0.35f)))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(Color.DarkGray)) {
                                accounts[s.user]?.pic?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            }
                            Text(s.user, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold)
                            Text(ago(s.at), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, textAlign = TextAlign.End)
                            Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = Color.White, modifier = Modifier.size(30.dp).clickable { close() })
                        }
                    }
                }
            }
        }
    }

    private fun ago(at: Long): String {
        val m = ((System.currentTimeMillis() - at) / 60_000).coerceAtLeast(0)
        return when { m < 60 -> "قبل $m د"; m < 1440 -> "قبل ${m / 60} س"; else -> "قبل ${m / 1440} يوم" }
    }

    override fun onPause() { CookieManager.getInstance().flush(); web?.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); web?.onResume() }
    override fun onDestroy() { web?.destroy(); web = null; super.onDestroy() }

    companion object {
        /** Paths never shown: the home feed, Reels (tab, single reels, a profile's reels), Explore. */
        fun blocked(path: String?): Boolean {
            val p = path ?: return false
            if (p == "" || p == "/") return Ig.loggedIn()
            return Regex("^/(reels?|explore)(/|$)").containsMatchIn(p) || Regex("^/[^/]+/reels?/?$").containsMatchIn(p)
        }

        fun fetchJs(users: List<String>) = """
(async function(){
  var users = ${JSONArray(users)}, ids = [];
  var h = {credentials: 'include', headers: {'X-IG-App-ID': '${Ig.APP_ID}', 'X-Requested-With': 'XMLHttpRequest'}};
  function wait(ms){ return new Promise(function(ok){ setTimeout(ok, ms); }); }
  for (var i = 0; i < users.length; i++) {
    var u = users[i];
    try {
      var r = await fetch('/api/v1/users/web_profile_info/?username=' + encodeURIComponent(u), h);
      var t = await r.text();
      try { var id = JSON.parse(t).data.user.id; if (id) ids.push(id); } catch (e) {}
      PlannerFocus.got('p', u, r.status, t);
    } catch (e) { PlannerFocus.got('p', u, 0, String(e)); }
    await wait(700);
  }
  if (ids.length) {
    try {
      var r2 = await fetch('/api/v1/feed/reels_media/?' + ids.map(function(i){ return 'reel_ids=' + i; }).join('&'), h);
      PlannerFocus.got('s', '', r2.status, await r2.text());
    } catch (e) { PlannerFocus.got('s', '', 0, String(e)); }
  }
  PlannerFocus.got('end', '', 0, '');
})();
"""

        const val FOCUS_JS = """
(function(){
  if (window.__plannerFocus) { window.__plannerFocus(); return; }
  var bad = /^\/(reels?|explore)(\/|$)|^\/[^\/]+\/reels?\/?$/;
  function path(u){ try { return new URL(u, location.href).pathname; } catch(e) { return ''; } }
  function tell(){ try { PlannerFocus.stopped(); } catch(e) {} }
  var css = 'a[href="/reels/"],a[href^="/reels"],a[href="/explore/"],a[href^="/explore"]{display:none!important}' +
            'a[href$="/reels/"]{display:none!important}';
  var st = document.createElement('style'); st.id = 'planner-focus'; st.textContent = css;
  (document.head || document.documentElement).appendChild(st);
  document.addEventListener('click', function(e){
    var a = e.target && e.target.closest ? e.target.closest('a') : null;
    if (!a) return;
    var h = a.getAttribute('href') || '';
    if (bad.test(path(h)) || h.indexOf('/reel/') >= 0) { e.preventDefault(); e.stopPropagation(); tell(); }
  }, true);
  // A page that still ends up somewhere closed goes to the messages (a full load, so nothing is left half-open).
  function guard(){
    var p = location.pathname, home = (p === '/' || p === '') && document.cookie.indexOf('ds_user_id') >= 0;
    if (bad.test(p) || home) { tell(); location.replace('/direct/inbox/'); }
  }
  // Instagram sometimes leaves an empty, loading window over the page; after 3 s it is taken away.
  function unstick(){
    document.querySelectorAll('div[role="dialog"]').forEach(function(d){
      if (!d.__pfSeen) { d.__pfSeen = Date.now(); return; }
      var empty = !(d.innerText || '').trim() && !d.querySelector('img,video,input,textarea,button');
      if (empty && Date.now() - d.__pfSeen > 3000) {
        var n = d; while (n.parentElement && n.parentElement !== document.body) n = n.parentElement;
        n.remove(); document.body.style.overflow = ''; document.documentElement.style.overflow = '';
      }
    });
  }
  ['pushState','replaceState'].forEach(function(k){
    var f = history[k];
    history[k] = function(){ var r = f.apply(this, arguments); setTimeout(guard, 0); return r; };
  });
  window.addEventListener('popstate', guard);
  setInterval(function(){ guard(); unstick(); }, 1000);
  window.__plannerFocus = function(){ guard(); unstick(); };
  guard();
})();
"""
    }
}
