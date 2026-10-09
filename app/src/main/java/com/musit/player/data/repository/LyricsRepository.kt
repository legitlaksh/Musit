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
 * A single line of synced lyrics with timestamp in milliseconds.
 */
data class LyricLine(
    val timestampMs: Long,
    val text: String
)

/**
 * Lyrics result: either synced (with timestamps) or plain text.
 */
sealed class LyricsResult {
    data class Synced(val lines: List<LyricLine>) : LyricsResult()
    data class Plain(val text: String) : LyricsResult()
    data object NotFound : LyricsResult()
}

/**
 * Fetches lyrics using the free LRCLIB API (https://lrclib.net).
 * Returns synced lyrics (LRC format) if available, else plain text.
 */
@Singleton
class LyricsRepository @Inject constructor() {

    suspend fun getLyrics(artist: String, title: String): LyricsResult = withContext(Dispatchers.IO) {
        try {
            val a = URLEncoder.encode(artist, "UTF-8")
            val t = URLEncoder.encode(title, "UTF-8")
            val url = URL("https://lrclib.net/api/get?artist_name=$a&track_name=$t")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "musit-android/1.0")
            if (conn.responseCode != 200) return@withContext LyricsResult.NotFound
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)

            // Try synced lyrics first
            val synced = json.optString("syncedLyrics", "")
            if (synced.isNotBlank()) {
                val lines = parseLrc(synced)
                if (lines.isNotEmpty()) {
                    return@withContext LyricsResult.Synced(lines)
                }
            }

            // Fallback to plain lyrics
            val plain = json.optString("plainLyrics", "")
            if (plain.isNotBlank()) {
                LyricsResult.Plain(plain)
            } else {
                LyricsResult.NotFound
            }
        } catch (e: Exception) {
            LyricsResult.NotFound
        }
    }

    /**
     * Parse LRC format: [mm:ss.xx] lyric text
     * Returns list of LyricLine sorted by timestamp.
     */
    private fun parseLrc(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")
        for (line in lrc.lines()) {
            val match = regex.find(line.trim()) ?: continue
            val (min, sec, ms, text) = match.destructured
            val timestampMs = min.toLong() * 60_000 +
                    sec.toLong() * 1_000 +
                    ms.padEnd(3, '0').toLong()
            val trimmedText = text.trim()
            if (trimmedText.isNotBlank()) {
                lines.add(LyricLine(timestampMs, trimmedText))
            }
        }
        return lines.sortedBy { it.timestampMs }
    }

    /**
     * Legacy: returns plain text only (for backward compatibility).
     */
    suspend fun getPlainLyrics(artist: String, title: String): String? {
        return when (val result = getLyrics(artist, title)) {
            is LyricsResult.Plain -> result.text
            is LyricsResult.Synced -> result.lines.joinToString("\n") { it.text }
            LyricsResult.NotFound -> null
        }
    }
}
