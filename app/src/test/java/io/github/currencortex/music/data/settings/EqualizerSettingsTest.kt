package io.github.currencortex.music.data.settings

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class EqualizerSettingsTest {
    @Test fun malformedStoredBandsAreBoundedAndMissingBandsAreFlat() {
        val settings = EqualizerSettings(gains = listOf(9999, -9999, 74)).normalized()
        assertEquals(listOf(1200, -1200, 100, 0, 0), settings.gains)
        assertEquals(settings, settings.normalized())
    }
    @Test fun customEditKeepsOtherBandsAndPresetSelectionNeverExceedsSafeRange() {
        val original = EqualizerSettings(enabled = true, preset = EqualizerPreset.BASS, gains = EqualizerPreset.BASS.gains)
        val custom = original.withBand(2, 777)
        assertEquals(EqualizerPreset.CUSTOM, custom.preset)
        assertEquals(listOf(600, 400, 800, -100, -200), custom.gains)
        assertTrue(custom.enabled)
        EqualizerPreset.entries.filter { it != EqualizerPreset.CUSTOM }.forEach {
            assertEquals(5, it.gains.size)
            assertTrue(it.gains.all { gain -> gain in -1200..1200 })
        }
    }
    @Test fun alternateDeviceBandLayoutsUseLogFrequencyInterpolation() {
        val settings = EqualizerSettings(preset = EqualizerPreset.BASS, gains = EqualizerPreset.BASS.gains)
        assertEquals(600, settings.gainAt(60f))
        assertEquals(400, settings.gainAt(230f))
        assertEquals(500, settings.gainAt(sqrt(60f * 230f)))
        assertEquals(600, settings.gainAt(20f))
        assertEquals(-200, settings.gainAt(20_000f))
    }
}
