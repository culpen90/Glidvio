package com.glidvio.app.data

import java.net.URI
import java.net.URLDecoder

object VideoLinks {
    private val idPattern = Regex("[A-Za-z0-9_-]{11}")
    fun videoId(input: String): String? {
        val text = input.trim()
        if (idPattern.matches(text)) return text
        val candidate = Regex("https?://[^\\s<>]+").find(text)?.value ?: return null
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("https", "http") || uri.userInfo != null) return null
        val host = uri.host?.lowercase() ?: return null
        val path = uri.path.orEmpty().trim('/').split('/')
        val id = when (host) {
            "youtu.be" -> path.firstOrNull()
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" -> when (path.firstOrNull()) {
                "watch" -> uri.rawQuery.orEmpty().split('&').firstNotNullOfOrNull { pair ->
                    val parts = pair.split('=', limit = 2)
                    if (parts.size == 2 && parts[0] == "v") runCatching { URLDecoder.decode(parts[1], "UTF-8") }.getOrNull() else null
                }
                "shorts", "live", "embed" -> path.getOrNull(1)
                else -> null
            }
            else -> null
        }
        return id?.takeIf(idPattern::matches)
    }
    fun canonical(input: String): String? = videoId(input)?.let { "https://www.youtube.com/watch?v=$it" }
}
