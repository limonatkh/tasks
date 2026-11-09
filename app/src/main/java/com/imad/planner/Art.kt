package com.imad.planner

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/*
 * The app's drawings, all by one set of rules (see the design board):
 *  - ink outline (#1C2340) about 1/32 of the drawing's width, round joins and ends;
 *  - three tones per material: base, one darker side, a small white highlight; no gradients;
 *  - round, full shapes; ink oval eyes with a light dot; pink cheeks;
 *  - at most two colours besides ink in one drawing; each domain has its colour.
 * Drawn in a design box and scaled, so they stay sharp at any size.
 */

private val Ink = Brand.ink

/** Grey version of a colour for the black-and-white look. */
private fun Color.tone(mono: Boolean): Color {
    if (!mono) return this
    val l = 0.299f * red + 0.587f * green + 0.114f * blue
    return Color(l, l, l, alpha)
}

private fun rr(l: Float, t: Float, w: Float, h: Float, r: Float) = Path().apply { addRoundRect(RoundRect(l, t, l + w, t + h, CornerRadius(r))) }
private fun oval(cx: Float, cy: Float, rx: Float, ry: Float) = Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }
private fun path(block: Path.() -> Unit) = Path().apply(block)

private fun DrawScope.fill(p: Path, c: Color, ink: Color, w: Float) {
    drawPath(p, c)
    drawPath(p, ink, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
private fun DrawScope.ink(p: Path, ink: Color, w: Float) = drawPath(p, ink, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))

/** Four-point sparkle. */
private fun sparkle(cx: Float, cy: Float, r: Float) = path {
    moveTo(cx, cy - r)
    quadraticBezierTo(cx + r * 0.18f, cy - r * 0.18f, cx + r, cy)
    quadraticBezierTo(cx + r * 0.18f, cy + r * 0.18f, cx, cy + r)
    quadraticBezierTo(cx - r * 0.18f, cy + r * 0.18f, cx - r, cy)
    quadraticBezierTo(cx - r * 0.18f, cy - r * 0.18f, cx, cy - r)
    close()
}

/** Draws [block] in a [vw]×[vh] design box fitted and centred in the canvas. */
private fun DrawScope.art(vw: Float, vh: Float, block: DrawScope.() -> Unit) {
    val k = minOf(size.width / vw, size.height / vh)
    translate((size.width - vw * k) / 2, (size.height - vh * k) / 2) {
        scale(k, k, Offset.Zero) { block() }
    }
}

// ---------------- «نور» ----------------

enum class NourMood { Dim, Awake, Happy, Cheer, Sleep }

/**
 * The lantern, in a 200×200 box (body 160×180 from (20,10)).
 * [blink] 1 = eyes open, 0 = shut. [glow] 0..1 = how bright the halo is. [twinkle] 0..1 for the stars.
 */
private fun DrawScope.drawNour(mood: NourMood, blink: Float, glow: Float, twinkle: Float, mono: Boolean, zLift: Float = 0f) {
    val w = 6f
    val lit = mood != NourMood.Dim && mood != NourMood.Sleep
    translate(20f, 10f) {
        // Halo: the light it gives.
        if (lit) drawCircle(Brand.flame.tone(mono).copy(alpha = 0.18f + 0.32f * glow), 82f, Offset(80f, 96f))
        // Handle and cap.
        ink(path { moveTo(64f, 24f); quadraticBezierTo(80f, 2f, 96f, 24f) }, Ink, w)
        fill(path { moveTo(56f, 40f); lineTo(104f, 40f); lineTo(96f, 24f); lineTo(64f, 24f); close() }, Brand.lanternCap.tone(mono), Ink, w)
        // Body: base, the darker right side, outline.
        val body = rr(34f, 40f, 92f, 114f, 30f)
        drawPath(body, (if (mood == NourMood.Dim) Color(0xFFE59A6A) else Brand.lantern).tone(mono))
        clipPath(body) { drawRect(Brand.lanternShade.tone(mono).copy(alpha = if (mood == NourMood.Dim) 0.6f else 1f), Offset(104f, 40f), androidx.compose.ui.geometry.Size(30f, 120f)) }
        ink(body, Ink, w)
        // Highlight on the left shoulder.
        ink(path { moveTo(44f, 76f); quadraticBezierTo(44f, 54f, 60f, 48f) }, Color.White.copy(alpha = 0.9f), 5f)
        // The window: the flame's light.
        val window = when (mood) {
            NourMood.Dim -> Color(0xFFEBDDB4)
            NourMood.Sleep -> Color(0xFFF1C97A)
            else -> Brand.flame
        }.tone(mono)
        fill(rr(50f, 58f, 60f, 72f, 22f), window, Ink, w * 0.85f)
        if (lit) drawCircle(Color.White.copy(alpha = 0.35f + 0.3f * glow), 9f, Offset(80f, 118f))
        // Eyes.
        when (mood) {
            NourMood.Sleep -> {
                ink(path { moveTo(61f, 92f); quadraticBezierTo(68f, 98f, 75f, 92f) }, Ink, 4.5f)
                ink(path { moveTo(85f, 92f); quadraticBezierTo(92f, 98f, 99f, 92f) }, Ink, 4.5f)
            }
            NourMood.Cheer -> {
                ink(path { moveTo(61f, 95f); quadraticBezierTo(68f, 84f, 75f, 95f) }, Ink, 4.5f)
                ink(path { moveTo(85f, 95f); quadraticBezierTo(92f, 84f, 99f, 95f) }, Ink, 4.5f)
            }
            else -> {
                val ry = 8f * blink.coerceIn(0.08f, 1f)
                drawPath(oval(68f, 92f, 6f, ry), Ink)
                drawPath(oval(92f, 92f, 6f, ry), Ink)
                if (blink > 0.6f) {
                    drawCircle(Color.White, 2.2f, Offset(70f, 88.5f))
                    drawCircle(Color.White, 2.2f, Offset(94f, 88.5f))
                }
            }
        }
        // Cheeks and mouth.
        drawPath(oval(57f, 107f, 6f, 3.6f), Brand.cheek.tone(mono).copy(alpha = if (lit) 1f else 0.6f))
        drawPath(oval(103f, 107f, 6f, 3.6f), Brand.cheek.tone(mono).copy(alpha = if (lit) 1f else 0.6f))
        when (mood) {
            NourMood.Dim -> ink(path { moveTo(72f, 108f); lineTo(88f, 108f) }, Ink, 4.5f)
            NourMood.Sleep -> drawPath(oval(80f, 109f, 3.5f, 4f), Ink)
            NourMood.Cheer, NourMood.Happy -> fill(path { moveTo(68f, 104f); quadraticBezierTo(80f, 122f, 92f, 104f); close() }, Color(0xFFB8432E).tone(mono), Ink, 4.5f)
            NourMood.Awake -> ink(path { moveTo(70f, 105f); quadraticBezierTo(80f, 115f, 90f, 105f) }, Ink, 4.5f)
        }
        // Base.
        fill(rr(46f, 152f, 68f, 16f, 8f), Brand.lanternCap.tone(mono), Ink, w)
        // Stars when cheering; z's when asleep.
        if (mood == NourMood.Cheer) {
            val a = 0.75f + 0.25f * twinkle
            listOf(Triple(4f, 30f, 13f), Triple(150f, 18f, 10f), Triple(158f, 120f, 9f), Triple(-6f, 128f, 8f)).forEachIndexed { i, (x, y, r) ->
                val rr2 = r * (if (i % 2 == 0) a else 1.75f - a)
                fill(sparkle(x, y, rr2), Brand.star.tone(mono), Ink, 2.6f)
            }
        }
        if (mood == NourMood.Sleep) {
            val c = Brand.muted.copy(alpha = 0.9f - 0.5f * zLift)
            val y = -zLift * 14f
            ink(path { moveTo(126f, 26f + y); lineTo(140f, 26f + y); lineTo(126f, 40f + y); lineTo(140f, 40f + y) }, c, 3.5f)
            ink(path { moveTo(146f, 6f + y); lineTo(156f, 6f + y); lineTo(146f, 16f + y); lineTo(156f, 16f + y) }, c, 3f)
        }
    }
}

/**
 * «نور», alive: the light breathes (2.4 s), it blinks every few seconds (150 ms), and when [cheer] changes it hops
 * (160 ms up, a springy landing). With calm motion it stands still.
 */
@Composable
fun Nour(mood: NourMood, modifier: Modifier = Modifier, size: Dp = 96.dp, cheer: Int = 0, label: String? = null) {
    val calm = calmMotion()
    val mono = LocalThemeChoice.current.mono
    val inf = rememberInfiniteTransition(label = "nour")
    val glowA by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val blinkA by inf.animateFloat(1f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 4600
        1f at 0; 1f at 4300; 0.05f at 4380; 1f at 4500
    }), label = "blink")
    val twinkleA by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "twinkle")
    val zA by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing)), label = "z")
    val hop = remember { Animatable(0f) }
    LaunchedEffect(cheer) {
        if (cheer != 0 && !calm) {
            hop.animateTo(-16f, tween(160))
            hop.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
    }
    Canvas(modifier.size(size).graphicsLayer { translationY = hop.value * density }.let { m ->
        if (label != null) m.semantics { contentDescription = label } else m
    }) {
        art(200f, 200f) {
            drawNour(mood, if (calm) 1f else blinkA, if (calm) 0.6f else glowA, if (calm) 0.5f else twinkleA, mono, if (calm) 0f else zA)
        }
    }
}

