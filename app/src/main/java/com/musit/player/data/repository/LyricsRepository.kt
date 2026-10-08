package com.musit.player.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches lyrics using the free LRCLIB API (https://lrclib.net).
 * Returns plain text lyrics, or null if not found.
 */
@Singleton
class LyricsRepository @Inject constructor() {

    suspend fun getLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        try {
            val a = URLEncoder.encode(artist, "UTF-8")
            val t = URLEncoder.encode(title, "UTF-8")
            val url = URL("https://lrclib.net/api/get?artist_name=$a&track_name=$t")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "musit-android/1.0")
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val plain = json.optString("plainLyrics", "")
            if (plain.isBlank()) null else plain
        } catch (e: Exception) {
            null
        }
    }
}
