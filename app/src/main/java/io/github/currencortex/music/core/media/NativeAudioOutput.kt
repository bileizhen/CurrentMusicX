package io.github.currencortex.music.core.media

import android.media.AudioDeviceInfo
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import java.nio.ByteBuffer

@androidx.annotation.Keep
internal object NativeAudioBridge {
    val available = runCatching { System.loadLibrary("currentmusic_output") }.isSuccess
    external fun open(api: Int, rate: Int, channels: Int, device: Int, exclusive: Boolean, softwareBufferTargetMs: Int = 500): Long
    external fun write(handle: Long, buffer: ByteBuffer, offset: Int, bytes: Int): Int
    external fun play(handle: Long)
    external fun pause(handle: Long)
    external fun close(handle: Long)
    external fun volume(handle: Long, gain: Float)
    external fun position(handle: Long): Long
    external fun api(handle: Long): Int
    external fun error(handle: Long): Int
    external fun snapshot(handle: Long): LongArray?
    external fun bufferFrames(handle: Long): Long
}

/** Values read from the open Oboe stream; no hardware-format or acoustic-delay inference. */
internal data class NativeOutputSnapshot(
    val backend: String, val sampleRate: Int, val channels: Int, val pcmEncoding: Int,
    val deviceId: Int?, val exclusive: Boolean, val deviceBufferFrames: Int?,
    val deviceCapacityFrames: Int?, val framesPerBurst: Int?, val softwareBufferFrames: Int,
    val underrunCount: Int?, val latencyMs: Double?, val errorCode: Int,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class NativeAudioOutput(private val config: AudioOutputProvider.OutputConfig,
    private val api: Int, private val exclusive: Boolean, private var device: Int,
    private val softwareBufferTargetMs: Int = 500) : AudioOutput {
    private val listeners = mutableSetOf<AudioOutput.Listener>()
    private var handle = open()
    private var gain = 1f
    private var started = false
    private var pausedPosition = 0L
    private var announced = false
    private var submittedFrames = 0L
    private var draining = false
    private var routeChanged = false
    private var released = false
    val actualApi get() = if (handle != 0L) NativeAudioBridge.api(handle) else api
    val actualExclusive get() = snapshot()?.exclusive == true
    fun snapshot(): NativeOutputSnapshot? {
        if (handle == 0L) return null
        val raw = NativeAudioBridge.snapshot(handle)?.takeIf { it.size == 13 } ?: return null
        return NativeOutputSnapshot(if (raw[0] == 2L) "AAudio" else "OpenSL ES", raw[1].toInt(), raw[2].toInt(),
            raw[3].toInt(), raw[4].toInt().takeIf { it > 0 }, raw[5] == 1L,
            raw[6].toInt().takeIf { it > 0 }, raw[7].toInt().takeIf { it > 0 }, raw[8].toInt().takeIf { it > 0 },
            raw[9].toInt(), raw[10].toInt().takeIf { it >= 0 }, raw[11].takeIf { it >= 0 }?.div(1000.0), raw[12].toInt())
    }
    private val channels get() = Integer.bitCount(config.channelMask)
    private fun open(): Long {
        check(NativeAudioBridge.available) { "原生音频库不可用" }
        require(config.encoding == C.ENCODING_PCM_16BIT) { "此原生输出仅接收 PCM 16-bit；其他格式必须协商或回退" }
        require(softwareBufferTargetMs in 20..2000) { "原生输出软件缓冲必须介于 20–2000 ms" }
        return NativeAudioBridge.open(api, config.sampleRate, channels, device, exclusive, softwareBufferTargetMs)
            .also { check(it != 0L) { "设备未接受请求的输出接口、采样率或独占模式" } }
    }
    override fun play() {
        check(handle != 0L) { "原生输出已释放" }
        if (started) return
        started = true; NativeAudioBridge.play(handle)
    }
    override fun pause() {
        if (handle != 0L) {
            if (started) NativeAudioBridge.pause(handle)
            pausedPosition = NativeAudioBridge.position(handle)
        }
        started = false
    }
    override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean {
        if (routeChanged || handle == 0L) throw AudioOutput.WriteException(-899, true)
        val count = NativeAudioBridge.write(handle, buffer, buffer.position(), buffer.remaining())
        if (count < 0) throw AudioOutput.WriteException(count, true)
        buffer.position(buffer.position() + count)
        submittedFrames += count / (channels * 2)
        return !buffer.hasRemaining()
    }
    override fun flush() {
        if (released) return
        NativeAudioBridge.close(handle); handle = 0
        submittedFrames = 0; draining = false; announced = false; routeChanged = false; pausedPosition = 0
        // A route can disappear while flushing. Let the next write request Media3's
        // recoverable output recreation, where the provider can select its fallback.
        handle = try { open() } catch (_: IllegalStateException) { return }
        NativeAudioBridge.volume(handle, gain)
        if (started) NativeAudioBridge.play(handle)
    }
    override fun stop() { draining = true }
    override fun release() {
        if (released) return
        released = true
        if (handle != 0L) NativeAudioBridge.close(handle)
        handle = 0; started = false
        listeners.toList().forEach { it.onReleased() }
    }
    override fun setVolume(volume: Float) { gain = volume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f; if (handle != 0L) NativeAudioBridge.volume(handle, gain) }
    override fun isOffloadedPlayback() = false
    override fun getAudioSessionId() = C.AUDIO_SESSION_ID_UNSET
    override fun getSampleRate() = config.sampleRate
    // Media3 writes into our software queue, whose capacity is separate from the
    // device's burst buffer. Report the real queue, not a hard-coded duration.
    override fun getBufferSizeInFrames() = if (handle != 0L) NativeAudioBridge.bufferFrames(handle) else 0L
    override fun getPositionUs(): Long {
        if (handle == 0L) return 0L
        val position = (if (started) NativeAudioBridge.position(handle) else pausedPosition)
            .coerceAtMost(submittedFrames * 1_000_000 / config.sampleRate)
        if (!announced && position > 0) { announced = true; listeners.toList().forEach { it.onPositionAdvancing(System.currentTimeMillis()) } }
        if (draining && position >= submittedFrames * 1_000_000 / config.sampleRate && started) pause()
        return position
    }
    override fun getPlaybackParameters() = PlaybackParameters.DEFAULT
    override fun isStalled() = handle == 0L || NativeAudioBridge.error(handle) != 0
    override fun addListener(listener: AudioOutput.Listener) { listeners += listener }
    override fun removeListener(listener: AudioOutput.Listener) { listeners -= listener }
    override fun setPlaybackParameters(playbackParams: PlaybackParameters) = Unit // Media3's PCM chain handles speed.
    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) = Unit
    override fun setOffloadEndOfStream() = Unit
    override fun attachAuxEffect(effectId: Int) = Unit
    override fun setAuxEffectSendLevel(level: Float) = Unit
    override fun setPreferredDevice(preferredDevice: AudioDeviceInfo?) {
        val next = preferredDevice?.id ?: 0
        if (actualApi != 1 && device != next) { device = next; routeChanged = true }
    }
    override fun canReuseAudioOutput(currentConfig: AudioOutputProvider.OutputConfig,
        newFormat: AudioOutputProvider.FormatConfig, newConfig: AudioOutputProvider.OutputConfig) = false
}
