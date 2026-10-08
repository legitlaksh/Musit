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
                                // Look for googlevideo audio stream URLs
                                if (url.contains("googlevideo.com") &&
                                    url.contains("mime=audio")
                                ) {
                                    // Found an audio stream URL! Capture and finish.
                                    finish(url)
                                }
                                return null
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // Try to trigger audio loading by playing the video
                                // (muted, we just want the URL, not the audio)
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        try {
                                            var v = document.querySelector('video');
                                            if (v) {
                                                v.muted = true;
                                                v.play().catch(function(){});
                                            }
                                        } catch(e) {}
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
}
