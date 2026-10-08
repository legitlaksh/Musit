package com.musit.player.data.repository

import com.musit.player.data.local.AppDatabase
import com.musit.player.data.local.FavoriteSong
import com.musit.player.data.local.PlaylistEntity
import com.musit.player.data.local.RecentSong
import com.musit.player.data.local.SearchHistoryEntry
import com.musit.player.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Lightweight playlist header for lists (song counts come from a separate Flow). */
data class PlaylistInfo(val id: Long, val name: String)

/**
 * Local library: favorites, recents, search history and user playlists,
 * all backed by Room and exposed as Flows.
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val db: AppDatabase
) {

    // ------------------------------------------------------------------
    // mapping helpers
    // ------------------------------------------------------------------

    private fun RecentSong.toSong() = Song(
        id = songId, title = title, artist = artist, artworkUrl = artworkUrl
    )

    private fun FavoriteSong.toSong() = Song(
        id = songId, title = title, artist = artist, artworkUrl = artworkUrl
    )

    // ------------------------------------------------------------------
    // recents
    // ------------------------------------------------------------------

    fun observeRecents(limit: Int = 50): Flow<List<Song>> =
        db.recentDao().observe(limit).map { rows -> rows.map { it.toSong() } }

    suspend fun addRecent(song: Song) {
        // Local songs are not tracked in recents (they live under "On this device").
        if (song.id.startsWith("local:")) return
        db.recentDao().upsert(
            RecentSong(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                artworkUrl = song.artworkUrl,
                playedAt = System.currentTimeMillis()
            )
        )
        db.recentDao().trim(50)
    }

    suspend fun clearRecents() = db.recentDao().clear()

    // ------------------------------------------------------------------
    // favorites
    // ------------------------------------------------------------------

    fun observeFavorites(): Flow<List<Song>> =
        db.favoriteDao().observeAll().map { rows -> rows.map { it.toSong() } }

    fun isFavorite(songId: String): Flow<Boolean> =
        db.favoriteDao().isFavorite(songId)

    /**
     * Flips the favorite state of [song]. The caller passes the currently
     * observed value of [isFavorite] — the UI always has it.
     */
    suspend fun toggleFavorite(song: Song, currentlyFavorite: Boolean) {
        setFavorite(song, !currentlyFavorite)
    }

    suspend fun setFavorite(song: Song, favorite: Boolean) {
        if (favorite) {
            db.favoriteDao().add(
                FavoriteSong(
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    artworkUrl = song.artworkUrl,
                    addedAt = System.currentTimeMillis()
                )
            )
        } else {
            db.favoriteDao().remove(song.id)
        }
    }

    // ------------------------------------------------------------------
    // search history
    // ------------------------------------------------------------------

    fun observeSearchHistory(): Flow<List<String>> =
        db.searchHistoryDao().observe().map { rows -> rows.map { it.query } }

    suspend fun recordQuery(query: String) {
        val q = query.trim()
        if (q.isNotEmpty()) {
            db.searchHistoryDao().upsert(SearchHistoryEntry(q, System.currentTimeMillis()))
        }
    }

    suspend fun clearSearchHistory() = db.searchHistoryDao().clear()

    // ------------------------------------------------------------------
    // playlists
    // ------------------------------------------------------------------

    fun observePlaylists(): Flow<List<PlaylistInfo>> =
        db.playlistDao().observePlaylists().map { rows ->
            rows.map { PlaylistInfo(it.id, it.name) }
        }

    fun observePlaylistName(playlistId: Long): Flow<String?> =
        db.playlistDao().observePlaylist(playlistId).map { it?.name }

    fun observePlaylistSongs(playlistId: Long): Flow<List<Song>> =
        db.playlistDao().observeSongs(playlistId).map { rows ->
            rows.map {
                Song(id = it.songId, title = it.title, artist = it.artist, artworkUrl = it.artworkUrl)
            }
        }

    fun observePlaylistSongCount(playlistId: Long): Flow<Int> =
        db.playlistDao().observeSongCount(playlistId)

    suspend fun createPlaylist(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Playlist name is empty" }
        return db.playlistDao().createPlaylist(
            PlaylistEntity(name = trimmed, createdAt = System.currentTimeMillis())
        )
    }

    suspend fun deletePlaylist(playlistId: Long) {
        db.playlistDao().deletePlaylistSongs(playlistId)
        db.playlistDao().deletePlaylist(playlistId)
    }

    suspend fun addToPlaylist(playlistId: Long, song: Song) {
        val position = db.playlistDao().nextPosition(playlistId)
        db.playlistDao().addSong(
            com.musit.player.data.local.PlaylistSongCrossRef(
                playlistId = playlistId,
                songId = song.id,
                position = position,
                title = song.title,
                artist = song.artist,
                artworkUrl = song.artworkUrl
            )
        )
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: String) =
        db.playlistDao().removeSong(playlistId, songId)
}
