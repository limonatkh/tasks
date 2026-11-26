@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.imad.planner

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/** Steps of a goal, each with its own length and optional details. */
@Composable
fun StagesEditor(stages: List<Stage>, defaultMinutes: Int, change: (List<Stage>) -> Unit) {
    Text("المراحل بالترتيب (بتيجي بالدور وبتتكرر)", style = MaterialTheme.typography.labelLarge)
    stages.forEachIndexed { i, st ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(st.name, "المرحلة ${i + 1}", { v -> change(stages.toMutableList().also { it[i] = st.copy(name = v) }) }, modifier = Modifier.weight(2f))
                Field(if (st.minutes > 0) st.minutes.toString() else "", "دقائق", { v ->
                    change(stages.toMutableList().also { it[i] = st.copy(minutes = digits(v).toIntOrNull()?.coerceIn(0, 900) ?: 0) })
                }, numeric = true, modifier = Modifier.weight(1f))
            }
            if (st.details.isNotBlank()) Muted(st.details)
            Row {
                if (i > 0) TextButton(onClick = { change(stages.toMutableList().also { val x = it.removeAt(i); it.add(i - 1, x) }) }) { Text("لفوق") }
                TextButton(onClick = { change(stages.toMutableList().also { it.removeAt(i) }) }) { Text("حذف") }
            }
        }
    }
    Secondary("+ مرحلة", { change(stages + Stage("", defaultMinutes)) }, Modifier.fillMaxWidth())
    if (stages.isNotEmpty()) Muted("الدورة الكاملة: ${durLabel(stages.sumOf { if (it.minutes > 0) it.minutes else defaultMinutes })}")
}

/** Uses the user's ChatGPT subscription without an API: copy a request, paste the answer back. */
@Composable
fun ChatGptImport(goal: String, days: Int, onSteps: (List<Stage>) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var pasted by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { Text(if (open) "إخفاء شات جيبيتي" else "خلّي شات جيبيتي يقسّمه") }
    if (!open) return
    Muted("١) اضغط «انسخ الطلب» والصقه بشات جيبيتي.  ٢) انسخ جوابه كامل والصقه هون.  ٣) راجع الخطوات وعدّل قبل الحفظ.")
    Action("انسخ الطلب", {
        val prompt = buildString {
            appendLine("بدي أنجز هذا الهدف: ${goal.ifBlank { "(اكتب هدفك)" }}")
            if (days > 0) appendLine("المدة: $days يوم.")
            appendLine("قسّمه لخطوات عملية مرتبة، كل خطوة جلسة عمل وحدة بيوم.")
            appendLine("اكتب كل خطوة بسطر لحالها بهذا الشكل بالضبط، وبدون أي كلام ثاني:")
            append("اسم الخطوة | المدة بالدقائق | تفاصيل قصيرة شو لازم أعمل فيها")
        }
        clipboard.setText(AnnotatedString(prompt))
        Toast.makeText(context, "انسخ الطلب. الصقه بشات جيبيتي", Toast.LENGTH_SHORT).show()
    }, Modifier.fillMaxWidth())
    OutlinedTextField(value = pasted, onValueChange = { pasted = it }, label = { Text("الصق جواب شات جيبيتي هون") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
    val parsed = Stages.fromText(pasted)
    if (parsed.isNotEmpty()) {
        Muted("لقيت ${parsed.size} خطوات · ${durLabel(parsed.sumOf { it.minutes })}")
        Secondary("استعمل هاي الخطوات", { onSteps(parsed); pasted = ""; open = false }, Modifier.fillMaxWidth())
    }
}

/** Kinds of videos for a channel and this month's titles. */
@Composable
fun ContentEditor(types: List<ContentType>, change: (List<ContentType>) -> Unit) {
    Text("أصناف المحتوى", style = MaterialTheme.typography.labelLarge)
    val totalSessions = types.sumOf { it.perMonth * it.sessions }.coerceAtLeast(1)
    types.forEachIndexed { i, t ->
        Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(t.name, "الصنف", { v -> change(types.toMutableList().also { it[i] = t.copy(name = v) }) }, modifier = Modifier.weight(1.4f))
                Field(t.perMonth.toString(), "بالشهر", { v -> change(types.toMutableList().also { it[i] = t.copy(perMonth = digits(v).toIntOrNull()?.coerceIn(0, 60) ?: 0) }) }, numeric = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(t.sessions.toString(), "جلسات لكل فيديو", { v -> change(types.toMutableList().also { it[i] = t.copy(sessions = digits(v).toIntOrNull()?.coerceIn(1, 40) ?: 1) }) }, numeric = true, modifier = Modifier.weight(1f))
                Field(t.minutes.toString(), "دقائق الجلسة", { v -> change(types.toMutableList().also { it[i] = t.copy(minutes = digits(v).toIntOrNull()?.coerceIn(5, 600) ?: 60) }) }, numeric = true, modifier = Modifier.weight(1f))
            }
            Muted("${t.perMonth * t.sessions * 100 / totalSessions}٪ من وقت القناة هالشهر")
            if (t.perMonth > 0) {
                Text("عناوين هالشهر", style = MaterialTheme.typography.labelLarge)
                (0 until t.perMonth).forEach { k ->
                    Field(t.titles.getOrNull(k) ?: "", "${t.name} ${k + 1}", { v ->
                        val titles = (0 until t.perMonth).map { j -> if (j == k) v else t.titles.getOrNull(j) ?: "" }
                        change(types.toMutableList().also { it[i] = t.copy(titles = titles) })
                    })
                }
            }
            TextButton(onClick = { change(types.toMutableList().also { it.removeAt(i) }) }) { Text("حذف الصنف") }
        }
    }
    Secondary("+ صنف", { change(types + ContentType("", 1, 1, 60)) }, Modifier.fillMaxWidth())
    HorizontalDivider()
    Muted("جلسات كل فيديو بتتوزع على أيام الشهر قبل نشره، والفيديوهات الطويلة بتمشي جنب القصيرة.")
}
