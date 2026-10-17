@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.imad.planner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// ---------------- Theme ----------------

/** Main accent: a clear warm terracotta, used for buttons and the timer. */
val Salmon = Color(0xFFD9774F)

/** Colours the user can give to tasks and groups. */
val TagColors = listOf(
    Color(0xFFD9774F), Color(0xFF6E9E7A), Color(0xFF5E8FB8), Color(0xFFD4A23A),
    Color(0xFF9A78B0), Color(0xFF3F9E9A), Color(0xFFC46F86), Color(0xFF8A8F98),
)
val TagNames = listOf("برتقالي", "أخضر", "أزرق", "ذهبي", "بنفسجي", "فيروزي", "وردي", "رمادي")

fun tagColor(i: Int): Color? = TagColors.getOrNull(i)

@Composable
fun PlannerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // Clear but calm: warm greys, readable text, one terracotta accent, sage and blue as helpers.
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFFE8916B), onPrimary = Color(0xFF2B1308),
        primaryContainer = Color(0xFF4A2E22), onPrimaryContainer = Color(0xFFFFDCCD),
        secondary = Color(0xFF9FC2A7), onSecondary = Color(0xFF142219),
        secondaryContainer = Color(0xFF2C3A30), onSecondaryContainer = Color(0xFFD6EADB),
        tertiary = Color(0xFF8FB8D6), onTertiary = Color(0xFF0E2433),
        tertiaryContainer = Color(0xFF263845), onTertiaryContainer = Color(0xFFD3E6F3),
        background = Color(0xFF1C1B19), onBackground = Color(0xFFEDE9E3),
        surface = Color(0xFF282725), onSurface = Color(0xFFEDE9E3),
        surfaceVariant = Color(0xFF34322F), onSurfaceVariant = Color(0xFFB3ADA3),
        surfaceTint = Color(0xFF282725), outline = Color(0xFF6B665E), outlineVariant = Color(0xFF3F3C38),
        surfaceBright = Color(0xFF34322F), surfaceDim = Color(0xFF1C1B19),
        surfaceContainerLowest = Color(0xFF151413), surfaceContainerLow = Color(0xFF211F1D),
        surfaceContainer = Color(0xFF242321), surfaceContainerHigh = Color(0xFF302E2B),
        surfaceContainerHighest = Color(0xFF3A3834),
        error = Color(0xFFFF9C8A), onError = Color(0xFF3A0E07),
    ) else lightColorScheme(
        primary = Color(0xFFB8572F), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFBE0D3), onPrimaryContainer = Color(0xFF4A1C08),
        secondary = Color(0xFF4F7A5B), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDDEBE0), onSecondaryContainer = Color(0xFF173222),
        tertiary = Color(0xFF3F6F92), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFDAE8F3), onTertiaryContainer = Color(0xFF0D2B40),
        background = Color(0xFFF4F1EC), onBackground = Color(0xFF26231F),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF26231F),
        surfaceVariant = Color(0xFFE9E4DC), onSurfaceVariant = Color(0xFF6B655B),
        surfaceTint = Color(0xFFFFFFFF), outline = Color(0xFFB9B2A6), outlineVariant = Color(0xFFDDD7CD),
        surfaceBright = Color(0xFFFFFFFF), surfaceDim = Color(0xFFE6E1D9),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF9F7F3),
        surfaceContainer = Color(0xFFF4F1EC), surfaceContainerHigh = Color(0xFFEDE8E1),
        surfaceContainerHighest = Color(0xFFE5DFD6),
        error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
    )
    MaterialTheme(
        colorScheme = colors,
        shapes = androidx.compose.material3.Shapes(
            small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(24.dp),
        ),
        typography = Typography(
            titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
            bodyLarge = TextStyle(fontSize = 17.sp),
            bodyMedium = TextStyle(fontSize = 16.sp),
            labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}

// ---------------- Formatting ----------------

/** 12-hour time with ص / م. */
fun fmt12(minuteOfDay: Int): String {
    val h = (minuteOfDay / 60).mod(24)
    val m = minuteOfDay.mod(60)
    val hh = if (h % 12 == 0) 12 else h % 12
    return String.format(Locale.US, "%d:%02d %s", hh, m, if (h < 12) "ص" else "م")
}

fun timeLabel(millis: Long): String = if (millis == 0L) "بدون موعد" else fmt12(Clock.minuteOfDay(millis))

fun durLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m د"
        m == 0 -> "$h س"
        else -> "$h س $m د"
    }
}

fun timerLabel(millis: Long): String {
    val sec = millis.coerceAtLeast(0) / 1000
    return String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60)
}

/** Accepts Arabic-Indic digits and the Arabic decimal separator in number fields. */
fun digits(s: String): String = s.map { c ->
    when {
        Character.isDigit(c) -> ('0' + Character.getNumericValue(c))
        c == '٫' || c == ',' -> '.'
        else -> c
    }
}.joinToString("")

fun num(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else String.format(Locale.US, "%.1f", d)

fun dayLabel(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEEE، d MMMM", Locale("ar")))

fun monthLabel(ym: YearMonth): String = ym.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("ar")))

val weekdayNames = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")

