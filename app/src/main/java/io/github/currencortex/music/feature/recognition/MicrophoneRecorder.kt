package io.github.currencortex.music.feature.recognition

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** Foreground-only PCM capture. Every exit stops and releases the microphone. */
class MicrophoneRecorder : RecognitionRecorder {
    override suspend fun record(stop: AtomicBoolean, progress: (Int, Float) -> Unit): ShortArray =
        recordStreaming(stop, progress) { }

    @SuppressLint("MissingPermission") // Permission is requested by the recognition screen before start.
    override suspend fun recordStreaming(stop: AtomicBoolean, progress: (Int, Float) -> Unit,
        onWindow: (ShortArray) -> Unit): ShortArray = withContext(Dispatchers.IO) {
        var recorder: AudioRecord? = null
        var sampleRate = 0
        for (rate in listOf(8000, 16000, 48000)) {
            val size = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (size <= 0) continue
            val next = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(size * 2, rate / 2))
            if (next.state == AudioRecord.STATE_INITIALIZED) { recorder = next; sampleRate = rate; break }
            next.release()
        }
        val audio = recorder ?: error("无法打开麦克风，请确认未被其他应用占用")
        val output = ShortArray(RecognitionWindows.MAX_SAMPLES)
        val windows = RecognitionWindows()
        val buffer = ShortArray(sampleRate / 10)
        val factor = sampleRate / 8000
        var size = 0
        var sum = 0; var count = 0
        try {
            audio.startRecording()
            check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "无法开始录音，请稍后重试" }
            while (size < output.size && !stop.get()) {
                currentCoroutineContext().ensureActive()
                val read = audio.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                check(read >= 0) { "麦克风录音中断，请重试" }
                if (read == 0) { delay(10); continue }
                var peak = 0
                for (index in 0 until read) {
                    peak = maxOf(peak, abs(buffer[index].toInt()))
                    sum += buffer[index]; count++
                    if (count == factor) {
                        if (size < output.size) output[size++] = (sum / factor).toShort()
                        sum = 0; count = 0
                    }
                }
                progress(size / 8000, (peak / 12000f).coerceIn(0f, 1f))
                windows.emitAvailable(size, { output.copyOf(it) }, onWindow)
            }
            output.copyOf(size)
        } finally {
            try { audio.stop() } catch (_: IllegalStateException) { }
            audio.release(); buffer.fill(0); output.fill(0)
        }
    }
}