// ---------------- Domains ----------------

/** The kinds of work the drawings know. Custom groups fall back to the task's icon, then to "other". */
enum class Domain(val color: Color, val pale: Color, val title: String) {
    Quran(Brand.quran, Color(0xFFD8F2EE), "القرآن"),
    Reading(Brand.reading, Color(0xFFDDE8FF), "القراءة"),
    Sport(Brand.sport, Color(0xFFFFE6D6), "الرياضة"),
    Content(Brand.content, Color(0xFFEDE7FF), "المحتوى"),
    Work(Color(0xFFB07A10), Color(0xFFFFF0C2), "الشغل"),
    Other(Color(0xFFC0587A), Color(0xFFF9E1EA), "أخرى"),
}

private val contentIcons = setOf("mic", "video", "edit", "publish", "photo", "design")

/** Which domain a task is in: its group first, then the icon chosen for it. */
fun domainOf(category: String, icon: String?): Domain {
    val k = icon?.substringAfter(':')
    return when {
        category == "quran" || k == "quran" || k == "memorize" -> Domain.Quran
        category == "sport" || icon?.startsWith("s:") == true || k in setOf("gym", "boxing", "run", "body", "sport") -> Domain.Sport
        k in contentIcons -> Domain.Content
        category == "reading" || k in setOf("read", "listen", "learn") -> Domain.Reading
        category == "work" || k in setOf("code", "work", "game", "write", "search", "idea") -> Domain.Work
        else -> Domain.Other
    }
}