/** Short names, Saturday first. */
fun shortDay(d: LocalDate): String = when (d.dayOfWeek.value) {
    6 -> "سبت"; 7 -> "أحد"; 1 -> "إثن"; 2 -> "ثلا"; 3 -> "أرب"; 4 -> "خمي"; else -> "جمع"
}

// ---------------- Clock icons ----------------

/** Plain clock (like the app icon) whose hands show the start time. */
@Composable
fun ClockIcon(minuteOfDay: Int?, size: Dp = 22.dp, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        val sw = r * 0.16f
        drawCircle(color, radius = r - sw / 2, style = Stroke(sw))
        if (minuteOfDay != null) {
            val h = (minuteOfDay / 60) % 12
            val m = minuteOfDay % 60
            val ha = Math.toRadians((h + m / 60.0) * 30 - 90)
            val ma = Math.toRadians(m * 6.0 - 90)
            drawLine(color, center, Offset(center.x + (r * 0.48f * cos(ha)).toFloat(), center.y + (r * 0.48f * sin(ha)).toFloat()), sw, StrokeCap.Round)
            drawLine(color, center, Offset(center.x + (r * 0.72f * cos(ma)).toFloat(), center.y + (r * 0.72f * sin(ma)).toFloat()), sw * 0.8f, StrokeCap.Round)
        }
    }
}

/** One circle per hour, filled as much as the duration: half a circle = half an hour. */
@Composable
fun DurationIcon(minutes: Int, size: Dp = 18.dp, color: Color = MaterialTheme.colorScheme.tertiary) {
    val full = (minutes / 60).coerceAtMost(4)
    val rem = minutes % 60
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(full) { Pie(1f, size, color) }
        if (rem > 0 || minutes == 0) Pie(rem / 60f, size, color)
    }
}

@Composable
private fun Pie(fraction: Float, size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        val sw = r * 0.18f
        drawCircle(color.copy(alpha = 0.4f), radius = r - sw / 2, style = Stroke(sw))
        if (fraction > 0f) {
            drawArc(
                color, startAngle = -90f, sweepAngle = 360f * fraction.coerceIn(0f, 1f), useCenter = true,
                topLeft = Offset(sw, sw), size = Size(this.size.width - 2 * sw, this.size.height - 2 * sw),
            )
        }
    }
}

/** Start time (clock) and duration (filled circles) side by side. */
@Composable
fun TimeLine(planned: Long, minutes: Int, end: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ClockIcon(if (planned > 0) Clock.minuteOfDay(planned) else null, 20.dp)
        Text(
            if (planned > 0 && end) "${timeLabel(planned)} – ${timeLabel(planned + minutes * 60_000L)}" else timeLabel(planned),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.width(4.dp))
        DurationIcon(minutes, 16.dp)
        Text(durLabel(minutes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------- Basic components ----------------

@Composable
fun Action(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick, modifier = modifier.heightIn(min = 52.dp), enabled = enabled,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
fun Secondary(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) { Text(label) }
}

/** Clock-face timer: a ring shows the time left, the hand shows where you are. */
@Composable
fun AnalogTimer(spentMs: Long, totalMs: Long, size: Dp = 230.dp) {
    val left = (1f - spentMs.toFloat() / totalMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val remainingMs = ((1f - (1f - left)) * totalMs).toLong()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2
            val w = r * 0.14f
            val inset = w / 2 + r * 0.02f
            val arcSize = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            if (left > 0f) drawArc(accent, -90f, 360f * left, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0 - 90)
                val o = Offset(center.x + (r * 0.74f * cos(a)).toFloat(), center.y + (r * 0.74f * sin(a)).toFloat())
                val n = Offset(center.x + (r * 0.68f * cos(a)).toFloat(), center.y + (r * 0.68f * sin(a)).toFloat())
                drawLine(tick.copy(alpha = 0.6f), n, o, r * 0.02f, StrokeCap.Round)
            }
            val ha = Math.toRadians(360.0 * (1 - left) - 90)
            drawLine(tick, center, Offset(center.x + (r * 0.6f * cos(ha)).toFloat(), center.y + (r * 0.6f * sin(ha)).toFloat()), r * 0.03f, StrokeCap.Round)
            drawCircle(accent, radius = r * 0.045f)
        }
        Text(timerLabel(remainingMs), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = (size.value * 0.35f).dp))
    }
}

/** Pick one of the task colours (or none). */
@Composable
fun ColorPicker(selected: Int, pick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(15.dp)).clickable { pick(-1) }, contentAlignment = Alignment.Center) {
            if (selected < 0) Text("✓", fontSize = 14.sp)
        }
        TagColors.forEachIndexed { i, c ->
            Box(Modifier.size(30.dp).background(c, RoundedCornerShape(15.dp)).clickable { pick(i) }, contentAlignment = Alignment.Center) {
                if (selected == i) Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Bottom tabs like the clock app: words, the open one underlined. */
@Composable
fun TextTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(top = 6.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Column(
                Modifier.clickable { onSelect(i) }.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, fontSize = 19.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.padding(top = 3.dp).width(if (on) 44.dp else 0.dp).heightIn(min = 3.dp, max = 3.dp)
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(2.dp)))
            }
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.Start, content = content)
    }
}

