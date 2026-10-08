package com.musit.player.network

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Extracts playable audio stream URLs using a real WebView (browser fingerprint).
 *
 * YouTube's bot detection is far more lenient toward real browser traffic than
 * direct HTTP requests. This loads the watch page in a WebView and extracts
 * the player response, which contains working stream URLs.
 *
 * This is a fallback for when direct InnerTube requests are bot-blocked.
 */
@Singleton
class WebViewStreamExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TIMEOUT_MS = 20_000L
        // Preferred audio itags, best quality first.
        private val PREFERRED_ITAGS = intArrayOf(251, 250, 249, 140, 139)
    }

    /**
     * Extracts the best audio stream URL for [videoId], or null if unavailable.
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

                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onPlayerResponse(json: String) {
                                val url = parseAudioUrl(json)
                                finish(url)
                            }
                        }, "MusitBridge")

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // Extract ytInitialPlayerResponse after page loads
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        try {
                                            var pr = null;
                                            // Try ytInitialPlayerResponse (embedded in page)
                                            if (typeof ytInitialPlayerResponse !== 'undefined' && ytInitialPlayerResponse) {
                                                pr = ytInitialPlayerResponse;
                                            }
                                            // Try ytplayer config
                                            if (!pr && typeof ytplayer !== 'undefined' && ytplayer.config && ytplayer.config.args) {
                                                var args = ytplayer.config.args;
                                                if (args.player_response) {
                                                    pr = JSON.parse(args.player_response);
                                                }
                                            }
                                            if (pr) {
                                                MusitBridge.onPlayerResponse(JSON.stringify(pr));
                                            } else {
                                                MusitBridge.onPlayerResponse('');
                                            }
                                        } catch (e) {
                                            MusitBridge.onPlayerResponse('');
                                        }
                                    })();
                                    """.trimIndent(),
                                    null
                                )
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

    /** Parses the player response JSON and returns the best audio URL. */
    private fun parseAudioUrl(json: String): String? {
        if (json.isEmpty()) return null
        return try {
            val root = JSONObject(json)
            val streamingData = root.optJSONObject("streamingData") ?: return null
            val formats = streamingData.optJSONArray("adaptiveFormats") ?: return null

            var bestUrl: String? = null
            var bestRank = Int.MAX_VALUE

            for (i in 0 until formats.length()) {
                val f = formats.getJSONObject(i)
                val itag = f.optInt("itag", -1)
                val mime = f.optString("mimeType", "")
                if (!mime.contains("audio")) continue

                val url = f.optString("url", "").ifEmpty { null }
                // Skip ciphered URLs (would need deciphering)
                if (url == null) continue

                val rank = PREFERRED_ITAGS.indexOf(itag).let { if (it == -1) 999 else it }
                if (rank < bestRank) {
                    bestRank = rank
                    bestUrl = url
                }
            }
            bestUrl
        } catch (e: Exception) {
            null
        }
    }
}
