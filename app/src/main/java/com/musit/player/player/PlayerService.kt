package com.musit.player.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.musit.player.ui.MainActivity
import com.musit.player.R
import com.musit.player.data.repository.LibraryRepository
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Media session service with custom notification actions:
 * Like and Add to Playlist buttons in the notification shade.
 */
@AndroidEntryPoint
class PlayerService : MediaSessionService() {

    @Inject
    lateinit var libraryRepository: LibraryRepository

    /**
     * Lazy so the playback repository (and its MediaController, which binds to
     * this service's own session) is only created after the session exists.
     */
    @Inject
    lateinit var playbackRepository: Lazy<PlaybackRepository>

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val COMMAND_TOGGLE_LIKE = "com.musit.player.TOGGLE_LIKE"
        const val COMMAND_ADD_TO_PLAYLIST = "com.musit.player.ADD_TO_PLAYLIST"
        const val COMMAND_NEXT_TRACK = "com.musit.player.NEXT_TRACK"
        const val ACTION_ADD_TO_PLAYLIST = "com.musit.player.action.ADD_TO_PLAYLIST"
        const val EXTRA_SONG_ID = "song_id"

        // Home screen widget protocol (see MusitWidgetProvider).
        const val ACTION_WIDGET_TOGGLE = "com.musit.player.widget.TOGGLE"
        const val ACTION_WIDGET_NEXT = "com.musit.player.widget.NEXT"
        const val ACTION_WIDGET_UPDATE = "com.musit.player.widget.UPDATE"
        const val EXTRA_WIDGET_TITLE = "widget_title"
        const val EXTRA_WIDGET_ARTIST = "widget_artist"
        const val EXTRA_WIDGET_ARTWORK = "widget_artwork"
        const val EXTRA_WIDGET_PLAYING = "widget_playing"
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(COMMAND_TOGGLE_LIKE, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_ADD_TO_PLAYLIST, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_NEXT_TRACK, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                COMMAND_TOGGLE_LIKE -> {
                    serviceScope.launch {
                        try {
                            val currentItem = player?.currentMediaItem
                            val songId = currentItem?.mediaId
                            if (songId != null) {
                                val metadata = currentItem.mediaMetadata
                                // Reconstruct Song from media metadata
                                val song = com.musit.player.model.Song(
                                    id = songId,
                                    title = metadata.title?.toString() ?: "Unknown",
                                    artist = metadata.artist?.toString() ?: "Unknown",
                                    artworkUrl = metadata.artworkUri?.toString()
                                )
                                val isFav = libraryRepository.isFavorite(songId).first()
                                libraryRepository.toggleFavorite(song, isFav)
                            }
                            updateCustomLayout()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                COMMAND_ADD_TO_PLAYLIST -> {
                    // Launch the app with Add to Playlist action
                    try {
                        val currentItem = player?.currentMediaItem
                        val songId = currentItem?.mediaId
                        val intent = Intent(this@PlayerService, MainActivity::class.java).apply {
                            action = ACTION_ADD_TO_PLAYLIST
                            putExtra(EXTRA_SONG_ID, songId)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                COMMAND_NEXT_TRACK -> {
                    // Widget "next": advance the app's internal queue via the
                    // playback repository (ExoPlayer only holds one item).
                    serviceScope.launch {
                        try {
                            playbackRepository.get().next()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    /**
     * Pushes the current playback state to the home screen widget, if any.
     * Explicit in-app broadcast; the widget provider renders it.
     */
    private fun broadcastWidgetUpdate() {
        try {
            val metadata = player?.currentMediaItem?.mediaMetadata
            val intent = Intent(ACTION_WIDGET_UPDATE).apply {
                setPackage(packageName)
                putExtra(EXTRA_WIDGET_TITLE, metadata?.title?.toString())
                putExtra(EXTRA_WIDGET_ARTIST, metadata?.artist?.toString())
                putExtra(EXTRA_WIDGET_ARTWORK, metadata?.artworkUri?.toString())
                putExtra(EXTRA_WIDGET_PLAYING, player?.isPlaying == true)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateCustomLayout() {        val session = this.session ?: return
        try {
            // Like button
            val likeButton = CommandButton.Builder()
                .setDisplayName("Like")
                .setIconResId(R.drawable.ic_like)
                .setSessionCommand(SessionCommand(COMMAND_TOGGLE_LIKE, Bundle.EMPTY))
                .setEnabled(player?.currentMediaItem != null)
                .build()

            // Add to Playlist button
            val addToPlaylistButton = CommandButton.Builder()
                .setDisplayName("Add to Playlist")
                .setIconResId(R.drawable.ic_playlist_add)
                .setSessionCommand(SessionCommand(COMMAND_ADD_TO_PLAYLIST, Bundle.EMPTY))
                .setEnabled(player?.currentMediaItem != null)
                .build()

            session.setCustomLayout(ImmutableList.of(likeButton, addToPlaylistButton))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val exoPlayer = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(audioAttributes, true)
            .build()
        player = exoPlayer
        // Update custom layout buttons when track changes
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int
            ) {
                updateCustomLayout()
                broadcastWidgetUpdate()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                broadcastWidgetUpdate()
            }
        })
        session = MediaSession.Builder(this, exoPlayer)
            .setCallback(sessionCallback)
            .build()
        updateCustomLayout()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        session

    override fun onDestroy() {
        session?.release()
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }
}
