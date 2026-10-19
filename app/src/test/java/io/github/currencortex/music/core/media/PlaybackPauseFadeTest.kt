package io.github.currencortex.music.core.media

import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackPauseFadeTest {
    @Test fun pauseRampsBeforeStoppingAndKeepsThePausedPipelineSilent() = runTest {
        val samples = mutableListOf<Float>()
        var pauses = 0
        val fade = PlaybackPauseFade(this, samples::add, { pauses++ }, nowMs = { testScheduler.currentTime })
        fade.pause(true); runCurrent()
        assertTrue(fade.pausedIntent)
        assertEquals(1f, fade.gain, 0f)
        advanceTimeBy(100); runCurrent()
        assertTrue(fade.gain in .45f.. .6f)
        assertEquals(0, pauses)
        advanceTimeBy(100); runCurrent()
        assertEquals(0f, fade.gain, 0f)
        assertEquals(0, pauses)
        advanceTimeBy(15); runCurrent()
        assertEquals(1, pauses)
        assertFalse(fade.fadingOut)
        assertTrue(samples.zipWithNext().all { (a, b) -> b <= a })
        advanceTimeBy(1000)
        assertEquals(0f, fade.gain, 0f)
    }

    @Test fun quickResumeTurnsAroundAtTheCurrentGainAndCannotPauseLater() = runTest {
        var pauses = 0
        val fade = PlaybackPauseFade(this, {}, { pauses++ }, nowMs = { testScheduler.currentTime })
        fade.pause(true); advanceTimeBy(80); runCurrent()
        val atPress = fade.gain
        fade.resume(); runCurrent()
        assertEquals(atPress, fade.gain, 0f)
        assertFalse(fade.pausedIntent)
        advanceUntilIdle()
        assertEquals(1f, fade.gain, 0f)
        assertEquals(0, pauses)
    }

    @Test fun repeatedPauseDoesNotRestartTheDeadlineOrRunTwice() = runTest {
        var pauses = 0
        val fade = PlaybackPauseFade(this, {}, { pauses++ }, nowMs = { testScheduler.currentTime })
        fade.pause(true); advanceTimeBy(100); fade.pause(true)
        advanceTimeBy(115); runCurrent()
        assertEquals(1, pauses)
        fade.pause(true); advanceUntilIdle()
        assertEquals(1, pauses)
    }

    @Test fun sourceReplacementInvalidatesThePendingPauseAndRestoresNormalGain() = runTest {
        var pauses = 0
        val fade = PlaybackPauseFade(this, {}, { pauses++ }, nowMs = { testScheduler.currentTime })
        fade.pause(true); advanceTimeBy(100)
        fade.reset(); advanceUntilIdle()
        assertEquals(0, pauses)
        assertFalse(fade.pausedIntent)
        assertEquals(1f, fade.gain, 0f)
    }

    @Test fun pauseDuringAResumeStartsFromItsCurrentGainAndStopsExactlyOnce() = runTest {
        var pauses = 0
        val fade = PlaybackPauseFade(this, {}, { pauses++ }, nowMs = { testScheduler.currentTime })
        fade.pause(true); advanceUntilIdle()
        fade.resume(); advanceTimeBy(80); runCurrent()
        val atPress = fade.gain
        fade.pause(true); runCurrent()
        assertEquals(atPress, fade.gain, 0f)
        advanceUntilIdle()
        assertEquals(2, pauses)
        assertEquals(0f, fade.gain, 0f)
    }

    @Test fun inaudiblePauseAndEndOfTrackStopImmediatelyWithoutAdvancing() = runTest {
        var pauses = 0
        val fade = PlaybackPauseFade(this, {}, { pauses++ }, nowMs = { testScheduler.currentTime })
        fade.pause(false)
        assertEquals(1, pauses)
        fade.reset(); fade.pause(true); advanceTimeBy(40)
        fade.finishPause(); advanceUntilIdle()
        assertEquals(2, pauses)
        assertEquals(0f, fade.gain, 0f)
    }
}
