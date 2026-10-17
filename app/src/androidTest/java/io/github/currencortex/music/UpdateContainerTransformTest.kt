package io.github.currencortex.music

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.update.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

class UpdateContainerTransformTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cardExpandsIntoPageAndReversesFromItsCurrentBounds() {
        val open = mutableStateOf(false)
        var clicks = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
            Box(Modifier.fillMaxSize().testTag("transform_fixture").background(Color(0xFFF4F5F9))) {
                Box(Modifier.padding(horizontal = 16.dp).padding(top = 150.dp).fillMaxWidth().height(80.dp)
                    .onGloballyPositioned { coordinates = it }.background(Color.White).testTag("source_card"))
                UpdateContainerTransform(open.value, coordinates?.let {
                    UpdatePageOrigin(it, Color.White, "检查更新", "查看版本和更新说明")
                }) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF172345))
                        .testTag("transform_destination").clickable { clicks++ })
                }
            }
        } }
        val source = compose.onNodeWithTag("source_card").getUnclippedBoundsInRoot()
        compose.runOnIdle { open.value = true }
        compose.mainClock.advanceTimeByFrame()
        val start = frame()
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertEquals(source.top.value * density, start.bounds.top, 4f)
        capture("container-open-00.png")
        compose.mainClock.advanceTimeBy(160)
        val intermediate = frame()
        assertTrue(intermediate.progress in .05f.. .95f)
        assertTrue(intermediate.bounds.top < start.bounds.top)
        assertTrue(intermediate.bounds.height > start.bounds.height)
        assertTrue(intermediate.radius < start.radius && intermediate.radius > 0f)
        capture("container-open-01.png")
        compose.runOnIdle { open.value = false }
        compose.mainClock.advanceTimeByFrame()
        val reversal = frame()
        assertTrue("A reversal must continue from the visible shape: ${intermediate.progress} -> ${reversal.progress}",
            abs(reversal.progress - intermediate.progress) < .12f)
        compose.mainClock.advanceTimeBy(160)
        assertTrue(frame().progress < reversal.progress)
        compose.runOnIdle { open.value = true }
        compose.mainClock.advanceTimeBy(650)
        assertEquals(1f, frame().progress, .001f)
        assertEquals(Rect(0f, 0f, frame().bounds.right, frame().bounds.bottom), frame().bounds)
        capture("container-open-02.png")
        compose.onNodeWithTag("transform_destination").performTouchInput { click(androidx.compose.ui.geometry.Offset(60f, 60f)) }
        compose.runOnIdle { assertEquals("Invisible source proxy must not intercept toolbar taps", 1, clicks); open.value = false }
        compose.mainClock.advanceTimeBy(220)
        capture("container-close-01.png")
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("update_container_transform").assertDoesNotExist()
        compose.onNodeWithTag("source_card").assertIsDisplayed()
    }

    @Test fun notificationEntryWithoutSourceFadesAndCanClose() {
        val open = mutableStateOf(false)
        val presented = mutableListOf<Boolean>()
        compose.mainClock.autoAdvance = false
        compose.setContent { UpdateContainerTransform(open.value, null, { presented += it }) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        } }
        compose.runOnIdle { open.value = true }
        compose.mainClock.advanceTimeBy(250)
        assertEquals(0f, frame().bounds.top, 0f)
        assertEquals(0f, frame().bounds.left, 0f)
        compose.runOnIdle { open.value = false }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("update_container_transform").assertDoesNotExist()
        assertTrue(presented.contains(true)); assertEquals(false, presented.last())
    }

    private fun frame() = compose.onNodeWithTag("update_container_transform").fetchSemanticsNode().config[UpdateTransformGeometry]
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
