package com.glidvio.app

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real player controls and lifecycle transitions on a device, without mocked media. */
@RunWith(AndroidJUnit4::class)
class PlayerControlsTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test(timeout = 180_000)
    fun fullscreenPictureInPictureAndSaveKeepTheSamePlayer() {
        val preferences = context.getSharedPreferences("glidvio", Context.MODE_PRIVATE)
        val previousSaved = preferences.getString("saved", null)
        try {
            // Exercise the actual Save action even if this video was already saved by the user.
            val original = JSONArray(previousSaved ?: "[]")
            val withoutTarget = JSONArray()
            for (index in 0 until original.length()) {
                val entry = original.getJSONObject(index)
                if (entry.optString("id") != VIDEO_ID) withoutTarget.put(entry)
            }
            assertTrue("Could not set up the saved-video state", preferences.edit().putString("saved", withoutTarget.toString()).commit())

            val launch = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$VIDEO_ID"), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ActivityScenario.launch<MainActivity>(launch).use { scenario ->
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                awaitState(scenario, "initial moving decoded video", 90_000) { activity ->
                    val player = activity.activePlayer
                    activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
                        !activity.isInPictureInPictureMode && !activity.pip &&
                        player != null && player.playbackState == Player.STATE_READY && player.isPlaying &&
                        player.currentPosition >= 1_000 && player.videoSize.width > 0
                }

                // Optional emulator diagnosis: keep the normal audio path by
                // default, but allow a controlled video-only lifecycle run when
                // a virtual device's audio HAL cannot consume PCM output.
                if (InstrumentationRegistry.getArguments().getString("glidvio.disableControlAudio") == "true") {
                    scenario.onActivity { activity ->
                        val player = activity.activePlayer ?: throw AssertionError("Player disappeared before the audio-HAL control")
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                            .build()
                    }
                }

                lateinit var originalActivity: MainActivity
                lateinit var originalPlayer: Player
                scenario.onActivity { activity ->
                    originalActivity = activity
                    originalPlayer = activity.activePlayer ?: throw AssertionError("Player disappeared after startup")
                }

                clickDescription("Enter fullscreen")
                awaitState(scenario, "landscape fullscreen") { activity ->
                    assertSame("Fullscreen recreated the Activity", originalActivity, activity)
                    assertSame("Fullscreen replaced the player", originalPlayer, activity.activePlayer)
                    activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                }
                awaitProgress(scenario, originalPlayer, position(scenario), "fullscreen")

                clickDescription("Exit fullscreen")
                awaitState(scenario, "portrait after leaving fullscreen") { activity ->
                    assertSame("Leaving fullscreen recreated the Activity", originalActivity, activity)
                    assertSame("Leaving fullscreen replaced the player", originalPlayer, activity.activePlayer)
                    activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                }
                awaitProgress(scenario, originalPlayer, position(scenario), "portrait")

                clickDescription("Picture in picture", allowScroll = true)
                awaitState(scenario, "picture in picture") { activity ->
                    assertSame("Picture in picture recreated the Activity", originalActivity, activity)
                    assertSame("Picture in picture replaced the player", originalPlayer, activity.activePlayer)
                    activity.isInPictureInPictureMode && activity.pip
                }
                awaitProgress(scenario, originalPlayer, position(scenario), "picture in picture")

                // MAIN has no video data, so singleTask should foreground the same playback.
                context.startActivity(Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                awaitState(scenario, "foreground return from picture in picture") { activity ->
                    assertSame("Returning from picture in picture recreated the Activity", originalActivity, activity)
                    assertSame("Returning from picture in picture replaced the player", originalPlayer, activity.activePlayer)
                    !activity.isInPictureInPictureMode && !activity.pip &&
                        activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                }
                // ActivityScenario matches lifecycle callbacks against its original launch
                // intent. MAIN has now been exercised; restore that match for close() cleanup.
                scenario.onActivity { it.intent = launch }
                awaitProgress(scenario, originalPlayer, position(scenario), "return from picture in picture")

                clickNode("Save text", allowScroll = true) { it.text?.toString() == "Save" }
                val savedDeadline = SystemClock.elapsedRealtime() + 5_000
                while (SystemClock.elapsedRealtime() < savedDeadline) {
                    val saved = JSONArray(preferences.getString("saved", "[]"))
                    if ((0 until saved.length()).any { saved.getJSONObject(it).optString("id") == VIDEO_ID }) return@use
                    SystemClock.sleep(100)
                }
                throw AssertionError("Save did not persist $VIDEO_ID. Saved preference: ${preferences.getString("saved", null)}")
            }
        } finally {
            val restore = preferences.edit()
            if (previousSaved == null) restore.remove("saved") else restore.putString("saved", previousSaved)
            assertTrue("Could not restore the user's saved videos", restore.commit())
        }
    }

    private fun awaitProgress(scenario: ActivityScenario<MainActivity>, expected: Player, fromMs: Long, phase: String) {
        awaitState(scenario, "continued playback in $phase") { activity ->
            val player = activity.activePlayer
            assertSame("$phase replaced the player", expected, player)
            player != null && player.isPlaying && player.currentPosition >= fromMs + 500
        }
    }

    private fun position(scenario: ActivityScenario<MainActivity>): Long {
        var result = -1L
        scenario.onActivity { activity ->
            result = activity.activePlayer?.currentPosition ?: throw AssertionError("Player disappeared while checking position")
        }
        return result
    }

    private fun awaitState(
        scenario: ActivityScenario<MainActivity>, label: String, timeoutMs: Long = 15_000,
        predicate: (MainActivity) -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var status = "Activity not observed"
        while (SystemClock.elapsedRealtime() < deadline) {
            var matches = false
            scenario.onActivity { activity ->
                val player = activity.activePlayer
                assertTrue("$label: player error ${player?.playerError}", player?.playerError == null)
                status = "orientation=${activity.resources.configuration.orientation}, pip=${activity.isInPictureInPictureMode}/${activity.pip}, " +
                    "state=${player?.playbackState}, playing=${player?.isPlaying}, playWhenReady=${player?.playWhenReady}, " +
                    "suppression=${player?.playbackSuppressionReason}, position=${player?.currentPosition}ms, " +
                    "contentBuffered=${player?.contentBufferedPosition}ms, totalBuffered=${player?.totalBufferedDuration}ms, video=${player?.videoSize}"
                matches = predicate(activity)
            }
            if (matches) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Timed out waiting for $label after ${timeoutMs}ms; $status")
    }

    private fun clickDescription(description: String, allowScroll: Boolean = false) =
        clickNode("content description '$description'", allowScroll) { it.contentDescription?.toString() == description }

    private fun clickNode(
        label: String, allowScroll: Boolean,
        matches: (AccessibilityNodeInfo) -> Boolean,
    ) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.elapsedRealtime() + 8_000
        var lastScrollAt = 0L
        var status = "No active accessibility window"
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = automation.rootInActiveWindow
            if (root != null && root.refresh()) {
                val nodes = mutableListOf(root)
                var index = 0
                while (index < nodes.size) {
                    val node = nodes[index++]
                    // Android 8 can retain cached descriptions across rotation.
                    // Refresh each node before using its label or child snapshot.
                    node.refresh()
                    for (child in 0 until node.childCount) node.getChild(child)?.let(nodes::add)
                }
                status = nodes.mapNotNull { it.contentDescription?.toString() ?: it.text?.toString() }.joinToString(" | ").take(1_500)
                for (node in nodes.filter(matches)) {
                    var target: AccessibilityNodeInfo? = node
                    repeat(4) {
                        val candidate = target
                        if (candidate != null && candidate.isVisibleToUser && candidate.isEnabled && candidate.isClickable &&
                            candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                        target = candidate?.parent
                    }
                }
                if (allowScroll && SystemClock.elapsedRealtime() - lastScrollAt >= 500) {
                    nodes.firstOrNull { it.isScrollable && it.isVisibleToUser }
                        ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    lastScrollAt = SystemClock.elapsedRealtime()
                }
            }
            SystemClock.sleep(100)
        }
        throw AssertionError("Could not click $label. Accessibility content: $status")
    }

    private companion object {
        const val VIDEO_ID = "aqz-KE-bpKQ"
    }
}
