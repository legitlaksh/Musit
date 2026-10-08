package com.musit.player.network

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Extracts playable audio stream URLs using a real WebView (browser fingerprint).
 *
 * YouTube's bot detection is far more lenient toward real browser traffic than
 * direct HTTP requests. This loads the watch page in a WebView and intercepts
 * the actual audio stream URL from the network requests.
 *
 * This is a fallback for when direct InnerTube requests are bot-blocked.
 */
@Singleton
class WebViewStreamExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TIMEOUT_MS = 25_000L
    }

    /**
     * Parses the player_response JSON to find an audio-only stream URL.
     * Looks in streamingData.adaptiveFormats for audio mime types.
     */
    private fun parseAudioUrlFromPlayerResponse(jsonStr: String): String? {
        return try {
            // Remove JSON string escaping from evaluateJavascript result
            val cleanJson = jsonStr
                .removeSurrounding("\"")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
            val root = org.json.JSONObject(cleanJson)
            val streamingData = root.optJSONObject("streamingData") ?: return null
            val formats = streamingData.optJSONArray("adaptiveFormats") ?: return null

            // Prefer audio-only formats (mimeType starts with "audio/")
            for (i in 0 until formats.length()) {
                val f = formats.getJSONObject(i)
                val mime = f.optString("mimeType", "")
                val url = f.optString("url", "")
                if (mime.startsWith("audio/") && url.isNotEmpty()) {
                    return url
                }
            }
            // Fallback: check formats array too
            val formats2 = streamingData.optJSONArray("formats")
            if (formats2 != null) {
                for (i in 0 until formats2.length()) {
                    val f = formats2.getJSONObject(i)
                    val url = f.optString("url", "")
                    if (url.isNotEmpty()) return url
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts the audio stream URL for [videoId] by intercepting the WebView's
     * network requests, or null if unavailable.
     * Must be called from a coroutine; WebView operations run on the main thread.
     */
    suspend fun extractAudioUrl(videoId: String): String? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                var webView: WebView? = null
                var resumed = false

                fun finish(url: String?) {
                    if (!resumed) {
                        resumed = true
                        try {
                            webView?.stopLoading()
                            webView?.destroy()
                        } catch (e: Exception) {
                            // ignore
                        }
                        webView = null
                        if (cont.isActive) cont.resume(url)
                    }
                }

                try {
                    webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url?.toString() ?: return null
                                // Look for googlevideo audio stream URLs.
                                // Check both literal and URL-encoded mime types,
                                // plus known audio-only itags.
                                if (url.contains("googlevideo.com")) {
                                    val isAudio = url.contains("mime=audio") ||
                                        url.contains("mime%3Daudio") ||
                                        url.contains("mime%253Daudio") ||
                                        url.contains("audio%2Fmp4") ||
                                        url.contains("audio%2Fwebm") ||
                                        url.contains("audio/mp4") ||
                                        url.contains("audio/webm") ||
                                        // Audio-only itags: 139, 140, 141, 171, 249, 250, 251
                                        url.contains("itag=139") ||
                                        url.contains("itag=140") ||
                                        url.contains("itag=141") ||
                                        url.contains("itag=171") ||
                                        url.contains("itag=249") ||
                                        url.contains("itag=250") ||
                                        url.contains("itag=251")
                                    if (isAudio) {
                                        // Found an audio stream URL! Capture and finish.
                                        finish(url)
                                    }
                                }
                                return null
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // Extract player_response JSON which contains stream URLs.
                                // This is more reliable than intercepting network requests.
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        try {
                                            // Try multiple ways to get player response
                                            var pr = null;
                                            if (window.ytplayer && window.ytplayer.config && window.ytplayer.config.args) {
                                                pr = window.ytplayer.config.args.player_response;
                                            }
                                            if (!pr && window.ytInitialPlayerResponse) {
                                                pr = JSON.stringify(window.ytInitialPlayerResponse);
                                            }
                                            if (!pr) {
                                                // Parse from page HTML
                                                var html = document.documentElement.innerHTML;
                                                var m = html.match(/ytInitialPlayerResponse\s*=\s*(\{.+?\});/);
                                                if (m) pr = m[1];
                                            }
                                            return pr || 'null';
                                        } catch(e) { return 'error:' + e.message; }
                                    })();
                                    """.trimIndent()
                                ) { result ->
                                    try {
                                        if (result != null && result != "null" && !result.startsWith("error")) {
                                            // Parse the JSON and find audio stream URL
                                            val audioUrl = parseAudioUrlFromPlayerResponse(result)
                                            if (audioUrl != null) {
                                                finish(audioUrl)
                                                return@evaluateJavascript
                                            }
                                        }
                                        // Fallback: try to trigger video play to generate network requests
                                        view?.evaluateJavascript(
                                            """
                                            (function() {
                                                try {
                                                    var v = document.querySelector('video');
                                                    if (v) { v.muted = true; v.play().catch(function(){}); }
                                                } catch(e) {}
                                            })();
                                            """.trimIndent(),
                                            null
                                        )
                                    } catch (e: Exception) {
                                        // Continue waiting for network interception
                                    }
                                }
                            }
                        }

                        loadUrl("https://www.youtube.com/watch?v=$videoId")
                    }

                    cont.invokeOnCancellation {
                        try {
                            webView?.stopLoading()
                            webView?.destroy()
                        } catch (e: Exception) {
                            // ignore
                        }
                        webView = null
                    }
                } catch (e: Exception) {
                    finish(null)
                }
            }
        }
    }
}
