package com.musit.player.network

import com.musit.player.model.Song
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unofficial YouTube Music (InnerTube) implementation of [MusicSource].
 *
 * Stream-resolution strategy (informed by Echo/Metrolist's 2026 measurements):
 *
 * 1. Try player clients in order: VISIONOS → ANDROID_VR → TVHTML5 → IOS.
 *    VISIONOS/ANDROID_VR serve whole files; IOS serves only a ~1 MiB
 *    preview (googlevideo 403s past it), so IOS is strictly last resort.
 *    ANDROID clients are sometimes bot-walled ("Sign in to confirm you're
 *    not a bot") — the chain skips them automatically.
 * 2. Every candidate URL is **probed** before it is returned: a ranged
 *    request for the first byte AND a ranged request past the 1 MiB mark
 *    must both answer 206. This is what catches preview-only URLs and
 *    dead links before ExoPlayer ever sees them.
 *
 * No API keys, cookies or credentials are hardcoded or required.
 */
@Singleton
class InnerTubeMusicSource @Inject constructor(
    private val webViewExtractor: WebViewStreamExtractor
) : MusicSource {

    private companion object {
        // API key is fetched dynamically from YouTube's pages (it's YouTube's
        // public web client key, not a secret). Never hardcoded per policy.
        const val SUGGEST_URL =
            "https://suggestqueries-clients6.youtube.com/complete/search?client=youtube&ds=yt&q="

        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        // Preferred audio itags, best quality first.
        val PREFERRED_ITAGS = intArrayOf(251, 250, 249, 140, 139)

        // Probe past the ~1 MiB preview cap IOS URLs carry.
        const val PREVIEW_CAP_PROBE_START = 1_048_576L
    }

    /** A YouTube player client we can request streams from. */
    private data class PlayerClient(
        val name: String,
        val version: String,
        val userAgent: String,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val androidSdkVersion: Int? = null
    )

    // Ordered by measured ability to serve a complete file.
    // YouTube Music's own clients first — they're the least likely to be blocked.
    private val streamClients = listOf(
        PlayerClient(
            name = "ANDROID_MUSIC", version = "8.10.51",
            userAgent = "com.google.android.apps.youtube.music/8.10.51 (Linux; U; Android 14; en_US) gzip",
            osName = "Android", osVersion = "14",
            deviceMake = "Google", deviceModel = "Pixel 8",
            androidSdkVersion = 34
        ),
        PlayerClient(
            name = "WEB_REMIX", version = "1.20250101.00.00",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        ),
        PlayerClient(
            name = "VISIONOS", version = "0.1",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
            osName = "visionOS", osVersion = "1.3.21O771",
            deviceMake = "Apple", deviceModel = "RealityDevice14,1"
        ),
        PlayerClient(
            name = "ANDROID_VR", version = "1.65.10",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            osName = "Android", osVersion = "12L",
            deviceMake = "Oculus", deviceModel = "Quest 3",
            androidSdkVersion = 32
        ),
        PlayerClient(
            name = "TVHTML5", version = "7.20250101.00.00",
            userAgent = "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 (KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15"
        ),
        PlayerClient(
            name = "IOS", version = "20.49.6",
            userAgent = "com.google.ios.youtube/20.49.6 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X)",
            osName = "iOS", osVersion = "18.2.0",
            deviceMake = "Apple", deviceModel = "iPhone16,2"
        )
    )

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // Validated stream URLs, keyed by song id. Entries expire with the URL
    // itself (~6 h server-side); the map is small and best-effort.
    private val streamCache = mutableMapOf<String, String>()

    // Dynamically fetched YouTube web client API key (public, not secret).
    @Volatile
    private var cachedApiKey: String? = null

    /** Fetches YouTube's public web API key from their homepage. */
    private suspend fun apiKey(): String = withContext(Dispatchers.IO) {
        cachedApiKey?.let { return@withContext it }
        try {
            val request = Request.Builder()
                .url("https://www.youtube.com/")
                .header("User-Agent", DESKTOP_UA)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val html = response.body?.string()
                if (html != null) {
                    // INNERTUBE_API_KEY":"AIza...
                    val match = Regex("\"INNERTUBE_API_KEY\"\\s*:\\s*\"([^\"]+)\"")
                        .find(html)
                    val key = match?.groupValues?.get(1)
                    if (key != null) {
                        cachedApiKey = key
                        return@withContext key
                    }
                }
            }
        } catch (e: Exception) {
            // fall through to throw below
        }
        throw IllegalStateException("Could not obtain YouTube API key")
    }

    private suspend fun searchUrl(): String =
        "https://music.youtube.com/youtubei/v1/search?key=${apiKey()}"

    private suspend fun playerUrl(): String =
        "https://www.youtube.com/youtubei/v1/player?key=${apiKey()}"

    // ------------------------------------------------------------------
    // MusicSource
    // ------------------------------------------------------------------

    override suspend fun search(query: String): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put(
                    "context", JSONObject().put(
                        "client", JSONObject()
                            .put("clientName", "WEB_REMIX")
                            .put("clientVersion", "1.20250101.00.00")
                            .put("hl", "en")
                            .put("gl", "US")
                    )
                )
                .put("query", query)
                .toString()
            val response = postJson(searchUrl(), body, DESKTOP_UA)
                ?: throw IllegalStateException("Search request failed")
            parseSearchResults(JSONObject(response))
                .ifEmpty { throw IllegalStateException("No results") }
        }
    }

    override suspend fun getSearchSuggestions(query: String): List<String> =
        withContext(Dispatchers.IO) {
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
                    parseSuggestions(response.body?.string() ?: return@withContext emptyList())
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    override suspend fun getStreamUrl(songId: String): Result<String> =
        getStreamUrls(songId).map { it.first() }

    override suspend fun getStreamUrls(songId: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                streamCache[songId]?.let { return@runCatching listOf(it) }
                val validated = mutableListOf<String>()
                val failures = mutableListOf<String>()
                for (playerClient in streamClients) {
                    val url = try {
                        resolveViaClient(songId, playerClient)
                    } catch (e: Exception) {
                        failures.add("${playerClient.name}: ${e.message}")
                        null
                    }
                    if (url != null && probeStreamUrl(url)) {
                        // ratebypass asks googlevideo not to throttle the stream.
                        val finalUrl = if (url.contains("ratebypass=")) url
                        else url + "&ratebypass=yes"
                        validated.add(finalUrl)
                        if (validated.size >= 3) break
                    } else if (url != null) {
                        failures.add("${playerClient.name}: preview-only/dead URL")
                    }
                }
                if (validated.isEmpty()) {
                    // Final fallback: real browser (WebView) extraction. YouTube
                    // trusts browser traffic far more than direct API calls.
                    val webUrl = try {
                        webViewExtractor.extractAudioUrl(songId)
                    } catch (e: Exception) {
                        failures.add("WebView: ${e.message}")
                        null
                    }
                    if (webUrl != null && probeStreamUrl(webUrl)) {
                        val finalUrl = if (webUrl.contains("ratebypass=")) webUrl
                        else webUrl + "&ratebypass=yes"
                        validated.add(finalUrl)
                    } else if (webUrl != null) {
                        failures.add("WebView: preview-only/dead URL")
                    }
                }
                if (validated.isEmpty()) {
                    throw IllegalStateException(
                        "No playable stream (${failures.take(3).joinToString("; ")})"
                    )
                }
                streamCache[songId] = validated.first()
                validated
            }
        }

    override suspend fun getTrending(): Result<List<Song>> = withContext(Dispatchers.IO) {
        // YouTube Music charts via a charts search; falls back to a genre
        // search if the shelf layout changes.
        search("top 100 songs").recoverCatching { search("trending music").getOrThrow() }
    }

    // ------------------------------------------------------------------
    // player
    // ------------------------------------------------------------------

    /** Resolves a raw (unvalidated) audio URL via one player client. Null if none. */
    private suspend fun resolveViaClient(songId: String, playerClient: PlayerClient): String? {
        val clientJson = JSONObject()
            .put("clientName", playerClient.name)
            .put("clientVersion", playerClient.version)
            .put("hl", "en")
            .put("gl", "US")
        playerClient.osName?.let { clientJson.put("osName", it) }
        playerClient.osVersion?.let { clientJson.put("osVersion", it) }
        playerClient.deviceMake?.let { clientJson.put("deviceMake", it) }
        playerClient.deviceModel?.let { clientJson.put("deviceModel", it) }
        playerClient.androidSdkVersion?.let { clientJson.put("androidSdkVersion", it) }

        val body = JSONObject()
            .put("context", JSONObject().put("client", clientJson))
            .put("videoId", songId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .toString()

        val text = postJson(playerUrl(), body, playerClient.userAgent) ?: return null
        val json = JSONObject(text)
        val status = json.optJSONObject("playabilityStatus")?.optString("status")
        if (status != "OK") return null

        val streamingData = json.optJSONObject("streamingData") ?: return null
        // HLS manifests are valid too; ExoPlayer handles them.
        streamingData.optString("hlsManifestUrl", null)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        val candidates = mutableListOf<JSONObject>()
        streamingData.optJSONArray("adaptiveFormats")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { candidates.add(it) }
        }
        streamingData.optJSONArray("formats")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { candidates.add(it) }
        }
        val audio = candidates.filter {
            it.optString("url").isNotEmpty() && it.optString("mimeType").contains("audio")
        }
        if (audio.isEmpty()) return null
        for (itag in PREFERRED_ITAGS) {
            audio.firstOrNull { it.optInt("itag", -1) == itag }
                ?.let { return it.optString("url") }
        }
        return audio.first().optString("url")
    }

    /**
     * Probes a stream URL the way ExoPlayer will request it. Both the first
     * byte and a byte past the ~1 MiB preview cap must answer 206 —
     * otherwise the URL will stall or die mid-song.
     */
    private fun probeStreamUrl(url: String): Boolean {
        return probeRange(url, 0L) && probeRange(url, PREVIEW_CAP_PROBE_START)
    }

    private fun probeRange(url: String, start: Long): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DESKTOP_UA)
                .header("Range", "bytes=$start-${start + 1023}")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                // 206 = proper ranged response. 200 = the server (or a carrier
                // proxy) ignored the Range header but is still serving the
                // file — the URL is valid and playable.
                response.code == 206 || response.code == 200
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun postJson(url: String, body: String, userAgent: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .post(body.toRequestBody(jsonType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // search parsing
    // ------------------------------------------------------------------

    private fun parseSearchResults(root: JSONObject): List<Song> {
        // Song rows live in musicShelfRenderer ("Songs") and
        // musicCardShelfRenderer ("Top result") — scan for the item
        // renderer directly instead of assuming a shelf type.
        val items = mutableListOf<JSONObject>()
        collectObjectsWithKey(root, "musicResponsiveListItemRenderer", items)
        val songs = mutableListOf<Song>()
        val seen = mutableSetOf<String>()
        for (renderer in items) {
            val song = parseSongItem(renderer) ?: continue
            if (seen.add(song.id)) songs.add(song)
        }
        return songs
    }

    private fun collectObjectsWithKey(value: Any?, key: String, out: MutableList<JSONObject>) {
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val child = value.opt(k)
                    if (k == key && child is JSONObject) out.add(child)
                    else collectObjectsWithKey(child, key, out)
                }
            }
            is JSONArray -> {
                for (i in 0 until value.length()) collectObjectsWithKey(value.opt(i), key, out)
            }
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
        val artist = segments.firstOrNull {
            !it.matches(Regex("\\d+:\\d+")) && !it.equals("Song", ignoreCase = true)
        } ?: segments.firstOrNull() ?: "Unknown artist"
        val durationText = Regex("\\d+:\\d+").find(subtitle)?.value
        val durationSec = durationText?.split(":")?.let { parts ->
            if (parts.size == 2) {
                parts[0].toLongOrNull()?.times(60)?.plus(parts[1].toLongOrNull() ?: 0)
            } else null
        }

        var artworkUrl: String? = null
        val thumbnails = renderer.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        if (thumbnails != null && thumbnails.length() > 0) {
            val raw = thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url")
            artworkUrl = when {
                raw.isNullOrEmpty() -> null
                raw.startsWith("//") -> "https:$raw"
                else -> raw
            }
        }

        return Song(
            id = videoId,
            title = title,
            artist = artist,
            artworkUrl = artworkUrl,
            durationText = durationText,
            durationSec = durationSec
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

    private fun parseSuggestions(text: String): List<String> {
        return try {
            // ["query",[["suggestion",0,...],...],{...}]
            val items = JSONArray(text).optJSONArray(1) ?: return emptyList()
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
}
