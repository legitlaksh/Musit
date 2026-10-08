package com.musit.player.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musit.player.data.repository.LibraryRepository
import com.musit.player.data.repository.LyricsRepository
import com.musit.player.model.Song
import com.musit.player.player.PlaybackRepository
import com.musit.player.player.SleepTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playback: PlaybackRepository,
    private val library: LibraryRepository,
    val sleepTimer: SleepTimer,
    private val lyricsRepo: LyricsRepository
) : ViewModel() {

    private val _lyrics = MutableStateFlow<String?>(null)
    val lyrics: StateFlow<String?> = _lyrics.asStateFlow()

    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()

    fun loadLyrics(artist: String, title: String) {
        viewModelScope.launch {
            _lyricsLoading.value = true
            _lyrics.value = lyricsRepo.getLyrics(artist, title)
            _lyricsLoading.value = false
        }
    }

    val favoriteIds: StateFlow<Set<String>> =
        library.observeFavorites()
            .map { songs -> songs.map { it.id }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun toggleFavorite(song: Song, currentlyFavorite: Boolean) {
        viewModelScope.launch { library.toggleFavorite(song, currentlyFavorite) }
    }
}
