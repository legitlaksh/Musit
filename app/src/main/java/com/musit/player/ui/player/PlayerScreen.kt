package com.musit.player.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.graphics.Brush
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
 * Echo-style full-bleed player: artwork fills the screen,
 * controls float over a bottom gradient.
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
    val sleepActive by viewModel.sleepTimer.isActive.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val lyricsLoading by viewModel.lyricsLoading.collectAsStateWithLifecycle()

    var sliderOverride by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1L)
    val sliderValue = sliderOverride ?: positionMs.toFloat().coerceIn(0f, duration.toFloat())

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-bleed artwork
        if (current?.artworkUrl != null) {
            AsyncImage(
                model = current.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212))
            )
        }

        // Bottom gradient for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.92f)
                        ),
                        startY = 300f
                    )
                )
        )

        // Top bar: close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        // Bottom content
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Title + artist + actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current?.title ?: "Not playing",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = current?.artist ?: "",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Download button
                IconButton(
                    onClick = { /* TODO: offline download */ },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = "Download",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                // Favorite button
                IconButton(
                    onClick = { current?.let { viewModel.toggleFavorite(it, isFav) } },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Icon(
                        if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFav) Color(0xFFE91E63) else Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress bar
            Slider(
                value = sliderValue,
                onValueChange = { sliderOverride = it },
                onValueChangeFinished = {
                    sliderOverride?.let { playback.seekTo(it.toLong()) }
                    sliderOverride = null
                },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White.copy(alpha = 0.9f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(sliderValue.toLong()),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
                Text(
                    text = formatMs(duration),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main controls: prev / play / next
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { playback.previous() },
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(
                    onClick = { playback.togglePlayPause() },
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { playback.next() },
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom icon row: queue / sleep / lyrics / shuffle / repeat / more
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenQueue) {
                    Icon(
                        Icons.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { showSleepDialog = true }) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = "Sleep timer",
                        tint = if (sleepActive) Color(0xFF9D4EDD) else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = {
                    current?.let { viewModel.loadLyrics(it.artist, it.title) }
                    showLyricsDialog = true
                }) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "Lyrics",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { playback.setShuffle(!shuffleEnabled) }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled) Color(0xFF9D4EDD) else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { playback.cycleRepeatMode() }) {
                    Icon(
                        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode == RepeatMode.OFF) Color.White.copy(alpha = 0.9f) else Color(0xFF9D4EDD),
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(
                    onClick = { /* more options */ },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (playerError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = playerError!!,
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp
                )
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
                                onClick = {
                                    viewModel.sleepTimer.start(mins)
                                    showSleepDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("$mins minutes")
                            }
                        }
                        if (sleepActive) {
                            TextButton(
                                onClick = {
                                    viewModel.sleepTimer.cancel()
                                    showSleepDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel timer", color = Color.Red)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showSleepDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Lyrics dialog
        if (showLyricsDialog) {
            AlertDialog(
                onDismissRequest = { showLyricsDialog = false },
                title = { Text("Lyrics") },
                text = {
                    if (lyricsLoading) {
                        Text("Loading lyrics...")
                    } else if (lyrics != null) {
                        Text(lyrics!!)
                    } else {
                        Text("No lyrics found for this song.")
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showLyricsDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
