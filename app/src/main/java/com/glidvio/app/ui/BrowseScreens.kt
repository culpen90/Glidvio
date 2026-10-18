package com.glidvio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glidvio.app.data.VideoItem
import java.util.Locale

private val Canvas = Color(0xFF0B101A)
private val Panel = Color(0xFF141C29)
private val PanelLight = Color(0xFF1C2737)
private val Accent = Color(0xFFCBF278)
private val Ink = Color(0xFFF5F6F8)
private val Muted = Color(0xFF98A5B7)

/** The browsing surface owns no network or playback state. */
@Composable
fun BrowseScreen(
    videos: List<VideoItem>,
    loading: Boolean,
    error: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    hasMore: Boolean,
    savedIds: Set<String>,
    onToggleSave: (VideoItem) -> Unit,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onSettings: () -> Unit,
    activeSearchTerm: String = query,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val submitSearch = { focus.clearFocus(); keyboard?.hide(); onSearch() }
    Scaffold(
        containerColor = Canvas,
        contentColor = Ink,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = { BrowseNavigation(selectedTab, onTabChange) },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(310.dp),
                modifier = Modifier.widthIn(max = 1160.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        BrandHeader(onSettings)
                        Spacer(Modifier.height(24.dp))
                        when (selectedTab) {
                            1 -> LibraryHeading("Saved for later", "Good finds, kept in one place.")
                            2 -> LibraryHeading("Your watch history", "Your recently watched videos.")
                            else -> {
                                Text(
                                    "Watch without\ninterruption.",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 35.sp,
                                        lineHeight = 40.sp,
                                        letterSpacing = (-1.2).sp,
                                    ),
                                    color = Ink,
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = Accent.copy(alpha = 0.11f), shape = RoundedCornerShape(8.dp)) {
                                        Row(
                                            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        ) {
                                            Icon(Icons.Outlined.Check, null, tint = Accent, modifier = Modifier.size(13.dp))
                                            Text("AD-FREE VIEWING", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text("More video. Less noise.", color = Muted, style = MaterialTheme.typography.bodySmall)
                                }
                                if (activeSearchTerm.isBlank()) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "Music charts from YouTube. Search to explore everything.",
                                        color = Muted,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        SearchField(query, onQueryChange, submitSearch)
                        if (selectedTab == 0) {
                            Spacer(Modifier.height(10.dp))
                            Topics(query) { topic -> onQueryChange(topic); submitSearch() }
                        }
                    }
                }
                if (error != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) { ErrorPanel(error, loading, onRetry) }
                }
                if (videos.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (selectedTab) { 1 -> "YOUR COLLECTION"; 2 -> "RECENTLY WATCHED"; else -> if (activeSearchTerm.isBlank()) "MUSIC CHARTS" else "SEARCH RESULTS" },
                                color = Muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.6.sp,
                                modifier = Modifier.weight(1f),
                            )
                            if (selectedTab == 0) {
                                IconButton(onClick = onRetry, enabled = !loading, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Outlined.Refresh, "Refresh videos", tint = Muted, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    items(videos, key = { it.id }) { video ->
                        VideoCard(video, video.id in savedIds, { onVideoClick(video) }, { onToggleSave(video) })
                    }
                }
                if (loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = Accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Finding your next watch…", color = Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if (videos.isEmpty() && error == null) {
                    item(span = { GridItemSpan(maxLineSpan) }) { EmptyLibrary(selectedTab, activeSearchTerm) }
                }
                if (hasMore && videos.isNotEmpty() && !loading && selectedTab == 0) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.Center) {
                            OutlinedButton(onClick = onLoadMore, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Load more videos", color = Accent)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Outlined.ArrowForward, null, tint = Accent, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandHeader(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, null, tint = Canvas, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text("glidvio", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Settings, "Open settings", tint = Muted, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun LibraryHeading(title: String, subtitle: String) {
    Text(title, color = Ink, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search videos or paste a YouTube link" },
        singleLine = true,
        shape = RoundedCornerShape(17.dp),
        placeholder = { Text("Search or paste a YouTube link", color = Muted, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Muted, modifier = Modifier.size(22.dp)) },
        trailingIcon = {
            Row {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, "Clear search", tint = Muted, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onSearch, enabled = query.isNotBlank()) {
                    Icon(Icons.Outlined.ArrowForward, "Search videos or open link", tint = if (query.isNotBlank()) Accent else Muted, modifier = Modifier.size(21.dp))
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
            cursorColor = Accent,
            focusedContainerColor = Panel,
            unfocusedContainerColor = Panel,
            focusedBorderColor = Accent.copy(alpha = 0.7f),
            unfocusedBorderColor = PanelLight,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch() }),
    )
}

@Composable
private fun Topics(query: String, onTopic: (String) -> Unit) {
    val topics = listOf("Music", "Technology", "Science", "Gaming", "Design", "Nature")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(topics.size) { index ->
            val topic = topics[index]
            FilterChip(
                selected = query.equals(topic, ignoreCase = true),
                onClick = { onTopic(topic) },
                label = { Text(topic, fontSize = 12.sp) },
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(11.dp),
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Panel,
                    labelColor = Muted,
                    selectedContainerColor = Accent,
                    selectedLabelColor = Canvas,
                ),
            )
        }
    }
}

@Composable
private fun VideoCard(video: VideoItem, saved: Boolean, onClick: () -> Unit, onSave: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = "Watch ${video.title}", onClick = onClick),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp))
                .background(PanelLight),
        ) {
            Icon(Icons.Outlined.VideoLibrary, null, tint = Muted.copy(alpha = 0.3f), modifier = Modifier.align(Alignment.Center).size(40.dp))
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.1f), Color.Transparent, Color.Black.copy(alpha = 0.48f)))))
            IconButton(
                onClick = onSave,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(48.dp)
                    .background(Canvas.copy(alpha = 0.74f), CircleShape),
            ) {
                Icon(
                    if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    if (saved) "Remove ${video.title} from saved" else "Save ${video.title} for later",
                    tint = if (saved) Accent else Ink,
                    modifier = Modifier.size(22.dp),
                )
            }
            if (video.durationSeconds > 0) {
                Text(
                    durationLabel(video.durationSeconds.toLong()),
                    color = Ink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 11.dp, bottom = 7.dp),
        ) {
            Text(video.title, color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(
                buildString {
                    append(video.channel.ifBlank { "YouTube" })
                    if (video.viewCount > 0) append("  ·  ${viewLabel(video.viewCount.toLong())} views")
                },
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ErrorPanel(error: String, loading: Boolean, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = Accent, modifier = Modifier.size(22.dp))
                Text("Couldn't load videos", color = Ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(error, color = Muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Canvas), modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("Try again")
            }
        }
    }
}

