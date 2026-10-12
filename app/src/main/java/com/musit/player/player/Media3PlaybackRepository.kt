package com.musit.player.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.musit.player.data.repository.LibraryRepository
import com.musit.player.data.repository.MusicRepository
import com.musit.player.data.store.SettingsStore
import com.musit.player.model.QueueState
import com.musit.player.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PlaybackRepository] backed by Media3. Drives the [PlayerService]'s
 * ExoPlayer through a [MediaController]; the queue itself is managed here
 * (one media item at a time) so shuffle/repeat semantics stay explicit.
 *
 * Stream URLs are resolved via [MusicRepository] on IO. If ExoPlayer reports
 * an error, the current song is retried once automatically; a second failure
 * surfaces a friendly message through [playerError].
 */
@Singleton
class Media3PlaybackRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val music: MusicRepository,
    private val library: LibraryRepository,
    private val stats: com.musit.player.data.repository.StatsRepository,
    private val settings: SettingsStore
) : PlaybackRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    override val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    override val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private val _queueState = MutableStateFlow(QueueState(null, emptyList(), -1))
    override val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    override val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    override val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _audioSessionId = MutableStateFlow(0)
    override val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    private val _activeAudioSessionId = MutableStateFlow(0)
    override val activeAudioSessionId: StateFlow<Int> = _activeAudioSessionId.asStateFlow()

    // ------------------------------------------------------------------
    // crossfade state
    // ------------------------------------------------------------------

    private var crossfadeEnabled = true
    private var crossfadeSec = 5

    /** True once the crossfade has handed off and the secondary player is audible. */
    private var xfadeActive = false

    /** True while a fade between the two players is running. */
    private var crossfading = false

    private var crossfadeJob: Job? = null

    /** Song id for which stats.recordPlay has already been counted (Fix 4). */
    private var statsRecordedSongId: String? = null

    private val xfadeListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && xfadeActive) {
                finishXfadeToPrimaryNext()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (xfadeActive) {
                xfadeActive = false
                xfadePlayerOrNull()?.stop()
                advance()
            }
        }
    }

    /**
     * Secondary player used only for genuine overlapping crossfades.
     * handleAudioFocus=false so the two players never fight over audio focus.
     */
    private var _xfadePlayer: ExoPlayer? = null
    private val xfadePlayer: ExoPlayer
        get() = _xfadePlayer ?: ExoPlayer.Builder(appContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false
            )
            .build()
            .also {
                it.addListener(xfadeListener)
                _xfadePlayer = it
            }

    /** The player that is currently audible (or should be, pre-handoff). */
    private fun activePlayer(): Player? = if (xfadeActive) xfadePlayer else controller

    /**
     * The secondary player only if a fade has actually created it; avoids
     * needlessly spinning up a second ExoPlayer on pause/cancel paths.
     */
    private fun xfadePlayerOrNull(): ExoPlayer? = _xfadePlayer

    // ------------------------------------------------------------------
    // queue model
    // ------------------------------------------------------------------

    private val queueSongs = mutableListOf<Song>()
    private var queueIndex = -1

    /** Play order as indices into [queueSongs]; first element is always current. */
    private var shuffleOrder = listOf<Int>()

    private var loadJob: Job? = null
    private var retriedCurrent = false
    private var pollingStarted = false

    private var activeUrls: List<String> = emptyList()
    private var activeUrlIndex: Int = 0

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) watchdogJob?.cancel()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                val d = controller?.duration ?: -1L
                if (d > 0) _durationMs.value = d
            } else if (playbackState == Player.STATE_ENDED) {
                onTrackEnded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlayerError(error)
        }
    }

    init {
        connect()
        scope.launch { settings.crossfadeEnabled.collect { crossfadeEnabled = it } }
        scope.launch { settings.crossfadeDurationSec.collect { crossfadeSec = it } }
    }

    private fun connect() {
        val token = SessionToken(appContext, ComponentName(appContext, PlayerService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                controller = try {
                    future.get()
                } catch (e: Exception) {
                    null
                }
                controller?.addListener(playerListener)
                // Assign both session flows when the controller connects (fixes the
                // never-assigned _audioSessionId and seeds the crossfade-aware one).
                val sessionId = PlayerService.audioSessionId
                _audioSessionId.value = sessionId
                _activeAudioSessionId.value = sessionId
                startPolling()
            },
            MoreExecutors.directExecutor()
        )
    }

    // ------------------------------------------------------------------
    // PlaybackRepository
    // ------------------------------------------------------------------

    override fun playSong(song: Song) = setQueueInternal(listOf(song), 0)

    override fun playQueue(songs: List<Song>, startIndex: Int) =
        setQueueInternal(songs, startIndex)

    override fun playNext(song: Song) {
        val insertAt = if (queueIndex >= 0) queueIndex + 1 else 0
        queueSongs.add(insertAt.coerceIn(0, queueSongs.size), song)
        if (queueIndex < 0) queueIndex = 0
        rebuildShuffleOrder()
        emitQueueState()
    }

    override fun togglePlayPause() {
        val p = activePlayer() ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    override fun pause() {
        if (crossfading && !xfadeActive) {
            // A fade is in flight: kill it and hand control back to the session player.
            crossfadeJob?.cancel()
            crossfading = false
            xfadePlayerOrNull()?.pause()
            controller?.volume = 1f
        }
        activePlayer()?.pause()
        _isPlaying.value = false
    }

    override fun next() {
        if (_repeatMode.value == RepeatMode.ONE) {
            seekTo(0)
            activePlayer()?.play()
            return
        }
        cancelCrossfade()
        advance()
    }

    override fun previous() {
        val p = activePlayer()
        if ((p?.currentPosition ?: 0L) > 3000L) {
            seekTo(0)
            return
        }
        if (xfadeActive) {
            // Going back while the secondary player is audible: drop the crossfade.
            crossfadeJob?.cancel()
            crossfading = false
            xfadeActive = false
            xfadePlayerOrNull()?.stop()
            _activeAudioSessionId.value = PlayerService.audioSessionId
            controller?.volume = 1f
        }
        if (queueSongs.isEmpty() || queueIndex < 0) return
        val order = playOrder()
        val pos = order.indexOf(queueIndex)
        if (pos > 0) {
            queueIndex = order[pos - 1]
            retriedCurrent = false
            loadCurrent()
        } else if (_repeatMode.value == RepeatMode.ALL && order.isNotEmpty()) {
            queueIndex = order.last()
            retriedCurrent = false
            loadCurrent()
        } else {
            seekTo(0)
        }
    }

    override fun seekTo(positionMs: Long) {
        activePlayer()?.seekTo(positionMs.coerceAtLeast(0L))
    }

    override fun setShuffle(enabled: Boolean) {
        _shuffleEnabled.value = enabled
        rebuildShuffleOrder()
        emitQueueState()
    }

    override fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        activePlayer()?.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    override fun removeFromQueue(index: Int) {
        if (index !in queueSongs.indices) return
        if (index == queueIndex) cancelCrossfade()
        val wasCurrent = index == queueIndex
        queueSongs.removeAt(index)
        when {
            queueSongs.isEmpty() -> {
                queueIndex = -1
                loadJob?.cancel()
                controller?.stop()
                controller?.clearMediaItems()
                _currentSong.value = null
                _isPlaying.value = false
                _isLoading.value = false
                _positionMs.value = 0L
                _durationMs.value = 0L
            }
            wasCurrent -> {
                queueIndex = index.coerceIn(queueSongs.indices)
                retriedCurrent = false
                loadCurrent()
                rebuildShuffleOrder()
                emitQueueState()
                return
            }
            index < queueIndex -> queueIndex--
        }
        rebuildShuffleOrder()
        emitQueueState()
    }

    override fun moveInQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in queueSongs.indices || toIndex !in queueSongs.indices || fromIndex == toIndex) return
        val currentId = queueSongs.getOrNull(queueIndex)?.id
        val song = queueSongs.removeAt(fromIndex)
        queueSongs.add(toIndex, song)
        queueIndex = currentId?.let { id -> queueSongs.indexOfFirst { it.id == id } } ?: -1
        rebuildShuffleOrder()
        emitQueueState()
    }

    override fun clearQueue() {
        queueSongs.clear()
        queueIndex = -1
        shuffleOrder = emptyList()
        loadJob?.cancel()
        watchdogJob?.cancel()
        cancelCrossfade()
        activeUrls = emptyList()
        controller?.stop()
        controller?.clearMediaItems()
        _currentSong.value = null
        _isPlaying.value = false
        _isLoading.value = false
        _positionMs.value = 0L
        _durationMs.value = 0L
        _playerError.value = null
        emitQueueState()
    }

    override fun removeUpcomingAt(displayIndex: Int) {
        val full = fullIndexOfUpcoming(displayIndex)
        if (full >= 0) removeFromQueue(full)
    }

    override fun moveUpcoming(fromDisplay: Int, toDisplay: Int) {
        val fromFull = fullIndexOfUpcoming(fromDisplay)
        val toFull = fullIndexOfUpcoming(toDisplay)
        if (fromFull >= 0 && toFull >= 0) moveInQueue(fromFull, toFull)
    }

    override fun playUpcomingAt(displayIndex: Int) {
        val full = fullIndexOfUpcoming(displayIndex)
        if (full >= 0 && full != queueIndex) {
            queueIndex = full
            retriedCurrent = false
            loadCurrent()
        }
    }

    /** Maps a display position in [QueueState.upcoming] to a queueSongs index. */
    private fun fullIndexOfUpcoming(displayIndex: Int): Int {
        if (displayIndex < 0) return -1
        val order = playOrder()
        val pos = order.indexOf(queueIndex)
        if (pos < 0) return -1
        val target = pos + 1 + displayIndex
        return if (target < order.size) order[target] else -1
    }

    override fun clearError() {
        _playerError.value = null
    }

    override fun retry() {
        clearError()
        retriedCurrent = false
        loadCurrent()
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private fun setQueueInternal(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        loadJob?.cancel()
        queueSongs.clear()
        queueSongs.addAll(songs)
        queueIndex = startIndex.coerceIn(songs.indices)
        retriedCurrent = false
        rebuildShuffleOrder()
        loadCurrent()
    }

    /** How long to wait for playback to actually start before trying the next URL. */
    private companion object {
        const val PLAYBACK_START_TIMEOUT_MS = 12_000L
    }

    private var watchdogJob: Job? = null

    /** Resolves the current song's playable URIs and starts it, failing over. */
    private fun loadCurrent() {
        loadJob?.cancel()
        watchdogJob?.cancel()
        cancelCrossfade()
        val song = queueSongs.getOrNull(queueIndex)
        _currentSong.value = song
        _positionMs.value = 0L
        _durationMs.value = 0L
        _playerError.value = null
        // Stats are counted lazily by the polling loop once audible playback
        // passes 3s (Fix 4), never on failed/skipped loads.
        statsRecordedSongId = null
        emitQueueState()
        if (song == null) {
            _isLoading.value = false
            return
        }
        _isLoading.value = true
        loadJob = scope.launch {
            val uris: List<String> = withContext(Dispatchers.IO) {
                music.resolvePlayableUris(song)
            }
            val c = controller
            when {
                uris.isEmpty() -> {
                    _isLoading.value = false
                    _playerError.value = if (music.isLocal(song)) {
                        "Couldn't open this audio file."
                    } else {
                        "Couldn't play this song. Check your connection and retry."
                    }
                }
                c == null -> {
                    _isLoading.value = false
                    _playerError.value = "Player isn't ready yet. Try again in a moment."
                }
                else -> tryPlayUrls(song, uris, 0)
            }
        }
    }

    /** Plays [urls][index]; the watchdog advances to the next URL on stall. */
    private fun tryPlayUrls(song: Song, urls: List<String>, index: Int) {
        val c = controller ?: run {
            _isLoading.value = false
            _playerError.value = "Player isn't ready yet. Try again in a moment."
            return
        }
        if (index >= urls.size) {
            _isLoading.value = false
            _playerError.value = "Couldn't play this song. Try another one."
            return
        }
        activeUrls = urls
        activeUrlIndex = index
        c.setMediaItem(buildMediaItem(song, urls[index]))
        c.prepare()
        c.play()
        _isLoading.value = false
        retriedCurrent = false
        scope.launch { runCatching { library.addRecent(song) } }

        // Watchdog: if playback hasn't started in time, fail over to the
        // next URL instead of sitting at 0:00 forever.
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(PLAYBACK_START_TIMEOUT_MS)
            val stillCurrent = _currentSong.value?.id == song.id
            if (stillCurrent && !_isPlaying.value && _playerError.value == null) {
                tryPlayUrls(song, urls, index + 1)
            }
        }
    }

    // ------------------------------------------------------------------
    // crossfade internals
    // ------------------------------------------------------------------

    /**
     * Aborts any in-progress crossfade and returns audible control to the
     * session player. Safe to call when no crossfade is running (no-op).
     * Only touches the secondary player if it was already created, so the
     * extra ExoPlayer is only ever built when a fade actually starts.
     */
    private fun cancelCrossfade() {
        crossfadeJob?.cancel()
        crossfading = false
        if (xfadeActive) {
            xfadeActive = false
            xfadePlayerOrNull()?.stop()
            _activeAudioSessionId.value = PlayerService.audioSessionId
        }
        controller?.volume = 1f
    }

    /**
     * Mirrors [advance]'s next-song selection without mutating any state.
     * Returns null when there is no next song (queue end without REPEAT_ALL).
     */
    private fun peekNextSong(): Song? {
        if (queueSongs.isEmpty() || queueIndex < 0) return null
        val order = playOrder()
        val pos = order.indexOf(queueIndex)
        return when {
            pos >= 0 && pos + 1 < order.size -> queueSongs.getOrNull(order[pos + 1])
            _repeatMode.value == RepeatMode.ALL && order.isNotEmpty() -> queueSongs.getOrNull(order[0])
            else -> null
        }
    }

    /**
     * Starts a genuine overlapping crossfade into [next]: the next song is
     * resolved and played on the secondary [xfadePlayer] while the session
     * player's volume ramps down, then the secondary player takes over as the
     * audible source until the next track change.
     */
    private fun beginCrossfade(next: Song) {
        if (crossfading || xfadeActive) return
        crossfading = true
        scope.launch {
            val uris = withContext(Dispatchers.IO) { music.resolvePlayableUris(next) }
            if (uris.isEmpty() || !crossfading) {
                crossfading = false
                return@launch
            }
            try {
                xfadePlayer.setMediaItem(buildMediaItem(next, uris[0]))
                xfadePlayer.prepare()
                xfadePlayer.play()
                xfadePlayer.volume = 0f
            } catch (e: Exception) {
                crossfading = false
                return@launch
            }
            val steps = 20
            val stepMs = crossfadeSec * 1000L / steps
            crossfadeJob = scope.launch {
                try {
                    for (i in 1..steps) {
                        delay(stepMs)
                        val t = i.toFloat() / steps
                        controller?.volume = (1 - t).coerceIn(0f, 1f)
                        if (xfadePlayer.isPlaying) xfadePlayer.volume = t
                    }
                    // Handoff: the session player goes quiet, the secondary
                    // player is now the audible one.
                    controller?.stop()
                    controller?.volume = 1f
                    xfadeActive = true
                    crossfading = false
                    queueIndex = queueSongs.indexOfFirst { it.id == next.id }
                        .takeIf { it >= 0 } ?: queueIndex
                    _currentSong.value = next
                    emitQueueState()
                    _activeAudioSessionId.value = xfadePlayer.audioSessionId
                    runCatching { library.addRecent(next) }
                    // Stats are counted by the polling loop once audible
                    // playback passes 3s (never on failed/skipped loads).
                    statsRecordedSongId = null
                    // Refresh notification metadata on the session player
                    // (paused, so no audible gap).
                    controller?.setMediaItem(buildMediaItem(next, uris[0]))
                    controller?.prepare()
                } catch (e: Exception) {
                    // Covers cancellation: reset so a later trigger can retry.
                    crossfading = false
                }
            }
        }
    }

    /** xfadePlayer finished while audible: stop it and advance on the session player. */
    private fun finishXfadeToPrimaryNext() {
        if (!xfadeActive) return
        xfadeActive = false
        xfadePlayer.stop()
        _activeAudioSessionId.value = PlayerService.audioSessionId
        advance()
    }

    private fun buildMediaItem(song: Song, uri: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .apply {
                song.artworkUrl?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(song.id)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun handlePlayerError(error: PlaybackException) {
        _isLoading.value = false
        watchdogJob?.cancel()
        // Fail over to the next validated URL before bothering the user.
        val song = queueSongs.getOrNull(queueIndex)
        if (song != null && activeUrlIndex + 1 < activeUrls.size) {
            scope.launch {
                delay(800)
                if (_currentSong.value?.id == song.id) {
                    tryPlayUrls(song, activeUrls, activeUrlIndex + 1)
                }
            }
            return
        }
        _playerError.value = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
                "You're offline. Check your connection and retry."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                "This song's audio format isn't supported."
            else -> "Couldn't play this song. Try another one."
        }
    }

    private fun onTrackEnded() {
        if (_repeatMode.value == RepeatMode.ONE) {
            controller?.seekTo(0)
            controller?.play()
            return
        }
        advance()
    }

    /** Moves to the next song in play order, wrapping/stopping per repeat mode. */
    private fun advance() {
        if (queueSongs.isEmpty() || queueIndex < 0) {
            // No queue - try autoplay with trending
            scope.launch {
                try {
                    val more = music.trending().getOrNull().orEmpty().take(10)
                    if (more.isNotEmpty()) {
                        queueSongs.clear()
                        queueSongs.addAll(more)
                        queueIndex = 0
                        rebuildShuffleOrder()
                        retriedCurrent = false
                        loadCurrent()
                    }
                } catch (e: Exception) { }
            }
            return
        }
        val order = playOrder()
        val pos = order.indexOf(queueIndex)
        if (pos + 1 < order.size) {
            queueIndex = order[pos + 1]
            retriedCurrent = false
            loadCurrent()
        } else if (_repeatMode.value == RepeatMode.ALL && order.isNotEmpty()) {
            queueIndex = order[0]
            retriedCurrent = false
            loadCurrent()
        } else {
            // Spotify-style autoplay: queue ended, fetch more songs and continue
            scope.launch {
                try {
                    val more = music.trending().getOrNull().orEmpty()
                        .filter { s -> queueSongs.none { it.id == s.id } }
                        .take(10)
                    if (more.isNotEmpty()) {
                        val startIndex = queueSongs.size
                        queueSongs.addAll(more)
                        queueIndex = startIndex
                        rebuildShuffleOrder()
                        retriedCurrent = false
                        loadCurrent()
                    } else {
                        controller?.pause()
                        controller?.seekTo(0)
                        _isPlaying.value = false
                    }
                } catch (e: Exception) {
                    controller?.pause()
                    controller?.seekTo(0)
                    _isPlaying.value = false
                }
            }
        }
    }

    private fun playOrder(): List<Int> =
        if (_shuffleEnabled.value && shuffleOrder.isNotEmpty()) shuffleOrder
        else queueSongs.indices.toList()

    private fun rebuildShuffleOrder() {
        shuffleOrder = if (_shuffleEnabled.value && queueSongs.isNotEmpty() && queueIndex >= 0) {
            listOf(queueIndex) + queueSongs.indices.filter { it != queueIndex }.shuffled()
        } else {
            emptyList()
        }
    }

    private fun emitQueueState() {
        val order = playOrder()
        val pos = order.indexOf(queueIndex)
        val upcoming = if (pos >= 0 && queueIndex >= 0) {
            order.subList(pos + 1, order.size).mapNotNull { queueSongs.getOrNull(it) }
        } else {
            emptyList()
        }
        _queueState.value = QueueState(
            current = queueSongs.getOrNull(queueIndex),
            upcoming = upcoming,
            currentIndex = queueIndex
        )
    }

    private fun startPolling() {
        if (pollingStarted) return
        pollingStarted = true
        scope.launch {
            while (true) {
                val p = activePlayer()
                if (p != null) {
                    _isPlaying.value = p.isPlaying
                    _positionMs.value = p.currentPosition.coerceAtLeast(0L)
                    val d = p.duration
                    if (d > 0) _durationMs.value = d
                    // Genuine crossfade trigger: within crossfadeSec of the
                    // song's end, start fading into the next song.
                    val pos = p.currentPosition
                    if (crossfadeEnabled && !xfadeActive && !crossfading &&
                        p.isPlaying && p === controller &&
                        _repeatMode.value != RepeatMode.ONE &&
                        d > 0 && pos > 0 && (d - pos) <= crossfadeSec * 1000L
                    ) {
                        peekNextSong()?.let { beginCrossfade(it) }
                    }
                }
                // Fix 4: count a play only after real audible playback starts
                // (>3s), once per song, never on failed/skipped loads.
                val song = _currentSong.value
                val songId = song?.id
                if (songId != null && songId != statsRecordedSongId) {
                    val ap = activePlayer()
                    if (ap?.isPlaying == true && ap.currentPosition > 3000L) {
                        try {
                            stats.recordPlay(song.title, song.artist, song.durationSec ?: 0L)
                        } catch (e: Exception) { }
                        statsRecordedSongId = songId
                    }
                }
                delay(500)
            }
        }
    }
}
