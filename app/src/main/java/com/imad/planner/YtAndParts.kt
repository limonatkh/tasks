@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.imad.planner

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.YearMonth

// ---------------- YouTube playlist on a task ----------------

@Composable
fun YtLinkDialog(t: Task, current: YtLink?, close: () -> Unit, save: (YtLink?) -> Unit) {
    var url by remember { mutableStateOf(current?.url?.ifBlank { null } ?: current?.list?.let { "https://www.youtube.com/playlist?list=$it" } ?: "") }
    var ep by remember { mutableStateOf(((current?.index ?: 0) + 1).toString()) }
    var min by remember { mutableStateOf(((current?.pos ?: 0) / 60).toString()) }
    val id = YtLink.listId(url)
    AlertDialog(
        onDismissRequest = close,
        title = { Text("قائمة يوتيوب لـ«${t.baseTitle}»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(url, "رابط قائمة التشغيل", { url = it })
                if (url.isNotBlank() && id == null) Text("الرابط لازم يكون فيه list=…", color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(ep, "وصلت للحلقة رقم", { ep = it }, numeric = true, modifier = Modifier.weight(1f))
                    Field(min, "عند الدقيقة", { min = it }, numeric = true, modifier = Modifier.weight(1f))
                }
                Muted("لما تبدأ المهمة بتشتغل الحلقة جوّا التطبيق من مكانك، وبيحفظ وين وقفت وبينقلك للي بعدها.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                id?.let {
                    save(YtLink(it, ((digits(ep).toIntOrNull() ?: 1) - 1).coerceAtLeast(0), (digits(min).toIntOrNull() ?: 0) * 60, current?.total ?: 0, url.trim()))
                    close()
                }
            }, enabled = id != null) { Text("حفظ") }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { save(null); close() }) { Text("شيل الرابط") }
                TextButton(onClick = close) { Text("إلغاء") }
            }
        },
    )
}

/** Bridge from the page: index, second, count and state of the player. */
private class YtBridge(val onProgress: (Int, Int, Int, Int) -> Unit, val onError: (Int) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface fun progress(index: Int, sec: Int, total: Int, state: Int) { main.post { onProgress(index, sec, total, state) } }
    @JavascriptInterface fun error(code: Int) { main.post { onError(code) } }
}

private fun playerPage(l: YtLink) = """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000}#p{width:100%;height:100%}</style></head>
<body><div id="p"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
var player, started=false;
function onYouTubeIframeAPIReady(){
  player=new YT.Player('p',{width:'100%',height:'100%',playerVars:{playsinline:1,rel:0,autoplay:1},
    events:{
      onReady:function(e){ e.target.loadPlaylist({listType:'playlist',list:'${l.list}',index:${l.index},startSeconds:${l.pos}}); },
      onStateChange:function(e){ report(); },
      onError:function(e){ Android.error(e.data); }
    }});
}
function report(){
  try{ var pl=player.getPlaylist(); Android.progress(player.getPlaylistIndex(), Math.floor(player.getCurrentTime()), pl?pl.length:0, player.getPlayerState()); }catch(x){}
}
setInterval(report,4000);
</script></body></html>
"""

/**
 * Plays the playlist inside the app (an app cannot see where the YouTube app itself stopped). Every few seconds it
 * keeps the episode and second; when an episode ends the next one plays and becomes the new place.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtPlayerSheet(title: String, link: YtLink, close: () -> Unit, save: (YtLink) -> Unit) {
    val context = LocalContext.current
    var cur by remember { mutableStateOf(link) }
    var error by remember { mutableStateOf<Int?>(null) }
    var view by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) { onDispose { view?.destroy() } }
    Dialog(onDismissRequest = { save(cur); close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(Modifier.fillMaxSize().background(Color.Black)) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("حلقة ${cur.index + 1}" + (if (cur.total > 0) " من ${cur.total}" else "") + if (cur.pos > 0) " · ${cur.pos / 60}:${"%02d".format(cur.pos % 60)}" else "",
                            color = Color(0xFFBBBBBB))
                    }
                    IconButton(onClick = { save(cur); close() }) { Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = Color.White) }
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.domStorageEnabled = true
                            webChromeClient = WebChromeClient()
                            addJavascriptInterface(YtBridge({ i, sec, total, state ->
                                val moved = i != cur.index
                                cur = cur.copy(index = i.coerceAtLeast(0), pos = if (moved) 0 else sec, total = if (total > 0) total else cur.total,
                                    finished = state == 0 && total > 0 && i >= total - 1)
                                if (moved || state == 2 || state == 0) save(cur)
                            }, { code -> error = code }), "Android")
                            loadDataWithBaseURL("https://planner.local/", playerPage(link), "text/html", "utf-8", null)
                            view = this
                        }
                    },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cur.finished) Text("خلصت السلسلة ✓", color = Status.done, fontWeight = FontWeight.Bold)
                    error?.let {
                        Text(if (it == 101 || it == 150 || it == 153) "هاي الحلقة ما بتسمح تشتغل برا يوتيوب." else "تعذّر تشغيل الحلقة ($it).", color = Color(0xFFFF8A80))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Secondary("حفظ مكاني", { save(cur) }, Modifier.weight(1f))
                        Secondary("افتح بيوتيوب", {
                            save(cur)
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://www.youtube.com/playlist?list=${cur.list}&index=${cur.index + 1}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }, Modifier.weight(1f))
                    }
                    Text("بيوتيوب نفسه ما بقدر أتابع وين وقفت. المتابعة بس هون.", color = Color(0xFF999999))
                }
            }
        }
    }
}

// ---------------- Split a task ----------------

@Composable
fun SplitDialog(t: Task, close: () -> Unit, split: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("قسّم «${t.baseTitle}» (${durLabel(t.minutes)})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (2..4).forEach { n -> FilterChip(false, { split(n); close() }, label = { Text("$n أجزاء · ${durLabel(t.minutes / n)}") }) }
                }
                Muted("كل جزء بيصير مهمة لحاله، وبتقدر تحطه بفترة غير.")
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
    )
}

// ---------------- Year plan in parts ----------------

/**
 * A year plan by hand: how many parts (12 months, 6 + 6, 4 × 3 … or custom lengths), each part's name and target
 * (a number or a percentage of the year's goal), then the months of each part.
 */
