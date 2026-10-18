package com.glidvio.app.sponsor

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorBlockClientTest {
    private val videoId = "dQw4w9WgXcQ"

    private fun client(status: Int, body: String, inspect: (Request) -> Unit = {}): SponsorBlockClient =
        SponsorBlockClient(OkHttpClient.Builder().addInterceptor { chain ->
            inspect(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("test")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build())

    @Test fun `request contains hash prefix and selected categories without full video id`() = runBlocking {
        var captured: Request? = null
        val result = client(200, "[]") { captured = it }.load(videoId, setOf(SponsorCategory.SPONSOR, SponsorCategory.INTRO))
        assertTrue(result is SponsorLoadResult.Available)
        val request = captured!!
        assertEquals("https", request.url.scheme)
        assertEquals("sponsor.ajay.app", request.url.host)
        assertEquals("/api/skipSegments/5f6b", request.url.encodedPath)
        assertEquals("[\"intro\",\"sponsor\"]", request.url.queryParameter("categories"))
        assertEquals("[\"skip\"]", request.url.queryParameter("actionTypes"))
        assertFalse(request.url.toString().contains(videoId))
        assertFalse(request.headers.toString().contains(videoId))
    }

    @Test fun `hash prefix uses four lowercase hexadecimal characters`() {
        assertEquals("5f6b", SponsorBlockClient.hashPrefix(videoId))
        assertEquals("e3b0", SponsorBlockClient.hashPrefix(""))
    }

    @Test fun `hash collision buckets do not leak another videos segments into playback`() {
        val body = """
            [
              {"videoID":"otherVideo1","segments":[{"segment":[0,999],"UUID":"wrong","category":"sponsor","actionType":"skip"}]},
              {"videoID":"$videoId","segments":[{"segment":[20,30],"UUID":"right","category":"sponsor","actionType":"skip"}]}
            ]
        """.trimIndent()
        val segments = SponsorBlockClient.parseResponse(videoId, body, SponsorCategory.defaults)
        assertEquals(listOf(Segment(20.0, 30.0, SponsorCategory.SPONSOR, "right")), segments)
    }

    @Test fun `malformed ranges unknown categories and non skip actions never cause seeks`() {
        val body = """
            [{"videoID":"$videoId","segments":[
              {"segment":[-1,20],"UUID":"negative","category":"sponsor"},
              {"segment":[10,10],"UUID":"empty","category":"sponsor"},
              {"segment":[20,10],"UUID":"reversed","category":"sponsor"},
              {"segment":[10,20,30],"UUID":"extra","category":"sponsor"},
              {"segment":[null,20],"UUID":"null","category":"sponsor"},
              {"segment":[0,20],"UUID":"unknown","category":"new_category"},
              {"segment":[0,20],"UUID":"mute","category":"sponsor","actionType":"mute"},
              {"segment":[0,20],"UUID":"disabled","category":"intro"},
              {"segment":[0,20],"category":"sponsor"},
              {"segment":[30,40],"UUID":"valid","category":"sponsor","actionType":"skip"}
            ]}]
        """.trimIndent()
        assertEquals(listOf("valid"), SponsorBlockClient.parseResponse(videoId, body, SponsorCategory.defaults).map { it.uuid })
    }

    @Test fun `not found means no community submissions while service errors are visible`() = runBlocking {
        assertEquals(SponsorLoadResult.Available(emptyList()), client(404, "missing").load(videoId))
        val error = client(503, "down").load(videoId) as SponsorLoadResult.Unavailable
        assertEquals(503, error.httpStatus)
        assertTrue(error.message.contains("not active"))
    }

    @Test fun `invalid response is unavailable rather than falsely reporting no sponsors`() = runBlocking {
        assertTrue(client(200, "<html>broken</html>").load(videoId) is SponsorLoadResult.Unavailable)
        assertTrue(client(200, "[{\"videoID\":\"$videoId\"}]").load(videoId) is SponsorLoadResult.Unavailable)
    }

    @Test fun `empty categories and invalid ids do not make network requests`() = runBlocking {
        var requests = 0
        val service = client(200, "[]") { requests++ }
        assertEquals(SponsorLoadResult.Available(emptyList()), service.load(videoId, emptySet()))
        assertTrue(service.load("https://youtu.be/$videoId") is SponsorLoadResult.Unavailable)
        assertEquals(0, requests)
    }

    @Test fun `oversized service response is unavailable`() = runBlocking {
        assertTrue(client(200, " ".repeat(1_048_577)).load(videoId) is SponsorLoadResult.Unavailable)
    }

    @Test fun `network failures remain distinct from missing community submissions`() = runBlocking {
        val service = SponsorBlockClient(OkHttpClient.Builder().addInterceptor {
            throw IOException("Simulated offline connection")
        }.build())
        val result = service.load(videoId) as SponsorLoadResult.Unavailable
        assertTrue(result.message.contains("not active"))
    }

    @Test fun `cancelling the load cancels its HTTP call`() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var httpCall: Call? = null
        val service = SponsorBlockClient(OkHttpClient.Builder().addInterceptor { chain ->
            httpCall = chain.call()
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            throw IOException("Test request released")
        }.build())
        val pending = async(Dispatchers.Default) { service.load(videoId) }
        try {
            assertTrue("HTTP call did not start", entered.await(5, TimeUnit.SECONDS))
            pending.cancelAndJoin()
            assertTrue(httpCall!!.isCanceled())
        } finally {
            pending.cancel()
            release.countDown()
        }
    }

    @Test(expected = JSONException::class)
    fun `parser rejects a non array response`() {
        SponsorBlockClient.parseResponse(videoId, "{}", SponsorCategory.defaults)
    }
}
