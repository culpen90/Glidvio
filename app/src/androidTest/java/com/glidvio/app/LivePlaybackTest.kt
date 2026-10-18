package com.glidvio.app

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.glidvio.app.data.ExtractorDownloader
import com.glidvio.app.data.PlaybackInfo
import com.glidvio.app.data.YouTubeRepository
import com.glidvio.app.sponsor.SkipPlanner
import com.glidvio.app.sponsor.SponsorBlockClient
import com.glidvio.app.sponsor.SponsorCategory
import com.glidvio.app.sponsor.SponsorLoadResult
import com.glidvio.app.sponsor.SkipWindow
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Manual, network-dependent smoke tests against YouTube and SponsorBlock. No responses are mocked. */
@RunWith(AndroidJUnit4::class)
class LivePlaybackTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test(timeout = 180_000)
    fun liveSearchAndPagination(): Unit = runBlocking {
        val repository = YouTubeRepository()
        val first = repository.search("Big Buck Bunny")
        assertTrue("YouTube should return real videos", first.items.isNotEmpty())
        assertTrue(first.items.all { it.id.matches(Regex("[A-Za-z0-9_-]{11}")) && it.title.isNotBlank() })
        assertNotNull("The live search should expose a continuation", first.nextPage)
        val second = repository.search("Big Buck Bunny", first.nextPage)
        assertTrue("The continuation should contain more real videos", second.items.isNotEmpty())
        assertTrue("Continuation should add new videos", second.items.any { next -> first.items.none { it.id == next.id } })
    }

    @Test(timeout = 180_000)
    fun livePlaybackExtractionAndMediaBytes(): Unit = runBlocking {
        // Public Blender Foundation film with both adaptive video and audio formats.
        val playback = YouTubeRepository().loadPlayback("https://www.youtube.com/watch?v=aqz-KE-bpKQ")
        assertTrue(playback.title.isNotBlank())
        assertTrue(playback.durationSeconds > 0)
        assertTrue(playback.videoUrl.startsWith("https://"))
        readRealMediaBytes(playback.videoUrl, playback, "video")
        playback.audioUrl?.let { readRealMediaBytes(it, playback, "audio") }
    }

    @Test(timeout = 120_000)
    fun actionViewRendersMovingVideo() {
        ActivityScenario.launch<MainActivity>(viewIntent("https://www.youtube.com/watch?v=jNQXAC9IVRw")).use { scenario ->
            waitForMovingVideo(scenario)
        }
    }

    @Test(timeout = 300_000)
    fun realSponsorSegmentAutomaticallySeeks(): Unit = runBlocking {
        val repository = YouTubeRepository()
        val sponsorBlock = SponsorBlockClient()
        val candidates = repository.search("Linus Tech Tips").items
            .filter { it.durationSeconds > 120 }
            .take(8)
        var covered: CoveredVideo? = null
        for (video in candidates) {
            val result = sponsorBlock.load(video.id)
            if (result is SponsorLoadResult.Available) {
                val interval = SkipPlanner.normalize(result.segments, video.durationSeconds.toDouble())
                    .firstOrNull { it.endSeconds - it.startSeconds >= 25 && it.startSeconds > 2 && it.endSeconds < video.durationSeconds - 2 }
                if (interval != null) {
                    covered = CoveredVideo(video.url, interval)
                    break
                }
            }
        }
        val target = covered ?: throw AssertionError("No sufficiently long community sponsor interval was available among the live search results")

        // Give this test a defined configuration, then restore the user's previous categories.
        val preferences = context.getSharedPreferences("glidvio", Context.MODE_PRIVATE)
        val previous = preferences.getStringSet("categories", null)?.toSet()
        assertTrue(preferences.edit().putStringSet("categories", SponsorCategory.defaults.map { it.apiName }.toSet()).commit())
        try {
            ActivityScenario.launch<MainActivity>(viewIntent(target.url)).use { scenario ->
                // The Undo action is in the portrait details panel, outside fullscreen video.
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                waitForMovingVideo(scenario)
                val startMs = ((target.window.startSeconds + 1.0) * 1000).toLong()
                val boundaryMs = (target.window.startSeconds * 1000).toLong()
                val endMs = (target.window.endSeconds * 1000).toLong()
                scenario.onActivity { activity ->
                    val player = activity.activePlayer ?: throw AssertionError("Player disappeared before the sponsor seek")
                    player.seekTo(startMs)
                    player.play()
                }
                waitForSponsorJump(scenario, startMs, endMs, 15_000, "Seeking into a sponsor")

                // Rewind before the boundary. This must be crossed during ordinary playback,
                // exercising scheduled player messages and the playback loop, not the seek listener.
                val beforeMs = (boundaryMs - 1_000).coerceAtLeast(0)
                scenario.onActivity { activity ->
                    val player = activity.activePlayer ?: throw AssertionError("Player disappeared before the natural boundary")
                    player.seekTo(beforeMs)
                    assertTrue("Rewind must stop before the target sponsor; observed ${player.currentPosition}ms, boundary ${boundaryMs}ms",
                        player.currentPosition < boundaryMs)
                    player.play()
                }
                waitForSponsorJump(scenario, beforeMs, endMs, 10_000, "Crossing a sponsor boundary")

                // Use the actual UI action, since planner-only tests cannot catch stale state
                // captured by ExoPlayer listeners or callbacks that immediately skip again.
                clickUndo()
                assertUndoKeepsPlayingInsideSegment(scenario, boundaryMs, endMs)
            }
        } finally {
            val editor = preferences.edit()
            if (previous == null) editor.remove("categories") else editor.putStringSet("categories", previous)
            assertTrue(editor.commit())
        }
    }

    private fun waitForSponsorJump(
        scenario: ActivityScenario<MainActivity>,
        fromMs: Long,
        endMs: Long,
        timeoutMs: Long,
        action: String,
    ) {
        val began = SystemClock.elapsedRealtime()
        var position = fromMs
        var status = "Player not created"
        while (SystemClock.elapsedRealtime() - began < timeoutMs) {
            scenario.onActivity { activity ->
                val player = activity.activePlayer ?: throw AssertionError("$action: player disappeared")
                assertTrue("$action: media error ${player.playerError}", player.playerError == null)
                position = player.currentPosition
                status = "state=${player.playbackState}, playing=${player.isPlaying}, position=${position}ms"
            }
            if (position >= endMs - 300) {
                val elapsed = SystemClock.elapsedRealtime() - began
                assertTrue("$action: segment must skip faster than ordinary playback; elapsed=${elapsed}ms, from=${fromMs}ms, end=${endMs}ms",
                    elapsed < endMs - fromMs - 1_000)
                return
            }
            SystemClock.sleep(100)
        }
        throw AssertionError("$action: expected an automatic seek to ${endMs}ms within ${timeoutMs}ms; $status")
    }

    private fun clickUndo() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.elapsedRealtime() + 6_000
        var lastScrollAt = 0L
        var visibleText = "No accessibility window"
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = automation.rootInActiveWindow
            if (root != null) {
                val nodes = accessibilityNodes(root)
                visibleText = nodes.mapNotNull { it.text?.toString() }.joinToString(" | ").take(1_000)
                for (node in nodes.filter { it.text?.toString() == "Undo" }) {
                    var target: AccessibilityNodeInfo? = node
                    repeat(4) {
                        val candidate = target
                        if (candidate != null && candidate.isVisibleToUser && candidate.isEnabled && candidate.isClickable &&
                            candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                        target = candidate?.parent
                    }
                }
                // The details card can initially be below the fold on a short display.
                if (SystemClock.elapsedRealtime() - lastScrollAt >= 500) {
                    nodes.firstOrNull { it.isScrollable && it.isVisibleToUser }
                        ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    lastScrollAt = SystemClock.elapsedRealtime()
                }
            }
            SystemClock.sleep(100)
        }
        throw AssertionError("The sponsor Undo button could not be clicked before its notice expired. Accessibility text: $visibleText")
    }

    private fun assertUndoKeepsPlayingInsideSegment(
        scenario: ActivityScenario<MainActivity>, boundaryMs: Long, endMs: Long,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        var restoredPosition: Long? = null
        var playedInsideAt: Long? = null
        var status = "Player not created"
        while (SystemClock.elapsedRealtime() < deadline) {
            var position = -1L
            var playing = false
            scenario.onActivity { activity ->
                val player = activity.activePlayer ?: throw AssertionError("Player disappeared after Undo")
                assertTrue("Media error after Undo: ${player.playerError}", player.playerError == null)
                position = player.currentPosition
                playing = player.isPlaying
                status = "state=${player.playbackState}, playing=$playing, position=${position}ms"
            }
            if (restoredPosition == null) {
                if (position in (boundaryMs - 300) until (boundaryMs + 3_000)) restoredPosition = position
            } else {
                assertTrue("Undo immediately skipped the sponsor again; $status, expected before ${endMs}ms", position < endMs - 300)
                if (playing && position >= restoredPosition + 1_000) {
                    if (playedInsideAt == null) playedInsideAt = SystemClock.elapsedRealtime()
                    if (SystemClock.elapsedRealtime() - playedInsideAt >= 1_000) return
                }
            }
            SystemClock.sleep(100)
        }
        throw AssertionError("Undo must return near ${boundaryMs}ms and play inside the sponsor for more than one second without another skip; restored=$restoredPosition, $status")
    }

    private fun accessibilityNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf(root)
        var index = 0
        while (index < nodes.size) {
            val node = nodes[index++]
            for (childIndex in 0 until node.childCount) node.getChild(childIndex)?.let(nodes::add)
        }
        return nodes
    }

    private fun readRealMediaBytes(url: String, playback: PlaybackInfo, kind: String) {
        val request = Request.Builder().url(url).apply {
            playback.httpHeaders.forEach { (key, value) -> header(key, value) }
            header("Range", "bytes=0-1023")
        }.build()
        ExtractorDownloader.client.newCall(request).execute().use { response ->
            assertTrue("$kind HTTP ${response.code}", response.isSuccessful)
            val body = response.body ?: throw AssertionError("$kind response has no body")
            val bytes = body.source().readByteArray(512)
            assertTrue("$kind response should contain actual media bytes", bytes.size == 512)
            assertFalse("$kind response must not be a web error document", response.header("Content-Type").orEmpty().contains("text/html"))
        }
    }

    private fun viewIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    private fun waitForMovingVideo(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.elapsedRealtime() + 90_000
        var moving = false
        var status = "Player not created"
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                val player = activity.activePlayer
                if (player != null) {
                    assertTrue("Playback error: ${player.playerError}", player.playerError == null)
                    status = "state=${player.playbackState}, playing=${player.isPlaying}, position=${player.currentPosition}, size=${player.videoSize.width}x${player.videoSize.height}"
                    moving = player.isPlaying && player.currentPosition > 1_000 && player.videoSize.width > 0
                }
            }
            if (moving) return
            SystemClock.sleep(500)
        }
        throw AssertionError("No moving decoded video after 90 seconds: $status")
    }

    private data class CoveredVideo(val url: String, val window: SkipWindow)
}
