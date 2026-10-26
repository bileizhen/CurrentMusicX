package io.github.currencortex.music.core.netease

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class NeteaseEndpoints(val web: String = "https://music.163.com/", val desktop: String = "https://interfacepc.music.163.com/",
    val clientLog: String = "https://clientlog.music.163.com/")
internal enum class NeteaseEncoding { WEB, DESKTOP, LOG }
internal enum class NeteaseClientProfile { DESKTOP, ANDROID }
internal class NeteaseResponse(val body: JsonObject, val cookies: Map<String, String>) {
    override fun toString() = "NeteaseResponse([redacted])"
}

/** Separate HTTP client: CurrentMusic Authorization headers can never reach NetEase. */
class NeteaseTransport(val sessions: NeteaseSessionStore, private val endpoints: NeteaseEndpoints = NeteaseEndpoints(),
    private val log: (String) -> Unit = {}, client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(18, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false).retryOnConnectionFailure(false).build()) {
    private val http = client.newBuilder().followRedirects(false).retryOnConnectionFailure(false).build()
    private class BufferedResponse(val code: Int, val headers: Headers, val bytes: ByteArray)
    internal suspend fun request(path: String, data: JsonObject = buildJsonObject {},
        encoding: NeteaseEncoding = NeteaseEncoding.DESKTOP, session: NeteaseSession? = null,
        cookieOverride: Map<String, String>? = null, saveCookies: Boolean = true,
        clientProfile: NeteaseClientProfile = NeteaseClientProfile.DESKTOP): NeteaseResponse = withContext(Dispatchers.IO) {
        val expected = session ?: sessions.snapshot()
        val cookies = cookieOverride ?: expected.credential.cookies
        require(path.startsWith("/api/") && '?' !in path && '#' !in path)
        sessions.check(expected)
        val platform = if (clientProfile == NeteaseClientProfile.ANDROID)
            mapOf("os" to "android", "appver" to "9.5.30") else
            mapOf("os" to "pc", "appver" to "3.1.17.204416", "osver" to "Microsoft-Windows-10-Professional-build-19045-64bit")
        val common = platform + mapOf(
            "deviceId" to expected.credential.deviceId, "channel" to "netease", "__remember_me" to "true") + cookies +
            (if (encoding == NeteaseEncoding.LOG) mapOf("os" to "osx") else emptyMap())
        val requestCommon = if (clientProfile == NeteaseClientProfile.ANDROID) common + platform else common
        val header = buildJsonObject {
            requestCommon.forEach { (key, value) -> put(key, value) }
            put("__csrf", cookies["__csrf"].orEmpty())
            put("requestId", "${System.currentTimeMillis()}_${java.util.concurrent.ThreadLocalRandom.current().nextInt(1000, 10000)}")
        }
        val payload = buildJsonObject {
            data.forEach { (key, value) -> put(key, value) }
            put("e_r", false)
            if (encoding == NeteaseEncoding.WEB) put("csrf_token", cookies["__csrf"].orEmpty()) else put("header", header)
        }
        val encoded = if (encoding == NeteaseEncoding.WEB) NeteaseCrypto.weapi(payload.toString()) else mapOf("params" to NeteaseCrypto.eapi(path, payload.toString()))
        val body = FormBody.Builder().apply { encoded.forEach { (key, value) -> add(key, value) } }.build()
        val prefix = if (encoding == NeteaseEncoding.WEB) "weapi/" else "eapi/"
        val base = when (encoding) { NeteaseEncoding.WEB -> endpoints.web; NeteaseEncoding.DESKTOP -> endpoints.desktop; NeteaseEncoding.LOG -> endpoints.clientLog }
        val url = base.toHttpUrl().resolve(prefix + path.removePrefix("/api/")) ?: throw ApiException(ErrorKind.Parse)
        val request = Request.Builder().url(url).post(body).header("Accept", "application/json")
            .header("Referer", "https://music.163.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36")
            .header("Cookie", requestCommon.entries.joinToString("; ") { "${it.key}=${it.value}" }).build()
        val response = suspendCancellableCoroutine<BufferedResponse> { cont ->
            val call = http.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        // Stay suspended until the body is consumed: cancellation can then
                        // interrupt a stalled read, including after response headers arrived.
                        val buffered = response.use {
                            val bytes = if (!it.isSuccessful) ByteArray(0) else {
                                val body = it.body ?: throw ApiException(ErrorKind.Parse)
                                if (body.contentLength() > MAX_JSON_BYTES) throw oversized()
                                val source = body.source()
                                source.request(MAX_JSON_BYTES.toLong() + 1)
                                if (source.buffer.size > MAX_JSON_BYTES) throw oversized()
                                source.readByteArray()
                            }
                            BufferedResponse(it.code, it.headers, bytes)
                        }
                        if (cont.isActive) cont.resume(buffered)
                    } catch (failure: Exception) {
                        if (cont.isActive) cont.resumeWithException(failure)
                    }
                }
            })
        }
        currentCoroutineContext().ensureActive()
        run {
            log("NetEase $path HTTP ${response.code}")
            sessions.check(expected)
            if (response.code !in 200..299) throw ApiException(when (response.code) {
                401 -> ErrorKind.NeteaseBindingRequired; 403 -> ErrorKind.Forbidden; 404 -> ErrorKind.NotFound
                429 -> ErrorKind.RateLimited; in 500..599 -> ErrorKind.Server; else -> ErrorKind.Unknown
            }, response.code)
            val bytes = response.bytes
            val raw = try {
                val decoded = if (bytes.firstOrNull { value -> value.toInt() !in setOf(9, 10, 13, 32) }?.toInt() == '{'.code) bytes
                    else NeteaseCrypto.decryptResponse(bytes)
                val plain = if (decoded.size > 2 && decoded[0] == 0x1f.toByte() && decoded[1] == 0x8b.toByte())
                    GZIPInputStream(ByteArrayInputStream(decoded)).use { stream -> limited(stream) } else decoded
                if (plain.size > MAX_JSON_BYTES) throw oversized()
                ApiJson.parseToJsonElement(plain.toString(Charsets.UTF_8)) as? JsonObject ?: throw ApiException(ErrorKind.Parse)
            } catch (failure: ApiException) { throw failure
            } catch (_: Exception) { throw ApiException(ErrorKind.Parse) }
            val changed = response.headers.values("Set-Cookie").mapNotNull { value -> Cookie.parse(url, value) }
                .filter { cookie -> cookie.expiresAt > System.currentTimeMillis() }.associate { cookie -> cookie.name to cookie.value }
            currentCoroutineContext().ensureActive()
            sessions.check(expected)
            if (saveCookies) sessions.updateCookies(changed, expected)
            NeteaseResponse(raw, cookies + changed)
        }
    }

    private fun limited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) return output.toByteArray()
            if (output.size().toLong() + size > MAX_JSON_BYTES) throw oversized()
            output.write(buffer, 0, size)
        }
    }
    companion object {
        // Shared native routes include large playlists; lyric adapters enforce their tighter caps.
        internal const val MAX_JSON_BYTES = 16 * 1024 * 1024
        private fun oversized() = ApiException(ErrorKind.Parse, userMessage = "网易云响应超过 16 MB 限制")
    }
}
