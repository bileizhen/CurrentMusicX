package io.github.currencortex.music.core.media

import io.github.currencortex.music.core.netease.NeteaseSessionStore
import io.github.currencortex.music.data.effects.OriginalMoodEntitlement
import io.github.currencortex.music.data.effects.OriginalAiMasterRepository
import io.github.currencortex.music.data.settings.OriginalSmartPreferences
import io.github.currencortex.music.data.settings.OriginalAiMasterSettingsRepository
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


data class OriginalAiMasterState(
    val enabled: Boolean = false,
    val loading: Boolean = false,
    val message: String = "网易云原版 AI 调音大师已关闭",
    val processing: Boolean = false,
    val available: Boolean = false,
    val requestedEnabled: Boolean = false,
    val songId: Long? = null,
    val description: String? = null,
    val baseName: String? = null,
    val longText: String? = null,
    val selectedDiy: Set<String> = emptySet(),
    val usedDiy: Set<String> = emptySet(),
    val diySaving: Boolean = false,
)

/** Original type-9 SVIP gate, account-bound cancellation and true native-PCM acknowledgement. */
class OriginalAiMasterEffectsController internal constructor(
    accounts: Flow<OriginalSmartAccount>,
    private val ready: suspend () -> Unit,
    private val readSaved: suspend () -> OriginalSmartPreferences,
    private val writeSaved: suspend (OriginalSmartPreferences) -> Unit,
    private val entitlement: suspend () -> OriginalMoodEntitlement,
    private val prepare: suspend (Long) -> io.github.currencortex.music.data.effects.OriginalAiMasterMaterial,
    songs: Flow<Song?>,
    engineStatus: Flow<Boolean>,
    private val scope: CoroutineScope,
    private val beforeEnable: suspend () -> Unit,
    private val definition: () -> ByteArray?,
    private val now: () -> Long = System::currentTimeMillis,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    supportStereo: Flow<Boolean> = flowOf(false),
    private val readDiy: suspend (Long) -> Set<String> = { emptySet() },
    private val writeDiy: suspend (Long, Set<String>) -> Unit = { _, _ -> },
) : java.io.Closeable {
    constructor(repository: OriginalAiMasterRepository, sessions: NeteaseSessionStore,
        settings: OriginalAiMasterSettingsRepository, songs: Flow<Song?>, engineStatus: Flow<Boolean>,
        scope: CoroutineScope, beforeEnable: suspend () -> Unit, definition: () -> ByteArray?,
        supportStereo: Flow<Boolean> = flowOf(false)) : this(
        accounts = sessions.state.map { OriginalSmartAccount(it.profile?.uid ?: 0, it.revision, it.loggedIn, it.stale) },
        ready = { sessions.ready.await(); Unit }, readSaved = settings::snapshot, writeSaved = settings::set,
        entitlement = repository::entitlement, prepare = repository::prepare,
        songs = songs, engineStatus = engineStatus, scope = scope, beforeEnable = beforeEnable,
        definition = definition, supportStereo = supportStereo,
        readDiy = settings::diyFor, writeDiy = settings::setDiy)

    private data class Target(val id: Long, val source: MusicSource, val video: Boolean)
    private data class Context(val account: OriginalSmartAccount, val target: Target?, val engine: Boolean, val stereo: Boolean)
    private val mutableState = MutableStateFlow(OriginalAiMasterState())
    val state: StateFlow<OriginalAiMasterState> = mutableState.asStateFlow()
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
    private var selectedDiy: Set<String> = emptySet()
    private var diyOwner = 0L
    private var diyGeneration = 0L
    private var diySaving = false
    private val diyPersistence = Mutex()
    private var lastMaterial: Pair<Context, io.github.currencortex.music.data.effects.OriginalAiMasterMaterial>? = null
    private val startupGeneration = generation
    private val observer = scope.launch {
        ready()
        val saved = readSaved()
        if (closed) return@launch
        if (generation == startupGeneration) requested = saved
        combine(accounts.distinctUntilChanged(),
            songs.map { it?.let { song -> Target(song.id, song.musicSource, song.video) } }.distinctUntilChanged(),
            engineStatus.distinctUntilChanged(), supportStereo.distinctUntilChanged()) { account, target, engine, stereo ->
            Context(account, target, engine, stereo) }
            .distinctUntilChanged().collectLatest { next ->
                val old = context
                context = next
                if (requested.enabled && requested.ownerId > 0 && requested.ownerId != next.account.userId) {
                    revoke("账号已切换，请为当前网易云账号重新开启AI 调音大师音效")
                }
                if (diyOwner != next.account.userId) {
                    invalidate()
                    val diyToken = ++diyGeneration
                    selectedDiy = emptySet(); diySaving = false; lastMaterial = null
                    mutableState.value = OriginalAiMasterState(available = next.engine, songId = next.target?.id)
                    val uid = next.account.userId
                    val stored = if (uid > 0) diyPersistence.withLock { readDiy(uid) } else emptySet()
                    if (closed || context != next || diyToken != diyGeneration) return@collectLatest
                    diyOwner = uid
                    selectedDiy = stored.filterTo(linkedSetOf()) { it in OriginalAiMasterCodec.DIY_KEYS }
                }
                if (old != next) evaluate()
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
        mutableState.value = OriginalAiMasterState(available = context?.engine ?: false,
            selectedDiy = selectedDiy, diySaving = diySaving)
        persistOff(token)
    }

    /** Save the actual account's five preferences and recompute with the original closing/merge rules. */
    fun setDiy(selected: Set<String>) {
        require(OriginalAiMasterCodec.DIY_KEYS.containsAll(selected))
        if (closed) return
        val account = context?.account ?: return
        if (!account.loggedIn || account.stale || account.userId <= 0) return
        val uid = account.userId
        val snapshot = selected.toSet()
        val token = ++diyGeneration
        diyOwner = uid; selectedDiy = snapshot; diySaving = true
        evaluate()
        scope.launch {
            try {
                diyPersistence.withLock {
                    if (closed || token != diyGeneration || context?.account?.userId != uid) return@withLock
                    writeDiy(uid, snapshot)
                }
                if (closed || token != diyGeneration || context?.account?.userId != uid) return@launch
                diySaving = false
                mutableState.value = state.value.copy(diySaving = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (!closed && token == diyGeneration && context?.account?.userId == uid) {
                    diySaving = false
                    mutableState.value = state.value.copy(diySaving = false,
                        message = "DIY 偏好已应用，但保存失败，请重试")
                }
            }
        }
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
        mutableState.value = OriginalAiMasterState(available = current?.engine ?: false,
            requestedEnabled = requested.enabled, songId = current?.target?.id,
            selectedDiy = selectedDiy, diySaving = diySaving,
            loading = requested.enabled && current != null,
            message = if (requested.enabled) "正在验证网易云 SVIP 权益" else "网易云原版 AI 调音大师已关闭")
        if (!requested.enabled || closed || current == null) return
        val account = current.account
        if (!account.loggedIn || account.stale || account.userId <= 0) {
            revoke("请先登录有效的网易云 AI 调音大师SVIP 账号")
            return
        }
        if (requested.ownerId != 0L && requested.ownerId != account.userId) {
            revoke("保存的AI 调音大师音效不属于当前网易云账号")
            return
        }
        pending = scope.launch request@ {
            try {
                val right = entitlement()
                if (!isCurrent(token, current)) return@request
                if (right.userId != account.userId) { revoke("网易云权益账号校验未通过，请重新登录后再试"); return@request }
                if (!right.canUse || right.expiresAt <= now()) {
                    revoke("网易云原版 AI 调音大师需要有效SVIP 权益")
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
                        message = "原版 AI 调音大师资源尚未导入，当前继续使用手动音效")
                    return@request
                }
                val target = current.target
                if (target == null || target.id <= 0 || target.source != MusicSource.NETEASE || target.video) {
                    // Validate the default resource and enforce mutual exclusion even before playback.
                    withContext(workDispatcher) { OriginalAiMasterCodec(raw) }
                    if (!isCurrent(token, current)) return@request
                    beforeEnable()
                    if (!isCurrent(token, current)) return@request
                    if (right.expiresAt <= now()) { revoke("网易云 SVIP 权益已过期，已恢复手动音效"); return@request }
                    mutableState.value = state.value.copy(enabled = true, loading = false,
                        message = "已开启，播放网易云歌曲时自动匹配原版音效")
                    return@request
                }
                val material = lastMaterial?.takeIf { it.first == current }?.second ?: prepare(target.id)
                if (!isCurrent(token, current)) return@request
                lastMaterial = current to material
                val prepared = withContext(workDispatcher) {
                    OriginalAiMasterCodec(raw).compose(material.base, material.diy, material.baseKey, material.song,
                        selected = selectedDiy, supportStereo = current.stereo)
                }
                if (!isCurrent(token, current)) return@request
                beforeEnable()
                if (!isCurrent(token, current)) return@request
                if (right.expiresAt <= now()) { revoke("网易云 AI 调音大师权益已过期，已恢复手动音效"); return@request }
                mutableSelection.value = prepared.preset()
                mutableState.value = state.value.copy(enabled = true, loading = false, processing = false,
                    description = material.description, baseName = material.baseName, longText = material.longText,
                    selectedDiy = selectedDiy, usedDiy = prepared.usedDiy.toSet(), diySaving = diySaving,
                    message = if (prepared.songSpecific) "原版 AI 调音大师已匹配，等待音频处理" else "已匹配原版调音基础模板，等待音频处理")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: LinkageError) {
                if (isCurrent(token, current)) reportFailure("原版 AI 调音大师引擎加载失败，请重新导入兼容安装包后重试")
            }
            catch (_: Exception) {
                if (isCurrent(token, current)) mutableState.value = state.value.copy(enabled = false, loading = false,
                    processing = false, message = "原版 AI 调音大师暂不可用，请检查网络或资源后重试；当前使用手动音效")
            }
        }
    }

    private fun isCurrent(token: Long, expected: Context): Boolean =
        !closed && token == generation && context == expected && requested.enabled

    private fun revoke(message: String) {
        requested = OriginalSmartPreferences()
        val token = invalidate()
        mutableState.value = OriginalAiMasterState(available = context?.engine ?: false,
            songId = context?.target?.id, selectedDiy = selectedDiy, diySaving = diySaving, message = message)
        persistOff(token)
    }

    private fun persistOff(token: Long) {
        scope.launch {
            try { persist(OriginalSmartPreferences(), token) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (!closed && token == generation) mutableState.value = state.value.copy(
                    message = "原版 AI 调音大师已关闭，但设置保存失败，请稍后重试")
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
        if (closed || presetId != ORIGINAL_AI_MASTER_PRESET_ID || selection.value?.id != presetId || !state.value.enabled) return
        if (permissionExpiresAt <= now()) evaluate()
        else mutableState.value = state.value.copy(processing = true, message = "网易云原版 AI 调音大师正在处理音频")
    }

    /** A native failure withdraws output but keeps the user's account-bound retry intention. */
    fun reportFailure(message: String) {
        if (closed) return
        invalidate()
        mutableState.value = state.value.copy(enabled = false, loading = false, processing = false,
            requestedEnabled = requested.enabled, description = null, baseName = null, longText = null,
            usedDiy = emptySet(),
            message = message.take(200).ifBlank { "原版 AI 调音大师处理失败，当前使用手动音效；可重试" })
    }

    override fun close() {
        if (closed) return
        invalidate(); closed = true; diyGeneration++; observer.cancel()
        mutableState.value = state.value.copy(enabled = false, loading = false, processing = false)
        // Container disposal is not a user request to erase an account-bound saved intent.
    }

}
