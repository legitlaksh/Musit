package com.musit.player.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listening statistics: total time, play counts, top artists.
 * Stored in SharedPreferences (simple, no DB migration needed).
 */
@Singleton
class StatsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("musit_stats", Context.MODE_PRIVATE)

    private val _totalMinutes = MutableStateFlow(getTotalMinutes())
    val totalMinutes: StateFlow<Long> = _totalMinutes.asStateFlow()

    private val _totalPlays = MutableStateFlow(getTotalPlays())
    val totalPlays: StateFlow<Long> = _totalPlays.asStateFlow()

    fun getTotalMinutes(): Long = prefs.getLong("total_minutes", 0L)
    fun getTotalPlays(): Long = prefs.getLong("total_plays", 0L)

    /**
     * Record a song play. Call when a song starts playing.
     */
    fun recordPlay(songTitle: String, artist: String, durationSec: Long) {
        // Total plays
        val plays = getTotalPlays() + 1
        prefs.edit().putLong("total_plays", plays).apply()
        _totalPlays.value = plays

        // Total minutes (approximate from duration)
        val minutes = getTotalMinutes() + (durationSec / 60)
        prefs.edit().putLong("total_minutes", minutes).apply()
        _totalMinutes.value = minutes

        // Per-song play count
        val songKey = "song:$artist:$songTitle"
        val songPlays = prefs.getInt(songKey, 0) + 1
        prefs.edit().putInt(songKey, songPlays).apply()

        // Per-artist play count
        val artistKey = "artist:$artist"
        val artistPlays = prefs.getInt(artistKey, 0) + 1
        prefs.edit().putInt(artistKey, artistPlays).apply()
    }

    /**
     * Get top songs by play count.
     */
    fun getTopSongs(limit: Int = 10): List<Pair<String, Int>> {
        val all = prefs.all
        return all.entries
            .filter { it.key.startsWith("song:") && it.value is Int }
            .map { it.key.removePrefix("song:") to (it.value as Int) }
            .sortedByDescending { it.second }
            .take(limit)
    }

    /**
     * Get top artists by play count.
     */
    fun getTopArtists(limit: Int = 10): List<Pair<String, Int>> {
        val all = prefs.all
        return all.entries
            .filter { it.key.startsWith("artist:") && it.value is Int }
            .map { it.key.removePrefix("artist:") to (it.value as Int) }
            .sortedByDescending { it.second }
            .take(limit)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _totalMinutes.value = 0L
        _totalPlays.value = 0L
    }
}
