package com.musit.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.musit.player.innertube.InnerTube
import com.musit.player.innertube.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App-wide playback controller. The [MusicService] owns the ExoPlayer;
 * this singleton drives it through a [MediaController] and exposes
 * player state as StateFlows for the UI.
 */
object PlayerManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val queue = mutableListOf<Song>()
    private var queueIndex = -1

    private var polling = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                val d = controller?.duration ?: -1L
                if (d > 0) _durationMs.value = d
            }
        }
    }

    /** Connects to the [MusicService] media session. Call once from the activity. */
    fun init(context: Context) {
        if (controllerFuture != null) return
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, MusicService::class.java))
        controllerFuture = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture?.addListener(
            {
                controller = try {
                    controllerFuture?.get()
                } catch (e: Exception) {
                    null
                }
                controller?.addListener(playerListener)
                syncState()
                startPolling()
            },
            MoreExecutors.directExecutor()
        )
    }

    /** Replaces the queue with [songs] and starts playing at [startIndex]. */
    fun playQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        queue.clear()
        queue.addAll(songs)
        queueIndex = startIndex.coerceIn(songs.indices)
        play(queue[queueIndex])
    }

    /** Resolves the stream URL for [song] and starts playback. */
    fun play(song: Song) {
        val existing = queue.indexOfFirst { it.videoId == song.videoId }
        if (existing >= 0) {
            queueIndex = existing
        } else {
            queue.add(song)
            queueIndex = queue.size - 1
        }
        _currentSong.value = song
        _positionMs.value = 0L
        _durationMs.value = 0L
        _isLoading.value = true

        scope.launch {
            val url = withContext(Dispatchers.IO) {
                InnerTube.getAudioStreamUrl(song.videoId)
            }
            val c = controller
            if (url.isNullOrEmpty() || c == null) {
                _isLoading.value = false
                return@launch
            }
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .apply {
                    val art = song.thumbnailUrl
                    if (!art.isNullOrEmpty()) setArtworkUri(Uri.parse(art))
                }
                .build()
            val item = MediaItem.Builder()
                .setUri(url)
                .setMediaId(song.videoId)
                .setMediaMetadata(metadata)
                .build()
            c.setMediaItem(item)
            c.prepare()
            c.play()
            _isLoading.value = false
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        if (queueIndex + 1 < queue.size) play(queue[queueIndex + 1])
    }

    fun previous() {
        if (queueIndex - 1 >= 0) play(queue[queueIndex - 1])
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    private fun syncState() {
        val c = controller ?: return
        _isPlaying.value = c.isPlaying
        _positionMs.value = c.currentPosition.coerceAtLeast(0L)
        val d = c.duration
        if (d > 0) _durationMs.value = d
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        scope.launch {
            while (true) {
                syncState()
                delay(500)
            }
        }
    }
}
