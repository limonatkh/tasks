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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Instagram the calm way: Instagram's own site inside the app, with Reels and Explore taken out, so what is left is
 * the posts of the people you follow and your messages. Nothing private is used: it is the same page a phone browser
 * shows, signed in with Instagram's own login. The password goes to Instagram only; the app keeps nothing but the
 * sign-in cookie, and the page is told not to offer saving passwords.
 *
 * How the distractions go away (the page is a single-page app, so all four are needed):
 *  1. links to /reels/ and /explore/ are hidden by CSS;
 *  2. a tap on such a link, or on any /reel/ post, is stopped before it happens;
 *  3. in-page navigation (history.pushState) to them is refused, and a page that still lands there goes back home;
 *  4. posts in the feed that are reels or videos are folded away as they appear.
 */
class IgFocusActivity : ComponentActivity() {
    private var web: WebView? = null
    private var tab by mutableIntStateOf(0)
    private var pageProgress by mutableIntStateOf(0)
    private var stopped by mutableIntStateOf(0)
    private var note by mutableStateOf<String?>(null)

    private val pages = listOf("https://www.instagram.com/", "https://www.instagram.com/direct/inbox/")

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab = intent.getIntExtra("tab", 0).coerceIn(0, 1)
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
                    pageProgress = p
                    if (p > 30) view.evaluateJavascript(FOCUS_JS, null)
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    val u = r.url.toString()
                    if (!(u.startsWith("http://") || u.startsWith("https://"))) return true
                    if (blocked(r.url.path)) { stopped++; return true }
                    return !Ig.loginAllowed(u)
                }
                override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) {
                    if (blocked(android.net.Uri.parse(url).path)) { v.stopLoading(); v.loadUrl(pages[0]); stopped++ }
                }
                override fun onPageFinished(v: WebView, url: String) {
                    v.evaluateJavascript(FOCUS_JS, null)
                    CookieManager.getInstance().flush()
                    note = if (url.contains("/accounts/login") || !Ig.loggedIn()) "سجّل دخول بصفحة إنستغرام نفسها. التطبيق ما بيحفظ كلمة السر، وخلّي «حفظ معلومات الدخول» زي ما بدك." else null
                }
            }
            // A start address can be given (checks use it); only Instagram pages, and the blocking still applies.
            val start = intent.getStringExtra("url")?.takeIf { it.startsWith("https://www.instagram.com/") }
            if (start != null && blocked(android.net.Uri.parse(start).path)) { stopped++; loadUrl(pages[0]) } else loadUrl(start ?: pages[tab])
        }
        web = w
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (w.canGoBack()) w.goBack() else finish() }
        })
        val ui = getSharedPreferences("ui", MODE_PRIVATE)
        setContent {
            CompositionLocalProvider(LocalThemeChoice provides ThemeChoice(ui.getBoolean("mono", false), {}, ui.getBoolean("calm", false), {})) {
                PlannerTheme(ui.getBoolean("mono", false)) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            Column(
                                Modifier.fillMaxWidth().background(headerColor(), RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                                    .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text("إنستغرام الهادي", fontFamily = DisplayFont, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = 24.sp, color = Color.White)
                                        Text(if (stopped > 0) "بلا ريلز ولا إكسبلور · وقّفت $stopped" else "بلا ريلز ولا إكسبلور: منشورات ورسائل بس",
                                            fontSize = 13.sp, color = Color(0xFFDCE4FF))
                                    }
                                    HeaderButton("رجوع للمخطط", { finish() }) { Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White) }
                                }
                                Segmented(listOf("المنشورات", "الرسائل"), tab) { i ->
                                    if (i == tab) web?.loadUrl(pages[i]) else { tab = i; web?.loadUrl(pages[i]) }
                                }
                            }
                            if (pageProgress in 1..99) LinearProgressIndicator(progress = { pageProgress / 100f }, modifier = Modifier.fillMaxWidth().height(3.dp), color = ctaColor())
                            note?.let { Text(it, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Box(Modifier.weight(1f).fillMaxWidth()) { AndroidView(factory = { w }, modifier = Modifier.fillMaxSize()) }
                        }
                    }
                }
            }
        }
    }

    /** Called from the page when it refused a reel or explore. */
    inner class Bridge {
        @JavascriptInterface fun stopped() { runOnUiThread { stopped++ } }
    }

    override fun onPause() { CookieManager.getInstance().flush(); web?.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); web?.onResume() }
    override fun onDestroy() { web?.destroy(); web = null; super.onDestroy() }

    companion object {
        /** Paths that are never shown: the Reels tab and single reels, Explore. */
        fun blocked(path: String?): Boolean {
            val p = path ?: return false
            return Regex("^/(reels?|explore)(/|$)").containsMatchIn(p) || Regex("^/[^/]+/reels?/?$").containsMatchIn(p)
        }

        const val FOCUS_JS = """
(function(){
  if (window.__plannerFocus) { window.__plannerFocus(); return; }
  var bad = /^\/(reels?|explore)(\/|$)|^\/[^\/]+\/reels?\/?$/;
  function path(u){ try { return new URL(u, location.href).pathname; } catch(e) { return ''; } }
  function tell(){ try { PlannerFocus.stopped(); } catch(e) {} }
  var css = 'a[href="/reels/"],a[href^="/reels"],a[href="/explore/"],a[href^="/explore"],a[href*="/reel/"] svg[aria-label*="Reel"]{display:none!important}' +
            'a[href*="/reels/"][role="tab"],a[href$="/reels/"]{display:none!important}';
  var st = document.createElement('style'); st.id = 'planner-focus'; st.textContent = css;
  (document.head || document.documentElement).appendChild(st);
  ['pushState','replaceState'].forEach(function(k){
    var f = history[k];
    history[k] = function(a, b, u){ if (u && bad.test(path(u))) { tell(); return; } return f.apply(this, arguments); };
  });
  document.addEventListener('click', function(e){
    var a = e.target && e.target.closest ? e.target.closest('a') : null;
    if (!a) return;
    var h = a.getAttribute('href') || '';
    if (bad.test(path(h)) || h.indexOf('/reel/') >= 0) { e.preventDefault(); e.stopPropagation(); tell(); }
  }, true);
  function fold(){
    if (bad.test(location.pathname)) { tell(); location.replace('/'); return; }
    if (location.pathname !== '/' ) return;
    document.querySelectorAll('article').forEach(function(a){
      if (a.__pf) return;
      if (a.querySelector('a[href*="/reel/"]') || a.querySelector('video')) { a.__pf = 1; a.style.display = 'none'; }
    });
  }
  var t = null;
  new MutationObserver(function(){ if (!t) t = setTimeout(function(){ t = null; fold(); }, 120); })
    .observe(document.documentElement, { childList: true, subtree: true });
  window.addEventListener('popstate', fold);
  window.__plannerFocus = fold;
  fold();
})();
"""
    }
}
