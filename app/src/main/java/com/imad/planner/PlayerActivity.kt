package com.imad.planner

import android.annotation.SuppressLint
import androidx.compose.ui.platform.ComposeView
import android.widget.FrameLayout
import android.view.ViewGroup
import android.view.View
import android.graphics.Bitmap
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plays a task's YouTube playlist full screen, turning with the phone. Controls show on a tap and hide by
 * themselves. Every few seconds the episode and second are kept, so the next time starts where it stopped.
 * An episode counts when it is watched to its end, or when the user says the rest is not needed.
 */
class PlayerActivity : ComponentActivity() {
    companion object {
        fun intent(context: Context, series: String, title: String, taskId: String?) = Intent(context, PlayerActivity::class.java)
            .putExtra("series", series).putExtra("title", title).putExtra("task", taskId)
    }

    private lateinit var repo: Repository
    private var link: YtLink? = null
    private var series = ""
    private var taskId: String? = null
    private var web: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = Repository.get(this)
        series = intent.getStringExtra("series") ?: ""
        taskId = intent.getStringExtra("task")
        val title = intent.getStringExtra("title") ?: ""
        // A link given directly (used when checking the screens) or the one saved on the series.
        link = intent.getStringExtra("list")?.let { YtLink(it, intent.getIntExtra("index", 0), intent.getIntExtra("pos", 0)) }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        lifecycleScope.launch {
            if (link == null) link = Settings.from(repo.store.allMeta().associate { it.key to it.value }).ytLinks[series]
            val l = link ?: run { finish(); return@launch }
            // The page is a plain view straight in the window, and the buttons are drawn over it on a clear layer,
            // so nothing of ours is painted under the video.
            val root = FrameLayout(this@PlayerActivity).apply { setBackgroundColor(android.graphics.Color.BLACK) }
            container = root
            val controls = ComposeView(this@PlayerActivity).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setContent {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        PlayerScreen(title, l, makeWeb = { createWeb(it) }, js = { web?.evaluateJavascript(it, null) },
                            save = { keep(it) }, ended = { i, total -> episodeEnded(i, total) }, close = { finish() })
                    }
                }
            }
            root.addView(controls, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setContentView(root)
        }
    }

    private var container: FrameLayout? = null
    private var last: YtLink? = null
    private fun currentLink(): YtLink = last ?: link!!

    private fun keep(l: YtLink) {
        last = l
        if (series.isNotEmpty()) lifecycleScope.launch { runCatching { repo.setYt(series, l) } }
    }

    private fun episodeEnded(index: Int, total: Int) {
        if (series.isEmpty()) return
        lifecycleScope.launch { runCatching { repo.episodeDone(taskId, series, index + 1, total) } }
    }

    override fun onPause() {
        super.onPause()
        web?.evaluateJavascript("try{player.pauseVideo()}catch(e){}", null)
        last?.let { keep(it) }
    }

    override fun onDestroy() {
        web?.destroy(); web = null
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWeb(bridge: Any): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.domStorageEnabled = true
        setBackgroundColor(android.graphics.Color.BLACK)
        // Drawn into its own GPU layer: the video frames go into the page's picture like everything else.
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webChromeClient = object : WebChromeClient() {
            // No grey "poster" box while a video loads.
            override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        container?.addView(this, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addJavascriptInterface(bridge, "Android")
        if (intent.getBooleanExtra("test", false)) { loadDataWithBaseURL("https://com.imad.planner", "<html><body style='margin:0;background:#000;color:#fff;font-size:28px'><div style='padding:12px'>اختبار الصورة</div><video id=v style='width:100%' autoplay loop playsinline controls src='https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4'></video></body></html>", "text/html", "utf-8", null); web = this; return@apply }
        // YouTube's own embed page is the whole page (no frame inside our page), so its picture is drawn like any page.
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) { view.evaluateJavascript(hook(currentLink()), null) }
            // Only YouTube's own pages may load here (the mobile page is the fallback for blocked embeds).
            override fun shouldOverrideUrlLoading(view: WebView, r: WebResourceRequest) = r.url.host?.endsWith("youtube.com") != true
        }
        loadUrl(embedUrl(link!!), mapOf("Referer" to "https://com.imad.planner/"))
        web = this
    }

    private fun embedUrl(l: YtLink): String {
        val common = "autoplay=1&controls=0&playsinline=1&rel=0&iv_load_policy=3&fs=0&disablekb=1&enablejsapi=1&origin=https://com.imad.planner&start=${l.pos}"
        return if (l.list.startsWith("v:")) {
            val ids = l.list.removePrefix("v:").split(",").filter { it.isNotBlank() }
            val first = ids.getOrElse(l.index) { ids.first() }
            val rest = ids.drop(l.index + 1)
            "https://www.youtube.com/embed/$first?" + (if (rest.isNotEmpty()) "playlist=${rest.joinToString(",")}&" else "") + common
        } else "https://www.youtube.com/embed/videoseries?list=${l.list}&index=${l.index}&$common"
    }

}

