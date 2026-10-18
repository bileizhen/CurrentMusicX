package io.github.currencortex.music.data.netease.listen

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Resolves official short redirects without an account, cookies, browser, or script execution. */
internal class ListenTogetherInvitationResolver(client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build(),
    private val shortOrigin: HttpUrl = "https://163cn.tv/".toHttpUrl()) {
    private val http = client.newBuilder().cookieJar(CookieJar.NO_COOKIES)
        .authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

    suspend fun resolve(text: String): ListenTogetherInvitation {
        ListenTogetherInvitation.parse(text)?.let { return it }
        var url = ListenTogetherInvitation.candidate(text).toHttpUrlOrNull()
            ?: error("请粘贴有效的网易云一起听邀请链接")
        repeat(5) {
            check(url.scheme == shortOrigin.scheme && url.host == shortOrigin.host && url.port == shortOrigin.port &&
                url.username.isEmpty() && url.password.isEmpty()) { "短链接跳转地址不受支持" }
            val request = Request.Builder().url(url).header("User-Agent", "CurrentMusicX/ListenTogether").build()
            val response = suspendCancellableCoroutine<Response> { continuation ->
                val call = http.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response, onCancellation = { _, value, _ -> value.close() })
                    }
                })
            }
            val target = response.use {
                check(it.code in setOf(301, 302, 303, 307, 308)) { "无法展开邀请短链接，请复制完整邀请链接" }
                url.resolve(it.header("Location") ?: error("邀请短链接缺少跳转地址"))
                    ?: error("邀请短链接跳转地址无效")
            }
            if (target.scheme == "https" && target.username.isEmpty() && target.password.isEmpty())
                ListenTogetherInvitation.parse(target.toString())?.let { return it }
            url = target
        }
        error("邀请短链接跳转次数过多")
    }
}
