package com.musit.player.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(song: RecentSong)

    @Query("SELECT * FROM recent_songs ORDER BY playedAt DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<RecentSong>>

    /** Keeps only the newest [limit] rows. */
    @Query("DELETE FROM recent_songs WHERE songId NOT IN (SELECT songId FROM recent_songs ORDER BY playedAt DESC LIMIT :limit)")
    suspend fun trim(limit: Int)

    @Query("DELETE FROM recent_songs")
    suspend fun clear()
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(song: FavoriteSong)

    @Query("DELETE FROM favorite_songs WHERE songId = :songId")
    suspend fun remove(songId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE songId = :songId)")
    fun isFavorite(songId: String): Flow<Boolean>

    @Query("SELECT * FROM favorite_songs ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteSong>>

    @Query("DELETE FROM favorite_songs")
    suspend fun clear()
}

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SearchHistoryEntry)

    @Query("SELECT * FROM search_history ORDER BY `at` DESC LIMIT 20")
    fun observe(): Flow<List<SearchHistoryEntry>>

    @Query("DELETE FROM search_history")
    suspend fun clear()
}

@Dao
interface PlaylistDao {
    @Insert
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    suspend fun deletePlaylistSongs(id: Long)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSong(ref: PlaylistSongCrossRef)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeSongs(playlistId: Long): Flow<List<PlaylistSongCrossRef>>

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    fun observeSongCount(playlistId: Long): Flow<Int>

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: String)
}

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DownloadEntity)

    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE songId = :songId")
    suspend fun get(songId: String): DownloadEntity?

    @Query("DELETE FROM downloads WHERE songId = :songId")
    suspend fun delete(songId: String)
}
