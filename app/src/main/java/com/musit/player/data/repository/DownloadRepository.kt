package com.musit.player.data.repository

import android.content.Context
import android.net.Uri
import com.musit.player.data.local.AppDatabase
import com.musit.player.data.local.DownloadEntity
import com.musit.player.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline downloads: tracks downloaded audio files in Room and wires the
 * file:// URI lookup into [MusicRepository] so downloaded songs play back
 * offline (see [MusicRepository.resolvePlayableUris]).
 *
 * Audio files live in the app-private "downloads" directory, so no storage
 * permission is needed.
 */
@Singleton
class DownloadRepository @Inject constructor(
    private val db: AppDatabase,
    private val music: MusicRepository,
    @ApplicationContext private val context: Context
) {
    private val dao get() = db.downloadDao()

    /** Directory holding downloaded audio files. */
    val downloadsDir: File get() = File(context.filesDir, "downloads")

    init {
        music.downloadedFileUriProvider = { songId -> fileUriFor(songId) }
    }

    fun observeDownloads(): Flow<List<DownloadEntity>> = dao.observeAll()

    /** File for [songId]'s download (may not exist yet). */
    fun fileFor(songId: String): File = File(downloadsDir, "${sanitizeId(songId)}.m4a")

    suspend fun isDownloaded(songId: String): Boolean {
        val row = dao.get(songId) ?: return false
        if (!File(downloadsDir, row.fileName).exists()) {
            // Stale row — the file was deleted outside the app.
            dao.delete(songId)
            return false
        }
        return true
    }

    /**
     * file:// URI for a completed download, or null. Suspend because of the
     * Room lookup — matches [MusicRepository.downloadedFileUriProvider].
     */
    suspend fun fileUriFor(songId: String): String? {
        val row = dao.get(songId) ?: return null
        val file = File(downloadsDir, row.fileName)
        return if (file.exists()) Uri.fromFile(file).toString() else null
    }

    suspend fun saveDownload(song: Song, fileName: String, sizeBytes: Long) {
        dao.upsert(
            DownloadEntity(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                artworkUrl = song.artworkUrl,
                fileName = fileName,
                downloadedAt = System.currentTimeMillis(),
                sizeBytes = sizeBytes
            )
        )
    }

    /** Deletes the audio file and its database row. */
    suspend fun removeDownload(songId: String) {
        dao.get(songId)?.let { File(downloadsDir, it.fileName).delete() }
        dao.delete(songId)
    }

    companion object {
        /** Keep the file name filesystem-safe. */
        fun sanitizeId(songId: String): String {
            val clean = songId
                .map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
                .joinToString("")
            return clean.ifBlank { "song" }
        }
    }
}
