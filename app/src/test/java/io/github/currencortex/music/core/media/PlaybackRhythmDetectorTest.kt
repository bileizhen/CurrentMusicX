package io.github.currencortex.music.core.media

import org.junit.Assert.*
import org.junit.Test

class PlaybackRhythmDetectorTest {
    private fun feed(detector: PlaybackRhythmDetector, period: Int, windows: Int, gain: Float = .5f): PlaybackRhythmDetector.Rhythm {
        var result = PlaybackRhythmDetector.Rhythm()
        repeat(windows) { frame ->
            val value = if (frame % period == 0) gain else .0001f
            result = detector.update(value, value * .7f, .04f)
        }
        return result
    }

    @Test fun followsTempoChangesWithinOneStreamWithoutSongMetadata() {
        val detector = PlaybackRhythmDetector()
        assertEquals(75f, feed(detector, 20, 200).bpm!!, 1f)
        assertEquals(150f, feed(detector, 10, 100).bpm!!, 1f)
        assertEquals(50f, feed(detector, 30, 240).bpm!!, 1f)
    }

    @Test fun loudnessDoesNotChangeTempoAndAttacksAreImmediate() {
        val quiet = PlaybackRhythmDetector()
        val loud = PlaybackRhythmDetector()
        assertEquals(1f, quiet.update(.02f, .01f, .04f).pulse, 0f)
        assertEquals(100f, feed(quiet, 15, 150, .02f).bpm!!, 1f)
        assertEquals(100f, feed(loud, 15, 150, .8f).bpm!!, 1f)
    }

    @Test fun steadyToneSilenceAndResetDoNotKeepAnOldTempo() {
        val detector = PlaybackRhythmDetector()
        assertNotNull(feed(detector, 10, 100).bpm)
        var result = PlaybackRhythmDetector.Rhythm()
        repeat(80) { result = detector.update(.1f, .05f, .04f) }
        assertNull(result.bpm)
        assertTrue(result.pulse < .001f)
        assertNotNull(feed(detector, 20, 160).bpm)
        detector.reset()
        assertNull(feed(detector, 20, 20).bpm)
        repeat(80) { result = detector.update(0f, 0f, .04f) }
        assertNull(result.bpm)
    }

    @Test fun nonFiniteInputCannotPoisonTheNextStream() {
        val detector = PlaybackRhythmDetector()
        assertEquals(PlaybackRhythmDetector.Rhythm(), detector.update(Float.NaN, .3f, .04f))
        assertEquals(PlaybackRhythmDetector.Rhythm(), detector.update(.5f, .3f, Float.POSITIVE_INFINITY))
        assertEquals(150f, feed(detector, 10, 100).bpm!!, 1f)
    }
}
