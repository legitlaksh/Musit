package com.musit.player.network

import android.content.Context
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates YouTube Proof-of-Origin (poToken) tokens via a WebView running
 * YouTube's BotGuard. The token goes in `serviceIntegrityDimensions.poToken`
 * of InnerTube player requests, which is what convinces YouTube the request
 * comes from a real client rather than a bot.
 */
@Singleton
class PoTokenGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        const val INIT_TIMEOUT_MS = 30_000L
        const val MINT_TIMEOUT_MS = 15_000L
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json".toMediaType()

    private val initMutex = Mutex()
    private var webView: WebView? = null
    private var initDeferred: CompletableDeferred<Boolean>? = null
    private var mintDeferred: CompletableDeferred<String>? = null
    private val mintMutex = Mutex()

    /**
     * Returns a poToken for [videoId], initializing the WebView/BotGuard on
     * first use. Returns null if token generation fails.
     */
    suspend fun getPoToken(videoId: String): String? {
        if (!ensureInitialized()) return null
        return mintMutex.withLock {
            withTimeoutOrNull(MINT_TIMEOUT_MS) {
                mintToken(videoId)
            }
        }
    }

    private suspend fun ensureInitialized(): Boolean {
        initMutex.withLock {
            initDeferred?.let { return it.await() }
            val deferred = CompletableDeferred<Boolean>()
            initDeferred = deferred
            try {
                val ok = withTimeoutOrNull(INIT_TIMEOUT_MS) {
                    initWebView()
                    deferred.await()
                } ?: false
                if (!ok) {
                    initDeferred = null
                    cleanup()
                }
                return ok
            } catch (e: Exception) {
                initDeferred = null
                cleanup()
                return false
            }
        }
    }

    private suspend fun initWebView() = withContext(Dispatchers.Main) {
        val wv = WebView(context)
        webView = wv
        wv.settings.javaScriptEnabled = true
        wv.settings.blockNetworkLoads = true
        wv.addJavascriptInterface(Bridge(), "MusitPoToken")

        val html = context.assets.open("po_token.html").bufferedReader().use { it.readText() }
        // Trigger downloadAndRunBotguard() after the page loads.
        val patched = html.replaceFirst(
            "</script>",
            "\nMusitPoToken.downloadAndRunBotguard()</script>"
        )
        wv.loadDataWithBaseURL("https://www.youtube.com", patched, "text/html", "utf-8", null)
    }

    private suspend fun mintToken(videoId: String): String? = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<String>()
        mintDeferred = deferred
        try {
            // Identifier is the videoId as bytes; JS converts to Uint8Array.
            val js = """
                (function() {
                    try {
                        var idBytes = new TextEncoder().encode("$videoId");
                        obtainPoToken(idBytes).then(function(tokenBytes) {
                            var binary = "";
                            for (var i = 0; i < tokenBytes.length; i++) {
                                binary += String.fromCharCode(tokenBytes[i]);
                            }
                            MusitPoToken.onPoTokenMinted(btoa(binary));
                        }).catch(function(e) {
                            MusitPoToken.onPoTokenError(e.message || ("" + e));
                        });
                    } catch (e) {
                        MusitPoToken.onPoTokenError(e.message || ("" + e));
                    }
                })();
            """.trimIndent()
            webView?.evaluateJavascript(js, null)
            deferred.await()
        } catch (e: Exception) {
            null
        } finally {
            mintDeferred = null
        }
    }

    private fun cleanup() {
        try {
            webView?.stopLoading()
            webView?.destroy()
        } catch (e: Exception) {
            // ignore
        }
        webView = null
    }

    private inner class Bridge {
        @JavascriptInterface
        fun downloadAndRunBotguard() {
            Thread {
                try {
                    val challengeJson = postBotguard(
                        "https://www.youtube.com/api/jnn/v1/Create",
                        JSONArray().put(REQUEST_KEY).toString()
                    ) ?: throw IllegalStateException("Create failed")
                    // challengeJson is like {"globalName": "...", "program": "...", ...}
                    val escaped = JSONObject.quote(challengeJson)
                    val js = """
                        try {
                            var data = JSON.parse($escaped);
                            runBotGuard(data).then(function(result) {
                                this.webPoSignalOutput = result.webPoSignalOutput;
                                MusitPoToken.onRunBotguardResult(result.botguardResponse);
                            }).catch(function(e) {
                                MusitPoToken.onJsError("runBotGuard: " + (e.message || e));
                            });
                        } catch (e) {
                            MusitPoToken.onJsError("parse: " + (e.message || e));
                        }
                    """.trimIndent()
                    webView?.post { webView?.evaluateJavascript(js, null) }
                } catch (e: Exception) {
                    initDeferred?.complete(false)
                }
            }.start()
        }

        @JavascriptInterface
        fun onRunBotguardResult(botguardResponse: String) {
            Thread {
                try {
                    val respJson = postBotguard(
                        "https://www.youtube.com/api/jnn/v1/GenerateIT",
                        JSONArray().put(REQUEST_KEY).put(botguardResponse).toString()
                    ) ?: throw IllegalStateException("GenerateIT failed")
                    // Response: {"integrityToken": "...", "expirationTimeInSeconds": ...}
                    // integrityToken is a JSON-encoded Uint8Array-ish structure; pass raw.
                    val obj = JSONObject(respJson)
                    val integrityToken = obj.get("integrityToken").toString()
                    val js = """
                        try {
                            this.integrityToken = $integrityToken;
                            createPoTokenMinter(webPoSignalOutput, integrityToken).then(function() {
                                MusitPoToken.onMinterCreated();
                            }).catch(function(e) {
                                MusitPoToken.onJsError("minter: " + (e.message || e));
                            });
                        } catch (e) {
                            MusitPoToken.onJsError("minterSync: " + (e.message || e));
                        }
                    """.trimIndent()
                    webView?.post { webView?.evaluateJavascript(js, null) }
                } catch (e: Exception) {
                    initDeferred?.complete(false)
                }
            }.start()
        }

        @JavascriptInterface
        fun onMinterCreated() {
            initDeferred?.complete(true)
        }

        @JavascriptInterface
        fun onPoTokenMinted(base64Token: String) {
            mintDeferred?.complete(base64Token)
        }

        @JavascriptInterface
        fun onPoTokenError(error: String) {
            mintDeferred?.completeExceptionally(IllegalStateException(error))
        }

        @JavascriptInterface
        fun onJsError(error: String) {
            initDeferred?.complete(false)
        }
    }

    private fun postBotguard(url: String, body: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody(jsonType))
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (e: Exception) {
            null
        }
    }
}
