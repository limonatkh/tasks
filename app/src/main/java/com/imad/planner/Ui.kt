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

@Composable
fun PlannerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // Calm blue-grey palette. Every surface role is set so no default tint (pink/purple) leaks in.
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF9CC3E4), onPrimary = Color(0xFF0D2A40),
        primaryContainer = Color(0xFF274861), onPrimaryContainer = Color(0xFFD3E6F6),
        secondary = Color(0xFFA9BCCB), onSecondary = Color(0xFF15232E),
        secondaryContainer = Color(0xFF2E4253), onSecondaryContainer = Color(0xFFD6E5F2),
        tertiary = Color(0xFF9FCFC3), onTertiary = Color(0xFF0E2D27),
        tertiaryContainer = Color(0xFF2B4A43), onTertiaryContainer = Color(0xFFCDEDE4),
        background = Color(0xFF121518), onBackground = Color(0xFFE2E6EA),
        surface = Color(0xFF1A1E22), onSurface = Color(0xFFE2E6EA),
        surfaceVariant = Color(0xFF2A3036), onSurfaceVariant = Color(0xFFB2BBC4),
        surfaceTint = Color(0xFF9CC3E4), outline = Color(0xFF6E7881), outlineVariant = Color(0xFF3A4249),
        surfaceBright = Color(0xFF2A2F34), surfaceDim = Color(0xFF121518),
        surfaceContainerLowest = Color(0xFF0E1114), surfaceContainerLow = Color(0xFF171B1F),
        surfaceContainer = Color(0xFF1B1F23), surfaceContainerHigh = Color(0xFF22272C),
        surfaceContainerHighest = Color(0xFF2A3036),
        error = Color(0xFFF2B8A8), onError = Color(0xFF4A1A0E),
    ) else lightColorScheme(
        primary = Color(0xFF2F628C), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD3E5F5), onPrimaryContainer = Color(0xFF0B2F4C),
        secondary = Color(0xFF52606D), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD9E6F1), onSecondaryContainer = Color(0xFF13273A),
        tertiary = Color(0xFF3F6E64), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD2EDE5), onTertiaryContainer = Color(0xFF0E2D27),
        background = Color(0xFFF3F5F7), onBackground = Color(0xFF1B1F23),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1B1F23),
        surfaceVariant = Color(0xFFE3E8ED), onSurfaceVariant = Color(0xFF515C66),
        surfaceTint = Color(0xFF2F628C), outline = Color(0xFF8A949D), outlineVariant = Color(0xFFCDD4DA),
        surfaceBright = Color(0xFFFFFFFF), surfaceDim = Color(0xFFE6EAEE),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F9FA),
        surfaceContainer = Color(0xFFEEF2F5), surfaceContainerHigh = Color(0xFFE8ECF0),
        surfaceContainerHighest = Color(0xFFE2E7EB),
        error = Color(0xFFB4462F), onError = Color(0xFFFFFFFF),
    )
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
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
        drawCircle(color.copy(alpha = 0.35f), radius = r - sw / 2, style = Stroke(sw))
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
    Button(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled) { Text(label) }
}

@Composable
fun Secondary(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled) { Text(label) }
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
