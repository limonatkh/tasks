package com.imad.planner

/** The six domains of the app. Colours are added on each platform (Android: Art.kt). */
enum class Domain(val title: String) {
    Quran("القرآن"),
    Reading("القراءة"),
    Sport("الرياضة"),
    Content("المحتوى"),
    Work("الشغل"),
    Other("أخرى"),
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
