package com.musit.player.data.repository

import com.musit.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last.fm API for music recommendations.
 * Free API key from https://www.last.fm/api/account/create
 *
 * TODO: Add your API key here
 */
@Singleton
class LastFmRepository @Inject constructor() {

    companion object {
        // TODO: Get free API key from https://www.last.fm/api/account/create
        const val API_KEY = "YOUR_LASTFM_API_KEY"
        const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"
    }

    /**
     * Get similar tracks to the given artist + track.
     * Uses Last.fm's collaborative filtering data from real listeners.
     */
    suspend fun getSimilarTracks(artist: String, track: String, limit: Int = 20): List<Song> =
        withContext(Dispatchers.IO) {
            try {
                if (API_KEY == "YOUR_LASTFM_API_KEY") return@withContext emptyList()

                val a = URLEncoder.encode(artist, "UTF-8") ?: return@withContext emptyList()
                val t = URLEncoder.encode(track, "UTF-8") ?: return@withContext emptyList()
                val url = URL("$BASE_URL?method=track.getsimilar&artist=$a&track=$t&api_key=$API_KEY&format=json&limit=$limit")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                if (conn.responseCode != 200) return@withContext emptyList()

                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val tracks = json.optJSONObject("similartracks")?.optJSONArray("track")
                    ?: return@withContext emptyList()

                val songs = mutableListOf<Song>()
                for (i in 0 until tracks.length()) {
                    val trackObj = tracks.optJSONObject(i) ?: continue
                    val name = trackObj.optString("name", "")
                    val artistName = trackObj.optJSONObject("artist")?.optString("name", "")
                    if (name.isBlank() || artistName.isBlank()) continue

                    // Get artwork from Last.fm images
                    var artworkUrl: String? = null
                    val images = trackObj.optJSONArray("image")
                    if (images != null) {
                        for (j in 0 until images.length()) {
                            val img = images.optJSONObject(j)
                            if (img?.optString("size") == "extralarge") {
                                artworkUrl = img.optString("#text", "").ifBlank { null }
                                break
                            }
                        }
                    }

                    songs.add(
                        Song(
                            id = "lastfm:$artistName:$name",
                            title = name,
                            artist = artistName,
                            artworkUrl = artworkUrl
                        )
                    )
                }
                songs
            } catch (e: Exception) {
                emptyList()
            }
        }

    /**
     * Get top tracks by tag/genre (e.g., "rock", "chill", "workout").
     */
    suspend fun getTopTracksByTag(tag: String, limit: Int = 20): List<Song> =
        withContext(Dispatchers.IO) {
            try {
                if (API_KEY == "YOUR_LASTFM_API_KEY") return@withContext emptyList()

                val encodedTag = URLEncoder.encode(tag, "UTF-8") ?: return@withContext emptyList()
                val url = URL("$BASE_URL?method=tag.gettoptracks&tag=$encodedTag&api_key=$API_KEY&format=json&limit=$limit")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                if (conn.responseCode != 200) return@withContext emptyList()

                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val tracks = json.optJSONObject("tracks")?.optJSONArray("track")
                    ?: return@withContext emptyList()

                val songs = mutableListOf<Song>()
                for (i in 0 until tracks.length()) {
                    val trackObj = tracks.optJSONObject(i) ?: continue
                    val name = trackObj.optString("name", "")
                    val artistName = trackObj.optJSONObject("artist")?.optString("name", "")
                    if (name.isBlank() || artistName.isBlank()) continue

                    songs.add(
                        Song(
                            id = "lastfm:$artistName:$name",
                            title = name,
                            artist = artistName,
                            artworkUrl = null
                        )
                    )
                }
                songs
            } catch (e: Exception) {
                emptyList()
            }
        }
}