fun domainOf(t: Task, s: Settings): Domain = domainOf(t.category, s.icons[seriesOf(t)])

/** The domain's drawing in a 40×40 box (no tile). */
private fun DrawScope.drawDomain(d: Domain, mono: Boolean) {
    val w = 2.4f
    when (d) {
        Domain.Quran -> {
            // Mushaf open on its stand, a soft light above.
            ink(path { moveTo(20f, 4f); lineTo(20f, 8f) }, Brand.star.tone(mono), 2.4f)
            ink(path { moveTo(11f, 7f); lineTo(13f, 10f) }, Brand.star.tone(mono), 2.4f)
            ink(path { moveTo(29f, 7f); lineTo(27f, 10f) }, Brand.star.tone(mono), 2.4f)
            fill(path { moveTo(8f, 33f); lineTo(20f, 26f); lineTo(32f, 33f); lineTo(32f, 36f); lineTo(20f, 30f); lineTo(8f, 36f); close() }, Color(0xFFB0753A).tone(mono), Ink, w)
            fill(path { moveTo(20f, 13f); cubicTo(15f, 10f, 10f, 10f, 7f, 11f); lineTo(7f, 29f); cubicTo(10f, 28f, 15f, 28f, 20f, 31f); close() }, Color.White, Ink, w)
            fill(path { moveTo(20f, 13f); cubicTo(25f, 10f, 30f, 10f, 33f, 11f); lineTo(33f, 29f); cubicTo(30f, 28f, 25f, 28f, 20f, 31f); close() }, Color(0xFF9FE0D6).tone(mono), Ink, w)
            ink(path { moveTo(10f, 17f); lineTo(16f, 18f); moveTo(10f, 22f); lineTo(16f, 23f) }, Brand.quran.tone(mono), 1.8f)
        }
        Domain.Reading -> {
            // A closed book standing, with a bookmark.
            fill(rr(9f, 7f, 20f, 27f, 4f), Brand.reading.tone(mono), Ink, w)
            clipPath(rr(9f, 7f, 20f, 27f, 4f)) { drawRect(Color(0xFF2343B0).tone(mono), Offset(24f, 7f), androidx.compose.ui.geometry.Size(6f, 27f)) }
            ink(rr(9f, 7f, 20f, 27f, 4f), Ink, w)
            fill(rr(27f, 9f, 5f, 24f, 2f), Color.White, Ink, w * 0.8f)
            fill(path { moveTo(14f, 7f); lineTo(14f, 18f); lineTo(17f, 15.5f); lineTo(20f, 18f); lineTo(20f, 7f) }, Brand.star.tone(mono), Ink, w * 0.8f)
            ink(path { moveTo(12.5f, 12f); quadraticBezierTo(12.5f, 9.5f, 15f, 9.5f) }, Color.White, 1.6f)
        }
        Domain.Sport -> {
            // Kettlebell.
            ink(path { moveTo(13f, 17f); cubicTo(13f, 6f, 27f, 6f, 27f, 17f) }, Ink, 5.6f)
            ink(path { moveTo(13f, 17f); cubicTo(13f, 6f, 27f, 6f, 27f, 17f) }, Color(0xFF8A90A8).tone(mono), 2.2f)
            val bell = oval(20f, 25f, 12f, 10.5f)
            drawPath(bell, Brand.sport.tone(mono))
            clipPath(bell) { drawRect(Color(0xFFB4501B).tone(mono), Offset(26f, 12f), androidx.compose.ui.geometry.Size(8f, 26f)) }
            ink(bell, Ink, w)
            ink(path { moveTo(13f, 22f); quadraticBezierTo(13.5f, 18f, 17f, 17f) }, Color.White, 1.8f)
            drawPath(rr(14f, 34f, 12f, 3f, 1.5f), Ink)
        }
        Domain.Content -> {
            // Microphone on a stand.
            fill(rr(14.5f, 5f, 11f, 18f, 5.5f), Color(0xFFB9A8F0).tone(mono), Ink, w)
            ink(path { moveTo(16.5f, 11f); lineTo(23.5f, 11f); moveTo(16.5f, 15f); lineTo(23.5f, 15f) }, Brand.content.tone(mono), 1.6f)
            ink(path { moveTo(10f, 18f); cubicTo(10f, 30f, 30f, 30f, 30f, 18f); moveTo(20f, 27f); lineTo(20f, 33f); moveTo(14f, 34f); lineTo(26f, 34f) }, Ink, w)
            ink(path { moveTo(17f, 8.5f); quadraticBezierTo(17.5f, 7f, 19f, 7f) }, Color.White, 1.5f)
        }
        Domain.Work -> {
            // Laptop with a little code.
            fill(rr(8f, 9f, 24f, 17f, 3f), Color(0xFFFFF7DD).tone(mono), Ink, w)
            ink(path { moveTo(13f, 15f); lineTo(16f, 17.5f); lineTo(13f, 20f); moveTo(19f, 20f); lineTo(25f, 20f) }, Color(0xFFB07A10).tone(mono), 2f)
            fill(path { moveTo(4f, 29f); lineTo(36f, 29f); lineTo(33f, 33f); lineTo(7f, 33f); close() }, Color(0xFFE0A030).tone(mono), Ink, w)
        }
        Domain.Other -> {
            // A note with a tick and a small star.
            fill(rr(9f, 7f, 22f, 27f, 4f), Color.White, Ink, w)
            ink(path { moveTo(13f, 14f); lineTo(27f, 14f); moveTo(13f, 20f); lineTo(23f, 20f) }, Color(0xFFC9CEDD), 2f)
            ink(path { moveTo(13f, 27f); lineTo(16f, 30f); lineTo(22f, 24f) }, Color(0xFFC0587A).tone(mono), 2.6f)
            fill(sparkle(30f, 8f, 5.5f), Brand.star.tone(mono), Ink, 1.6f)
        }
    }
}

