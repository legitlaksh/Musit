package com.musit.player.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musit.player.data.repository.LibraryRepository
import com.musit.player.data.repository.MusicRepository
import com.musit.player.model.Song
import com.musit.player.ui.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val music: MusicRepository,
    private val library: LibraryRepository,
    private val lastFm: com.musit.player.data.repository.LastFmRepository
) : ViewModel() {

    val recents: StateFlow<List<Song>> =
        library.observeRecents(20)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<Song>> =
        library.observeFavorites()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _trending = MutableStateFlow<UiState<List<Song>>>(UiState.Loading)
    val trending: StateFlow<UiState<List<Song>>> = _trending.asStateFlow()

    private val _smartMix = MutableStateFlow<UiState<List<Song>>?>(null)
    val smartMix: StateFlow<UiState<List<Song>>?> = _smartMix.asStateFlow()

    private val _recommended = MutableStateFlow<UiState<List<Song>>>(UiState.Loading)
    val recommended: StateFlow<UiState<List<Song>>> = _recommended.asStateFlow()

    init {
        refreshTrending()
        loadRecommendations()
    }

    /**
     * Load Last.fm recommendations based on user's favorites.
     * If no favorites, uses trending songs as seed.
     */
    fun loadRecommendations() {
        viewModelScope.launch {
            _recommended.value = UiState.Loading
            try {
                val favs = favorites.value
                val seed = if (favs.isNotEmpty()) {
                    favs.random()
                } else {
                    val trendingSongs = (_trending.value as? UiState.Success)?.data
                    trendingSongs?.randomOrNull()
                }

                if (seed != null) {
                    val similar = lastFm.getSimilarTracks(seed.artist, seed.title, 20)
                    _recommended.value = if (similar.isEmpty()) {
                        UiState.Error("No recommendations yet. Like some songs!")
                    } else {
                        UiState.Success(similar)
                    }
                } else {
                    _recommended.value = UiState.Error("Play some music first!")
                }
            } catch (e: Exception) {
                _recommended.value = UiState.Error("Couldn't load recommendations.")
            }
        }
    }

    fun refreshTrending() {
        viewModelScope.launch {
            _trending.value = UiState.Loading
            _trending.value = music.trending().fold(
                onSuccess = { songs ->
                    if (songs.isEmpty()) UiState.Error("Nothing trending right now.")
                    else UiState.Success(songs.shuffled())
                },
                onFailure = { e ->
                    UiState.Error(e.message ?: "Couldn't load trending music.")
                }
            )
        }
    }

    /**
     * Generates a smart "Your Mix" playlist based on listening taste:
     * combines liked songs, recently played, and trending, then shuffles.
     */
    fun generateSmartMix() {
        viewModelScope.launch {
            _smartMix.value = UiState.Loading
            try {
                val liked = favorites.value
                val recent = recents.value
                val trendingSongs = (_trending.value as? UiState.Success)?.data ?: emptyList()

                // Combine: prioritize liked, then recent, then trending
                val combined = (liked + recent + trendingSongs)
                    .distinctBy { it.id }
                    .shuffled()
                    .take(30)

                if (combined.isEmpty()) {
                    _smartMix.value = UiState.Error("Play some songs first to generate your mix!")
                } else {
                    _smartMix.value = UiState.Success(combined)
                }
            } catch (e: Exception) {
                _smartMix.value = UiState.Error(e.message ?: "Couldn't generate mix")
            }
        }
    }
}
