package com.imad.planner

import org.json.JSONArray
import org.json.JSONObject

/** A followed account and when it was added (only posts after that show). */
data class IgPerson(val user: String, val addedAt: Long)

/** One post or reel in the feed. state: new | kept | gone. */
data class IgPost(val id: String, val user: String, val code: String, val image: String, val video: String, val at: Long, val caption: String, val state: String = "new")

/**
 * Following chosen people without opening Instagram. Login is on Instagram's own page (no password is kept);
 * when the feed opens on Wi-Fi it asks Instagram, with that login, for each person's latest posts and keeps the
 * ones newer than the moment the person was added.
 */
object Ig {
    const val APP_ID = "936619743392459"

    const val UA = "Mozilla/5.0 (Linux; Android 13; SM-M135F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"


    fun username(text: String): String? {
        val t = text.trim()
        Regex("instagram\\.com/(?:stories/)?([A-Za-z0-9._]{1,30})", RegexOption.IGNORE_CASE).find(t)?.let { m ->
            val u = m.groupValues[1].lowercase()
            if (u !in reserved) return u
        }
        val plain = t.removePrefix("@").lowercase()
        return plain.takeIf { Regex("^[a-z0-9._]{1,30}$").matches(it) && it !in reserved }
    }


    private val reserved = setOf("p", "reel", "reels", "explore", "accounts", "direct", "stories", "tv", "about", "legal")


    fun parse(text: String?): List<String> = runCatching {
        val a = JSONArray(text ?: "[]"); (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())


    fun encode(list: List<String>) = JSONArray(list).toString()


    fun people(text: String?): List<IgPerson> = runCatching {
        val a = JSONArray(text ?: "[]"); (0 until a.length()).map { a.getJSONObject(it).let { o -> IgPerson(o.getString("u"), o.optLong("at")) } }
    }.getOrDefault(emptyList())


    fun encodePeople(list: List<IgPerson>) = JSONArray().apply { list.forEach { put(JSONObject().put("u", it.user).put("at", it.addedAt)) } }.toString()


    fun posts(text: String?): List<IgPost> = runCatching {
        val a = JSONArray(text ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it).let { o -> IgPost(o.getString("id"), o.optString("u"), o.optString("c"), o.optString("i"), o.optString("v"), o.optLong("t"), o.optString("cap"), o.optString("s", "new")) } }
    }.getOrDefault(emptyList())


    fun encodePosts(list: List<IgPost>) = JSONArray().apply {
        list.forEach { put(JSONObject().put("id", it.id).put("u", it.user).put("c", it.code).put("i", it.image).put("v", it.video).put("t", it.at).put("cap", it.caption).put("s", it.state)) }
    }.toString()


    /** Turns Instagram's answer for one account into posts (null when it is not the expected answer). */
    fun parsePosts(user: String, body: String): List<IgPost>? = runCatching {
        val o = JSONObject(body)
        val edges = o.getJSONObject("data").getJSONObject("user").getJSONObject("edge_owner_to_timeline_media").getJSONArray("edges")
        (0 until edges.length()).map { i ->
            val n = edges.getJSONObject(i).getJSONObject("node")
            val cap = n.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")?.optJSONObject(0)?.optJSONObject("node")?.optString("text") ?: ""
            IgPost(n.getString("id"), user, n.optString("shortcode"), n.optString("display_url"), if (n.optBoolean("is_video")) n.optString("video_url") else "",
                n.optLong("taken_at_timestamp") * 1000, cap.take(300))
        }
    }.getOrNull()


    /** What Instagram's answer code means, in words. */
    fun why(code: Int): String = when (code) {
        0 -> "ما في اتصال بإنستغرام"
        401, 403 -> "إنستغرام بدّه تسجيل دخول من جديد ($code)"
        404 -> "الحساب مش موجود أو الاسم غلط (404)"
        429 -> "إنستغرام طالب تستنى شوي قبل التحميل (429)"
        else -> "إنستغرام ردّ برقم $code"
    }

}

/** One account's answer from Instagram: HTTP code and body. */
class IgAnswer(val code: Int, val body: String)
