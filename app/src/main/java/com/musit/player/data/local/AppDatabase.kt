package com.musit.player.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        RecentSong::class,
        FavoriteSong::class,
        SearchHistoryEntry::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentDao(): RecentDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun playlistDao(): PlaylistDao
}
