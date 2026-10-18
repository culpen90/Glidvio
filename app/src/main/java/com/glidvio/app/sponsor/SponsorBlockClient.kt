package com.glidvio.app.sponsor

import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException

/**
 * Read-only SponsorBlock client. Only a four-character SHA-256 prefix leaves the
 * device; matching the full video ID happens locally. Community coverage varies.
 * API: https://wiki.sponsor.ajay.app/w/API_Docs
 */
class SponsorBlockClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun load(
        videoId: String,
        categories: Set<SponsorCategory> = SponsorCategory.defaults,
    ): SponsorLoadResult {
        if (!VIDEO_ID.matches(videoId)) {
            return SponsorLoadResult.Unavailable("SponsorBlock needs a valid YouTube video ID.")
        }
        if (categories.isEmpty()) return SponsorLoadResult.Available(emptyList())

        val url = "https://sponsor.ajay.app/api/skipSegments/${hashPrefix(videoId)}".toHttpUrl()
            .newBuilder()
            .addQueryParameter("categories", JSONArray(categories.sortedBy { it.apiName }.map { it.apiName }).toString())
            .addQueryParameter("actionTypes", "[\"skip\"]")
            .addQueryParameter("service", "YouTube")
            .build()
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", "Glidvio/1.0").build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(
                        SponsorLoadResult.Unavailable("SponsorBlock is unavailable. Sponsor skipping is not active."),
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        when {
                            it.code == 404 -> SponsorLoadResult.Available(emptyList())
                            !it.isSuccessful -> SponsorLoadResult.Unavailable(
                                "SponsorBlock returned HTTP ${it.code}. Sponsor skipping is not active.", it.code,
                            )
                            else -> try {
                                val body = it.body ?: throw IOException("Empty response")
                                if (body.contentLength() > MAX_RESPONSE_BYTES) throw IOException("Response too large")
                                val source = body.source()
                                if (source.request(MAX_RESPONSE_BYTES + 1)) throw IOException("Response too large")
                                SponsorLoadResult.Available(parseResponse(videoId, source.readUtf8(), categories))
                            } catch (_: IOException) {
                                SponsorLoadResult.Unavailable("SponsorBlock could not be read. Sponsor skipping is not active.")
                            } catch (_: JSONException) {
                                SponsorLoadResult.Unavailable("SponsorBlock returned invalid data. Sponsor skipping is not active.")
                            }
                        }
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }
    }

    companion object {
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
        private const val MAX_RESPONSE_BYTES = 1_048_576L

        internal fun hashPrefix(videoId: String): String = MessageDigest.getInstance("SHA-256")
            .digest(videoId.toByteArray(Charsets.UTF_8))
            .take(2).joinToString("") { "%02x".format(it.toInt() and 0xff) }

        internal fun parseResponse(
            videoId: String,
            response: String,
            categories: Set<SponsorCategory>,
        ): List<Segment> {
            val buckets = JSONArray(response)
            val segments = mutableListOf<Segment>()
            for (index in 0 until buckets.length()) {
                val bucket = buckets.optJSONObject(index) ?: continue
                if (bucket.optString("videoID") != videoId) continue
                val rows = bucket.getJSONArray("segments")
                for (rowIndex in 0 until rows.length()) {
                    val row = rows.optJSONObject(rowIndex) ?: continue
                    // Ignore mute/full/poi/chapter actions even if the server sends them.
                    if (row.optString("actionType", "skip") != "skip") continue
                    val category = SponsorCategory.fromApiName(row.optString("category")) ?: continue
                    if (category !in categories) continue
                    val times = row.optJSONArray("segment") ?: continue
                    if (times.length() != 2) continue
                    val segment = Segment(
                        times.optDouble(0, Double.NaN), times.optDouble(1, Double.NaN),
                        category, row.optString("UUID"),
                    )
                    if (segment.isValid) segments += segment
                }
            }
            return segments.distinct().sortedWith(compareBy<Segment> { it.startSeconds }
                .thenBy { it.endSeconds }.thenBy { it.uuid }.thenBy { it.category.apiName })
        }
    }
}
