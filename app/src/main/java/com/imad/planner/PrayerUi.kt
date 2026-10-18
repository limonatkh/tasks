@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.imad.planner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.LocalDate

/** Prayer times: today's times, the message for ChatGPT, the place to paste its answer, and the reserved minutes. */
@Composable
fun PrayerSheet(s: Settings, today: LocalDate, close: () -> Unit, set: (String, String) -> Unit) {
    val context = LocalContext.current
    val p = s.prayersOn(today)
    var code by remember { mutableStateOf("") }
    var before by remember { mutableStateOf(s.prayerBefore.toString()) }
    var after by remember { mutableStateOf(s.prayerAfter.toString()) }
    var nap by remember { mutableStateOf(s.fajrNap.toString()) }
    val parsed = remember(code) { Prayer.parse(code.map { if (Character.isDigit(it)) ('0' + Character.getNumericValue(it)) else it }.joinToString("")) }
    Sheet("مواقيت الصلاة", close) {
        Panel {
            Text("مواقيت اليوم", style = MaterialTheme.typography.titleMedium)
            listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha").forEach { k ->
                Row {
                    Text(Prayer.names.getValue(k), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(fmt12(p.at(k)), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
            Muted(
                if (p.source == "table") "المصدر: الجدول اللي لصقته" + (s.prayersUntil?.let { " (مغطّي لحد ${dayLabel(it)})" } ?: "")
                else "المصدر: حساب تقريبي (غزة، العصر شافعي) مصحَّح على آخر جدول. الصق جدول الشهر الجديد عشان يرجع دقيق."
            )
        }
        Panel {
            Text("جدول الشهر الجديد", style = MaterialTheme.typography.titleMedium)
            Muted("١) صوّر ورقة المواقيت. ٢) انسخ الرسالة وابعتها مع الصورة لشات جي بي تي. ٣) الصق اللي برجّعه هون واضغط حفظ.")
            Action("نسخ الرسالة", { copyText(context, Prayer.CHATGPT_PROMPT); Toast.makeText(context, "انسخت الرسالة", Toast.LENGTH_SHORT).show() }, Modifier.fillMaxWidth())
            OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("الصق الكود هون") }, modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 10)
            if (code.isNotBlank()) Muted(
                if (parsed.isEmpty()) "ما قدرت أقرأ ولا سطر. تأكد إن كل سطر: التاريخ وبعده الأوقات."
                else "قرأت ${parsed.size} يوم: من ${dayLabel(parsed.first().date)} لـ ${dayLabel(parsed.last().date)}"
            )
            Action("حفظ الجدول", {
                // Days already stored and not in the new code are kept, so nothing is lost when months overlap.
                val old = Prayer.parse(s.prayers).filter { o -> parsed.none { it.date == o.date } }
                set("prayers", Prayer.encode((old + parsed).sortedBy { it.date }.takeLast(400)))
                code = ""
                Toast.makeText(context, "انحفظت المواقيت", Toast.LENGTH_SHORT).show()
            }, Modifier.fillMaxWidth(), enabled = parsed.isNotEmpty())
        }
        Panel {
            Text("وقت محجوز للصلاة", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(before, "دقائق قبل", { before = it; digits(it).toIntOrNull()?.let { v -> if (v in 0..60) set("prayerBefore", v.toString()) } }, numeric = true, modifier = Modifier.weight(1f))
                Field(after, "دقائق بعد", { after = it; digits(it).toIntOrNull()?.let { v -> if (v in 0..90) set("prayerAfter", v.toString()) } }, numeric = true, modifier = Modifier.weight(1f))
            }
            Muted("ما بينحط شي بهاد الوقت. ما في أذان من التطبيق.")
            Field(nap, "نومة بعد الفجر (دقائق، ٠ = بدون)", { nap = it; digits(it).toIntOrNull()?.let { v -> if (v in 0..300) set("fajrNap", v.toString()) } }, numeric = true)
        }
        Panel {
            Text("تعديل الوقت بالدقائق", style = MaterialTheme.typography.titleMedium)
            Muted("لو مسجدك بيقيم بعد الوقت المطبوع، زيد الدقائق هون.")
            Prayer.keys.forEach { k ->
                val v = s.prayerOffsets[k] ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Prayer.names.getValue(k), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { set("prayerOffsets", JSONObject(s.prayerOffsets.toMap()).put(k, v - 5).toString()) }) { Text("−٥") }
                    Text(if (v == 0) "٠" else (if (v > 0) "+$v" else "$v"), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { set("prayerOffsets", JSONObject(s.prayerOffsets.toMap()).put(k, v + 5).toString()) }) { Text("+٥") }
                }
            }
        }
    }
}

