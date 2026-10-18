package io.github.currencortex.music.core.netease.listen

import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.netease.NeteaseSession
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.data.netease.listen.*
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NeteaseListenTogetherState(val room: ListenTogetherRoom? = null,
    val availableRoom: ListenTogetherRoom? = null, val active: Boolean = false,
    val busy: Boolean = false, val connected: Boolean = false, val error: String? = null,
    val queueCount: Int = 0, val invitationUrl: String? = null,
    val queueLoading: Boolean = false, val queueError: String? = null)

/** Original recovery snapshots are polled; a heartbeat is never interpreted as a playback command. */
class NeteaseListenTogetherSession internal constructor(private val repo: ListenTogetherProtocol,
    private val player: ExternalPlayer, private val scope: CoroutineScope,
    private val detail: suspend (Long) -> Song, private val source: suspend (Song) -> String,
    private val monotonic: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val pollIntervalMs: Long = 1500L,
    private val details: suspend (List<Long>) -> List<Song> = { ids -> ids.map { detail(it) } },
    private val trace: (String) -> Unit = {}) : ExternalPlayback {
    val state = MutableStateFlow(NeteaseListenTogetherState())
    private val _queue = MutableStateFlow(QueueSnapshot())
    val queue = _queue.asStateFlow()
    private var metadata: Job? = null
    private var metadataIds = emptyList<Long>()
    private class Active(val roomId: String, val expected: NeteaseSession, val epoch: Long) {
        var queue = ListenTogetherQueue()
        var clientSeq = 0
        var reservedQueueVersion = -1L
        var lastServerSeq = -1L
        var lastPlayback: ListenTogetherPlayback? = null
        var loadedSongId = 0L
        var heartbeatAt = Long.MIN_VALUE
        var polls = 0
        var members: Set<Long>? = null
        val songs = mutableMapOf<Long, Song>()
        var lastTrace: String? = null
    }
    private val commands = Mutex()
    private var generation = 0L
    private var active: Active? = null
    private var polling: Job? = null
    private fun valid(current: Active) {
        if (active !== current || current.epoch != generation) throw CancellationException("Listen session replaced")
        repo.checkSession(current.expected)
    }

    private fun checkEpoch(epoch: Long) {
        if (generation != epoch) throw CancellationException("Listen session replaced")
    }

    private fun operation(block: suspend (Long) -> Unit) {
        if (state.value.busy) return
        val epoch = generation
        state.update { it.copy(busy = true, error = null) }
        scope.launch {
            try { commands.withLock { if (generation == epoch) block(epoch) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (generation == epoch) {
                if (active != null && error is ApiException && error.kind in setOf(
                        ErrorKind.NeteaseSessionChanged, ErrorKind.NeteaseBindingRequired)) disconnect(message(error))
                else state.update { it.copy(error = message(error)) }
            } }
            finally { if (generation == epoch) state.update { it.copy(busy = false) } }
        }
    }

    fun create() = operation { epoch ->
        check(active == null && player.state.value.mode == PlayerMode.LOCAL) { "请先退出一起听或结束投屏" }
        val expected = repo.captureSession()
        checkEpoch(epoch)
        val original = player.queue.state.value
        val playback = player.state.value
        check(original.current == null || songId(original.current) != null) { "请先选择一首网易云歌曲" }
        val room = repo.create(expected)
        checkEpoch(epoch)
        repo.checkSession(expected)
        startRoom(room, expected, epoch) { current ->
            original.songs.filter { songId(it) != null }.forEach { current.songs[songId(it)!!] = it }
            val anchor = songId(original.current)
            val ids = original.songs.mapNotNull(::songId).distinct().take(MAX_QUEUE).let { first ->
                if (anchor != null && anchor !in first) first.dropLast(1) + anchor else first
            }
            if (ids.isNotEmpty()) {
                current.queue = ListenTogetherQueue(ids,
                    randomSongIds = if (original.mode == PlaybackMode.SHUFFLE) ids.shuffled() else emptyList(),
                    playMode = when (original.mode) {
                        PlaybackMode.ONE -> "SINGLE_LOOP"; PlaybackMode.SHUFFLE -> "RANDOM"; else -> "ORDER_LOOP"
                    }, anchorSongId = anchor ?: ids.first(),
                    anchorPosition = ids.indexOf(anchor).coerceAtLeast(0), version = reserveQueueVersion(current),
                    clientSeq = ++current.clientSeq)
                repo.reportQueue(expected, room.roomId, current.queue)
                valid(current)
                sendPlayback(current, "GOTO", current.queue.anchorSongId, playback.showPause, playback.positionMs)
            }
        }
    }

    fun join(inviteText: String) = operation { epoch ->
        check(active == null && player.state.value.mode == PlayerMode.LOCAL) { "请先退出一起听或结束投屏" }
        val invitation = ListenTogetherInvitation.parse(inviteText) ?: error("请粘贴有效的网易云一起听邀请链接")
        val expected = repo.captureSession()
        checkEpoch(epoch)
        val room = repo.accept(expected, invitation)
        checkEpoch(epoch)
        repo.checkSession(expected)
        startRoom(room, expected, epoch) { synchronize(it, members = true) }
    }

    fun resume() = operation { epoch ->
        check(active == null && player.state.value.mode == PlayerMode.LOCAL) { "请先退出一起听或结束投屏" }
        val expected = repo.captureSession()
        checkEpoch(epoch)
        val remote = repo.status(expected)
        checkEpoch(epoch)
        val room = remote.room.takeIf { remote.inRoom } ?: error("没有可恢复的网易云一起听会话")
        repo.restoreDevice(expected, room.roomId)
        checkEpoch(epoch)
        startRoom(room, expected, epoch) { synchronize(it, members = true) }
    }

    /** One startup attempt; a user transport action during lookup always wins. */
    fun restoreAutomatically(eligible: () -> Boolean) = operation { epoch ->
        if (active != null || player.state.value.mode != PlayerMode.LOCAL || !eligible()) return@operation
        val expected = repo.captureSession()
        checkEpoch(epoch)
        val remote = repo.status(expected)
        checkEpoch(epoch)
        repo.checkSession(expected)
        val room = remote.room.takeIf { remote.inRoom } ?: return@operation
        if (!eligible() || player.state.value.mode != PlayerMode.LOCAL) return@operation
        repo.restoreDevice(expected, room.roomId)
        checkEpoch(epoch)
        if (!eligible() || player.state.value.mode != PlayerMode.LOCAL) return@operation
        startRoom(room, expected, epoch) { synchronize(it, members = true) }
    }

    private suspend fun startRoom(room: ListenTogetherRoom, expected: NeteaseSession, epoch: Long,
        initialize: suspend (Active) -> Unit) {
        val current = attach(room, expected, epoch)
        try {
            // Some current deployments no longer expose the legacy IM sync-notice route.
            // Recovery snapshots still work; absence of this optional nudge must not end a room.
            try { repo.requestSync(expected, room.roomId) }
            catch (failure: ApiException) { if (failure.kind != ErrorKind.NotFound) throw failure }
            valid(current)
            initialize(current)
            valid(current)
            startPolling(current)
        } catch (failure: Throwable) {
            if (active === current) disconnect(if (failure is CancellationException) null else message(failure))
            throw failure
        }
    }

    private suspend fun attach(room: ListenTogetherRoom, expected: NeteaseSession, epoch: Long): Active {
        checkEpoch(epoch)
        repo.checkSession(expected)
        try {
            check(player.beginExternal(PlayerMode.ROOM, this)) { "无法启动双人一起听" }
            checkEpoch(epoch)
            repo.checkSession(expected)
            val current = Active(room.roomId, expected, epoch)
            active = current
            state.value = NeteaseListenTogetherState(room = room, active = true, busy = true, connected = true,
                invitationUrl = ListenTogetherInvitation(room.roomId, expected.credential.profile!!.uid).shareUrl())
            publishParticipants(current, room)
            player.roomTrack(null, null, 0, false)
            valid(current)
            return current
        } catch (failure: Throwable) {
            if (generation == epoch) disconnect(if (failure is CancellationException) null else message(failure))
            else if (player.external === this) player.endExternal(this)
            throw failure
        }
    }

    private fun publishParticipants(current: Active, room: ListenTogetherRoom) {
        valid(current)
        val self = current.expected.credential.profile!!
        val localUser = io.github.currencortex.music.data.netease.listen.ListenTogetherParticipant(self.uid, self.nickname, self.avatar)
        val users = (room.participants + localUser).distinctBy { it.userId }.take(2)
        player.state.update { it.copy(canControlPlayback = true,
            listenTogether = ListenTogetherPair(users.map { user ->
                io.github.currencortex.music.core.media.ListenTogetherParticipant(user.userId, user.nickname, user.avatarUrl)
            })) }
    }

    fun refresh() = operation { epoch ->
        val current = active
        if (current == null) {
            val expected = repo.captureSession()
            checkEpoch(epoch)
            val remote = repo.status(expected)
            checkEpoch(epoch)
            repo.checkSession(expected)
            state.update { it.copy(availableRoom = remote.room.takeIf { remote.inRoom }, connected = false) }
        } else {
            repo.restoreDevice(current.expected, current.roomId)
            valid(current)
            try { repo.requestSync(current.expected, current.roomId) }
            catch (failure: ApiException) { if (failure.kind != ErrorKind.NotFound) throw failure }
            valid(current)
            metadataIds = emptyList()
            synchronize(current, members = true, forcePlayback = true)
            if (active === current && polling?.isActive != true) startPolling(current)
        }
    }

    private fun startPolling(current: Active) {
        polling?.cancel()
        polling = scope.launch {
            var backoff = pollIntervalMs
            while (isActive && active === current) {
                try {
                    commands.withLock {
                        valid(current)
                        val now = monotonic()
                        if (current.heartbeatAt == Long.MIN_VALUE || now - current.heartbeatAt >= NeteaseListenTogetherRepository.HEARTBEAT_INTERVAL_MS) {
                            val playback = player.state.value
                            val alive = repo.heartbeat(current.expected, current.roomId, songId(playback.song) ?: 0,
                                playback.showPause, playback.positionMs.coerceAtLeast(0), current.queue.version)
                            valid(current)
                            if (!alive) { disconnect("网易云一起听已结束"); return@withLock }
                            current.heartbeatAt = now
                        }
                        synchronize(current, members = current.polls++ % 3 == 0)
                    }
                    backoff = pollIntervalMs
                } catch (cancelled: CancellationException) { throw cancelled }
                  catch (error: Exception) {
                    if (active !== current) return@launch
                    if (error is ApiException && error.kind in setOf(ErrorKind.NeteaseSessionChanged,
                        ErrorKind.NeteaseBindingRequired, ErrorKind.NotFound, ErrorKind.Forbidden)) {
                        disconnect(message(error)); return@launch
                    }
                    state.update { it.copy(connected = false, error = "同步失败：${message(error)}，正在重试") }
                    backoff = (backoff * 2).coerceAtMost(15000)
                }
                delay(backoff)
            }
        }
    }

    private suspend fun synchronize(current: Active, members: Boolean, forcePlayback: Boolean = false) {
        valid(current)
        if (members) {
            val remote = repo.status(current.expected)
            valid(current)
            if (!remote.inRoom || remote.room?.roomId != current.roomId) {
                disconnect("网易云一起听已结束"); return
            }
            state.update { it.copy(room = remote.room) }
            publishParticipants(current, remote.room)
            val membersNow = remote.room.participants.map { it.userId }.toSet()
            if (current.members != null && current.members != membersNow && current.loadedSongId > 0 &&
                remote.room.creatorId == current.expected.credential.profile?.uid) {
                sendPlayback(current, "PROGRESS", current.loadedSongId, player.state.value.showPause,
                    player.state.value.positionMs, "AUTO")
            }
            current.members = membersNow
        }
        val snapshot = repo.playlist(current.expected, current.roomId, if (forcePlayback)
            current.queue.copy(songIds = emptyList(), randomSongIds = emptyList()) else current.queue)
        valid(current)
        current.queue = snapshot.queue
        state.update { it.copy(queueCount = snapshot.queue.songIds.size, connected = true, error = null) }
        publishQueue(current)
        if (snapshot.queue.songIds.isEmpty() && current.loadedSongId != 0L) {
            player.roomTrack(null, null, 0, false)
            valid(current)
            current.loadedSongId = 0
        }
        val playback = snapshot.playback ?: return
        val diagnostic = "snapshot seq=${playback.serverSeq} seen=${current.lastServerSeq} client=${playback.clientSeq} " +
            "sender=${if (playback.userId == current.expected.credential.profile?.uid) "self" else "peer"} " +
            "command=${playback.commandType} target=${playback.targetSongId} loaded=${current.loadedSongId} " +
            "playing=${playback.playing} count=${snapshot.queue.songIds.size} forced=$forcePlayback"
        if (current.lastTrace != diagnostic) { trace(diagnostic); current.lastTrace = diagnostic }
        // Recovery may retain the last command after the shared list has removed its target.
        if (current.queue.songIds.isEmpty()) return
        // Repeated recovery snapshots contain an old progress, not a live clock.
        // Never seek to it again or an actively playing song would jump backwards each poll.
        if (!forcePlayback && playback.serverSeq <= current.lastServerSeq && current.lastServerSeq >= 0) return
        if (!forcePlayback && playback.userId == current.expected.credential.profile?.uid &&
            playback.clientSeq > 0 && playback.clientSeq <= current.clientSeq && current.loadedSongId == playback.targetSongId) {
            current.lastServerSeq = playback.serverSeq; current.lastPlayback = playback; return
        }
        applyPlayback(current, playback)
        valid(current)
        current.lastServerSeq = playback.serverSeq
        current.lastPlayback = playback
        publishQueue(current)
    }

    private suspend fun applyPlayback(current: Active, playback: ListenTogetherPlayback) {
        valid(current)
        if (current.loadedSongId != playback.targetSongId) {
            val song = current.songs[playback.targetSongId] ?: detail(playback.targetSongId).also {
                check(songId(it) == playback.targetSongId) { "歌曲信息与同步指令不匹配" }
                current.songs[playback.targetSongId] = it
            }
            valid(current)
            val url = source(song)
            valid(current)
            player.roomTrack(song, url, playback.positionMs, playback.playing)
            valid(current)
            current.loadedSongId = playback.targetSongId
        } else player.roomCorrection(playback.positionMs,
            playback.commandType in setOf("PROGRESS", "GOTO", "NEXT", "PREVIOUS") ||
                kotlin.math.abs(player.state.value.positionMs - playback.positionMs) > 1500,
            1f, playback.playing)
    }

    private suspend fun sendPlayback(current: Active, type: String, target: Long,
        playing: Boolean, position: Long, trigger: String = "MANUAL") {
        valid(current)
        val command = ListenTogetherPlayback(type, target, current.loadedSongId, playing,
            position.coerceAtLeast(0), ++current.clientSeq, userId = current.expected.credential.profile!!.uid,
            triggerType = trigger)
        trace("send command=$type client=${command.clientSeq} target=$target former=${command.formerSongId} playing=$playing")
        repo.reportPlayback(current.expected, current.roomId, command)
        trace("accepted command=$type client=${command.clientSeq} target=$target")
        valid(current)
        applyPlayback(current, command)
        current.lastPlayback = command
        publishQueue(current)
    }

    /** Display the shared list separately: roomTrack owns a one-track audio transport queue. */
    private fun publishQueue(current: Active) {
        valid(current)
        val ids = current.queue.songIds
        val mode = when (current.queue.playMode) {
            "RANDOM" -> PlaybackMode.SHUFFLE; "SINGLE_LOOP" -> PlaybackMode.ONE; else -> PlaybackMode.LIST
        }
        _queue.value = QueueSnapshot(ids.map { current.songs[it] ?: Song(it, "歌曲信息加载中…") },
            ids.indexOf(current.loadedSongId), mode = mode)
        val missing = ids.filterNot { it in current.songs }
        if (missing.isEmpty()) {
            metadata?.cancel(); metadata = null; metadataIds = emptyList()
            state.update { it.copy(queueLoading = false, queueError = null) }
        } else if (metadataIds != ids) {
            metadata?.cancel(); metadataIds = ids
            state.update { it.copy(queueLoading = true, queueError = null) }
            metadata = scope.launch {
                try {
                    val loaded = details(missing)
                    valid(current)
                    loaded.forEach { song -> songId(song)?.takeIf { it in ids }?.let { current.songs[it] = song } }
                    val latest = current.queue.songIds
                    _queue.update { it.copy(songs = latest.map { id -> current.songs[id] ?: Song(id, "歌曲 $id") }) }
                    state.update { it.copy(queueLoading = false,
                        queueError = if (latest.any { id -> id !in current.songs }) "部分歌曲信息暂不可用，可刷新重试" else null) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (active === current) state.update {
                    it.copy(queueLoading = false, queueError = "歌曲信息加载失败，可刷新重试")
                } }
            }
        }
    }

    fun selectSong(id: Long) = command { current ->
        synchronize(current, members = false); valid(current)
        check(id in current.queue.songIds) { "这首歌已被移出一起听歌单" }
        if (id != current.loadedSongId) {
            // Desktop drops PLAY_COMMAND targets absent from its local shared list.
            // Publish the room list first so its PLAYLIST event precedes this cut.
            replaceQueue(current, current.queue.songIds)
            sendPlayback(current, "GOTO", id, true, 0)
        }
    }

    private suspend fun replaceQueue(current: Active, ids: List<Long>, mode: String = current.queue.playMode) {
        val anchor = current.loadedSongId.takeIf { it in ids } ?: ids.firstOrNull() ?: 0L
        val random = if (mode == "RANDOM") {
            if (current.queue.playMode == mode) current.queue.randomSongIds.filter { it in ids }.let { kept -> kept + ids.filterNot { it in kept } }
            else ids.shuffled()
        } else emptyList()
        val replacement = current.queue.copy(songIds = ids, randomSongIds = random, playMode = mode,
            anchorSongId = anchor, anchorPosition = ids.indexOf(anchor).coerceAtLeast(0),
            version = reserveQueueVersion(current), clientSeq = ++current.clientSeq)
        repo.reportQueue(current.expected, current.roomId, replacement)
        valid(current)
        current.queue = replacement
        state.update { it.copy(queueCount = ids.size) }
        publishQueue(current)
    }

    override fun enqueue(song: Song, next: Boolean) = command { current ->
        val id = songId(song) ?: error("双人一起听仅支持网易云歌曲")
        synchronize(current, members = false); valid(current)
        if (id in current.queue.songIds) return@command
        check(current.queue.songIds.size < MAX_QUEUE) { "一起听队列已满，请先移除歌曲" }
        current.songs[id] = song
        val ids = current.queue.songIds.toMutableList()
        val position = if (next) (ids.indexOf(current.loadedSongId) + 1).coerceIn(0, ids.size) else ids.size
        ids.add(position, id)
        replaceQueue(current, ids)
    }

    fun removeSong(id: Long) = command { current ->
        synchronize(current, members = false); valid(current)
        val index = current.queue.songIds.indexOf(id)
        if (index < 0) return@command
        val ids = current.queue.songIds.filterNot { it == id }
        if (ids.isEmpty()) clearQueue(current)
        else {
            val wasPlaying = player.state.value.showPause
            replaceQueue(current, ids)
            if (current.loadedSongId == id) sendPlayback(current, "GOTO", ids[index.coerceAtMost(ids.lastIndex)], wasPlaying, 0)
        }
    }

    fun clearQueue() = command { current -> synchronize(current, members = false); valid(current); clearQueue(current) }
    private suspend fun clearQueue(current: Active) {
        if (current.loadedSongId > 0) sendPlayback(current, "PAUSE", current.loadedSongId, false, player.state.value.positionMs)
        replaceQueue(current, emptyList())
        player.roomTrack(null, null, 0, false)
        valid(current)
        current.loadedSongId = 0
        publishQueue(current)
    }

    fun setQueueMode(mode: PlaybackMode) = command { current ->
        check(mode != PlaybackMode.HEART) { "一起听暂不支持本机心动模式" }
        synchronize(current, members = false); valid(current)
        replaceQueue(current, current.queue.songIds, when (mode) {
            PlaybackMode.SHUFFLE -> "RANDOM"; PlaybackMode.ONE -> "SINGLE_LOOP"; else -> "ORDER_LOOP"
        })
    }

    private fun command(block: suspend (Active) -> Unit) = operation { active?.let { valid(it); block(it) } }
    override fun play(playing: Boolean) = command { current ->
        val target = current.loadedSongId.takeIf { it > 0 } ?: current.queue.songIds.firstOrNull()
            ?: error("请先点一首网易云歌曲")
        sendPlayback(current, if (playing) "PLAY" else "PAUSE", target, playing, player.state.value.positionMs)
    }
    override fun seek(position: Long) = command { current ->
        check(current.loadedSongId > 0) { "请先点一首网易云歌曲" }
        sendPlayback(current, "PROGRESS", current.loadedSongId, player.state.value.showPause, position)
    }
    private fun advance(direction: Int, automatic: Boolean = false) = command { current ->
        if (!automatic) { synchronize(current, members = false); valid(current) }
        val ids = current.queue.randomSongIds.takeIf { current.queue.playMode == "RANDOM" && it.isNotEmpty() } ?: current.queue.songIds
        if (ids.isEmpty()) return@command
        val index = ids.indexOf(current.loadedSongId).coerceAtLeast(0)
        val next = if (automatic && current.queue.playMode == "SINGLE_LOOP") index
            else (index + direction + ids.size) % ids.size
        if (!automatic) replaceQueue(current, current.queue.songIds)
        sendPlayback(current, if (direction < 0) "PREVIOUS" else "NEXT", ids[next], true, 0, if (automatic) "AUTO" else "MANUAL")
    }
    override fun next() = advance(1)
    override fun previous() = advance(-1)
    override fun ended() { if (state.value.room?.creatorId == active?.expected?.credential?.profile?.uid) advance(1, automatic = true) }
    override fun failed() { state.update { it.copy(error = "当前歌曲播放失败，可重试或切换歌曲") } }
    /** The original client reserves its own vector entry before sending; failed requests cannot reuse it. */
    private fun reserveQueueVersion(current: Active): List<ListenTogetherVersion> {
        val uid = current.expected.credential.profile!!.uid
        val previous = current.queue.version.firstOrNull { it.userId == uid }
        val next = Math.addExact(maxOf(previous?.version ?: -1L, current.reservedQueueVersion), 1L)
        current.reservedQueueVersion = next
        return current.queue.version.filterNot { it.userId == uid } + ListenTogetherVersion(uid, next, previous?.outerId)
    }
    override fun request(song: Song) = command { current ->
        val id = songId(song) ?: error("双人一起听仅支持网易云歌曲")
        check(id in current.queue.songIds || current.queue.songIds.size < MAX_QUEUE) { "一起听队列已满，请先移除歌曲" }
        current.songs[id] = song
        val queue = current.queue.copy(songIds = (current.queue.songIds + id).distinct(),
            randomSongIds = if (current.queue.playMode == "RANDOM")
                ((current.queue.randomSongIds.ifEmpty { current.queue.songIds }) + id).distinct() else current.queue.randomSongIds,
            anchorSongId = id, anchorPosition = current.queue.songIds.indexOf(id).takeIf { it >= 0 } ?: current.queue.songIds.size,
            version = reserveQueueVersion(current), clientSeq = ++current.clientSeq)
        repo.reportQueue(current.expected, current.roomId, queue)
        valid(current)
        current.queue = queue
        state.update { it.copy(queueCount = queue.songIds.size) }
        publishQueue(current)
        sendPlayback(current, "GOTO", id, true, 0)
    }
    override fun stop() = leave()
    fun leave() = operation {
        val current = active ?: return@operation
        polling?.cancel()
        var failure: String? = null
        try { repo.leave(current.expected, current.roomId) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure = "已退出本机，远端会话结束尚未确认" }
        finally { if (active === current) disconnect(failure) }
    }
    fun disconnect(error: String? = null) {
        ++generation
        polling?.cancel(); polling = null
        metadata?.cancel(); metadata = null; metadataIds = emptyList()
        _queue.value = QueueSnapshot()
        active = null
        player.endExternal(this)
        state.value = NeteaseListenTogetherState(error = error)
    }
    fun dismissError() { state.update { it.copy(error = null) } }
    private fun message(error: Throwable): String = when (error) {
        is ApiException -> error.kind.message
        is IllegalStateException, is IllegalArgumentException -> error.message ?: "无法执行一起听操作"
        else -> "一起听连接失败，请重试"
    }
    private fun songId(song: Song?): Long? = song?.takeIf { !it.video && it.musicSource == MusicSource.NETEASE && it.navidrome == null }
        ?.let { it.externalIds.neteaseId?.toLongOrNull() ?: it.id }?.takeIf { it > 0 }
    private companion object { const val MAX_QUEUE = 1000 }
}
