package io.github.currencortex.music

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.config.AboutCredits
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.about.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Touch input matters: a semantic ScrollTo bypasses a broken parent nested-scroll handler. */
class AboutScrollTest {
    @get:Rule val compose = createComposeRule()
    private fun checkGestures(blur: Boolean) {
        var backs = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { LeiTheme(AppearanceSettings(blur = blur)) {
            AboutScreenMiuix(AboutUiState(), AboutScreenActions({ backs++ }, {}), blur, { _, _ -> })
        } }
        fun advance() {
            compose.waitForIdle(); compose.mainClock.advanceTimeBy(900); compose.waitForIdle()
        }
        advance()
        val list = compose.onNodeWithTag("about_screen")
        fun position() = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        fun swipe(up: Boolean) {
            list.performTouchInput {
                val low = Offset(width * .55f, height * .8f)
                val high = Offset(width * .55f, height * .3f)
                swipe(if (up) low else high, if (up) high else low, durationMillis = 600)
            }
            advance()
        }
        val initial = position()
        swipe(true)
        assertTrue("An upward finger swipe must move the about list", position() > initial + .02f)
        repeat(5) { swipe(true) }
        compose.onNodeWithText(AboutCredits.sections.last().members.last().name).assertIsDisplayed()
        val end = position()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir,
            "about-scrolled-${if (blur) "blur" else "plain"}.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        swipe(false)
        assertTrue("A downward swipe must move back toward the logo", position() < end - .02f)
        repeat(5) { swipe(false) }
        assertEquals(initial, position(), .02f)
        compose.onNodeWithTag("about_logo").assertIsDisplayed()
        compose.onNodeWithTag("navigate_back").performClick()
        assertEquals(1, backs)
    }
    @Test fun fingerSwipesReachCreditsAndReturnToLogoWithBlur() = checkGestures(true)
    @Test fun fingerSwipesReachCreditsAndReturnToLogoWithoutBlur() = checkGestures(false)
}
