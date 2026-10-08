package com.musit.player.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sleep timer: stops playback after a set duration.
 */
@Singleton
class SleepTimer @Inject constructor(
    private val playback: PlaybackRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null

    private val _remainingMs = MutableStateFlow<Long?>(null)
    val remainingMs: StateFlow<Long?> = _remainingMs.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    fun start(minutes: Int) {
        cancel()
        _isActive.value = true
        val totalMs = minutes * 60 * 1000L
        _remainingMs.value = totalMs
        timerJob = scope.launch {
            var remaining = totalMs
            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                _remainingMs.value = remaining
            }
            // Time's up: pause playback
            playback.pause()
            _isActive.value = false
            _remainingMs.value = null
        }
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        _isActive.value = false
        _remainingMs.value = null
    }
}
