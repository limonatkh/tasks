package com.imad.planner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** Arabic-Indic digits for the big numbers. */
fun ar(n: Number): String = n.toString().map { c -> if (c in '0'..'9') '٠' + (c - '0') else c }.joinToString("")

@Composable
private fun bandColor(): Color = if (LocalThemeChoice.current.mono) MaterialTheme.colorScheme.surfaceContainerHighest else Brand.violet

/** Star and level on the Today header; tapping it opens the goals page. */
@Composable
fun LevelChip(level: Progress.Level, open: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = open)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics { contentDescription = "مستوى ${level.level}، ${level.name}. باقي ${level.left} نقطة" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("★", color = Brand.star, fontSize = 18.sp)
        Text(ar(level.level), color = Color.White, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}

/** A thin bar for progress: violet track, star-yellow fill on the band, violet fill elsewhere. */
@Composable
fun Bar(fraction: Float, onBand: Boolean, modifier: Modifier = Modifier) {
    val mono = LocalThemeChoice.current.mono
    val track = if (onBand) Color(0xFF4A2F9E) else MaterialTheme.colorScheme.surfaceVariant
    val fill = when { mono -> MaterialTheme.colorScheme.onSurface; onBand -> Brand.star; else -> Brand.violet }
    Box(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(fill))
    }
}

/** Top of the goals page: «نور», the level and its name, how far to the next, the kept weeks. Opens the details. */
@Composable
fun ProgressBand(sum: Progress.Summary, open: () -> Unit) {
    val onBand = if (LocalThemeChoice.current.mono) MaterialTheme.colorScheme.onSurface else Color.White
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(bandColor()).clickable(onClick = open).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Nour(NourMood.Happy, size = 84.dp, label = "نور")
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("مستواك العام", fontSize = 13.sp, color = onBand.copy(alpha = 0.8f))
            Text("مستوى ${ar(sum.total.level)} · ${sum.total.name}", fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp, color = onBand)
            Bar(sum.total.fraction, onBand = true)
            Text("باقي ${ar(sum.total.left)} نقطة للمستوى ${ar(sum.total.level + 1)}" + if (sum.committedWeeks > 0) " · ${ar(sum.committedWeeks)} أسابيع ملتزمة" else "",
                fontSize = 12.sp, color = onBand.copy(alpha = 0.85f))
        }
        Text("←", color = onBand, fontSize = 22.sp)
    }
}

