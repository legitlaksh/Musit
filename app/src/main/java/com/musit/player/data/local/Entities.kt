package com.musit.player.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A song the user played recently. Song snapshot is stored so the row renders offline. */
@Entity(tableName = "recent_songs")
data class RecentSong(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val playedAt: Long
)

/** A song the user marked as a favorite. */
@Entity(tableName = "favorite_songs")
data class FavoriteSong(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val addedAt: Long
)

/** One entry of the search-history list. */
@Entity(tableName = "search_history")
data class SearchHistoryEntry(
    @PrimaryKey val query: String,
    val at: Long
)

/** A user-created playlist. */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

/**
 * Songs inside a playlist. The song snapshot (title/artist/artwork) is stored
 * with the cross-ref so playlists render without re-fetching.
 */
@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: String,
    val position: Int,
    val title: String,
    val artist: String,
    val artworkUrl: String?
)

/**
 * A song downloaded for offline listening. [fileName] is the audio file
 * inside the app-private "downloads" directory (see DownloadRepository).
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val fileName: String,
    val downloadedAt: Long,
    val sizeBytes: Long
)
