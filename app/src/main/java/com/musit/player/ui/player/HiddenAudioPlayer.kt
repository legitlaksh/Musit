package com.musit.player.ui.player

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Hidden YouTube Music player for AUDIO playback.
 *
 * The WebView is invisible (0 size) — it only plays audio via YouTube's
 * official web player. The beautiful native UI (artwork, controls) is
 * shown on top by PlayerScreen, just like Echo.
 *
 * Playback is controlled via JavaScript bridge.
 */
@Composable
fun HiddenAudioPlayer(
    videoId: String,
    onWebViewReady: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    val url = remember(videoId) {
        "https://music.youtube.com/watch?v=$videoId&autoplay=1"
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Auto-play when loaded
                        view?.evaluateJavascript(
                            "document.querySelector('video')?.play()",
                            null
                        )
                        view?.let { onWebViewReady(it) }
                    }
                }
                loadUrl(url)
            }
        },
        update = { webView ->
            webView.loadUrl(url)
        },
        modifier = modifier,
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        }
    )
}

/**
 * Controller for the hidden audio player.
 * Call these from the native UI buttons.
 */
object AudioPlayerBridge {
    fun play(webView: WebView?) {
        webView?.evaluateJavascript(
            "document.querySelector('video')?.play()",
            null
        )
    }

    fun pause(webView: WebView?) {
        webView?.evaluateJavascript(
            "document.querySelector('video')?.pause()",
            null
        )
    }

    fun seekTo(webView: WebView?, seconds: Float) {
        webView?.evaluateJavascript(
            "document.querySelector('video').currentTime = $seconds",
            null
        )
    }
}
