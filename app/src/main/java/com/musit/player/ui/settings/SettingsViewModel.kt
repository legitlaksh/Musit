package com.musit.player.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.musit.player.data.repository.LibraryRepository
import com.musit.player.data.store.AudioQuality
import com.musit.player.data.store.SettingsStore
import com.musit.player.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val library: LibraryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK
        )

    val audioQuality: StateFlow<AudioQuality> =
        settings.audioQuality.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), AudioQuality.HIGH
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch { settings.setAudioQuality(quality) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { library.clearSearchHistory() }
    }

    /** Clears Coil's image caches (memory + disk). */
    fun clearImageCache(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.imageLoader.memoryCache?.clear()
                    context.imageLoader.diskCache?.clear()
                }
            }
            onDone()
        }
    }

    fun appVersion(): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: "2.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "2.0"
        }
    }
}
