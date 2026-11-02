package io.github.currencortex.music.data.lx

import android.content.Context
import android.net.Uri
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.song.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import io.github.currencortex.music.core.network.SourceAvailabilityStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Scripts may contain their own keys. Keep the library encrypted and out of Android backups. */
class LxSourceStore(private val context: Context, private val vault: TokenStore, scope: CoroutineScope,
    private val runtime: LxRuntime = LxRuntime(context), private val http: LxHttpClient = LxHttpClient()) : java.io.Closeable {
    val state = MutableStateFlow<List<LxSourceInfo>>(emptyList())
    val health = MutableStateFlow<Map<String, LxSourceHealth>>(emptyMap())
    val ready = CompletableDeferred<Unit>()
    private val lock = Mutex()
    private val probeLock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(LxStoredSource.serializer())
    private var entries: List<LxStoredSource> = emptyList()
    init { scope.launch(Dispatchers.IO) {
        try {
            entries = vault.read()?.let { json.decodeFromString(serializer, it) }.orEmpty()
                .filter { it.script.toByteArray().size <= LX_MAX_SCRIPT_BYTES && lxScriptInfo(it.script).id == it.info.id }.take(12)
                .map { LxStoredSource(it.info.copy(capabilities = lxNeteaseCapabilities(it.info.capabilities)), it.script) }
            state.value = entries.map { it.info }
            ready.complete(Unit)
        } catch (cancelled: CancellationException) { ready.cancel(cancelled); throw cancelled }
        catch (_: Exception) { ready.completeExceptionally(LxSourceException("落雪音源列表读取失败，请重新导入")) }
    } }
    fun supported() = runtime.supported()
    suspend fun importFile(uri: Uri): LxSourceInfo = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "请选择系统文件选择器中的音源脚本" }
        val script = context.contentResolver.openInputStream(uri)?.use { lxReadBounded(it, LX_MAX_SCRIPT_BYTES).toString(Charsets.UTF_8) }
            ?: throw LxSourceException("无法读取音源文件")
        importScript(script)
    }
    suspend fun importUrl(value: String): LxSourceInfo = withContext(Dispatchers.IO) {
        val url = value.trim()
        importScript(http.download(url), url)
    }
    suspend fun importScript(script: String, origin: String = ""): LxSourceInfo = withContext(Dispatchers.IO) {
        ready.await()
        lock.withLock {
            val info = lxScriptInfo(script, origin)
            entries.firstOrNull { it.info.id == info.id }?.let { throw LxSourceException("该音源已导入，可直接选择使用") }
            require(entries.size < 12 && entries.sumOf { it.script.toByteArray().size } + script.toByteArray().size <= 8 * LX_MAX_SCRIPT_BYTES) {
                "音源数量或总大小已达上限，请先移除不使用的音源"
            }
            val capabilities = runtime.inspect(info, script)
            val complete = info.copy(capabilities = capabilities)
            save(entries + LxStoredSource(complete, script))
            complete
        }
    }
    suspend fun remove(id: String) {
        ready.await()
        withContext(Dispatchers.IO) { lock.withLock { save(entries.filterNot { it.info.id == id }) } }
        runtime.close()
        health.update { it - id }
    }
    /** Called by the visible settings page; no timer or background polling. */
    suspend fun checkAvailability(id: String) {
        ready.await()
        if (!probeLock.tryLock()) return
        val previous = health.value[id] ?: LxSourceHealth()
        try {
            if (state.value.none { it.id == id } || previous.fresh(android.os.SystemClock.elapsedRealtime())) return
            health.update { it + (id to previous.copy(status = SourceAvailabilityStatus.CHECKING)) }
            val result = try {
                withTimeout(60_000) {
                    // Fixed NetEase test track. Never changes the selected source, queue or playback.
                    val test = Song(2135426631, "5:20AM", "刀酱", "5:20AM", durationMs = 142000)
                    val audio = source(test, AudioQuality.STANDARD, id)
                    val status = http.probeAudio(audio.url)
                    LxSourceHealth(status, android.os.SystemClock.elapsedRealtime(), when (status) {
                        SourceAvailabilityStatus.AVAILABLE -> "已解析并读取到音频数据"
                        SourceAvailabilityStatus.LIMITED -> "音频地址返回限流响应"
                        SourceAvailabilityStatus.TIMEOUT -> "音频地址响应超时"
                        else -> "返回的音频地址不可用或不是音频数据"
                    })
                }
            } catch (_: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                LxSourceHealth(SourceAvailabilityStatus.TIMEOUT, android.os.SystemClock.elapsedRealtime(), "检测超时")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: java.net.SocketTimeoutException) {
                LxSourceHealth(SourceAvailabilityStatus.TIMEOUT, android.os.SystemClock.elapsedRealtime(), "网络响应超时")
            } catch (error: LxSourceException) {
                val timedOut = error.message?.contains("超时") == true
                LxSourceHealth(if (timedOut) SourceAvailabilityStatus.TIMEOUT else SourceAvailabilityStatus.UNAVAILABLE,
                    android.os.SystemClock.elapsedRealtime(), if (timedOut) "音源解析超时" else "音源解析失败")
            } catch (_: Exception) {
                LxSourceHealth(SourceAvailabilityStatus.UNAVAILABLE, android.os.SystemClock.elapsedRealtime(), "音源解析或网络连接失败")
            }
            if (state.value.any { it.id == id }) health.update { it + (id to result) }
        } catch (cancelled: CancellationException) {
            health.update { if (state.value.any { entry -> entry.id == id }) it + (id to previous) else it - id }
            throw cancelled
        } finally { probeLock.unlock() }
    }
    suspend fun source(song: Song, quality: AudioQuality, id: String): AudioSource {
        ready.await()
        val source = lock.withLock { entries.firstOrNull { it.info.id == id } }
            ?: throw LxSourceException("请先在音源与网络中导入并选择落雪音源")
        val (request, url) = lxResolveTrack(song, quality, source.info.capabilities) { data ->
            runtime.resolve(source.info, source.script, data)
        }
        val level = when (request.quality) { "flac24bit" -> "hires"; "flac" -> "lossless"; "320k" -> "exhigh"; else -> "standard" }
        return AudioSource(url, level, 0, 0, if (request.quality.startsWith("flac")) "flac" else "mp3", validForSeconds = 30)
    }
    private fun save(value: List<LxStoredSource>) {
        vault.write(json.encodeToString(serializer, value))
        entries = value; state.value = value.map { it.info }
    }
    override fun close() = runtime.close()
}
