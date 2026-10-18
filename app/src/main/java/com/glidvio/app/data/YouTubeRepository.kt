// SPDX-License-Identifier: GPL-3.0-or-later
package com.glidvio.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.search.SearchExtractor.NothingFoundException
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeTrendingMusicLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import org.jsoup.Jsoup
import java.io.IOException
import java.util.Locale

data class VideoItem(
    val id: String,
    val title: String = "",
    val channel: String = "",
    val thumbnailUrl: String = "",
    val durationSeconds: Long = 0,
    val viewCount: Long = -1,
    val url: String = "https://www.youtube.com/watch?v=$id",
    val channelUrl: String = "",
)

data class VideoPage(
    val items: List<VideoItem>,
    val nextPage: Page? = null,
    val suggestion: String? = null,
)

data class SubtitleTrack(
    val url: String,
    val languageTag: String,
    val label: String,
    val mimeType: String,
)

data class PlaybackInfo(
    val id: String,
    val url: String,
    val title: String,
    val channel: String,
    val description: String,
    val thumbnailUrl: String,
    val videoUrl: String,
    val audioUrl: String? = null,
    val mimeType: String? = null,
    val audioMimeType: String? = null,
    val durationSeconds: Long = 0,
    val related: List<VideoItem> = emptyList(),
    val qualityLabel: String = "Auto",
    val subtitles: List<SubtitleTrack> = emptyList(),
    val httpHeaders: Map<String, String> = mapOf("User-Agent" to ExtractorDownloader.USER_AGENT),
)

/** Uses content streams directly; no embedded YouTube player, advertising SDK, or login is used. */
class YouTubeRepository {
    private val service = ServiceList.YouTube

    init {
        initializeExtractor()
    }

