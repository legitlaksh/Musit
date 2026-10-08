package com.musit.player.ui.artist

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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.musit.player.model.Song
import com.musit.player.ui.UiState
import com.musit.player.ui.components.ErrorView
import com.musit.player.ui.components.LoadingView

/**
 * Artist detail: shows artist's top songs.
 * Searches for the artist's music via InnerTube.
 */
@Composable
fun ArtistDetailScreen(
    artistName: String,
    onBack: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    viewModel: ArtistViewModel = hiltViewModel()
) {
    val songsState by viewModel.songs.collectAsStateWithLifecycle()

    LaunchedEffect(artistName) {
        viewModel.loadArtistSongs(artistName)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Top bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Text("Artist", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
            }
        }

        // Artist header
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(120.dp).clip(CircleShape)
                        .background(Color(0xFF6A1B9A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, null, tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(56.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(artistName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Top songs", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Songs
        when (val state = songsState) {
            is UiState.Loading -> item { LoadingView() }
            is UiState.Error -> item { ErrorView(state.message, onRetry = { viewModel.loadArtistSongs(artistName) }) }
            is UiState.Success -> {
                itemsIndexed(state.data) { idx, song ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onSongClick(state.data, idx) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${idx + 1}", color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp, modifier = Modifier.width(28.dp))
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
            null -> {}
        }
    }
}