/** Levels per domain, side by side. */
@Composable
fun DomainLevels(sum: Progress.Summary) {
    if (sum.domains.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sum.domains.forEach { (d, l) ->
            Column(
                Modifier.width(104.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DomainTile(d, 42.dp)
                Text(d.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("مستوى ${ar(l.level)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (LocalThemeChoice.current.mono) MaterialTheme.colorScheme.onSurface else d.color)
                Bar(l.fraction, onBand = false)
            }
        }
    }
}

/** One goal as a road with four stations: passed ones green with a tick, the next one golden, the flag at the end. */
@Composable
fun GoalRoad(p: Progress.GoalPath) {
    val mono = LocalThemeChoice.current.mono
    val done = doneColor()
    val trackC = MaterialTheme.colorScheme.surfaceVariant
    val lived = if (mono) MaterialTheme.colorScheme.onSurface else Brand.violet
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(p.name, Modifier.weight(1f), fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text("${num(p.now)} من ${num(p.target)} ${p.unit}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(64.dp).semantics { contentDescription = "قطعت ${(p.fraction * 100).toInt()}٪ من الطريق" }) {
            val w = size.width; val h = size.height
            // From the right (the start) to the left (the flag), a gentle wave.
            fun at(t: Float) = Offset(w - 16.dp.toPx() - t * (w - 48.dp.toPx()), h * 0.62f - kotlin.math.sin(t * Math.PI * 2).toFloat() * h * 0.2f)
            val road = Path().apply { val a = at(0f); moveTo(a.x, a.y); for (i in 1..40) { val b = at(i / 40f); lineTo(b.x, b.y) } }
            drawPath(road, trackC, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
            if (p.fraction > 0f) {
                val lp = Path().apply { val a = at(0f); moveTo(a.x, a.y); for (i in 1..40) { val b = at(i / 40f * p.fraction * 0.94f); lineTo(b.x, b.y) } }
                drawPath(lp, lived, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
            }
            for (q in 1..4) {
                val c = at(q / 4f * 0.94f)
                val passed = p.fraction >= q / 4f - 1e-6f
                val next = !passed && p.fraction >= (q - 1) / 4f - 1e-6f
                val r = if (next) 12.dp.toPx() else 10.dp.toPx()
                drawCircle(when { passed -> done; next -> Brand.star; else -> Color.White }, r, c)
                drawCircle(Brand.ink, r, c, style = Stroke(2.5.dp.toPx()))
                if (passed) drawPath(Path().apply { moveTo(c.x - 5.dp.toPx(), c.y); lineTo(c.x - 1.dp.toPx(), c.y + 4.dp.toPx()); lineTo(c.x + 6.dp.toPx(), c.y - 4.dp.toPx()) },
                    Color.White, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            val f = at(1f)
            drawLine(Brand.ink, Offset(f.x, f.y + 10.dp.toPx()), Offset(f.x, f.y - 22.dp.toPx()), 2.5.dp.toPx(), StrokeCap.Round)
            val flag = Path().apply { moveTo(f.x, f.y - 22.dp.toPx()); lineTo(f.x + 16.dp.toPx(), f.y - 17.dp.toPx()); lineTo(f.x, f.y - 12.dp.toPx()); close() }
            drawPath(flag, if (mono) Color.Gray else Brand.lantern)
            drawPath(flag, Brand.ink, style = Stroke(2.dp.toPx()))
        }
        val qName = listOf("الربع الأول", "النص", "ثلاث أرباع", "خط النهاية")
        val nextQ = (1..4).firstOrNull { p.fraction < it / 4f - 1e-6f }
        Text(if (nextQ == null) "وصلت الهدف ✓" else "المحطة الجاية: ${qName[nextQ - 1]}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BadgeTile(b: Progress.Badge, modifier: Modifier = Modifier) {
    val c = b.domain?.color ?: Brand.sky
    Row(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(if (b.earned) (if (LocalThemeChoice.current.mono) MaterialTheme.colorScheme.surface else Color(0xFFFFF6D8).copy(alpha = if (androidx.compose.foundation.isSystemInDarkTheme()) 0.16f else 1f)) else MaterialTheme.colorScheme.surface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Medal(c, b.earned, if (b.key.startsWith("r:")) "↑" else if (b.key.startsWith("w:")) "✓" else "★", 44.dp)
        Column(Modifier.weight(1f)) {
            Text(b.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, color = if (b.earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            val line = if (b.earned) listOfNotNull(b.line.takeIf { it.isNotBlank() }, b.day?.let { shortDate(it) }).joinToString(" · ") else b.left.orEmpty()
            if (line.isNotBlank()) Text(line, fontSize = 12.sp, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun shortDate(day: String) = runCatching { java.time.LocalDate.parse(day).format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale("ar"))) }.getOrDefault(day)

/** Items for the top of the goals page. */
fun LazyListScope.progressItems(sum: Progress.Summary, openAll: () -> Unit) {
    item(key = "xp-band") { ProgressBand(sum, openAll) }
    item(key = "xp-domains") { DomainLevels(sum) }
    val got = Progress.earned(sum).take(2)
    val next = Progress.next(sum).take(2)
    if (got.isNotEmpty() || next.isNotEmpty()) item(key = "xp-badges") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("إنجازات حقيقية", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = openAll) { Text("كلها") }
            }
            (got + next).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { BadgeTile(it, Modifier.weight(1f)) }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Everything: levels, the roads of the goals, every achievement got and the ones coming. */
@Composable
fun ProgressSheet(sum: Progress.Summary, close: () -> Unit) {
    Sheet("تقدّمك", close) {
        ProgressBand(sum) {}
        Muted("النقاط من سجلّك بس: ١٠ لكل مهمة منجزة، ونقطة لكل دقيقة حقيقية (لحد مرة ونص من تقديرها)، و٥٠ لكل ربع هدف، و٣٠ لكل رقم جديد.")
        DomainLevels(sum)
        if (sum.goalPaths.isNotEmpty()) {
            Text("طريق أهدافك", style = MaterialTheme.typography.titleMedium)
            sum.goalPaths.sortedByDescending { it.fraction }.forEach { GoalRoad(it) }
        }
        val got = Progress.earned(sum)
        Text("اللي وصلته · ${ar(got.size)}", style = MaterialTheme.typography.titleMedium)
        if (got.isEmpty()) NourNote(NourMood.Awake, "أول إنجاز قريب", "خلّص أول مهمة وبتطلع هون.", 90.dp)
        got.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { BadgeTile(it, Modifier.weight(1f)) }; if (row.size == 1) Box(Modifier.weight(1f)) } }
        val next = Progress.next(sum)
        if (next.isNotEmpty()) {
            Text("الجاي", style = MaterialTheme.typography.titleMedium)
            next.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { BadgeTile(it, Modifier.weight(1f)) }; if (row.size == 1) Box(Modifier.weight(1f)) } }
        }
    }
}

/** Once per new level: «نور» cheers, says what got you here. Can be closed right away. */
@Composable
fun LevelUpDialog(sum: Progress.Summary, close: () -> Unit) {
    var hop by remember { mutableStateOf(1) }
    Dialog(onDismissRequest = close) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(bandColor()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Nour(NourMood.Cheer, size = 150.dp, cheer = hop, label = "نور فرحان")
            val on = if (LocalThemeChoice.current.mono) MaterialTheme.colorScheme.onSurface else Color.White
            Text("مستوى ${ar(sum.total.level)}", fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, color = on)
            Text("صرت «${sum.total.name}»", fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Brand.star)
            val top = sum.domains.firstOrNull()
            Text(
                "${ar(sum.doneCount)} مهمة منجزة" + (top?.let { " · أكثر إشي ${it.first.title}" } ?: "") + if (sum.committedWeeks > 0) " · ${ar(sum.committedWeeks)} أسابيع ملتزمة" else "",
                color = on.copy(alpha = 0.9f), textAlign = TextAlign.Center, fontSize = 15.sp,
            )
            Box(Modifier.size(4.dp))
            Action("يلا نكمّل", { hop++; close() }, Modifier.fillMaxWidth())
        }
    }
}
