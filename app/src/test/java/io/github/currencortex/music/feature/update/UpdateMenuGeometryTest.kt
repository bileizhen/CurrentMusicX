package io.github.currencortex.music.feature.update

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class UpdateMenuGeometryTest {
    @Test fun collapsedPanelMeetsTheActualButtonEvenWhenOutsidePanelBounds() {
        val panel = Rect(360f, 180f, 600f, 660f)
        for (anchor in listOf(Rect(512f, 60f, 608f, 156f), Rect(200f, 60f, 296f, 156f))) {
            val geometry = updateMenuGeometry(anchor, panel)
            val pivot = Offset(panel.width * geometry.origin.pivotFractionX,
                panel.height * geometry.origin.pivotFractionY)
            assertEquals(anchor.center.x, (panel.topLeft + pivot + geometry.translation(0f)).x, .001f)
            assertEquals(anchor.center.y, (panel.topLeft + pivot + geometry.translation(0f)).y, .001f)
            assertEquals(0f, geometry.translation(1f).getDistance(), .001f)
        }
    }

    @Test fun diagonalTravelCurvesAndRemainsStableAtSpringOvershoot() {
        val geometry = updateMenuGeometry(Rect(20f, 20f, 60f, 60f), Rect(180f, 180f, 480f, 680f))
        val middle = geometry.translation(.5f)
        assertTrue((middle - geometry.start * .5f).getDistance() > 1f)
        assertEquals(0f, geometry.translation(1.02f).getDistance(), .001f)
        assertEquals(geometry.start, geometry.translation(-.02f))
        assertEquals(0f, updateMenuGeometry(null, Rect.Zero).translation(.5f).getDistance(), .001f)
    }
}
