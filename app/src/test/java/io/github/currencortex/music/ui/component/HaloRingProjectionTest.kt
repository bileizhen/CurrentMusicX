package io.github.currencortex.music.ui.component

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class HaloRingProjectionTest {
    @Test fun perspectivePreservesTheSampledRingBounds() {
        for (depth in listOf(-.22f, 0f, .22f)) {
            var left = Float.POSITIVE_INFINITY; var right = Float.NEGATIVE_INFINITY
            var top = Float.POSITIVE_INFINITY; var bottom = Float.NEGATIVE_INFINITY
            for (i in 0..1440) {
                val theta = i * (2f * PI.toFloat() / 1440f)
                val x = HaloRingProjection.x(400f, cos(theta), sin(theta), 1f, depth)
                val y = HaloRingProjection.y(80f, sin(theta), 1f, depth)
                left = min(left, x); right = max(right, x); top = min(top, y); bottom = max(bottom, y)
            }
            assertEquals(-400f, left, .01f); assertEquals(400f, right, .01f)
            assertEquals(-80f, top, .01f); assertEquals(80f, bottom, .01f)
        }
    }

    @Test fun foreshorteningChangesNearAndFarRimWidthsAndCrossesBothFaces() {
        val nearWidth = abs(HaloRingProjection.y(80f, -1f, 1.04f, .22f) -
            HaloRingProjection.y(80f, -1f, .96f, .22f))
        val farWidth = abs(HaloRingProjection.y(80f, 1f, 1.04f, .22f) -
            HaloRingProjection.y(80f, 1f, .96f, .22f))
        assertTrue("Perspective must affect the actual luminous ribbon", nearWidth > farWidth * 2f)
        for (flip in listOf(1000f, 1800f)) {
            assertTrue(abs(HaloRingProjection.heightScale(flip)) < .01f)
            assertTrue(HaloRingProjection.heightScale(flip - 1f) * HaloRingProjection.heightScale(flip + 1f) < 0f)
        }
        assertTrue("The settled crest keeps its existing rendering", HaloRingProjection.strength(2200f) < .005f)
    }

    @Test fun everyShutterSampleHasFiniteProjectionIncludingEdgeOnFrames() {
        for (time in 850..2000 step 5) {
            val depth = HaloRingProjection.depth(time.toFloat(), .2f)
            for (i in 0..96) {
                val theta = i * (2f * PI.toFloat() / 96f)
                for (radius in listOf(.95f, 1f, 1.05f)) {
                    assertTrue(HaloRingProjection.x(400f, cos(theta), sin(theta), radius, depth).isFinite())
                    assertTrue(HaloRingProjection.y(80f * HaloRingProjection.heightScale(time.toFloat()),
                        sin(theta), radius, depth).isFinite())
                }
            }
        }
    }
}
