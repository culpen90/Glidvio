package com.glidvio.app.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glidvio.app.AppModel
import com.glidvio.app.MainActivity
import com.glidvio.app.sponsor.SponsorCategory

@Composable
fun GlidvioApp(model: AppModel, activity: MainActivity) {
    var settings by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (model.selected == null) {
            BrowseScreen(
                videos = when (model.tab) { 1 -> model.saved; 2 -> model.history; else -> model.videos },
                loading = model.tab == 0 && model.loading,
                error = model.error.takeIf { model.tab == 0 },
                query = model.query, onQueryChange = { model.query = it }, onSearch = model::search,
                onVideoClick = model::open, onRetry = model::retry, onLoadMore = model::more,
                hasMore = model.tab == 0 && model.nextPage != null,
                savedIds = model.saved.map { it.id }.toSet(), onToggleSave = model::toggleSaved,
                selectedTab = model.tab, onTabChange = { model.tab = it; if (it == 0) model.discover() }, onSettings = { settings = true },
                activeSearchTerm = model.searchTerm,
            )
        } else {
            val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            val fullscreen = landscape || activity.pip
            val close = {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                model.closePlayback()
            }
            BackHandler {
                if (landscape) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else close()
            }
            Column(Modifier.fillMaxSize().then(if (activity.pip) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))) {
                if (!fullscreen) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to videos") }
                        Text("Now watching", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Rounded.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                        Text("  Ad-free", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
                        IconButton(onClick = { settings = true }) { Icon(Icons.Rounded.Tune, "Sponsor skipping settings") }
                    }
                }
                val info = model.playback
                if (info != null) {
                    PlayerScreen(info, model.sponsorResult, model.enabledCategories, fullscreen, activity.pip,
                        activity = activity, onRetry = model::retryPlayback,
                        onFullscreen = {
                            activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        },
                        saved = model.saved.any { it.id == info.id },
                        onSave = { model.selected?.let(model::toggleSaved) }, onVideoClick = model::open,
                        onSettings = { settings = true })
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            if (model.playbackLoading) {
                                CircularProgressIndicator()
                                Text("Preparing ad-free playback…")
                                Text("Checking sponsor segments before starting", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Icon(Icons.Rounded.CloudOff, null, Modifier.size(40.dp))
                                Text(model.playbackError ?: "Could not load this video.")
                                Button(onClick = model::retryPlayback) { Text("Try again") }
                                TextButton(onClick = close) { Text("Back to videos") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (settings) SettingsDialog(model, onDismiss = { settings = false })
}

@Composable
private fun SettingsDialog(model: AppModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var licenses by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Your uninterrupted view") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ad-free playback", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("Videos play directly in Glidvio. No YouTube ad player and no advertising SDK.")
                HorizontalDivider()
                Text("Automatically skip", style = MaterialTheme.typography.titleMedium)
                SponsorCategory.entries.forEach { category ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(category.label, Modifier.weight(1f), fontSize = 14.sp)
                        Switch(checked = category in model.enabledCategories, onCheckedChange = { model.toggleCategory(category, it) })
                    }
                }
                Text("SponsorBlock uses community reports. Unreported segments can remain. Coverage status is shown for each video.", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("Your saved videos and history stay on this device. No account is needed.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = model::clearHistory) { Text("Clear watch history") }
                Text("Glidvio 0.1.0 · GPL-3.0-or-later", style = MaterialTheme.typography.labelSmall)
                Text("Uses NewPipe Extractor (GPL-3.0-or-later) and SponsorBlock data (CC BY-NC-SA 4.0).", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { licenses = true }) { Text("Open-source licenses") }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sponsor.ajay.app/"))) }) { Text("SponsorBlock · community credits") }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
    if (licenses) {
        val notices = remember { runCatching { context.assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() } }.getOrDefault("See THIRD_PARTY_NOTICES.md in the Glidvio source project.") }
        AlertDialog(onDismissRequest = { licenses = false }, title = { Text("Open-source licenses") },
            text = { Text(notices, Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { licenses = false }) { Text("Done") } })
    }
}
