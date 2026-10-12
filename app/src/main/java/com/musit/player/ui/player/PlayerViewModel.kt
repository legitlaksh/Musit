package com.musit.player.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.musit.player.data.repository.DownloadRepository
import com.musit.player.data.repository.LibraryRepository
import com.musit.player.data.repository.LyricsRepository
import com.musit.player.data.repository.LyricsResult
import com.musit.player.download.DownloadService
import com.musit.player.model.Song
import com.musit.player.player.PlaybackRepository
import com.musit.player.player.SleepTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playback: PlaybackRepository,
    private val library: LibraryRepository,
    val sleepTimer: SleepTimer,
    private val lyricsRepo: LyricsRepository,
    private val downloads: DownloadRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _lyrics = MutableStateFlow<LyricsResult>(LyricsResult.NotFound)
    val lyrics: StateFlow<LyricsResult> = _lyrics.asStateFlow()

    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()

    // Lyrics sync offset in milliseconds (user adjustable, +/-)
    private val _lyricsOffsetMs = MutableStateFlow(0L)
    val lyricsOffsetMs: StateFlow<Long> = _lyricsOffsetMs.asStateFlow()

    fun adjustLyricsOffset(deltaMs: Long) {
        _lyricsOffsetMs.value = (_lyricsOffsetMs.value + deltaMs).coerceIn(-10000L, 10000L)
    }

    fun resetLyricsOffset() {
        _lyricsOffsetMs.value = 0L
    }

    fun loadLyrics(song: com.musit.player.model.Song) {
        viewModelScope.launch {
            _lyricsLoading.value = true
            _lyrics.value = lyricsRepo.getLyrics(song.artist, song.title, song.durationSec)
            _lyricsLoading.value = false
        }
    }

    /** Backwards-compatible overload. */
    fun loadLyrics(artist: String, title: String, durationSec: Long? = null) {
        viewModelScope.launch {
            _lyricsLoading.value = true
            _lyrics.value = lyricsRepo.getLyrics(artist, title, durationSec)
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

    // ------------------------------------------------------------------
    // offline downloads
    // ------------------------------------------------------------------

    private val _downloadMessage = MutableStateFlow<String?>(null)
    val downloadMessage: StateFlow<String?> = _downloadMessage.asStateFlow()

    fun clearDownloadMessage() {
        _downloadMessage.value = null
    }

    /** Starts an offline download of [song] (or reports it is already saved). */
    fun downloadSong(song: Song) {
        viewModelScope.launch {
            if (downloads.isDownloaded(song.id)) {
                _downloadMessage.value = "Already downloaded"
            } else {
                DownloadService.enqueue(context, song)
                _downloadMessage.value = "Downloading ${song.title}"
            }
        }
    }
}
