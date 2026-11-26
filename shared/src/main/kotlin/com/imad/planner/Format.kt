package com.imad.planner

import java.util.Locale

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
