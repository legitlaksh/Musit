package com.musit.player

import android.app.Application
import com.musit.player.data.repository.DownloadRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class MusitApp : Application() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface EagerInitEntryPoint {
        fun downloadRepository(): DownloadRepository
    }

    override fun onCreate() {
        super.onCreate()
        // Eagerly create the downloads repository so it registers the
        // offline file-URI provider with MusicRepository before any playback.
        EntryPointAccessors.fromApplication(this, EagerInitEntryPoint::class.java)
            .downloadRepository()
    }
}
