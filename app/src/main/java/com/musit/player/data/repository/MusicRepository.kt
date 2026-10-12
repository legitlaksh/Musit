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

    /** All validated playable audio URLs for [songId], best first. */
    suspend fun streamUrls(songId: String): Result<List<String>> = source.getStreamUrls(songId)

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

    /**
     * Offline-download lookup, wired up by the downloads feature at startup
     * (see DownloadRepository). Returns a file:// URI for [songId] when the
     * song has been downloaded, else null. Kept as a settable hook instead
     * of a direct dependency to avoid a DI cycle.
     */
    var downloadedFileUriProvider: (suspend (songId: String) -> String?)? = null

    /**
     * Best playable URIs for [song], in preference order:
     * 1. offline download (file://), 2. on-device local song, 3. network stream URLs.
     * Used by the playback repository for both normal and crossfade playback.
     */
    suspend fun resolvePlayableUris(song: Song): List<String> {
        downloadedFileUriProvider?.invoke(song.id)?.let { return listOf(it) }
        if (isLocal(song)) {
            return listOfNotNull(localUri(song.id))
        }
        return streamUrls(song.id).getOrNull().orEmpty()
    }
}
