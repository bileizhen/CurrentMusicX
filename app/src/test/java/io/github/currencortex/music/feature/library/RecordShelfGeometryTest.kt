package io.github.currencortex.music.feature.library

import org.junit.Assert.*
import org.junit.Test

class RecordShelfGeometryTest {
    @Test fun portraitLayoutRetainsTheOriginalQuadsWindowsAndEntrancePath() {
        val expected = FloatArray(8)
        val actual = FloatArray(8)
        for ((width, height) in listOf(724f to 1200f, 1080f to 1500f)) {
            val layout = ShelfGeometry(width, 150f, height)
            assertFalse(layout.wide)
            assertEquals(shelfWindow(3.25f, 100), layout.window(3.25f, 100))
            for (distance in -10..18) for (extraction in listOf(0f, .4f, 1f)) {
                shelfQuad(distance.toFloat(), width, 150f, height, extraction, expected)
                layout.quad(distance.toFloat(), extraction, actual)
                assertArrayEquals(expected, actual, .001f)
                assertEquals(shelfEntryOffsetX(.7f, distance.toFloat(), width, actual[0]),
                    layout.entryOffset(.7f, distance.toFloat(), actual[0]), .001f)
            }
        }
    }

    @Test fun wideRackCoversBothEdgesWithoutChangingCaseShapeOrExtraction() {
        for ((width, height, viewportHeight) in listOf(Triple(2400f, 460f, 1080f),
                Triple(1920f, 350f, 1080f), Triple(2560f, 620f, 1440f))) {
            val layout = ShelfGeometry(width, 200f, height, viewportHeight)
            assertTrue(layout.wide)
            val quads = layout.window(.35f, 10_000).map { logical ->
                FloatArray(8).also { layout.quad(logical - .35f, 0f, it) }
            }
            for (fraction in listOf(0f, .02f, .1f, .25f, .5f, .75f, .9f, .98f, 1f)) {
                val x = width * fraction
                assertTrue("A visible case must cross the viewport at $fraction of $width", quads.any {
                    if (it[0] > x || it[2] < x) false else {
                        val u = (x - it[0]) / (it[2] - it[0])
                        val upper = it[1] + (it[3] - it[1]) * u
                        val lower = it[7] + (it[5] - it[7]) * u
                        minOf(lower, viewportHeight) - maxOf(upper, 0f) >= layout.caseScale * .1f
                    }
                })
            }
            for (quad in quads) {
                assertEquals(layout.caseScale * .435f, quad[2] - quad[0], .001f)
                assertEquals(layout.caseScale * .45f, quad[7] - quad[1], .001f)
                assertEquals(layout.caseScale * .132f, quad[3] - quad[1], .001f)
            }
            val rest = FloatArray(8); val pulled = FloatArray(8); val near = FloatArray(8)
            layout.quad(0f, 0f, rest); layout.quad(0f, 1f, pulled); layout.quad(-1f, 0f, near)
            val slope = (near[1] - rest[1]) / (rest[0] - near[0])
            assertTrue("The full-width rack must visibly rise toward the right, rather than become horizontal", slope in .36f.. .58f)
            assertTrue("Steepness must still fit the viewport diagonal", slope <= minOf(.5773503f, .92f * viewportHeight / width) + .00001f)
            for (corner in 0..3) {
                assertEquals(layout.caseScale * .367f, pulled[corner * 2] - rest[corner * 2], .001f)
                assertEquals(layout.caseScale * .095f, pulled[corner * 2 + 1] - rest[corner * 2 + 1], .001f)
            }
            assertTrue("The extracted case must clear the foreground neighbour", pulled[0] >= near[2])
            assertTrue(pulled[5] <= layout.top + height)
        }
    }

    @Test fun wideEntranceAndSearchExitReachTheRealScreenEdge() {
        val layout = ShelfGeometry(2400f, 200f, 460f, 1080f)
        val quad = FloatArray(8)
        for (logical in layout.window(.25f, 1000)) {
            val distance = logical - .25f
            layout.quad(distance, 0f, quad)
            val start = layout.entryOffset(0f, distance, quad[0])
            assertTrue("Incoming artwork must start beyond the full viewport", quad[0] + start >= layout.viewportWidth)
            assertTrue(layout.entryRise(start).isFinite())
            assertEquals(0f, layout.entryOffset(1.85f, distance, quad[0]), .001f)
            assertEquals(0f, layout.exitOffset(0f, distance, quad[0]), .001f)
            assertTrue("Removed artwork must leave the full viewport", quad[0] + layout.exitOffset(.52f, distance, quad[0]) >= layout.viewportWidth)
        }
    }

    @Test fun wideWindowsStayBoundedAndNeverRepeatSmallPlaylists() {
        for (width in listOf(1080f, 2400f, 3840f)) {
            val layout = ShelfGeometry(width, 150f, 460f, 1080f)
            for (count in listOf(0, 1, 2, 7, 28, 29, 30, 10_000)) {
                for (position in listOf(-5.3f, 0f, 200.6f)) {
                    val window = layout.window(position, count)
                    assertEquals(minOf(count, 29), window.count())
                    if (count > 0) {
                        val indices = window.map { Math.floorMod(it, count) }
                        assertEquals(indices.size, indices.distinct().size)
                    }
                }
            }
        }
    }

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
