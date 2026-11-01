package com.imad.planner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One-colour sport pictures drawn in code (a 24×24 grid scaled to the size asked).
 * Stick figures with their equipment, so each record is recognisable at a glance.
 */
object SportIcons {
    val all: List<Pair<String, String>> = listOf(
        "curl" to "كيرل", "deadlift" to "ديدلفت", "log" to "لوق", "bench" to "بنش", "cleanjerk" to "كلين آند جيرك",
        "snatch" to "سناتش", "squat" to "سكوات", "pushup" to "ضغط", "knuckle" to "ضغط قبضة", "pullup" to "عقلة",
        "dips" to "متوازي", "weightedpull" to "عقلة بوزن", "fingers" to "أصابع", "doubleunder" to "دبل أندر",
        "run" to "ركض", "longjump" to "وثب طويل", "swim" to "سباحة", "skips" to "أندر سكيبس", "vjump" to "قفز عمودي",
        "boxjump" to "بوكس جمب", "boxing" to "ملاكمة", "armwrestle" to "مصارعة ذراع", "dumbbell" to "دمبل",
        "kettlebell" to "كيتلبل", "stopwatch" to "ساعة", "trophy" to "كأس",
    )

    /** The picture each of the first records starts with (the user can change or remove it). */
    val defaults = mapOf(
        "sport-1" to "curl", "sport-2" to "deadlift", "sport-3" to "log", "sport-4" to "bench", "sport-5" to "cleanjerk",
        "sport-6" to "snatch", "sport-7" to "squat", "sport-8" to "pushup", "sport-9" to "knuckle", "sport-10" to "pullup",
        "sport-11" to "dips", "sport-12" to "weightedpull", "sport-13" to "fingers", "sport-14" to "doubleunder",
        "sport-15" to "run", "sport-16" to "longjump", "sport-17" to "swim", "sport-18" to "skips", "sport-19" to "vjump",
        "sport-20" to "boxjump",
    )
}

@Composable
fun SportIcon(key: String, color: Color, size: Dp = 28.dp) {
    Canvas(Modifier.size(size)) { drawSport(key, color) }
}