@Composable
fun YearPartsSheet(
    title: String, unit: String, yearGoal: Double, firstMonth: YearMonth, existing: List<YearPart>,
    monthValue: (YearMonth) -> Double?, close: () -> Unit, save: (List<YearPart>) -> Unit, editMonth: (YearMonth) -> Unit,
) {
    val parts = remember { mutableStateListOf<YearPart>().apply { addAll(existing.ifEmpty { YearPart.equal(12) }) } }
    var step by remember { mutableStateOf(if (existing.isEmpty()) 0 else 2) }
    Sheet("خطة سنة: $title", close) {
        when (step) {
            0 -> {
                Text("كم جزء بدك تقسم السنة؟", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(12 to "١٢ شهر", 6 to "٦ (كل جزء شهرين)", 4 to "٤ (كل جزء ٣ أشهر)", 3 to "٣ (كل جزء ٤ أشهر)", 2 to "٢ (٦ و٦)", 1 to "جزء واحد").forEach { (n, l) ->
                        FilterChip(false, { parts.clear(); parts.addAll(YearPart.equal(n)); step = if (n == 12) 2 else 1 }, label = { Text(l) })
                    }
                    FilterChip(false, { parts.clear(); parts.addAll(listOf(YearPart("الجزء 1", 6), YearPart("الجزء 2", 6))); step = 1 }, label = { Text("أجزاء مش متساوية") })
                }
            }
            1 -> {
                Text("الأجزاء (مجموع الأشهر لازم ١٢)", style = MaterialTheme.typography.titleMedium)
                parts.forEachIndexed { i, p ->
                    Panel {
                        Field(p.name, "اسم الجزء", { parts[i] = p.copy(name = it) })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("أشهر", Modifier.weight(1f))
                            Stepper(p.months) { parts[i] = p.copy(months = it.coerceIn(1, 12)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(!p.percent, { parts[i] = p.copy(percent = false) }, label = { Text("رقم") })
                            FilterChip(p.percent, { parts[i] = p.copy(percent = true) }, label = { Text("نسبة من هدف السنة") })
                        }
                        var v by remember("$i${p.percent}") { mutableStateOf(if (p.value == 0.0) "" else num(p.value)) }
                        Field(v, if (p.percent) "النسبة ٪" else "الهدف ($unit)", { v = it; parts[i] = parts[i].copy(value = digits(it).toDoubleOrNull() ?: 0.0) }, numeric = true)
                        if (p.percent && yearGoal > 0 && p.value > 0) Muted("يعني ${num(yearGoal * p.value / 100)} $unit")
                        if (parts.size > 1) TextButton(onClick = { parts.removeAt(i) }) { Text("شيل الجزء") }
                    }
                }
                Secondary("+ جزء", { parts.add(YearPart("الجزء ${parts.size + 1}", 1)) }, Modifier.fillMaxWidth())
                val sum = parts.sumOf { it.months }
                if (sum != 12) Text("المجموع $sum شهر", color = MaterialTheme.colorScheme.error)
                Action("التالي: أهداف الأشهر", { save(parts.toList()); step = 2 }, Modifier.fillMaxWidth(), enabled = sum == 12)
                TextButton(onClick = { step = 0 }) { Text("رجوع") }
            }
            else -> {
                var m = 0
                parts.forEachIndexed { pi, p ->
                    if (parts.size > 1 || p.months < 12) {
                        Text((p.name.ifBlank { "الجزء ${pi + 1}" }) + if (p.value > 0) " · هدف ${num(p.value)}${if (p.percent) "٪" else " $unit"}" else "",
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    repeat(p.months) {
                        val ym = firstMonth.plusMonths(m.toLong())
                        val v = monthValue(ym)
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(monthLabel(ym), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(v?.let { "${num(it)} $unit" } ?: "—", fontWeight = FontWeight.Medium)
                            TextButton(onClick = { editMonth(ym) }) { Text(if (v == null) "حدد" else "عدّل") }
                        }
                        m++
                    }
                    HorizontalDivider()
                }
                Secondary("غيّر الأجزاء", { step = 0 }, Modifier.fillMaxWidth())
            }
        }
    }
}