/** Domain picture on its pale tile. When the user gave the task an icon, that icon sits on the tile instead. */
@Composable
fun DomainTile(d: Domain, size: Dp = 44.dp, look: String? = null, done: Boolean = false) {
    val mono = LocalThemeChoice.current.mono
    val dark = isSystemInDarkTheme()
    val tile = when {
        mono -> MaterialTheme.colorScheme.surfaceVariant
        dark -> d.color.copy(alpha = 0.28f).compositeOver(MaterialTheme.colorScheme.surface)
        else -> d.pale
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(tile).graphicsLayer { alpha = if (done) 0.55f else 1f },
        contentAlignment = Alignment.Center,
    ) {
        if (!look.isNullOrEmpty() && !look.startsWith("s:")) TaskBadge(look, if (mono) MaterialTheme.colorScheme.onSurface else d.color, size * 0.58f)
        else if (look?.startsWith("s:") == true) SportIcon(look.removePrefix("s:"), if (mono) MaterialTheme.colorScheme.onSurface else d.color, size * 0.66f)
        else Canvas(Modifier.size(size * 0.86f)) { art(40f, 40f) { drawDomain(d, mono) } }
    }
}

// ---------------- Navigation ----------------

private fun DrawScope.drawNav(i: Int, on: Boolean, mono: Boolean, dim: Color) {
    val w = 2.8f
    val inkC = if (on) Ink else dim
    val soft = Color(0xFFC9D2EA)
    when (i) {
        0 -> { // Sun on the horizon.
            ink(path { moveTo(4f, 30f); lineTo(36f, 30f) }, inkC, w)
            fill(path { moveTo(9f, 30f); arcTo(Rect(9f, 19f, 31f, 41f), 180f, 180f, false); close() }, (if (on) Brand.star else soft).tone(mono), inkC, w)
            ink(path { moveTo(20f, 6f); lineTo(20f, 11f); moveTo(8f, 12f); lineTo(11f, 15f); moveTo(32f, 12f); lineTo(29f, 15f) }, inkC, w)
        }
        1 -> { // Calendar with two dots.
            fill(rr(5f, 8f, 30f, 27f, 7f), Color.White, inkC, w)
            ink(path { moveTo(5f, 15f); lineTo(35f, 15f); moveTo(13f, 4f); lineTo(13f, 11f); moveTo(27f, 4f); lineTo(27f, 11f) }, inkC, w)
            drawCircle((if (on) Brand.lantern else soft).tone(mono), 3f, Offset(13f, 23f))
            drawCircle((if (on) Color(0xFF9DB4FF) else soft).tone(mono), 3f, Offset(20f, 27f))
        }
        2 -> { // Mountain with a flag on top.
            fill(path { moveTo(3f, 35f); lineTo(16f, 12f); lineTo(23f, 22f); lineTo(27f, 16f); lineTo(37f, 35f); close() }, (if (on) Color(0xFF9DB4FF) else soft).tone(mono), inkC, w)
            fill(path { moveTo(27f, 16f); lineTo(27f, 4f); lineTo(35f, 7f); lineTo(27f, 10f) }, (if (on) Brand.lantern else soft).tone(mono), inkC, w)
        }
        else -> { // Three bars, the last one tall.
            fill(rr(5f, 20f, 7f, 14f, 2f), (if (on) Color(0xFF9DB4FF) else soft).tone(mono), inkC, w)
            fill(rr(16.5f, 13f, 7f, 21f, 2f), (if (on) Color(0xFF9DB4FF) else soft).tone(mono), inkC, w)
            fill(rr(28f, 7f, 7f, 27f, 2f), (if (on) Brand.violet else soft).tone(mono), inkC, w)
        }
    }
}

