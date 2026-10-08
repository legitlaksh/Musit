package com.musit.player.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musit.player.model.Song
import com.musit.player.network.InnerTubeMusicSource
import com.musit.player.ui.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads an artist's top songs via InnerTube search.
 */
@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val musicSource: InnerTubeMusicSource
) : ViewModel() {

    private val _songs = MutableStateFlow<UiState<List<Song>>?>(null)
    val songs: StateFlow<UiState<List<Song>>?> = _songs.asStateFlow()

    fun loadArtistSongs(artistName: String) {
        viewModelScope.launch {
            _songs.value = UiState.Loading
            try {
                val result = musicSource.search(artistName)
                result.fold(
                    onSuccess = { songs ->
                        _songs.value = if (songs.isEmpty()) {
                            UiState.Error("No songs found for $artistName")
                        } else {
                            UiState.Success(songs)
                        }
                    },
                    onFailure = { e ->
                        _songs.value = UiState.Error(e.message ?: "Failed to load artist songs")
                    }
                )
            } catch (e: Exception) {
                _songs.value = UiState.Error(e.message ?: "Failed to load artist songs")
            }
        }
    }
}
