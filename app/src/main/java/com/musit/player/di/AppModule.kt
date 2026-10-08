package com.musit.player.di

import android.content.Context
import androidx.room.Room
import com.musit.player.data.local.AppDatabase
import com.musit.player.data.repository.LibraryRepository
import com.musit.player.data.repository.MusicRepository
import com.musit.player.data.store.SettingsStore
import com.musit.player.network.InnerTubeMusicSource
import com.musit.player.network.MusicSource
import com.musit.player.player.Media3PlaybackRepository
import com.musit.player.player.PlaybackRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ------------------------------------------------------------------
    // network
    // ------------------------------------------------------------------

    @Provides
    @Singleton
    fun provideMusicSource(impl: InnerTubeMusicSource): MusicSource = impl

    // ------------------------------------------------------------------
    // database
    // ------------------------------------------------------------------

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "musit.db")
            .fallbackToDestructiveMigration()
            .build()

    // ------------------------------------------------------------------
    // repositories
    // ------------------------------------------------------------------

    @Provides
    @Singleton
    fun provideLibraryRepository(db: AppDatabase): LibraryRepository =
        LibraryRepository(db)

    @Provides
    @Singleton
    fun provideMusicRepository(
        source: MusicSource,
        library: LibraryRepository
    ): MusicRepository = MusicRepository(source, library)

    @Provides
    @Singleton
    fun providePlaybackRepository(
        impl: Media3PlaybackRepository
    ): PlaybackRepository = impl

    // ------------------------------------------------------------------
    // settings
    // ------------------------------------------------------------------

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore =
        SettingsStore(context)
}
