package com.imad.planner

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray

/**
 * People the user follows without opening Instagram: only their pages can be opened, in a browser that refuses
 * everything else (feed, explore, reels, search, other accounts). Login happens on Instagram's own page;
 * the app never sees or keeps the password.
 */
object Ig {
    /** "https://www.instagram.com/name/?igsh=…" or "@name" or "name" → "name". */
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

    /** Pages the browser may show: login steps, the chosen people, their stories, and single posts opened from them. */
    fun allowed(url: String, people: List<String>): Boolean {
        val u = android.net.Uri.parse(url)
        val host = u.host ?: return false
        if (!host.endsWith("instagram.com")) return host.endsWith("cdninstagram.com") || host.endsWith("fbcdn.net") || host == "about:blank"
        val parts = u.pathSegments.map { it.lowercase() }
        if (parts.isEmpty()) return false
        return when (parts[0]) {
            "accounts", "challenge", "two_factor" -> true
            "stories" -> parts.getOrNull(1) in people
            "p", "reel" -> true
            else -> parts[0] in people
        }
    }
}

@Composable
fun IgSheet(s: Snapshot, close: () -> Unit, save: (List<String>) -> Unit, seen: (String) -> Unit) {
    val people = Ig.parse(s.meta["igPeople"])
    val seenToday = Ig.parse(s.meta["igSeen:${s.today}"]).toSet()
    var adding by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    var stories by remember { mutableStateOf(false) }
    Sheet("متابعة أشخاص", close) {
        Muted("بتفتح صفحات هالأشخاص بس. الفيد وإكسبلور والريلز وأي حساب ثاني مقفولين. تسجيل الدخول عند إنستغرام نفسه، والتطبيق ما بيحفظ كلمة السر.")
        Action("+ أضف شخص (الصق رابط صفحته)", { adding = true }, Modifier.fillMaxWidth())
        if (people.isEmpty()) Muted("لسا ما أضفت حدا.")
        people.forEach { p ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("@$p", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (p in seenToday) Text("شفته اليوم ✓", style = MaterialTheme.typography.bodyMedium, color = Status.done)
                    }
                    TextButton(onClick = { save(people - p) }) { Text("شيل", color = MaterialTheme.colorScheme.error) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Action("الصفحة", { stories = false; open = p }, Modifier.weight(1f))
                    Secondary("الستوريات", { stories = true; open = p }, Modifier.weight(1f))
                    Secondary(if (p in seenToday) "✓" else "شفته", { seen(p) }, Modifier.weight(0.7f))
                }
            }
        }
        if (people.isNotEmpty()) TextButton(onClick = { CookieManager.getInstance().removeAllCookies(null) }) { Text("تسجيل خروج من إنستغرام") }
    }
    if (adding) {
        var text by remember { mutableStateOf("") }
        val u = Ig.username(text)
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("رابط صفحة الشخص") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Field(text, "https://www.instagram.com/…", { text = it })
                    if (text.isNotBlank()) Muted(u?.let { "@$it" } ?: "ما عرفت اسم الحساب من الرابط")
                }
            },
            confirmButton = { TextButton(onClick = { u?.let { if (it !in people) save(people + it) }; adding = false }, enabled = u != null) { Text("إضافة") } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("إلغاء") } },
        )
    }
    open?.let { p -> IgBrowser(p, stories, people, close = { open = null }) }
}

/** A browser that only goes where [Ig.allowed] says; anything else sends it back to the person's page. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun IgBrowser(person: String, stories: Boolean, people: List<String>, close: () -> Unit) {
    val home = if (stories) "https://www.instagram.com/stories/$person/" else "https://www.instagram.com/$person/"
    var view by remember { mutableStateOf<WebView?>(null) }
    var blocked by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { view?.destroy() } }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("@$person", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { view?.loadUrl(home) }) { Text("رجوع للصفحة") }
                    IconButton(onClick = close) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") }
                }
                if (blocked) Text("هاي الصفحة مقفولة. رجعتك لصفحة @$person.", Modifier.padding(horizontal = 12.dp).clickable { blocked = false },
                    color = MaterialTheme.colorScheme.error)
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                                    if (!r.isForMainFrame) return false
                                    if (Ig.allowed(r.url.toString(), people)) return false
                                    blocked = true; v.loadUrl(home); return true
                                }
                                override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) {
                                    if (!Ig.allowed(url, people)) { blocked = true; v.stopLoading(); v.loadUrl(home) }
                                }
                                // Instagram changes pages without loading them; every change of address is checked too.
                                override fun doUpdateVisitedHistory(v: WebView, url: String, isReload: Boolean) {
                                    if (!Ig.allowed(url, people)) { blocked = true; v.loadUrl(home) }
                                }
                            }
                            loadUrl(home)
                            view = this
                        }
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}
