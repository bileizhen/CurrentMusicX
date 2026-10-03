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

class PlayerController(private val context: Context, val queue: PlaybackQueue, private val scope: CoroutineScope) {
    val state = MutableStateFlow(PlayerState())
    private var controller: MediaController? = null
    private var connecting: Deferred<MediaController>? = null
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
        val matches = player.currentMediaItem?.mediaId == queue.state.value.current?.id?.toString()
        state.value = state.value.copy(song = queue.state.value.current, playing = player.isPlaying,
            loading = state.value.resolving || player.playbackState == Player.STATE_BUFFERING,
            positionMs = if (matches && !state.value.resolving) player.currentPosition.coerceAtLeast(0) else state.value.positionMs,
            durationMs = if (matches) player.duration.takeIf { it > 0 } ?: queue.state.value.current?.durationMs ?: 0
                else queue.state.value.current?.durationMs ?: 0)
    }
    fun load(play: Boolean = true, position: Long = queue.state.value.positionMs) = scope.launch(Dispatchers.Main.immediate) {
        try {
            connect().sendCustomCommand(SessionCommand(MusicService.LOAD, Bundle.EMPTY), Bundle().apply {
                putBoolean("play", play); putLong("position", position)
            }).await()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { state.value = state.value.copy(error = "无法连接播放器") }
    }
    fun playList(songs: List<Song>, index: Int) { queue.replace(songs, index); load() }
    fun toggle() = scope.launch(Dispatchers.Main.immediate) {
        val player = connect()
        if (state.value.playing || state.value.loading || player.playWhenReady) player.pause()
        else if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) load()
        else player.play()
    }
    fun pause() { controller?.pause() }
    fun next() { queue.next(); load() }
    fun previous() { queue.previous(); load() }
    fun seek(position: Long) { controller?.seekTo(position.coerceAtLeast(0)) }
    fun add(song: Song, next: Boolean = false) { queue.add(song, next) }
    fun remove(index: Int) { if (queue.remove(index)) { if (queue.state.value.current == null) clear() else load(state.value.playing) } }
    fun clear() { queue.clear(); controller?.stop(); controller?.clearMediaItems(); state.value = PlayerState() }
    fun select(index: Int) { queue.select(index); load() }
    fun setMode(mode: PlaybackMode) { queue.state.value = queue.state.value.copy(mode = mode) }
    fun qualityChanged() { load(state.value.playing || state.value.warning != null, state.value.positionMs) }
    fun acceptHighSpec() = scope.launch {
        connect().sendCustomCommand(SessionCommand(MusicService.ACCEPT_SPEC, Bundle.EMPTY), Bundle.EMPTY).await()
    }
    fun dismissError() { state.value = state.value.copy(error = null) }
    fun disconnect() { controller?.release(); controller = null; connecting?.cancel(); connecting = null }
}
