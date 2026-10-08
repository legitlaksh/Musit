package com.musit.player.ui.queue

import androidx.lifecycle.ViewModel
import com.musit.player.player.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    val playback: PlaybackRepository
) : ViewModel() {

    fun removeAt(displayIndex: Int) = playback.removeUpcomingAt(displayIndex)

    fun move(fromDisplay: Int, toDisplay: Int) = playback.moveUpcoming(fromDisplay, toDisplay)

    fun clear() = playback.clearQueue()

    fun playAt(displayIndex: Int) = playback.playUpcomingAt(displayIndex)
}
