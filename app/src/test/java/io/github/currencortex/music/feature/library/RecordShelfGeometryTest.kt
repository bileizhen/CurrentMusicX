package io.github.currencortex.music.feature.library

import org.junit.Assert.*
import org.junit.Test

class RecordShelfGeometryTest {
    @Test fun windowIsBoundedAndEveryTrackOfALongPlaylistIsReachableWithoutDuplicates() {
        assertTrue(shelfWindow(0f, 0).isEmpty())
        assertEquals(listOf(0), shelfWindow(0f, 1).toList())
        for (index in 0 until 1003) {
            val window = shelfWindow(index.toFloat(), 1003)
            assertTrue(index in window)
            assertTrue(window.count() <= 29)
            assertEquals(window.count(), window.map { Math.floorMod(it, 1003) }.distinct().size)
        }
        assertTrue(shelfWindow(0f, 1003).first < 0)
        for (count in listOf(1, 2, 3, 15, 29, 30)) {
            val indices = shelfWindow(-2f, count).map { Math.floorMod(it, count) }
            assertEquals(minOf(29, count), indices.size)
            assertEquals(indices.size, indices.distinct().size)
        }
    }

    @Test fun parallelCaseEdgesKeepTheirWindingAndExtractionMovesForwardWithoutScaling() {
        val rest = FloatArray(8); val pulled = FloatArray(8)
        for (distance in -10..18) {
            shelfQuad(distance.toFloat(), 1080f, 150f, 1500f, 0f, rest)
            shelfQuad(distance.toFloat(), 1080f, 150f, 1500f, 1f, pulled)
            assertTrue(rest.all { it.isFinite() })
            assertTrue(rest[2] > rest[0]); assertTrue(rest[7] > rest[1])
            assertEquals(rest[2] - rest[0], pulled[2] - pulled[0], .001f)
            assertTrue(pulled[0] > rest[0]); assertTrue(pulled[1] > rest[1])
            assertEquals(rest[2] - rest[0], rest[4] - rest[6], .001f)
        }
    }

    @Test fun extractionKeepsItsDirectionAcrossViewportAspectRatiosAndClearsTheNearSleeve() {
        val rest = FloatArray(8); val pulled = FloatArray(8); val near = FloatArray(8)
        for (height in listOf(1100f, 1500f, 2200f)) {
            shelfQuad(0f, 1080f, 150f, height, 0f, rest)
            shelfQuad(0f, 1080f, 150f, height, 1f, pulled)
            shelfQuad(-1f, 1080f, 150f, height, 0f, near)
            assertEquals(.095f / .367f, (pulled[1] - rest[1]) / (pulled[0] - rest[0]), .00001f)
            assertTrue("The foreground sleeve occludes the starting slot", rest[0] < near[2])
            assertTrue("The pulled sleeve just clears the foreground sleeve", pulled[0] >= near[2] - 2f)
        }
    }

    @Test fun allFourCornerTracksStayCollinearAcrossTheWholeRackAndViewportSizes() {
        val previous = FloatArray(8); val current = FloatArray(8)
        for (height in listOf(1100f, 1500f, 2200f)) {
            shelfQuad(-10f, 1080f, 150f, height, 0f, previous)
            for (distance in -9..18) {
                shelfQuad(distance.toFloat(), 1080f, 150f, height, 0f, current)
                for (corner in 0..3) {
                    assertEquals(1080f * .069f, current[corner * 2] - previous[corner * 2], .001f)
                    assertEquals(-1080f * .046f, current[corner * 2 + 1] - previous[corner * 2 + 1], .001f)
                }
                current.copyInto(previous)
            }
        }
        assertEquals(0f, shelfEntryOffsetX(1.30f, -10f, 1080f, -500f), .001f)
    }

    @Test fun entranceFollowsTrackedReferencePositionsAndRearCasesSettleBeforeTheFront() {
        // 724 px reference screen; tracked top-left corners at 0.50 and 0.75 seconds.
        for ((time, expected) in listOf(.50f to floatArrayOf(523f, 541f, 617f),
            .75f to floatArrayOf(167f, 315f, 499f))) {
            for ((i, distance) in listOf(0f, 4f, 8f).withIndex()) {
                val restingX = floatArrayOf(90f, 292f, 496f)[i]
                assertEquals(expected[i], restingX + shelfEntryOffsetX(time, distance, 724f, restingX), 18f)
            }
        }
        // Foreground right-top corners: delayed starts, then the same aligned resting plane.
        for ((time, expected) in listOf(.65f to floatArrayOf(574f, 545f, 542f),
            .85f to floatArrayOf(388f, 280f, 196f))) {
            for ((i, distance) in listOf(-1f, -4f, -7f).withIndex()) {
                val rightEdge = floatArrayOf(355f, 205f, 55f)[i]
                assertEquals(expected[i], rightEdge + shelfEntryOffsetX(time, distance, 724f, rightEdge - 315f), 10f)
            }
        }
        assertEquals(0f, shelfEntryOffsetX(.9f, 8f, 724f, 496f), .001f)
        assertTrue(shelfEntryOffsetX(.9f, 0f, 724f, 90f) > 0f)
        assertEquals(0f, shelfEntryOffsetX(1.2f, 0f, 724f, 90f), .001f)
        assertEquals(-35f, shelfEntryOffsetY(127f, 724f), 2f)
    }

    @Test fun settlingFeelsTheSameAtSixtyAndOneHundredTwentyHertzAndEndsExactly() {
        var at60 = 0f; var at120 = 0f
        repeat(30) { at60 = shelfApproach(at60, 1f, 1f / 60) }
        repeat(60) { at120 = shelfApproach(at120, 1f, 1f / 120) }
        assertEquals(at60, at120, .0001f)
        repeat(120) { at120 = shelfApproach(at120, 1f, 1f / 120) }
        assertEquals(1f, at120, 0f)
    }

    @Test fun searchExitStartsAtTheCurrentEntrancePoseAndClearsEveryCaseBeforeTheSwap() {
        for (distance in -10..18) {
            val restingX = 724f * (.12f + distance * .069f)
            for (intro in listOf(.7f, 1.85f)) {
                val initial = shelfEntryOffsetX(intro, distance.toFloat(), 724f, restingX)
                assertEquals(initial, shelfResultsExitOffsetX(0f, distance.toFloat(), 724f, restingX, initial), .001f)
                var previous = initial
                for (frame in 1..42) {
                    val offset = shelfResultsExitOffsetX(frame / 100f, distance.toFloat(), 724f, restingX, initial)
                    assertTrue(offset >= previous - .001f)
                    previous = offset
                }
                assertTrue("Every sleeve must leave the right edge before replacing its artwork", restingX + previous >= 724f)
            }
        }
    }
}
