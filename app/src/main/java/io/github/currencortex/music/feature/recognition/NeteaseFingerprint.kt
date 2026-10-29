package io.github.currencortex.music.feature.recognition

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
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
        require(samples.size in 24_000..80_000)
        val pcm = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, sample -> pcm[i * 2] = sample.toByte(); pcm[i * 2 + 1] = (sample.toInt() shr 8).toByte() }
        val encoded = Base64.getEncoder().encodeToString(pcm)
        pcm.fill(0)
        val html = withContext(Dispatchers.IO) {
            val runtime = context.assets.open("recognition/afp.js").bufferedReader().use { it.readText() }
            val wasm = context.assets.open("recognition/afp.wasm.js").bufferedReader().use { it.readText() }
            """<!doctype html><meta charset="utf-8"><script>$wasm</script><script>$runtime</script>
                <script>
                async function identifyPcm() {
                  try {
                    const raw = atob('$encoded');
                    const floats = new Float32Array(raw.length / 2);
                    for (let i = 0; i < floats.length; i++) {
                      const n = raw.charCodeAt(i*2) | (raw.charCodeAt(i*2+1) << 8);
                      floats[i] = (n >= 32768 ? n - 65536 : n) / 32768;
                    }
                    const result = await GenerateFP(floats);
                    CurrentMusicFingerprint.success(result);
                  } catch (e) { CurrentMusicFingerprint.failure(); }
                }
                identifyPcm();
                </script>""".trimIndent()
        }
        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val web = WebView(context)
                val main = Handler(Looper.getMainLooper())
                var closed = false
                fun close() {
                    if (!closed) {
                        closed = true; web.stopLoading()
                        web.removeJavascriptInterface("CurrentMusicFingerprint"); web.destroy()
                    }
                }
                web.settings.apply {
                    javaScriptEnabled = true
                    allowFileAccess = false; allowContentAccess = false
                    blockNetworkLoads = true; domStorageEnabled = false
                    mediaPlaybackRequiresUserGesture = true
                }
                web.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                        WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }
                web.addJavascriptInterface(FingerprintCallback { result -> main.post {
                    if (continuation.isActive) {
                        if (result != null && result.length in 4..128_000 && result.matches(Regex("[A-Za-z0-9+/=]+")))
                            continuation.resume(result)
                        else continuation.resumeWithException(IllegalStateException("无法生成音频指纹，请重新录音"))
                    }
                    close()
                } }, "CurrentMusicFingerprint")
                continuation.invokeOnCancellation { main.post { close() } }
                web.loadDataWithBaseURL("https://currentmusic.invalid/recognition/", html, "text/html", "UTF-8", null)
            }
        }
    }

    @Keep private class FingerprintCallback(private val complete: (String?) -> Unit) {
        @JavascriptInterface fun success(value: String) = complete(value)
        @JavascriptInterface fun failure() = complete(null)
    }
}
