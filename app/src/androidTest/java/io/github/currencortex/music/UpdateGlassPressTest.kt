package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.update.UpdateButtonGlowKey
import io.github.currencortex.music.feature.update.UpdateGlassButton
import io.github.currencortex.music.ui.theme.LeiTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text

class UpdateGlassPressTest {
    @get:Rule val compose = createComposeRule()
    private var clicks = 0

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent { LeiTheme(AppearanceSettings()) { Scaffold {
            Box(Modifier.fillMaxSize().background(Color(0xFF100D29)), contentAlignment = Alignment.Center) {
                UpdateGlassButton(null, "press_button", "更多", { clicks++ }) { Text("⋮", color = Color.White) }
            }
        } } }
        compose.mainClock.advanceTimeBy(500)
    }

    @Test fun quickTapCompletesGrowthAndLightThenReturnsToRest() {
        show()
        val button = compose.onNodeWithTag("press_button")
        button.performTouchInput { down(center); advanceEventTime(8); up() }
        compose.mainClock.advanceTimeBy(100)
        assertTrue(button.fetchSemanticsNode().config[UpdateButtonGlowKey] > .5f)
        val bright = button.captureToImage().asAndroidBitmap()
        assertTrue("A quick tap still completes the upstream growth",
            button.fetchSemanticsNode().config[io.github.currencortex.music.ui.component.nativeui.ElasticPressGeometry].scaleX>1.01f)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        File(context.getExternalFilesDir(null), "update-button-tap-bright.png").outputStream().use {
            bright.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bright.recycle()
        compose.mainClock.advanceTimeBy(800)
        assertEquals(0f, button.fetchSemanticsNode().config[UpdateButtonGlowKey], .001f)
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun holdsTheLightWhilePressedAndCancellationDoesNotClick() {
        show()
        val button = compose.onNodeWithTag("press_button")
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(1f, button.fetchSemanticsNode().config[UpdateButtonGlowKey], .001f)
        button.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(0f, button.fetchSemanticsNode().config[UpdateButtonGlowKey], .001f)
        compose.runOnIdle { assertEquals(0, clicks) }
    }
}
