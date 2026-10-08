package com.musit.player.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musit.player.data.repository.LibraryRepository
import com.musit.player.data.repository.MusicRepository
import com.musit.player.model.Song
import com.musit.player.ui.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val music: MusicRepository,
    private val library: LibraryRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _results = MutableStateFlow<UiState<List<Song>>?>(null)
    val results: StateFlow<UiState<List<Song>>?> = _results.asStateFlow()

    private val _lastSearched = MutableStateFlow("")
    val lastSearched: StateFlow<String> = _lastSearched.asStateFlow()

    val history: StateFlow<List<String>> =
        library.observeSearchHistory()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Favorite song ids, for cheap per-row lookups. */
    val favoriteIds: StateFlow<Set<String>> =
        library.observeFavorites()
            .map { songs -> songs.map { it.id }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private var searchJob: Job? = null

    init {
        collectDebouncedQueries()
    }

    @OptIn(FlowPreview::class)
    private fun collectDebouncedQueries() {
        viewModelScope.launch {
            _query
                .debounce(350)
                .distinctUntilChanged()
                .filter { it.isNotBlank() && it != _lastSearched.value }
                .collect { q ->
                    _suggestions.value = music.suggestions(q)
                }
        }
    }

    fun onQueryChange(q: String) {
        _query.value = q
        if (q.isBlank()) {
            _suggestions.value = emptyList()
        }
    }

    fun clearQuery() {
        _query.value = ""
        _suggestions.value = emptyList()
        _results.value = null
        _lastSearched.value = ""
    }

    fun search(q: String) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _query.value = trimmed
            _suggestions.value = emptyList()
            _results.value = UiState.Loading
            _results.value = music.search(trimmed).fold(
                onSuccess = { songs ->
                    _lastSearched.value = trimmed
                    if (songs.isEmpty()) UiState.Error("No results for \"$trimmed\"")
                    else UiState.Success(songs)
                },
                onFailure = { e ->
                    UiState.Error(e.message ?: "Search failed. Check your connection.")
                }
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch { library.clearSearchHistory() }
    }

    fun toggleFavorite(song: Song, currentlyFavorite: Boolean) {
        viewModelScope.launch { library.toggleFavorite(song, currentlyFavorite) }
    }
}
