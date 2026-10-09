package com.musit.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.RemoteViews
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.musit.player.R
import com.musit.player.player.PlayerService
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Home screen widget for musit: artwork, title/artist, play-pause and next.
 *
 * Control flow:
 * - Button taps send explicit broadcasts back to this provider.
 * - The provider drives playback through a [MediaController] bound to
 *   [PlayerService]'s MediaSession (play/pause directly; next via the
 *   custom [PlayerService.COMMAND_NEXT_TRACK] session command so the app's
 *   internal queue advances).
 * - [PlayerService] pushes state changes via [PlayerService.ACTION_WIDGET_UPDATE]
 *   broadcasts, which this provider renders.
 */
class MusitWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Set up static click targets immediately, then refresh live state.
        appWidgetIds.forEach { id ->
            val views = buildViews(context, null, null, null, false)
            appWidgetManager.updateAppWidget(id, views)
        }
        refreshFromSession(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PlayerService.ACTION_WIDGET_TOGGLE -> {
                val pending = goAsync()
                Thread {
                    try {
                        withController(context) { controller ->
                            if (controller.isPlaying) controller.pause() else controller.play()
                        }
                    } finally {
                        pending.finish()
                    }
                }.start()
                return
            }
            PlayerService.ACTION_WIDGET_NEXT -> {
                val pending = goAsync()
                Thread {
                    try {
                        withController(context) { controller ->
                            try {
                                controller.sendCustomCommand(
                                    SessionCommand(PlayerService.COMMAND_NEXT_TRACK, Bundle.EMPTY),
                                    Bundle.EMPTY
                                ).get(6, TimeUnit.SECONDS)
                            } catch (e: Exception) {
                                // Fall back to the session's default next if the
                                // custom command fails for any reason.
                                controller.seekToNext()
                            }
                        }
                    } finally {
                        pending.finish()
                    }
                }.start()
                return
            }
            PlayerService.ACTION_WIDGET_UPDATE -> {
                updateWidgets(
                    context = context,
                    title = intent.getStringExtra(PlayerService.EXTRA_WIDGET_TITLE),
                    artist = intent.getStringExtra(PlayerService.EXTRA_WIDGET_ARTIST),
                    artworkUrl = intent.getStringExtra(PlayerService.EXTRA_WIDGET_ARTWORK),
                    isPlaying = intent.getBooleanExtra(PlayerService.EXTRA_WIDGET_PLAYING, false)
                )
                return
            }
        }
        super.onReceive(context, intent)
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private fun buildViews(
        context: Context,
        title: String?,
        artist: String?,
        artwork: Bitmap?,
        isPlaying: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_musit)
        views.setTextViewText(
            R.id.widget_title,
            title ?: context.getString(R.string.app_name)
        )
        views.setTextViewText(
            R.id.widget_artist,
            artist ?: "Tap to open musit"
        )
        views.setImageViewResource(
            R.id.widget_play_pause,
            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )
        if (artwork != null) {
            views.setImageViewBitmap(R.id.widget_artwork, artwork)
        } else {
            views.setImageViewResource(R.id.widget_artwork, R.drawable.ic_widget_note)
        }

        // Tap artwork / text -> open the app.
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        if (launchIntent != null) {
            val openApp = PendingIntent.getActivity(
                context, REQ_OPEN, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_artwork, openApp)
            views.setOnClickPendingIntent(R.id.widget_title, openApp)
            views.setOnClickPendingIntent(R.id.widget_artist, openApp)
        }

        // Play / pause.
        val toggle = Intent(context, MusitWidgetProvider::class.java)
            .setAction(PlayerService.ACTION_WIDGET_TOGGLE)
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            PendingIntent.getBroadcast(
                context, REQ_TOGGLE, toggle,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        // Next.
        val next = Intent(context, MusitWidgetProvider::class.java)
            .setAction(PlayerService.ACTION_WIDGET_NEXT)
        views.setOnClickPendingIntent(
            R.id.widget_next,
            PendingIntent.getBroadcast(
                context, REQ_NEXT, next,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        return views
    }

    private fun updateWidgets(
        context: Context,
        title: String?,
        artist: String?,
        artworkUrl: String?,
        isPlaying: Boolean
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, MusitWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return

        // Text + icon immediately (placeholder artwork).
        ids.forEach { id ->
            manager.updateAppWidget(id, buildViews(context, title, artist, null, isPlaying))
        }

        // Artwork asynchronously; merge it in when ready.
        if (!artworkUrl.isNullOrBlank()) {
            Thread {
                val bitmap = downloadBitmap(artworkUrl)
                if (bitmap != null) {
                    ids.forEach { id ->
                        val partial = RemoteViews(context.packageName, R.layout.widget_musit)
                        partial.setImageViewBitmap(R.id.widget_artwork, bitmap)
                        manager.partiallyUpdateAppWidget(id, partial)
                    }
                }
            }.start()
        }
    }

    /** Best-effort read of the current session state (used when the widget is added). */
    private fun refreshFromSession(context: Context) {
        Thread {
            try {
                withController(context, fallbackToApp = false) { controller ->
                    val metadata = controller.mediaMetadata
                    updateWidgets(
                        context = context,
                        title = metadata.title?.toString(),
                        artist = metadata.artist?.toString(),
                        artworkUrl = metadata.artworkUri?.toString(),
                        isPlaying = controller.isPlaying
                    )
                }
            } catch (e: Exception) {
                // No active session; the placeholder state stays.
            }
        }.start()
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Runs [block] with a [MediaController] bound to [PlayerService].
     * When there is no active session and [fallbackToApp] is true, launches
     * the app instead.
     */
    private fun withController(
        context: Context,
        fallbackToApp: Boolean = true,
        block: (MediaController) -> Unit
    ) {
        val controller = try {
            val token = SessionToken(
                context,
                ComponentName(context, PlayerService::class.java)
            )
            MediaController.Builder(context, token)
                .buildAsync()
                .get(6, TimeUnit.SECONDS)
        } catch (e: Exception) {
            // No active session.
            if (fallbackToApp) {
                try {
                    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(it)
                    }
                } catch (ignored: Exception) {
                }
            }
            return
        }
        try {
            block(controller)
        } catch (e: Exception) {
            // Action failed; the next state broadcast will correct the widget.
        } finally {
            controller.release()
        }
    }

    private fun downloadBitmap(url: String): Bitmap? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.connect()
            try {
                BitmapFactory.decodeStream(connection.inputStream)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val REQ_OPEN = 1001
        private const val REQ_TOGGLE = 1002
        private const val REQ_NEXT = 1003
    }
}
