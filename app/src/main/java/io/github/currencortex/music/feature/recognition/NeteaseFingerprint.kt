package io.github.currencortex.music.feature.recognition

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Runs the bundled NetEase fingerprint runtime locally, without a network-capable page or microphone bridge. */
class NeteaseFingerprint(context: Context) {
    private val context = context.applicationContext

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun encode(samples: ShortArray): String {
        require(samples.size in 16_000..120_000)
        val pcm = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, sample -> pcm[i * 2] = sample.toByte(); pcm[i * 2 + 1] = (sample.toInt() shr 8).toByte() }
        val encoded = try { Base64.getEncoder().encodeToString(pcm) } finally { pcm.fill(0) }
        val html = withContext(Dispatchers.IO) {
            val runtime = context.assets.open("recognition/afp.js").bufferedReader().use { it.readText() }
            val wasm = context.assets.open("recognition/afp.wasm.js").bufferedReader().use { it.readText() }
            """<!doctype html><meta charset="utf-8"><script>
                window.addEventListener('error', function() { CurrentMusicFingerprint.failure(); });
                window.addEventListener('unhandledrejection', function(event) {
                  event.preventDefault(); CurrentMusicFingerprint.failure();
                });
                </script><script>$wasm</script><script>$runtime</script>
                <script>
                async function identifyPcm() {
                  let floats;
                  try {
                    const raw = atob('$encoded');
                    floats = new Float32Array(raw.length / 2);
                    for (let i = 0; i < floats.length; i++) {
                      const n = raw.charCodeAt(i*2) | (raw.charCodeAt(i*2+1) << 8);
                      floats[i] = (n >= 32768 ? n - 65536 : n) / 32768;
                    }
                    const result = await GenerateFP(floats);
                    CurrentMusicFingerprint.success(result);
                  } catch (e) { CurrentMusicFingerprint.failure(); }
                  finally { if (floats) floats.fill(0); }
                }
                identifyPcm();
                </script>""".trimIndent()
        }
        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val main = Handler(Looper.getMainLooper())
                var web: WebView? = null
                var closed = false
                fun close() {
                    if (!closed) {
                        closed = true
                        val active = web
                        web = null
                        // A dead renderer can make individual cleanup operations fail.
                        // Always attempt destroy, including setup/load failures.
                        runCatching { active?.stopLoading() }
                        runCatching { active?.removeJavascriptInterface("CurrentMusicFingerprint") }
                        runCatching { active?.destroy() }
                    }
                }
                fun complete(result: String? = null, message: String = "无法生成音频指纹，请重新录音", cause: Throwable? = null) {
                    if (closed) return
                    close()
                    if (continuation.isActive) {
                        if (result != null && result.length in 4..128_000 && result.matches(Regex("[A-Za-z0-9+/=]+")))
                            continuation.resume(result)
                        else continuation.resumeWithException(IllegalStateException(message, cause))
                    }
                }
                continuation.invokeOnCancellation { main.post { close() } }
                if (!continuation.isActive) return@suspendCancellableCoroutine
                try {
                    val active = WebView(context)
                    web = active
                    active.settings.apply {
                        javaScriptEnabled = true
                        allowFileAccess = false; allowContentAccess = false
                        blockNetworkLoads = true; domStorageEnabled = false
                        mediaPlaybackRequiresUserGesture = true
                    }
                    active.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                            // Serve the document entirely in memory. Loading data with a synthetic
                            // base URL crashes some WebView/ART versions during cold background startup.
                            val document = request.isForMainFrame && request.url.toString() == DOCUMENT_URL
                            return WebResourceResponse(if (document) "text/html" else "text/plain", "UTF-8",
                                ByteArrayInputStream(if (document) html.toByteArray(Charsets.UTF_8) else ByteArray(0)))
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) complete(message = "声音处理页面加载失败，请重试")
                        }
                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            complete(message = "声音处理进程已退出，请重试")
                            return true
                        }
                    }
                    active.webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR)
                                complete(message = "声音处理运行失败，请重试")
                            return true // Do not forward script content, audio or fingerprints to logs.
                        }
                    }
                    active.addJavascriptInterface(FingerprintCallback { result -> main.post { complete(result) } }, "CurrentMusicFingerprint")
                    if (continuation.isActive)
                        active.loadUrl(DOCUMENT_URL)
                    else close()
                } catch (failure: Exception) {
                    complete(message = "无法启动声音处理，请检查系统 WebView 后重试", cause = failure)
                }
            }
        }
    }

    @Keep private class FingerprintCallback(private val complete: (String?) -> Unit) {
        @JavascriptInterface fun success(value: String) = complete(value)
        @JavascriptInterface fun failure() = complete(null)
    }

    private companion object { const val DOCUMENT_URL = "https://currentmusic.invalid/recognition/index.html" }
}
