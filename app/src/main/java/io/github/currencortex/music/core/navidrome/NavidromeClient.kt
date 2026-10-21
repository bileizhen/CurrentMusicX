package io.github.currencortex.music.core.navidrome

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/** Credentials stay in the encrypted account store, never in songs, URLs or diagnostics. */
class NavidromeAccess(val server: String, val username: String, internal val password: String,
    val revision: String = java.util.UUID.randomUUID().toString(), val home: Boolean = false) {
    val identity = digest("SHA-256", "$server\n$username").take(32)
    override fun toString() = "NavidromeAccess(identity=$identity, credentials=[redacted])"
}

internal fun digest(algorithm: String, value: String) = MessageDigest.getInstance(algorithm)
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

fun normalizeNavidromeServer(value: String): String {
    val url = value.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("服务器地址无效")
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null)
    var path = url.encodedPath.trimEnd('/')
    if (path.endsWith("/rest")) path = path.removeSuffix("/rest")
    return url.newBuilder().encodedPath("$path/").build().toString()
}

class NavidromeClient(private val access: () -> NavidromeAccess?) {
    private val wire = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    private val random = SecureRandom()
    fun current() = access() ?: throw navidromeUnauthorized()
    fun ensure(expected: NavidromeAccess) {
        if (access()?.revision != expected.revision) throw navidromeUnauthorized()
    }
    private fun url(account: NavidromeAccess, endpoint: String, params: List<Pair<String, String>>, signed: Boolean): HttpUrl {
        require(endpoint.matches(Regex("[A-Za-z0-9]+")))
        val builder = account.server.toHttpUrlOrNull()!!.newBuilder().addPathSegment("rest").addPathSegment("$endpoint.view")
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        if (signed) {
            val salt = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
            builder.addQueryParameter("u", account.username).addQueryParameter("s", salt)
                .addQueryParameter("t", digest("MD5", account.password + salt))
                .addQueryParameter("v", "1.16.1").addQueryParameter("c", "CurrentMusicX").addQueryParameter("f", "json")
        } else builder.addQueryParameter(MARKER, account.identity)
        return builder.build()
    }
    fun media(endpoint: String, params: List<Pair<String, String>>, expected: NavidromeAccess = current()): String {
        require(endpoint in MEDIA_ENDPOINTS)
        return url(expected, endpoint, params, false).toString()
    }
    /** Sign only our marked, exact-origin media routes. Return the safe request to callers/cache. */
    fun mediaInterceptor() = Interceptor { chain ->
        val safe = chain.request()
        val identity = safe.url.queryParameter(MARKER)
        if (identity == null) chain.proceed(safe) else {
            val account = current()
            val endpoint = safe.url.pathSegments.last().removeSuffix(".view")
            val base = account.server.toHttpUrlOrNull()!!
            val expectedPath = base.newBuilder().addPathSegment("rest").addPathSegment("$endpoint.view").build().encodedPath
            if (identity != account.identity || safe.url.scheme != base.scheme || safe.url.host != base.host ||
                safe.url.port != base.port || safe.url.encodedPath != expectedPath || endpoint !in MEDIA_ENDPOINTS)
                throw IOException("Navidrome 账号或服务器已变化")
            if (safe.url.queryParameterNames.any { it !in setOf(MARKER, "id", "size", "format", "maxBitRate", "timeOffset") })
                throw IOException("Navidrome 媒体参数无效")
            val params = safe.url.queryParameterNames.filter { it != MARKER }.flatMap { key ->
                safe.url.queryParameterValues(key).filterNotNull().map { key to it }
            }
            // The dedicated client never forwards authentication through redirects.
            val response = wire.newCall(safe.newBuilder().url(url(account, endpoint, params, true)).build()).execute()
            if (response.code in 300..399) { response.close(); throw IOException("Navidrome 媒体地址重定向，请检查服务器地址") }
            try { ensure(account) } catch (e: Exception) { response.close(); throw e }
            response.newBuilder().request(safe).build()
        }
    }
    suspend fun request(endpoint: String, params: List<Pair<String, String>> = emptyList(),
        expected: NavidromeAccess = current(), provisional: Boolean = false): JsonObject = withContext(Dispatchers.IO) {
        val call = wire.newCall(Request.Builder().url(url(expected, endpoint, params, true)).build())
        val response = suspendCancellableCoroutine<Response> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(IOException("Navidrome 连接失败，请检查网络与服务器")))
                }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        }
        response.use {
            if (it.code in setOf(401, 403)) throw navidromeUnauthorized(it.code)
            if (!it.isSuccessful) throw ApiException(ErrorKind.Server, it.code)
            val body = it.body ?: throw IOException("Navidrome 响应为空")
            val source = body.source(); source.request(MAX_BYTES + 1)
            if (source.buffer.size > MAX_BYTES) throw IOException("曲库响应过大，请分页读取")
            val root = runCatching { ApiJson.parseToJsonElement(source.readUtf8()).jsonObject["subsonic-response"]?.jsonObject }
                .getOrNull() ?: throw IOException("服务器未返回 Subsonic 接口，请检查地址或反向代理")
            if (root.string("status") != "ok") {
                val code = root["error"]?.jsonObject?.number("code")?.toInt() ?: 0
                if (code in setOf(40, 41)) throw navidromeUnauthorized(code)
                throw ApiException(when (code) { 50 -> ErrorKind.Forbidden; 70 -> ErrorKind.NotFound; else -> ErrorKind.Server }, code)
            }
            if (!provisional) ensure(expected)
            root
        }
    }
    companion object {
        const val MARKER = "_cm_navidrome"
        private const val MAX_BYTES = 8L * 1024 * 1024
        private val MEDIA_ENDPOINTS = setOf("stream", "download", "getCoverArt")
    }
}

internal fun navidromeUnauthorized(status: Int = 0) =
    ApiException(ErrorKind.Unauthorized, status, "Navidrome 登录已失效，请重新连接")

internal fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.longOrNull ?: 0L
internal fun JsonObject.bool(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull ?: false
internal fun JsonObject.objects(key: String): List<JsonObject> = when (val value = get(key)) {
    is JsonArray -> value.filterIsInstance<JsonObject>(); is JsonObject -> listOf(value); else -> emptyList()
}
