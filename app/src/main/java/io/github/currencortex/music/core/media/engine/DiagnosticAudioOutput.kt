package io.github.currencortex.music.core.media.engine

import android.media.AudioDeviceInfo
import android.media.AudioTimestamp
import android.os.SystemClock
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioTrackAudioOutput
import io.github.currencortex.music.core.media.NativeAudioOutput
import java.nio.ByteBuffer

/** Read metrics at most once a second on the writer thread, never from the native audio callback. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class DiagnosticAudioOutput(private val delegate: AudioOutput, private val config: AudioOutputProvider.OutputConfig,
    private val diagnostics: AudioDiagnostics, private val active: () -> Boolean,
    private val backend: String, private val routeName: (Int) -> String?) : AudioOutput by delegate {
    private var last = Long.MIN_VALUE
    private var frames = 0L
    private val frameBytes = Integer.bitCount(config.channelMask) * when (config.encoding) { 3 -> 1; 21 -> 3; 22, 4 -> 4; else -> 2 }
    private fun sample(force: Boolean = false) {
        if (!active()) return
        val now = SystemClock.elapsedRealtime()
        if (!force && last != Long.MIN_VALUE && now - last < 1000) return
        last = now
        val native = (delegate as? NativeAudioOutput)?.snapshot()
        val track = (delegate as? AudioTrackAudioOutput)?.audioTrack
        val route = track?.routedDevice
        val deviceId = native?.deviceId ?: route?.id
        val timestamp = AudioTimestamp()
        val latency = native?.latencyMs ?: if (track != null && track.playState == android.media.AudioTrack.PLAYSTATE_PLAYING &&
            track.getTimestamp(timestamp) && frames > 0 && track.sampleRate > 0) {
            val elapsedFrames = (System.nanoTime() - timestamp.nanoTime).coerceAtLeast(0) * track.sampleRate / 1_000_000_000
            ((frames - timestamp.framePosition - elapsedFrames).coerceAtLeast(0) * 1000.0 / track.sampleRate)
        } else null
        diagnostics.update { old -> old.copy(backend = native?.backend ?: backend,
            output = AudioPcmFormat(native?.sampleRate ?: track?.sampleRate ?: delegate.sampleRate.takeIf { it > 0 },
                native?.pcmEncoding ?: track?.audioFormat, native?.channels ?: track?.channelCount),
            formatEvidence = if (old.bitPerfect == BitPerfectEvidence.ANDROID_CONFIGURATION_VERIFIED) old.formatEvidence else when {
                native != null -> "Oboe 已打开流的格式；不等同于最终硬件格式"
                track != null -> "AudioTrack 实际对象格式；系统混音后格式未验证"
                else -> "输出接口报告；最终硬件格式未验证"
            }, deviceId = deviceId, device = route?.productName?.toString() ?: deviceId?.let(routeName),
            softwareBufferFrames = native?.softwareBufferFrames?.toLong(),
            deviceBufferFrames = native?.deviceBufferFrames ?: track?.bufferSizeInFrames,
            capacityFrames = native?.deviceCapacityFrames ?: track?.bufferCapacityInFrames,
            underruns = native?.underrunCount ?: track?.underrunCount,
            latencyMs = latency?.takeIf { it.isFinite() && it >= 0 },
            latencyEvidence = if (latency == null) "系统未提供" else "平台时间戳估计，非声学/USB DAC 实测延迟") }
    }
    override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean {
        val before = buffer.remaining()
        val result = delegate.write(buffer, encodedAccessUnitCount, presentationTimeUs)
        if (frameBytes > 0) frames += (before - buffer.remaining()) / frameBytes
        runCatching { sample() } // Diagnostic failure must not stop PCM delivery.
        return result
    }
    override fun play() { delegate.play(); runCatching { sample(true) } }
    override fun flush() { frames = 0; last = Long.MIN_VALUE; delegate.flush() }
    override fun setPreferredDevice(preferredDevice: AudioDeviceInfo?) { delegate.setPreferredDevice(preferredDevice); last = Long.MIN_VALUE }
}
