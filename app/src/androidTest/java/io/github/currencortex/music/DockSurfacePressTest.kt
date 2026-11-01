package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.ElasticPressGeometry
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@android.annotation.TargetApi(33)
class DockSurfacePressTest {
    @get:Rule val compose = createComposeRule()
    private var selected by mutableIntStateOf(0)
    private var toggles = 0
    private var opens = 0

    private fun show(expanded: Boolean) {
        compose.setContent { LeiTheme(AppearanceSettings(blur = true, liquidGlass = true)) {
            HighApiFloatingNavigation(selected, listOf("首页", "发现", "我的", "设置"), List(4) { Icons.Default.Home },
                { selected = it }, blur = true, glass = true,
                content = { Box(Modifier.fillMaxSize().background(Color.Black)) }, overlay = {
                    UnifiedMusicDock(expanded, true, true, expanded, Modifier.align(Alignment.BottomCenter), mini = {
                        Row(Modifier.fillMaxWidth().height(52.dp)) {
                            Box(Modifier.weight(1f).fillMaxHeight().testTag("press_title").clickable { opens++ })
                            Box(Modifier.width(48.dp).fillMaxHeight().testTag("press_toggle")
                                .clickable(role = Role.Button) { toggles++ })
                        }
                    }, navigation = LocalMusicDockNavigation.current!!)
                })
        } }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1000)
    }

    private fun checkSurface(expanded: Boolean) {
        show(expanded)
        val dock = compose.onNodeWithTag("music_dock")
        val original = dock.fetchSemanticsNode().boundsInRoot
        val restingMini = compose.onNodeWithTag("press_toggle").fetchSemanticsNode().boundsInRoot
        val pixelsPerDp = restingMini.height / 52f
        val visibleTop = restingMini.top - 6f * pixelsPerDp
        val surface = compose.onNodeWithTag("dock_surface_press")
        surface.performTouchInput { down(Offset(center.x, 3f)) }
        compose.mainClock.advanceTimeBy(240)
        val held = surface.fetchSemanticsNode().config[ElasticPressGeometry]
        assertTrue("Background press grows the whole capsule", held.scaleX > 1f && held.scaleY > 1f)
        surface.performTouchInput { moveBy(Offset(40f, -70f)) }
        compose.mainClock.advanceTimeBy(320)
        val pulled = surface.fetchSemanticsNode().config[ElasticPressGeometry]
        assertTrue("Background follows the dragging finger", pulled.pull.x > 1f && pulled.pull.y < -1f)
        val restHeight = pixelsPerDp * (if (expanded) 122f else 64f)
        val top = visibleTop + restHeight / 2f * (1f - pulled.scaleY) + pulled.pull.y
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir,
            "dock-surface-${if (expanded) "expanded" else "collapsed"}-held.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        val pixel = image.getPixel((original.center.x + pulled.pull.x).toInt(), (top + 3).toInt())
        assertTrue("Expanded glass paints past its original top without being clipped", android.graphics.Color.red(pixel) > 15)
        image.recycle()
        assertEquals("Surface animation must not resize the dock layout", original, dock.fetchSemanticsNode().boundsInRoot)
        surface.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1800)
        val settled = surface.fetchSemanticsNode().config[ElasticPressGeometry]
        assertEquals(1f, settled.scaleX, .001f)
        assertEquals(1f, settled.scaleY, .001f)
        assertEquals(0f, settled.pull.getDistance(), .1f)
        assertEquals(0, opens); assertEquals(0, toggles); assertEquals(0, selected)
        compose.mainClock.autoAdvance = true
    }

    @Test fun expandedCapsuleStretchesAndReturnsWithoutMovingLayout() = checkSurface(true)
    @Test fun collapsedCapsuleUsesItsVisibleHeightAndDoesNotClipTheStretch() = checkSurface(false)

    @Test fun playbackAndNavigationButtonsStretchTheDockAndKeepTheirOwnGestures() {
        show(true)
        for (tag in listOf("press_toggle", "press_title", "tab_3")) {
            val button = compose.onNodeWithTag(tag)
            button.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(240)
            val frame = compose.onNodeWithTag("dock_surface_press").fetchSemanticsNode().config[ElasticPressGeometry]
            assertTrue("Button $tag must also stretch the entire dock", frame.scaleX > 1f && frame.scaleY > 1f)
            button.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1000)
        }
        assertEquals(1, toggles); assertEquals(1, opens); assertEquals(3, selected)
        compose.mainClock.autoAdvance = true
    }
}
