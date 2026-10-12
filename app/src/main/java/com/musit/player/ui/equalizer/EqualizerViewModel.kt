package com.musit.player.ui.equalizer

import androidx.lifecycle.ViewModel
import com.musit.player.player.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Provides the audio session ID for the Equalizer.
 * Follows the live audio session from the playback repository (which tracks
 * the MediaController's audio session, including session changes) instead of
 * snapshotting a static value at ViewModel creation.
 */
@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val playback: PlaybackRepository
) : ViewModel() {
    val audioSessionId: StateFlow<Int> = playback.activeAudioSessionId
}