/** Bottom navigation with the four drawn icons. The chosen one gets colour, a pill behind it, and a small hop. */
@Composable
fun JourneyNav(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val mono = LocalThemeChoice.current.mono
    val calm = calmMotion()
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                val pill by animateFloatAsState(if (on) 1f else 0f, tween(if (calm) 0 else 200), label = "pill")
                val pop = remember { Animatable(1f) }
                LaunchedEffect(on) { if (on && !calm) { pop.snapTo(0.86f); pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f)) } }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) }
                        .semantics { role = Role.Tab; this.selected = on }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(Modifier.width(60.dp).height(36.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.width(60.dp).height(36.dp).graphicsLayer { scaleX = 0.6f + 0.4f * pill; alpha = pill }
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                        )
                        Canvas(Modifier.size(28.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
                            art(40f, 40f) { drawNav(i, on, mono, dim) }
                        }
                    }
                    Text(
                        label, fontSize = 13.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.primary else dim,
                    )
                }
            }
        }
    }
}

// ---------------- Page header ----------------

/** The blue band on top of each page: big title, a line under it, round buttons, and room for a drawing. */
@Composable
fun PageHeader(title: String, sub: String, actions: @Composable RowScope.() -> Unit = {}, below: @Composable ColumnScope.() -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().background(headerColor(), RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 38.sp, color = Color.White)
                Text(sub, fontSize = 14.sp, color = Color(0xFFDCE4FF))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        below()
    }
}

/** A round square button on the header. */
@Composable
fun HeaderButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier.size(46.dp).pressScale(press).clip(RoundedCornerShape(15.dp))
            .background(if (LocalThemeChoice.current.mono) Color(0xFF333333) else Color.White.copy(alpha = 0.16f))
            .clickable(interactionSource = press, indication = androidx.compose.foundation.LocalIndication.current) { onClick() }
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---------------- The day's arc ----------------

