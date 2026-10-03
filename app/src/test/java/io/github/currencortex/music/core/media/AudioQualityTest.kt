package io.github.currencortex.music.core.media
import org.junit.Assert.*
import org.junit.Test
class AudioQualityTest {
    @Test fun apiValuesRoundTripAndUnknownFallsBackToAuto() {
        assertEquals(listOf("auto","jymaster","jyeffect","sky","hires","lossless","exhigh","standard"), AudioQuality.entries.map { it.value })
        AudioQuality.entries.forEach { assertEquals(it, AudioQuality.from(it.value)) }
        assertEquals(AudioQuality.AUTO, AudioQuality.from("invalid"))
    }
}
