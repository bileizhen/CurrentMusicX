package io.github.currencortex.music.core.visualizer

import kotlinx.coroutines.flow.Flow

/** Owned snapshots: implementations must never expose a platform's reusable callback buffer. */
data class AudioCapturePacket(
    val timestampNanos: Long,
    val sampleRateHz: Int,
    val fft: ByteArray,
    val waveform: ByteArray,
)

interface AudioCaptureSource {
    /** Collecting owns capture; cancellation must release it before collection finishes. */
    fun frames(audioSessionId: Int): Flow<AudioCapturePacket>
}
