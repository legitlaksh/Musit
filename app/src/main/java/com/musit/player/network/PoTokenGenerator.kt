package com.musit.player.network

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
 * Generates YouTube PO tokens by running BotGuard in a hidden WebView.
 *
 * Flow:
 *  1. POST Create            -> scrambled challenge (JSON array)
 *  2. descramble + parse     -> interpreter JS, program, globalName
 *  3. WebView runs VM        -> botguardResponse
 *  4. POST GenerateIT        -> integrity token (base64) + ttl
 *  5. WebView builds minter  -> mint(identifier) -> base64url PO token
 *
 * Usage: getPoToken(videoId) for the player request,
 *        getPoToken(visitorData) for the stream URL (&pot=).
 */
@Singleton
class PoTokenGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG = "PoToken"
        const val CREATE_URL = "https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa/Create"
        const val GENERATE_IT_URL = "https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa/GenerateIT"
        const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        const val GOOG_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        const val INIT_TIMEOUT_MS = 40_000L
        const val MINT_TIMEOUT_MS = 15_000L
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val jspb = "application/json+protobuf".toMediaType()

    private val lock = Mutex()
    private var webView: WebView? = null
    private var expiresAtMs = 0L

    @Volatile private var pageLoaded: CompletableDeferred<Unit>? = null
    @Volatile private var bgResult: CompletableDeferred<String>? = null
    @Volatile private var minterReady: CompletableDeferred<Unit>? = null
    @Volatile private var mintResult: CompletableDeferred<String>? = null

    /** Returns a base64url PO token bound to [identifier], or null on failure. */
    suspend fun getPoToken(identifier: String): String? = lock.withLock {
        try {
            if (webView == null || System.currentTimeMillis() >= expiresAtMs) {
                init()
            }
            mint(identifier)
        } catch (e: Throwable) {
            Log.e(TAG, "getPoToken failed: ${e.message}", e)
            destroy()
            null
        }
    }

    // ------------------------------------------------------------------

    private suspend fun init() {
        destroy()
        withTimeout(INIT_TIMEOUT_MS) {
            // 1-2. challenge
            val raw = withContext(Dispatchers.IO) { post(CREATE_URL, JSONArray().put(REQUEST_KEY).toString()) }
            val challenge = parseChallenge(raw)
            Log.d(TAG, "challenge ok: global=${challenge.getString("globalName")}")

            // 3. WebView + BotGuard
            startWebView()
            bgResult = CompletableDeferred()
            evalJs("bgRun(${challenge});")
            val botguardResponse = bgResult!!.await()
            Log.d(TAG, "botguard response len=${botguardResponse.length}")

            // 4. integrity token. Response is an ARRAY:
            //    [integrityTokenB64, ttlSeconds, refreshThreshold, fallbackToken]
            val itRaw = withContext(Dispatchers.IO) {
                post(
                    GENERATE_IT_URL,
                    JSONArray().put(REQUEST_KEY).put(botguardResponse).toString()
                )
            }
            val itArr = JSONArray(itRaw)
            val integrityToken = itArr.getString(0)
            val ttlSec = itArr.optLong(1, 3600L)
            expiresAtMs = System.currentTimeMillis() + (ttlSec * 1000L * 9 / 10)
            Log.d(TAG, "integrity token ok, ttl=${ttlSec}s")

            // 5. minter
            minterReady = CompletableDeferred()
            evalJs("bgCreateMinter(${JSONObject.quote(integrityToken)});")
            minterReady!!.await()
            Log.d(TAG, "minter ready")
        }
    }

    private suspend fun mint(identifier: String): String = withTimeout(MINT_TIMEOUT_MS) {
        mintResult = CompletableDeferred()
        evalJs("bgMint(${JSONObject.quote(identifier)});")
        mintResult!!.await()
    }

    /** Descramble (if needed) and extract what the WebView needs. */
    private fun parseChallenge(raw: String): JSONObject {
        var arr = JSONArray(raw)
        // Scrambled form: [messageId, "<scrambled base64>"]
        if (arr.length() > 1 && arr.opt(1) is String) {
            arr = JSONArray(descramble(arr.getString(1)))
        }
        // [messageId, wrappedScript, wrappedUrl, hash, program, globalName, ?, experiments]
        val wrappedScript = arr.optJSONArray(1)
        var script: String? = null
        if (wrappedScript != null) {
            for (i in 0 until wrappedScript.length()) {
                val v = wrappedScript.opt(i)
                if (v is String && v.isNotEmpty()) { script = v; break }
            }
        }
        val program = arr.optString(4)
        val globalName = arr.optString(5)
        require(!script.isNullOrEmpty() && program.isNotEmpty() && globalName.isNotEmpty()) {
            "unexpected challenge shape (script=${script != null}, program=${program.isNotEmpty()}, global=$globalName)"
        }
        return JSONObject()
            .put("interpreterJs", script)
            .put("program", program)
            .put("globalName", globalName)
    }

    /** base64 (url-safe tolerant) -> each byte + 97 -> UTF-8 text. */
    private fun descramble(s: String): String {
        val std = s.replace('-', '+').replace('_', '/').replace('.', '=')
        val bytes = Base64.decode(std, Base64.DEFAULT)
        val out = ByteArray(bytes.size) { ((bytes[it].toInt() + 97) and 0xFF).toByte() }
        return String(out, Charsets.UTF_8)
    }

    private fun post(url: String, body: String): String {
        val req = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json+protobuf")
            .header("x-goog-api-key", GOOG_API_KEY)
            .header("x-user-agent", "grpc-web-javascript/0.1")
            .header("User-Agent", UA)
            .post(body.toRequestBody(jspb))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("HTTP ${resp.code} from ${url.substringAfterLast('/')}: ${text.take(200)}")
            }
            return text
        }
    }

    // ------------------------------------------------------------------
    // WebView plumbing
    // ------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun startWebView() {
        val loaded = CompletableDeferred<Unit>()
        pageLoaded = loaded
        withContext(Dispatchers.Main) {
            val wv = WebView(context)
            wv.settings.javaScriptEnabled = true
            wv.addJavascriptInterface(Bridge(), "MusitPoToken")
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    loaded.complete(Unit)
                }
            }
            val html = context.assets.open("po_token.html").bufferedReader().use { it.readText() }
            wv.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
            webView = wv
        }
        loaded.await()
    }

    private suspend fun evalJs(js: String) = withContext(Dispatchers.Main) {
        webView?.evaluateJavascript(js, null)
    }

    private fun destroy() {
        val wv = webView
        webView = null
        expiresAtMs = 0L
        if (wv != null) {
            wv.post {
                try { wv.stopLoading(); wv.destroy() } catch (_: Exception) {}
            }
        }
    }

    private inner class Bridge {
        @JavascriptInterface fun onBotguardResult(response: String) {
            bgResult?.complete(response)
        }
        @JavascriptInterface fun onMinterCreated() {
            minterReady?.complete(Unit)
        }
        @JavascriptInterface fun onPoToken(identifier: String, token: String) {
            mintResult?.complete(token)
        }
        @JavascriptInterface fun onError(message: String) {
            Log.e(TAG, "JS error: $message")
            val ex = IllegalStateException(message)
            bgResult?.completeExceptionally(ex)
            minterReady?.completeExceptionally(ex)
            mintResult?.completeExceptionally(ex)
        }
    }
}
