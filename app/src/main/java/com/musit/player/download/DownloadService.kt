package com.musit.player.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.musit.player.data.repository.DownloadRepository
import com.musit.player.data.repository.MusicRepository
import com.musit.player.model.Song
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

/**
 * Foreground service that downloads a song's audio stream to the app-private
 * downloads directory and records it in [DownloadRepository].
 *
 * Start with [enqueue]; cancel everything with [cancelAll].
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var music: MusicRepository
    @Inject lateinit var downloads: DownloadRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                val song = songFrom(intent) ?: return START_NOT_STICKY
                handleDownload(song, startId)
            }
            ACTION_CANCEL -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun handleDownload(song: Song, startId: Int) {
        createChannel()
        // Foreground immediately: Android requires this within seconds of start.
        startForegroundCompat(PROGRESS_NOTIFICATION_ID, progressNotification(song, -1))
        scope.launch {
            try {
                when {
                    music.isLocal(song) -> {
                        notifyFinished(song, "Already on your device", "This song is stored on your phone.")
                    }
                    downloads.isDownloaded(song.id) -> {
                        notifyFinished(song, "Already downloaded", "\"${song.title}\" is already downloaded.")
                    }
                    else -> {
                        val url = music.streamUrls(song.id).getOrNull()?.firstOrNull()
                            ?: throw IOException("Could not resolve a stream URL")
                        val file = downloads.fileFor(song.id)
                        file.parentFile?.mkdirs()
                        downloadToFile(song, url, file)
                        downloads.saveDownload(song, file.name, file.length())
                        notifyFinished(song, "Download complete", "\"${song.title}\" saved for offline listening.")
                    }
                }
            } catch (e: Exception) {
                notifyFinished(song, "Download failed", e.message ?: "Unknown error")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
    }

    private fun downloadToFile(song: Song, url: String, file: File) {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response body")
            val total = body.contentLength()
            var done = 0L
            var lastReported = -1
            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
                            if (pct >= lastReported + 5) {
                                lastReported = pct
                                updateProgress(song, pct)
                            }
                        }
                    }
                }
            }
            if (file.length() == 0L) {
                file.delete()
                throw IOException("Downloaded file is empty")
            }
        }
    }

    // ------------------------------------------------------------------
    // notifications
    // ------------------------------------------------------------------

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager().createNotificationChannel(channel)
        }
    }

    private fun baseBuilder(title: String, text: String): Notification.Builder {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder.setContentTitle(title).setContentText(text)
    }

    private fun progressNotification(song: Song, percent: Int): Notification {
        val builder = baseBuilder("Downloading", song.title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
        if (percent in 0..100) {
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun updateProgress(song: Song, percent: Int) {
        notificationManager().notify(PROGRESS_NOTIFICATION_ID, progressNotification(song, percent))
    }

    private fun notifyFinished(song: Song, title: String, text: String) {
        val notification = baseBuilder(title, text)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
        val id = FINISHED_NOTIFICATION_ID_BASE + (song.id.hashCode() and 0x7fffffff) % 1000
        notificationManager().notify(id, notification)
    }

    private fun startForegroundCompat(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(id, notification)
        }
    }

    // ------------------------------------------------------------------
    // intent plumbing (Song is not Parcelable, so fields go as extras)
    // ------------------------------------------------------------------

    private fun songFrom(intent: Intent): Song? {
        val id = intent.getStringExtra(EXTRA_ID) ?: return null
        return Song(
            id = id,
            title = intent.getStringExtra(EXTRA_TITLE) ?: "Unknown title",
            artist = intent.getStringExtra(EXTRA_ARTIST) ?: "Unknown artist",
            artworkUrl = intent.getStringExtra(EXTRA_ARTWORK)
        )
    }

    companion object {
        const val ACTION_DOWNLOAD = "com.musit.player.download.ACTION_DOWNLOAD"
        const val ACTION_CANCEL = "com.musit.player.download.ACTION_CANCEL"

        private const val CHANNEL_ID = "musit_downloads"
        private const val PROGRESS_NOTIFICATION_ID = 2001
        private const val FINISHED_NOTIFICATION_ID_BASE = 2100

        private const val EXTRA_ID = "song_id"
        private const val EXTRA_TITLE = "song_title"
        private const val EXTRA_ARTIST = "song_artist"
        private const val EXTRA_ARTWORK = "song_artwork"

        fun enqueue(context: Context, song: Song) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_ID, song.id)
                putExtra(EXTRA_TITLE, song.title)
                putExtra(EXTRA_ARTIST, song.artist)
                putExtra(EXTRA_ARTWORK, song.artworkUrl)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancelAll(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }
}
