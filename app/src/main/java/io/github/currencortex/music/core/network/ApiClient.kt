package io.github.currencortex.music.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.util.concurrent.TimeUnit
import io.github.currencortex.music.core.netease.NeteaseGateway

class ApiClient(
    private val server: () -> String,
    private val token: () -> String?,
    private val onUnauthorized: (RequestSession) -> Unit,
    private val log: (String) -> Unit = {},
    client: OkHttpClient = defaultHttpClient(),
    val netease: NeteaseGateway? = null,
    private val nativeMedia: () -> Boolean = { false },
    private val currentMusicAccountReady: () -> Boolean = { !token().isNullOrBlank() },
    private val currentMusicSessionRevision: () -> Long = { 0L },
    val routeServer: (String) -> String = { it },
) {
    companion object {
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(75, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS).followRedirects(false).build()
    }

    private val http = client.newBuilder().addInterceptor(AuthInterceptor()).build()
    private val singleAttemptHttp = http.newBuilder().retryOnConnectionFailure(false).build()
    private val feedbackHttp = singleAttemptHttp.newBuilder().followRedirects(false).followSslRedirects(false)
        .authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE).build()
    private val feedbackUploadHttp = feedbackHttp.newBuilder()
        .readTimeout(180, TimeUnit.SECONDS).callTimeout(240, TimeUnit.SECONDS).build()
    private val audioSourceHttp = http.newBuilder().followRedirects(false).followSslRedirects(false)
        .cookieJar(okhttp3.CookieJar.NO_COOKIES).build()
    val hasLocalNeteaseLogin get() = netease?.sessions?.state?.value?.loggedIn == true
    val nativeMediaEnabled get() = nativeMedia() && netease != null
    /** Public metadata requests belong to a server, never to an account token. */
    fun publicSession() = RequestSession(server(), null)
    private fun useNative(path: String, authenticated: Boolean, projectToken: String?): Boolean {
        if (netease?.handles(path, nativeMedia()) != true) return false
        // Imported snapshots belong to the project account, even with a local NetEase login.
        if (path == "ncmbind/sync" || path == "ncmbind/import") return false
        // Explicit native mode never falls back to the project server for NetEase routes.
        if (nativeMedia()) return true
        // Login runs on-device; project binding is synchronized separately over HTTPS.
        if (path.startsWith("ncmbind/phone/") || path.startsWith("ncmbind/qr/") ||
            path in setOf("ncmbind/membership", "ncmbind/medals", "ncm/captcha/sent/v1")) return true
        // Without a local credential, the project server can use its own saved binding.
        return hasLocalNeteaseLogin || !authenticated || projectToken.isNullOrBlank()
    }
    suspend fun request(method: String, path: String, query: Map<String, String> = emptyMap(),
                        body: JsonElement? = null, authenticated: Boolean = false, expectedSession: RequestSession? = null,
                        retryConnection: Boolean = true, callTimeoutMillis: Long? = null,
                        projectOnly: Boolean = false, passkeyOrigin: String? = null,
                        extraHeaders: Map<String, String> = emptyMap()): JsonElement = withContext(Dispatchers.IO) {
        require(extraHeaders.keys.all { it.equals("X-QQ-Session", ignoreCase = true) } &&
            (extraHeaders.isEmpty() || path.startsWith("qqmusic/")))
        val session = RequestSession(server(), if (authenticated) token() else null)
        val serverAudioSource = path == "audio-sources" || path.startsWith("audio-sources/")
        val requiresAccount = serverAudioSource || path == "ncmbind/import" || path.startsWith("ncmbind/qr/") || path.startsWith("ncmbind/phone/") || path == "ncm/captcha/sent/v1" ||
            (authenticated && path.startsWith("qqmusic/"))
        val loginOwner = RequestSession(server(), token())
        val loginRevision = currentMusicSessionRevision()
        fun checkLoginOwner() {
            if (requiresAccount && (loginOwner.token.isNullOrBlank() || !currentMusicAccountReady() ||
                loginOwner != RequestSession(server(), token()) || loginRevision != currentMusicSessionRevision()))
                throw if (serverAudioSource) ApiException(ErrorKind.Unauthorized, userMessage = "请登录当前 CurrentMusic 服务器后重试")
                else ApiException(ErrorKind.CurrentMusicAccountRequired)
        }
        // Native routes bypass project HTTP authentication, so login needs its own prerequisite.
        checkLoginOwner()
        if (expectedSession != null && (expectedSession.server != session.server || expectedSession.token != session.token)) throw ApiException(ErrorKind.Unauthorized)
        if ((netease?.handles(path) == true || path == "ncmbind/import") && expectedSession?.neteaseRevision != null &&
            expectedSession.neteaseRevision != netease?.sessions?.state?.value?.revision) throw ApiException(ErrorKind.NeteaseSessionChanged)
        if (!projectOnly && useNative(path, authenticated, session.token)) {
            val netease = requireNotNull(netease)
            if (expectedSession?.neteaseRevision != null && expectedSession.neteaseRevision != netease.sessions.state.value.revision)
                throw ApiException(ErrorKind.NeteaseSessionChanged)
            val response = netease.request(method, path, query, body, expectedSession?.neteaseRevision, ::checkLoginOwner)
            checkLoginOwner()
            if (expectedSession != null && (expectedSession.server != server() || expectedSession.token != if (authenticated) token() else null))
                throw ApiException(ErrorKind.Unauthorized)
            val changesLogin = path == "ncmbind/phone/login" || path == "ncmbind/qr/check" || path == "ncmbind/refresh" || (path == "ncmbind" && method == "DELETE")
            if (!changesLogin && expectedSession?.neteaseRevision != null && expectedSession.neteaseRevision != netease.sessions.state.value.revision)
                throw ApiException(ErrorKind.NeteaseSessionChanged)
            return@withContext response
        }
        // Native routing above owns its own credentials. Project-only protected requests
        // need a project session; do not send known-anonymous requests just to receive 401.
        if (authenticated && session.token.isNullOrBlank())
            throw ApiException(ErrorKind.Unauthorized, userMessage = "请先登录 CurrentMusic 账号")
        if (passkeyOrigin != null) {
            require(path in setOf("auth/passkeys/login/options", "auth/passkeys/login/verify"))
            require(passkeyOrigin.matches(Regex("android:apk-key-hash:[A-Za-z0-9_-]{43}")))
        }
        val url = ServerUrl.endpoint(if (passkeyOrigin != null) NativePasskeyRoute.server(session.server)
            else routeServer(session.server), path, query)
        if (serverAudioSource && !url.isHttps)
            throw ApiException(ErrorKind.Forbidden, userMessage = "服务器音源需要 HTTPS 线路")
        val request = Request.Builder().url(url).tag(RequestSession::class.java, session)
            .header("Accept", "application/json")
            .apply { extraHeaders.forEach { (key, value) -> header(key, value) } }
            .apply { if (passkeyOrigin != null) header("Origin", passkeyOrigin) }
            .method(method, if (method == "GET") null else (body?.toString() ?: "{}").toRequestBody("application/json".toMediaType()))
            .build()
        val requestClient = when {
            serverAudioSource -> audioSourceHttp
            retryConnection -> http
            else -> singleAttemptHttp
        }
        val call = requestClient.newCall(request)
        callTimeoutMillis?.let { call.timeout().timeout(it, TimeUnit.MILLISECONDS) }
        executeProjectRequest(path, call) {
            checkLoginOwner()
            if (expectedSession != null && (expectedSession.server != server() || expectedSession.token != if (authenticated) token() else null))
                throw ApiException(ErrorKind.Unauthorized)
            if ((netease?.handles(path) == true || path == "ncmbind/import") && expectedSession?.neteaseRevision != null &&
                expectedSession.neteaseRevision != netease?.sessions?.state?.value?.revision) throw ApiException(ErrorKind.NeteaseSessionChanged)
        }
    }

    internal suspend fun feedbackCapabilities(expectedSession: RequestSession): JsonElement? = withContext(Dispatchers.IO) {
        val session = publicSession()
        if (session.server != expectedSession.server) throw ApiException(ErrorKind.Unauthorized)
        val path = "feedback/capabilities"
        val url = ServerUrl.endpoint(routeServer(session.server), path, emptyMap())
        if (!url.isHttps) return@withContext null
        val request = Request.Builder().url(url).tag(RequestSession::class.java, session)
            .header("Accept", "application/json")
            .build()
        executeProjectRequest(path, feedbackHttp.newCall(request)) {
            if (session.server != server()) throw ApiException(ErrorKind.Unauthorized)
        }
    }

    internal suspend fun submitFeedback(
        expectedSession: RequestSession,
        jsonBody: JsonElement,
        attachmentBody: MultipartBody?,
        hasMedia: Boolean = false,
    ): JsonElement = feedbackMutation(
        expectedSession, "POST", "feedback",
        attachmentBody ?: jsonBody.toString().toRequestBody("application/json".toMediaType()),
        httpsOnly = attachmentBody != null || hasMedia,
    )

    internal suspend fun uploadFeedbackMedia(
        expectedSession: RequestSession,
        requestId: String,
        slot: String,
        payload: RequestBody,
    ): JsonElement = feedbackMutation(
        expectedSession, "PUT", "feedback/$requestId/media/$slot", payload, mediaUpload = true,
    )

    internal suspend fun deleteFeedbackMedia(expectedSession: RequestSession, requestId: String, slot: String) {
        feedbackMutation(expectedSession, "DELETE", "feedback/$requestId/media/$slot", ByteArray(0).toRequestBody())
    }

    private suspend fun feedbackMutation(
        expectedSession: RequestSession,
        method: String,
        path: String,
        payload: RequestBody,
        httpsOnly: Boolean = true,
        mediaUpload: Boolean = false,
    ): JsonElement = withContext(Dispatchers.IO) {
        val session = RequestSession(server(), token())
        val revision = currentMusicSessionRevision()
        if (session.server != expectedSession.server || session.token != expectedSession.token)
            throw ApiException(ErrorKind.Unauthorized)
        if (session.token.isNullOrBlank())
            throw ApiException(ErrorKind.Unauthorized, userMessage = "请先登录 CurrentMusic 账号")
        val url = ServerUrl.endpoint(routeServer(session.server), path, emptyMap())
        if (httpsOnly && !url.isHttps)
            throw ApiException(ErrorKind.Forbidden, userMessage = "反馈附件需要 HTTPS 线路")
        val singleUseBody = object : RequestBody() {
            override fun contentType() = payload.contentType()
            override fun contentLength() = payload.contentLength()
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) = payload.writeTo(sink)
        }
        val request = Request.Builder().url(url).tag(RequestSession::class.java, session)
            .header("Accept", "application/json")
            .apply { if (mediaUpload) header("X-Feedback-Media-Consent", "true") }
            .method(method, singleUseBody)
            .build()
        fun checkSession() {
            if (session != RequestSession(server(), token()) || revision != currentMusicSessionRevision())
                throw ApiException(ErrorKind.Unauthorized)
        }
        checkSession()
        val requestClient = if (mediaUpload) feedbackUploadHttp else feedbackHttp
        executeProjectRequest(path, requestClient.newCall(request), ::checkSession)
    }

    private suspend fun executeProjectRequest(path: String, call: Call, checkSession: () -> Unit): JsonElement {
        val request = call.request()
        val session = checkNotNull(request.tag(RequestSession::class.java))
        val authenticated = session.token != null
        val serverAudioSource = path == "audio-sources" || path.startsWith("audio-sources/")
        val (code, raw) = call.readResponse { response ->
            val responseBody = response.body
            if (serverAudioSource && responseBody?.source()?.request(65_537) == true)
                throw ApiException(ErrorKind.Parse, userMessage = "服务器音源响应过大")
            response.code to responseBody?.string().orEmpty()
        }
        checkSession()
        log("${request.method} $path HTTP $code")
        if (code !in 200..299) {
            // Older NCM gateway also uses 401 for missing NCM binding. Only a project-session
            // rejection should invalidate the account.
            val errorText = raw
            if (code == 401 && authenticated && !path.startsWith("qqmusic/") && !errorText.contains("绑定")) onUnauthorized(session)
            // Account validation is user-facing upstream text, not a generic HTTP failure.
            // Other routes and server failures retain their existing bounded error mapping.
            val accountError = if (code in 400..499 && (path.startsWith("qqmusic/") || path in setOf("auth/login", "auth/2fa/login", "auth/security-question/login",
                    "auth/login/email", "auth/login/email/code", "auth/2fa/setup", "auth/2fa/enable", "auth/2fa/disable",
                    "auth/security-question", "auth/passkeys/login/options", "auth/passkeys/login/verify",
                    "auth/register", "auth/email/code", "auth/phone/code",
                    "auth/password/code", "auth/password/reset", "profile/password/code", "profile/password")))
                runCatching {
                    val error = ApiJson.parseToJsonElement(errorText).jsonObject
                    ((error["error"] ?: error["message"]) as? JsonPrimitive)
                        ?.takeIf { value -> value.isString }?.content?.trim()?.takeIf { value -> value.isNotEmpty() }?.take(240)
                }.getOrNull() else null
            throw ApiException(when (code) {
                401 -> if (errorText.contains("绑定")) ErrorKind.Forbidden else ErrorKind.Unauthorized
                403 -> ErrorKind.Forbidden; 404 -> ErrorKind.NotFound; 429 -> ErrorKind.RateLimited
                in 500..599 -> ErrorKind.Server; else -> ErrorKind.Unknown
            }, code, accountError)
        }
        return if (raw.isBlank()) JsonNull else ApiJson.parseToJsonElement(raw)
    }
    suspend inline fun <reified T> decode(value: JsonElement): T = withContext(Dispatchers.Default) {
        ApiJson.decodeFromJsonElement(value)
    }
    suspend inline fun <reified T> get(path: String, query: Map<String, String> = emptyMap(), authenticated: Boolean = false): T =
        decode(request("GET", path, query, authenticated = authenticated))
}
