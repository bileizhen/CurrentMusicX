package io.github.currencortex.music.data.lx

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A script never receives CurrentMusic tokens, NetEase cookies, or the app's authenticated client. */
class LxHttpClient(private val allowPrivateForTests: Boolean = false) {
    private val client = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
                if (!allowPrivateForTests && addresses.any(::isPrivate)) throw IOException("音源不能访问本机或局域网地址")
            }
        }).build()
    internal fun url(value: String): HttpUrl {
        val url = value.toHttpUrlOrNull() ?: throw LxSourceException("音源地址必须是 HTTP 或 HTTPS 链接")
        if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.toString().length > 8192)
            throw LxSourceException("音源地址无效")
        if (!allowPrivateForTests && (url.host.equals("localhost", true) || url.host.endsWith(".localhost", true) ||
                url.host.endsWith(".local", true) || isNumericPrivate(url.host))) throw LxSourceException("音源不能访问本机或局域网地址")
        return url
    }
    suspend fun download(value: String): String {
        val response = exchange(Request.Builder().url(url(value)).header("Accept", "text/javascript,text/plain,*/*").build(), LX_MAX_SCRIPT_BYTES)
        if (response.code !in 200..299) throw LxSourceException("下载音源失败（HTTP ${response.code}）")
        return response.text.removePrefix("\uFEFF")
    }
    internal suspend fun probeAudio(value: String): io.github.currencortex.music.core.network.SourceAvailabilityStatus {
        val result = exchange(Request.Builder().url(url(value)).header("Range", "bytes=0-4095").build(), 4096, 12000, prefixOnly = true)
        return when (result.code) {
            429 -> io.github.currencortex.music.core.network.SourceAvailabilityStatus.LIMITED
            408, 504 -> io.github.currencortex.music.core.network.SourceAvailabilityStatus.TIMEOUT
            200, 206 -> if (lxAudioPrefix(result.bytes)) io.github.currencortex.music.core.network.SourceAvailabilityStatus.AVAILABLE
                else io.github.currencortex.music.core.network.SourceAvailabilityStatus.UNAVAILABLE
            else -> io.github.currencortex.music.core.network.SourceAvailabilityStatus.UNAVAILABLE
        }
    }
    internal suspend fun execute(input: JsonObject): JsonObject {
        val options = input["options"] as? JsonObject ?: buildJsonObject {}
        val builder = Request.Builder().url(url(input["url"]!!.jsonPrimitive.content))
        val headers = options["headers"] as? JsonObject
        require((headers?.size ?: 0) <= 64) { "音源请求头过多" }
        headers?.forEach { (key, value) -> builder.header(key, value.jsonPrimitive.content) }
        builder.header("User-Agent", headers?.entries?.firstOrNull { it.key.equals("User-Agent", true) }?.value?.jsonPrimitive?.content
            ?: "CurrentMusic-LX/2.0.0")
        val method = options["method"]?.jsonPrimitive?.contentOrNull?.uppercase(java.util.Locale.ROOT) ?: "GET"
        require(method in setOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")) { "不支持的音源请求方法" }
        val raw = options["body"]
        val form = options["form"] as? JsonObject
        val formData = options["formData"] as? JsonObject
        val body = when {
            raw != null && raw !is JsonNull -> {
                val text = if (raw is JsonPrimitive && raw.isString) raw.content else raw.toString()
                require(text.toByteArray().size <= LX_MAX_RESPONSE_BYTES)
                text.toRequestBody(headers?.entries?.firstOrNull { it.key.equals("Content-Type", true) }?.value?.jsonPrimitive?.content?.toMediaTypeOrNull()
                    ?: if (raw is JsonObject || raw is JsonArray) "application/json; charset=utf-8".toMediaTypeOrNull() else null)
            }
            form != null -> FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v.jsonPrimitive.content) } }.build()
            formData != null -> MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                formData.forEach { (k, v) -> addFormDataPart(k, v.jsonPrimitive.content) }
            }.build()
            method in setOf("POST", "PUT", "PATCH") -> ByteArray(0).toRequestBody()
            else -> null
        }
        require(body == null || method !in setOf("GET", "HEAD")) { "GET 请求不能携带请求体" }
        builder.method(method, body)
        val requestedTimeout = options["timeout"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }?.coerceIn(100, 20000) ?: 20000
        val response = exchange(builder.build(), LX_MAX_RESPONSE_BYTES, requestedTimeout)
        val parsed = runCatching { Json.parseToJsonElement(response.text) }.getOrElse { JsonPrimitive(response.text) }
        return buildJsonObject {
            put("statusCode", response.code); put("statusMessage", response.message)
            put("headers", buildJsonObject { response.headers.names().forEach { name -> put(name, response.headers[name].orEmpty()) } })
            put("body", parsed); put("raw", java.util.Base64.getEncoder().encodeToString(response.bytes)); put("bytes", response.bytes.size)
        }
    }
    private data class Result(val code: Int, val message: String, val headers: Headers, val bytes: ByteArray) {
        val text get() = bytes.toString(Charsets.UTF_8)
    }
    private suspend fun exchange(initial: Request, limit: Int, timeout: Long = 20000, prefixOnly: Boolean = false): Result {
        var request = initial
        repeat(6) { hop ->
            val response = suspendCancellableCoroutine<Result> { continuation ->
                val call = client.newBuilder().callTimeout(timeout, TimeUnit.MILLISECONDS).build().newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val result = response.use {
                                val redirect = response.code in setOf(301, 302, 303, 307, 308) && response.header("Location") != null
                                val body = response.body
                                val bytes = if (redirect || body == null) byteArrayOf() else {
                                    if (!prefixOnly && body.contentLength() > limit) throw LxSourceException("音源响应过大")
                                    body.byteStream().use { stream ->
                                        if (!prefixOnly) lxReadBounded(stream, limit) else {
                                            val buffer = ByteArray(limit)
                                            var count = 0
                                            while (count < limit) {
                                                val read = stream.read(buffer, count, limit - count)
                                                if (read < 0) break
                                                count += read
                                            }
                                            buffer.copyOf(count)
                                        }
                                    }
                                }
                                Result(response.code, response.message, response.headers, bytes)
                            }
                            continuation.resume(result)
                        } catch (failure: Exception) {
                            if (!continuation.isCancelled) continuation.resumeWithException(failure)
                        }
                    }
                })
            }
            run {
                val target = response.headers["Location"]
                if (response.code in setOf(301, 302, 303, 307, 308) && target != null) {
                    if (hop == 5) throw LxSourceException("音源重定向次数过多")
                    val next = url(request.url.resolve(target)?.toString() ?: throw LxSourceException("音源重定向地址无效"))
                    val builder = request.newBuilder().url(next)
                    if (next.host != request.url.host || next.scheme != request.url.scheme || next.port != request.url.port) {
                        builder.removeHeader("Authorization"); builder.removeHeader("Cookie"); builder.removeHeader("Proxy-Authorization")
                    }
                    if (response.code == 303 || (response.code in setOf(301, 302) && request.method == "POST")) builder.method("GET", null)
                    request = builder.build()
                } else {
                    return response
                }
            }
        }
        throw LxSourceException("音源请求失败")
    }
    private fun isNumericPrivate(host: String): Boolean {
        if (!host.contains(':') && !host.all { it.isDigit() || it == '.' }) return false
        return runCatching { isPrivate(InetAddress.getByName(host)) }.getOrDefault(true)
    }
    private fun isPrivate(address: InetAddress): Boolean = address.isAnyLocalAddress || address.isLoopbackAddress ||
        address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress ||
        (address.address.size == 16 && (address.address[0].toInt() and 0xfe) == 0xfc) ||
        (address.address.size == 4 && ((address.address[0].toInt() and 255) == 0 ||
            (address.address[0].toInt() and 255) == 100 && (address.address[1].toInt() and 255) in 64..127))
}

internal fun lxReadBounded(stream: java.io.InputStream, limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val size = stream.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
        if (size < 0) return output.toByteArray()
        output.write(buffer, 0, size)
        if (output.size() > limit) throw LxSourceException("文件或响应超过大小限制")
    }
}
