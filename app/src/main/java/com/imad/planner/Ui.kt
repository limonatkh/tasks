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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Work
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
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

/** Kept for older call sites: the lantern orange of the main buttons. */
val Salmon = Color(0xFFFF8A3D)

/**
 * The «رحلة اليوم» palette. Bright where it matters (the header, the one main button, rewards), calm everywhere else.
 * Text contrast on each: white on [sky] 6.1:1, ink on [lantern] 6.6:1, ink on [star] 10.7:1, white on [violet] 7.5:1,
 * white on [mint] 5.3:1, ink on [container] 13.5:1.
 */
object Brand {
    val sky = Color(0xFF2F56D9)
    val skyLight = Color(0xFF4A6DE6)
    val skyArc = Color(0xFF5B78EC)
    val lantern = Color(0xFFFF8A3D)
    val lanternShade = Color(0xFFE86A24)
    val lanternCap = Color(0xFFFFB020)
    val lanternText = Color(0xFFB85A1E)
    val flame = Color(0xFFFFE08A)
    val star = Color(0xFFFFD25E)
    val violet = Color(0xFF5A3DB8)
    val violetLight = Color(0xFF7457D6)
    val mint = Color(0xFF137A53)
    val container = Color(0xFFEAF0FF)
    val bg = Color(0xFFF4F7FE)
    val ink = Color(0xFF1C2340)
    val muted = Color(0xFF5F6787)
    val cheek = Color(0xFFF4A0A0)
    val quran = Color(0xFF0F8C7E)
    val sport = Color(0xFFDB6A2C)
    val content = Color(0xFF5A3DB8)
    val reading = Color(0xFF2F56D9)
    val other = Color(0xFFE0A030)
    val darkHeader = Color(0xFF26389A)
}

/** Colours the user can give to tasks and groups (kept in the same order: saved choices are their index). */
val TagColors = listOf(
    Color(0xFFFF8A3D), Color(0xFF2FA36B), Color(0xFF2F56D9), Color(0xFFE0A030),
    Color(0xFF7457D6), Color(0xFF0F8C7E), Color(0xFFC0587A), Color(0xFF8A90A8),
)
val TagNames = listOf("برتقالي", "أخضر", "أزرق", "ذهبي", "بنفسجي", "فيروزي", "وردي", "رمادي")

fun tagColor(i: Int): Color? = TagColors.getOrNull(i)

/** Black-and-white look on or off, and the switch for it (kept on the phone). Less motion too. */
class ThemeChoice(val mono: Boolean, val set: (Boolean) -> Unit, val calm: Boolean = false, val setCalm: (Boolean) -> Unit = {})
val LocalThemeChoice = androidx.compose.runtime.staticCompositionLocalOf { ThemeChoice(false, {}) }

/** True when animations should be skipped: the phone's "remove animations" or the app's own "حركة أقل". */
@Composable
fun calmMotion(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val system = androidx.compose.runtime.remember {
        runCatching { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) == 0f
    }
    return system || LocalThemeChoice.current.calm
}

/** Colour of the big header band on top of each page. */
@Composable
fun headerColor(): Color = when {
    LocalThemeChoice.current.mono -> if (isSystemInDarkTheme()) Color(0xFF1A1A1A) else Color(0xFF000000)
    isSystemInDarkTheme() -> Brand.darkHeader
    else -> Brand.sky
}

/** The main action colour (one per screen) and the text on it. */
@Composable
fun ctaColor(): Color = if (LocalThemeChoice.current.mono) MaterialTheme.colorScheme.primary else Brand.lantern
@Composable
fun onCtaColor(): Color = if (LocalThemeChoice.current.mono) MaterialTheme.colorScheme.onPrimary else Brand.ink

/** Done is always this green, nothing else uses it. */
@Composable
fun doneColor(): Color = if (LocalThemeChoice.current.mono) MaterialTheme.colorScheme.onSurface
    else if (isSystemInDarkTheme()) Color(0xFF4FC48F) else Brand.mint

private fun monoColors(dark: Boolean) = if (dark) darkColorScheme(
    primary = Color(0xFFFFFFFF), onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF2A2A2A), onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFD0D0D0), onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF262626), onSecondaryContainer = Color(0xFFEEEEEE),
    tertiary = Color(0xFFBDBDBD), onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF222222), onTertiaryContainer = Color(0xFFEEEEEE),
    background = Color(0xFF000000), onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF111111), onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1C1C1C), onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceTint = Color(0xFF111111), outline = Color(0xFF5A5A5A), outlineVariant = Color(0xFF2C2C2C),
    surfaceBright = Color(0xFF1C1C1C), surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000), surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF111111), surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF242424),
    error = Color(0xFFFF8A80), onError = Color(0xFF000000),
) else lightColorScheme(
    primary = Color(0xFF000000), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDEDED), onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF3A3A3A), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFEFEF), onSecondaryContainer = Color(0xFF111111),
    tertiary = Color(0xFF555555), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF0F0F0), onTertiaryContainer = Color(0xFF111111),
    background = Color(0xFFFFFFFF), onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF2F2F2), onSurfaceVariant = Color(0xFF5E5E5E),
    surfaceTint = Color(0xFFFFFFFF), outline = Color(0xFFBDBDBD), outlineVariant = Color(0xFFE2E2E2),
    surfaceBright = Color(0xFFFFFFFF), surfaceDim = Color(0xFFEAEAEA),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF5F5F5), surfaceContainerHigh = Color(0xFFEEEEEE),
    surfaceContainerHighest = Color(0xFFE6E6E6),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
)

