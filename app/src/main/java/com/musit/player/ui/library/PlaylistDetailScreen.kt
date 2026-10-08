package com.musit.player.ui.library

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musit.player.model.Song

/**
 * Mockup-style Playlist detail: header, actions, song list.
 */
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit
) {
    // TODO: Load real playlist data from repository
    val songs = emptyList<Song>()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Top bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.MoreVert, "More", tint = Color.White)
                }
            }
        }

        // Header
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(180.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF6A1B9A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(64.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Playlist", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("by you", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                Text("${songs.size} songs", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.Download, "Download",
                            tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.Shuffle, "Shuffle",
                            tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
                    }
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White)
                            .clickable { if (songs.isNotEmpty()) onSongClick(songs, 0) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PlayArrow, "Play", tint = Color.Black,
                            modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Songs
        if (songs.isEmpty()) {
            item {
                Text("No songs in this playlist yet",
                    color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp,
                    modifier = Modifier.padding(20.dp))
            }
        } else {
            itemsIndexed(songs) { idx, song ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onSongClick(songs, idx) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (song.artworkUrl != null) {
                        AsyncImage(
                            model = song.artworkUrl,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2A2A2A)))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(song.title, color = Color.White, fontSize = 15.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
