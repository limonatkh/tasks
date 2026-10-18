package com.imad.planner

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/** Prayer times of one day, minutes from midnight (wall clock). */
data class PrayerDay(
    val date: LocalDate,
    val fajr: Int, val sunrise: Int, val dhuhr: Int, val asr: Int, val maghrib: Int, val isha: Int,
    /** "table" when it came from the pasted monthly sheet, "approx" when calculated. */
    val source: String = "table",
) {
    fun at(key: String): Int = when (key) {
        "fajr" -> fajr; "sunrise" -> sunrise; "dhuhr" -> dhuhr; "asr" -> asr; "maghrib" -> maghrib; else -> isha
    }
}

/** A part of the day between two prayers. */
data class Period(val key: String, val name: String, val start: Int, val end: Int)

object Prayer {
    val keys = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
    val names = mapOf("fajr" to "الفجر", "sunrise" to "الشروق", "dhuhr" to "الظهر", "asr" to "العصر", "maghrib" to "المغرب", "isha" to "العشاء")
    val periodNames = mapOf(
        "fajr" to "الفجر ← الظهر", "dhuhr" to "الظهر ← العصر", "asr" to "العصر ← المغرب",
        "maghrib" to "المغرب ← العشاء", "isha" to "العشاء ← النوم",
    )

    // Gaza; the sheet the user photographs is for Gaza Strip.
    const val LAT = 31.50
    const val LON = 34.47

    /**
     * Pasted code: one line per day, "date fajr second-fajr sunrise dhuhr asr maghrib isha" in 24-hour time.
     * The second fajr column is optional. Lines that do not parse are ignored.
     */
    fun parse(text: String): List<PrayerDay> = text.lines().mapNotNull { line ->
        val parts = line.trim().split(Regex("[\\s,;|]+")).filter { it.isNotBlank() }
        if (parts.size < 7) return@mapNotNull null
        val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return@mapNotNull null
        val times = parts.drop(1).mapNotNull { hm(it) }
        when {
            times.size >= 7 -> PrayerDay(date, times[0], times[2], times[3], times[4], times[5], times[6])
            times.size == 6 -> PrayerDay(date, times[0], times[1], times[2], times[3], times[4], times[5])
            else -> null
        }
    }.distinctBy { it.date }.sortedBy { it.date }

    fun encode(days: List<PrayerDay>): String = days.joinToString("\n") {
        listOf(it.date.toString(), t(it.fajr), t(it.sunrise), t(it.dhuhr), t(it.asr), t(it.maghrib), t(it.isha)).joinToString(" ")
    }