private class Pen(val d: DrawScope, val c: Color) {
    val u = d.size.minDimension / 24f
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(vararg pts: Float) {
        for (i in 0 until pts.size - 2 step 2)
            d.drawLine(c, p(pts[i], pts[i + 1]), p(pts[i + 2], pts[i + 3]), strokeWidth = 1.9f * u, cap = StrokeCap.Round)
    }
    fun head(x: Float, y: Float, r: Float = 2.1f) = d.drawCircle(c, r * u, p(x, y))
    fun ring(x: Float, y: Float, r: Float) = d.drawCircle(c, r * u, p(x, y), style = Stroke(1.6f * u))
    fun box(x: Float, y: Float, w: Float, h: Float, fill: Boolean = true, r: Float = 1f) =
        if (fill) d.drawRoundRect(c, p(x, y), Size(w * u, h * u), CornerRadius(r * u))
        else d.drawRoundRect(c, p(x, y), Size(w * u, h * u), CornerRadius(r * u), style = Stroke(1.6f * u))
    /** A bar with plates at both ends. */
    fun bar(x1: Float, x2: Float, y: Float, plate: Float = 2.6f) {
        d.drawLine(c, p(x1, y), p(x2, y), strokeWidth = 1.3f * u, cap = StrokeCap.Round)
        box(x1 - 0.2f, y - plate, 1.6f, plate * 2, r = 0.6f); box(x2 - 1.4f, y - plate, 1.6f, plate * 2, r = 0.6f)
    }
    fun curve(vararg pts: Float, closed: Boolean = false) {
        val path = Path().apply {
            moveTo(pts[0] * u, pts[1] * u)
            var i = 2
            while (i + 3 < pts.size) { quadraticBezierTo(pts[i] * u, pts[i + 1] * u, pts[i + 2] * u, pts[i + 3] * u); i += 4 }
            if (closed) close()
        }
        d.drawPath(path, c, style = Stroke(1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    fun arrow(x1: Float, y1: Float, x2: Float, y2: Float) {
        line(x1, y1, x2, y2)
        val dx = x2 - x1; val dy = y2 - y1
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
        val ux = dx / len; val uy = dy / len
        line(x2, y2, x2 - 2.5f * ux + 1.8f * uy, y2 - 2.5f * uy - 1.8f * ux)
        line(x2, y2, x2 - 2.5f * ux - 1.8f * uy, y2 - 2.5f * uy + 1.8f * ux)
    }
}

fun DrawScope.drawSport(key: String, color: Color) {
    val k = Pen(this, color)
    when (key) {
        "curl" -> { k.head(12f, 3.5f); k.line(12f, 6f, 12f, 14f); k.line(12f, 14f, 9.5f, 21.5f); k.line(12f, 14f, 14.5f, 21.5f)
            k.line(12f, 7.5f, 9f, 11f, 8f, 8f); k.line(12f, 7.5f, 15f, 11f, 16f, 8f); k.bar(4f, 20f, 8f, 2.2f) }
        "deadlift" -> { k.head(7.5f, 7f); k.line(9.3f, 8.5f, 15f, 13f); k.line(15f, 13f, 13.5f, 17f, 14f, 21.5f); k.line(15f, 13f, 17.5f, 17f, 17f, 21.5f)
            k.line(10.5f, 9.5f, 10.5f, 17.5f); k.bar(3f, 21f, 17.5f) }
        "log" -> { k.head(12f, 8.5f); k.line(12f, 11f, 12f, 17f); k.line(12f, 17f, 9.5f, 22f); k.line(12f, 17f, 14.5f, 22f)
            k.line(12f, 12f, 8f, 6f); k.line(12f, 12f, 16f, 6f); k.box(4f, 2.2f, 16f, 3.6f, r = 1.8f) }
        "bench" -> { k.line(3f, 16.5f, 19f, 16.5f); k.line(5f, 16.5f, 5f, 21f); k.line(17f, 16.5f, 17f, 21f)
            k.head(4.5f, 13f); k.line(6.8f, 14f, 14f, 14f); k.line(14f, 14f, 18f, 16f, 20f, 21f); k.line(9f, 14f, 9f, 7.5f); k.bar(3f, 15f, 7.5f, 2.2f) }
        "cleanjerk" -> { k.head(12f, 6f); k.line(12f, 8.5f, 12f, 14.5f); k.line(12f, 14.5f, 8f, 18f, 6f, 22f); k.line(12f, 14.5f, 16f, 18f, 18.5f, 22f)
            k.line(12f, 9.5f, 9f, 3f); k.line(12f, 9.5f, 15f, 3f); k.bar(4f, 20f, 3f, 2.2f) }
        "snatch" -> { k.head(12f, 7.5f); k.line(12f, 10f, 12f, 15f); k.line(12f, 15f, 7.5f, 16.5f, 8f, 22f); k.line(12f, 15f, 16.5f, 16.5f, 16f, 22f)
            k.line(12f, 10.5f, 6f, 3.5f); k.line(12f, 10.5f, 18f, 3.5f); k.bar(2.5f, 21.5f, 3.5f, 2.2f) }
        "squat" -> { k.head(12f, 4.5f); k.line(12f, 7f, 11f, 13.5f); k.line(11f, 13.5f, 16f, 15f, 15f, 21.5f); k.line(11f, 13.5f, 7f, 15f, 8f, 21.5f)
            k.line(12f, 8f, 8f, 8f); k.line(12f, 8f, 16f, 8f); k.bar(3.5f, 20.5f, 7.5f) }
        "pushup", "knuckle" -> { k.line(2f, 20f, 22f, 20f); k.head(5f, 10.5f); k.line(7f, 12f, 20f, 17.5f); k.line(9f, 13f, 9f, 19f)
            if (key == "knuckle") k.head(9f, 19f, 1.5f) }
        "pullup", "weightedpull" -> { k.line(3f, 3f, 21f, 3f); k.line(9f, 3f, 10f, 8f, 12f, 10f); k.line(15f, 3f, 14f, 8f, 12f, 10f); k.head(12f, 7.5f, 1.9f)
            k.line(12f, 10f, 12f, 16f); k.line(12f, 16f, 10.5f, 21f); k.line(12f, 16f, 13.5f, 21f)
            if (key == "weightedpull") k.ring(12f, 19.5f, 2.6f) }
        "dips" -> { k.line(5f, 12f, 5f, 22f); k.line(19f, 12f, 19f, 22f); k.line(3.5f, 12f, 7.5f, 12f); k.line(16.5f, 12f, 20.5f, 12f)
            k.head(12f, 4.5f); k.line(12f, 7f, 12f, 14f); k.line(12f, 8f, 6.5f, 12f); k.line(12f, 8f, 17.5f, 12f); k.line(12f, 14f, 10f, 18f, 12f, 20f) }
        "fingers" -> { k.line(3f, 3.5f, 21f, 3.5f); k.line(10f, 3.5f, 10f, 11f); k.line(13.5f, 3.5f, 13.5f, 11f); k.box(7.5f, 10.5f, 9f, 7f, fill = false, r = 2.5f)
            k.line(12f, 17.5f, 12f, 21.5f) }
        "doubleunder", "skips" -> { k.head(12f, 4f); k.line(12f, 6.5f, 12f, 13f); k.line(12f, 13f, 10f, 17f); k.line(12f, 13f, 14f, 17f)
            k.line(12f, 8f, 8f, 11.5f); k.line(12f, 8f, 16f, 11.5f)
            k.curve(8f, 11.5f, 2f, 23f, 12f, 22.5f); k.curve(16f, 11.5f, 22f, 23f, 12f, 22.5f)
            if (key == "doubleunder") { k.curve(8f, 11.5f, 4f, 20f, 12f, 19.5f); k.curve(16f, 11.5f, 20f, 20f, 12f, 19.5f) }
            else { k.line(3f, 15f, 5f, 15f); k.line(19f, 15f, 21f, 15f) } }
        "run" -> { k.head(14.5f, 3.5f); k.line(14f, 6f, 12f, 13f); k.line(12f, 13f, 16f, 16.5f, 15f, 21.5f); k.line(12f, 13f, 8.5f, 16f, 5.5f, 15f)
            k.line(13.5f, 8f, 17.5f, 10.5f); k.line(13.5f, 8f, 9.5f, 10f); k.line(2f, 7f, 6f, 7f); k.line(1f, 10f, 5f, 10f) }
        "longjump" -> { k.head(18f, 6.5f); k.line(16.5f, 8f, 11f, 11.5f); k.line(11f, 11.5f, 6f, 9f); k.line(11f, 11.5f, 7f, 14.5f)
            k.line(15f, 9f, 19f, 12f); k.arrow(3f, 20f, 21f, 20f) }
        "swim" -> { k.curve(2f, 16f, 5f, 13.5f, 8f, 16f, 11f, 18.5f, 14f, 16f, 17f, 13.5f, 20f, 16f, 21.5f, 17f, 22f, 16f)
            k.curve(2f, 20.5f, 5f, 18f, 8f, 20.5f, 11f, 23f, 14f, 20.5f, 17f, 18f, 20f, 20.5f)
            k.head(8f, 12f); k.curve(10f, 12.5f, 13f, 5f, 18f, 9f) }
        "vjump" -> { k.head(10f, 4f); k.line(10f, 6.5f, 10f, 12.5f); k.line(10f, 12.5f, 7f, 15f, 9f, 18f); k.line(10f, 12.5f, 13f, 15f, 11.5f, 18f)
            k.line(10f, 8f, 6.5f, 4f); k.line(10f, 8f, 13.5f, 4f); k.line(4f, 22f, 16f, 22f); k.arrow(19.5f, 21f, 19.5f, 3f) }
        "boxjump" -> { k.box(11f, 14f, 10f, 8f, fill = false, r = 0.8f); k.line(2f, 22f, 22f, 22f)
            k.head(9f, 4f); k.line(9f, 6.5f, 9f, 10.5f); k.line(9f, 10.5f, 13f, 12f); k.line(9f, 10.5f, 7f, 13.5f); k.line(9f, 7.5f, 5f, 9f); k.line(9f, 7.5f, 12f, 5f) }
        "boxing" -> { k.box(5f, 4f, 12f, 11f, r = 4.5f); k.box(6f, 14f, 9f, 5f, fill = false, r = 1f); k.ring(17.5f, 10f, 2.5f) }
        "armwrestle" -> { k.line(2f, 18f, 22f, 18f); k.line(4f, 18f, 9f, 13f, 11f, 6f); k.line(20f, 18f, 15f, 13f, 13f, 6f); k.head(12f, 5f, 2.4f) }
        "dumbbell" -> { k.line(6f, 12f, 18f, 12f); k.box(3f, 7.5f, 3f, 9f, r = 1f); k.box(18f, 7.5f, 3f, 9f, r = 1f) }
        "kettlebell" -> { k.ring(12f, 15f, 6.5f); k.curve(8f, 10f, 8f, 3f, 12f, 3f); k.curve(16f, 10f, 16f, 3f, 12f, 3f) }
        "stopwatch" -> { k.ring(12f, 13.5f, 8f); k.line(12f, 13.5f, 12f, 8.5f); k.line(12f, 13.5f, 15.5f, 15.5f); k.line(10f, 2.5f, 14f, 2.5f); k.line(12f, 2.5f, 12f, 5.5f) }
        "trophy" -> { k.curve(6f, 3f, 6f, 13f, 12f, 13f); k.curve(18f, 3f, 18f, 13f, 12f, 13f); k.line(6f, 3f, 18f, 3f)
            k.curve(6f, 5f, 2f, 5f, 6f, 9.5f); k.curve(18f, 5f, 22f, 5f, 18f, 9.5f); k.line(12f, 13f, 12f, 18f); k.box(8f, 18f, 8f, 3.5f, r = 0.8f) }
        else -> k.ring(12f, 12f, 8f)
    }
}
