package io.github.currencortex.music.feature.lyrics.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopGeometryTest {
    @Test fun rotationAndOversizedWindowsAlwaysRemainInsideSafeArea() {
        for (area in listOf(DesktopSafeArea(20, 90, 1080, 2300), DesktopSafeArea(90, 20, 2300, 1080))) {
            for (x in listOf(-4f, 0f, .5f, 1f, 8f, Float.NaN)) {
                val p = desktopPlacement(area, 5000, 5000, x, x)
                assertTrue(p.x >= area.left && p.y >= area.top)
                assertTrue(p.x + p.width <= area.right && p.y + p.height <= area.bottom)
            }
        }
    }
    @Test fun lockedOpacityHonorsDeviceLimitAndInvalidSettingsAreNormalized() {
        assertEquals(.79f, desktopWindowAlpha(.95f, true, .8f), .001f)
        assertEquals(.49f, desktopWindowAlpha(.85f, true, .5f), .001f)
        assertEquals(.95f, desktopWindowAlpha(.95f, false, .5f), .001f)
        val s = DesktopLyricsSettings(fontSize = Float.NaN, opacity = Float.POSITIVE_INFINITY, offsetMs = Long.MAX_VALUE, x = -100f).normalized()
        assertEquals(26f, s.fontSize, 0f); assertEquals(.85f, s.opacity, 0f)
        assertEquals(60000L, s.offsetMs); assertEquals(0f, s.x, 0f)
    }
}
