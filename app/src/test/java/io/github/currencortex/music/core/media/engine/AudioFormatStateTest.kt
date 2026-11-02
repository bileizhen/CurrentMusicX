package io.github.currencortex.music.core.media.engine

import org.junit.Assert.*
import org.junit.Test

class AudioFormatStateTest {
    @Test fun secondaryPrerollStaysPrivateUntilPromotionPublishesItsCachedFormats() {
        val diagnostics = AudioDiagnostics()
        val outgoing = AudioFormatState(); val incoming = AudioFormatState()
        val oldSource = AudioPcmFormat(44100, null, 2); val oldDecoded = AudioPcmFormat(44100, 2, 2)
        outgoing.source(oldSource); outgoing.decoded(oldDecoded); outgoing.publish(diagnostics)
        val newSource = AudioPcmFormat(96000, 21, 2); val newDecoded = AudioPcmFormat(96000, 22, 2)
        incoming.source(newSource); incoming.decoded(newDecoded)
        assertEquals(oldSource, diagnostics.state.value.source); assertEquals(oldDecoded, diagnostics.state.value.decoded)
        diagnostics.resetTrack(); incoming.publish(diagnostics)
        assertEquals(newSource, diagnostics.state.value.source); assertEquals(newDecoded, diagnostics.state.value.decoded)
        assertEquals(AudioPcmFormat(), diagnostics.state.value.output)
    }
    @Test fun codecChangeClearsStaleDecodedEvidenceUntilTheNewSinkReportsIt() {
        val diagnostics = AudioDiagnostics(); val formats = AudioFormatState()
        formats.source(AudioPcmFormat(192000, 21, 2)); formats.decoded(AudioPcmFormat(192000, 22, 2))
        formats.source(AudioPcmFormat(48000, null, 1)); formats.publish(diagnostics)
        assertEquals(AudioPcmFormat(48000, null, 1), diagnostics.state.value.source)
        assertEquals(AudioPcmFormat(), diagnostics.state.value.decoded)
        formats.decoded(AudioPcmFormat(48000, 2, 1)); formats.publish(diagnostics)
        assertEquals(AudioPcmFormat(48000, 2, 1), diagnostics.state.value.decoded)
    }
    @Test fun publicationNeverPromotesSourceDeclarationIntoActualOutputOrChangesRouting() {
        val diagnostics = AudioDiagnostics(); val formats = AudioFormatState()
        diagnostics.update { it.copy(backend = "AudioTrack", deviceId = 7, output = AudioPcmFormat(48000, 2, 2)) }
        formats.source(AudioPcmFormat(192000, 21, 2)); formats.publish(diagnostics)
        assertEquals(AudioPcmFormat(48000, 2, 2), diagnostics.state.value.output)
        assertEquals(7, diagnostics.state.value.deviceId); assertEquals("AudioTrack", diagnostics.state.value.backend)
        formats.clear(); formats.publish(diagnostics)
        assertEquals(AudioPcmFormat(), diagnostics.state.value.source); assertEquals(AudioPcmFormat(), diagnostics.state.value.decoded)
    }
}
