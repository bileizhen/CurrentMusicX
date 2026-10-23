package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.feature.lyrics.ui.*
import org.junit.Assert.*
import org.junit.Test
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import kotlin.math.abs

class MonetLyricMotionTest {
    @Test fun expandedViewportAddsTracksAndKeepsLongSongsBounded() {
        val normal = monetViewportRadius(360f, 30f)
        val tall = monetViewportRadius(720f, 30f)
        assertTrue(tall > normal)
        assertTrue(monetViewportRadius(720f, 60f) < tall)
        assertEquals(4, monetViewportRadius(0f, 30f))
        assertEquals(24, monetViewportRadius(100_000f, 12f))
    }
    @Test fun scaleDoesNotSnapTheLastPercentWhenThePositionIsStillSettling() {
        for ((from, to) in listOf(.72f to 1f, 1f to .72f)) {
            val animation = TargetBasedAnimation(monetScaleSpring(), Float.VectorConverter, from, to)
            for (hz in listOf(60, 120)) {
                val previousFrame = animation.durationNanos - 1_000_000_000L / hz
                val lastStep = abs(to - animation.getValueFromNanos(previousFrame))
                println("scale $from->$to ${hz}Hz durationMs=${animation.durationNanos / 1_000_000} finalStep=$lastStep")
                assertTrue("The final scale step must stay below .05 percent, rather than snapping a visible glyph width: $lastStep",
                    lastStep < .0005f)
            }
        }
    }
    @Test fun wrappedActiveParagraphAndSubtitleStayBetweenScaledNeighbours() {
        val heights = listOf(40f, 180f, 60f)
        val scales = listOf(.72f, 1f, .72f)
        val y = monetTrackPositions(heights, scales, 1, 300f, 36.5f, setOf(1))
        assertEquals(300f, y[1] + heights[1] / 2, .001f)
        assertTrue(y[0] + heights[0] * scales[0] < y[1])
        assertTrue(y[1] + heights[1] < y[2])
        val collapsed = monetTrackPositions(listOf(40f, 80f, 60f), scales, 1, 300f, 36.5f, setOf(1))
        assertEquals(300f, collapsed[1] + 40f, .001f)
        assertTrue(collapsed[2] < y[2])
    }

    @Test fun glowReachesPeakAfterWordAndPersistsAcrossLineHandoff() {
        assertEquals(0f, monetGlow(1000, 1000, 2000, 2200), 0f)
        assertEquals(1f, monetGlow(2180, 1000, 2000, 2200), .001f)
        assertTrue(monetGlow(2400, 1000, 2000, 2200) > .5f)
        assertEquals(0f, monetGlow(3050, 1000, 2000, 2200), .001f)
        assertEquals(0f, monetGlow(900, 1000, 2000, 2200), 0f)
    }

    @Test fun mixedWidthGraphemesUseEqualTimeAndFeatherClearsAtWordEnd() {
        assertEquals(8f, monetFillWidth(.5f, listOf(8f, 30f)), .001f)
        assertEquals(23f, monetFillWidth(.75f, listOf(8f, 30f)), .001f)
        assertEquals(38f, monetFillWidth(1f, listOf(8f, 30f)), .001f)
        val edge = monetSweepSoftness(36f)
        assertEquals(38f + edge, monetSweepEnd(38f, 38f, edge), .001f)
        assertEquals(0f, monetSweepEnd(0f, 38f, edge), 0f)
    }

    @Test fun waitingAndPassedHaveDistinctToneAndUpstreamSpringMassIsPreserved() {
        assertTrue(monetTone(false, 1).opacity > monetTone(false, -1).opacity)
        assertEquals(.68f, monetTone(false, 9).scale, .001f)
        assertEquals(142f / .82f, MONET_SCROLL_STIFFNESS, .001f)
        assertEquals(150f / .78f, MONET_SCALE_STIFFNESS, .001f)
        assertTrue(MONET_SCROLL_DAMPING > 1f)
    }
}
