package com.glidvio.app

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.media3.common.Player
import com.glidvio.app.data.VideoItem
import com.glidvio.app.data.VideoLinks
import com.glidvio.app.ui.*

class MainActivity : ComponentActivity() {
    private val model: AppModel by viewModels()
    var activePlayer: Player? = null
    var pip by mutableStateOf(false)
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFCBF278), onPrimary = Color(0xFF17220B), background = Color(0xFF0B101A),
                surface = Color(0xFF111925), surfaceVariant = Color(0xFF1A2432), onSurface = Color(0xFFF4F6FA),
                onSurfaceVariant = Color(0xFFA3AFBF), outline = Color(0xFF384251),
            )) { GlidvioApp(model, this) }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }
    private fun handleIntent(intent: Intent?) {
        val input = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        val url = VideoLinks.canonical(input)
        if (url != null) model.open(VideoItem(id = VideoLinks.videoId(url)!!, title = "Opening video…", url = url))
        else { model.query = input; model.search() }
    }
    fun pictureInPicture() {
        if (activePlayer == null || !packageManager.hasSystemFeature("android.software.picture_in_picture")) return
        runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
    }
    override fun onUserLeaveHint() { super.onUserLeaveHint(); if (activePlayer?.isPlaying == true) pictureInPicture() }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig); pip = isInPictureInPictureMode
    }
    override fun onStop() { if (!isInPictureInPictureMode) activePlayer?.pause(); super.onStop() }
}
