package io.github.currencortex.music.core.room

import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.room.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class RoomSessionState(val detail: RoomDetail? = null, val connecting: Boolean = false,
    val connected: Boolean = false, val error: String? = null, val driftMs: Long = 0)

class RoomSession(private val repo: RoomRepository, private val sse: RoomSseClient,
    private val player: ExternalPlayer, private val scope: CoroutineScope,
    private val userId: () -> Long, private val expire: (RequestSession) -> Unit,
    private val monotonic: () -> Long = { android.os.SystemClock.elapsedRealtime() }) : ExternalPlayback {
    val state = MutableStateFlow(RoomSessionState())
    private var session: RequestSession? = null
    private var activeId: String? = null
    private var job: Job? = null
    private val clock = RoomSynchronizer()
    private var seq = 0L
    private var lastId: String? = null
    private var loaded: Pair<Long, String>? = null
    private var advanced: Long? = null
    private var lastRenew = Long.MIN_VALUE
    private val role get() = state.value.detail?.role(userId())
    val active get() = activeId != null
    suspend fun join(info: RoomInfo, password: String = "", created: Boolean = false, expected: RequestSession = repo.session()) {
        check(!active && player.state.value.mode == PlayerMode.LOCAL) { "请先退出一起听或结束投屏" }
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
                    launch { while (isActive) { delay(1000); when(val result = appResult { align() }) {
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
                                        val next = raw["seq"]?.jsonPrimitive?.longOrNull
                                        if (next != null && next <= seq) return@collect
                                        if (next != null) seq = next
                                        lastId = signal.id ?: lastId
                                        backoff = 1000
                                        val payload = (raw["payload"] as? JsonObject) ?: raw
                                        if (signal.type == "close" || signal.type == "kick" && payload["userId"]?.jsonPrimitive?.longOrNull == userId()) {
                                            disconnect("已离开房间"); return@collect
                                        }
                                        payload["timeline"]?.takeIf { it != JsonNull }?.let { tl ->
                                            val timeline = ApiJson.decodeFromJsonElement<RoomTimeline>(tl)
                                            state.update { it.copy(detail = it.detail?.copy(timeline = timeline)) }; applyTimeline()
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
    private suspend fun calibrate(id: String, expected: RequestSession) {
        clock.reset()
        repeat(5) {
            val sent = monotonic()
            when (val sample = appResult { repo.sync(id, expected) }) {
                is AppResult.Success -> clock.sample(sample.value, sent, monotonic())
                is AppResult.Failure -> Unit
            }
        }
    }
    suspend fun refresh() {
        val id = activeId ?: return; val expected = session ?: return
        val detail = repo.detail(id, expected)
        if (activeId != id || expected != session || expected != repo.session()) return
        if (detail.role(userId()) == null) { disconnect("你已不在此房间"); return }
        seq = maxOf(seq, detail.latestSeq)
        state.update { it.copy(detail = detail) }; applyTimeline()
    }
    private suspend fun applyTimeline() {
        publishPermission()
        val timeline = state.value.detail?.timeline
        val url = timeline?.stream?.url?.takeIf { it.toHttpUrlOrNull() != null }
        val key = if (timeline != null && timeline.trackNcmId > 0 && url != null) timeline.trackNcmId to url else null
        if (key != loaded) {
            loaded = key
            player.roomTrack(key?.let { timeline!!.trackMeta?.song(it.first) ?: Song(it.first, "房间歌曲") }, key?.second,
                timeline?.let { target(it) } ?: 0, timeline?.playing == true)
            publishPermission()
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
        if (!clock.ready || loaded == null || player.state.value.loading) return
        val correction = clock.correction(tl, monotonic(), player.state.value.positionMs)
        state.update { it.copy(driftMs = correction.position - player.state.value.positionMs) }
        player.roomCorrection(correction.position, correction.seek, correction.speed, tl.playing)
    }
    fun action(action: String, fields: JsonObject = buildJsonObject {}, permission: (RoomRole?) -> Boolean = { it?.controls == true }) {
        val id = activeId ?: return; val expected = session ?: return
        if (!permission(role)) { state.update { it.copy(error = "没有操作权限") }; return }
        scope.launch { when (val result = appResult {
            repo.action(id, action, fields, expected)
            if (action == "close") disconnect("房间已关闭") else refresh()
        }) {
            is AppResult.Failure -> state.update { it.copy(error = result.kind.message) }; else -> Unit } }
    }
    fun queueAction(item: RoomQueueItem, action: String) {
        val id = activeId ?: return; val expected = session ?: return
        if (if (action == "remove") !RoomPermissions.remove(role, item) else role?.controls != true) {
            state.update { it.copy(error = "没有操作权限") }; return
        }
        scope.launch { when (val result = appResult { repo.queueAction(id, item.id, action, expected); refresh() }) {
            is AppResult.Failure -> state.update { it.copy(error = result.kind.message) }; else -> Unit } }
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
    override fun next() = action("next")
    override fun previous() = action("prev")
    override fun ended() {
        val timeline = state.value.detail?.timeline ?: return
        // Only the owner advances automatically; administrators can still advance explicitly.
        if (role == RoomRole.OWNER && timeline.playing && advanced != timeline.baseAt) {
            advanced = timeline.baseAt; next()
        }
    }
    override fun failed() {
        val now = monotonic()
        if (lastRenew != Long.MIN_VALUE && now - lastRenew < 10_000) return
        lastRenew = now; loaded = null
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
        job?.cancel(); job = null; activeId = null; session = null; loaded = null
        player.endExternal(this); state.value = RoomSessionState(error = message)
    }
}
