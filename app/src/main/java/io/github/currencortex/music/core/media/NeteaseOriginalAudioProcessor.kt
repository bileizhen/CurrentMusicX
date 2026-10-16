package io.github.currencortex.music.core.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/** Original DSP owns PCM before the level meter; no audio-session/global effects are used. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class NeteaseOriginalAudioProcessor(
    private val selection: () -> NeteaseOriginalPreset?,
    private val failure: (NeteaseOriginalPreset?, String) -> Unit,
    private val processing: (NeteaseOriginalPreset) -> Unit = {},
) : BaseAudioProcessor() {
    private var engine: NeteaseNativeEngine? = null
    private var active: NeteaseOriginalPreset? = null
    private var failed: NeteaseOriginalPreset? = null
    private var draining = false
    private var drained = true
    private var reported: NeteaseOriginalPreset? = null

    // Remain in the chain while disabled, including unsupported formats. A later
    // selection must still get a buffer to reject safely and restore Android EQ.
    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat = inputAudioFormat

    override fun onFlush(streamMetadata: StreamMetadata) { release(); failed = null; draining = false; drained = true }
    override fun onReset() { release(); failed = null; draining = false; drained = true }
    private fun release() { engine?.close(); engine = null; active = null; reported = null }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val selected = selection()
        if (selected !== active) {
            release()
            if (selected !== failed && selected != null) {
                try {
                    require(inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount in 1..2 &&
                        inputAudioFormat.sampleRate in 8_000..192_000) { "原版引擎不支持当前格式" }
                    engine = NeteaseNativeEngine(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, selected)
                    active = selected
                } catch (_: Exception) { reject(selected) }
                catch (_: LinkageError) { reject(selected) }
            }
        }
        val native = engine
        if (native == null) { replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip(); return }
        try {
            val output = replaceOutputBuffer(inputBuffer.remaining() + 8192)
            val count = native.process(inputBuffer, output)
            check(count >= 0 && count % inputAudioFormat.bytesPerFrame == 0)
            inputBuffer.position(inputBuffer.limit())
            output.limit(count)
            if (count > 0 && selected != null && reported !== selected) {
                reported = selected
                processing(selected)
            }
        } catch (_: Exception) {
            reject(selected)
            replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
        } catch (_: LinkageError) {
            reject(selected)
            replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
        }
    }

    private fun reject(preset: NeteaseOriginalPreset?) {
        release(); failed = preset; drained = true
        failure(preset, "原版音效处理失败，已恢复本机音效")
    }

    override fun onQueueEndOfStream() { draining = engine != null; drained = !draining }
    override fun getOutput(): ByteBuffer {
        val ready = super.getOutput()
        if (ready.hasRemaining() || !draining || drained) return ready
        return try {
            val output = replaceOutputBuffer(32768)
            val count = engine?.drain(output) ?: 0
            check(count >= 0 && count % inputAudioFormat.bytesPerFrame == 0)
            output.limit(count)
            drained = count == 0
            super.getOutput()
        } catch (_: Exception) { reject(active); EMPTY_BUFFER }
        catch (_: LinkageError) { reject(active); EMPTY_BUFFER }
    }
    override fun isEnded(): Boolean = super.isEnded() && drained
}