private class Bridge(
    val onProgress: (Int, Float, Float, Int, Int) -> Unit, val onEnded: (Int, Int) -> Unit, val onError: (Int) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface fun progress(i: Int, t: Double, d: Double, n: Int, state: Int) { main.post { onProgress(i, t.toFloat(), d.toFloat(), n, state) } }
    @JavascriptInterface fun ended(i: Int, n: Int) { main.post { onEnded(i, n) } }
    @JavascriptInterface fun error(code: Int) { main.post { onError(code) } }
}

@Composable
private fun PlayerScreen(title: String, start: YtLink, makeWeb: (Any) -> WebView, js: (String) -> Unit, save: (YtLink) -> Unit, ended: (Int, Int) -> Unit, close: () -> Unit) {
    var index by remember { mutableIntStateOf(start.index) }
    var total by remember { mutableIntStateOf(start.total) }
    var time by remember { mutableFloatStateOf(start.pos.toFloat()) }
    var duration by remember { mutableFloatStateOf(0f) }
    var playing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var shown by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    var ticks by remember { mutableIntStateOf(0) }
    var countedFor by remember { mutableIntStateOf(-1) }
    // Controls hide by themselves a few seconds after the last touch, while playing.
    LaunchedEffect(touch, playing) { if (playing) { delay(3500); shown = false } }
    val bridge = remember {
        Bridge({ i, t, d, n, state ->
            if (i >= 0 && i != index) { index = i; time = 0f }
            if (seeking == null) time = t
            if (d > 0) duration = d
            if (n > 0) total = n
            playing = state == 1
            ticks++
            if (ticks % 5 == 0 || state == 2) save(YtLink(start.list, index, time.toInt(), total, start.url))
        }, { i, n ->
            if (countedFor != i) { countedFor = i; ended(i, n) }
            save(YtLink(start.list, i + 1, 0, n, start.url, finished = n > 0 && i + 1 >= n))
        }, { error = it })
    }
    fun poke() { shown = true; touch++ }
    fun clock(s: Float) = (s.toInt()).let { "%d:%02d".format(it / 60, it % 60) }
    remember { makeWeb(bridge) }
    Box(Modifier.fillMaxSize()) {
        // A layer over the video takes the taps (show or hide the controls), so they never reach YouTube's page.
        Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (shown) shown = false else poke() })
        AnimatedVisibility(visible = shown, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
                Row(Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("حلقة ${index + 1}" + if (total > 0) " من $total" else "", color = Color(0xFFDDDDDD), fontSize = 14.sp)
                    }
                    RoundButton("✕", 44) { save(YtLink(start.list, index, time.toInt(), total, start.url)); close() }
                }
                // Media buttons read left to right, like every player.
                androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundButton("⏮", 52) { poke(); js("player.previousVideo()") }
                    RoundButton("10-", 52) { poke(); js("player.seekTo(Math.max(0,player.getCurrentTime()-10),true)") }
                    RoundButton(if (playing) "⏸" else "▶", 72) { poke(); js(if (playing) "player.pauseVideo()" else "player.playVideo()") }
                    RoundButton("+10", 52) { poke(); js("player.seekTo(player.getCurrentTime()+10,true)") }
                    RoundButton("⏭", 52) { poke(); js("player.nextVideo()") }
                }
                }
                Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    error?.let { Text(if (it == 101 || it == 150 || it == 153) "هاي الحلقة صاحبها مانع تشغيلها برا يوتيوب ($it). اضغط ⏭ للي بعدها." else "تعذّر تشغيل الحلقة ($it).", color = Color(0xFFFF8A80)) }
                    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(clock(seeking ?: time), color = Color.White, fontSize = 13.sp)
                            Slider(
                                value = if (duration > 0) ((seeking ?: time) / duration).coerceIn(0f, 1f) else 0f,
                                onValueChange = { poke(); seeking = it * duration },
                                onValueChangeFinished = { seeking?.let { js("player.seekTo($it,true)"); time = it }; seeking = null },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(thumbColor = Color(0xFFE8916B), activeTrackColor = Color(0xFFE8916B), inactiveTrackColor = Color(0x55FFFFFF)),
                            )
                            Text(clock(duration), color = Color.White, fontSize = 13.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Box(Modifier.background(Color(0xFF2E9E5B), RoundedCornerShape(22.dp)).clickable {
                            // The rest of the episode is not needed: it counts as watched and the next one starts.
                            if (countedFor != index) { countedFor = index; ended(index, total) }
                            save(YtLink(start.list, index + 1, 0, total, start.url, finished = total > 0 && index + 1 >= total))
                            js("player.nextVideo()"); poke()
                        }.padding(horizontal = 22.dp, vertical = 10.dp)) { Text("انتهت الحلقة ✓", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun RoundButton(label: String, size: Int, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).background(Color(0x88000000), CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = (size / 3).sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Runs inside YouTube's page (the embed page, or the mobile watch page when the owner blocks embedding):
 * names its player `player`, goes to the saved episode and second first, and only then reports every second,
 * so a fresh start never overwrites where the user had reached.
 */
private fun hook(l: YtLink) = """
(function(){
  if (window.__hooked) return; window.__hooked = true;
  var T = ${l.index}, P = ${l.pos}, LIST = '${if (l.list.startsWith("v:")) "" else l.list}';
  var mobile = location.host.indexOf('m.youtube') >= 0;
  var st = document.createElement('style');
  st.textContent = '#movie_player > :not(.html5-video-container):not(.ytp-error){display:none!important}.ytp-chrome-top,.ytp-chrome-bottom,.ytp-gradient-top,.ytp-gradient-bottom,.ytp-pause-overlay,.ytp-watermark,.ytp-ce-element,.ytp-endscreen-content,.ytp-show-cards-title,.ytp-paid-content-overlay{display:none!important}' +
    (mobile ? 'html,body{background:#000!important;overflow:hidden!important}ytm-mobile-topbar-renderer,header,ytm-pivot-bar-renderer,ytm-item-section-renderer,ytm-single-column-watch-next-results-renderer,ytm-watch-metadata-app-promo-renderer,ytm-companion-slot,.mobile-topbar-header,.watch-below-the-player,ytm-engagement-panel{display:none!important}#player-container-id,#player-container,#player,.player-container,#movie_player,#movie_player video{position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;z-index:99999!important;background:#000!important}video{object-fit:contain!important}' : '');
  (document.head || document.documentElement).appendChild(st);
  var reached = mobile || T <= 0, jumped = false, ended = -1;
  window.__err = function(e){
    var code = (e && e.data !== undefined) ? e.data : e;
    var p = window.player;
    if (!mobile && (code == 101 || code == 150) && p) {
      // Embedding is blocked by the owner: the same episode plays from YouTube's mobile page instead.
      var pl = p.getPlaylist ? p.getPlaylist() : null, i = p.getPlaylistIndex ? p.getPlaylistIndex() : 0;
      var id = (pl && pl[i]) || (p.getVideoData && p.getVideoData().video_id);
      if (id) { var t = reached ? Math.floor(p.getCurrentTime() || 0) : P; location.href = 'https://m.youtube.com/watch?v=' + id + (LIST ? '&list=' + LIST : '') + '&t=' + t + 's'; return; }
    }
    try { Android.error(code); } catch(x){}
  };
  setInterval(function(){
    var p = document.getElementById('movie_player');
    if (!p || !p.getCurrentTime) return;
    window.player = p;
    if (!window.__ev) { window.__ev = true; try { p.addEventListener('onError', '__err'); } catch(x){} }
    try {
      var pl = p.getPlaylist ? p.getPlaylist() : null, i = p.getPlaylistIndex ? p.getPlaylistIndex() : 0, s = p.getPlayerState();
      if (!reached) {
        if (!pl || !pl.length) return;
        if (i !== T && T < pl.length) { if (!jumped) { jumped = true; p.playVideoAt(T); } return; }
        reached = true;
        if (P > 3) p.seekTo(P, true);
        return;
      }
      if (mobile && i < 0) i = T;
      Android.progress(i < 0 ? 0 : i, p.getCurrentTime(), p.getDuration(), pl ? pl.length : 0, s);
      if (s === 0 && ended !== i) { ended = i; Android.ended(i < 0 ? 0 : i, pl ? pl.length : 0); }
      if (s !== 0) ended = -1;
    } catch(x){}
  }, 1000);
})();
"""
