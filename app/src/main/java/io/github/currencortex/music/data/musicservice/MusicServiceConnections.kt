package io.github.currencortex.music.data.musicservice

import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.core.network.RequestSession
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.core.network.readResponse
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.song.AudioSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ConnectedMusicService(
    val key: String, val descriptorUrl: String, val descriptor: MusicServiceDescriptor,
    val authorizedScopes: Set<String> = emptySet(), val account: String? = null,
    val authorizationRevision: String = "",
)

@Serializable
private class ServiceGrant(
    @SerialName("access_token") val access: String,
    @SerialName("refresh_token") val refresh: String? = null,
    @SerialName("expires_in") val expiry: Long,
    val scope: String = "",
    val sub: String,
) {
    val scopes get() = scope.split(' ').filter(String::isNotBlank).toSet()
}

@Serializable private class ServiceEntry(
    val key: String, val url: String, val descriptor: MusicServiceDescriptor,
    val grant: ServiceGrant? = null, val expiresAt: Long = 0, val authorizationRevision: String = "",
) {
    fun view() = ConnectedMusicService(key, url, descriptor, grant?.scopes.orEmpty(), grant?.sub, authorizationRevision)
    fun withGrant(value: ServiceGrant, revision: String = authorizationRevision) = ServiceEntry(key, url, descriptor, value,
        System.currentTimeMillis() + value.expiry.coerceIn(1, 86_400) * 1000, revision)
}

@Serializable private class PendingServiceGrant(
    val key: String, val state: String, val verifier: String, val required: Set<String>, val startedAt: Long,
)

@Serializable private class ConnectionData(
    val entries: List<ServiceEntry> = emptyList(), val pending: PendingServiceGrant? = null,
)

