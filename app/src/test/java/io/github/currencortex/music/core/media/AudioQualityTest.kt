package io.github.currencortex.music.core.media
import org.junit.Assert.*
import org.junit.Test
class AudioQualityTest {
    @Test fun apiValuesRoundTripAndUnknownFallsBackToAuto() {
        assertEquals(setOf("auto","jymaster","jyeffect","sky","vivid","dolby","hires","lossless","exhigh","higher","standard"), AudioQuality.entries.map { it.value }.toSet())
        AudioQuality.entries.forEach { assertEquals(it, AudioQuality.from(it.value)) }
        assertEquals(AudioQuality.AUTO, AudioQuality.from("invalid"))
    }
    @Test fun highResolutionAndEffectLevelsHaveDistinctLabels() {
        assertEquals("Hi-Res", AudioQuality.from("hires").label)
        assertEquals("高清臻音", AudioQuality.from("jyeffect").label)
        assertEquals("臻音全景声", AudioQuality.from("vivid").label)
        assertEquals(AudioQuality.entries.size, AudioQuality.entries.map { it.label }.toSet().size)
    }
}
