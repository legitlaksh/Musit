package com.musit.player.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musit.player.model.Song
import com.musit.player.ui.UiState
import com.musit.player.ui.components.EmptyView
import com.musit.player.ui.components.ErrorView
import com.musit.player.ui.components.LoadingView
import com.musit.player.ui.components.SongRow

/**
 * Mockup-style search: colorful category cards + Browse All genres.
 * ViewModel logic unchanged — only UI restyled.
 */
@Composable
fun SearchScreen(
    onSongClick: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onToggleFavorite: (Song, Boolean) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    initialQuery: String = "",
    viewModel: SearchViewModel = hiltViewModel()
) {
    // Auto-search when navigated with a query (e.g., from mood chips)
    androidx.compose.runtime.LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            viewModel.search(initialQuery)
        }
    }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val lastSearched by viewModel.lastSearched.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()

    val showSuggestions = query.isNotBlank() && query != lastSearched &&
        suggestions.isNotEmpty() && results !is UiState.Loading

    val isIdle = results == null && query.isBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "Search",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp)
        )

        // Search bar
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            placeholder = { Text("Search songs, artists, albums...", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearQuery() }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF9D4EDD),
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                unfocusedContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) })
        )

        AnimatedVisibility(
            visible = showSuggestions,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                color = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.height(
                        (48.dp * suggestions.size.coerceAtMost(5)).coerceAtMost(240.dp)
                    )
                ) {
                    items(suggestions) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.search(suggestion) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.TrendingUp, null,
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(suggestion, color = MaterialTheme.colorScheme.onBackground, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (val state = results) {
            null -> {
                if (isIdle) {
                    // Mockup browse UI
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        // 2x2 category cards
                        item {
                            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    BrowseCard("Trending", listOf(Color(0xFFE65100), Color(0xFFFF9800)),
                                        Icons.Filled.TrendingUp,
                                        modifier = Modifier.weight(1f), onClick = { viewModel.search("trending") })
                                    BrowseCard("New Releases", listOf(Color(0xFFAD1457), Color(0xFFE91E63)),
                                        Icons.Filled.NewReleases,
                                        modifier = Modifier.weight(1f), onClick = { viewModel.search("new releases") })
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    BrowseCard("Charts", listOf(Color(0xFF1565C0), Color(0xFF42A5F5)),
                                        Icons.Filled.BarChart,
                                        modifier = Modifier.weight(1f), onClick = { viewModel.search("top charts") })
                                    BrowseCard("Moods", listOf(Color(0xFF6A1B9A), Color(0xFFE91E63)),
                                        Icons.Filled.Mood,
                                        modifier = Modifier.weight(1f), onClick = { viewModel.search("mood") })
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // Browse All
                        item {
                            Text("Browse All", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        val genres = listOf("Hindi", "Punjabi", "English", "Lo-Fi", "Hip Hop", "Pop", "EDM")
                        val genreColors = listOf(
                            Color(0xFFE65100), Color(0xFF2E7D32), Color(0xFF1565C0),
                            Color(0xFF5D4037), Color(0xFF6A1B9A), Color(0xFFAD1457), Color(0xFF00838F)
                        )
                        items(genres.size) { i ->
                            GenreRow(
                                name = genres[i],
                                color = genreColors[i % genreColors.size],
                                onClick = { viewModel.search(genres[i]) }
                            )
                        }

                        // Recent searches below genres
                        if (history.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Recent searches", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    TextButton(onClick = { viewModel.clearHistory() }) {
                                        Text("Clear", color = Color(0xFF9D4EDD))
                                    }
                                }
                            }
                            items(history, key = { it }) { past ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { viewModel.search(past) }
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.History, null,
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(past, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else if (history.isNotEmpty()) {
                    // History when query typed but no results yet
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(history, key = { it }) { past ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { viewModel.search(past) }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.History, null,
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(past, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    EmptyView(message = "Search millions of songs", hint = "Type above — suggestions appear as you type")
                }
            }
            is UiState.Loading -> LoadingView(message = "Searching…")
            is UiState.Error -> ErrorView(
                message = state.message,
                onRetry = { viewModel.search(query.ifBlank { lastSearched }) }
            )
            is UiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    itemsIndexed(state.data, key = { _, s -> s.id }) { index, song ->
                        val fav = song.id in favoriteIds
                        SongRow(
                            song = song,
                            onClick = { onSongClick(state.data, index) },
                            isFavorite = fav,
                            onToggleFavorite = { onToggleFavorite(it, fav) },
                            onPlayNext = onPlayNext,
                            onAddToPlaylist = onAddToPlaylist
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseCard(
    title: String,
    gradient: List<Color>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(gradient))
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Column {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f), modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GenreRow(name: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(name.first().toString(), color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(name, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
    }
}
