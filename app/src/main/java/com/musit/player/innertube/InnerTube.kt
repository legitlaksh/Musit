package com.musit.player.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Minimal YouTube Music (InnerTube) client.
 *
 * Search uses the WEB_REMIX client on music.youtube.com.
 * Audio stream URLs come from ANDROID_VR first (direct audio URLs, no
 * signature deciphering), falling back to IOS (HLS manifest or
 * progressive audio formats).
 */
data class Song(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationText: String?,
    val thumbnailUrl: String?
)

object InnerTube {

    private const val API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?key=$API_KEY"
    private const val PLAYER_URL_KEYED = "https://www.youtube.com/youtubei/v1/player?key=$API_KEY"
    private const val SUGGEST_URL = "https://suggestqueries-clients6.youtube.com/complete/search?client=youtube&ds=yt&q="

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    private const val IOS_UA =
        "com.google.ios.youtube/20.49.6 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X)"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var cachedVisitorData: String? = null

    // ------------------------------------------------------------------
    // visitorData (required on player calls)
    // ------------------------------------------------------------------

    /**
     * Mints visitorData by fetching a watch page and extracting it from the
     * embedded HTML. Cached in memory. Best effort: returns null on failure.
     */
    suspend fun getVisitorData(videoId: String): String? = withContext(Dispatchers.IO) {
        cachedVisitorData?.let { return@withContext it }
        var result: String? = null
        try {
            val request = Request.Builder()
                .url("https://www.youtube.com/watch?v=$videoId")
                .header("User-Agent", DESKTOP_UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val html = response.body?.string() ?: return@withContext null
                val vd = Regex("\"visitorData\":\"([A-Za-z0-9_\\-]+)\"")
                    .find(html)
                    ?.groupValues
                    ?.getOrNull(1)
                if (!vd.isNullOrEmpty()) {
                    cachedVisitorData = vd
                    result = vd
                }
            }
        } catch (e: Exception) {
            // best effort only; caller may still try IOS without it
        }
        result
    }

