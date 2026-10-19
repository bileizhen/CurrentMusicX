package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.feature.lyrics.ui.lyricsFollowStep
import org.junit.Assert.*
import org.junit.Test

class LyricsFollowSpringTest {
    @Test fun differentRefreshRatesFollowTheSamePathWithoutOvershoot() {
        fun follow(hz: Int): Float {
            var remaining = 700f
            var velocity = 0f
            repeat(hz / 2) {
                val step = lyricsFollowStep(remaining, velocity, 1f / hz)
                remaining -= step.distance
                velocity = step.velocity
                assertTrue("A settled target cannot spring past its anchor", remaining >= -.001f)
                assertTrue(step.distance >= 0f)
            }
            return remaining
        }
        assertEquals(follow(60), follow(120), .01f)
        assertEquals(follow(60), follow(30), .01f)
        assertTrue(follow(60) < 3f)
    }

    @Test fun quickLineChangesKeepVelocityWhileASeekCanReverseSmoothly() {
        val moving = lyricsFollowStep(200f, 900f, 1f / 120f)
        val retargeted = lyricsFollowStep(400f, moving.velocity, 1f / 120f)
        val restarted = lyricsFollowStep(400f, 0f, 1f / 120f)
        assertTrue("The following short line carries the previous movement", retargeted.distance > restarted.distance * 2)
        var remaining = -300f
        var velocity = moving.velocity
        repeat(120) {
            val step = lyricsFollowStep(remaining, velocity, 1f / 120f)
            remaining -= step.distance
            velocity = step.velocity
            assertTrue(velocity.isFinite())
        }
        assertEquals(0f, remaining, .01f)
    }
}
