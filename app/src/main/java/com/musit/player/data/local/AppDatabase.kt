package com.musit.player.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RecentSong::class,
        FavoriteSong::class,
        SearchHistoryEntry::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        DownloadEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentDao(): RecentDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `downloads` (`songId` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `artworkUrl` TEXT, `fileName` TEXT NOT NULL, `downloadedAt` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, PRIMARY KEY(`songId`))")
            }
        }
    }
}
