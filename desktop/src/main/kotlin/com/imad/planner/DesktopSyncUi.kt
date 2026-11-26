package com.imad.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the sync panel shows about the last attempt ("" when all is fine). */
object SyncUi { val problem = mutableStateOf("") }

/** The sync panel: pair with the phone by a code, then see when it last synced. */
@Composable
fun SyncPanel(sync: DesktopSync, close: () -> Unit) {
    val scope = rememberCoroutineScope()
    var owner by remember { mutableStateOf(sync.owner) }
    var code by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }

    // While a code is shown: ask every 3 seconds whether the phone accepted it.
    LaunchedEffect(code) {
        if (code == null) return@LaunchedEffect
        while (owner == null) {
            delay(3_000)
            val ok = withContext(Dispatchers.IO) { runCatching { sync.checkPaired() }.getOrDefault(false) }
            if (ok) { owner = sync.owner; code = null; note = "انربط ✓ وصلت بيانات الجوال" }
        }
    }

    Sheet("التزامن مع الجوال", close) {
        when {
            !FirebaseConfig.ready -> Muted("التزامن مش جاهز بهالنسخة من البرنامج.")
            owner == null && code == null -> {
                Muted("اربط الكمبيوتر بالجوال مرة وحدة، وبعدها كل تعديل على جهاز بيوصل للتاني.")
                Muted("انتبه: عند الربط، اللي على الكمبيوتر بيتبدّل باللي على الجوال.")
                Action("ابدأ الربط", {
                    scope.launch {
                        note = "عم يجهّز رمز…"
                        runCatching { withContext(Dispatchers.IO) { sync.newCode() } }
                            .onSuccess { code = it; note = "" }
                            .onFailure { note = "ما زبط: ${it.message ?: it.javaClass.simpleName}" }
                    }
                }, Modifier.fillMaxWidth())
            }
            owner == null -> {
                Muted("بالجوال: الإعدادات ← التزامن مع الكمبيوتر ← اكتب هالرمز واضغط «ربط الكمبيوتر».")
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFEAF0FF)).padding(20.dp), contentAlignment = Alignment.Center) {
                    Text("⁦${code}⁩", fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, letterSpacing = 6.sp)
                }
                Muted("عم يستنّى الجوال…")
            }
            else -> {
                val last = sync.prefs.get("lastSync")?.toLongOrNull()
                Muted("مربوط بالجوال." + (last?.let { " آخر تزامن ${fmt12(Clock.minuteOfDay(it))}." } ?: ""))
                Muted("بيتزامن لحاله كل 20 ثانية والبرنامج مفتوح.")
                Action("مزامنة هلأ", {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { sync.syncNow() } }
                            .onSuccess { r -> note = r?.let { "انبعت ${it.sent} · وصل ${it.received}" } ?: "" }
                            .onFailure { note = "ما زبط: ${it.message ?: it.javaClass.simpleName}" }
                    }
                }, Modifier.fillMaxWidth())
                TextButton(onClick = { sync.unpair(); owner = null; note = "انفك الربط. البيانات ضلّت على الكمبيوتر." }) { Text("فك الربط") }
            }
        }
        if (note.isNotBlank()) Muted(note)
        val problem = SyncUi.problem.value
        if (problem.isNotBlank()) Text(problem, color = warmText(), fontSize = 14.sp)
    }
}
