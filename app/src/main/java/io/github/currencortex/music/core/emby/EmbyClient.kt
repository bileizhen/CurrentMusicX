package io.github.currencortex.music.core.emby

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.ScheduledThreadPoolExecutor
import okio.ForwardingSource
import okio.buffer

class EmbyAccess(val server: String, val username: String, val userId: String,
    internal val token: String, val deviceId: String, val revision: String = UUID.randomUUID().toString()) {
    val identity = MessageDigest.getInstance("SHA-256").digest("emby\n$server\n$userId".toByteArray())
        .joinToString("") { "%02x".format(it) }.take(32)
    override fun toString() = "EmbyAccess(identity=$identity, credentials=[redacted])"
}

/** Accept a server root or a reverse-proxy subpath, including an existing /emby suffix. */
fun normalizeEmbyServer(value: String): String {
    val url = value.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("服务器地址无效")
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "请填写服务器地址，不包含登录信息或网页参数" }
    val path = url.encodedPath.trimEnd('/').removeSuffix("/emby")
    return url.newBuilder().encodedPath("$path/emby/").build().toString()
}

class EmbyClient(private val access: () -> EmbyAccess?, private val wire: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS)
    .followRedirects(false).followSslRedirects(false).build()) {
    // A stream can remain open for the full song; API request deadlines must not cut off playback.
    private val mediaWire = wire.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).build()
    fun current() = access() ?: throw embyUnauthorized()
    fun ensure(expected: EmbyAccess) { if (access()?.revision != expected.revision) throw embyUnauthorized() }
    private fun url(account: EmbyAccess, path: List<String>, params: List<Pair<String, String>>): HttpUrl =
        account.server.toHttpUrlOrNull()!!.newBuilder().apply {
            path.forEach(::addPathSegment); params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
    private fun authenticate(request: Request, account: EmbyAccess) = request.newBuilder()
        .header("X-Emby-Authorization", "Emby Client=\"CurrentMusicX\", Device=\"Android\", DeviceId=\"${account.deviceId}\", Version=\"1.3.2\"")
        .apply { if (account.token.isNotEmpty()) header("X-Emby-Token", account.token) }.build()

    suspend fun request(path: List<String>, params: List<Pair<String, String>> = emptyList(),
        method: String = "GET", body: JsonObject? = null, expected: EmbyAccess = current(),
        provisional: Boolean = false): JsonObject = withContext(Dispatchers.IO) {
        if (!provisional) ensure(expected)
        val payload = if (method == "POST") (body?.toString() ?: "{}").toRequestBody("application/json".toMediaType()) else null
        val call = wire.newCall(authenticate(Request.Builder().url(url(expected, path, params)).method(method, payload).build(), expected))
        suspendCancellableCoroutine<JsonObject> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(IOException("Emby 连接失败，请检查网络与服务器")))
                }
                override fun onResponse(call: Call, response: Response) {
                    // Keep cancellation attached until the body has been read, not just until headers arrive.
                    val result = runCatching { response.use {
                        checkStatus(it)
                        val source = it.body?.source() ?: throw IOException("Emby 响应为空")
                        source.request(MAX_BYTES + 1)
                        if (source.buffer.size > MAX_BYTES) throw IOException("曲库响应过大，请分页读取")
                        val text = source.readUtf8()
                        val parsed = if (text.isBlank()) JsonObject(emptyMap()) else
                            runCatching { ApiJson.parseToJsonElement(text).jsonObject }.getOrNull()
                                ?: throw IOException("服务器未返回 Emby 接口，请检查地址")
                        if (!provisional) ensure(expected)
                        parsed
                    } }
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            })
        }
    }

    /** Stored media URLs contain identity only; authentication is added immediately before transport. */
    fun media(path: List<String>, params: List<Pair<String, String>> = emptyList(), expected: EmbyAccess = current()): String =
        url(expected, path, params + (MARKER to expected.identity)).toString()

    fun mediaInterceptor() = Interceptor { chain ->
        val safe = chain.request()
        val identity = safe.url.queryParameter(MARKER)
        if (identity == null) chain.proceed(safe) else {
            val account = current()
            val base = account.server.toHttpUrlOrNull()!!
            val path = safe.url.encodedPath.removePrefix(base.encodedPath)
            if (identity != account.identity || safe.url.scheme != base.scheme || safe.url.host != base.host ||
                safe.url.port != base.port || !safe.url.encodedPath.startsWith(base.encodedPath) ||
                !path.matches(Regex("(?:Audio/[A-Za-z0-9_-]+/stream|Items/[A-Za-z0-9_-]+/(?:Images/Primary|Download))")) ||
                safe.url.queryParameterNames.any { it !in setOf(MARKER, "Static", "MediaSourceId", "MaxWidth", "Tag") })
                throw IOException("Emby 账号或媒体地址已变化")
            var request = authenticate(safe.newBuilder().url(safe.url.newBuilder().removeAllQueryParameters(MARKER).build()).build(), account)
            var response: Response? = null
            // Remote-backed Emby libraries commonly redirect. Never forward account headers to the destination.
            for (hop in 0..5) {
                ensure(account)
                val next = try { executeMedia(request, chain.call()) }
                catch (_: IOException) { throw IOException("Emby 媒体读取失败，请检查网络或重试其他歌曲") }
                if (next.code !in 300..399) { response = next; break }
                val target = next.header("Location")?.let(next.request.url::resolve)
                next.close()
                if (hop == 5 || target == null || target.username.isNotEmpty() || target.password.isNotEmpty() ||
                    (target.scheme != "https" && !(base.scheme == "http" && target.host == base.host && target.port == base.port)))
                    throw IOException("Emby 媒体重定向无效")
                request = Request.Builder().url(target).apply {
                    safe.header("Range")?.let { header("Range", it) }
                }.build()
            }
            val result = response ?: throw IOException("Emby 媒体不可用")
            try { ensure(account); checkStatus(result) } catch (e: Exception) { result.close(); throw e }
            result.newBuilder().request(safe).build()
        }
    }
    private fun executeMedia(request: Request, outer: Call): Response {
        if (outer.isCanceled()) throw IOException("Canceled")
        val call = mediaWire.newCall(request)
        // Interceptor calls have no cancellation callback. A shared lightweight watcher bridges
        // the outer call until this hop's response body is consumed or closed, including redirects.
        val cancellation = mediaCancellation.scheduleAtFixedRate({
            if (outer.isCanceled()) call.cancel()
        }, 0, 50, TimeUnit.MILLISECONDS)
        try {
            val response = call.execute()
            val body = response.body ?: run { cancellation.cancel(false); return response }
            val source = object : ForwardingSource(body.source()) {
                override fun read(sink: okio.Buffer, byteCount: Long): Long = try {
                    super.read(sink, byteCount).also { if (it == -1L) cancellation.cancel(false) }
                } catch (e: Exception) { cancellation.cancel(false); throw e }
                override fun close() { try { super.close() } finally { cancellation.cancel(false) } }
            }.buffer()
            return response.newBuilder().body(object : ResponseBody() {
                override fun contentType() = body.contentType()
                override fun contentLength() = body.contentLength()
                override fun source() = source
            }).build()
        } catch (e: Exception) { cancellation.cancel(false); call.cancel(); throw e }
    }
    private fun checkStatus(response: Response) {
        if (response.code == 401) throw embyUnauthorized()
        if (response.code == 403) throw ApiException(ErrorKind.Forbidden, 403, "Emby 账号没有此操作权限")
        if (!response.isSuccessful) throw ApiException(ErrorKind.Server, response.code, "Emby 请求失败（${response.code}）")
    }
    companion object {
        const val MARKER = "_cm_emby"
        private const val MAX_BYTES = 4L * 1024 * 1024
        private val mediaCancellation = ScheduledThreadPoolExecutor(1) { task ->
            Thread(task, "Emby media cancellation").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }
    }
}

internal fun embyUnauthorized() = ApiException(ErrorKind.Unauthorized, 401, "Emby 登录已失效，请重新连接")
internal fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.longOrNull ?: 0L
internal fun JsonObject.flag(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull ?: false
internal fun JsonObject.obj(key: String) = get(key) as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonObject.objects(key: String) = (get(key) as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
