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

class ApiClient(
    private val server: () -> String,
    private val token: () -> String?,
    private val onUnauthorized: (RequestSession) -> Unit,
    private val log: (String) -> Unit = {},
    client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS).followRedirects(false).build(),
) {
    private val http = client.newBuilder().addInterceptor(AuthInterceptor()).build()
    suspend fun request(method: String, path: String, query: Map<String, String> = emptyMap(),
                        body: JsonElement? = null, authenticated: Boolean = false): JsonElement = withContext(Dispatchers.IO) {
        val session = RequestSession(server(), if (authenticated) token() else null)
        val url = ServerUrl.endpoint(session.server, path, query)
        val request = Request.Builder().url(url).tag(RequestSession::class.java, session)
            .header("Accept", "application/json")
            .method(method, if (method == "GET") null else (body?.toString() ?: "{}").toRequestBody("application/json".toMediaType()))
            .build()
        val response = suspendCancellableCoroutine<Response> { cont ->
            val call = http.newCall(request)
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
    suspend inline fun <reified T> get(path: String, query: Map<String, String> = emptyMap(), authenticated: Boolean = false): T =
        ApiJson.decodeFromJsonElement(request("GET", path, query, authenticated = authenticated))
}
