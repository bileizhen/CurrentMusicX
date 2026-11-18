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
    internal val health = MutableStateFlow<Map<Pair<LxPlatform, String>, LxSourceHealth>>(emptyMap())
    internal val resolveTimes = MutableStateFlow<Map<Pair<LxPlatform, String>, Long>>(emptyMap())
    val ready = CompletableDeferred<Unit>()
    private val lock = Mutex()
    private val probeLock = Mutex()
    private val resolveLock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(LxStoredSource.serializer())
    private var entries: List<LxStoredSource> = emptyList()
    init { scope.launch(Dispatchers.IO) {
        try {
            entries = vault.read()?.let { json.decodeFromString(serializer, it) }.orEmpty()
                .filter { it.script.toByteArray().size <= LX_MAX_SCRIPT_BYTES && lxScriptInfo(it.script).id == it.info.id }.take(12)
                .map { it.copy(info = it.info.copy(capabilities = lxNormalizeCapabilities(it.info.capabilities))) }
            state.value = entries.map { it.info }
            publishTimes()
            ready.complete(Unit)
        } catch (cancelled: CancellationException) { ready.cancel(cancelled); throw cancelled }
        catch (_: Exception) { ready.completeExceptionally(LxSourceException("落雪音源列表读取失败，请重新导入")) }
    } }
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
            entries.firstOrNull { it.info.id == info.id }?.let { return@withLock it.info }
            require(entries.size < 12 && entries.sumOf { it.script.toByteArray().size } + script.toByteArray().size <= 8 * LX_MAX_SCRIPT_BYTES) {
                "音源数量或总大小已达上限，请先移除不使用的音源"
            }
            // The source library can retain a script while its runtime or upstream service is unavailable.
            save(entries + LxStoredSource(info, script))
            info
        }
    }
    suspend fun remove(id: String) {
        ready.await()
        withContext(Dispatchers.IO) { lock.withLock { save(entries.filterNot { it.info.id == id }) } }
        runtime.closeSafely()
        health.update { current -> current.filterKeys { it.second != id } }
    }

    /** Called by the visible settings page; no timer or background polling. */
    internal suspend fun checkAvailability(id: String, platform: LxPlatform) {
        ready.await()
        val key = platform to id
        probeLock.withLock {
            val previous = health.value[key] ?: LxSourceHealth()
            if (state.value.none { it.id == id } || previous.fresh(android.os.SystemClock.elapsedRealtime())) return
            health.update { it + (key to previous.copy(status = SourceAvailabilityStatus.CHECKING)) }
            try {
                val result = probe(id, platform)
                if (state.value.any { it.id == id }) health.update { it + (key to result) }
            } catch (cancelled: CancellationException) {
                health.update { if (state.value.any { entry -> entry.id == id }) it + (key to previous) else it - key }
                throw cancelled
            }
        }
    }

    private suspend fun probe(id: String, platform: LxPlatform): LxSourceHealth {
        fun result(status: SourceAvailabilityStatus, message: String) =
            LxSourceHealth(status, android.os.SystemClock.elapsedRealtime(), message)
        return try {
            withTimeout(60_000) {
                val stored = lock.withLock { entries.firstOrNull { it.info.id == id } }
                    ?: return@withTimeout LxSourceHealth()
                val selected = refreshCapabilities(stored)
                if (selected.info.qualities(platform).isEmpty()) {
                    return@withTimeout result(SourceAvailabilityStatus.UNAVAILABLE, "此音源未声明${platform.label}播放接口")
                }
                val (audio, elapsedMs) = resolveLock.withLock {
                    val started = android.os.SystemClock.elapsedRealtime()
                    val audio = resolveSource(lxProbeSong(platform), AudioQuality.STANDARD, selected)
                    audio to (android.os.SystemClock.elapsedRealtime() - started)
                }
                val status = http.probeAudio(audio.url)
                if (status == SourceAvailabilityStatus.AVAILABLE) recordPerformance(id, platform, elapsedMs)
                result(status, when (status) {
                    SourceAvailabilityStatus.AVAILABLE -> "已解析并读取到音频数据"
                    SourceAvailabilityStatus.LIMITED -> "音频地址返回限流响应"
                    SourceAvailabilityStatus.TIMEOUT -> "音频地址响应超时"
                    else -> "返回的音频地址不可用或不是音频数据"
                })
            }
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            result(SourceAvailabilityStatus.TIMEOUT, "检测超时")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: java.net.SocketTimeoutException) {
            result(SourceAvailabilityStatus.TIMEOUT, "网络响应超时")
        } catch (error: LxSourceException) {
            when {
                error.message?.contains("超时") == true -> result(SourceAvailabilityStatus.TIMEOUT, error.message.orEmpty())
                error.qualityUnavailable -> result(SourceAvailabilityStatus.UNAVAILABLE, "音源未能解析${platform.label}测试歌曲")
                else -> result(SourceAvailabilityStatus.UNAVAILABLE, error.message ?: "音源检测失败")
            }
        } catch (_: Exception) {
            result(SourceAvailabilityStatus.UNAVAILABLE, "音源解析或网络连接失败")
        }
    }
    suspend fun source(song: Song, quality: AudioQuality, id: String, smartSwitch: Boolean = false,
        excludeIds: Set<String> = emptySet()): AudioSource {
        ready.await()
        val platform = LxPlatform.from(song)
        // Exclude time queued behind another playback/probe from the performance measurement.
        return resolveLock.withLock {
            val candidates = lock.withLock {
                if (smartSwitch) lxOrderedSources(entries, id, System.currentTimeMillis(), platform)
                else listOf(entries.firstOrNull { it.info.id == id }
                    ?: throw LxSourceException("请先在音源与网络中导入并选择落雪音源"))
            }.filterNot { it.info.id in excludeIds }
            if (candidates.isEmpty()) throw LxSourceException("没有其他可尝试的落雪音源")
            lxTrySources(candidates) { candidate ->
                val current = refreshCapabilities(candidate)
                if (current.info.qualities(platform).isEmpty())
                    throw LxSourceException("此音源未声明${platform.label}播放接口，请切换音源")
                val started = android.os.SystemClock.elapsedRealtime()
                try {
                    val audio = if (smartSwitch) {
                        try { withTimeout(12_000) { resolveSource(song, quality, current) } }
                        catch (_: TimeoutCancellationException) {
                            currentCoroutineContext().ensureActive()
                            throw LxSourceException("落雪音源解析超时，请重试或更换音源")
                        }
                    } else resolveSource(song, quality, current)
                    recordPerformance(current.info.id, platform, android.os.SystemClock.elapsedRealtime() - started)
                    audio
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: LxSourceException) {
                    recordPerformance(current.info.id, platform, null)
                    throw error
                }
            }
        }
    }
    internal suspend fun reportPlaybackFailure(id: String, platform: LxPlatform = LxPlatform.NETEASE) =
        recordPerformance(id, platform, null)
    private suspend fun refreshCapabilities(source: LxStoredSource): LxStoredSource {
        val id = source.info.id
        lock.withLock {
            val latest = entries.firstOrNull { it.info.id == id }
                ?: throw LxSourceException("该音源已移除")
            if (latest.capabilitiesChecked && latest.info.capabilityVersion >= LX_CAPABILITY_VERSION) return latest
        }
        val capabilities = runtime.inspect(source.info, source.script)
        return lock.withLock {
            val latest = entries.firstOrNull { it.info.id == id }
                ?: throw LxSourceException("该音源已移除")
            if (latest.capabilitiesChecked && latest.info.capabilityVersion >= LX_CAPABILITY_VERSION) return@withLock latest
            val updated = latest.copy(
                info = latest.info.copy(capabilities = capabilities, capabilityVersion = LX_CAPABILITY_VERSION),
                capabilitiesChecked = true,
            )
            save(entries.map { if (it.info.id == id) updated else it })
            updated
        }
    }
    private suspend fun resolveSource(song: Song, quality: AudioQuality, source: LxStoredSource): AudioSource {
        val (request, url) = lxResolveTrack(song, quality, source.info.capabilities) { data ->
            runtime.resolve(source.info, source.script, data)
        }
        return AudioSource(url, lxQualityLevel(LxPlatform.from(song), request.quality), 0, 0,
            lxQualityFormat(request.quality),
            validForSeconds = 30, source = "lx:${source.info.id}")
    }
    private suspend fun recordPerformance(id: String, platform: LxPlatform, elapsedMs: Long?) = withContext(Dispatchers.IO) {
        lock.withLock {
            val source = entries.firstOrNull { it.info.id == id } ?: return@withLock
            val now = System.currentTimeMillis()
            val performance = if (elapsedMs == null) source.performanceFor(platform).copy(failedAtMs = now)
                else LxSourcePerformance(elapsedMs.coerceAtLeast(0), now)
            val measured = if (platform == LxPlatform.QQ_MUSIC) source.copy(qqPerformance = performance)
                else source.copy(performance = performance)
            val updated = entries.map { if (it.info.id == id) measured else it }
            // A statistics write failure must not discard a usable playback URL.
            try { vault.write(json.encodeToString(serializer, updated)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { }
            entries = updated
            publishTimes()
        }
    }
    private fun publishTimes() {
        val now = System.currentTimeMillis()
        resolveTimes.value = buildMap {
            for (source in this@LxSourceStore.entries) for (platform in LxPlatform.entries) {
                source.performanceFor(platform).recentTime(now)?.let { put(platform to source.info.id, it) }
            }
        }
    }
    private fun save(value: List<LxStoredSource>) {
        vault.write(json.encodeToString(serializer, value))
        entries = value; state.value = value.map { it.info }
        publishTimes()
    }
    override fun close() = runtime.close()
}
