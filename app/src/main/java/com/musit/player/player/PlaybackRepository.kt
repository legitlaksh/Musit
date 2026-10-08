package com.musit.player.player

import com.musit.player.model.QueueState
import com.musit.player.model.Song
import kotlinx.coroutines.flow.StateFlow

/** Repeat behaviour for the queue. */
enum class RepeatMode { OFF, ALL, ONE }

/**
 * App-wide playback controller. Implemented by [Media3PlaybackRepository],
 * which drives the [PlayerService]'s ExoPlayer through a MediaController.
 */
interface PlaybackRepository {

    val currentSong: StateFlow<Song?>
    val isPlaying: StateFlow<Boolean>
    val isLoading: StateFlow<Boolean>
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val playerError: StateFlow<String?>
    val queueState: StateFlow<QueueState>
    val shuffleEnabled: StateFlow<Boolean>
    val repeatMode: StateFlow<RepeatMode>

    /** Plays a single song (replaces the queue). */
    fun playSong(song: Song)

    /** Replaces the queue and starts at [startIndex]. */
    fun playQueue(songs: List<Song>, startIndex: Int)

    /** Inserts [song] right after the current one. */
    fun playNext(song: Song)

    fun togglePlayPause()
    fun pause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun setShuffle(enabled: Boolean)
    fun cycleRepeatMode()

    fun removeFromQueue(index: Int)
    fun moveInQueue(fromIndex: Int, toIndex: Int)
    fun clearQueue()

    /**
     * Queue editing in *display* order ([QueueState.current] then
     * [QueueState.upcoming]), which already accounts for shuffle.
     * [displayIndex] 0 is the first upcoming song.
     */
    fun removeUpcomingAt(displayIndex: Int)
    fun moveUpcoming(fromDisplay: Int, toDisplay: Int)
    fun playUpcomingAt(displayIndex: Int)

    fun clearError()

    /** Retries the current song (re-resolves its stream URL). */
    fun retry()
}
