package io.github.currencortex.music.core.network

import io.github.currencortex.music.core.config.ServerDefaults
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.*
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Declaration order is also the presentation order; latency never reorders these choices. */
enum class CurrentMusicServer(val label: String, val url: String? = null) {
    AUTO("自动"), MAIN("music.20110208.xyz · 主站", ServerDefaults.URL),
    CDN("cm.bileizhen.top · CDN", ServerDefaults.CDN),
    CDN_GEEKERTAO("currentmusic.geekertao.com · CDN", ServerDefaults.CDN_GEEKERTAO),
    CDN_CTCEHS("music.ctcehs.com · CDN", ServerDefaults.CDN_CTCEHS), CUSTOM("自定义");

    companion object {
        val presets = listOf(MAIN, CDN, CDN_GEEKERTAO, CDN_CTCEHS)
        fun infer(server: String?): CurrentMusicServer = when (server?.let(ServerUrl::normalize)) {
            null -> AUTO
            ServerDefaults.URL -> MAIN
            ServerDefaults.CDN -> CDN
            ServerDefaults.CDN_GEEKERTAO -> CDN_GEEKERTAO
            ServerDefaults.CDN_CTCEHS -> CDN_CTCEHS
            else -> CUSTOM
        }
        fun from(value: String?, server: String?): CurrentMusicServer =
            entries.firstOrNull { it.name == value } ?: infer(server)
        fun builtIn(server: String) = presets.any { it.url == server }
    }
}

enum class ServerStatus { CHECKING, AVAILABLE, TIMEOUT, UNAVAILABLE }
data class ServerHealth(val status: ServerStatus, val latencyMs: Long? = null)
fun interface ServerCheck { suspend fun check(server: String): ServerHealth }

/** Anonymous application-level check: no account tokens, redirects, or arbitrary success pages. */
class CurrentMusicServerProbe(client: OkHttpClient = OkHttpClient(), timeoutMs: Long = 5_000) : ServerCheck {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS).callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun check(server: String): ServerHealth = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        try {
            val request = Request.Builder().url(ServerUrl.endpoint(server, "announcements", mapOf("limit" to "1")))
                .header("Accept", "application/json").build()
            val call = http.newCall(request)
            val cancel = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { call.cancel() }
            try {
                val response = suspendCancellableCoroutine<Response> { cont ->
                    cont.invokeOnCancellation { call.cancel() }
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
                        override fun onResponse(call: Call, response: Response) {
                            cont.resume(response, onCancellation = { _, value, _ -> value.close() })
                        }
                    })
                }
                response.use {
                    val valid = it.isSuccessful && runCatching {
                        ApiJson.parseToJsonElement(it.peekBody(64 * 1024).string()).jsonObject["items"] is JsonArray
                    }.getOrDefault(false)
                    ServerHealth(if (valid) ServerStatus.AVAILABLE else ServerStatus.UNAVAILABLE,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started).coerceAtLeast(1))
                }
            } finally { cancel.dispose() }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            ServerHealth(if (failure is InterruptedIOException) ServerStatus.TIMEOUT else ServerStatus.UNAVAILABLE)
        }
    }
}

/** Transport changes within the trusted presets do not change account/session identity. */
class CurrentMusicServerRouter(private val check: ServerCheck = CurrentMusicServerProbe()) {
    private data class Selection(val mode: CurrentMusicServer, val server: String)
    @Volatile private var selection = Selection(CurrentMusicServer.MAIN, ServerDefaults.URL)
    private val mutex = Mutex()
    private val _health = MutableStateFlow<Map<String, ServerHealth>>(emptyMap())
    val health = _health.asStateFlow()
    private val _automatic = MutableStateFlow(ServerDefaults.URL)
    val automatic = _automatic.asStateFlow()

    fun configure(mode: CurrentMusicServer, server: String) {
        selection = Selection(mode, ServerUrl.normalize(server))
    }

    fun resolve(server: String): String {
        val selected = selection
        // A saved account, custom server, or unrelated media host cannot inherit this route.
        if (server != selected.server || !CurrentMusicServer.builtIn(server)) return server
        return when (selected.mode) {
            CurrentMusicServer.AUTO -> automatic.value
            CurrentMusicServer.MAIN -> ServerDefaults.URL
            CurrentMusicServer.CDN -> ServerDefaults.CDN
            CurrentMusicServer.CDN_GEEKERTAO -> ServerDefaults.CDN_GEEKERTAO
            CurrentMusicServer.CDN_CTCEHS -> ServerDefaults.CDN_CTCEHS
            CurrentMusicServer.CUSTOM -> server
        }
    }

    suspend fun refresh(customServer: String = "") = mutex.withLock {
        val servers = (CurrentMusicServer.presets.map { requireNotNull(it.url) } +
            listOfNotNull(customServer.takeIf(String::isNotBlank)?.let(ServerUrl::normalize))).distinct()
        _health.update { it + servers.associateWith { ServerHealth(ServerStatus.CHECKING) } }
        val results = coroutineScope {
            servers.map { server -> async { server to check.check(server) } }.awaitAll().toMap()
        }
        _health.update { it + results }
        // Failed/custom checks never win. Ties preserve preset order; total failure keeps the last route.
        CurrentMusicServer.presets.map { requireNotNull(it.url) }
            .filter { results[it]?.status == ServerStatus.AVAILABLE }
            .minByOrNull { results[it]?.latencyMs ?: Long.MAX_VALUE }?.let { _automatic.value = it }
    }
}
