package io.github.currencortex.music.core.links

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** An anonymous client: short links never receive application cookies or authorization headers. */
class MusicWebLinkResolver(client: OkHttpClient = OkHttpClient()) {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .cookieJar(okhttp3.CookieJar.NO_COOKIES).authenticator(okhttp3.Authenticator.NONE)
        .proxyAuthenticator(okhttp3.Authenticator.NONE).retryOnConnectionFailure(false)
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS).build()

    suspend fun resolve(raw: String): MusicWebLink? {
        var target = MusicWebLink.parse(raw)
        repeat(5) {
            val short = target as? MusicWebLink.ShortLink ?: return target
            val response = suspendCancellableCoroutine<Response> { continuation ->
                val call = http.newCall(Request.Builder().url(short.url).get().build())
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (!continuation.isCancelled) continuation.resumeWithException(e)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response, onCancellation = { _, value, _ -> value.close() })
                    }
                })
            }
            val next = response.use {
                if (response.code !in setOf(301, 302, 303, 307, 308)) return null
                response.header("Location")?.let(short.url::resolve)?.toString()
            } ?: return null
            // Validate every hop before issuing another request. Never fetch an arbitrary redirect.
            target = MusicWebLink.parse(next)
        }
        return target.takeUnless { it is MusicWebLink.ShortLink }
    }
}
