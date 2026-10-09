package com.musit.player.ui.equalizer

import androidx.lifecycle.ViewModel
import com.musit.player.player.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Provides the audio session ID for the Equalizer.
 * Uses the real ExoPlayer audio session from PlayerService.
 */
@HiltViewModel
class EqualizerViewModel @Inject constructor() : ViewModel() {
    // Real audio session from PlayerService's ExoPlayer
    val audioSessionId: StateFlow<Int> = kotlinx.coroutines.flow.MutableStateFlow(
        com.musit.player.player.PlayerService.audioSessionId
    )
}
