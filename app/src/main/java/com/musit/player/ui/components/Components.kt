package com.musit.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musit.player.data.repository.PlaylistInfo
import com.musit.player.model.Song

// ------------------------------------------------------------------
// song row
// ------------------------------------------------------------------

/**
 * Standard song list row: artwork, title, artist/duration, favorite state
 * and an overflow menu (Play next / Add to playlist / Favorite).
 */
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: ((Song) -> Unit)? = null,
    onPlayNext: ((Song) -> Unit)? = null,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.artworkUrl,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
            placeholder = null,
            error = null
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = listOfNotNull(song.artist, song.durationText).joinToString(" • "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onToggleFavorite != null) {
            IconButton(onClick = { onToggleFavorite(song) }) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove favorite" else "Favorite",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        if (onPlayNext != null || onAddToPlaylist != null) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (onPlayNext != null) {
                        DropdownMenuItem(
                            text = { Text("Play next") },
                            leadingIcon = {
                                Icon(Icons.Filled.QueueMusic, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onPlayNext(song)
                            }
                        )
                    }
                    if (onAddToPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Add to playlist") },
                            leadingIcon = {
                                Icon(Icons.Filled.PlaylistAdd, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onAddToPlaylist(song)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// mini player
// ------------------------------------------------------------------

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    progress: Float,
    onTogglePlayPause: () -> Unit,
    onTap: () -> Unit,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Echo-style floating pill mini-player
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF1E1E1E))
            .clickable(onClick = onTap)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A2A)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(28.dp)
                )
            }
            // White circular play button (Echo style)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onTogglePlayPause),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.Black
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// ------------------------------------------------------------------
// section header
// ------------------------------------------------------------------

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        action?.invoke()
    }
}

// ------------------------------------------------------------------
// loading / error / empty
// ------------------------------------------------------------------

@Composable
fun LoadingView(modifier: Modifier = Modifier, message: String = "Loading…") {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
fun EmptyView(
    message: String,
    modifier: Modifier = Modifier,
    hint: String? = null
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            if (hint != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ------------------------------------------------------------------
// playlist picker dialog
// ------------------------------------------------------------------

/**
 * Dialog to add a song to a playlist: lists existing playlists and offers
 * to create a new one.
 */
@Composable
fun PlaylistPickerDialog(
    playlists: List<PlaylistInfo>,
    onDismiss: () -> Unit,
    onSelect: (PlaylistInfo) -> Unit,
    onCreateNew: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                if (playlists.isEmpty()) {
                    Text(
                        "No playlists yet — create one below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(200.dp.coerceAtMost(64.dp * playlists.size))) {
                        items(playlists, key = { it.id }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(playlist) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newName.isNotBlank()) {
                        onCreateNew(newName.trim())
                        onDismiss()
                    }
                },
                enabled = newName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