/** What the arc is showing: "now", or a moment the user picked on it. */
class ArcPick(val minute: Int, val now: Boolean)

/**
 * The sky arc of the day: each prayer is a station along it (right = morning, as the day reads in Arabic), the
 * part already lived is golden, and «نور» stands where the time is now. [periods] must be in time order.
 *
 * It answers touch: tap «نور» for now; tap anywhere on the line (or drag along it) and «نور» walks there (350 ms)
 * and [card] shows that moment; it walks back home after 8 s, or on a second tap of «نور», or from the card's ×.
 * Stations are magnetic: a tap near one picks the station's start.
 */
@Composable
fun DayArc(periods: List<Period>, minute: Int, line: String, modifier: Modifier = Modifier,
           card: @Composable ColumnScope.(ArcPick, () -> Unit) -> Unit = { _, _ -> }) {
    val mono = LocalThemeChoice.current.mono
    val calm = calmMotion()
    val inf = rememberInfiniteTransition(label = "arc")
    val glow by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "g")
    val colors = periods.map { periodAccent(it.key) }
    if (periods.isEmpty()) return
    val from = periods.first().start
    val to = periods.last().end.coerceAtLeast(periods.last().start + 60)
    fun frac(m: Int) = ((m - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    fun minuteAt(t: Float) = (from + t * (to - from)).toInt()
    val now = frac(minute)
    var pick by remember { androidx.compose.runtime.mutableStateOf<ArcPick?>(null) }
    var poke by remember { mutableIntStateOf(0) }
    val walk = remember { Animatable(now) }
    // «نور» walks to the picked moment and back; without a pick it simply follows the clock.
    LaunchedEffect(pick?.minute, pick?.now, now) {
        val target = pick?.takeIf { !it.now }?.let { frac(it.minute) } ?: now
        if (calm) walk.snapTo(target) else walk.animateTo(target, tween(350, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(pick, poke) {
        if (pick != null) { delay(8000); pick = null }
    }
    val hop = remember { Animatable(0f) }
    LaunchedEffect(poke) {
        if (poke != 0 && !calm) { hop.animateTo(-10f, tween(140)); hop.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 500f)) }
    }
    val stationT = periods.map { frac(it.start) }
    Column(modifier.fillMaxWidth().animateContentSize()) {
        Box(Modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = line + ". اضغط على الفانوس أو على أي مكان بالخط" }) {
            Canvas(
                Modifier.fillMaxWidth().height(110.dp)
                    .pointerInput(periods, minute) {
                        val geo = ArcGeo(size.width.toFloat(), size.height.toFloat(), density)
                        detectTapGestures { pos ->
                            val (t, d) = geo.nearest(pos)
                            val nourAt = geo.at(walk.value)
                            when {
                                (pos - nourAt).getDistance() < 34.dp.toPx() -> {
                                    // «نور» itself: now; a second tap while showing now closes it.
                                    pick = if (pick?.now == true) null else ArcPick(minute, true); poke++
                                }
                                d < 30.dp.toPx() -> {
                                    val snap = stationT.withIndex().minByOrNull { kotlin.math.abs(it.value - t) }
                                        ?.takeIf { (geo.at(it.value) - pos).getDistance() < 22.dp.toPx() }
                                    pick = ArcPick(snap?.let { periods[it.index].start } ?: minuteAt(t), false); poke++
                                }
                                else -> pick = null
                            }
                        }
                    }
                    .pointerInput(periods, minute) {
                        val geo = ArcGeo(size.width.toFloat(), size.height.toFloat(), density)
                        detectDragGestures { change, _ ->
                            val (t, d) = geo.nearest(change.position)
                            if (d < 60.dp.toPx()) { pick = ArcPick(minuteAt(t), false); change.consume() }
                        }
                    },
            ) {
                val geo = ArcGeo(size.width, size.height, density)
                val stroke = 8.dp.toPx()
                drawPath(geo.path(0f, 1f), Color.White.copy(alpha = 0.22f), style = Stroke(stroke, cap = StrokeCap.Round))
                if (now > 0f) drawPath(geo.path(0f, now), Brand.star.tone(mono), style = Stroke(stroke, cap = StrokeCap.Round))
                // A picked moment gets a thin white mark on the line.
                pick?.takeIf { !it.now }?.let { p ->
                    val c = geo.at(frac(p.minute))
                    drawCircle(Color.White, 5.dp.toPx(), c)
                }
                periods.forEachIndexed { i, p ->
                    val c = geo.at(stationT[i])
                    val chosen = pick?.let { !it.now && it.minute >= p.start && it.minute < p.end } == true
                    val r = (if (chosen) 9.dp else 7.dp).toPx()
                    drawCircle(colors[i].tone(mono), r, c)
                    drawCircle(if (chosen) Color.White else Ink, r, c, style = Stroke(2.dp.toPx()))
                }
                val n = geo.at(walk.value)
                val s = 48.dp.toPx()
                val away = kotlin.math.abs(walk.value - now) > 0.01f
                translate(n.x - s / 2, n.y - s * 0.62f + hop.value * density) {
                    scale(s / 200f, s / 200f, Offset.Zero) { drawNour(if (pick?.now == true) NourMood.Happy else NourMood.Awake, 1f, if (calm) 0.6f else glow, 0f, mono) }
                }
                // While «نور» is away, a small ghost dot keeps the real now in sight.
                if (away) drawCircle(Brand.star.tone(mono), 6.dp.toPx(), geo.at(now))
            }
            Text(line, Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        androidx.compose.animation.AnimatedVisibility(
            pick != null,
            enter = androidx.compose.animation.fadeIn(tween(if (calm) 0 else 180)) + androidx.compose.animation.expandVertically(tween(if (calm) 0 else 240)),
            exit = androidx.compose.animation.fadeOut(tween(if (calm) 0 else 140)) + androidx.compose.animation.shrinkVertically(tween(if (calm) 0 else 200)),
        ) {
            val shown = remember { androidx.compose.runtime.mutableStateOf(ArcPick(minute, true)) }
            pick?.let { shown.value = it }
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) { detectTapGestures { poke++ } }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    card(shown.value) { pick = null }
                }
            }
        }
    }
}

