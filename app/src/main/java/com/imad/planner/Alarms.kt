package com.imad.planner

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Ringing alarms at task boundaries, rest/set alarms during training, and the day roll-over. */
object Alarms {
    private const val ALARM_PREFIX = "planner_alarm_"
    const val TIMER_CHANNEL = "planner_timer_v1"
    private const val BASE_CODE = 5000
    private const val MAX_EVENTS = 80
    private const val TIMER_NOTIFICATION = 1
    private const val PREFS = "alarms"

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** [kind]: task | prayer (rings like an alarm) | note (plain notification) | period (period ended, with actions). */
    data class Event(val at: Long, val title: String, val text: String, val taskId: String?, val canStart: Boolean, val silent: Boolean = false, val close: Boolean = false,
                     val kind: String = "task", val period: String = "")

    const val PRAYER_CHANNEL = "planner_prayer_v1"
    const val NOTE_CHANNEL = "planner_note_v1"

    /** The task sound the user picked; by default a short notification tone, not the wake-up alarm. */
    fun soundUri(context: Context): android.net.Uri? {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("sound", null)
        return saved?.let(android.net.Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    fun setSound(context: Context, uri: android.net.Uri?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("sound", uri?.toString()).apply()
        channels(context)
    }

    /** Channel sounds cannot change, so each sound gets its own channel and old ones are removed. */
    private fun alarmChannel(context: Context): String = ALARM_PREFIX + (soundUri(context)?.toString() ?: "none").hashCode().toUInt().toString(16)

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val id = alarmChannel(context)
        nm.notificationChannels.filter { (it.id.startsWith(ALARM_PREFIX) || it.id == "planner_alarm_v1") && it.id != id }
            .forEach { nm.deleteNotificationChannel(it.id) }
        val alarm = NotificationChannel(id, "تنبيهات المهام", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "ترن عند انتهاء مهمة وبدء التالية"
            setSound(
                soundUri(context),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val timer = NotificationChannel(TIMER_CHANNEL, "المؤقت الجاري", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        }
        val prayer = NotificationChannel(PRAYER_CHANNEL, "منبهات الصلاة", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "ترن زي المنبه قبل الصلاة أو مع وقتها"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val note = NotificationChannel(NOTE_CHANNEL, "ملخصات وتذكيرات", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "ملخص الصبح وتذكير آخر كل فترة"
        }
        nm.createNotificationChannel(alarm)
        nm.createNotificationChannel(timer)
        nm.createNotificationChannel(prayer)
        nm.createNotificationChannel(note)
    }

    fun canExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    /** Builds the list of alarm events for today from the planned tasks. */
    fun events(tasks: List<Task>, now: Long): List<Event> {
        val open = tasks.filter { it.open && it.parallelTo == null && !it.overflow && (it.planned > 0 || it.running) }
        val ends = mutableListOf<Pair<Long, Task>>()
        val out = mutableListOf<Event>()
        val running = open.firstOrNull { it.running }
        if (running != null) {
            val end = now + Maths.remaining(running, now)
            ends += end to running
            if (running.sets > 1 && running.restSec > 0) {
                val effStart = now - Maths.spent(running, now)
                val windows = Scheduler.restWindows(0, running.minutes, running.sets, running.workSec, running.restSec)
                val parallel = tasks.filter { it.parallelTo == running.id && it.open }.joinToString("، ") { it.title }
                windows.forEachIndexed { i, w ->
                    val restAt = effStart + w.startSec * 1000L
                    val nextSetAt = effStart + w.endSec * 1000L
                    if (restAt > now) out += Event(
                        restAt, "راحة — ${running.title}",
                        if (parallel.isNotEmpty()) "بالتوازي: $parallel" else "الجولة ${i + 2} بعد ${w.endSec - w.startSec} ث",
                        running.id, false
                    )
                    if (nextSetAt > now) out += Event(nextSetAt, "الجولة ${i + 2} من ${running.sets}", running.title, running.id, false)
                }
            }
        }
        for (t in open.filter { !it.running }) {
            ends += t.planned + t.minutes * 60_000L to t
        }
        for (t in open.filter { !it.running && it.planned > now }.sortedBy { it.planned }) {
            val before = ends.filter { (end, other) -> other.id != t.id && kotlin.math.abs(end - t.planned) <= 2 * 60_000L }
                .map { it.second.title }
            val text = if (before.isNotEmpty()) "انتهى وقت: ${before.first()}" else "${t.minutes} دقيقة"
            out += Event(t.planned, "حان وقت: ${t.title}", text, t.id, true)
        }
        // An end with no task right after it still rings.
        val starts = open.filter { !it.running }.map { it.planned }
        for ((end, t) in ends) {
            if (end <= now) continue
            if (starts.any { kotlin.math.abs(it - end) <= 2 * 60_000L }) continue
            out += Event(end, "انتهى وقت: ${t.title}", "أنهِ المهمة أو مدّدها", t.id, false)
        }
        return out.sortedBy { it.at }.take(MAX_EVENTS - 1)
    }

    fun reschedule(context: Context, tasks: List<Task>, settings: Settings) {
        val am = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getInt("count", MAX_EVENTS)
        for (i in 0 until previous) {
            PendingIntent.getBroadcast(context, BASE_CODE + i, Intent(context, AlarmReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                ?.let { am.cancel(it); it.cancel() }
        }
        val now = System.currentTimeMillis()
        val list = if (settings.alarms) taskEvents(tasks, now, settings).filter { it.at > now }.toMutableList() else mutableListOf()
        list += dayEvents(tasks, now, settings).filter { it.at > now }
        val tomorrow = Clock.epoch(Clock.today().plusDays(1), 1)
        list += Event(tomorrow, "", "", null, false, silent = true)
        // Sleep time: the day ends; ask what to do with what is left.
        val sleepAt = Clock.epoch(Clock.today(), settings.dayEnd)
        val left = tasks.count { it.open }
        if (sleepAt > now && left > 0) list += Event(sleepAt, "انتهى يومك", "$left مهام ما خلصت", null, false, close = true)
        list.forEachIndexed { i, e ->
            val intent = Intent(context, AlarmReceiver::class.java)
                .putExtra("title", e.title).putExtra("text", e.text).putExtra("task", e.taskId)
                .putExtra("start", e.canStart).putExtra("silent", e.silent).putExtra("id", BASE_CODE + i).putExtra("close", e.close)
                .putExtra("day", Clock.today().toString())
                .putExtra("insistent", settings.insistent)
                .putExtra("kind", e.kind).putExtra("period", e.period)
            val pi = PendingIntent.getBroadcast(context, BASE_CODE + i, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            try {
                if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.at, pi)
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.at, pi)
            }
        }
        prefs.edit().putInt("count", list.size).apply()
        timerNotification(context, tasks.firstOrNull { it.running }, now)
        PlannerWidget.update(context, tasks, settings)
    }

    /**
     * Task alarms. In prayer mode tasks have no set start, so only the running task's end (and its rest/sets)
     * and tasks with a fixed time ring. Nothing rings between sleep and wake-up.
     */
    fun taskEvents(tasks: List<Task>, now: Long, s: Settings): List<Event> {
        val all = events(if (s.prayerMode) tasks.filter { it.running || it.pinned } else tasks, now)
        return all.filter { e -> e.taskId != null && tasks.any { it.id == e.taskId && it.running } || !s.isNight(Clock.minuteOfDay(e.at)) }
    }

    /** Prayer alarms, the wake-up summary and the period-end reminders of today (and tomorrow's fajr). */
    fun dayEvents(tasks: List<Task>, now: Long, s: Settings): List<Event> {
        val out = mutableListOf<Event>()
        if (!s.prayerMode && s.prayerAlarms.isEmpty()) return out
        val today = Clock.today()
        for (d in listOf(today, today.plusDays(1))) {
            val p = s.prayersOn(d)
            for (k in Prayer.keys) {
                if (d != today && k != "fajr") continue
                val at = Clock.epoch(d, p.at(k))
                val name = Prayer.names.getValue(k)
                val before = s.alarmBefore(k)
                if (before > 0) out += Event(at - before * 60_000L, "صلاة $name بعد $before دقيقة", "الساعة ${fmt12(p.at(k))}", null, false, kind = "prayer")
                if (s.alarmAtAdhan(k)) out += Event(at, "حان وقت صلاة $name", fmt12(p.at(k)), null, false, kind = "prayer")
            }
        }
        if (!s.prayerMode) return out
        val periods = s.periodsOn(today)
        if (s.morningNote) {
            val counts = periods.mapNotNull { pr -> tasks.count { it.open && it.period == pr.key }.takeIf { it > 0 }?.let { "${pr.name} $it" } }
            if (counts.isNotEmpty()) out += Event(Clock.epoch(today, s.dayStart), "صباح الخير، هاي خطة اليوم", counts.joinToString(" · "), null, false, kind = "note")
        }
        if (s.periodNote) for (pr in periods) {
            val left = tasks.filter { it.open && !it.running && it.period == pr.key }
            if (left.isEmpty() || pr.end >= s.dayEnd) continue
            out += Event(Clock.epoch(today, pr.end - 5), "خلصت فترة ${pr.name} وباقي ${left.size}", left.take(3).joinToString("، ") { it.baseTitle },
                null, false, kind = "period", period = pr.key)
        }
        return out
    }

    fun prayerRing(context: Context, id: Int, title: String, text: String) {
        channels(context)
        val n = NotificationCompat.Builder(context, PRAYER_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true).setContentIntent(openApp(context)).setFullScreenIntent(openApp(context), true)
            .addAction(0, "إيقاف", ActionReceiver.intent(context, ActionReceiver.DISMISS, null, id))
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        try { NotificationManagerCompat.from(context).notify(id, n) } catch (_: SecurityException) {}
    }

    fun note(context: Context, id: Int, title: String, text: String, period: String) {
        channels(context)
        val b = NotificationCompat.Builder(context, NOTE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(openApp(context))
        if (period.isNotEmpty()) {
            b.addAction(0, "للفترة الجاية", ActionReceiver.intent(context, ActionReceiver.PERIOD_NEXT, period, id))
            b.addAction(0, "لبكرا", ActionReceiver.intent(context, ActionReceiver.PERIOD_TOMORROW, period, id))
        }
        try { NotificationManagerCompat.from(context).notify(id, b.build()) } catch (_: SecurityException) {}
    }

    private fun timerNotification(context: Context, running: Task?, now: Long) {
        val nm = NotificationManagerCompat.from(context)
        if (running == null) { nm.cancel(TIMER_NOTIFICATION); return }
        channels(context)
        val end = now + Maths.remaining(running, now)
        val n = NotificationCompat.Builder(context, TIMER_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(running.title)
            .setContentText("المؤقت جارٍ")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(end).setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true)
            .setContentIntent(openApp(context))
            .addAction(0, "إيقاف مؤقت", ActionReceiver.intent(context, ActionReceiver.TOGGLE, running.id, TIMER_NOTIFICATION))
            .addAction(0, "إنهاء", ActionReceiver.intent(context, ActionReceiver.FINISH, running.id, TIMER_NOTIFICATION))
            .build()
        try { nm.notify(TIMER_NOTIFICATION, n) } catch (_: SecurityException) {}
    }

    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** End of day: two buttons, carry or close. Without an answer the default from the settings is used later. */
    fun closePrompt(context: Context, day: String, text: String) {
        channels(context)
        val n = NotificationCompat.Builder(context, alarmChannel(context))
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("انتهى يومك")
            .setContentText("$text · ترحّلها لبكرا ولا تسكّرها؟")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .addAction(0, "ترحيل لبكرا", ActionReceiver.intent(context, ActionReceiver.CLOSE_CARRY, day, CLOSE_ID))
            .addAction(0, "إغلاق اليوم", ActionReceiver.intent(context, ActionReceiver.CLOSE_SHUT, day, CLOSE_ID))
            .build()
        try { NotificationManagerCompat.from(context).notify(CLOSE_ID, n) } catch (_: SecurityException) {}
    }

    private const val CLOSE_ID = 4

    fun ring(context: Context, id: Int, title: String, text: String, taskId: String?, canStart: Boolean, insistent: Boolean) {
        channels(context)
        val b = NotificationCompat.Builder(context, alarmChannel(context))
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .setFullScreenIntent(openApp(context), true)
            .addAction(0, "إيقاف", ActionReceiver.intent(context, ActionReceiver.DISMISS, taskId, id))
        if (canStart && taskId != null) b.addAction(0, "بدء", ActionReceiver.intent(context, ActionReceiver.START, taskId, id))
        val n = b.build()
        if (insistent) n.flags = n.flags or Notification.FLAG_INSISTENT
        try { NotificationManagerCompat.from(context).notify(id, n) } catch (_: SecurityException) {}
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("silent", false)) {
            // A new day: yesterday's reminders are not needed any more.
            runCatching { NotificationManagerCompat.from(context).cancelAll() }
            val pending = goAsync()
            Alarms.scope.launch {
                try { Repository.get(context).refreshDay() } finally { pending.finish() }
            }
            return
        }
        if (intent.getBooleanExtra("close", false)) {
            Alarms.closePrompt(context, intent.getStringExtra("day") ?: Clock.today().toString(), intent.getStringExtra("text") ?: "")
            return
        }
        when (intent.getStringExtra("kind")) {
            "prayer" -> { Alarms.prayerRing(context, intent.getIntExtra("id", 2), intent.getStringExtra("title") ?: "", intent.getStringExtra("text") ?: ""); return }
            "note", "period" -> { Alarms.note(context, intent.getIntExtra("id", 2), intent.getStringExtra("title") ?: "", intent.getStringExtra("text") ?: "", intent.getStringExtra("period") ?: ""); return }
        }
        Alarms.ring(
            context, intent.getIntExtra("id", 2), intent.getStringExtra("title") ?: "", intent.getStringExtra("text") ?: "",
            intent.getStringExtra("task"), intent.getBooleanExtra("start", false), intent.getBooleanExtra("insistent", true)
        )
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("notification", 0)
        NotificationManagerCompat.from(context).cancel(id)
        val task = intent.getStringExtra("task")
        val action = intent.action ?: return
        if (action == DISMISS || task == null) return
        val pending = goAsync()
        Alarms.scope.launch {
            try {
                val repo = Repository.get(context)
                val t = repo.store.task(task)
                when (action) {
                    CLOSE_CARRY -> repo.closeDay("carry", java.time.LocalDate.parse(task))
                    CLOSE_SHUT -> repo.closeDay("close", java.time.LocalDate.parse(task))
                    START -> if (t != null && !t.running) repo.startNow(task)
                    PERIOD_NEXT -> repo.movePeriodTasks(task, toTomorrow = false)
                    PERIOD_TOMORROW -> repo.movePeriodTasks(task, toTomorrow = true)
                    TOGGLE -> repo.timer(task)
                    FINISH -> repo.finish(task)
                }
            } finally { pending.finish() }
        }
    }

    companion object {
        const val DISMISS = "com.imad.planner.DISMISS"
        const val START = "com.imad.planner.START"
        const val TOGGLE = "com.imad.planner.TOGGLE"
        const val FINISH = "com.imad.planner.FINISH"
        const val CLOSE_CARRY = "com.imad.planner.CLOSE_CARRY"
        const val CLOSE_SHUT = "com.imad.planner.CLOSE_SHUT"
        const val PERIOD_NEXT = "com.imad.planner.PERIOD_NEXT"
        const val PERIOD_TOMORROW = "com.imad.planner.PERIOD_TOMORROW"

        fun intent(context: Context, action: String, task: String?, notification: Int): PendingIntent {
            val i = Intent(context, ActionReceiver::class.java).setAction(action)
                .putExtra("task", task).putExtra("notification", notification)
            return PendingIntent.getBroadcast(
                context, (action.hashCode() * 31 + notification), i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Alarms.scope.launch {
            try { Repository.get(context).refreshDay() } finally { pending.finish() }
        }
    }
}
