package io.github.currencortex.music.core.room

import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.room.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RoomSessionState(val detail: RoomDetail? = null, val connecting: Boolean = false,
    val connected: Boolean = false, val error: String? = null, val driftMs: Long = 0,
    val refreshing: Boolean = false, val pendingActions: Set<String> = emptySet())

class RoomSession(private val repo: RoomRepository, private val sse: RoomSseClient,
    private val player: ExternalPlayer, private val scope: CoroutineScope,
    private val userId: () -> Long, private val expire: (RequestSession) -> Unit,
    private val monotonic: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val recoveryIntervalMs: Long = 10_000) : ExternalPlayback {
    val state = MutableStateFlow(RoomSessionState())
    private var session: RequestSession? = null
    private var activeId: String? = null
    private var job: Job? = null
    private val clock = RoomSynchronizer()
    private val clockMutex = Mutex()
    private val refreshMutex = Mutex()
    private val commandMutex = Mutex()
    private var generation = 0L
    private var seq = 0L
    private var lastId: String? = null
    private var loaded: Triple<Long, String, Long?>? = null
    private var scheduledStart: Job? = null
    private data class AdvanceState(val track: Long, val revision: Long?, val position: Long, val anchor: Long, val playing: Boolean)
    private fun RoomTimeline.advanceState() = AdvanceState(trackNcmId, trackRevision, basePosition, baseAt, playing)
    private var advanced: AdvanceState? = null
    private var advanceIntent = 0L
    private var advanceRetry: Job? = null
    private var lastRenew = Long.MIN_VALUE
    private fun RoomTimeline.recoveryIdentity() = trackNcmId to trackRevision
    private var recoveryTrack: Pair<Long, Long?>? = null
    private var recoveryJob: Job? = null
    private var recoveryRevision = 0L
    private var lastRecovery = Long.MIN_VALUE
    private val role get() = state.value.detail?.role(userId())
    val active get() = activeId != null
    fun requireCanJoin() {
        check(!active && player.state.value.mode == PlayerMode.LOCAL) { "请先退出一起听" }
    }
    suspend fun join(info: RoomInfo, password: String = "", created: Boolean = false, expected: RequestSession = repo.session()) {
        requireCanJoin()
        if (expected != repo.session()) throw ApiException(ErrorKind.Unauthorized)
        if (!created) repo.action(info.id, "join", buildJsonObject { put("password", password) }, expected)
        try {
            if (expected != repo.session()) throw ApiException(ErrorKind.Unauthorized)
            val detail = repo.detail(info.id, expected)
            check(detail.role(userId()) != null) { "房间成员信息尚未更新，请重新进入" }
            activeId = info.id; session = expected; seq = detail.latestSeq; lastId = null; loaded = null; advanced = null; lastRenew = Long.MIN_VALUE; clock.reset()
            state.value = RoomSessionState(detail, connecting = true)
            check(player.beginExternal(PlayerMode.ROOM, this)) { "无法切换播放器模式" }
            player.roomTrack(null, null, 0, false)
            publishPermission()
            job = scope.launch {
                try {
                coroutineScope {
                    calibrate(info.id, expected)
                    applyTimeline()
                    launch { while (isActive) { delay(15_000); val result = appResult { repo.action(info.id, "heartbeat", expected = expected) }
                        if (result is AppResult.Failure) state.update { it.copy(error = "房间心跳失败：${result.kind.message}") } } }
                    launch { while (isActive) { delay(state.value.detail?.timeline?.let { clock.startsIn(it, monotonic()).takeIf { wait -> wait > 0 }?.coerceAtMost(1000) } ?: 1000); when(val result = appResult { align() }) {
                        is AppResult.Failure -> state.update { it.copy(error = "同步播放失败：${result.kind.message}") }; else -> Unit } } }
                    launch { while (isActive) { delay(60_000); calibrate(info.id, expected) } }
                    var backoff = 1000L
                    while (isActive && expected == repo.session()) {
                        try {
                            sse.events(info.id, seq, lastId, expected).collect { signal ->
                                when (signal) {
                                    RoomSignal.Open -> {
                                        state.update { it.copy(connected = true, connecting = false, error = null) }
                                        refresh(); calibrate(info.id, expected)
                                    }
                                    is RoomSignal.Event -> {
                                        val raw = runCatching { ApiJson.parseToJsonElement(signal.data) as? JsonObject }.getOrNull() ?: return@collect
                                        // reset may carry the same/older sequence as our cursor.
                                        if (signal.type == "reset") { refresh(reset = true); return@collect }
                                        val next = raw["seq"]?.jsonPrimitive?.longOrNull
                                        if (next != null && next <= seq) return@collect
                                        val payload = (raw["payload"] as? JsonObject) ?: raw
                                        // Decode before acknowledging the event so a malformed timeline can be replayed.
                                        val timeline = payload["timeline"]?.takeIf { it != JsonNull }?.let {
                                            ApiJson.decodeFromJsonElement<RoomTimeline>(it)
                                        }
                                        if (next != null) seq = next
                                        lastId = next?.toString() ?: signal.id ?: lastId
                                        backoff = 1000
                                        if (signal.type == "close" || signal.type == "kick" && payload["userId"]?.jsonPrimitive?.longOrNull == userId()) {
                                            disconnect("已离开房间"); return@collect
                                        }
                                        timeline?.let { updated ->
                                            state.update { it.copy(detail = it.detail?.copy(timeline = updated, latestSeq = seq)) }; applyTimeline()
                                        }
                                        if (signal.type in setOf("join", "leave", "queue", "settings", "role", "kick", "transfer", "track", "next", "prev")) refresh()
                                    }
                                }
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            if (e is ApiException && e.status in setOf(401, 403, 404)) {
                                if (e.status == 401) expire(expected)
                                disconnect("房间不可访问：${e.kind.message}"); return@coroutineScope
                            }
                            state.update { it.copy(connected = false, connecting = true, error = "连接中断，正在重连…") }
                            delay(backoff); backoff = (backoff * 2).coerceAtMost(30_000)
                        }
                    }
                }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { disconnect("房间同步失败，请重新加入") }
            }
        } catch (e: Exception) {
            withContext(NonCancellable) { appResult { repo.action(info.id, "leave", expected = expected) } }
            disconnect(); throw e
        }
    }
    private suspend fun calibrate(id: String, expected: RequestSession) = clockMutex.withLock {
        val samples = RoomSynchronizer()
        repeat(5) {
            val sent = monotonic()
            when (val sample = appResult { repo.sync(id, expected) }) {
                is AppResult.Success -> samples.sample(sample.value, sent, monotonic())
                is AppResult.Failure -> Unit
            }
        }
        if (activeId == id && session == expected) clock.replaceWith(samples)
    }
    suspend fun refresh(reset: Boolean = false, reloadTrack: Pair<Long, Long?>? = null) = refreshMutex.withLock {
        val id = activeId ?: return@withLock; val expected = session ?: return@withLock
        val detail = repo.detail(id, expected)
        if (activeId != id || expected != session || expected != repo.session()) return@withLock
        if (!reset && detail.latestSeq < seq) {
            if (reloadTrack != null && state.value.detail?.timeline?.recoveryIdentity() == reloadTrack) {
                loaded = null; applyTimeline()
            }
            return@withLock
        }
        if (detail.room.archived) { disconnect("房间已归档，可从我的房间恢复"); return@withLock }
        if (detail.role(userId()) == null) { disconnect("你已不在此房间"); return@withLock }
        seq = detail.latestSeq
        lastId = seq.toString()
        if (reloadTrack != null && detail.timeline?.recoveryIdentity() == reloadTrack) loaded = null
        state.update { it.copy(detail = detail) }; applyTimeline()
    }
    fun dismissError() { state.update { it.copy(error = null) } }
    fun refreshWithFeedback() {
        if (state.value.refreshing || !active) return
        val epoch = generation
        state.update { it.copy(refreshing = true) }
        scope.launch {
            try {
                when (val result = appResult { refresh() }) {
                    is AppResult.Success -> if (epoch == generation) dismissError()
                    is AppResult.Failure -> if (epoch == generation) state.update { it.copy(error = "房间刷新失败：${result.kind.message}") }
                }
            } finally { if (epoch == generation) state.update { it.copy(refreshing = false) } }
        }
    }
    private suspend fun applyTimeline() {
        publishPermission()
        val timeline = state.value.detail?.timeline
        if (recoveryTrack != null && timeline?.recoveryIdentity() != recoveryTrack) {
            recoveryTrack = null
            val previous = recoveryJob; recoveryJob = null
            // A recovery fetch can discover a new track; let that fetch finish applying it.
            if (currentCoroutineContext()[Job] !== previous) previous?.cancel()
        }
        val url = timeline?.stream?.url?.takeIf { it.toHttpUrlOrNull() != null }
        val key = if (timeline != null && timeline.trackNcmId > 0 && url != null) Triple(timeline.trackNcmId, url, timeline.trackRevision) else null
        if (key != loaded) {
            loaded = key
            player.roomTrack(key?.let { timeline!!.trackMeta?.song(it.first) ?: Song(it.first, "房间歌曲") }, key?.second,
                timeline?.let { target(it) } ?: 0, timeline?.let { clock.shouldPlay(it, monotonic()) } == true)
            publishPermission()
        }
        scheduledStart?.cancel()
        val wait = timeline?.let { clock.startsIn(it, monotonic()) } ?: 0
        if (wait > 0) {
            val epoch = generation
            scheduledStart = scope.launch {
                delay(wait)
                if (epoch == generation && state.value.detail?.timeline == timeline) {
                    when (val result = appResult { align() }) {
                        is AppResult.Failure -> if (epoch == generation) state.update { it.copy(error = "同步播放失败：${result.kind.message}") }
                        else -> Unit
                    }
                }
            }
        }
        align()
    }
    private fun publishPermission() {
        if (player.state.value.mode != PlayerMode.ROOM || player.external !== this) return
        player.state.update { it.copy(canControlPlayback = role?.controls == true) }
    }
    private fun target(tl: RoomTimeline) = if (clock.ready) clock.position(tl, monotonic()) else tl.basePosition
    private suspend fun align() {
        val tl = state.value.detail?.timeline ?: return
        if (clock.ready && tl.trackNcmId > 0 && (tl.stream?.url.isNullOrBlank() ||
            (tl.stream?.expireAt ?: 0) > 0 && tl.stream!!.expireAt - clock.serverNow(monotonic()) <= 60_000)) renewStream()
        if (!clock.ready || loaded == null || player.state.value.loading) return
        val correction = clock.correction(tl, monotonic(), player.state.value.positionMs)
        state.update { it.copy(driftMs = correction.position - player.state.value.positionMs) }
        player.roomCorrection(correction.position, correction.seek, correction.speed, clock.shouldPlay(tl, monotonic()))
    }
    fun action(action: String, fields: JsonObject = buildJsonObject {}, permission: (RoomRole?) -> Boolean = { it?.controls == true }) {
        val id = activeId ?: return; val expected = session ?: return
        if (!permission(role)) { state.update { it.copy(error = "没有操作权限") }; return }
        if (action in setOf("play", "pause", "seek")) cancelAdvanceRetry()
        command(action, id, expected, { permission(role) }) {
            repo.action(id, action, fields, expected)
            if (action == "close") disconnect("房间已归档，可从我的房间恢复")
        }
    }
    fun queueAction(item: RoomQueueItem, action: String) {
        val id = activeId ?: return; val expected = session ?: return
        if (if (action == "remove") !RoomPermissions.remove(role, item) else role?.controls != true) {
            state.update { it.copy(error = "没有操作权限") }; return
        }
        command("queue:${item.id}", id, expected,
            { if (action == "remove") RoomPermissions.remove(role, item) else role?.controls == true }) {
            repo.queueAction(id, item.id, action, expected)
        }
    }
    fun reorder(item: RoomQueueItem, index: Int) {
        val id = activeId ?: return; val expected = session ?: return
        fun permitted() = role?.controls == true && state.value.detail?.queue?.any {
            it.id == item.id && it.status == "approved"
        } == true
        if (!permitted()) return
        command("queue:${item.id}", id, expected, ::permitted) { repo.reorder(id, item.id, index, expected) }
    }
    private fun command(key: String, id: String, expected: RequestSession, permitted: () -> Boolean,
        onFailure: (() -> Unit)? = null, write: suspend () -> Unit) {
        if (key in state.value.pendingActions) return
        val epoch = generation
        fun valid() = generation == epoch && activeId == id && session == expected && repo.session() == expected
        state.update { it.copy(pendingActions = it.pendingActions + key, error = null) }
        scope.launch {
            try {
                val result = appResult { commandMutex.withLock {
                    if (!valid()) throw ApiException(ErrorKind.Unauthorized)
                    if (!permitted()) throw ApiException(ErrorKind.Forbidden)
                    write()
                } }
                if (!valid()) return@launch
                if (result is AppResult.Failure) {
                    state.update { it.copy(error = result.kind.message) }
                    onFailure?.invoke()
                }
                else if (appResult { refresh() } is AppResult.Failure && valid())
                    state.update { it.copy(error = "操作已提交，房间刷新失败，请刷新查看") }
            } finally { if (valid()) state.update { it.copy(pendingActions = it.pendingActions - key) } }
        }
    }
    suspend fun submit(song: Song): AppResult<Unit> {
        val id = activeId ?: return AppResult.Failure(ErrorKind.Forbidden)
        val expected = session ?: return AppResult.Failure(ErrorKind.Unauthorized)
        val result = appResult { repo.add(id, song, expected) }
        if (result is AppResult.Failure && activeId == id && expected == session) state.update { it.copy(error = result.kind.message) }
        // A successful write stays successful even if fetching the new queue fails.
        if (result is AppResult.Success && activeId == id && expected == session) {
            scope.launch {
                val updated = appResult { refresh() }
                if (updated is AppResult.Failure && activeId == id && expected == session)
                    state.update { it.copy(error = "点歌已提交，队列刷新失败：${updated.kind.message}") }
            }
        }
        return result
    }
    override fun request(song: Song) { scope.launch { submit(song) } }
    override fun play(playing: Boolean) = action(if (playing) "play" else "pause", buildJsonObject { if (playing) put("position", player.state.value.positionMs) })
    override fun seek(position: Long) = action("seek", buildJsonObject { put("position", position) })
    private fun cancelAdvanceRetry() {
        advanceIntent++
        advanceRetry?.cancel(); advanceRetry = null
    }
    private fun changeTrack(action: String, automatic: Boolean = false) {
        val id = activeId ?: return; val expected = session ?: return
        val timeline = state.value.detail?.timeline
        if (role?.controls != true) { state.update { it.copy(error = "没有操作权限") }; return }
        val revision = timeline?.trackRevision
        val identity = timeline?.advanceState()
        if (!automatic) cancelAdvanceRetry()
        val intent = advanceIntent
        fun stillEnded() = activeId == id && session == expected && repo.session() == expected &&
            intent == advanceIntent && role?.controls == true &&
            state.value.detail?.timeline?.advanceState() == identity
        command(action, id, expected, { role?.controls == true && (!automatic || stillEnded()) }, onFailure = {
            // Retrying an ambiguous POST is safe only with the server's revision guard.
            if (automatic && revision != null && stillEnded() && advanceRetry?.isActive != true) {
                val epoch = generation
                advanceRetry = scope.launch {
                    var retryDelay = 5000L
                    while (isActive && epoch == generation && stillEnded()) {
                        delay(retryDelay)
                        if (epoch != generation || !stillEnded()) return@launch
                        val refreshed = appResult { refresh() }
                        if (epoch != generation || !stillEnded()) return@launch
                        if (refreshed is AppResult.Success) {
                            advanced = null; advanceRetry = null; ended()
                            return@launch
                        }
                        if (refreshed is AppResult.Failure && refreshed.kind in setOf(
                                ErrorKind.Unauthorized, ErrorKind.Forbidden, ErrorKind.NotFound)) return@launch
                        // A temporary snapshot failure must not permanently strand an ended track.
                        retryDelay = (retryDelay * 2).coerceAtMost(30_000)
                    }
                }
            }
        }) {
            repo.action(id, action, buildJsonObject { revision?.let { put("trackRevision", it) } }, expected)
        }
        if (automatic) advanced = identity
    }
    override fun next() = changeTrack("next")
    override fun previous() = changeTrack("prev")
    override fun ended() {
        val timeline = state.value.detail?.timeline ?: return
        if (role?.controls == true && timeline.playing && advanced != timeline.advanceState()) {
            changeTrack("next", automatic = true)
        }
    }
    override fun failed() {
        val timeline = state.value.detail?.timeline?.takeIf { it.trackNcmId > 0 } ?: return
        val id = activeId ?: return; val expected = session ?: return
        val track = timeline.recoveryIdentity()
        if (recoveryTrack != track) {
            recoveryJob?.cancel(); recoveryJob = null
            recoveryTrack = track; lastRecovery = Long.MIN_VALUE
        }
        recoveryRevision++
        if (recoveryJob?.isActive == true) return
        val epoch = generation
        fun valid() = generation == epoch && activeId == id && session == expected && repo.session() == expected &&
            role != null && recoveryTrack == track && state.value.detail?.timeline?.recoveryIdentity() == track
        val recovery = scope.launch(start = CoroutineStart.LAZY) {
            try {
                while (isActive && valid()) {
                    if (lastRecovery != Long.MIN_VALUE) {
                        delay((recoveryIntervalMs - (monotonic() - lastRecovery)).coerceAtLeast(1))
                    }
                    // Keep the failed-prepare request while a proactive renewal is in flight.
                    state.first { "stream/renew" !in it.pendingActions }
                    if (!valid()) return@launch
                    renewStream()
                    state.first { "stream/renew" !in it.pendingActions }
                    if (!valid()) return@launch
                    lastRecovery = monotonic()
                    val attempt = recoveryRevision
                    when (val result = appResult { refresh(reloadTrack = track) }) {
                        is AppResult.Success -> if (valid() && attempt == recoveryRevision) {
                            return@launch
                        }
                        is AppResult.Failure -> if (valid()) {
                            state.update { it.copy(error = "房间音频恢复失败：${result.kind.message}") }
                            if (result.kind in setOf(ErrorKind.Unauthorized, ErrorKind.Forbidden, ErrorKind.NotFound)) return@launch
                        }
                    }
                }
            } finally {
                if (recoveryJob === currentCoroutineContext()[Job]) recoveryJob = null
            }
        }
        recoveryJob = recovery
        recovery.start()
    }
    private fun renewStream() {
        val now = monotonic()
        if (lastRenew != Long.MIN_VALUE && now - lastRenew < 10_000) return
        lastRenew = now
        action("stream/renew", permission = { it != null })
    }
    override fun stop() { scope.launch { leave() } }
    suspend fun leave() {
        val id = activeId ?: return; val expected = session ?: return
        disconnect()
        when (val result = appResult { repo.action(id, "leave", expected = expected) }) {
            is AppResult.Failure -> state.update { it.copy(error = "本地已退出，服务器退出失败：${result.kind.message}") }; else -> Unit }
    }
    fun disconnect(message: String? = null) {
        generation++
        advanceRetry?.cancel(); advanceRetry = null
        scheduledStart?.cancel(); scheduledStart = null
        recoveryJob?.cancel(); recoveryJob = null; recoveryTrack = null
        job?.cancel(); job = null; activeId = null; session = null; loaded = null
        player.endExternal(this); state.value = RoomSessionState(error = message)
    }
}
