package com.musit.player.data.local

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.musit.player.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lists audio files on the device via MediaStore. Returned songs use ids of
 * the form "local:<mediaId>"; their content URIs must be registered with
 * [com.musit.player.data.repository.MusicRepository.registerLocalUri] before
 * playback so the player can build a MediaItem straight from the URI
 * (no stream resolution).
 */
@Singleton
class LocalMusicScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun scan(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        try {
            context.contentResolver.query(
                collection, projection, selection, null, sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val songId = "local:$mediaId"
                    val uri = ContentUris.withAppendedId(collection, mediaId).toString()
                    val durationMs = cursor.getLong(durCol)
                    val totalSec = (durationMs / 1000).coerceAtLeast(0)
                    songs.add(
                        Song(
                            id = songId,
                            title = cursor.getString(titleCol) ?: "Unknown title",
                            artist = cursor.getString(artistCol) ?: "Unknown artist",
                            album = cursor.getString(albumCol),
                            artworkUrl = null,
                            durationText = "%d:%02d".format(totalSec / 60, totalSec % 60),
                            durationSec = totalSec
                        )
                    )
                    lastScanUris[songId] = uri
                }
            }
        } catch (e: Exception) {
            // Missing permission or provider failure -> empty list, not a crash.
        }
        songs
    }

    /**
     * Content URIs collected during the last [scan], keyed by song id.
     * The caller registers them with MusicRepository before playback.
     */
    val lastScanUris: MutableMap<String, String> = mutableMapOf()
}
