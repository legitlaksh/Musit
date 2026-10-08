package com.musit.player.ui.player

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * YouTube's supported embed player via WebView.
 *
 * This is the ONLY supported way to play YouTube content in a third-party app.
 * We do not extract direct stream URLs or bypass any access controls.
 * Search/metadata comes from the InnerTube-compatible library (NewPipe);
 * playback happens here, completely separated from Media3 (which handles
 * only local on-device files).
 */
@Composable
fun YouTubeEmbedPlayer(
    videoId: String,
    modifier: Modifier = Modifier
) {
    // Use YouTube Music watch page for music tracks (embed doesn't support them).
    // This is YouTube's supported web player.
    val url = remember(videoId) {
        "https://music.youtube.com/watch?v=$videoId&autoplay=1"
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = WebViewClient()
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