@Composable
private fun EmptyLibrary(selectedTab: Int, query: String) {
    val icon: ImageVector = when (selectedTab) { 1 -> Icons.Outlined.BookmarkBorder; 2 -> Icons.Outlined.History; else -> Icons.Outlined.Search }
    val title = when (selectedTab) {
        1 -> "Keep a good video close."
        2 -> "A fresh start."
        else -> if (query.isBlank()) "Find your next good watch." else "No videos found."
    }
    val subtitle = when (selectedTab) {
        1 -> "Tap the bookmark on a video to save it for later."
        2 -> "Videos you watch will appear here."
        else -> if (query.isBlank()) "Search for something you love, or paste a YouTube link." else "Try a different search or paste a video link."
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(68.dp).background(Panel, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.widthIn(max = 340.dp))
    }
}

@Composable
private fun BrowseNavigation(selectedTab: Int, onTabChange: (Int) -> Unit) {
    NavigationBar(containerColor = Canvas, contentColor = Muted, tonalElevation = 0.dp) {
        val labels = listOf("Discover", "Saved", "History")
        val icons = listOf(Icons.Outlined.Explore, Icons.Outlined.BookmarkBorder, Icons.Outlined.History)
        val selectedIcons = listOf(Icons.Filled.Explore, Icons.Filled.Bookmark, Icons.Filled.History)
        labels.forEachIndexed { index, label ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabChange(index) },
                icon = { Icon(if (selectedTab == index) selectedIcons[index] else icons[index], null, modifier = Modifier.size(23.dp)) },
                label = { Text(label, fontSize = 11.sp, fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Accent,
                    selectedTextColor = Accent,
                    indicatorColor = Accent.copy(alpha = 0.11f),
                    unselectedIconColor = Muted,
                    unselectedTextColor = Muted,
                ),
            )
        }
    }
}

private fun durationLabel(seconds: Long): String = if (seconds >= 3600) {
    String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
} else {
    String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}

private fun viewLabel(views: Long): String = when {
    views >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", views / 1_000_000_000.0)
    views >= 1_000_000 -> String.format(Locale.US, "%.1fM", views / 1_000_000.0)
    views >= 1_000 -> String.format(Locale.US, "%.1fK", views / 1_000.0)
    else -> views.toString()
}.replace(".0", "")