class MusicServiceConnections(private val store: TokenStore, private val scope: CoroutineScope, http: OkHttpClient = OkHttpClient()) {
    private val http = http.newBuilder().followRedirects(false).followSslRedirects(false)
        .cookieJar(okhttp3.CookieJar.NO_COOKIES).retryOnConnectionFailure(false)
        .callTimeout(35, TimeUnit.SECONDS).build()
    private val mutex = Mutex()
    private val random = SecureRandom()
    @Volatile private var data = ConnectionData()
    private val ready = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val _services = MutableStateFlow(emptyList<ConnectedMusicService>())
    val services = _services.asStateFlow()
    val pendingConnection = MutableStateFlow<Pair<String, MusicServiceDescriptor>?>(null)
    internal val lastAuthorizedService = MutableStateFlow<String?>(null)
    private val _authorizationOutcome = MutableStateFlow<AppResult<String>?>(null)
    internal val authorizationOutcome = _authorizationOutcome.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            try {
                val saved = store.read()?.let { ApiJson.decodeFromString<ConnectionData>(it) } ?: ConnectionData()
                publish(ConnectionData(saved.entries.mapNotNull { entry ->
                    runCatching { entry.descriptor.validate(entry.url); entry }.getOrNull()
                }, saved.pending))
                ready.complete(Unit)
            } catch (e: CancellationException) {
                ready.cancel(e)
                throw e
            } catch (e: Exception) {
                ready.completeExceptionally(ApiException(ErrorKind.Parse, userMessage = "无法读取本机的外接服务"))
            }
        }
    }

    private fun publish(value: ConnectionData) {
        data = value
        _services.value = value.entries.map(ServiceEntry::view)
    }

    private suspend fun save(value: ConnectionData) {
        withContext(Dispatchers.IO) { store.write(ApiJson.encodeToString(value)) }
        publish(value)
    }

    suspend fun preview(url: String) {
        ready.await()
        pendingConnection.value = null
        val endpoint = httpsUrl(url)
        require(endpoint.query == null) { "服务描述地址不能携带查询参数" }
        val response = fetch(endpoint)
        pendingConnection.value = endpoint.toString() to
            ApiJson.decodeFromJsonElement<MusicServiceDescriptor>(response).validate(endpoint.toString())
    }

    suspend fun connect(url: String, descriptor: MusicServiceDescriptor): String = mutex.withLock {
        ready.await()
        val checked = descriptor.validate(url)
        val old = data.entries.firstOrNull { it.url == url }
        if (old != null && (old.descriptor.authorization != checked.authorization ||
                old.descriptor.apiBase != checked.apiBase || old.descriptor.id != checked.id)) {
            throw ApiException(ErrorKind.Forbidden, userMessage = "授权地址已变更，请先断开原连接")
        }
        require(old != null || data.entries.size < 16) { "已达到外接服务数量上限" }
        val entry = ServiceEntry(old?.key ?: UUID.randomUUID().toString(), url, checked, old?.grant, old?.expiresAt ?: 0,
            old?.authorizationRevision.orEmpty())
        save(ConnectionData(data.entries.filterNot { it.key == entry.key } + entry, data.pending))
        pendingConnection.value = null
        entry.key
    }

    private fun entry(key: String): ServiceEntry = data.entries.firstOrNull { it.key == key }
        ?: throw ApiException(ErrorKind.NotFound, userMessage = "该外接服务已断开")

    suspend fun beginAuthorization(key: String, requested: Set<String>): String = mutex.withLock {
        ready.await()
        val entry = entry(key)
        val auth = entry.descriptor.authorization ?: throw ApiException(ErrorKind.Forbidden)
        val supported = entry.descriptor.platforms.values.flatten().mapNotNull(MusicServiceCapability::scope).toSet()
        require(requested.isNotEmpty() && supported.containsAll(requested))
        val scopes = requested + entry.grant?.scopes.orEmpty().intersect(supported)
        val pending = PendingServiceGrant(key, nonce(), nonce(), scopes, System.currentTimeMillis())
        val challenge = base64(MessageDigest.getInstance("SHA-256").digest(pending.verifier.toByteArray()))
        val authorizationUrl = httpsUrl(auth.authorizeUrl, fragment = true)
        fun HttpUrl.Builder.parameters() = addQueryParameter("client_id", auth.clientId)
            .addQueryParameter("response_type", "code").addQueryParameter("redirect_uri", REDIRECT_URI)
            .addQueryParameter("scope", (listOf("openid", "offline_access") + scopes.sorted()).joinToString(" "))
            .addQueryParameter("state", pending.state).addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
        val result = if (authorizationUrl.fragment != null) {
            val route = authorizationUrl.fragment.orEmpty()
            require(route.startsWith("/") && !route.startsWith("//") && '?' !in route)
            val params = HttpUrl.Builder().scheme("https").host(authorizationUrl.host).parameters().build().encodedQuery
            authorizationUrl.newBuilder().encodedFragment("$route?$params").build().toString()
        } else authorizationUrl.newBuilder().parameters().build().toString()
        save(ConnectionData(data.entries, pending))
        result
    }

    internal fun receiveAuthorization(raw: String) {
        scope.launch { _authorizationOutcome.value = appResult { completeAuthorization(raw) } }
    }

    internal fun consumeAuthorizationOutcome(outcome: AppResult<String>) {
        _authorizationOutcome.compareAndSet(outcome, null)
    }

    internal suspend fun completeAuthorization(raw: String): String = mutex.withLock {
        ready.await()
        val url = httpsUrl(raw)
        require(url.host == CONNECTION_HOST && url.port == 443 && url.encodedPath == RETURN_PATH)
        val pending = data.pending ?: throw ApiException(ErrorKind.Unauthorized, userMessage = "没有待完成的服务授权")
        val entry = entry(pending.key)
        val auth = requireNotNull(entry.descriptor.authorization)
        require(url.queryParameterValues("iss") == listOf(auth.issuer) &&
            url.queryParameterValues("state") == listOf(pending.state)) { "授权回跳与请求不匹配" }
        require(System.currentTimeMillis() - pending.startedAt in 0..600_000) { "服务授权已过期" }
        save(ConnectionData(data.entries))
        if (url.queryParameter("error") != null) throw ApiException(ErrorKind.Unauthorized, userMessage = "已取消服务授权")
        val code = url.queryParameterValues("code").singleOrNull()?.takeIf { it.length in 1..512 }
            ?: throw ApiException(ErrorKind.Parse, userMessage = "授权回跳缺少有效授权码")
        val grant = token(auth, buildJsonObject {
            put("grant_type", "authorization_code"); put("client_id", auth.clientId); put("code", code)
            put("redirect_uri", REDIRECT_URI); put("code_verifier", pending.verifier); put("platform", "android")
        })
        require(grant.scopes.containsAll(pending.required)) { "服务返回的授权范围不足" }
        replace(entry.withGrant(grant, UUID.randomUUID().toString()))
        lastAuthorizedService.value = entry.key
        entry.key
    }

    suspend fun disconnect(key: String): Boolean = mutex.withLock {
        ready.await()
        val entry = entry(key)
        save(ConnectionData(data.entries.filterNot { it.key == key }, data.pending?.takeUnless { it.key == key }))
        val auth = entry.descriptor.authorization
        val grant = entry.grant
        if (auth == null || grant == null) return@withLock true
        try {
            fetch(httpsUrl(auth.revokeUrl), buildJsonObject {
                put("client_id", auth.clientId)
                put("token", grant.refresh ?: grant.access)
            })
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun replace(entry: ServiceEntry) =
        save(ConnectionData(data.entries.map { if (it.key == entry.key) entry else it }, data.pending))

    private suspend fun token(auth: MusicServiceAuthorization, input: JsonObject): ServiceGrant =
        ApiJson.decodeFromJsonElement<ServiceGrant>(fetch(httpsUrl(auth.tokenUrl), input)).also {
            require(it.access.isNotBlank() && it.access.length <= 4096 && it.sub.isNotBlank() && it.sub.length <= 256 &&
                (it.refresh?.length ?: 0) <= 4096)
        }

    private fun checkAuthorization(expected: ServiceEntry, current: ServiceEntry) {
        if (expected.grant?.sub != current.grant?.sub || expected.authorizationRevision != current.authorizationRevision)
            throw ApiException(ErrorKind.AudioSourceChanged)
    }

    private suspend fun access(expected: ServiceEntry, scope: String): String = mutex.withLock {
        val entry = entry(expected.key)
        checkAuthorization(expected, entry)
        val grant = entry.grant?.takeIf { scope in it.scopes }
            ?: throw ApiException(ErrorKind.Unauthorized, userMessage = "请授权这项音乐服务能力")
        if (entry.expiresAt > System.currentTimeMillis() + 30_000) return@withLock grant.access
        val auth = requireNotNull(entry.descriptor.authorization)
        val refresh = grant.refresh ?: throw ApiException(ErrorKind.Unauthorized, userMessage = "服务授权已过期")
        val next = token(auth, buildJsonObject {
            put("grant_type", "refresh_token"); put("client_id", auth.clientId); put("refresh_token", refresh)
        })
        require(next.sub == grant.sub && scope in next.scopes) { "刷新后的服务授权已变更" }
        val refreshToken = next.refresh?.takeIf(String::isNotBlank) ?: refresh
        replace(entry.withGrant(ServiceGrant(next.access, refreshToken, next.expiry, next.scope, next.sub)))
        next.access
    }

    suspend fun call(key: String, platform: String, operation: String, input: JsonObject = buildJsonObject {},
        confirm: Boolean = false, expectedConnection: ConnectedMusicService? = null): JsonObject {
        ready.await()
        val entry = entry(key)
        if (expectedConnection != null && (expectedConnection.key != key || expectedConnection.account != entry.grant?.sub ||
                expectedConnection.authorizationRevision != entry.authorizationRevision))
            throw ApiException(ErrorKind.AudioSourceChanged)
        val capability = entry.descriptor.capability(platform, operation) ?: throw ApiException(ErrorKind.NotFound)
        require(!capability.write || confirm) { "请确认本次写入" }
        val bearer = capability.scope?.let { access(entry, it) }
        val endpoint = httpsUrl(entry.descriptor.apiBase).newBuilder()
            .encodedPath(httpsUrl(entry.descriptor.apiBase).encodedPath.trimEnd('/') + "/")
            .addPathSegment(operation).build()
        return fetch(endpoint, buildJsonObject {
            put("provider", platform)
            put("input", input)
            if (confirm) put("confirm", true)
        }, bearer).jsonObject.also {
            checkAuthorization(entry, entry(key))
        }
    }

    internal fun playbackSession(track: ServiceTrack): RequestSession =
        RequestSession(data.entries.firstOrNull { it.key == track.connection }?.descriptor?.apiBase.orEmpty(), null)

    internal fun playbackIdentity(track: ServiceTrack): String? =
        identityFor(track, "playback.resolve")

    internal fun identityFor(track: ServiceTrack, operation: String): String? =
        data.entries.firstOrNull { it.key == track.connection }
            ?.takeIf { entry ->
                val cap = entry.descriptor.capability(track.platform, operation) ?: return@takeIf false
                cap.scope == null || cap.scope in entry.grant?.scopes.orEmpty()
            }?.let { "external:${track.connection}:${track.platform}:${it.grant?.sub.orEmpty()}:${it.authorizationRevision}" }

    internal fun historySession(track: ServiceTrack): RequestSession =
        RequestSession(playbackSession(track).server, identityFor(track, "history.record"))

    internal suspend fun resolve(track: ServiceTrack, quality: AudioQuality, refresh: Boolean, supportsDolby: Boolean): AudioSource =
        media(track, "playback.resolve", quality, refresh, supportsDolby)

    internal suspend fun media(track: ServiceTrack, operation: String, quality: AudioQuality = AudioQuality.AUTO,
        refresh: Boolean = false, supportsDolby: Boolean = false): AudioSource {
        require(operation in setOf("playback.resolve", "playback.mv", "playback.download"))
        val result = call(track.connection, track.platform, operation, buildJsonObject {
            put("id", track.id); put("quality", quality.value); put("refresh", refresh)
            put("supportsDolby", supportsDolby)
        })
        val url = serviceMediaUrl(result["url"]?.jsonPrimitive?.content.orEmpty())
        return AudioSource(url, result["quality"]?.jsonPrimitive?.content.orEmpty(), 0, 0,
            format = result["format"]?.jsonPrimitive?.content.orEmpty(),
            bitrate = result["bitrate"]?.jsonPrimitive?.longOrNull ?: 0,
            validForSeconds = (result["expiresIn"]?.jsonPrimitive?.longOrNull ?: 60).coerceIn(1, 600))
    }

    private suspend fun fetch(endpoint: HttpUrl, input: JsonObject? = null, bearer: String? = null): JsonElement {
        val request = Request.Builder().url(endpoint).header("Accept", "application/json").apply {
            if (bearer != null) header("Authorization", "Bearer $bearer")
            if (input != null) post(input.toString().toRequestBody("application/json".toMediaType()))
        }.build()
        return http.newCall(request).readResponse { response ->
            val body = response.body ?: throw ApiException(ErrorKind.Parse)
            if (body.source().request(2_097_153)) throw ApiException(ErrorKind.Parse)
            val text = body.string()
            val value = runCatching { ApiJson.parseToJsonElement(text) }.getOrNull()
            if (!response.isSuccessful) {
                val message = (value as? JsonObject)?.get("error") as? JsonPrimitive
                val kind = when (response.code) {
                    401 -> ErrorKind.Unauthorized
                    403 -> ErrorKind.Forbidden
                    404 -> ErrorKind.NotFound
                    429 -> ErrorKind.RateLimited
                    in 500..599 -> ErrorKind.Server
                    else -> ErrorKind.Unknown
                }
                throw ApiException(kind, response.code, message?.content?.take(160))
            }
            value ?: throw ApiException(ErrorKind.Parse)
        }
    }

    private fun nonce(): String = base64(ByteArray(32).also(random::nextBytes))
    private fun base64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
