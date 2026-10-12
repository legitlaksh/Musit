package com.musit.player.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musit.player.data.local.DownloadEntity
import com.musit.player.data.local.LocalMusicScanner
import com.musit.player.data.repository.DownloadRepository
import com.musit.player.data.repository.LibraryRepository
import com.musit.player.data.repository.MusicRepository
import com.musit.player.data.repository.PlaylistInfo
import com.musit.player.model.Song
import com.musit.player.player.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val music: MusicRepository,
    private val scanner: LocalMusicScanner,
    val playback: PlaybackRepository,
    private val downloadRepository: DownloadRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val favorites: StateFlow<List<Song>> =
        library.observeFavorites()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteIds: StateFlow<Set<String>> =
        favorites.map { songs -> songs.map { it.id }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val recents: StateFlow<List<Song>> =
        library.observeRecents(30)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistInfo>> =
        library.observePlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory: StateFlow<List<String>> =
        library.observeSearchHistory()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _localSongs = MutableStateFlow<List<Song>>(emptyList())
    val localSongs: StateFlow<List<Song>> = _localSongs.asStateFlow()

    private val _localLoading = MutableStateFlow(false)
    val localLoading: StateFlow<Boolean> = _localLoading.asStateFlow()

    // ------------------------------------------------------------------
    // offline downloads
    // ------------------------------------------------------------------

    val downloads: StateFlow<List<DownloadEntity>> =
        downloadRepository.observeDownloads()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteDownload(songId: String) {
        viewModelScope.launch { downloadRepository.removeDownload(songId) }
    }

    /** Plays a downloaded song; the rest of the downloads list becomes the queue. */
    fun playDownload(download: DownloadEntity, all: List<DownloadEntity>) {
        val songs = all.map { entity ->
            Song(
                id = entity.songId,
                title = entity.title,
                artist = entity.artist,
                artworkUrl = entity.artworkUrl
            )
        }
        playback.playQueue(songs, all.indexOf(download).coerceAtLeast(0))
    }

    fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun loadLocalSongs() {
        if (!hasAudioPermission() || _localLoading.value) return
        viewModelScope.launch {
            _localLoading.value = true
            try {
                val songs = scanner.scan()
                scanner.lastScanUris.forEach { (id, uri) ->
                    music.registerLocalUri(id, uri)
                }
                _localSongs.value = songs
            } finally {
                _localLoading.value = false
            }
        }
    }

    fun toggleFavorite(song: Song, currentlyFavorite: Boolean) {
        viewModelScope.launch { library.toggleFavorite(song, currentlyFavorite) }
    }

    fun createPlaylist(name: String, firstSong: Song? = null) {
        viewModelScope.launch {
            val id = library.createPlaylist(name)
            if (firstSong != null) library.addToPlaylist(id, firstSong)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { library.deletePlaylist(id) }
    }

    fun addToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch { library.addToPlaylist(playlistId, song) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { library.clearSearchHistory() }
    }

    fun playlistSongs(playlistId: Long): StateFlow<List<Song>> =
        library.observePlaylistSongs(playlistId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun playlistName(playlistId: Long): StateFlow<String?> =
        library.observePlaylistName(playlistId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun removeFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch { library.removeFromPlaylist(playlistId, songId) }
    }
}
