package com.musit.player.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import com.musit.player.model.Song
import com.musit.player.ui.UiState
import com.musit.player.ui.components.ErrorView
import com.musit.player.ui.components.LoadingView
import java.util.Calendar

/**
 * Mockup-style home: greeting, quick access grid, Made for You, Trending.
 */
@Composable
fun HomeScreen(
    onSongClick: (List<Song>, Int) -> Unit,
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLikedClick: () -> Unit = {},
    onMoodClick: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val trending by viewModel.trending.collectAsStateWithLifecycle()
    var selectedChip by remember { mutableStateOf("All") }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    val chips = listOf("All", "Music", "Podcasts", "Chill", "Work")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Header: musit + settings
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "musit",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        // Filter chips
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chips) { chip ->
                    val selected = selectedChip == chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) Color(0xFF9D4EDD) else Color.White.copy(alpha = 0.08f))
                            .clickable {
                                selectedChip = chip
                                if (chip != "All") onMoodClick(chip)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = chip,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Greeting
        item {
            Text(
                text = greeting,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Quick access 2x2 grid
        item {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAccessCard(
                        title = "Liked Songs",
                        icon = { Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(24.dp)) },
                        bgColor = Color(0xFF1A1A2E),
                        modifier = Modifier.weight(1f),
                        onClick = onLikedClick
                    )
                    val recentSong = recents.firstOrNull()
                    QuickAccessCard(
                        title = "Recent",
                        artworkUrl = recentSong?.artworkUrl,
                        modifier = Modifier.weight(1f),
                        onClick = { if (recents.isNotEmpty()) onSongClick(recents, 0) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAccessCard(
                        title = "Downloads",
                        icon = { Icon(Icons.Filled.Download, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp)) },
                        bgColor = Color(0xFF1A1A2E),
                        modifier = Modifier.weight(1f),
                        onClick = {}
                    )
                    QuickAccessCard(
                        title = "Your Mix",
                        bgColor = Color(0xFF6A1B9A),
                        modifier = Modifier.weight(1f),
                        onClick = {}
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Made for You
        item {
            Text(
                text = "Made for You",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val mixes = listOf(
                    "Chill Mix" to "Relax and unwind",
                    "Workout Mix" to "High energy"
                )
                items(mixes) { (title, subtitle) ->
                    Column(modifier = Modifier.width(160.dp)) {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF6A1B9A), Color(0xFF9D4EDD))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(title.first().toString(), color = Color.White.copy(alpha = 0.3f), fontSize = 64.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Trending
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Trending", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("See All", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp,
                    modifier = Modifier.clickable { })
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        when (val t = trending) {
            is UiState.Loading -> item { LoadingView() }
            is UiState.Error -> item { ErrorView(t.message, onRetry = viewModel::refreshTrending) }
            is UiState.Success -> {
                items(t.data.take(5)) { song ->
                    val idx = t.data.indexOf(song)
                    MockupSongRow(song = song, onClick = { onSongClick(t.data, idx) })
                }
            }
            null -> {}
        }
    }
}

@Composable
private fun QuickAccessCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    artworkUrl: String? = null,
    bgColor: Color = Color(0xFF1E1E1E),
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (artworkUrl != null) {
                AsyncImage(model = artworkUrl, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (icon != null) {
                icon()
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MockupSongRow(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.artworkUrl,
            contentDescription = null,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = {}) {
            Icon(Icons.Filled.MoreVert, null, tint = Color.White.copy(alpha = 0.5f))
        }
        IconButton(onClick = onClick) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
        }
    }
}
