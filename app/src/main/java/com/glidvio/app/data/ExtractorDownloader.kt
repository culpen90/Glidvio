// SPDX-License-Identifier: GPL-3.0-or-later
package com.glidvio.app.data

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/** Network transport shared by the extractor and SponsorBlock; it stores no account cookies. */
class ExtractorDownloader(private val httpClient: OkHttpClient = client) : Downloader() {
    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .header("User-Agent", USER_AGENT)

        // Extractor headers identify the particular YouTube client. They must override defaults.
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        val method = request.httpMethod()
        val body = request.dataToSend()?.toRequestBody()
            ?: if (method in setOf("POST", "PUT", "PATCH")) ByteArray(0).toRequestBody() else null
        builder.method(method, body)

        return httpClient.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException(
                    "YouTube is limiting requests on this connection. Please try again later.",
                    request.url(),
                )
            }
            Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string().orEmpty(),
                response.request.url.toString(),
            )
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Mobile Safari/537.36"

        val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
