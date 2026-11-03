package io.github.currencortex.music.core.media

import android.content.Context
import android.net.Uri
import com.netease.cloudmusic.media.audiofx.effects.AudioEffects
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor

data class NeteaseOriginalPreset(val id: Long, val name: String, val type: Int, val bytes: ByteArray)

/** One original engine per playback sink, confined to its audio thread. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class NeteaseNativeEngine(sampleRate: Int, channels: Int, preset: NeteaseOriginalPreset) : AutoCloseable {
    private val effects: AudioEffects
    private val inputResampler: SonicAudioProcessor?
    private val outputResampler: SonicAudioProcessor?
    private var scratch = ByteBuffer.allocateDirect(0)
    private var inputEnded = false
    private var nativeEnded = false
    val outputHeadroom: Int
    init {
        check(available()) { "原版音效引擎未加载" }
        require(sampleRate in 8_000..192_000 && channels in 1..2)
        val parameters = if (preset.type == 1) NeteasePresetParameters.fromJson(preset.bytes.toString(Charsets.UTF_8), channels = channels)
            else { require(preset.type == 2); NeteasePresetParameters.fromFir(preset.bytes) }
        // This vendor's speaker-spatial stage emits 48 kHz PCM for >48 kHz
        // input. Keep its whole chain at 48 kHz and convert back, so Media3
        // still receives the configured PCM rate and an advancing clock.
        val processingRate = if (sampleRate > 48000 && parameters.parameterCalls.any {
            it.first == "setSpeakerSpatialON" && it.second.firstOrNull() == true
        }) 48000 else sampleRate
        outputHeadroom = (8192L * sampleRate / processingRate).toInt()
        fun resampler(from: Int, to: Int) = SonicAudioProcessor().apply {
            setOutputSampleRateHz(to)
            configure(AudioProcessor.AudioFormat(from, channels, C.ENCODING_PCM_16BIT))
            flush(AudioProcessor.StreamMetadata(C.TIME_UNSET))
        }
        inputResampler = if (processingRate != sampleRate) resampler(sampleRate, processingRate) else null
        outputResampler = if (processingRate != sampleRate) resampler(processingRate, sampleRate) else null
        effects = AudioEffects()
        try {
            check(NeteasePcmBridge.initialize(effects.nativeInstance, processingRate, channels)) { "原版音效初始化失败" }
            parameters.applyTo(effects)
        } catch (failure: Throwable) { effects.close(); throw failure }
    }

    private fun nativeBuffer(size: Int): ByteBuffer {
        if (scratch.capacity() < size) scratch = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        scratch.clear()
        return scratch
    }
    private fun queueNative(input: ByteBuffer) {
        if (!input.hasRemaining()) return
        val buffer = nativeBuffer(input.remaining() + 8192)
        val count = NeteasePcmBridge.process(effects.nativeInstance, input, input.position(), input.remaining(), buffer, buffer.capacity())
        check(count >= 0)
        input.position(input.limit())
        buffer.limit(count)
        outputResampler!!.queueInput(buffer)
    }
    private fun convertedOutput(output: ByteBuffer): Int {
        val ready = outputResampler!!.output
        check(ready.remaining() <= output.capacity()) { "Resampled native output exceeds buffer" }
        val count = ready.remaining()
        output.put(ready)
        output.flip()
        return count
    }
    fun process(input: ByteBuffer, output: ByteBuffer): Int {
        val converter = inputResampler ?: return NeteasePcmBridge.process(
            effects.nativeInstance, input, input.position(), input.remaining(), output, output.capacity())
        converter.queueInput(input.duplicate().order(input.order()))
        queueNative(converter.output)
        return convertedOutput(output)
    }

    fun drain(output: ByteBuffer): Int {
        val converter = inputResampler ?: return NeteasePcmBridge.drain(effects.nativeInstance, output, output.capacity())
        if (!inputEnded) { converter.queueEndOfStream(); inputEnded = true; queueNative(converter.output) }
        var count = convertedOutput(output)
        if (count > 0 || nativeEnded) return count
        do {
            val buffer = nativeBuffer(2048)
            // Limit the read, even if a larger scratch allocation was reused.
            count = NeteasePcmBridge.drain(effects.nativeInstance, buffer, 2048)
            check(count >= 0)
            buffer.limit(count)
            if (count > 0) outputResampler!!.queueInput(buffer)
            else { nativeEnded = true; outputResampler!!.queueEndOfStream() }
            output.clear()
            count = convertedOutput(output)
        } while (count == 0 && !nativeEnded)
        return count
    }
    override fun close() { inputResampler?.reset(); outputResampler?.reset(); effects.close() }

    companion object {
        fun configure(context: Context) = NeteaseEngineLibraries.configure(context)
        fun available(): Boolean = NeteaseEngineLibraries.available()
        fun smartDefinition(): ByteArray? = NeteaseEngineLibraries.smartDefinition()
        fun moodDefinition(): ByteArray? = NeteaseEngineLibraries.moodDefinition()
        suspend fun installFromApk(context: Context, uri: Uri) = NeteaseEngineLibraries.installFromApk(context, uri)
        suspend fun installFromInstalled(context: Context) = NeteaseEngineLibraries.installFromInstalled(context)
    }
}

@androidx.annotation.Keep
internal object NeteasePcmBridge {
    external fun initialize(handle: Long, sampleRate: Int, channels: Int): Boolean
    external fun process(handle: Long, input: ByteBuffer, offset: Int, length: Int, output: ByteBuffer, capacity: Int): Int
    external fun drain(handle: Long, output: ByteBuffer, capacity: Int): Int
}
