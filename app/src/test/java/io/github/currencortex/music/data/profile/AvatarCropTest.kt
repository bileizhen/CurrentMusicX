package io.github.currencortex.music.data.profile

import org.junit.Assert.*
import org.junit.Test

class AvatarCropTest {
    @Test fun initialCropIsTheCenteredSquareForPortraitAndLandscape() {
        val landscape = AvatarCrop().bounds(600, 400)
        assertEquals(100f, landscape.left, .001f); assertEquals(0f, landscape.top, .001f)
        assertEquals(400f, landscape.size, .001f)
        val portrait = AvatarCrop().bounds(400, 600)
        assertEquals(0f, portrait.left, .001f); assertEquals(100f, portrait.top, .001f)
    }

    @Test fun panningAndZoomingNeverExposePixelsOutsideTheImage() {
        for ((w, h) in listOf(400 to 600, 600 to 400, 1 to 8)) {
            for (zoom in listOf(.1f, 1f, 2f, 5f, 100f)) {
                val crop = AvatarCrop(zoom, -10f, 10f).constrained(w, h)
                val rect = crop.bounds(w, h)
                assertTrue(crop.zoom in 1f..5f)
                assertTrue(rect.left >= -.001f && rect.top >= -.001f)
                assertTrue(rect.left + rect.size <= w + .001f && rect.top + rect.size <= h + .001f)
            }
        }
    }

    @Test fun pinchKeepsTheImagePointUnderTheFingersStationary() {
        val initial = AvatarCrop(2f)
        val focus = 140f
        val next = initial.transform(600, 400, 200f, 0f, 0f, 1.5f, focus, 100f)
        val beforePoint = initial.centerX * 600 + (focus - 100) / (200f / 400 * initial.zoom)
        val afterPoint = next.centerX * 600 + (focus - 100) / (200f / 400 * next.zoom)
        assertEquals(beforePoint, afterPoint, .001f)
    }

    @Test fun equivalentGesturesProduceTheSameCropAcrossViewportSizes() {
        val small = AvatarCrop().transform(600, 400, 200f, -40f, 0f, 2f)
        val large = AvatarCrop().transform(600, 400, 400f, -80f, 0f, 2f)
        assertEquals(small, large)
        assertEquals(200f, small.bounds(600, 400).size, .001f)
    }
}
