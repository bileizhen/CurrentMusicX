package io.github.currencortex.music.data.kugou

import io.github.currencortex.music.core.network.ApiClient
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.data.kugou.KugouParsing.text
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin HTTP layer over the KuGou gateways. It owns request signing (both GET and body-signed POST JSON)
 * and the headers the Android client sends (`x-router` selects the backend behind `gateway.kugou.com`);
 * response interpretation stays in the repository.
 */
class KugouClient(
    baseClient: OkHttpClient = ApiClient.defaultHttpClient(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: ((String) -> Unit)? = null,
) {
    private val http = baseClient.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun nowMillis(): Long = clock()
    fun nowSeconds(): Long = clock() / 1000
    fun logEvent(message: String) { log?.invoke(message) }

    /**
     * @param device when set the request is signed; [credentials] adds `userid`/`token` (anonymous calls send userid=0).
     * @param check reject payloads whose `status` is neither 1 nor 200 (play-URL answers use other codes legitimately).
     */
    suspend fun call(
        base: String, path: String, params: Map<String, String> = emptyMap(), device: KugouDevice? = null,
        credentials: KugouCredentials? = null, router: String? = null, key: String = KugouProtocol.SIGN_KEY,
        appId: String = KugouProtocol.APP_ID, check: Boolean = true, headers: Map<String, String> = emptyMap(),
        clientTimeSeconds: Long? = null, includeAuth: Boolean = true,
    ): JsonObject {
        val ts = clientTimeSeconds ?: nowSeconds()
        val query = buildQuery(params, device, credentials, ts, key, appId, body = "", includeAuth = includeAuth)
        val url = (base + path).toHttpUrl().newBuilder().apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }.build()
        val request = baseRequest(url.toString(), router, headers).get().build()
        return parseResponse("GET $path", execute(request), check)
    }

    /**
     * Signed JSON POST used by KMR (`kmr.service.kugou.com`), JSON `cloudlist.service`, and
     * `login.user.kugou.com` endpoints. The raw JSON body is included directly in the Android `Utils.s` signature.
     */
    suspend fun postJson(
        base: String, path: String, body: String, params: Map<String, String> = emptyMap(),
        device: KugouDevice, credentials: KugouCredentials? = null, router: String? = null,
        key: String = KugouProtocol.SIGN_KEY, appId: String = KugouProtocol.APP_ID,
        check: Boolean = true, headers: Map<String, String> = emptyMap(), clientTimeSeconds: Long? = null,
        includeAuth: Boolean = true, maxSignatureBodyBytes: Int = Int.MAX_VALUE,
    ): JsonObject {
        val ts = clientTimeSeconds ?: nowSeconds()
        val query = buildQuery(params, device, credentials, ts, key, appId, body = body,
            includeAuth = includeAuth, maxBodyBytes = maxSignatureBodyBytes)
        val url = (base + path).toHttpUrl().newBuilder().apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }.build()
        val request = baseRequest(url.toString(), router, headers)
            .post(body.toRequestBody(jsonMediaType))
            .build()
        return parseResponse("POST $path", execute(request), check)
    }

    private fun buildQuery(
        params: Map<String, String>, device: KugouDevice?, credentials: KugouCredentials?,
        clientTimeSeconds: Long, key: String, appId: String, body: String, includeAuth: Boolean,
        maxBodyBytes: Int = Int.MAX_VALUE,
    ): Map<String, String> {
        if (device == null) return params
        val auth = if (!includeAuth) emptyMap()
        else if (credentials != null) mapOf("userid" to credentials.userId.toString(), "token" to credentials.token)
        else mapOf("userid" to "0")
        return KugouProtocol.signed(auth + params, device.mid, device.dfid, clientTimeSeconds, key, appId, body, maxBodyBytes)
    }

    private fun baseRequest(url: String, router: String?, headers: Map<String, String>): Request.Builder =
        Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", KugouProtocol.USER_AGENT).header("KG-RC", "1").header("KG-THash", "13a3164")
            .header("KG-Fake", "0").apply {
                router?.let { header("x-router", it) }
                headers.forEach { (name, value) -> header(name, value) }
            }

    private fun parseResponse(label: String, body: String, check: Boolean): JsonObject {
        val root = try { json.parseToJsonElement(body) as? JsonObject } catch (_: Exception) { null }
            ?: throw ApiException(ErrorKind.Parse)
        val status = root.text("status")
        val errCode = root.text("error_code", "err_code", "errcode")
        val message = if (status == "1" || status == "200") "" else root.text("error", "error_msg", "errmsg", "info", "msg")
        log?.invoke(buildString {
            append("KuGou $label status=${status.ifBlank { "-" }} err=${errCode.ifBlank { "0" }}")
            if (message.isNotBlank()) append(" msg=$message")
        })
        if (check) ensureOk(root)
        return root
    }

    private fun ensureOk(root: JsonObject) {
        val status = root.text("status").toLongOrNull() ?: return
        if (status == 1L || status == 200L) return
        val errCode = root.text("error_code", "err_code", "errcode").toIntOrNull()
        val message = root.text("error", "error_msg", "errmsg", "info", "msg")
        if (errCode in setOf(20018, 20019, 10001) || message.contains("token", ignoreCase = true) || message.contains("登录"))
            throw ApiException(ErrorKind.Unauthorized, status.toInt(), message.ifBlank { null })
        throw ApiException(ErrorKind.Server, status.toInt(), message.ifBlank { null })
    }

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = try { it.body?.string().orEmpty() } catch (e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                        return
                    }
                    when {
                        it.code == 404 -> continuation.resumeWithException(ApiException(ErrorKind.NotFound, 404))
                        it.code == 429 -> continuation.resumeWithException(ApiException(ErrorKind.RateLimited, 429))
                        it.code >= 400 -> continuation.resumeWithException(ApiException(ErrorKind.Server, it.code))
                        else -> if (continuation.isActive) continuation.resume(text)
                    }
                }
            }
        })
    }
}
