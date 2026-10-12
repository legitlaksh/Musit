package com.musit.player.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.musit.player.data.repository.PlaylistInfo
import com.musit.player.model.Song
import com.musit.player.ui.components.EmptyView

/**
 * Mockup-style Library: tabs, Liked Songs, playlists.
 * Data logic unchanged — UI restyled.
 */
@Composable
fun LibraryScreen(
    onSongClick: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onSearchClick: () -> Unit = {},
    initialTab: String? = null,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val localSongs by viewModel.localSongs.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab ?: "Playlists") }
    val tabs = listOf("Playlists", "Artists", "Albums", "Downloads")

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.loadLocalSongs()
    }

    LaunchedEffect(Unit) {
        if (viewModel.hasAudioPermission()) viewModel.loadLocalSongs()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Your Library", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
                }
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
                }
            }
        }

        // Tabs
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tabs) { tab ->
                    val selected = selectedTab == tab
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (selected) Color(0xFF9D4EDD) else Color.White.copy(alpha = 0.08f))
                            .clickable { selectedTab = tab }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(tab, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            Spacer(modifier = Modifier.size(16.dp))
        }

        when (selectedTab) {
            "Playlists" -> {
                // Liked Songs
                item {
                    LibraryRow(
                        icon = { Icon(Icons.Filled.Favorite, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp)) },
                        iconBg = Color(0xFF9D4EDD),
                        title = "Liked Songs",
                        subtitle = "${favorites.size} songs",
                        onClick = { if (favorites.isNotEmpty()) onSongClick(favorites, 0) }
                    )
                }
                // Downloaded
                if (localSongs.isNotEmpty()) {
                    item {
                        LibraryRow(
                            icon = { Icon(Icons.Filled.Download, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp)) },
                            iconBg = Color(0xFF4CAF50),
                            title = "Downloaded",
                            subtitle = "${localSongs.size} songs",
                            onClick = { onSongClick(localSongs, 0) }
                        )
                    }
                }
                // User playlists
                items(playlists, key = { it.id }) { playlist ->
                    LibraryRow(
                        icon = { Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp)) },
                        iconBg = Color(0xFF6A1B9A),
                        title = playlist.name,
                        subtitle = "by you",
                        onClick = { onOpenPlaylist(playlist.id) }
                    )
                }
                if (playlists.isEmpty() && favorites.isEmpty()) {
                    item {
                        EmptyView(message = "No playlists yet", hint = "Tap + to create one")
                    }
                }
            }
            "Artists" -> {
                item {
                    Text("Artists coming soon", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(20.dp))
                }
            }
            "Albums" -> {
                item {
                    Text("Albums coming soon", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(20.dp))
                }
            }
            "Downloads" -> {
                if (downloads.isEmpty()) {
                    item {
                        EmptyView(message = "No downloads", hint = "Download songs to listen offline")
                    }
                } else {
                    items(downloads, key = { it.songId }) { download ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { viewModel.playDownload(download, downloads) }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = download.artworkUrl,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(download.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(download.artist, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { viewModel.deleteDownload(download.songId) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remove download",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Create playlist dialog
    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.createPlaylist(name.trim())
                            showCreateDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LibraryRow(
    icon: @Composable () -> Unit,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.MoreVert, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
    }
}
