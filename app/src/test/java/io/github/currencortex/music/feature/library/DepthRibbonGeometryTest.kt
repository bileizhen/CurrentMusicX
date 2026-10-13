package io.github.currencortex.music.feature.library

import org.junit.Assert.*
import org.junit.Test

class DepthRibbonGeometryTest {
    @Test fun overviewTakesTimeToEaseOutAndCanReverseWithoutJumping() {
        val transition = RibbonRevealTransition()
        transition.reveal(true)
        repeat(10) { transition.advance(.02f) }
        assertTrue(transition.value in .2f.. .4f)
        val intermediate = transition.value
        transition.reveal(false)
        assertEquals(intermediate, transition.value, 0f)
        transition.advance(.016f)
        assertTrue(kotlin.math.abs(transition.value - intermediate) < .04f)
        repeat(100) { transition.advance(.02f) }
        assertTrue(transition.value < .005f)
        transition.reveal(true)
        repeat(60) { transition.advance(.02f) }
        assertTrue(transition.value > .97f)
    }
    @Test fun loopHasNoPositionOrDirectionJumpInEitherTravelDirection() {
        for (count in listOf(1, 3, 36, 72)) {
            val path = DepthRibbonPath(count)
            for (p in listOf(-.001f, .001f, 1.3f)) {
                assertTrue((path.point(p) - path.point(p + count)).length() < .001f)
                assertTrue((path.tangent(p) - path.tangent(p + count)).length() < .01f)
            }
        }
    }
    @Test fun arcLengthSamplingDoesNotAccelerateAtTheLoopCrossing() {
        val path = DepthRibbonPath(72)
        val lengths = (0 until 720).map { i -> (path.point(i / 10f + .05f) - path.point(i / 10f)).length() }
        assertTrue(lengths.max() / lengths.min() < 1.04f)
    }
    @Test fun longPlaylistsRemainReachableAndBackwardTravelWrapsToTheLastSong() {
        val count = 1003
        assertEquals(1002, ribbonSongIndex(-1, count))
        assertEquals((0 until count).toSet(), (0 until count).map { ribbonSongIndex(it, count) }.toSet())
        assertEquals(0, ribbonSongIndex(count, count))
    }
}
