package io.github.currencortex.music.core.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import io.github.currencortex.music.core.netease.NeteaseGateway

class ApiClient(
    private val server: () -> String,
    private val token: () -> String?,
    private val onUnauthorized: (RequestSession) -> Unit,
    private val log: (String) -> Unit = {},
    client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS).followRedirects(false).build(),
    val netease: NeteaseGateway? = null,
) {
    private val http = client.newBuilder().addInterceptor(AuthInterceptor()).build()
    private val singleAttemptHttp = http.newBuilder().retryOnConnectionFailure(false).build()
    suspend fun request(method: String, path: String, query: Map<String, String> = emptyMap(),
                        body: JsonElement? = null, authenticated: Boolean = false, expectedSession: RequestSession? = null,
                        retryConnection: Boolean = true, callTimeoutMillis: Long? = null): JsonElement = withContext(Dispatchers.IO) {
        val session = RequestSession(server(), if (authenticated) token() else null)
        if (expectedSession != null && (expectedSession.server != session.server || expectedSession.token != session.token)) throw ApiException(ErrorKind.Unauthorized)
        if (netease?.handles(path) == true) {
            if (expectedSession?.neteaseRevision != null && expectedSession.neteaseRevision != netease.sessions.state.value.revision)
                throw ApiException(ErrorKind.NeteaseSessionChanged)
            val response = netease.request(method, path, query, body, expectedSession?.neteaseRevision)
            if (expectedSession != null && (expectedSession.server != server() || expectedSession.token != if (authenticated) token() else null))
                throw ApiException(ErrorKind.Unauthorized)
            val changesLogin = path == "ncmbind/phone/login" || path == "ncmbind/qr/check" || path == "ncmbind/refresh" || (path == "ncmbind" && method == "DELETE")
            if (!changesLogin && expectedSession?.neteaseRevision != null && expectedSession.neteaseRevision != netease.sessions.state.value.revision)
                throw ApiException(ErrorKind.NeteaseSessionChanged)
            return@withContext response
        }
        val url = ServerUrl.endpoint(session.server, path, query)
        val request = Request.Builder().url(url).tag(RequestSession::class.java, session)
            .header("Accept", "application/json")
            .method(method, if (method == "GET") null else (body?.toString() ?: "{}").toRequestBody("application/json".toMediaType()))
            .build()
        val response = suspendCancellableCoroutine<Response> { cont ->
            val call = (if (retryConnection) http else singleAttemptHttp).newCall(request)
            callTimeoutMillis?.let { call.timeout().timeout(it, TimeUnit.MILLISECONDS) }
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response, onCancellation = { _, value, _ -> value.close() })
                }
            })
        }
        response.use {
            log("$method $path HTTP ${it.code}")
            if (!it.isSuccessful) {
                // Older NCM gateway also uses 401 for missing NCM binding. Only a project-session
                // rejection should invalidate the account.
                val errorText = it.body?.string().orEmpty()
                if (it.code == 401 && authenticated && !errorText.contains("绑定")) onUnauthorized(session)
                throw ApiException(when (it.code) {
                    401 -> if (errorText.contains("绑定")) ErrorKind.Forbidden else ErrorKind.Unauthorized
                    403 -> ErrorKind.Forbidden; 404 -> ErrorKind.NotFound; 429 -> ErrorKind.RateLimited
                    in 500..599 -> ErrorKind.Server; else -> ErrorKind.Unknown
                }, it.code)
            }
            val raw = it.body?.string().orEmpty()
            if (raw.isBlank()) JsonNull else ApiJson.parseToJsonElement(raw)
        }
    }
    suspend inline fun <reified T> decode(value: JsonElement): T = withContext(Dispatchers.Default) {
        ApiJson.decodeFromJsonElement(value)
    }
    suspend inline fun <reified T> get(path: String, query: Map<String, String> = emptyMap(), authenticated: Boolean = false): T =
        decode(request("GET", path, query, authenticated = authenticated))
}
