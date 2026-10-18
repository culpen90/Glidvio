@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.glidvio.app.ui

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.glidvio.app.MainActivity
import com.glidvio.app.data.ExtractorDownloader
import com.glidvio.app.data.PlaybackInfo
import com.glidvio.app.data.VideoItem
import com.glidvio.app.sponsor.*
import kotlinx.coroutines.delay
import kotlin.math.ceil

@Composable
fun PlayerScreen(
    info: PlaybackInfo, sponsorResult: SponsorLoadResult?, enabledCategories: Set<SponsorCategory>,
    fullscreen: Boolean, pip: Boolean, activity: MainActivity, onRetry: () -> Unit, onFullscreen: () -> Unit,
    saved: Boolean, onSave: () -> Unit, onVideoClick: (VideoItem) -> Unit, onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val segments = (sponsorResult as? SponsorLoadResult.Available)?.segments.orEmpty().filter { it.category in enabledCategories }
    var ignored by remember(info) { mutableStateOf(emptySet<String>()) }
    var lastSkip by remember(info) { mutableStateOf<SkipDecision?>(null) }
    var skipNoticeTime by remember(info) { mutableLongStateOf(0L) }
    var skippedSeconds by remember(info) { mutableDoubleStateOf(0.0) }
    var playbackError by remember(info) { mutableStateOf<String?>(null) }
    var ready by remember(info) { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var speedMenu by remember { mutableStateOf(false) }
    var expandedDescription by remember(info) { mutableStateOf(false) }
    var pendingAutoplay by remember(info) { mutableStateOf(true) }
    val currentSegments by rememberUpdatedState(segments)
    val player = remember(info) {
        val http = OkHttpDataSource.Factory(ExtractorDownloader.client).setDefaultRequestProperties(info.httpHeaders)
        val factory = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))
        ExoPlayer.Builder(context).setMediaSourceFactory(factory).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
            val video = MediaItem.Builder().setMediaId(info.id).setUri(info.videoUrl).setMimeType(info.mimeType)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(info.title).setArtist(info.channel).build())
                .setSubtitleConfigurations(info.subtitles.map { subtitle -> MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                    .setMimeType(subtitle.mimeType).setLanguage(subtitle.languageTag).setLabel(subtitle.label).build() }).build()
            if (info.audioUrl != null) {
                val audio = MediaItem.Builder().setUri(info.audioUrl).setMimeType(info.audioMimeType).build()
                setMediaSource(MergingMediaSource(factory.createMediaSource(video), factory.createMediaSource(audio)))
            } else setMediaItem(video)
            setPlaybackSpeed(speed)
            // Seek past a segment at the beginning before any frames or audio are presented.
            SkipPlanner.nextSkip(0.0, segments, durationSeconds = info.durationSeconds.toDouble())?.let { decision ->
                seekTo(ceil(decision.endSeconds * 1000).toLong())
            }
            prepare()
            playWhenReady = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || activity.pip
            pendingAutoplay = !playWhenReady
        }
    }
    fun performSkip(decision: SkipDecision) {
        player.seekTo(ceil(decision.endSeconds * 1000).toLong())
        skippedSeconds += decision.endSeconds - decision.positionSeconds
        lastSkip = decision
        skipNoticeTime = System.currentTimeMillis()
    }
    DisposableEffect(player) {
        activity.activePlayer = player
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { playbackError = "This stream could not play. Retry to request a fresh video stream." }
            override fun onPlaybackStateChanged(state: Int) { ready = state == Player.STATE_READY || state == Player.STATE_ENDED }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    SkipPlanner.nextSkip(newPosition.positionMs / 1000.0, currentSegments, ignored,
                        player.duration.takeIf { it > 0 }?.div(1000.0))?.let(::performSkip)
                }
            }
        }
        player.addListener(listener)
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && pendingAutoplay) {
                pendingAutoplay = false
                player.play()
            }
            if (event == Lifecycle.Event.ON_STOP && !activity.isInPictureInPictureMode) player.pause()
        }
        activity.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            if (activity.activePlayer === player) activity.activePlayer = null
            activity.lifecycle.removeObserver(lifecycleObserver)
            player.removeListener(listener); player.release()
        }
    }
    DisposableEffect(player, segments, ignored) {
        // Schedule known boundaries on the player's timeline. Polling below also covers
        // timeline changes; the listener above handles manual seeks into a segment.
        val messages = SkipPlanner.normalize(segments, info.durationSeconds.toDouble()).map { window ->
            player.createMessage { _, _ ->
                val position = maxOf(window.startSeconds, player.currentPosition / 1000.0)
                SkipPlanner.nextSkip(position, currentSegments, ignored,
                    player.duration.takeIf { it > 0 }?.div(1000.0))?.let(::performSkip)
            }.setLooper(player.applicationLooper)
                .setPosition(ceil(window.startSeconds * 1000).toLong())
                .setDeleteAfterDelivery(false).send()
        }
        onDispose { messages.forEach { it.cancel() } }
    }
    LaunchedEffect(player) {
        val initial = SkipPlanner.nextSkip(0.0, currentSegments, durationSeconds = info.durationSeconds.toDouble())
        if (initial != null) { lastSkip = initial; skipNoticeTime = System.currentTimeMillis(); skippedSeconds += initial.endSeconds }
        while (true) {
            if (player.playbackState == Player.STATE_READY && player.playWhenReady) {
                val decision = SkipPlanner.nextSkip(player.currentPosition / 1000.0, currentSegments, ignored, player.duration.takeIf { it > 0 }?.div(1000.0))
                if (decision != null) {
                    performSkip(decision)
                }
            }
            if (lastSkip != null && System.currentTimeMillis() - skipNoticeTime > 8000) lastSkip = null
            delay(100)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().then(if (fullscreen) Modifier.weight(1f) else Modifier.aspectRatio(16f / 9f)).background(Color.Black)) {
            AndroidView(factory = { viewContext -> PlayerView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                this.player = player; keepScreenOn = true; setShowSubtitleButton(true); setShowNextButton(false); setShowPreviousButton(false)
            } }, update = { it.player = player; it.useController = !pip }, modifier = Modifier.fillMaxSize())
            if (!pip) {
                IconButton(onClick = onFullscreen, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .background(Color.Black.copy(alpha = .65f), CircleShape)) {
                    Icon(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        if (fullscreen) "Exit fullscreen" else "Enter fullscreen", tint = Color.White)
                }
            }
            if (!ready && playbackError == null && !pip) CircularProgressIndicator(Modifier.align(Alignment.Center))
            if (playbackError != null && !pip) {
                Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .85f)).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(playbackError!!, color = Color.White)
                    Button(onClick = onRetry) { Text("Retry playback") }
                }
            }
        }
        if (!fullscreen) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(info.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(info.channel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = onSave, label = { Text(if (saved) "Saved" else "Save") }, leadingIcon = { Icon(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null, Modifier.size(18.dp)) })
                    Box {
                        AssistChip(onClick = { speedMenu = true }, label = { Text("${speed}×") }, leadingIcon = { Icon(Icons.Rounded.Speed, null, Modifier.size(18.dp)) })
                        DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                            listOf(.5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { rate -> DropdownMenuItem(text = { Text("${rate}×") }, onClick = { speed = rate; player.setPlaybackSpeed(rate); speedMenu = false }) }
                        }
                    }
                    IconButton(onClick = activity::pictureInPicture) { Icon(Icons.Rounded.PictureInPictureAlt, "Picture in picture") }
                    IconButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, info.url), "Share video")) }) { Icon(Icons.Rounded.Share, "Share video link") }
                }
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text("  Interruption protection", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Tune, "Configure automatic skipping") }
                        }
                        Text("Ad-free video · ${info.qualityLabel}", color = MaterialTheme.colorScheme.primary)
                        val coverage = when (sponsorResult) {
                            is SponsorLoadResult.Unavailable -> sponsorResult.message
                            is SponsorLoadResult.Available -> when {
                                enabledCategories.isEmpty() -> "Automatic skipping is off in settings."
                                segments.isEmpty() -> "No reported segments for your selected categories."
                                else -> "${segments.size} reported segments · skipping automatically"
                            }
                            null -> "Checking SponsorBlock…"
                        }
                        Text(coverage, style = MaterialTheme.typography.bodySmall)
                        if (skippedSeconds > 0) Text("${skippedSeconds.toInt()} seconds skipped this session", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        lastSkip?.let { decision ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Skipped ${decision.categories.joinToString { it.label.lowercase() }}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = {
                                    val undo = SkipPlanner.undo(decision, ignored); ignored = undo.ignoredUuids
                                    skippedSeconds = (skippedSeconds - (decision.endSeconds - decision.positionSeconds)).coerceAtLeast(0.0)
                                    player.seekTo((undo.positionSeconds * 1000).toLong()); lastSkip = null
                                }) { Text("Undo") }
                            }
                        }
                        Text("Sponsor coverage comes from community reports.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (info.description.isNotBlank()) {
                    Text(info.description, maxLines = if (expandedDescription) Int.MAX_VALUE else 3, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { expandedDescription = !expandedDescription })
                    TextButton(onClick = { expandedDescription = !expandedDescription }) { Text(if (expandedDescription) "Show less" else "Show description") }
                }
                if (info.related.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Keep watching", style = MaterialTheme.typography.titleMedium)
                    info.related.take(12).forEach { video ->
                        Surface(onClick = { onVideoClick(video) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(video.title, maxLines = 2, style = MaterialTheme.typography.bodyLarge)
                                Text(video.channel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