fun copyText(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("text", text))
}

/** Where a task's series goes in prayer mode. */
@Composable
fun PlaceDialog(title: String, current: String?, groupPeriod: String?, close: () -> Unit, save: (String) -> Unit) {
    var kind by remember { mutableStateOf(when { current == null -> ""; current.startsWith("a:") -> "a"; else -> "p" }) }
    var period by remember { mutableStateOf(current?.split(":")?.getOrNull(1) ?: groupPeriod ?: "fajr") }
    var minutes by remember { mutableStateOf(current?.split(":")?.getOrNull(2) ?: "30") }
    Sheet("وقت «$title» بالصلوات", close) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = kind == "", onClick = { kind = "" }, label = { Text("حسب المجموعة") })
            FilterChip(selected = kind == "p", onClick = { kind = "p" }, label = { Text("مرنة بفترة") })
            FilterChip(selected = kind == "a", onClick = { kind = "a" }, label = { Text("بعد صلاة بوقت") })
        }
        when (kind) {
            "" -> Muted(groupPeriod?.let { "بتنحط بفترة ${Prayer.periodNames[it]} زي مجموعتها." } ?: "مجموعتها ما إلها فترة، فبتنرتب حسب أجزاء اليوم العادية.")
            "p" -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Prayer.keys.forEach { k -> FilterChip(selected = period == k, onClick = { period = k }, label = { Text(Prayer.periodNames.getValue(k)) }) }
                }
                Muted("بتنعمل بأي وقت جوّا الفترة، وما بتنحسب متأخرة إلا لما تخلص الفترة.")
            }
            else -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Prayer.keys.forEach { k -> FilterChip(selected = period == k, onClick = { period = k }, label = { Text(Prayer.names.getValue(k)) }) }
                }
                Field(minutes, "كم دقيقة بعد الصلاة", { minutes = it }, numeric = true)
            }
        }
        Muted("الموعد الثابت بالساعة بيتحدد من «التفاصيل والتعديل».")
        Action("حفظ", {
            save(when (kind) { "" -> ""; "p" -> "p:$period"; else -> "a:$period:${digits(minutes).toIntOrNull()?.coerceIn(0, 300) ?: 30}" })
            close()
        }, Modifier.fillMaxWidth())
    }
}

