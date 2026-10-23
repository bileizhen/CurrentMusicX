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
    external fun open(api: Int, rate: Int, channels: Int, device: Int, exclusive: Boolean): Long
    external fun write(handle: Long, buffer: ByteBuffer, offset: Int, bytes: Int): Int
    external fun play(handle: Long)
    external fun pause(handle: Long)
    external fun close(handle: Long)
    external fun volume(handle: Long, gain: Float)
    external fun position(handle: Long): Long
    external fun api(handle: Long): Int
    external fun error(handle: Long): Int
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class NativeAudioOutput(private val config: AudioOutputProvider.OutputConfig,
    private val api: Int, private val exclusive: Boolean, private var device: Int) : AudioOutput {
    private val listeners = mutableSetOf<AudioOutput.Listener>()
    private var handle = open()
    private var gain = 1f
    private var started = false
    private var pausedPosition = 0L
    private var announced = false
    private var submittedFrames = 0L
    private var draining = false
    private var routeChanged = false
    val actualApi get() = if (handle != 0L) NativeAudioBridge.api(handle) else api
    private val channels get() = Integer.bitCount(config.channelMask)
    private fun open(): Long {
        check(NativeAudioBridge.available) { "原生音频库不可用" }
        return NativeAudioBridge.open(api, config.sampleRate, channels, device, exclusive)
            .also { check(it != 0L) { "设备未接受请求的输出接口、采样率或独占模式" } }
    }
    override fun play() { started = true; if (handle != 0L) NativeAudioBridge.play(handle) }
    override fun pause() {
        if (handle != 0L) {
            NativeAudioBridge.pause(handle)
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
        NativeAudioBridge.close(handle); handle = 0
        submittedFrames = 0; draining = false; announced = false; routeChanged = false; pausedPosition = 0
        // A route can disappear while flushing. Let the next write request Media3's
        // recoverable output recreation, where the provider can select its fallback.
        handle = try { open() } catch (_: IllegalStateException) { return }
        NativeAudioBridge.volume(handle, gain)
        if (started) NativeAudioBridge.play(handle)
    }
    override fun stop() { draining = true }
    override fun release() { if (handle != 0L) { NativeAudioBridge.close(handle); handle = 0 }; listeners.toList().forEach { it.onReleased() } }
    override fun setVolume(volume: Float) { gain = volume; if (handle != 0L) NativeAudioBridge.volume(handle, volume) }
    override fun isOffloadedPlayback() = false
    override fun getAudioSessionId() = C.AUDIO_SESSION_ID_UNSET
    override fun getSampleRate() = config.sampleRate
    override fun getBufferSizeInFrames() = config.sampleRate / 2L
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