/** Where the arc runs inside a box of [w]×[h] px: a quadratic curve from right to left. */
private class ArcGeo(w: Float, h: Float, density: Float) {
    private val p0 = Offset(w - 18 * density, h - 26 * density)
    private val p1 = Offset(w / 2, -h * 0.42f)
    private val p2 = Offset(18 * density, h - 26 * density)
    fun at(t: Float): Offset {
        val a = (1 - t) * (1 - t); val b = 2 * (1 - t) * t; val c = t * t
        return Offset(a * p0.x + b * p1.x + c * p2.x, a * p0.y + b * p1.y + c * p2.y)
    }
    fun path(t0: Float, t1: Float) = Path().apply {
        val a = at(t0); moveTo(a.x, a.y)
        for (i in 1..48) { val b = at(t0 + (t1 - t0) * i / 48f); lineTo(b.x, b.y) }
    }
    /** Closest point of the line to [p]: its position 0..1 and the distance in px. */
    fun nearest(p: Offset): Pair<Float, Float> {
        var best = 0f; var bd = Float.MAX_VALUE
        for (i in 0..200) { val t = i / 200f; val d = (at(t) - p).getDistance(); if (d < bd) { bd = d; best = t } }
        return best to bd
    }
}

// ---------------- Badges ----------------

/** A medal: ribbon, disc and a mark. Locked ones are grey with a question mark. */
@Composable
fun Medal(color: Color, earned: Boolean, mark: String = "★", size: Dp = 46.dp) {
    val mono = LocalThemeChoice.current.mono
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            art(44f, 48f) {
                val inkC = if (earned) Ink else Color(0xFF8A90A8)
                fill(path { moveTo(14f, 30f); lineTo(10f, 46f); lineTo(22f, 40f); lineTo(34f, 46f); lineTo(30f, 30f) }, (if (earned) color else Color(0xFFE3EAFB)).tone(mono), inkC, 2.5f)
                fill(oval(22f, 20f, 16f, 16f), (if (earned) Brand.star else Color(0xFFF4F7FE)).tone(mono), inkC, 2.5f)
                if (earned) ink(path { moveTo(12f, 15f); quadraticBezierTo(13f, 9f, 19f, 7.5f) }, Color.White, 2f)
            }
        }
        Text(if (earned) mark else "؟", Modifier.padding(bottom = size * 0.16f), fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.ExtraBold,
            color = if (earned) Brand.ink else Color(0xFF8A90A8), fontFamily = DisplayFont)
    }
}

// ---------------- Done circle and the moment of doing ----------------

/**
 * Round tick on a task. Tapping it fills the circle green and draws the tick (300 ms), then [onDone] runs.
 * It answers right away; nothing waits for the animation.
 */