    // ------------------------------------------------------------------
    // search
    // ------------------------------------------------------------------

    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", "WEB_REMIX")
                        .put("clientVersion", "1.20260114.03.00")
                        .put("hl", "en")
                        .put("gl", "US")
                )
            )
            .put("query", query)
            .toString()

        val request = Request.Builder()
            .url(SEARCH_URL)
            .header("Content-Type", "application/json")
            .header("User-Agent", DESKTOP_UA)
            .post(body.toRequestBody(jsonType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val text = response.body?.string() ?: return@withContext emptyList()
                parseSearchResults(JSONObject(text))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseSearchResults(root: JSONObject): List<Song> {
        // Collect song rows from anywhere in the response: YouTube Music puts
        // results in musicShelfRenderer ("Songs" shelf) but also in
        // musicCardShelfRenderer ("Top result"), so scan for the item
        // renderer directly instead of assuming a shelf type.
        val items = mutableListOf<JSONObject>()
        collectObjectsWithKey(root, "musicResponsiveListItemRenderer", items)
        val songs = mutableListOf<Song>()
        val seen = mutableSetOf<String>()
        for (renderer in items) {
            val song = parseSongItem(renderer) ?: continue
            if (seen.add(song.videoId)) songs.add(song)
        }
        return songs
    }

    /** Recursively collects every JSONObject stored under [key], anywhere in the tree. */
    private fun collectObjectsWithKey(value: Any?, key: String, out: MutableList<JSONObject>) {
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val child = value.opt(k)
                    if (k == key && child is JSONObject) {
                        out.add(child)
                    } else {
                        collectObjectsWithKey(child, key, out)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    collectObjectsWithKey(value.opt(i), key, out)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // search suggestions
    // ------------------------------------------------------------------

    /** YouTube search suggestions for the given prefix. */
    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val url = SUGGEST_URL + URLEncoder.encode(query.trim(), "UTF-8")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DESKTOP_UA)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val text = response.body?.string() ?: return@withContext emptyList()
                parseSuggestions(text)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseSuggestions(text: String): List<String> {
        return try {
            // Format: ["query",[["suggestion",0,...],...],{...}]
            val root = JSONArray(text)
            val items = root.optJSONArray(1) ?: return emptyList()
            val out = mutableListOf<String>()
            for (i in 0 until items.length()) {
                val s = when (val item = items.opt(i)) {
                    is JSONArray -> item.optString(0, null)
                    is String -> item
                    else -> null
                }
                if (!s.isNullOrBlank()) out.add(s)
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseSongItem(renderer: JSONObject): Song? {
        var videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId")
        if (videoId.isNullOrEmpty()) {
            videoId = renderer.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
        }
        if (videoId.isNullOrEmpty()) return null

        val flexColumns = renderer.optJSONArray("flexColumns") ?: return null
        val title = flexColumnText(flexColumns, 0) ?: return null
        val subtitle = flexColumnText(flexColumns, 1).orEmpty()

        val segments = subtitle.split("•").map { it.trim() }.filter { it.isNotEmpty() }
        val artist = segments.firstOrNull { !it.matches(Regex("\\d+:\\d+")) }
            ?: "Unknown artist"
        val durationText = Regex("\\d+:\\d+").find(subtitle)?.value

        var thumbnailUrl: String? = null
        val thumbnails = renderer.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        if (thumbnails != null && thumbnails.length() > 0) {
            val raw = thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url")
            thumbnailUrl = when {
                raw.isNullOrEmpty() -> null
                raw.startsWith("//") -> "https:$raw"
                else -> raw
            }
        }

        return Song(
            videoId = videoId,
            title = title,
            artist = artist,
            durationText = durationText,
            thumbnailUrl = thumbnailUrl
        )
    }

    private fun flexColumnText(flexColumns: JSONArray, index: Int): String? {
        val runs = flexColumns.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?: return null
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        return sb.toString().ifEmpty { null }
    }

    // ------------------------------------------------------------------
    // audio stream URLs
    // ------------------------------------------------------------------

    /**
     * Resolves a playable audio URL via the IOS client (direct audio URLs,
     * HLS manifest fallback). ANDROID clients are currently bot-blocked by
     * YouTube ("Sign in to confirm you're not a bot"), so IOS goes first.
     * visitorData is best-effort: most videos resolve without it.
     */
    suspend fun getAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        iosStreamUrl(videoId, visitorData = null)
            ?: getVisitorData(videoId)?.let { iosStreamUrl(videoId, visitorData = it) }
    }

    private suspend fun iosStreamUrl(videoId: String, visitorData: String?): String? =
        withContext(Dispatchers.IO) {
            val clientJson = JSONObject()
                .put("clientName", "IOS")
                .put("clientVersion", "20.49.6")
                .put("deviceMake", "Apple")
                .put("deviceModel", "iPhone16,2")
                .put("osName", "iOS")
                .put("osVersion", "18.2.0")
                .put("hl", "en")
                .put("gl", "US")
            if (!visitorData.isNullOrEmpty()) clientJson.put("visitorData", visitorData)

            val body = JSONObject()
                .put("context", JSONObject().put("client", clientJson))
                .put("videoId", videoId)
                .put("contentCheckOk", true)
                .put("racyCheckOk", true)
                .toString()

            val requestBuilder = Request.Builder()
                .url(PLAYER_URL_KEYED)
                .header("Content-Type", "application/json")
                .header("User-Agent", IOS_UA)
                .post(body.toRequestBody(jsonType))
            if (!visitorData.isNullOrEmpty()) {
                requestBuilder.header("X-Goog-Visitor-Id", visitorData)
            }

            try {
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val text = response.body?.string() ?: return@withContext null
                    val json = JSONObject(text)
                    val hls = json.optJSONObject("streamingData")?.optString("hlsManifestUrl")
                    if (!hls.isNullOrEmpty()) return@withContext hls
                    pickAudioUrl(json)
                }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Picks the best direct audio URL from a player response:
     * itag 251, 250, 249, 140 first, then any audio format.
     */
    private fun pickAudioUrl(playerResponse: JSONObject): String? {
        val status = playerResponse.optJSONObject("playabilityStatus")?.optString("status")
        if (status != "OK" && status != "PLAYABLE") return null

        val streamingData = playerResponse.optJSONObject("streamingData") ?: return null
        val candidates = mutableListOf<JSONObject>()
        streamingData.optJSONArray("adaptiveFormats")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { candidates.add(it) }
        }
        streamingData.optJSONArray("formats")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { candidates.add(it) }
        }

        val audio = candidates.filter { format ->
            format.optString("url").isNotEmpty() &&
                format.optString("mimeType").contains("audio")
        }
        if (audio.isEmpty()) return null

        for (itag in intArrayOf(251, 250, 249, 140)) {
            val match = audio.firstOrNull { it.optInt("itag", -1) == itag }
            if (match != null) return match.optString("url")
        }
        return audio.first().optString("url")
    }
}
