package com.glidvio.app.data

import org.junit.Assert.*
import org.junit.Test

class VideoLinksTest {
    private val id = "dQw4w9WgXcQ"
    @Test fun acceptsSupportedLinksAndSharedText() {
        listOf("https://www.youtube.com/watch?v=$id&t=4", "https://youtu.be/$id?si=abc", "https://m.youtube.com/shorts/$id", "https://youtube.com/live/$id", "Watch this https://youtu.be/$id", id).forEach {
            assertEquals(it, id, VideoLinks.videoId(it))
        }
    }
    @Test fun rejectsUntrustedHostsAndMalformedIds() {
        listOf("https://youtube.com.evil.test/watch?v=$id", "https://evil.test/$id", "https://youtube.com@evil.test/watch?v=$id", "https://youtube.com/watch?v=short", "https://youtube.com/watch?v=$id/evil", "search text", "https://youtu.be/", "file:///etc/passwd").forEach { assertNull(it, VideoLinks.videoId(it)) }
    }
    @Test fun canonicalizesToHttpsWatchUrl() { assertEquals("https://www.youtube.com/watch?v=$id", VideoLinks.canonical("http://youtu.be/$id")) }
}
