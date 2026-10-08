package com.musit.player.network

import com.musit.player.model.Song

/**
 * Abstraction over whatever backend provides music. Today this is an
 * unofficial InnerTube client ([InnerTubeMusicSource]); tomorrow it can be
 * swapped without touching the UI or the player.
 *
 * All functions are safe to call from any thread (they switch to IO
 * internally) and never throw — failures come back as [Result.failure].
 */
interface MusicSource {

    /** Full-text search for songs. */
    suspend fun search(query: String): Result<List<Song>>

    /** Type-ahead suggestions for [query]. Never fails hard — empty on error. */
    suspend fun getSearchSuggestions(query: String): List<String>

    /**
     * Resolves a **validated** playable audio URL for [songId].
     * The URL is probed before it is returned, so callers can hand it
     * straight to ExoPlayer. Failure means "try again later", not
     * "this song is broken".
     */
    suspend fun getStreamUrl(songId: String): Result<String>

    /**
     * All validated playable audio URLs for [songId], best first.
     * The player tries them in order until one actually plays —
     * a URL that probes fine can still stall on some networks.
     */
    suspend fun getStreamUrls(songId: String): Result<List<String>>

    /** Songs for the Home screen's trending/quick-picks shelves. */
    suspend fun getTrending(): Result<List<Song>>
}
