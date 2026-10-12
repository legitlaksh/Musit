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

    suspend fun getLyrics(artist: String, title: String, durationSec: Long? = null): LyricsResult = withContext(Dispatchers.IO) {
        try {
            // Clean the title (remove "Official Video", etc.)
            val cleanTitle = title
                .replace(Regex("\\(Official.*?\\)", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\[Official.*?\\]", RegexOption.IGNORE_CASE), "")
                .replace(Regex("Official Video", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifBlank { title }

            // If artist is generic, try with just the title
            val isGenericArtist = artist.equals("Video", ignoreCase = true) ||
                    artist.equals("Unknown artist", ignoreCase = true) ||
                    artist.isBlank()

            // Try artist + title (+ duration) first (unless generic)
            if (!isGenericArtist) {
                val result = fetchLyrics(artist, cleanTitle, durationSec)
                if (result is LyricsResult.Synced || result is LyricsResult.Plain) {
                    return@withContext result
                }
            }

            // Fallback: search API, preferring artist + duration matches
            val searchResult = searchLyrics(cleanTitle, artist.takeIf { !isGenericArtist }, durationSec)
            if (searchResult != null) {
                return@withContext searchResult
            }

            LyricsResult.NotFound
        } catch (e: Exception) {
            LyricsResult.NotFound
        }
    }

    /** Duration tolerance (seconds) when matching search results to the playing song. */
    private companion object {
        const val DURATION_TOLERANCE_SEC = 10L
    }

    private suspend fun fetchLyrics(artist: String, title: String, durationSec: Long? = null): LyricsResult {
        val a = URLEncoder.encode(artist, "UTF-8") ?: return LyricsResult.NotFound
        val t = URLEncoder.encode(title, "UTF-8") ?: return LyricsResult.NotFound
        // LRCLIB /api/get accepts an optional duration param to disambiguate versions.
        val durationParam = if (durationSec != null && durationSec > 0) "&duration=$durationSec" else ""
        val url = URL("https://lrclib.net/api/get?artist_name=$a&track_name=$t$durationParam")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", "musit-android/1.0")
        if (conn.responseCode != 200) return LyricsResult.NotFound
        val body = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(body)

        val synced = json.optString("syncedLyrics", "")
        if (synced.isNotBlank()) {
            val lines = parseLrc(synced)
            if (lines.isNotEmpty()) {
                return LyricsResult.Synced(lines)
            }
        }

        val plain = json.optString("plainLyrics", "")
        if (plain.isNotBlank()) {
            return LyricsResult.Plain(plain)
        }
        return LyricsResult.NotFound
    }

    /**
     * Search API fallback for when artist is unknown or /api/get missed.
     * Scores every result instead of blindly taking the first: prefers
     * artist matches, then duration within [DURATION_TOLERANCE_SEC], then
     * synced lyrics over plain text. This avoids grabbing lyrics for the
     * wrong version (remix/live/cover) of a song.
     */
    private suspend fun searchLyrics(query: String, artist: String?, durationSec: Long?): LyricsResult? {
        try {
            val q = URLEncoder.encode(query, "UTF-8") ?: return null
            val url = URL("https://lrclib.net/api/search?q=$q")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "musit-android/1.0")
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            val arr = org.json.JSONArray(body)
            if (arr.length() == 0) return null

            var best: org.json.JSONObject? = null
            var bestScore = Int.MIN_VALUE
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                var score = 0
                // Artist match is the strongest signal.
                val resultArtist = obj.optString("artistName", "")
                if (!artist.isNullOrBlank() && resultArtist.isNotBlank()) {
                    val a = artist.lowercase()
                    val r = resultArtist.lowercase()
                    if (a == r) score += 100
                    else if (r.contains(a) || a.contains(r)) score += 50
                }
                // Duration within tolerance strongly suggests the right version.
                val resultDuration = obj.optDouble("duration", -1.0)
                if (durationSec != null && durationSec > 0 && resultDuration >= 0) {
                    val diff = kotlin.math.abs(resultDuration - durationSec)
                    if (diff <= DURATION_TOLERANCE_SEC) score += 75
                    else score -= 25
                }
                // Prefer synced lyrics.
                if (obj.optString("syncedLyrics", "").isNotBlank()) score += 10
                if (score > bestScore) {
                    bestScore = score
                    best = obj
                }
            }

            val first = best ?: return null
            val synced = first.optString("syncedLyrics", "")
            if (synced.isNotBlank()) {
                val lines = parseLrc(synced)
                if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
            }
            val plain = first.optString("plainLyrics", "")
            if (plain.isNotBlank()) return LyricsResult.Plain(plain)
        } catch (e: Exception) {
            // Ignore
        }
        return null
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
    suspend fun getPlainLyrics(artist: String, title: String, durationSec: Long? = null): String? {
        return when (val result = getLyrics(artist, title, durationSec)) {
            is LyricsResult.Plain -> result.text
            is LyricsResult.Synced -> result.lines.joinToString("\n") { it.text }
            LyricsResult.NotFound -> null
        }
    }
}
