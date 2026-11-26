package com.imad.planner

import org.json.JSONArray
import org.json.JSONObject

object Plans {
    fun parse(text: String?): List<Plan> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching {
            val a = JSONArray(text)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Plan(
                    id = o.getString("id"), title = o.getString("title"), kind = o.getString("kind"),
                    unit = o.optString("unit", "صفحة"), total = o.optInt("total", MUSHAF_PAGES), done = o.optInt("done"),
                    perDay = o.optInt("perDay"), endDay = o.optString("endDay", Clock.today().plusYears(1).toString()),
                    minutesPerUnit = o.optInt("minutesPerUnit", 5), cycleDays = o.optInt("cycleDays", 7),
                    pointer = o.optInt("pointer", 1), startPage = o.optInt("startPage", 1),
                    accepted = o.optBoolean("accepted"), configured = o.optBoolean("configured"),
                    dayKey = o.optString("dayKey"), dayDone = o.optInt("dayDone"), dayQuota = o.optInt("dayQuota"),
                    category = o.optString("category"), stages = o.optString("stages"), perWeek = o.optDouble("perWeek", 1.0),
                    khatmaDays = o.optInt("khatmaDays"), startDay = o.optString("startDay"), startDone = o.optInt("startDone"),
                    content = o.optString("content"), contentMonth = o.optString("contentMonth"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encode(list: List<Plan>): String {
        val a = JSONArray()
        list.forEach { p ->
            a.put(
                JSONObject().put("id", p.id).put("title", p.title).put("kind", p.kind).put("unit", p.unit)
                    .put("total", p.total).put("done", p.done).put("perDay", p.perDay).put("endDay", p.endDay)
                    .put("minutesPerUnit", p.minutesPerUnit).put("cycleDays", p.cycleDays).put("pointer", p.pointer)
                    .put("startPage", p.startPage).put("accepted", p.accepted).put("configured", p.configured)
                    .put("dayKey", p.dayKey).put("dayDone", p.dayDone).put("dayQuota", p.dayQuota)
                    .put("category", p.category).put("stages", p.stages).put("perWeek", p.perWeek)
                    .put("khatmaDays", p.khatmaDays).put("startDay", p.startDay).put("startDone", p.startDone)
                    .put("content", p.content).put("contentMonth", p.contentMonth)
            )
        }
        return a.toString()
    }
}
