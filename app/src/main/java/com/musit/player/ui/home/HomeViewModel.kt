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
    library: LibraryRepository
) : ViewModel() {

    val recents: StateFlow<List<Song>> =
        library.observeRecents(20)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _trending = MutableStateFlow<UiState<List<Song>>>(UiState.Loading)
    val trending: StateFlow<UiState<List<Song>>> = _trending.asStateFlow()

    init {
        refreshTrending()
    }

    fun refreshTrending() {
        viewModelScope.launch {
            _trending.value = UiState.Loading
            _trending.value = music.trending().fold(
                onSuccess = { songs ->
                    if (songs.isEmpty()) UiState.Error("Nothing trending right now.")
                    else UiState.Success(songs)
                },
                onFailure = { e ->
                    UiState.Error(e.message ?: "Couldn't load trending music.")
                }
            )
        }
    }
}