private fun journeyColors(dark: Boolean) = if (dark) darkColorScheme(
    primary = Color(0xFF9DB4FF), onPrimary = Color(0xFF0E1A4D),
    primaryContainer = Color(0xFF26389A), onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF6FD3A5), onSecondary = Color(0xFF00391F),
    secondaryContainer = Color(0xFF0F4D35), onSecondaryContainer = Color(0xFFC8F2DD),
    tertiary = Color(0xFFC2B2FF), onTertiary = Color(0xFF2A1A66),
    tertiaryContainer = Color(0xFF3D2A8A), onTertiaryContainer = Color(0xFFE8E0FF),
    background = Color(0xFF141833), onBackground = Color(0xFFECEFFA),
    surface = Color(0xFF1E2347), onSurface = Color(0xFFECEFFA),
    surfaceVariant = Color(0xFF2A3060), onSurfaceVariant = Color(0xFFA9B0CC),
    surfaceTint = Color(0xFF1E2347), outline = Color(0xFF5D6590), outlineVariant = Color(0xFF343B6E),
    surfaceBright = Color(0xFF2A3060), surfaceDim = Color(0xFF141833),
    surfaceContainerLowest = Color(0xFF10142B), surfaceContainerLow = Color(0xFF191E3F),
    surfaceContainer = Color(0xFF1E2347), surfaceContainerHigh = Color(0xFF262C55),
    surfaceContainerHighest = Color(0xFF2E3562),
    error = Color(0xFFFFB4A8), onError = Color(0xFF5C1209),
) else lightColorScheme(
    primary = Brand.sky, onPrimary = Color.White,
    primaryContainer = Brand.container, onPrimaryContainer = Brand.ink,
    secondary = Brand.mint, onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF3EA), onSecondaryContainer = Color(0xFF0B3D2A),
    tertiary = Brand.violet, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE7FF), onTertiaryContainer = Color(0xFF2A1A66),
    background = Brand.bg, onBackground = Brand.ink,
    surface = Color.White, onSurface = Brand.ink,
    surfaceVariant = Color(0xFFE3EAFB), onSurfaceVariant = Brand.muted,
    surfaceTint = Color.White, outline = Color(0xFFA7B0CF), outlineVariant = Color(0xFFDCE3F5),
    surfaceBright = Color.White, surfaceDim = Color(0xFFE1E8FA),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF9FBFF),
    surfaceContainer = Color(0xFFF1F5FE), surfaceContainerHigh = Color(0xFFE9EFFC),
    surfaceContainerHighest = Color(0xFFE1E8FA),
    error = Color(0xFFB3261E), onError = Color.White,
)

