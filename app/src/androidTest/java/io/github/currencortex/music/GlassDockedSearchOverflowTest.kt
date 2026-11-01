package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.blur.layerBackdrop
import java.io.File

class GlassDockedSearchOverflowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun draggedGlassPaintsPastItsRestingContainerWithoutMovingTheHitBox() {
        compose.setContent { LeiTheme(AppearanceSettings(themeMode = ThemeMode.DARK, liquidGlass = true)) {
            val backdrop = rememberGlassBackdrop()
            var query by remember { mutableStateOf("") }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.matchParentSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                    .background(Color.Black))
                CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                    GlassDockedSearch(query, { query = it }, Modifier.padding(start = 32.dp, top = 180.dp, end = 32.dp),
                        inputTag = "overflow_search")
                }
            }
        } }
        val dock = compose.onNodeWithTag("overflow_search_dock")
        val original = dock.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        // The upper blank strip belongs to ElasticPress, outside the editor's input area.
        dock.performTouchInput { down(Offset(width * .6f, 4f)) }
        compose.mainClock.advanceTimeBy(400)
        dock.performTouchInput { moveBy(Offset(-150f, -550f)) }
        compose.mainClock.advanceTimeBy(400)
        val frame = compose.onNodeWithTag("overflow_search_surface").fetchSemanticsNode().config[DockedSearchFrame]
        assertTrue("The dragged pane must cross the resting top edge", frame.bounds.top < -8f)
        assertEquals(original, dock.fetchSemanticsNode().boundsInRoot)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        try {
            val x = (original.left + frame.bounds.center.x).toInt()
            val y = (original.top + frame.bounds.top + 4f).toInt()
            val pixel = bitmap.getPixel(x, y)
            assertTrue("Glass beyond the old rectangular target must remain visible",
                android.graphics.Color.red(pixel) > 5 || android.graphics.Color.green(pixel) > 5 || android.graphics.Color.blue(pixel) > 5)
            File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir, "font-search-drag-overflow.png")
                .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle(); dock.performTouchInput { up() } }
        compose.mainClock.advanceTimeBy(1500)
        val settled = compose.onNodeWithTag("overflow_search_surface").fetchSemanticsNode().config[DockedSearchFrame]
        assertEquals(0f, settled.bounds.top, 1f)
        assertEquals(original, dock.fetchSemanticsNode().boundsInRoot)
    }
}
