package com.musit.player.data.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.musit.player.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "musit_settings")

/** Requested audio quality. Stored for now; a future MusicSource can consume it for itag choice. */
enum class AudioQuality { LOW, MEDIUM, HIGH }

/**
 * Persisted user settings (theme, audio quality) via DataStore Preferences.
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val QUALITY = stringPreferencesKey("audio_quality")
        val CROSSFADE_ENABLED = booleanPreferencesKey("crossfade_enabled")
        val CROSSFADE_DURATION_SEC = intPreferencesKey("crossfade_duration_sec")
    }

    val crossfadeEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.CROSSFADE_ENABLED] ?: true
    }

    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.CROSSFADE_ENABLED] = enabled }
    }

    /** Crossfade duration in seconds, clamped to 1..12. */
    val crossfadeDurationSec: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        (prefs[Keys.CROSSFADE_DURATION_SEC] ?: 5).coerceIn(1, 12)
    }

    suspend fun setCrossfadeDurationSec(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.CROSSFADE_DURATION_SEC] = seconds.coerceIn(1, 12) }
    }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[Keys.THEME] ?: ThemeMode.DARK.name) }
            .getOrDefault(ThemeMode.DARK)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME] = mode.name }
    }

    val audioQuality: Flow<AudioQuality> = context.settingsDataStore.data.map { prefs ->
        runCatching { AudioQuality.valueOf(prefs[Keys.QUALITY] ?: AudioQuality.HIGH.name) }
            .getOrDefault(AudioQuality.HIGH)
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        context.settingsDataStore.edit { it[Keys.QUALITY] = quality.name }
    }
}
