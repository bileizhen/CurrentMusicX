package io.github.currencortex.music.core.media.engine

import java.util.concurrent.atomic.AtomicReference

/** Per-deck evidence, cached even during silent pre-roll and published only by the current owner. */
class AudioFormatState {
    private data class Formats(val source: AudioPcmFormat = AudioPcmFormat(), val decoded: AudioPcmFormat = AudioPcmFormat())
    private val latest = AtomicReference(Formats())
    fun source(value: AudioPcmFormat) {
        // A new codec/source notification must not retain the previous track's decoded precision.
        latest.updateAndGet { it.copy(source = value, decoded = AudioPcmFormat()) }
    }
    fun decoded(value: AudioPcmFormat) { latest.updateAndGet { it.copy(decoded = value) } }
    fun clear() { latest.set(Formats()) }
    fun publish(diagnostics: AudioDiagnostics) {
        val value = latest.get()
        diagnostics.update { it.copy(source = value.source, decoded = value.decoded) }
    }
}