/** Rounded happy Arabic for titles and big numbers. */
val DisplayFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.baloo_bold, FontWeight.Bold),
    androidx.compose.ui.text.font.Font(R.font.baloo_extrabold, FontWeight.ExtraBold),
    androidx.compose.ui.text.font.Font(R.font.baloo_bold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(R.font.baloo_bold, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(R.font.baloo_bold, FontWeight.Normal),
)

/** Clear Arabic for everything people read. */
val BodyFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.plex_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(R.font.plex_regular, FontWeight.Light),
    androidx.compose.ui.text.font.Font(R.font.plex_semibold, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(R.font.plex_semibold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(R.font.plex_bold, FontWeight.Bold),
    androidx.compose.ui.text.font.Font(R.font.plex_bold, FontWeight.ExtraBold),
)

private fun journeyType(): Typography {
    val b = Typography()
    fun TextStyle.body() = copy(fontFamily = BodyFont)
    fun TextStyle.display() = copy(fontFamily = DisplayFont)
    return Typography(
        displayLarge = b.displayLarge.display(), displayMedium = b.displayMedium.display(), displaySmall = b.displaySmall.display(),
        headlineLarge = b.headlineLarge.display(), headlineMedium = b.headlineMedium.display(),
        headlineSmall = TextStyle(fontFamily = DisplayFont, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontFamily = DisplayFont, fontSize = 25.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontFamily = BodyFont, fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontFamily = BodyFont, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontFamily = BodyFont, fontSize = 17.sp, lineHeight = 27.sp),
        bodyMedium = TextStyle(fontFamily = BodyFont, fontSize = 15.sp, lineHeight = 24.sp),
        bodySmall = b.bodySmall.body(),
        labelLarge = TextStyle(fontFamily = BodyFont, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = b.labelMedium.body(), labelSmall = b.labelSmall.body(),
    )
}

@Composable
fun PlannerTheme(mono: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (mono) monoColors(dark) else journeyColors(dark),
        shapes = androidx.compose.material3.Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(26.dp),
            extraLarge = RoundedCornerShape(30.dp),
        ),
        typography = journeyType(),
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
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Button(
        onClick = onClick, modifier = modifier.heightIn(min = 52.dp).pressScale(press), enabled = enabled, interactionSource = press,
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = ctaColor(), contentColor = onCtaColor()),
    ) { Text(label, fontWeight = FontWeight.ExtraBold, fontFamily = DisplayFont, fontSize = 18.sp) }
}

@Composable
fun Secondary(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Button(
        onClick = onClick, modifier = modifier.heightIn(min = 48.dp).pressScale(press), enabled = enabled, interactionSource = press,
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) { Text(label, fontWeight = FontWeight.SemiBold) }
}

/** Pressed things sink a little (to 96%) and come back: 120 ms, a soft spring. Nothing when motion is calmed. */
@Composable
fun Modifier.pressScale(source: androidx.compose.foundation.interaction.InteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val calm = calmMotion()
    val k by androidx.compose.animation.core.animateFloatAsState(
        if (pressed && !calm) 0.96f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 900f), label = "press",
    )
    return this.graphicsLayer { scaleX = k; scaleY = k }
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

/**
 * A card that comes up from the bottom (260 ms, eases in), the page behind dims. Back or the × closes it.
 * Used for every sheet in the app, so they all open and close the same way.
 */
@Composable
fun Sheet(title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val calm = calmMotion()
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val shown = remember { androidx.compose.animation.core.MutableTransitionState(calm) }.apply { targetState = true }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = close)
                .statusBarsPadding(), contentAlignment = Alignment.BottomCenter) {
                val cap = maxHeight * 0.94f
                androidx.compose.animation.AnimatedVisibility(
                    visibleState = shown,
                    enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 2 } +
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)),
                ) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = cap)
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                            .navigationBarsPadding().imePadding(),
                    ) {
                        Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.width(44.dp).heightIn(min = 5.dp, max = 5.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp)))
                        }
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    }
}

// ---------------- Task icons ----------------

/** One-colour icons for the kinds of tasks in the plan. */
val TaskIcons: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>> = listOf(
    "quran" to Icons.Filled.MenuBook,
    "read" to Icons.Filled.AutoStories,
    "listen" to Icons.Filled.Headphones,
    "memorize" to Icons.Filled.Psychology,
    "gym" to Icons.Filled.FitnessCenter,
    "boxing" to Icons.Filled.SportsMma,
    "run" to Icons.Filled.DirectionsRun,
    "body" to Icons.Filled.SelfImprovement,
    "sport" to Icons.Filled.Sports,
    "code" to Icons.Filled.Code,
    "game" to Icons.Filled.SportsEsports,
    "video" to Icons.Filled.Videocam,
    "edit" to Icons.Filled.Movie,
    "mic" to Icons.Filled.Mic,
    "publish" to Icons.Filled.Upload,
    "write" to Icons.Filled.Edit,
    "search" to Icons.Filled.Search,
    "idea" to Icons.Filled.Lightbulb,
    "shop" to Icons.Filled.ShoppingCart,
    "learn" to Icons.Filled.School,
    "work" to Icons.Filled.Work,
    "design" to Icons.Filled.Brush,
    "photo" to Icons.Filled.CameraAlt,
    "sleep" to Icons.Filled.Bedtime,
)

fun iconOf(key: String?): androidx.compose.ui.graphics.vector.ImageVector? =
    key?.takeIf { it.startsWith("i:") }?.removePrefix("i:")?.let { k -> TaskIcons.firstOrNull { it.first == k }?.second }

/** The task's icon or short text, in its colour. Nothing when none is set. */
@Composable
fun TaskBadge(look: String?, color: Color?, size: Dp = 26.dp) {
    val c = color ?: MaterialTheme.colorScheme.primary
    val icon = iconOf(look)
    when {
        look != null && look.startsWith("s:") -> SportIcon(look.removePrefix("s:"), c, size)
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
    Text("رياضة", style = MaterialTheme.typography.labelLarge)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SportIcons.all.forEach { (k, _) ->
            val on = selected == "s:$k"
            Box(Modifier.size(40.dp).background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).clickable { pick("s:$k") },
                contentAlignment = Alignment.Center) { SportIcon(k, color ?: MaterialTheme.colorScheme.onSurface, 28.dp) }
        }
    }
    Field(abbr, "أو اختصار (حرفين أو ثلاثة)", { v -> abbr = v.take(4); if (abbr.isNotBlank()) pick("t:" + abbr.trim()) })
}
