package io.github.currencortex.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.asAndroidBitmap
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.settings.AppearanceColorSwatches
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

class AppearancePaletteLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val density = mutableFloatStateOf(3.5f)
    private val width = mutableFloatStateOf(329f)
    private fun show() {
        compose.setContent {
            LeiTheme(AppearanceSettings(themeMode = ThemeMode.DARK)) {
                CompositionLocalProvider(LocalDensity provides Density(density.floatValue)) {
                    Box(Modifier.requiredWidth(width.floatValue.dp)) {
                        AppearanceColorSwatches(AppearanceSettings(), {}, {}, {})
                    }
                }
            }
        }
    }
    private fun assertRows(columns: Int) {
        compose.waitForIdle()
        val tags = listOf("setting_monet") + DefaultThemeColors.map { "appearance_color_${it.toString(16)}" } + "appearance_color_add"
        val nodes = tags.map { compose.onNodeWithTag(it).fetchSemanticsNode() }
        val card = compose.onNodeWithTag("appearance_palette").fetchSemanticsNode()
        val left = card.positionInRoot.x + 10 * density.floatValue
        val right = card.positionInRoot.x + card.size.width - 10 * density.floatValue
        val top = nodes.first().positionInRoot.y
        assertEquals("A planned column must not wrap early at fractional density", columns,
            nodes.count { abs(it.positionInRoot.y - top) < 1f })
        nodes.forEachIndexed { index, node ->
            val row = index / columns
            assertEquals(nodes[row * columns].positionInRoot.y, node.positionInRoot.y, .1f)
            assertTrue("Swatch must stay inside the left inset", node.positionInRoot.x >= left - 1)
            assertTrue("Swatch must stay inside the right inset", node.positionInRoot.x + node.size.width <= right + 1)
        }
        val last = nodes[columns - 1]
        assertTrue("A full row must not leave a column-sized blank on the right", right - last.positionInRoot.x - last.size.width < 6 * density.floatValue)
    }
    @Test fun fractionalDensitiesKeepAllSixColumnsAndBothRowEdges() {
        show()
        for (value in listOf(3.5f, 2.625f, 4.2f, 1.5f)) {
            compose.runOnUiThread { density.floatValue = value }
            assertRows(6)
        }
    }
    @Test fun narrowAndWideCardsReflowWithoutAnUnusedTrailingColumn() {
        show()
        for ((value, columns) in listOf(256f to 4, 280f to 5, 329f to 6, 390f to 7)) {
            compose.runOnUiThread { width.floatValue = value }
            assertRows(columns)
        }
        compose.runOnUiThread { width.floatValue = 329f }
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir, "appearance-palette-columns-fixed.png")
            .outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
