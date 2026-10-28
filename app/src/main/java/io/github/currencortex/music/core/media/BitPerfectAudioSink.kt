package io.github.currencortex.music.core.media

import android.content.Context
import android.media.AudioDeviceInfo
import android.os.Build
import androidx.media3.common.*
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.Clock
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.*
import io.github.currencortex.music.data.settings.AudioOutputMode
import io.github.currencortex.music.data.settings.AudioOutputSettings
import java.nio.ByteBuffer

/** Bypasses DefaultAudioSink's int16/float conversion. Only codec padding is removed;
 * every remaining PCM byte reaches AudioOutput unchanged, including 24/32-bit PCM. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class BitPerfectAudioSink(normal: AudioSink, private val settings: () -> AudioOutputSettings,
    private val report: (String) -> Unit,
    private val open: (Format, AudioAttributes, Clock) -> BitPerfectStream,
) : ForwardingAudioSink(normal) {
    constructor(context: Context, normal: AudioSink, settings: () -> AudioOutputSettings, report: (String) -> Unit) :
        this(normal, settings, report, { format, attributes, clock ->
            if (settings().mode == AudioOutputMode.NETEASE_USB)
                NeteaseUsbStream.open(context, format, settings(), report)
            else if (Build.VERSION.SDK_INT >= 34)
                AndroidBitPerfectStream.open(context, format, settings(), attributes, clock, report)
            else throw BitPerfectFailure(BitPerfectFailureCode.ANDROID_VERSION, "Bit-perfect 需要 Android API 34 或更新版本")
        })
    private fun selected() = settings().mode.rawPcm
    private val label get() = if (settings().mode == AudioOutputMode.NETEASE_USB) "USB 独占（网易云）" else "Bit-perfect"
    private var active = false
    private var format = Format.Builder().build()
    private var next: AudioSink.AudioSinkConfig? = null
    private var stream: BitPerfectStream? = null
    private var attributes = AudioAttributes.DEFAULT
    private var clock = Clock.DEFAULT
    private var listener: AudioSink.Listener? = null
    private val trim = TrimmingAudioProcessor()
    private var pending = AudioProcessor.EMPTY_BUFFER
    private var written = 0L
    private var start = C.TIME_UNSET
    private var discontinuity = false
    private var ending = false
    private var stopped = false
    private var playing = false
    private var volume = 1f
    private var trimEnded = false
    private var frameSize = 0
    override fun setListener(listener: AudioSink.Listener) { this.listener = listener; super.setListener(listener) }
    override fun setClock(clock: Clock) { this.clock = clock; super.setClock(clock) }
    override fun supportsFormat(format: Format) = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED
    override fun getFormatSupport(format: Format): Int = if (!selected()) super.getFormatSupport(format) else
        if (format.sampleMimeType == MimeTypes.AUDIO_RAW && format.pcmEncoding in PCM_ENCODINGS)
            AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY else AudioSink.SINK_FORMAT_UNSUPPORTED
    override fun getFormatOffloadSupport(format: Format) = if (selected()) AudioOffloadSupport.DEFAULT_UNSUPPORTED else super.getFormatOffloadSupport(format)
    override fun configure(config: AudioSink.AudioSinkConfig) {
        if (active && stream != null) { next = config; return }
        active = selected()
        if (!active) { super.configure(config); return }
        format = config.format
        CurrentMusicAudioDiagnostics.format("decodedPcm.sink", format)
        val mapping = config.outputChannelMapping
        if (getFormatSupport(format) == AudioSink.SINK_FORMAT_UNSUPPORTED || format.sampleRate <= 0 || format.channelCount <= 0 ||
            (mapping != null && (mapping.length() != format.channelCount || (0 until mapping.length()).any { mapping[it] != it }))) {
            val reason = "$label 已停止：不支持此 PCM 格式或声道重排"
            report(reason)
            throw AudioSink.ConfigurationException(reason, format)
        }
        frameSize = Util.getPcmFrameSize(format.pcmEncoding, format.channelCount)
        trim.setTrimFrameCount(format.encoderDelay, format.encoderPadding)
        trim.configure(AudioProcessor.AudioFormat(format))
        clearPosition()
    }
    private fun clearPosition() {
        written = 0; start = C.TIME_UNSET; ending = false; stopped = false; trimEnded = false; discontinuity = false
        pending = AudioProcessor.EMPTY_BUFFER
        trim.flush(AudioProcessor.StreamMetadata(C.TIME_UNSET))
    }
    private fun fail(error: Exception): Nothing {
        val message = "$label 已停止：${error.message ?: "输出条件已改变"}"
        CurrentMusicAudioDiagnostics.record("finalState=FAILED reason=${error.javaClass.simpleName} detail=${error.message?.take(600)}")
        report(message)
        throw AudioSink.InitializationException(message, 0, format, false, error)
    }
    private fun checkStream() {
        try { stream?.verify() } catch (error: Exception) { releaseRaw(); fail(error) }
    }
    private fun ensureStream() {
        if (stream != null) return
        try {
            stream = open(format, attributes, clock)
            stream!!.output.addListener(object : AudioOutput.Listener {
                override fun onPositionAdvancing(time: Long) { listener?.onPositionAdvancing(time) }
                override fun onOffloadDataRequest() = Unit
                override fun onOffloadPresentationEnded() = Unit
                override fun onUnderrun() { listener?.onUnderrun(0, 0, 0) }
                override fun onReleased() = Unit
            })
            // Never scale PCM. Ducking/mute pauses output instead of changing samples.
            if (playing && volume == 1f) stream!!.output.play()
            else if (volume != 1f) report("$label 已暂停：静音或音频焦点衰减；请使用 DAC 调节音量")
        } catch (error: Exception) { releaseRaw(); fail(error) }
    }
    private fun drain() {
        if (!pending.hasRemaining()) return
        checkStream()
        val before = pending.remaining()
        try { stream!!.output.write(pending, 1, start) }
        catch (error: AudioOutput.WriteException) {
            CurrentMusicAudioDiagnostics.record("failure=WRITE_FAILED errorCode=${error.errorCode}")
            releaseRaw()
            report("$label 已停止：[WRITE_FAILED] USB 写入失败，错误码 ${error.errorCode}")
            throw AudioSink.WriteException(error.errorCode, format, false)
        } catch (error: Exception) { releaseRaw(); fail(BitPerfectFailure(BitPerfectFailureCode.WRITE_FAILED, "音频写入异常（${error.javaClass.simpleName}）", error)) }
        written += before - pending.remaining()
    }
    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        next?.let { config ->
            playToEndOfStream()
            if (hasPendingData()) return false
            releaseRaw(); next = null; configure(config)
        }
        if (!active) return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        ensureStream(); checkStream()
        drain()
        if (pending.hasRemaining()) return false
        if (start == C.TIME_UNSET) start = presentationTimeUs.coerceAtLeast(0)
        if (discontinuity) {
            start = presentationTimeUs - durationUs(); discontinuity = false; listener?.onPositionDiscontinuity()
        }
        if (trim.isActive) { trim.queueInput(buffer); pending = trim.output } else pending = buffer
        drain()
        return !buffer.hasRemaining() && !pending.hasRemaining()
    }
    private fun durationUs() = if (frameSize == 0) 0L else written / frameSize * 1_000_000 / format.sampleRate
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        if (!active) return super.getCurrentPositionUs(sourceEnded)
        if (start == C.TIME_UNSET) return AudioSink.CURRENT_POSITION_NOT_SET
        return start + (stream?.output?.positionUs ?: 0L).coerceIn(0, durationUs())
    }
    override fun playToEndOfStream() {
        if (!active) { super.playToEndOfStream(); return }
        try { checkStream() } catch (_: AudioSink.InitializationException) { throw AudioSink.WriteException(-1, format, false) }
        drain()
        if (pending.hasRemaining()) return
        if (trim.isActive && !trimEnded) {
            // This is a real stream boundary, not a processor drain for speed changes.
            // Mark reconfiguration so TrimmingAudioProcessor discards codec end padding.
            trim.configure(AudioProcessor.AudioFormat(format))
            trim.queueEndOfStream(); trimEnded = true; pending = trim.output; drain()
        }
        if (pending.hasRemaining()) return
        ending = true
        if (!stopped) { stream?.output?.stop(); stopped = true }
    }
    override fun hasPendingData() = if (!active) super.hasPendingData() else
        pending.hasRemaining() || (stream != null && (stream!!.output.positionUs.coerceAtLeast(0) < durationUs()))
    override fun isEnded() = if (!active) super.isEnded() else ending && !hasPendingData()
    override fun handleDiscontinuity() { if (active) discontinuity = true else super.handleDiscontinuity() }
    override fun play() { playing = true; if (active) { if (volume == 1f) stream?.output?.play() } else super.play() }
    override fun pause() { playing = false; if (active) stream?.output?.pause() else super.pause() }
    override fun setVolume(volume: Float) {
        this.volume = volume
        if (!active) { super.setVolume(volume); return }
        if (volume != 1f) { stream?.output?.pause(); report("$label 已暂停：静音或音频焦点衰减；请使用 DAC 调节音量") }
        else if (playing) stream?.output?.play()
    }
    override fun setPlaybackParameters(parameters: PlaybackParameters) { super.setPlaybackParameters(if (selected()) PlaybackParameters.DEFAULT else parameters) }
    override fun getPlaybackParameters() = if (active || selected()) PlaybackParameters.DEFAULT else super.getPlaybackParameters()
    override fun setSkipSilenceEnabled(enabled: Boolean) = super.setSkipSilenceEnabled(enabled && !selected())
    override fun getSkipSilenceEnabled() = !active && super.getSkipSilenceEnabled()
    override fun setAudioAttributes(attributes: AudioAttributes) { this.attributes = attributes; super.setAudioAttributes(attributes) }
    override fun setAuxEffectInfo(info: AuxEffectInfo) { if (!selected()) super.setAuxEffectInfo(info) }
    override fun setPreferredDevice(device: AudioDeviceInfo?) { super.setPreferredDevice(device) }
    override fun getAudioTrackBufferSizeUs() = if (active) (stream?.output?.bufferSizeInFrames ?: 0) * 1_000_000 / format.sampleRate else super.getAudioTrackBufferSizeUs()
    private fun releaseRaw() {
        val previous = stream; stream = null
        runCatching { previous?.close() }.onFailure { CurrentMusicAudioDiagnostics.record("cleanup.stream=${it.javaClass.simpleName}") }
    }
    override fun flush() { releaseRaw(); next = null; clearPosition(); super.flush() }
    override fun reset() { flush(); active = false; trim.reset(); super.reset() }
    override fun release() { releaseRaw(); trim.reset(); super.release() }
    companion object { val PCM_ENCODINGS = setOf(C.ENCODING_PCM_8BIT, C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT) }
}
