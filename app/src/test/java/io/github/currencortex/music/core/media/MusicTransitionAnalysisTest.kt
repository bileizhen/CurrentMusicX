package io.github.currencortex.music.core.media

import org.junit.Assert.*
import org.junit.Test

class MusicTransitionAnalysisTest {
    @Test fun energyChangesProduceDifferentPlansAndLouderIncomingIsAttenuated() {
        val loud = smartMusicTransition(MusicEnergyProfile(.2f), MusicEnergyProfile(.4f))
        val quiet = smartMusicTransition(MusicEnergyProfile(.02f), MusicEnergyProfile(.03f))
        val tail = smartMusicTransition(MusicEnergyProfile(.2f, .2f), MusicEnergyProfile(.1f, leadingSilenceMs = 3000))
        assertTrue(loud.durationMs < quiet.durationMs)
        assertTrue(quiet.durationMs < tail.durationMs)
        assertTrue(loud.incomingGain in .7f.. .99f)
        assertEquals(1500L, tail.incomingStartMs)
        assertEquals(5000L, smartMusicTransition(null, null).durationMs)
    }
    @Test fun equalPowerEnvelopeHasContinuousEndpointsAndMonotonicGains() {
        var previous = 1f to 0f
        for (step in 0..1000) {
            val gains = musicTransitionGains(step / 1000f)
            assertTrue(gains.first <= previous.first + .00001f)
            assertTrue(gains.second >= previous.second - .00001f)
            assertEquals(1f, gains.first * gains.first + gains.second * gains.second, .00001f)
            previous = gains
        }
        assertEquals(0f, previous.first, .00001f)
        assertEquals(1f, musicTransitionGains(1f, .7f).second, .00001f)
        assertEquals(0f, musicTransitionGains(0f, .7f).second, 0f)
    }
    @Test fun decodedWindowsDetectSilenceFallingTailAndStablePulse() {
        val intro = TrackEnergyProbe().apply { enabled = true }
        repeat(10) { intro.publish(0f, 0f) }
        repeat(40) { intro.publish(.1f, .1f) }
        assertEquals(400L, intro.profile()!!.leadingSilenceMs)
        val tail = TrackEnergyProbe().apply { enabled = true }
        repeat(125) { tail.publish(.3f, .1f) }
        repeat(125) { tail.publish(.03f, .01f) }
        assertEquals(.1f, tail.profile()!!.tailRatio, .001f)
        val beat = TrackEnergyProbe().apply { enabled = true }
        repeat(250) { beat.publish(if (it % 12 == 0) .3f else .01f, 0f) }
        assertEquals(480L, beat.profile()!!.beatPeriodMs)
        assertEquals(0L, smartMusicTransition(beat.profile(), intro.profile()).durationMs % 480)
    }
    @Test fun rollingWindowDropsOldMusicAndRejectsInvalidOrDisabledSamples() {
        val probe = TrackEnergyProbe()
        repeat(20) { probe.publish(1f, 1f) }
        assertNull(probe.profile())
        probe.enabled = true
        repeat(250) { probe.publish(1f, 1f) }
        repeat(250) { probe.publish(.1f, .1f) }
        probe.publish(Float.NaN, 0f); probe.publish(0f, Float.POSITIVE_INFINITY)
        assertEquals(.1f, probe.profile()!!.rms, .001f)
        probe.reset(); assertNull(probe.profile())
    }
}