@Composable
fun Field(value: String, label: String, change: (String) -> Unit, numeric: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value, onValueChange = change, label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
    )
}

@Composable
fun Muted(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun SwitchRow(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { change(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = change)
    }
}

/** "مقترح" for a default nobody looked at yet, "راجعته" after review. */
@Composable
fun ReviewBadge(reviewed: Boolean) {
    val bg = if (reviewed) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (reviewed) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Text(
        if (reviewed) "✓ راجعته" else "مقترح",
        Modifier.background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
        color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium,
    )
}

/** Tap to fold / unfold a category. */
@Composable
fun CategoryHeader(title: String, count: Int, expanded: Boolean, extra: String? = null, toggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .clickable(onClick = toggle).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (extra != null) { Text(extra, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary); Box(Modifier.width(8.dp)) }
        Text("$count", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (expanded) "طي" else "فتح")
    }
}

@Composable
fun TimeDialog(initial: Int, close: () -> Unit, pick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = (initial / 60) % 24, initialMinute = initial % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = close,
        confirmButton = { TextButton(onClick = { pick(state.hour * 60 + state.minute) }) { Text("حسناً") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
        text = { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { TimePicker(state = state) } },
    )
}

@Composable
fun Sheet(title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = close) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") }
                    }
                    content()
                }
            }
        }
    }
}

// ---------------- Task icons ----------------

/** One-colour icons for the kinds of tasks in the plan. */
val TaskIcons: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>> = listOf(
    "quran" to androidx.compose.material.icons.Icons.Filled.MenuBook,
    "read" to androidx.compose.material.icons.Icons.Filled.AutoStories,
    "listen" to androidx.compose.material.icons.Icons.Filled.Headphones,
    "memorize" to androidx.compose.material.icons.Icons.Filled.Psychology,
    "gym" to androidx.compose.material.icons.Icons.Filled.FitnessCenter,
    "boxing" to androidx.compose.material.icons.Icons.Filled.SportsMma,
    "run" to androidx.compose.material.icons.Icons.Filled.DirectionsRun,
    "body" to androidx.compose.material.icons.Icons.Filled.SelfImprovement,
    "sport" to androidx.compose.material.icons.Icons.Filled.Sports,
    "code" to androidx.compose.material.icons.Icons.Filled.Code,
    "game" to androidx.compose.material.icons.Icons.Filled.SportsEsports,
    "video" to androidx.compose.material.icons.Icons.Filled.Videocam,
    "edit" to androidx.compose.material.icons.Icons.Filled.Movie,
    "mic" to androidx.compose.material.icons.Icons.Filled.Mic,
    "publish" to androidx.compose.material.icons.Icons.Filled.Upload,
    "write" to androidx.compose.material.icons.Icons.Filled.Edit,
    "search" to androidx.compose.material.icons.Icons.Filled.Search,
    "idea" to androidx.compose.material.icons.Icons.Filled.Lightbulb,
    "shop" to androidx.compose.material.icons.Icons.Filled.ShoppingCart,
    "learn" to androidx.compose.material.icons.Icons.Filled.School,
    "work" to androidx.compose.material.icons.Icons.Filled.Work,
    "design" to androidx.compose.material.icons.Icons.Filled.Brush,
    "photo" to androidx.compose.material.icons.Icons.Filled.CameraAlt,
    "sleep" to androidx.compose.material.icons.Icons.Filled.Bedtime,
)

fun iconOf(key: String?): androidx.compose.ui.graphics.vector.ImageVector? =
    key?.takeIf { it.startsWith("i:") }?.removePrefix("i:")?.let { k -> TaskIcons.firstOrNull { it.first == k }?.second }

/** The task's icon or short text, in its colour. Nothing when none is set. */
@Composable
fun TaskBadge(look: String?, color: Color?, size: Dp = 26.dp) {
    val c = color ?: MaterialTheme.colorScheme.primary
    val icon = iconOf(look)
    when {
        icon != null -> androidx.compose.material3.Icon(icon, contentDescription = null, tint = c, modifier = Modifier.size(size))
        look != null && look.startsWith("t:") -> Text(look.removePrefix("t:"), color = c, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.62f).sp, maxLines = 1)
    }
}

/** Choose an icon from the list or type a short abbreviation. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun IconPicker(selected: String?, color: Color?, pick: (String) -> Unit) {
    var abbr by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(selected?.takeIf { it.startsWith("t:") }?.removePrefix("t:") ?: "") }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(40.dp).background(if (selected.isNullOrEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).clickable { pick("") },
            contentAlignment = Alignment.Center) { Text("—") }
        TaskIcons.forEach { (k, v) ->
            val on = selected == "i:$k"
            Box(Modifier.size(40.dp).background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).clickable { pick("i:$k") },
                contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(v, contentDescription = k, tint = color ?: MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
            }
        }
    }
    Field(abbr, "أو اختصار (حرفين أو ثلاثة)", { v -> abbr = v.take(4); if (abbr.isNotBlank()) pick("t:" + abbr.trim()) })
}
