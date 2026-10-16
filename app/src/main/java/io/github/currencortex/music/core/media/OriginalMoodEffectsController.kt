package io.github.currencortex.music.core.media

import io.github.currencortex.music.core.netease.NeteaseSessionStore
import io.github.currencortex.music.data.effects.OriginalMoodEntitlement
import io.github.currencortex.music.data.effects.OriginalMoodModelRepository
import io.github.currencortex.music.data.settings.OriginalSmartPreferences
import io.github.currencortex.music.data.settings.OriginalMoodSettingsRepository
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


data class OriginalMoodState(
    val enabled: Boolean = false,
    val loading: Boolean = false,
    val message: String = "网易云原版AI 情绪音效已关闭",
    val processing: Boolean = false,
    val available: Boolean = false,
    val requestedEnabled: Boolean = false,
    val songId: Long? = null,
    val emotion: String? = null,
    val family: String? = null,
    /** Only original algorithm output belongs here; the UI never synthesizes song measurements. */
    val visualExtra: kotlinx.serialization.json.JsonObject? = null,
)

/** The dedicated 106/116 benefit gates the entire original model and preset pipeline. */
class OriginalMoodEffectsController internal constructor(
    accounts: Flow<OriginalSmartAccount>,
    private val ready: suspend () -> Unit,
    private val readSaved: suspend () -> OriginalSmartPreferences,
    private val writeSaved: suspend (OriginalSmartPreferences) -> Unit,
    private val entitlement: suspend () -> OriginalMoodEntitlement,
    private val prepareModel: suspend () -> java.io.File,
    private val descriptor: suspend (Long) -> kotlinx.serialization.json.JsonObject?,
    songs: Flow<Song?>,
    engineStatus: Flow<Boolean>,
    private val scope: CoroutineScope,
    private val beforeEnable: suspend () -> Unit,
    private val definition: () -> ByteArray?,
    private val now: () -> Long = System::currentTimeMillis,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : java.io.Closeable {
    constructor(repository: OriginalMoodModelRepository, sessions: NeteaseSessionStore,
        settings: OriginalMoodSettingsRepository, songs: Flow<Song?>, engineStatus: Flow<Boolean>,
        scope: CoroutineScope, beforeEnable: suspend () -> Unit, definition: () -> ByteArray?) : this(
        accounts = sessions.state.map { OriginalSmartAccount(it.profile?.uid ?: 0, it.revision, it.loggedIn, it.stale) },
        ready = { sessions.ready.await(); Unit }, readSaved = settings::snapshot, writeSaved = settings::set,
        entitlement = repository::entitlement, prepareModel = repository::prepare, descriptor = repository::descriptor,
        songs = songs, engineStatus = engineStatus, scope = scope, beforeEnable = beforeEnable, definition = definition)

    private data class Target(val id: Long, val source: MusicSource, val video: Boolean)
    private data class Context(val account: OriginalSmartAccount, val target: Target?, val engine: Boolean)
    private val mutableState = MutableStateFlow(OriginalMoodState())
    val state: StateFlow<OriginalMoodState> = mutableState.asStateFlow()
    private val mutableSelection = MutableStateFlow<NeteaseOriginalPreset?>(null)
    val selection: StateFlow<NeteaseOriginalPreset?> = mutableSelection.asStateFlow()
    private var requested = OriginalSmartPreferences()
    private var context: Context? = null
    private var generation = 0L
    private var pending: Job? = null
    private var expiry: Job? = null
    private var permissionExpiresAt = 0L
    private var closed = false
    private val persistence = Mutex()
    private var lastSavedGeneration = -1L
    private val startupGeneration = generation
    private val observer = scope.launch {
        ready()
        val saved = readSaved()
        if (closed) return@launch
        if (generation == startupGeneration) requested = saved
        combine(accounts.distinctUntilChanged(),
            songs.map { it?.let { song -> Target(song.id, song.musicSource, song.video) } }.distinctUntilChanged(),
            engineStatus.distinctUntilChanged()) { account, target, engine -> Context(account, target, engine) }
            .distinctUntilChanged().collect { next ->
                val old = context
                context = next
                if (requested.enabled && requested.ownerId > 0 && requested.ownerId != next.account.userId) {
                    revoke("账号已切换，请为当前网易云账号重新开启AI 情绪音效")
                } else if (old != next) evaluate()
            }
    }

    fun setEnabled(value: Boolean) {
        if (closed) return
        if (!value) { disable(); return }
        // A direct request binds to the current account only after live entitlement verification.
        requested = OriginalSmartPreferences(true, context?.account?.userId?.takeIf { it > 0 } ?: 0)
        evaluate()
    }

    fun disable() {
        if (closed) return
        requested = OriginalSmartPreferences()
        val token = invalidate()
        mutableState.value = OriginalMoodState(available = context?.engine ?: false)
        persistOff(token)
    }

    /** Retries an import or network failure without manufacturing a previous entitlement. */
    fun refresh() { if (!closed) evaluate() }

    private fun invalidate(): Long {
        val token = ++generation
        pending?.cancel(); pending = null
        expiry?.cancel(); expiry = null
        permissionExpiresAt = 0L
        mutableSelection.value = null
        return token
    }

    private fun evaluate() {
        val token = invalidate()
        val current = context
        mutableState.value = OriginalMoodState(available = current?.engine ?: false,
            requestedEnabled = requested.enabled, songId = current?.target?.id,
            loading = requested.enabled && current != null,
            message = if (requested.enabled) "正在验证网易云 AI 情绪专属权益" else "网易云原版AI 情绪音效已关闭")
        if (!requested.enabled || closed || current == null) return
        val account = current.account
        if (!account.loggedIn || account.stale || account.userId <= 0) {
            revoke("请先登录有效的网易云 AI 情绪专属账号")
            return
        }
        if (requested.ownerId != 0L && requested.ownerId != account.userId) {
            revoke("保存的AI 情绪音效不属于当前网易云账号")
            return
        }
        pending = scope.launch request@ {
            try {
                val right = entitlement()
                if (!isCurrent(token, current)) return@request
                if (right.userId != account.userId) { revoke("网易云权益账号校验未通过，请重新登录后再试"); return@request }
                if (!right.canUse || right.expiresAt <= now()) {
                    revoke("网易云原版AI 情绪音效需要有效AI 情绪专属权益")
                    return@request
                }
                requested = OriginalSmartPreferences(true, account.userId)
                persist(requested, token)
                if (!isCurrent(token, current)) return@request
                permissionExpiresAt = right.expiresAt
                expiry = scope.launch {
                    while (isCurrent(token, current)) {
                        val remaining = right.expiresAt - now()
                        if (remaining <= 0) { evaluate(); return@launch }
                        // Android delays use uptime, while server membership deadlines use wall time.
                        delay(minOf(remaining, 60_000L))
                    }
                }
                if (!current.engine) {
                    mutableState.value = state.value.copy(loading = false,
                        message = "已记住开启意图；导入兼容原版引擎后将重新验证权益")
                    return@request
                }
                val raw = withContext(workDispatcher) { definition()?.copyOf() }
                if (!isCurrent(token, current)) return@request
                if (raw == null) {
                    mutableState.value = state.value.copy(loading = false,
                        message = "原版AI 情绪音效资源尚未导入，当前继续使用手动音效")
                    return@request
                }
                val target = current.target
                if (target == null || target.id <= 0 || target.source != MusicSource.NETEASE || target.video) {
                    // Validate the default resource and enforce mutual exclusion even before playback.
                    withContext(workDispatcher) { OriginalMoodCodec(raw) }
                    if (!isCurrent(token, current)) return@request
                    beforeEnable()
                    if (!isCurrent(token, current)) return@request
                    if (right.expiresAt <= now()) { revoke("网易云 AI 情绪专属权益已过期，已恢复手动音效"); return@request }
                    mutableState.value = state.value.copy(enabled = true, loading = false,
                        message = "已开启，播放网易云歌曲时自动匹配原版音效")
                    return@request
                }
                val prepared = withContext(workDispatcher) {
                    val algorithm = OriginalMoodCodec(raw)
                    val row = descriptor(target.id) ?: kotlinx.serialization.json.buildJsonObject {
                        put("song_id", kotlinx.serialization.json.JsonPrimitive(target.id))
                    }
                    OriginalMoodModel(prepareModel()).use { model ->
                        val input = algorithm.prepare(row, model.inputs, model.outputs)
                        val output = if (input.infer) model.infer(input.emotion, input.genre, input.features, input.extra) else null
                        algorithm.finish(input, output)
                    }
                }
                if (!isCurrent(token, current)) return@request
                beforeEnable()
                if (!isCurrent(token, current)) return@request
                if (right.expiresAt <= now()) { revoke("网易云 AI 情绪权益已过期，已恢复手动音效"); return@request }
                mutableSelection.value = prepared.preset()
                mutableState.value = state.value.copy(enabled = true, loading = false, processing = false,
                    emotion = prepared.emotion, family = prepared.family, visualExtra = prepared.visualExtra,
                    message = if (prepared.inferred) "原版 AI 情绪模型已匹配，等待音频处理" else "特征不足，已匹配原版情绪模板")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: LinkageError) {
                if (isCurrent(token, current)) reportFailure("原版 AI 情绪引擎加载失败，请重新导入兼容安装包后重试")
            }
            catch (_: Exception) {
                if (isCurrent(token, current)) mutableState.value = state.value.copy(enabled = false, loading = false,
                    processing = false, message = "原版AI 情绪音效暂不可用，请检查网络或资源后重试；当前使用手动音效")
            }
        }
    }

    private fun isCurrent(token: Long, expected: Context): Boolean =
        !closed && token == generation && context == expected && requested.enabled

    private fun revoke(message: String) {
        requested = OriginalSmartPreferences()
        val token = invalidate()
        mutableState.value = OriginalMoodState(available = context?.engine ?: false,
            songId = context?.target?.id, message = message)
        persistOff(token)
    }

    private fun persistOff(token: Long) {
        scope.launch {
            try { persist(OriginalSmartPreferences(), token) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (!closed && token == generation) mutableState.value = state.value.copy(
                    message = "原版AI 情绪音效已关闭，但设置保存失败，请稍后重试")
            }
        }
    }

    private suspend fun persist(value: OriginalSmartPreferences, token: Long) = persistence.withLock {
        if ((!closed && token == generation) || (!value.enabled && token >= lastSavedGeneration)) {
            writeSaved(value)
            lastSavedGeneration = token
        }
    }

    /** Acknowledged only after the audio processor successfully produces native PCM. */
    fun reportProcessing(presetId: Long) {
        if (closed || presetId != ORIGINAL_MOOD_PRESET_ID || selection.value?.id != presetId || !state.value.enabled) return
        if (permissionExpiresAt <= now()) evaluate()
        else mutableState.value = state.value.copy(processing = true, message = "网易云原版AI 情绪音效正在处理音频")
    }

    /** A native failure withdraws output but keeps the user's account-bound retry intention. */
    fun reportFailure(message: String) {
        if (closed) return
        invalidate()
        mutableState.value = state.value.copy(enabled = false, loading = false, processing = false,
            requestedEnabled = requested.enabled, emotion = null, family = null, visualExtra = null,
            message = message.take(200).ifBlank { "原版AI 情绪音效处理失败，当前使用手动音效；可重试" })
    }

    override fun close() {
        if (closed) return
        invalidate(); closed = true; observer.cancel()
        mutableState.value = state.value.copy(enabled = false, loading = false, processing = false)
        // Container disposal is not a user request to erase an account-bound saved intent.
    }

}
