package io.github.currencortex.music.core.media.engine

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.currencortex.music.data.settings.AudioBufferPolicy
import io.github.currencortex.music.data.settings.AudioOutputMode
import io.github.currencortex.music.data.settings.AudioOutputSettings

/** MediaSessionService owns the player/focus; the engine never creates a competing playback instance. */
interface AudioEngine {
    val player: Player
    fun initialize()
    fun prepare(item: MediaItem, positionMs: Long = 0, play: Boolean = true)
    fun play()
    fun pause()
    fun stop()
    fun seek(positionMs: Long)
    fun setOutputDevice(key: String)
    fun setOutputMode(mode: AudioOutputMode)
    fun setBufferPolicy(policy: AudioBufferPolicy)
    fun getAudioCapabilities(): List<Pair<String, AudioDeviceCapabilities>>
    fun getAudioDiagnostics(): AudioDiagnosticsSnapshot
    fun release()
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioEngineManager(private val activePlayer: () -> ExoPlayer, private val devices: AudioDeviceMonitor,
    val router: AudioOutputRouter, val buffers: AudioBufferController, private val diagnostics: AudioDiagnostics,
    private val reconfigure: () -> Unit, private val releaseOwner: () -> Unit) : AudioEngine {
    private var initialized = false
    private var released = false
    private var output = AudioOutputSettings()
    override val player: Player get() = activePlayer()
    override fun initialize() { check(!released); initialized = true; router.request(output) }
    private fun ready() { check(initialized && !released) { "Audio engine is not active" } }
    override fun prepare(item: MediaItem, positionMs: Long, play: Boolean) {
        ready(); router.retry(); diagnostics.resetTrack()
        activePlayer().apply { setMediaItem(item, positionMs.coerceAtLeast(0)); prepare(); playWhenReady = play }
    }
    override fun play() { ready(); activePlayer().play() }
    override fun pause() { ready(); activePlayer().pause() }
    override fun stop() { ready(); activePlayer().stop() }
    override fun seek(positionMs: Long) { ready(); activePlayer().seekTo(positionMs.coerceAtLeast(0)) }
    fun configureOutput(value: AudioOutputSettings, restart: Boolean = true) {
        ready(); val previous = output; output = value; router.request(value); buffers.policy = value.bufferPolicy
        if (restart && previous != value && previous.copy(bufferPolicy = value.bufferPolicy) != value) reconfigure()
    }
    override fun setOutputDevice(key: String) = configureOutput(output.copy(deviceKey = key))
    override fun setOutputMode(mode: AudioOutputMode) = configureOutput(output.copy(mode = mode))
    override fun setBufferPolicy(policy: AudioBufferPolicy) = configureOutput(output.copy(bufferPolicy = policy))
    override fun getAudioCapabilities() = devices.capabilities()
    override fun getAudioDiagnostics() = diagnostics.state.value
    override fun release() { if (!released) { released = true; releaseOwner() } }
}