    private fun t(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    private fun hm(s: String): Int? {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(s.trim()) ?: return null
        val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
        if (h > 23 || mi > 59) return null
        return h * 60 + mi
    }

    /** Times for [day]: from the sheet if present, else calculated and corrected by how the sheet differed from the calculation. */
    fun day(day: LocalDate, table: List<PrayerDay>, zone: ZoneId = ZoneId.systemDefault()): PrayerDay {
        table.firstOrNull { it.date == day }?.let { return it }
        val calc = calculate(day, zone)
        val sample = table.takeLast(10)
        if (sample.isEmpty()) return calc
        fun off(sel: (PrayerDay) -> Int): Int {
            val diffs = sample.map { d -> sel(d) - sel(calculate(d.date, zone)) }.map { if (abs(it) > 120) 0 else it }
            return diffs.average().roundToInt()
        }
        return PrayerDay(
            day, calc.fajr + off { it.fajr }, calc.sunrise + off { it.sunrise }, calc.dhuhr + off { it.dhuhr },
            calc.asr + off { it.asr }, calc.maghrib + off { it.maghrib }, calc.isha + off { it.isha }, source = "approx",
        )
    }

    /** Periods of the day; the last one ends at sleep time. */
    fun periods(p: PrayerDay, sleep: Int): List<Period> = listOf(
        Period("fajr", periodNames.getValue("fajr"), p.fajr, p.dhuhr),
        Period("dhuhr", periodNames.getValue("dhuhr"), p.dhuhr, p.asr),
        Period("asr", periodNames.getValue("asr"), p.asr, p.maghrib),
        Period("maghrib", periodNames.getValue("maghrib"), p.maghrib, p.isha),
        Period("isha", periodNames.getValue("isha"), p.isha, maxOf(sleep, p.isha + 30)),
    )

    // ---------- Astronomical calculation (Egyptian General Authority: fajr 19.5°, isha 17.5°, Shafi'i asr) ----------

    private fun dsin(d: Double) = sin(Math.toRadians(d))
    private fun dcos(d: Double) = cos(Math.toRadians(d))
    private fun dtan(d: Double) = tan(Math.toRadians(d))
    private fun darcsin(x: Double) = Math.toDegrees(asin(x))
    private fun darccos(x: Double) = Math.toDegrees(acos(x.coerceIn(-1.0, 1.0)))
    private fun darctan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun darccot(x: Double) = Math.toDegrees(atan(1 / x))
    private fun fix(a: Double, b: Double) = (a - b * floor(a / b)).let { if (it < 0) it + b else it }

    private fun sun(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g), 360.0)
        val e = 23.439 - 0.00000036 * d
        val ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15
        val eqt = q / 15 - fix(ra, 24.0)
        val decl = darcsin(dsin(e) * dsin(l))
        return decl to eqt
    }

    fun calculate(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): PrayerDay {
        val tz = zone.rules.getOffset(day.atTime(12, 0)).totalSeconds / 3600.0
        val jd = julian(day) - LON / 360.0
        fun midDay(t: Double): Double = 12 - sun(jd + t).second
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sun(jd + t).first
            val noon = midDay(t)
            val v = darccos((-dsin(angle) - dsin(decl) * dsin(LAT)) / (dcos(decl) * dcos(LAT))) / 15
            return noon + if (ccw) -v else v
        }
        fun asrTime(t: Double): Double {
            val decl = sun(jd + t).first
            val angle = -darccot(1 + dtan(abs(LAT - decl)))
            return angleTime(angle, t, false)
        }
        val fajr = angleTime(19.5, 5.0 / 24, true)
        val sunrise = angleTime(0.833, 6.0 / 24, true)
        val dhuhr = midDay(12.0 / 24)
        val asr = asrTime(13.0 / 24)
        val maghrib = angleTime(0.833, 18.0 / 24, false)
        val isha = angleTime(17.5, 18.0 / 24, false)
        fun toMin(h: Double) = ((h + tz - LON / 15) * 60).roundToInt()
        return PrayerDay(day, toMin(fajr), toMin(sunrise), toMin(dhuhr), toMin(asr), toMin(maghrib), toMin(isha), source = "approx")
    }

    private fun julian(d: LocalDate): Double {
        var y = d.year; var m = d.monthValue
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d.dayOfMonth + b - 1524.5
    }

    /** Message to send to ChatGPT together with the photo of the monthly sheet. */
    const val CHATGPT_PROMPT = """هذه صورة جدول مواقيت الصلاة لشهر كامل. حوّلها لنص بهذا الشكل بالضبط، سطر لكل يوم، وبدون أي كلام ثاني:
YYYY-MM-DD فجر_أول فجر_ثاني شروق ظهر عصر مغرب عشاء
- التاريخ ميلادي بصيغة YYYY-MM-DD (السنة-الشهر-اليوم).
- كل الأوقات بنظام 24 ساعة: الظهر بعد 12 تبقى كما هي، والعصر والمغرب والعشاء أضف لها 12 (مثلاً 3:45 تصير 15:45، و7:33 تصير 19:33).
- إذا كانت الخانة مكتوب فيها الدقائق فقط مثل :32 فالساعة هي نفس ساعة السطر الذي قبله في نفس العمود.
- انتبه لتغيير التوقيت الشتوي/الصيفي في منتصف الجدول واكتب الأوقات كما هي مطبوعة.
مثال سطر: 2026-10-12 04:45 05:15 06:43 12:29 15:45 18:17 19:33"""

    /** Gaza Strip sheet, Jumada al-Ula 1448 (12 Oct – 10 Nov 2026), as the user sent it. */
    const val SEED = """2026-10-12 04:45 05:15 06:43 12:29 15:45 18:17 19:33
2026-10-13 04:46 05:16 06:44 12:29 15:45 18:16 19:32
2026-10-14 04:47 05:17 06:45 12:29 15:44 18:14 19:30
2026-10-15 04:47 05:17 06:45 12:28 15:43 18:13 19:29
2026-10-16 04:48 05:18 06:46 12:28 15:42 18:12 19:28
2026-10-17 04:49 05:19 06:47 12:28 15:41 18:11 19:27
2026-10-18 04:49 05:19 06:48 12:28 15:40 18:09 19:26
2026-10-19 04:50 05:20 06:48 12:28 15:40 18:08 19:25
2026-10-20 04:51 05:21 06:49 12:28 15:39 18:07 19:24
2026-10-21 04:51 05:21 06:50 12:27 15:38 18:06 19:23
2026-10-22 04:52 05:22 06:50 12:27 15:38 18:05 19:22
2026-10-23 04:53 05:23 06:51 12:27 15:37 18:04 19:21
2026-10-24 03:54 04:24 05:53 11:27 14:36 17:03 18:20
2026-10-25 03:55 04:24 05:53 11:27 14:35 17:02 18:20
2026-10-26 03:55 04:25 05:54 11:27 14:34 17:01 18:19
2026-10-27 03:56 04:26 05:54 11:27 14:34 17:00 18:18
2026-10-28 03:56 04:26 05:55 11:26 14:33 16:59 18:17
2026-10-29 03:57 04:27 05:56 11:26 14:32 16:58 18:16
2026-10-30 03:58 04:28 05:56 11:26 14:32 16:57 18:15
2026-10-31 03:58 04:28 05:57 11:26 14:31 16:56 18:14
2026-11-01 03:59 04:29 05:58 11:26 14:30 16:56 18:13
2026-11-02 04:00 04:30 05:59 11:26 14:30 16:55 18:12
2026-11-03 04:00 04:30 06:00 11:26 14:29 16:55 18:12
2026-11-04 04:01 04:31 06:01 11:26 14:29 16:54 18:11
2026-11-05 04:02 04:32 06:01 11:26 14:28 16:53 18:11
2026-11-06 04:03 04:33 06:02 11:26 14:28 16:53 18:10
2026-11-07 04:03 04:33 06:03 11:26 14:27 16:52 18:10
2026-11-08 04:04 04:34 06:04 11:26 14:27 16:51 18:09
2026-11-09 04:05 04:35 06:05 11:27 14:26 16:51 18:09
2026-11-10 04:06 04:36 06:06 11:27 14:26 16:50 18:08"""
}
