package com.musit.player.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.musit.player.data.repository.LyricsResult
import com.musit.player.player.RepeatMode
import com.musit.player.ui.formatMs
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Full-bleed artwork player: the song artwork fills the screen with a vertical
 * gradient blend (transparent top -> dark bottom) so white text/controls stay
 * readable. Playback logic unchanged — UI only.
 */
private val WavyShape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val lobes = 12
    val steps = lobes * 16
    val baseR = min(size.width, size.height) / 2f
    val amp = baseR * 0.07f
    for (i in 0..steps) {
        val a = (i.toFloat() / steps) * (2f * PI.toFloat())
        // Radius oscillates between (baseR - 2*amp) and baseR: gentle scalloped edge.
        val r = baseR - amp * (1f - cos(lobes * a))
        val x = cx + r * cos(a)
        val y = cy + r * sin(a)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

@Composable
fun PlayerScreen(
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    onArtistClick: (String) -> Unit = {},
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

    // Artwork scale animation (subtle pulse when playing)
    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.02f else 1f,
        animationSpec = tween(durationMillis = 500),
        label = "artworkScale"
    )
    val shuffleEnabled by playback.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by playback.repeatMode.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()

    val current = song
    val isFav = current?.let { favoriteIds.contains(it.id) } ?: false

    var showSleepDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    val playbackSpeed by playback.playbackSpeed.collectAsStateWithLifecycle()
    var showLyricsDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val sleepActive by viewModel.sleepTimer.isActive.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val lyricsLoading by viewModel.lyricsLoading.collectAsStateWithLifecycle()
    val lyricsOffsetMs by viewModel.lyricsOffsetMs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun shareSong() {
        current?.let { song ->
            val youtubeLink = "https://music.youtube.com/watch?v=${song.id}"
            val shareText = "🎵 ${song.title} by ${song.artist}\n$youtubeLink\nListening on musit"
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share song"))
        }
    }

    var sliderOverride by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1L)
    val sliderValue = sliderOverride ?: positionMs.toFloat().coerceIn(0f, duration.toFloat())

    // Load lyrics when song changes
    LaunchedEffect(current?.id) {
        current?.let { viewModel.loadLyrics(it.artist, it.title) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Full-bleed artwork background
        if (current?.artworkUrl != null) {
            AsyncImage(
                model = current.artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = artworkScale
                        scaleY = artworkScale
                    },
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No artwork", color = Color.White.copy(alpha = 0.4f))
            }
        }

        // 2. Blend: transparent top -> dark bottom so text/controls stay readable
        // Stronger gradient for readability over bright artwork
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.25f),
                        0.35f to Color.Transparent,
                        0.50f to Color.Black.copy(alpha = 0.45f),
                        0.70f to Color.Black.copy(alpha = 0.75f),
                        1f to Color.Black.copy(alpha = 0.92f)
                    )
                )
        )
        // Top scrim so the dismiss button is visible over bright artwork
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.4f),
                        1f to Color.Transparent
                    )
                )
        )

        // 3. Top bar: dismiss
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.KeyboardArrowDown, "Close",
                    tint = Color.White, modifier = Modifier.size(32.dp)
                )
            }
        }

        // 4. Bottom content: title, progress, controls, utility row
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            // Title + artist + download/like buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        current?.title ?: "Not playing",
                        color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                        lineHeight = 34.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        current?.artist ?: "",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 17.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(
                            enabled = current?.artist?.isNotBlank() == true
                        ) {
                            current?.artist?.let { onArtistClick(it) }
                        }
                    )
                    if (playerError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            playerError!!, color = Color(0xFFFF8A80), fontSize = 13.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Download (white rounded square)
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .clickable {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Downloads coming soon")
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Download, "Download",
                            tint = Color(0xFF1A1A1A), modifier = Modifier.size(26.dp)
                        )
                    }
                    // Like (white rounded square)
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .clickable { current?.let { viewModel.toggleFavorite(it, isFav) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            "Like",
                            tint = if (isFav) Color(0xFFF06292) else Color(0xFF1A1A1A),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Progress bar
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
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatMs(sliderValue.toLong()), color = Color.White, fontSize = 13.sp)
                Text(formatMs(duration), color = Color.White, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main controls: previous / wavy play-pause / next
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .clickable { playback.previous() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.SkipPrevious, "Previous",
                        tint = Color.White, modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.width(26.dp))
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(WavyShape)
                        .background(Color.White)
                        .clickable { playback.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            color = Color(0xFF1A1A1A),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            tint = Color(0xFF1A1A1A), modifier = Modifier.size(42.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(26.dp))
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .clickable { playback.next() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.SkipNext, "Next",
                        tint = Color.White, modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Bottom utility row: 5 outlined buttons + white circular More
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UtilityButton(
                        icon = Icons.Filled.QueueMusic,
                        active = false,
                        onClick = onOpenQueue,
                        description = "Queue"
                    )
                    UtilityButton(
                        icon = Icons.Filled.Nightlight,
                        active = sleepActive,
                        onClick = { showSleepDialog = true },
                        description = "Sleep timer"
                    )
                    UtilityButton(
                        icon = Icons.AutoMirrored.Filled.Notes,
                        active = false,
                        onClick = { showLyricsDialog = true },
                        description = "Lyrics"
                    )
                    UtilityButton(
                        icon = Icons.Filled.Shuffle,
                        active = shuffleEnabled,
                        onClick = { playback.setShuffle(!shuffleEnabled) },
                        description = "Shuffle"
                    )
                    UtilityButton(
                        icon = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        active = repeatMode != RepeatMode.OFF,
                        onClick = { playback.cycleRepeatMode() },
                        description = "Repeat"
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { showMoreMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MoreVert, "More",
                            tint = Color(0xFF1A1A1A), modifier = Modifier.size(26.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        containerColor = Color(0xFF1E1E1E)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share", color = Color.White) },
                            leadingIcon = {
                                Icon(Icons.Filled.Share, null, tint = Color.White)
                            },
                            onClick = { showMoreMenu = false; shareSong() }
                        )
                        DropdownMenuItem(
                            text = { Text("Playback speed", color = Color.White) },
                            leadingIcon = {
                                Icon(Icons.Filled.FastForward, null, tint = Color.White)
                            },
                            onClick = { showMoreMenu = false; showSpeedDialog = true }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    // Sleep timer dialog
    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Sleep timer") },
            text = {
                Column {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        TextButton(
                            onClick = { viewModel.sleepTimer.start(mins); showSleepDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("$mins minutes", color = Color.White) }
                    }
                    if (sleepActive) {
                        TextButton(
                            onClick = { viewModel.sleepTimer.cancel(); showSleepDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cancel timer", color = Color(0xFFFF8A80)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSleepDialog = false }) {
                    Text("Close", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    // Playback speed dialog
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Playback speed") },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { speed ->
                        TextButton(
                            onClick = { playback.setPlaybackSpeed(speed); showSpeedDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${speed}x",
                                color = Color.White,
                                fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    // Full lyrics dialog
    if (showLyricsDialog) {
        AlertDialog(
            onDismissRequest = { showLyricsDialog = false },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Lyrics") },
            text = {
                if (lyricsLoading) {
                    Text("Loading...", color = Color.White.copy(alpha = 0.6f))
                } else {
                    when (val l = lyrics) {
                        is LyricsResult.Synced -> Column {
                            // Sync adjustment controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { viewModel.adjustLyricsOffset(-500) }) {
                                    Text("-0.5s", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                }
                                Text(
                                    if (lyricsOffsetMs == 0L) "Sync" else "${lyricsOffsetMs / 1000.0}s",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                TextButton(onClick = { viewModel.adjustLyricsOffset(500) }) {
                                    Text("+0.5s", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                }
                            }
                            SyncedLyricsView(
                                lines = l.lines,
                                currentPositionMs = positionMs,
                                offsetMs = lyricsOffsetMs,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(400.dp)
                            )
                        }
                        is LyricsResult.Plain -> Text(
                            l.text,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        LyricsResult.NotFound -> Text(
                            "No lyrics found for this song.",
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLyricsDialog = false }) {
                    Text("Close", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}

/**
 * Small outlined rounded-square utility button (transparent bg, white border).
 * Active state gets a subtle white fill.
 */
@Composable
private fun UtilityButton(
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    description: String
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(shape)
            .background(if (active) Color.White.copy(alpha = 0.25f) else Color.Transparent)
            .border(1.dp, Color.White.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}
