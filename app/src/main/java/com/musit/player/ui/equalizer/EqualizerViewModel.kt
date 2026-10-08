package com.musit.player.ui.equalizer

import androidx.lifecycle.ViewModel
import com.musit.player.player.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Provides the audio session ID for the Equalizer.
 * Uses global session (0) which applies to all audio output.
 */
@HiltViewModel
class EqualizerViewModel @Inject constructor() : ViewModel() {
    // Global audio session - applies equalizer to all output
    val audioSessionId: StateFlow<Int> = kotlinx.coroutines.flow.MutableStateFlow(0)
}
