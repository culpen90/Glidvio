package com.glidvio.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalLibrary(context: Context) {
    private val prefs = context.getSharedPreferences("glidvio", Context.MODE_PRIVATE)
    fun read(key: String): List<VideoItem> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            VideoItem(id = item.getString("id"), title = item.getString("title"), channel = item.optString("channel"),
                thumbnailUrl = item.optString("thumbnail"), durationSeconds = item.optLong("duration"),
                viewCount = item.optLong("views"), url = item.getString("url"))
        }
    }.getOrDefault(emptyList())
    fun write(key: String, videos: List<VideoItem>) {
        val array = JSONArray()
        val storedVideos = if (key == "history") videos.take(200) else videos
        storedVideos.forEach { video -> array.put(JSONObject().put("id", video.id).put("title", video.title)
            .put("channel", video.channel).put("thumbnail", video.thumbnailUrl).put("duration", video.durationSeconds)
            .put("views", video.viewCount).put("url", video.url)) }
        prefs.edit().putString(key, array.toString()).apply()
    }
    fun categories(): Set<String>? = prefs.getStringSet("categories", null)?.toSet()
    fun setCategories(values: Set<String>) { prefs.edit().putStringSet("categories", values).apply() }
    fun clearHistory() { prefs.edit().remove("history").apply() }
}
