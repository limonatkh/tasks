package com.imad.planner

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.launch

/** Home-screen widget: the current prayer period and its open tasks, each with a start button. */
class PlannerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Alarms.scope.launch {
            try { Repository.get(context).refreshWidget() } finally { pending.finish() }
        }
    }

    companion object {
        private val rows = listOf(
            Triple(R.id.row0, R.id.name0, R.id.start0), Triple(R.id.row1, R.id.name1, R.id.start1),
            Triple(R.id.row2, R.id.name2, R.id.start2), Triple(R.id.row3, R.id.name3, R.id.start3),
        )

        fun update(context: Context, todays: List<Task>, s: Settings) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = runCatching { manager.getAppWidgetIds(ComponentName(context, PlannerWidget::class.java)) }.getOrNull() ?: return
            if (ids.isEmpty()) return
            val now = System.currentTimeMillis()
            val minute = Clock.minuteOfDay(now)
            val today = Clock.today()
            val v = RemoteViews(context.packageName, R.layout.widget)
            v.setOnClickPendingIntent(R.id.root, Alarms.openApp(context))
            val running = todays.firstOrNull { it.running }
            val list: List<Task>
            if (s.prayerMode) {
                val key = s.periodAt(today, minute)
                v.setTextViewText(R.id.title, "فترة ${Prayer.names[key]}")
                list = todays.filter { it.open && it.period == key }.sortedBy { if (it.running) 0L else if (it.planned == 0L) Long.MAX_VALUE else it.planned }
            } else {
                v.setTextViewText(R.id.title, "مهام اليوم")
                list = todays.filter { it.open }.sortedBy { if (it.planned == 0L) Long.MAX_VALUE else it.planned }
            }
            v.setTextViewText(R.id.sub, when {
                s.isNight(minute) -> "وقت النوم"
                running != null -> "شغّالة: ${running.title}"
                list.isEmpty() -> "خلصت مهام هاي الفترة"
                else -> "${list.size} مهام باقية"
            })
            rows.forEachIndexed { i, (row, name, start) ->
                val t = list.getOrNull(i)
                if (t == null || s.isNight(minute)) { v.setViewVisibility(row, View.GONE); return@forEachIndexed }
                v.setViewVisibility(row, View.VISIBLE)
                v.setTextViewText(name, (if (t.running) "▶ " else "") + t.title + " · " + durLabel(t.minutes))
                v.setViewVisibility(start, if (t.running) View.GONE else View.VISIBLE)
                v.setOnClickPendingIntent(start, ActionReceiver.intent(context, ActionReceiver.START, t.id, 9000 + i))
            }
            manager.updateAppWidget(ids, v)
        }
    }
}