    suspend fun search(query: String, page: Page? = null): VideoPage = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        require(trimmed.isNotEmpty()) { "Enter a search or paste a YouTube link." }
        ensureActive()
        val handler = service.searchQHFactory.fromQuery(
            trimmed,
            listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
            "",
        )
        val result = if (Page.isValid(page)) {
            val info = SearchInfo.getMoreItems(service, handler, page)
            checkPageErrors(info.items, info.errors)
            VideoPage(toVideos(info.items), info.nextPage.takeIf(Page::isValid))
        } else {
            val info = SearchInfo.getInfo(service, handler)
            checkPageErrors(info.relatedItems, info.errors)
            VideoPage(
                toVideos(info.relatedItems),
                info.nextPage.takeIf(Page::isValid),
                info.searchSuggestion?.takeIf(String::isNotBlank),
            )
        }
        ensureActive()
        result
    }

    /** YouTube removed its general Trending page; this feed uses its current music charts. */
    suspend fun trending(page: Page? = null): VideoPage = withContext(Dispatchers.IO) {
        ensureActive()
        val extractor = service.kioskList.getExtractorById(
            YoutubeTrendingMusicLinkHandlerFactory.KIOSK_ID,
            page,
        )
        val result = if (Page.isValid(page)) {
            val info = extractor.getPage(page)
            checkPageErrors(info.items, info.errors)
            VideoPage(toVideos(info.items), info.nextPage.takeIf(Page::isValid))
        } else {
            extractor.fetchPage()
            val info = KioskInfo.getInfo(extractor)
            checkPageErrors(info.relatedItems, info.errors)
            VideoPage(toVideos(info.relatedItems), info.nextPage.takeIf(Page::isValid))
        }
        ensureActive()
        result
    }

    suspend fun related(url: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(service, normalizeVideoUrl(url))
        ensureActive()
        toVideos(info.relatedItems)
    }

    suspend fun loadPlayback(url: String): PlaybackInfo = withContext(Dispatchers.IO) {
        ensureActive()
        val normalizedUrl = normalizeVideoUrl(url)
        val info = StreamInfo.getInfo(service, normalizedUrl)
        ensureActive()

        val audio = info.audioStreams.orEmpty().filter(::isProgressiveUrl).maxWithOrNull(
            compareBy<AudioStream> { it.audioTrackType == AudioTrackType.ORIGINAL }
                .thenBy { it.audioTrackType == null }
                .thenBy { it.format?.mimeType == "audio/mp4" }
                .thenBy { it.averageBitrate },
        )
        val muxed = bestVideo(info.videoStreams.orEmpty().filter(::isProgressiveUrl))
        val videoOnly = if (audio != null) {
            bestVideo(info.videoOnlyStreams.orEmpty().filter(::isProgressiveUrl))
        } else null
        val selectedVideo = if (videoOnly != null &&
            (muxed == null || resolutionHeight(videoOnly) > resolutionHeight(muxed))
        ) videoOnly else muxed
        val selectedAudio = if (selectedVideo?.isVideoOnly() == true) audio else null

        val selectedUrl: String
        val mimeType: String?
        val quality: String
        if (selectedVideo != null) {
            selectedUrl = selectedVideo.content
            mimeType = selectedVideo.format?.mimeType
            quality = selectedVideo.getResolution()
        } else if (!info.hlsUrl.isNullOrBlank()) {
            selectedUrl = info.hlsUrl
            mimeType = "application/x-mpegURL"
            quality = "Auto · HLS"
        } else if (!info.dashMpdUrl.isNullOrBlank()) {
            selectedUrl = info.dashMpdUrl
            mimeType = "application/dash+xml"
            quality = "Auto · DASH"
        } else {
            throw IOException("YouTube did not provide a playable video stream. Try another video or retry later.")
        }

        PlaybackInfo(
            id = info.id,
            url = normalizedUrl,
            title = info.name.orEmpty(),
            channel = info.uploaderName.orEmpty(),
            description = info.description?.let {
                if (it.type == Description.HTML) Jsoup.parse(it.content).text() else it.content
            }.orEmpty(),
            thumbnailUrl = thumbnail(info.thumbnails),
            videoUrl = selectedUrl,
            audioUrl = selectedAudio?.content,
            mimeType = mimeType,
            audioMimeType = selectedAudio?.format?.mimeType,
            durationSeconds = info.duration.coerceAtLeast(0),
            related = toVideos(info.relatedItems),
            qualityLabel = quality,
            subtitles = info.subtitles.orEmpty()
                .filter { it.isUrl && it.content.startsWith("https://") }
                .mapNotNull { track ->
                    track.format?.mimeType?.let { type ->
                        SubtitleTrack(
                            url = track.content,
                            languageTag = track.languageTag,
                            label = track.displayLanguageName + if (track.isAutoGenerated) " (auto)" else "",
                            mimeType = type,
                        )
                    }
                },
        )
    }

    fun normalizeVideoUrl(value: String): String {
        val input = value.trim()
        val handler = if (VIDEO_ID.matches(input)) service.streamLHFactory.fromId(input)
            else service.streamLHFactory.fromUrl(input)
        return handler.url
    }

    private fun toVideos(items: List<InfoItem>?): List<VideoItem> = items.orEmpty()
        .filterIsInstance<StreamInfoItem>()
        .mapNotNull { item ->
            val id = runCatching { service.streamLHFactory.fromUrl(item.url).id }.getOrNull()
                ?: return@mapNotNull null
            VideoItem(
                id = id,
                title = item.name.orEmpty(),
                channel = item.uploaderName.orEmpty(),
                thumbnailUrl = thumbnail(item.thumbnails),
                durationSeconds = item.duration.coerceAtLeast(0),
                viewCount = item.viewCount,
                url = item.url,
                channelUrl = item.uploaderUrl.orEmpty(),
            )
        }
        .distinctBy(VideoItem::id)

    private fun bestVideo(streams: List<VideoStream>): VideoStream? {
        // Prefer hardware-friendly MP4 up to 1080p; higher resolutions are unnecessary on most phones.
        val bounded = streams.filter { resolutionHeight(it) in 1..1080 }.ifEmpty { streams }
        return bounded.maxWithOrNull(
            compareBy<VideoStream> { resolutionHeight(it) }
                .thenBy { it.format?.mimeType == "video/mp4" }
                .thenBy { it.codec?.startsWith("avc1") == true },
        )
    }

    private fun resolutionHeight(stream: VideoStream): Int =
        RESOLUTION.find(stream.getResolution().orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun checkPageErrors(items: List<InfoItem>?, errors: List<Throwable>?) {
        if (items.isNullOrEmpty()) {
            errors.orEmpty().firstOrNull { it !is NothingFoundException }?.let { cause ->
                throw IOException("Could not read YouTube results. Please retry later.", cause)
            }
        }
    }

    private fun isProgressiveUrl(stream: Stream): Boolean =
        stream.isUrl && stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
            stream.content.startsWith("https://")

    private fun thumbnail(images: List<Image>?): String = images.orEmpty()
        .maxByOrNull { it.width.coerceAtLeast(0) * it.height.coerceAtLeast(0) }
        ?.url.orEmpty()

    companion object {
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
        private val RESOLUTION = Regex("(\\d+)p")
        @Volatile private var initialized = false

        private fun initializeExtractor() {
            if (initialized) return
            synchronized(YouTubeRepository::class.java) {
                if (!initialized) {
                    val locale = Locale.getDefault()
                    val country = locale.country.ifBlank { "US" }
                    NewPipe.init(
                        ExtractorDownloader(),
                        Localization(locale.language.ifBlank { "en" }, country),
                        ContentCountry(country),
                    )
                    initialized = true
                }
            }
        }
    }
}
