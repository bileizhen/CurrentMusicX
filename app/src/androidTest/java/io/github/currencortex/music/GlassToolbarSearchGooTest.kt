package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.blur.layerBackdrop
import java.io.File

class GlassToolbarSearchGooTest {
    @get:Rule val compose = createComposeRule()

    private fun checkBothNeighbors(glass: Boolean) {
        compose.setContent { LeiTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT, liquidGlass = glass)) {
            val backdrop = rememberGlassBackdrop()
            var text by remember { mutableStateOf("") }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.matchParentSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                    .background(Color.Black))
                CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                    GlassSearchToolbar("搜索测试", 1f, {}, true, {}, text, { text = it },
                        GlassToolbarAction(MusicIcons.Image, "操作", {}, "goo_left"),
                        GlassToolbarAction(MusicIcons.More, "更多", {}, "goo_more"),
                        Modifier.padding(top = 120.dp).testTag("goo_toolbar"), inputTag = "goo_query")
                }
            }
        } }
        val root = compose.onRoot()
        val toolbar = compose.onNodeWithTag("goo_toolbar").fetchSemanticsNode().boundsInRoot
        val before = root.captureToImage().asAndroidBitmap()
        val original = compose.onNodeWithTag("goo_query_surface").fetchSemanticsNode().config[BarSearchPanesKey]
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("goo_query").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(240)
        val pressed = compose.onNodeWithTag("goo_query_surface").fetchSemanticsNode().config[BarSearchPanesKey]
        val image = root.captureToImage().asAndroidBitmap()
        val necks = listOf(
            Offset((pressed.left.right + pressed.field.left) / 2, (pressed.left.center.y + pressed.field.center.y) / 2),
            Offset((pressed.field.right + pressed.right.left) / 2, (pressed.field.center.y + pressed.right.center.y) / 2),
        )
        assertTrue("Left neck is between separate pane bounds, rather than an overlap", pressed.field.left > pressed.left.right)
        assertTrue("Right neck is between separate pane bounds, rather than an overlap", pressed.right.left > pressed.field.right)
        fun pixel(point: Offset, bitmap: android.graphics.Bitmap) =
            android.graphics.Color.red(bitmap.getPixel((toolbar.left + point.x).toInt(), (toolbar.top + point.y).toInt()))
        necks.forEachIndexed { index, point ->
            assertTrue("Press must paint a real glass neck in gap $index", pixel(point, image) > pixel(point, before) + 30)
        }
        File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir,
            "bar-search-three-pill-${if (glass) "glass" else "standard"}-pressed.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithTag("goo_query").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1500)
        val after = root.captureToImage().asAndroidBitmap()
        val settled = compose.onNodeWithTag("goo_query_surface").fetchSemanticsNode().config[BarSearchPanesKey]
        assertEquals(original.field.left, settled.field.left, 1f)
        necks.forEach { point -> assertTrue("Release separates both neighbors again",
            kotlin.math.abs(pixel(point, before) - pixel(point, after)) <= 4) }
        before.recycle(); image.recycle(); after.recycle()
        compose.mainClock.autoAdvance = true
    }

    @Test fun liquidGlassConnectsToBothButtonsAndSeparatesOnRelease() = checkBothNeighbors(true)
    @Test fun standardSurfaceUsesTheSameThreePaneOutline() = checkBothNeighbors(false)
}
