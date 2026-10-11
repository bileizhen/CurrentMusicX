package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.feature.lyrics.timeline.*
import org.junit.Assert.*
import org.junit.Test

class LyricsPlaybackClockTest {
    @Test fun smallSampleDriftDoesNotMakeWordsJumpBackwards() {
        val clock = LyricsPlaybackClock()
        clock.update(1, PlaybackAnchor(1000, 0, true))
        assertEquals(1300, clock.positionAt(300).toInt())
        clock.update(1, PlaybackAnchor(1260, 300, true))
        assertEquals(1300, clock.positionAt(300).toInt())
        val positions = (300..600 step 16).map { clock.positionAt(it.toLong()) }
        assertTrue(positions.zipWithNext().all { (a,b) -> b > a })
        assertEquals(1560, clock.positionAt(600).toInt())
    }
    @Test fun seekPauseAndTrackSwitchApplyImmediately() {
        val clock = LyricsPlaybackClock()
        clock.update(1, PlaybackAnchor(1000, 0, true))
        clock.update(1, PlaybackAnchor(8000, 300, true))
        assertEquals(8000, clock.positionAt(300).toInt())
        clock.update(1, PlaybackAnchor(8100, 500, false))
        assertEquals(8100, clock.positionAt(900).toInt())
        clock.update(2, PlaybackAnchor(100, 1000, true, 5000, 2f))
        assertEquals(500, clock.positionAt(1200).toInt())
    }
    @Test fun unchangedSampleDoesNotResetItsInterpolationAnchor() {
        val clock = LyricsPlaybackClock(); val sample = PlaybackAnchor(1000, 0, true)
        clock.update(1, sample); clock.update(1, sample)
        assertEquals(1800, clock.positionAt(800).toInt())
    }
    @Test fun negativeDriftAtSlowSpeedStillMovesForward() {
        val clock = LyricsPlaybackClock()
        clock.update(1, PlaybackAnchor(1000, 0, true, speed = .5f))
        clock.update(1, PlaybackAnchor(990, 300, true, speed = .5f))
        val positions = (300..1000 step 16).map { clock.positionAt(it.toLong()) }
        assertTrue(positions.zipWithNext().all { (a, b) -> b >= a })
    }
}