/** Pick a period to move a task to (today only). */
@Composable
fun PeriodPicker(periods: List<Period>, now: Int, close: () -> Unit, pick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("لأي فترة؟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                periods.filter { it.end > now }.forEach { p ->
                    Text("${p.name} · ${fmt12(p.start)}–${fmt12(p.end)}", Modifier.fillMaxWidth().clickable { pick(p.key); close() }.padding(vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
    )
}

/** Capacity of one period: free minutes, minutes the tasks need, and what is left over or missing. */
data class PeriodLoad(val period: Period, val tasks: List<Task>, val free: Int, val need: Int) {
    val balance get() = free - need
}

fun periodLoads(s: Snapshot, todays: List<Task>): List<PeriodLoad> {
    val st = s.settings
    val periods = st.periodsOn(s.today)
    val blocks = st.configFor(s.today).blocks
    val nowMin = Clock.minuteOfDay(s.now)
    return periods.map { p ->
        val from = maxOf(p.start, st.dayStart, nowMin)
        val to = minOf(p.end, st.dayEnd)
        // Busy minutes are merged first, so overlapping blocks are not subtracted twice.
        val busy = blocks.map { (a, b) -> maxOf(a, from) to minOf(b, to) }.filter { it.second > it.first }.sortedBy { it.first }
        var taken = 0; var edge = from
        for ((a, b) in busy) { val s0 = maxOf(a, edge); if (b > s0) { taken += b - s0; edge = b } }
        val free = (to - from - taken).coerceAtLeast(0)
        val inside = todays.filter { t -> t.planned > 0 && Clock.minuteOfDay(t.planned).let { it >= p.start && it < p.end } }
        val need = inside.filter { it.open }.sumOf { ((Maths.remaining(it, s.now) + 59_999) / 60_000).toInt() }
        PeriodLoad(p, inside, free, need)
    }
}

/** Ask before postponing; the note is optional. A recurring task also asks what happens with tomorrow's own session. */
@Composable
fun PostponeChoice(t: Task, recurring: Boolean, close: () -> Unit, done: (String, String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(if (recurring) "merge" else "move") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("تأجيل «${t.baseTitle}» لبكرا؟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recurring) {
                    Muted("هاي مهمة متكررة، وإلها جلسة بكرا. شو بدك؟")
                    listOf(
                        "merge" to "دمج: جلسة وحدة أطول بكرا",
                        "keep" to "إبقاء الثنتين: هاي وجلسة بكرا",
                        "skip" to "تخطّي هاي الجلسة: بكرا جلستها العادية",
                    ).forEach { (k, label) ->
                        FilterChip(selected = mode == k, onClick = { mode = k }, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
                    }
                    Muted("بكل الحالات مواعيد السلسلة ما بتتغير. ترحيل آخر اليوم الإجباري بيضل يزحزح السلسلة زي قبل.")
                }
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("ملاحظة (اختياري): ليش أجّلتها؟") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { done(text, mode) }) { Text(if (mode == "skip") "تخطّي" else "تأجيل") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
    )
}

/** Time is up: done, extend, or stop and keep what is left. */
@Composable
fun KeepRestDialog(t: Task, close: () -> Unit, done: (Int) -> Unit) {
    var left by remember { mutableStateOf("15") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("إيقاف «${t.baseTitle}» وحفظ المتبقي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Muted("الوقت اللي اشتغلته بينحفظ مرة وحدة، والباقي بيتنقل لوقت فاضي لاحقاً اليوم.")
                Field(left, "كم دقيقة باقي تقريباً؟", { left = it }, numeric = true)
            }
        },
        confirmButton = { TextButton(onClick = { digits(left).toIntOrNull()?.takeIf { it > 0 }?.let(done) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
    )
}

/** What is in a backup file, shown before replacing the data. */
@Composable
fun RestorePreview(text: String, close: () -> Unit, restore: () -> Unit) {
    val info = remember(text) {
        runCatching {
            val o = JSONObject(text)
            val at = o.optLong("createdAt")
            val tasks = o.optJSONArray("tasks")?.length() ?: 0
            val goals = o.optJSONArray("goals")?.length() ?: 0
            val rules = o.optJSONArray("rules")?.length() ?: 0
            val events = o.optJSONArray("events")?.length() ?: 0
            val prayers = o.optJSONObject("settings")?.has("prayers") == true
            buildString {
                append(if (at > 0) "تاريخ النسخة: ${dayLabel(Clock.dayOf(at))} الساعة ${fmt12(Clock.minuteOfDay(at))}" else "تاريخ النسخة: غير مسجَّل (نسخة قديمة)")
                append("\n$tasks مهمة · $goals هدف · $rules تكرار · $events حدث بالسجل")
                if (prayers) append("\nفيها مواقيت الصلاة وإعداداتها")
            }
        }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("استعادة البيانات؟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(info ?: "الملف مش نسخة احتياطية صالحة.", style = MaterialTheme.typography.bodyLarge)
                if (info != null) Muted("قبل الاستعادة بحفظ نسخة أمان من بياناتك الحالية، وبتقدر ترجعلها بزر «تراجع». المؤقتات المستعادة بتكون متوقفة.")
            }
        },
        confirmButton = { if (info != null) TextButton(onClick = { close(); restore() }) { Text("استعادة") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
    )
}
