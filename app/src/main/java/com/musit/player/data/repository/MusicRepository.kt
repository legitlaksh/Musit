package com.musit.player.data.repository

import com.musit.player.model.Song
import com.musit.player.network.MusicSource
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network music: search, suggestions, trending and stream URLs, all via the
 * [MusicSource] abstraction. Also owns the in-memory registry of local
 * (on-device) song URIs, since [Song] itself carries no URI.
 */
@Singleton
class MusicRepository @Inject constructor(
    private val source: MusicSource,
    private val library: LibraryRepository
) {

    private val localUris = ConcurrentHashMap<String, String>()

    /** Full-text song search. Successful searches are recorded in history. */
    suspend fun search(query: String): Result<List<Song>> {
        val result = source.search(query)
        if (result.isSuccess) {
            runCatching { library.recordQuery(query) }
        }
        return result
    }

    /** Type-ahead suggestions. Never throws — empty list on error. */
    suspend fun suggestions(query: String): List<String> =
        try {
            source.getSearchSuggestions(query)
        } catch (e: Exception) {
            emptyList()
        }

    /** Songs for the Home screen's trending shelf. */
    suspend fun trending(): Result<List<Song>> = source.getTrending()

    /** Validated playable audio URL for [songId]. */
    suspend fun streamUrl(songId: String): Result<String> = source.getStreamUrl(songId)

    // ------------------------------------------------------------------
    // local (on-device) songs
    // ------------------------------------------------------------------

    /** Registers the content URI for a local song id ("local:<mediaId>"). */
    fun registerLocalUri(songId: String, uri: String) {
        localUris[songId] = uri
    }

    /** Returns the registered content URI, or null if unknown. */
    fun localUri(songId: String): String? = localUris[songId]

    fun isLocal(song: Song): Boolean = song.id.startsWith("local:")
}
