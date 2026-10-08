package com.musit.player.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.musit.player.model.Song
import com.musit.player.ui.UiState
import com.musit.player.ui.components.EmptyView
import com.musit.player.ui.components.ErrorView
import com.musit.player.ui.components.LoadingView
import com.musit.player.ui.components.SectionHeader
import com.musit.player.ui.components.SongRow

@Composable
fun HomeScreen(
    onSongClick: (List<Song>, Int) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val trending by viewModel.trending.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text(
                text = "musit",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
            )
        }

        if (recents.isNotEmpty()) {
            item {
                SectionHeader(title = "Recently played")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recents, key = { it.id }) { song ->
                        RecentCard(
                            song = song,
                            onClick = {
                                val index = recents.indexOfFirst { it.id == song.id }
                                onSongClick(recents, index.coerceAtLeast(0))
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        item {
            SectionHeader(title = "Trending now")
        }

        when (val state = trending) {
            is UiState.Loading -> item {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                    LoadingView()
                }
            }
            is UiState.Error -> item {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                    ErrorView(
                        message = state.message,
                        onRetry = { viewModel.refreshTrending() }
                    )
                }
            }
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                            EmptyView("Nothing here yet", hint = "Check your connection and retry")
                        }
                    }
                } else {
                    items(state.data, key = { it.id }) { song ->
                        val index = state.data.indexOfFirst { it.id == song.id }
                        SongRow(
                            song = song,
                            onClick = { onSongClick(state.data, index.coerceAtLeast(0)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentCard(song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = song.artworkUrl,
            contentDescription = null,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = song.artist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
