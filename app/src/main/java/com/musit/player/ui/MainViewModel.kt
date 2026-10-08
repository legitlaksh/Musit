package com.musit.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musit.player.data.repository.LibraryRepository
import com.musit.player.data.repository.PlaylistInfo
import com.musit.player.data.store.SettingsStore
import com.musit.player.model.Song
import com.musit.player.player.PlaybackRepository
import com.musit.player.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity-level view model: theme, global playback actions and the shared
 * "add to playlist" dialog state used by every screen's song menu.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    val playback: PlaybackRepository,
    private val library: LibraryRepository,
    settingsStore: SettingsStore
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> =
        settingsStore.themeMode.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK
        )

    val playlists: StateFlow<List<PlaylistInfo>> =
        library.observePlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _pendingPlaylistSong = MutableStateFlow<Song?>(null)
    val pendingPlaylistSong: StateFlow<Song?> = _pendingPlaylistSong.asStateFlow()

    fun playQueue(songs: List<Song>, index: Int) = playback.playQueue(songs, index)

    fun playNext(song: Song) = playback.playNext(song)

    fun toggleFavorite(song: Song, currentlyFavorite: Boolean) {
        viewModelScope.launch { library.toggleFavorite(song, currentlyFavorite) }
    }

    fun requestAddToPlaylist(song: Song) {
        _pendingPlaylistSong.value = song
    }

    fun dismissPlaylistDialog() {
        _pendingPlaylistSong.value = null
    }

    fun confirmAddToPlaylist(info: PlaylistInfo) {
        viewModelScope.launch {
            _pendingPlaylistSong.value?.let { library.addToPlaylist(info.id, it) }
            _pendingPlaylistSong.value = null
        }
    }

    fun createPlaylistAndAdd(name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@launch
            val id = library.createPlaylist(trimmed)
            _pendingPlaylistSong.value?.let { library.addToPlaylist(id, it) }
            _pendingPlaylistSong.value = null
        }
    }
}
