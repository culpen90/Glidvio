package com.glidvio.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glidvio.app.data.*
import com.glidvio.app.sponsor.*
import kotlinx.coroutines.*
import org.schabi.newpipe.extractor.Page

class AppModel(application: Application) : AndroidViewModel(application) {
    private val repository = YouTubeRepository()
    private val sponsorBlock = SponsorBlockClient()
    private val library = LocalLibrary(application)
    var query by mutableStateOf("")
    var searchTerm by mutableStateOf("")
        private set
    var videos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var saved by mutableStateOf(library.read("saved"))
        private set
    var history by mutableStateOf(library.read("history"))
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var nextPage by mutableStateOf<Page?>(null)
        private set
    var tab by mutableStateOf(0)
    var selected by mutableStateOf<VideoItem?>(null)
        private set
    var playback by mutableStateOf<PlaybackInfo?>(null)
        private set
    var playbackLoading by mutableStateOf(false)
        private set
    var playbackError by mutableStateOf<String?>(null)
        private set
    var sponsorResult by mutableStateOf<SponsorLoadResult?>(null)
        private set
    var enabledCategories by mutableStateOf(SponsorCategory.entries.filter { category ->
        library.categories()?.contains(category.apiName) ?: (category in SponsorCategory.defaults)
    }.toSet())
        private set
    private var searchJob: Job? = null
    private var playbackJob: Job? = null
    init { discover() }

    fun discover() { query = ""; searchTerm = ""; loadFeed(false) }
    fun search() {
        val input = query.trim()
        if (input.isEmpty()) { discover(); return }
        val url = VideoLinks.canonical(input)
        if (url != null) { open(VideoItem(id = VideoLinks.videoId(url)!!, title = "Opening video…", url = url)); return }
        tab = 0; searchTerm = input; loadFeed(false)
    }
    fun retry() = loadFeed(false)
    fun more() { if (!loading && nextPage != null) loadFeed(true) }
    private fun loadFeed(append: Boolean) {
        searchJob?.cancel()
        val term = searchTerm
        val page = if (append) nextPage else null
        searchJob = viewModelScope.launch {
            loading = true; error = null
            if (!append) { videos = emptyList(); nextPage = null }
            try {
                val result = if (term.isBlank()) repository.trending(page) else repository.search(term, page)
                ensureActive()
                videos = (if (append) videos + result.items else result.items).distinctBy { it.id }
                nextPage = result.nextPage
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = friendlyError(failure) }
            finally { if (isActive) loading = false }
        }
    }
    fun open(video: VideoItem) {
        playbackJob?.cancel(); selected = video; playback = null; playbackError = null; sponsorResult = null; playbackLoading = true
        playbackJob = viewModelScope.launch {
            try {
                coroutineScope {
                    val segments = async { sponsorBlock.load(video.id, SponsorCategory.entries.toSet()) }
                    val info = repository.loadPlayback(video.url)
                    val coverage = segments.await()
                    ensureActive()
                    sponsorResult = coverage
                    if (coverage is SponsorLoadResult.Unavailable && enabledCategories.isNotEmpty()) {
                        playbackError = "Sponsor protection could not be checked. Please retry before starting this video."
                        return@coroutineScope
                    }
                    playback = info
                    val actual = video.copy(title = info.title, channel = info.channel, thumbnailUrl = info.thumbnailUrl, durationSeconds = info.durationSeconds)
                    selected = actual
                    history = (listOf(actual) + history.filterNot { it.id == actual.id }).take(200)
                    library.write("history", history)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { playbackError = friendlyError(failure) }
            finally { if (isActive) playbackLoading = false }
        }
    }
    fun retryPlayback() { selected?.let(::open) }
    fun closePlayback() { playbackJob?.cancel(); selected = null; playback = null; playbackLoading = false; playbackError = null }
    fun toggleSaved(video: VideoItem) {
        saved = if (saved.any { it.id == video.id }) saved.filterNot { it.id == video.id } else listOf(video) + saved
        library.write("saved", saved)
    }
    fun toggleCategory(category: SponsorCategory, enabled: Boolean) {
        enabledCategories = if (enabled) enabledCategories + category else enabledCategories - category
        library.setCategories(enabledCategories.map { it.apiName }.toSet())
    }
    fun clearHistory() { history = emptyList(); library.clearHistory() }

    private fun friendlyError(error: Exception): String {
        val details = generateSequence<Throwable>(error) { it.cause }.mapNotNull { it.message }.joinToString(" ")
        return when {
            details.contains("Sign in", true) || details.contains("bot", true) -> "YouTube is asking this connection to verify it is human. Try again on another network or later."
            listOf("age restricted", "age-restricted", "confirm your age").any { details.contains(it, true) } -> "This video is age restricted and cannot be played without signing in."
            details.contains("unavailable", true) || details.contains("private", true) -> "This video is unavailable, private, or restricted in your region."
            error is java.io.IOException -> "Could not connect. Check your internet connection and try again."
            else -> "YouTube could not load this content. Please retry; an extractor update may be needed if the problem continues."
        }
    }
}
