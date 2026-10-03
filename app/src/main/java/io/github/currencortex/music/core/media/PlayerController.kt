package io.github.currencortex.music.core.media

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await
import io.github.currencortex.music.core.network.ApiJson
import kotlinx.serialization.encodeToString

class PlayerController(private val context: Context, override val queue: PlaybackQueue, private val scope: CoroutineScope) : ExternalPlayer {
    override val state = MutableStateFlow(PlayerState())
    private var controller: MediaController? = null
    private var connecting: Deferred<MediaController>? = null
    private var beforeVideo: QueueSnapshot? = null
    private var beforeExternal: QueueSnapshot? = null
    override var external: ExternalPlayback? = null
        private set
    private var generation = 0L
    fun queueForPersistence() = beforeExternal ?: beforeVideo ?: queue.state.value
    override suspend fun beginExternal(mode: PlayerMode, controls: ExternalPlayback): Boolean {
        if (state.value.mode != PlayerMode.LOCAL || queue.state.value.current?.video == true) return false
        val snapshot = queue.state.value
        val saved = snapshot.copy(positionMs = if (state.value.song?.id == snapshot.current?.id) state.value.positionMs else snapshot.positionMs)
        val epoch = ++generation
        beforeExternal = saved; external = controls
        state.value = state.value.copy(mode = mode, playing = false, resolving = false, loading = false, warning = null)
        // Await this command before telling a TV to play: it cancels pending source resolution too.
        try { connect().sendCustomCommand(SessionCommand(MusicService.QUIESCE, Bundle.EMPTY), Bundle.EMPTY).await() }
        catch (e: Exception) { endExternal(controls); throw e }
        if (epoch != generation) return false
        return true
    }
    override fun endExternal(controls: ExternalPlayback) {
        if (external !== controls) return
        ++generation; external = null
        val saved = beforeExternal; beforeExternal = null
        state.value = PlayerState()
        if (saved != null) { queue.restore(saved); load(false, saved.positionMs) }
    }
    override suspend fun roomTrack(song: Song?, url: String?, position: Long, playing: Boolean) {
        if (state.value.mode != PlayerMode.ROOM) return
        if (song == null || url == null) {
            connect().sendCustomCommand(SessionCommand(MusicService.QUIESCE, Bundle.EMPTY), Bundle.EMPTY).await()
            queue.clear(); state.value = PlayerState(mode = PlayerMode.ROOM); return
        }
        queue.replace(listOf(song), 0)
        state.value = PlayerState(song = song, loading = true, mode = PlayerMode.ROOM, positionMs = position, durationMs = song.durationMs)
        connect().sendCustomCommand(SessionCommand(MusicService.ROOM_TRACK, Bundle.EMPTY), Bundle().apply {
            putString("song", ApiJson.encodeToString(song)); putString("url", url); putLong("position", position); putBoolean("play", playing)
        }).await()
    }
    override suspend fun roomCorrection(position: Long, seek: Boolean, speed: Float, playing: Boolean) {
        if (state.value.mode != PlayerMode.ROOM) return
        connect().sendCustomCommand(SessionCommand(MusicService.ROOM_SYNC, Bundle.EMPTY), Bundle().apply {
            putLong("position", position); putBoolean("seek", seek); putFloat("speed", speed); putBoolean("play", playing)
        }).await()
    }
    private fun localOnly(): Boolean {
        if (state.value.mode == PlayerMode.LOCAL) return true
        state.value = state.value.copy(error = "请先退出一起听或结束投屏")
        return false
    }
    fun playVideo(song: Song) {
        if (!localOnly()) return
        if (queue.state.value.current?.video != true) beforeVideo = queue.state.value
        queue.replace(listOf(song), 0); load()
    }
    fun closeVideo() {
        if (queue.state.value.current?.video != true) return
        pause()
        val saved = beforeVideo
        beforeVideo = null
        if (saved == null || saved.songs.isEmpty()) clear()
        else { queue.restore(saved); load(false, saved.positionMs) }
    }
    suspend fun connect(): MediaController {
        controller?.let { return it }
        val pending = connecting ?: scope.async(Dispatchers.Main.immediate) {
            MediaController.Builder(context, SessionToken(context, ComponentName(context, MusicService::class.java))).buildAsync().await().also {
                controller = it
                it.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) { publish(player) }
                })
                scope.launch { while (isActive && controller === it) { publish(it); delay(300) } }
            }
        }.also { connecting = it }
        return try { pending.await() } finally { connecting = null }
    }
    private fun publish(player: Player) {
        if (state.value.mode == PlayerMode.CAST) return
        val matches = player.currentMediaItem?.mediaId == queue.state.value.current?.id?.toString()
        state.value = state.value.copy(song = queue.state.value.current, playing = player.isPlaying,
            loading = state.value.resolving || player.playbackState == Player.STATE_BUFFERING,
            positionMs = if (matches && !state.value.resolving) player.currentPosition.coerceAtLeast(0)
                else if (state.value.song?.id == queue.state.value.current?.id) state.value.positionMs else queue.state.value.positionMs,
            durationMs = if (matches) player.duration.takeIf { it > 0 } ?: queue.state.value.current?.durationMs ?: 0
                else queue.state.value.current?.durationMs ?: 0)
    }
    fun load(play: Boolean = true, position: Long = queue.state.value.positionMs) = scope.launch(Dispatchers.Main.immediate) {
        val epoch = generation
        try {
            val connected = connect()
            if (epoch != generation || state.value.mode != PlayerMode.LOCAL) return@launch
            connected.sendCustomCommand(SessionCommand(MusicService.LOAD, Bundle.EMPTY), Bundle().apply {
                putBoolean("play", play); putLong("position", position)
            }).await()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { state.value = state.value.copy(error = "无法连接播放器") }
    }
    fun playList(songs: List<Song>, index: Int) { if (!localOnly()) return; beforeVideo = null; queue.replace(songs, index); load() }
    fun toggle() = scope.launch(Dispatchers.Main.immediate) {
        external?.let { it.play(!state.value.playing); return@launch }
        val player = connect()
        if (state.value.playing || state.value.loading || player.playWhenReady) player.pause()
        else if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) load()
        else player.play()
    }
    fun pause() { external?.let { it.play(false); return }; controller?.pause() }
    fun next() { external?.let { it.next(); return }; queue.next(); load() }
    fun previous() { external?.let { it.previous(); return }; queue.previous(); load() }
    fun seek(position: Long) { external?.let { it.seek(position.coerceAtLeast(0)); return }; controller?.seekTo(position.coerceAtLeast(0)) }
    fun add(song: Song, next: Boolean = false) { if (state.value.mode == PlayerMode.ROOM) { external?.request(song); return }; queue.add(song, next) }
    fun remove(index: Int) { if (!localOnly()) return; if (queue.remove(index)) { if (queue.state.value.current == null) clear() else load(state.value.playing) } }
    fun clear() { if (!localOnly()) return; beforeVideo = null; queue.clear(); controller?.stop(); controller?.clearMediaItems(); state.value = PlayerState() }
    fun select(index: Int) { if (!localOnly()) return; queue.select(index); load() }
    fun setMode(mode: PlaybackMode) { if (!localOnly()) return; queue.state.value = queue.state.value.copy(mode = mode) }
    fun qualityChanged() { if (localOnly()) load(state.value.playing || state.value.warning != null, state.value.positionMs) }
    fun acceptHighSpec() = scope.launch {
        connect().sendCustomCommand(SessionCommand(MusicService.ACCEPT_SPEC, Bundle.EMPTY), Bundle.EMPTY).await()
    }
    fun dismissError() { state.value = state.value.copy(error = null) }
    fun disconnect() { controller?.release(); controller = null; connecting?.cancel(); connecting = null }
}