@Composable
fun DoneCircle(done: Boolean, label: String, onDone: (() -> Unit)?) {
    val calm = calmMotion()
    var tapped by remember { mutableIntStateOf(0) }
    val fillK by animateFloatAsState(if (done || tapped > 0) 1f else 0f, tween(if (calm) 0 else 300, easing = FastOutSlowInEasing), label = "tick")
    val green = doneColor()
    val ring = MaterialTheme.colorScheme.outline
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(24.dp))
            .let { m -> if (onDone != null && !done) m.clickable { tapped++; onDone() } else m }
            .semantics { contentDescription = if (done) "منجزة" else label; role = Role.Checkbox },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(34.dp)) {
            val r = size.minDimension / 2
            drawCircle(ring, r - 1.5.dp.toPx(), style = Stroke(3.dp.toPx()))
            if (fillK > 0f) {
                drawCircle(green, (r - 1.5.dp.toPx()) * fillK)
                val p = path {
                    moveTo(size.width * 0.28f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.68f)
                    lineTo(size.width * 0.74f, size.height * 0.36f)
                }
                val m = androidx.compose.ui.graphics.PathMeasure().apply { setPath(p, false) }
                val part = Path()
                m.getSegment(0f, m.length * ((fillK - 0.3f) / 0.7f).coerceIn(0f, 1f), part, true)
                drawPath(part, Color.White, style = Stroke(3.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** The "+N" that rises after finishing something, and the two stars beside it. Fired from anywhere. */
object Cheer {
    var points by androidx.compose.runtime.mutableIntStateOf(0)
    var at by androidx.compose.runtime.mutableLongStateOf(0L)
    var title by androidx.compose.runtime.mutableStateOf("")
    fun fire(p: Int, what: String) { points = p; title = what; at = System.currentTimeMillis() }
}

/** Shown over the page for 1.1 s after something is done (450 ms rise, then it fades). Tapping does not wait for it. */
@Composable
fun CheerLayer(showPoints: Boolean) {
    val calm = calmMotion()
    val k = remember { Animatable(0f) }
    var visible by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(Cheer.at) {
        if (Cheer.at == 0L) return@LaunchedEffect
        visible = true
        k.snapTo(0f)
        if (calm) { k.snapTo(0.5f); delay(1100) } else {
            k.animateTo(0.5f, tween(450, easing = FastOutSlowInEasing))
            delay(350)
            k.animateTo(1f, tween(300))
        }
        visible = false
    }
    if (!visible) return
    val v = k.value
    val alpha = if (v <= 0.5f) (v / 0.5f).coerceIn(0f, 1f) else (1f - (v - 0.5f) / 0.5f).coerceIn(0f, 1f)
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 1.dp)) {
        Row(
            Modifier.align(Alignment.Center).graphicsLayer { translationY = -60.dp.toPx() * v; this.alpha = alpha }
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Canvas(Modifier.size(22.dp).graphicsLayer { rotationZ = 90f * v }) { art(20f, 20f) { fill(sparkle(10f, 10f, 8.5f), Brand.star, Ink, 1.6f) } }
            Text(
                if (showPoints && Cheer.points > 0) "+${Cheer.points}" else "✓",
                fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp,
                color = if (LocalThemeChoice.current.mono) MaterialTheme.colorScheme.onSurface else Brand.violet,
            )
            Text(Cheer.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.size(16.dp).graphicsLayer { rotationZ = -90f * v }) { art(20f, 20f) { fill(sparkle(10f, 10f, 8.5f), Brand.star, Ink, 2f) } }
        }
    }
}

/** «نور» with a few words, for empty and quiet places. */
@Composable
fun NourNote(mood: NourMood, title: String, text: String? = null, size: Dp = 110.dp) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Nour(mood, size = size, label = title)
        Text(title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 21.sp, textAlign = TextAlign.Center)
        if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Clock shown in the header line: minutes from now to [target] as "2 س 25 د". */
fun untilLabel(nowMinute: Int, target: Int): String = durLabel((target - nowMinute).coerceAtLeast(0))


/**
 * Two or three choices in one pill; the white piece slides to the chosen one (200 ms). Works the same in RTL:
 * the first choice is on the right.
 */
@Composable
fun Segmented(labels: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val calm = calmMotion()
    BoxWithConstraints(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
        val segW = maxWidth / labels.size
        val x by androidx.compose.animation.core.animateDpAsState(segW * selected, tween(if (calm) 0 else 200, easing = FastOutSlowInEasing), label = "seg")
        Box(Modifier.offset(x = x).width(segW).height(42.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                val on = i == selected
                Box(
                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp)).clickable { onSelect(i) }
                        .semantics { role = Role.Tab; this.selected = on },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(l, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

