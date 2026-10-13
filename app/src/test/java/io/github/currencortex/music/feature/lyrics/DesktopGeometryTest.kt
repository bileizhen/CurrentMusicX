package io.github.currencortex.music.feature.lyrics.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopGeometryTest {
    @Test fun rotationAndOversizedWindowsAlwaysRemainInsideSafeArea() {
        for (area in listOf(DesktopSafeArea(20, 90, 1080, 2300), DesktopSafeArea(90, 20, 2300, 1080))) {
            for (x in listOf(-4f, 0f, .5f, 1f, 8f, Float.NaN)) {
                val p = desktopIslandPlacement(area, 5000, 5000, x, (area.left + area.right) / 2)
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
        assertEquals(20f, s.fontSize, 0f); assertEquals(.85f, s.opacity, 0f)
        assertEquals(20f, DesktopLyricsSettings().fontSize, 0f)
        assertEquals(60000L, s.offsetMs); assertEquals(0f, s.x, 0f)
    }
    @Test fun islandOpacityReachesFullyOpaqueWhileTouchThroughKeepsDeviceLimit() {
        assertEquals(1f, desktopWindowAlpha(1f, false, .8f), .001f)
        assertEquals(.79f, desktopWindowAlpha(1f, true, .8f), .001f)
        assertEquals(1f, DesktopLyricsSettings(islandOpacity = 1f).normalized().islandOpacity, .001f)
        assertEquals(1f, DesktopLyricsSettings(islandOpacity = 3f).normalized().islandOpacity, .001f)
        assertEquals(.95f, DesktopLyricsSettings(opacity = 1f).normalized().opacity, .001f)
    }
    @Test fun islandPlacementMapsHeightAcrossTheFullScreenBounds() {
        val screen = DesktopSafeArea(0, 0, 1080, 2400)
        assertEquals(0, desktopIslandPlacement(screen, 500, 120, 0f, 540).y)
        assertEquals(2280, desktopIslandPlacement(screen, 500, 120, 1f, 540).y)
    }
}
