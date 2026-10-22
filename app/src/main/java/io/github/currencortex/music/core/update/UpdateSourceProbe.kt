package io.github.currencortex.music.core.update

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

enum class UpdateSourceStatus { CHECKING, AVAILABLE, LIMITED, TIMEOUT, UNAVAILABLE, UNKNOWN }

data class UpdateSourceHealth(
    val status: UpdateSourceStatus,
    val latencyMs: Long? = null,
    val detail: String = "",
)

fun interface UpdateSourceCheck {
    suspend fun check(release: AppRelease, source: UpdateSource): UpdateSourceHealth
}

/** Checks the actual APK response, reading only its four-byte ZIP signature. No full-file verification. */
class UpdateSourceProbe(
    client: OkHttpClient = OkHttpClient(),
    private val urlFor: (AppRelease, UpdateSource) -> String = { release, source -> source.url(release) },
    private val nanoTime: () -> Long = System::nanoTime,
) : UpdateSourceCheck {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    private val privateMirrorHttp = http.newBuilder().retryOnConnectionFailure(true)
        .dns(object : okhttp3.Dns {
            override fun lookup(hostname: String) = http.dns.lookup(hostname).let { addresses ->
                if (hostname == "music.20110208.xyz") addresses.sortedBy { if (it is java.net.Inet4Address) 0 else 1 }
                else addresses
            }
        }).build()

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun check(release: AppRelease, source: UpdateSource): UpdateSourceHealth = withContext(Dispatchers.IO) {
        val started = nanoTime()
        fun result(status: UpdateSourceStatus, detail: String = "") = UpdateSourceHealth(
            status, TimeUnit.NANOSECONDS.toMillis(nanoTime() - started).coerceAtLeast(1), detail)
        try {
            var url = urlFor(release, source).toHttpUrl()
            repeat(9) { redirect ->
                check(url.scheme == "https" && url.username.isEmpty() && url.password.isEmpty()) { "下载源必须使用 HTTPS" }
                val remaining = TimeUnit.SECONDS.toNanos(8) - (nanoTime() - started)
                if (remaining <= 0) return@withContext result(UpdateSourceStatus.TIMEOUT, "检测超时")
                val call = (if (source == UpdateSource.CURRENTMUSIC) privateMirrorHttp else http)
                    .newCall(Request.Builder().url(url).header("Range", "bytes=0-3")
                    .header("Accept-Encoding", "identity")
                    .header("Accept", "application/vnd.android.package-archive, application/octet-stream")
                    .header("User-Agent", "CurrentMusic updater").build())
                call.timeout().timeout(remaining, TimeUnit.NANOSECONDS)
                val cancel = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) {
                    if (it != null) call.cancel()
                }
                try {
                    call.execute().use { response ->
                        if (response.code in listOf(301, 302, 303, 307, 308)) {
                            check(redirect < 8) { "重定向次数过多" }
                            url = response.header("Location")?.let(url::resolve) ?: error("重定向地址无效")
                        } else {
                            if (response.code == 429) return@withContext result(UpdateSourceStatus.LIMITED, "HTTP 429")
                            if (!response.isSuccessful) return@withContext result(UpdateSourceStatus.UNAVAILABLE, "HTTP ${response.code}")
                            val body = response.body ?: return@withContext result(UpdateSourceStatus.UNAVAILABLE, "响应为空")
                            val type = body.contentType()?.let { "${it.type}/${it.subtype}" }
                            check(type !in setOf("text/html", "application/xhtml+xml", "application/json")) { "返回了网页或错误信息" }
                            if (response.code == 206) {
                                val range = Regex("bytes 0-([0-9]+)/([0-9]+)").matchEntire(response.header("Content-Range").orEmpty())
                                check(range != null && range.groupValues[1].toLong() >= 3 &&
                                    (release.size <= 0 || range.groupValues[2].toLong() == release.size)) { "安装包范围或大小不匹配" }
                            } else {
                                check(release.size <= 0 || body.contentLength() <= 0 || body.contentLength() == release.size) { "安装包大小不匹配" }
                            }
                            val signature = ByteArray(4)
                            val input = body.byteStream()
                            var received = 0
                            while (received < signature.size) {
                                val count = input.read(signature, received, signature.size - received)
                                if (count < 0) break
                                received += count
                            }
                            check(received == 4 && signature.contentEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04))) { "未返回 APK 文件" }
                            return@withContext result(UpdateSourceStatus.AVAILABLE, "APK 文件头可读取；完整下载后仍需校验 SHA-256")
                        }
                    }
                } finally { cancel.dispose() }
            }
            result(UpdateSourceStatus.UNAVAILABLE, "重定向次数过多")
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            result(if (failure is InterruptedIOException) UpdateSourceStatus.TIMEOUT else UpdateSourceStatus.UNAVAILABLE,
                if (failure is IOException) "连接失败：${failure.message.orEmpty()}" else failure.message.orEmpty())
        }
    }
}
