package com.musit.player.ui.player

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.musit.player.player.RepeatMode
import com.musit.player.ui.formatMs

/**
 * Mockup-style Now Playing: card artwork, full controls, lyrics section.
 * Playback logic unchanged — UI only.
 */
@Composable
fun PlayerScreen(
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    BackHandler(onBack = onClose)

    val playback = viewModel.playback
    val song by playback.currentSong.collectAsStateWithLifecycle()
    val isPlaying by playback.isPlaying.collectAsStateWithLifecycle()
    val isLoading by playback.isLoading.collectAsStateWithLifecycle()
    val positionMs by playback.positionMs.collectAsStateWithLifecycle()
    val durationMs by playback.durationMs.collectAsStateWithLifecycle()
    val playerError by playback.playerError.collectAsStateWithLifecycle()
    val shuffleEnabled by playback.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by playback.repeatMode.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()

    val current = song
    val isFav = current?.let { favoriteIds.contains(it.id) } ?: false

    var showSleepDialog by remember { mutableStateOf(false) }
    var showLyricsDialog by remember { mutableStateOf(false) }
    var lyricsExpanded by remember { mutableStateOf(false) }
    val sleepActive by viewModel.sleepTimer.isActive.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val lyricsLoading by viewModel.lyricsLoading.collectAsStateWithLifecycle()

    var sliderOverride by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1L)
    val sliderValue = sliderOverride ?: positionMs.toFloat().coerceIn(0f, duration.toFloat())

    // Load lyrics when song changes
    androidx.compose.runtime.LaunchedEffect(current?.id) {
        current?.let { viewModel.loadLyrics(it.artist, it.title) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Close",
                        tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Text("Now Playing", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.MoreVert, "More",
                        tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }

        // Artwork
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1E1E))
                    .clickable { }
            ) {
                if (current?.artworkUrl != null) {
                    AsyncImage(
                        model = current.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(320.dp),
                        contentAlignment = Alignment.Center) {
                        Text("No artwork", color = Color.White.copy(alpha = 0.4f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Title + artist + heart
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        current?.title ?: "Not playing",
                        color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        current?.artist ?: "",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { current?.let { viewModel.toggleFavorite(it, isFav) } }) {
                    Icon(
                        if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        "Favorite",
                        tint = if (isFav) Color(0xFFE91E63) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Progress
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderOverride = it },
                    onValueChangeFinished = {
                        sliderOverride?.let { playback.seekTo(it.toLong()) }
                        sliderOverride = null
                    },
                    valueRange = 0f..duration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White.copy(alpha = 0.9f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMs(sliderValue.toLong()), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Text(formatMs(duration), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Main controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { playback.setShuffle(!shuffleEnabled) }) {
                    Icon(Icons.Filled.Shuffle, "Shuffle",
                        tint = if (shuffleEnabled) Color(0xFF9D4EDD) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = { playback.previous() }) {
                    Icon(Icons.Filled.SkipPrevious, "Previous",
                        tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.White)
                        .clickable { playback.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.Black, strokeWidth = 3.dp)
                    } else {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            tint = Color.Black, modifier = Modifier.size(36.dp)
                        )
                    }
                }
                IconButton(onClick = { playback.next() }) {
                    Icon(Icons.Filled.SkipNext, "Next",
                        tint = Color.White, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { playback.cycleRepeatMode() }) {
                    Icon(
                        if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        "Repeat",
                        tint = if (repeatMode == RepeatMode.OFF) Color.White.copy(alpha = 0.7f) else Color(0xFF9D4EDD),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Secondary icons row
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.Filled.QueueMusic, "Queue", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = { showSleepDialog = true }) {
                    Icon(Icons.Filled.Timer, "Sleep timer",
                        tint = if (sleepActive) Color(0xFF9D4EDD) else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = { showLyricsDialog = true }) {
                    Icon(Icons.Filled.Mic, "Lyrics", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Lyrics section (mockup style)
        item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E1E))
                    .clickable { lyricsExpanded = !lyricsExpanded }
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Lyrics", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Icon(
                            if (lyricsExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            null, tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (lyricsLoading) {
                        Text("Loading lyrics...", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                    } else if (!lyrics.isNullOrBlank()) {
                        val preview = if (lyricsExpanded) lyrics!! else lyrics!!.lines().take(2).joinToString("\n")
                        Text(preview, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp,
                            maxLines = if (lyricsExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis)
                    } else {
                        Text("Lyrics not available", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        if (playerError != null) {
            item {
                Text(playerError!!, color = Color(0xFFFF6B6B), fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 32.dp))
            }
        }
    }

    // Sleep timer dialog
    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Sleep timer") },
            text = {
                Column {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        TextButton(
                            onClick = { viewModel.sleepTimer.start(mins); showSleepDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("$mins minutes") }
                    }
                    if (sleepActive) {
                        TextButton(
                            onClick = { viewModel.sleepTimer.cancel(); showSleepDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cancel timer", color = Color.Red) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSleepDialog = false }) { Text("Close") } }
        )
    }

    // Full lyrics dialog
    if (showLyricsDialog) {
        AlertDialog(
            onDismissRequest = { showLyricsDialog = false },
            title = { Text("Lyrics") },
            text = {
                if (lyricsLoading) Text("Loading...")
                else Text(lyrics ?: "No lyrics found for this song.")
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showLyricsDialog = false }) { Text("Close") } }
        )
    }
}
