package io.github.currencortex.music.core.dlna

import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class CastState(val device: DlnaDevice? = null, val busy: Boolean = false, val volume: Int? = null,
    val quality: String = "无损", val error: String? = null)
class DlnaController(private val player: ExternalPlayer, private val music: MusicRepository,
    private val session: () -> RequestSession, private val scope: CoroutineScope, private val soap: SoapClient = SoapClient()) : ExternalPlayback {
    val state = MutableStateFlow(CastState())
    private val mutex = Mutex()
    private var generation = 0L
    private var operation: Job? = null
    private var polling: Job? = null
    private var av: AvTransportClient? = null
    private var rendering: RenderingControlClient? = null
    private var expected: RequestSession? = null
    private var sourceUrl: String? = null
    private var observedPlaying = false
    private var ending = false
    private fun valid(epoch: Long) = epoch == generation && expected == session() && player.external === this && !ending
    fun start(device: DlnaDevice) {
        if (state.value.busy || state.value.device != null) return
        if (player.state.value.mode != PlayerMode.LOCAL || player.queue.state.value.current?.video == true) {
            state.value = CastState(error = "请先退出一起听或关闭 MV"); return
        }
        if (player.queue.state.value.current == null) { state.value = CastState(error = "请先选择一首歌曲"); return }
        val epoch = ++generation; expected = session(); ending = false
        state.value = CastState(device, busy = true)
        av = AvTransportClient(soap, device.transport); rendering = device.rendering?.let { RenderingControlClient(soap, it) }
        operation = scope.launch {
            try {
                check(player.beginExternal(PlayerMode.CAST, this@DlnaController))
                mutex.withLock { load(epoch, player.state.value.positionMs) }
                if (!valid(epoch)) return@launch
                polling = scope.launch { while (isActive && valid(epoch)) {
                    delay(2000)
                    mutex.withLock { poll(epoch) }
                } }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (epoch == generation) state.update { it.copy(error = "投屏失败，请重试或选择其他设备") } }
            finally { if (epoch == generation) state.update { it.copy(busy = false) } }
        }
    }
    private suspend fun load(epoch: Long, position: Long = 0, compatible: Boolean = false) {
        val song = player.queue.state.value.current ?: return
        val transport = av ?: return
        state.update { it.copy(busy = true, error = null) }
        val quality = if (compatible) AudioQuality.EXHIGH else AudioQuality.LOSSLESS
        var source = music.source(song.id, quality)
        var fallback = compatible
        if (source.highSpec) { source = music.source(song.id, AudioQuality.EXHIGH); fallback = true }
        if (!valid(epoch)) return
        suspend fun send(value: AudioSource) {
            require(!value.highSpec)
            val url = value.url.toHttpUrlOrNull() ?: throw java.io.IOException()
            require(url.username.isBlank() && url.password.isBlank())
            val mime = when (value.format.lowercase(java.util.Locale.ROOT)) {
                "flac" -> "audio/flac"; "wav" -> "audio/wav"; "aac" -> "audio/aac"; "m4a", "mp4" -> "audio/mp4"
                else -> if (url.encodedPath.endsWith(".flac", true)) "audio/flac" else "audio/mpeg"
            }
            transport.setUri(value.url, DidlMetadataBuilder.build(song, value.url, mime))
            if (!valid(epoch)) return
            if (position >= 1000) transport.seek(position)
            if (!valid(epoch)) return
            transport.play(); sourceUrl = value.url; observedPlaying = false
        }
        try { send(source) } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (fallback || !valid(epoch)) throw e
            appResult { transport.stop() }
            source = music.source(song.id, AudioQuality.EXHIGH); fallback = true
            if (!valid(epoch)) return
            send(source)
        }
        if (!valid(epoch)) return
        state.update { it.copy(busy = false, quality = if (fallback) "极高（兼容）" else "无损", error = null) }
        player.state.value = PlayerState(song = song, playing = true, positionMs = position, durationMs = song.durationMs, mode = PlayerMode.CAST)
    }
    private suspend fun poll(epoch: Long) {
        try {
            val transport = av ?: return
            val transportState = transport.state()
            val position = transport.position()
            if (!valid(epoch)) return
            // A device playing another application's URI must not advance our queue.
            if (position.uri.isNotBlank() && position.uri != sourceUrl) {
                state.update { it.copy(error = "设备音源已改变，请结束投屏") }; return
            }
            if (transportState == "PLAYING") observedPlaying = true
            player.state.update { it.copy(playing = transportState == "PLAYING", positionMs = position.positionMs,
                durationMs = position.durationMs.takeIf { value -> value > 0 } ?: it.song?.durationMs ?: 0) }
            val volume = rendering?.let { control -> try { control.volume() } catch (e: CancellationException) { throw e } catch (_: Exception) { null } }
            if (valid(epoch)) state.update { it.copy(volume = volume, error = null) }
            if (transportState == "STOPPED" && observedPlaying && position.durationMs > 0 && position.positionMs >= position.durationMs - 2000) {
                observedPlaying = false; player.queue.next(automatic = true); load(epoch)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            if (valid(epoch)) state.update { it.copy(error = "无法读取设备状态，请检查 Wi-Fi 或结束投屏") }
        }
    }
    private fun command(block: suspend (Long, AvTransportClient) -> Unit) {
        if (ending || state.value.busy) return
        val epoch = generation
        operation = scope.launch { mutex.withLock {
            if (!valid(epoch)) return@withLock
            val transport = av ?: return@withLock
            when (val result = appResult { block(epoch, transport) }) {
                is AppResult.Failure -> state.update { it.copy(busy = false, error = "设备操作失败，请重试") }; else -> Unit }
        } }
    }
    override fun play(playing: Boolean) = command { epoch, transport ->
        if (sourceUrl == null) load(epoch) else { if (playing) transport.play() else transport.pause(); if (valid(epoch)) player.state.update { it.copy(playing = playing) } }
    }
    override fun seek(position: Long) = command { epoch, transport -> transport.seek(position); if (valid(epoch)) player.state.update { it.copy(positionMs = position) } }
    override fun next() = command { epoch, _ -> player.queue.next(); load(epoch) }
    override fun previous() = command { epoch, _ -> player.queue.previous(); load(epoch) }
    override fun request(song: Song) { player.queue.add(song) }
    fun volume(value: Int) = command { epoch, _ -> rendering?.volume(value); if (valid(epoch)) state.update { it.copy(volume = value.coerceIn(0, 100)) } }
    fun compatible() = command { epoch, _ -> load(epoch, player.state.value.positionMs, compatible = true) }
    override fun stop() {
        if (ending || state.value.device == null) return
        ending = true; ++generation; operation?.cancel(); polling?.cancel()
        val transport = av
        state.update { it.copy(busy = true) }
        scope.launch {
            var stopped = false
            try {
                stopped = withTimeoutOrNull(10_000) { appResult { mutex.withLock { transport?.stop() } } } is AppResult.Success
            } finally {
                player.endExternal(this@DlnaController); av = null; rendering = null; expected = null; sourceUrl = null
                state.value = CastState(error = if (!stopped) "手机已结束投屏，设备停止失败，请在设备上停止播放" else null)
                ending = false
            }
        }
    }
}
