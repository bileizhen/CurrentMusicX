package io.github.currencortex.music

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class AppearanceMotionTest {
    private var reduced = false
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor: Float get() = if (reduced) 0f else 1f
    })
    private val settings = mutableStateOf(AppearanceSettings(themeMode = ThemeMode.LIGHT))
    private fun showTheme() {
        compose.setContent { LeiTheme(settings.value) {
            var count by remember { mutableIntStateOf(0) }
            Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background).testTag("theme_sample")) {
                Text("count$count", Modifier.padding(30.dp).clickable { count++ }.testTag("theme_counter"))
            }
        } }
    }
    private fun pixel(tag: String): Int {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        return bitmap.getPixel((bitmap.width * .9f).toInt(), (bitmap.height * .8f).toInt()).also { bitmap.recycle() }
    }
    @Test fun themeChangeHasAnIntermediateFrameAndKeepsTheSameContentState() {
        compose.mainClock.autoAdvance = false
        showTheme()
        compose.onNodeWithTag("theme_counter").performClick()
        val light = pixel("theme_sample")
        compose.runOnUiThread { settings.value = settings.value.copy(themeMode = ThemeMode.DARK) }
        compose.waitUntil(8000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("theme_cross_fade").fetchSemanticsNode().config[ThemeFadeWeight] > 0f
        }
        compose.mainClock.advanceTimeBy(64)
        val middle = pixel("theme_cross_fade")
        compose.mainClock.advanceTimeBy(400)
        val dark = pixel("theme_sample")
        assertTrue("The old frame must fade over the new dark theme", Color.red(middle) in (Color.red(dark) + 1) until Color.red(light))
        assertEquals(0f, compose.onNodeWithTag("theme_cross_fade").fetchSemanticsNode().config[ThemeFadeWeight])
        compose.onNodeWithTag("theme_counter").assertTextEquals("count1")
    }
    @Test fun disabledSystemAnimationsDoNotCaptureOrDelayTheThemeChange() {
        reduced = true
        showTheme()
        compose.runOnUiThread { settings.value = settings.value.copy(themeMode = ThemeMode.DARK) }
        compose.waitForIdle()
        assertTrue(Color.red(pixel("theme_sample")) < 40)
        assertEquals(0f, compose.onNodeWithTag("theme_cross_fade").fetchSemanticsNode().config[ThemeFadeWeight])
    }
}
