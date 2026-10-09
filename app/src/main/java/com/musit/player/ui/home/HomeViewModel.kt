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
    private val lastFm: com.musit.player.data.repository.LastFmRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
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
     * Load Last.fm recommendations based on user's onboarding preferences
     * (genres, artists, mood) and favorites.
     */
    fun loadRecommendations() {
        viewModelScope.launch {
            _recommended.value = UiState.Loading
            try {
                val prefs = context.getSharedPreferences("musit_prefs", android.content.Context.MODE_PRIVATE)
                val genres = prefs.getStringSet("user_genres", emptySet()) ?: emptySet()
                val artists = prefs.getStringSet("user_artists", emptySet()) ?: emptySet()
                val mood = prefs.getString("user_mood", "") ?: ""

                val allSongs = mutableListOf<com.musit.player.model.Song>()

                // 1. From favorite songs (most personal)
                val favs = favorites.value
                if (favs.isNotEmpty()) {
                    val seed = favs.random()
                    allSongs.addAll(lastFm.getSimilarTracks(seed.artist, seed.title, 10))
                }

                // 2. From onboarding artists
                if (artists.isNotEmpty() && allSongs.size < 15) {
                    val artist = artists.random()
                    // Get similar artists' top tracks via tag
                    allSongs.addAll(lastFm.getTopTracksByTag(artist, 10))
                }

                // 3. From onboarding genres
                if (genres.isNotEmpty() && allSongs.size < 15) {
                    val genre = genres.random()
                    allSongs.addAll(lastFm.getTopTracksByTag(genre, 10))
                }

                // 4. From mood
                if (mood.isNotBlank() && allSongs.size < 15) {
                    allSongs.addAll(lastFm.getTopTracksByTag(mood, 10))
                }

                // Deduplicate
                val unique = allSongs.distinctBy { "${it.artist}:${it.title}" }.take(20)

                _recommended.value = if (unique.isEmpty()) {
                    UiState.Error("No recommendations yet. Like some songs!")
                } else {
                    UiState.Success(unique.shuffled())
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
