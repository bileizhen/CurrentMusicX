package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.settings.AppearanceColorSheet
import io.github.currencortex.music.ui.component.nativeui.SettingsUiScope
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppearanceColorSheetTest {
    @get:Rule val compose = createComposeRule()
    private var committed: Long? = null
    private var closed = false
    private fun show(dark: Boolean = true, editing: Boolean = false) {
        compose.setContent {
            val config = AppearanceSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, pureBlack = dark)
            var open by remember { mutableStateOf(true) }
            LeiTheme(config) { SettingsUiScope { top.yukonga.miuix.kmp.basic.Scaffold {
                if (open) AppearanceColorSheet(config, 0xFF007AFF, { open = false; closed = true },
                    { committed = it }, if (editing) { { committed = -1L } } else null)
            } } }
        }
        compose.waitForIdle()
    }
    private fun hex(value: String) {
        compose.onNodeWithTag("appearance_color_hex").performTextReplacement(value)
        compose.onNodeWithTag("appearance_color_hex").performImeAction()
        compose.waitUntil(5000) { android.view.inspector.WindowInspector.getGlobalWindowViews().none {
            it.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
        } }
    }
    private fun mode(index: Int) = compose.onNodeWithTag("appearance_palette_mode_$index").performClick()
    private fun progress(tag: String, value: Float) = compose.onNodeWithTag(tag).performScrollTo()
        .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
    private fun assertPreview(value: String) = compose.onNodeWithTag("appearance_color_preview")
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "#$value"))
    private fun assertBothPreviewsDrawContent() {
        listOf("light", "dark").forEach { mode ->
            val image = compose.onNodeWithTag("appearance_color_preview_$mode").captureToImage().asAndroidBitmap()
            val samples = mutableSetOf<Int>()
            for (y in image.height / 10 until image.height * 3 / 4 step 8)
                for (x in image.width / 10 until image.width * 9 / 10 step 8) samples += image.getPixel(x, y)
            image.recycle()
            assertTrue("$mode preview must retain its body after changing picker mode", samples.size > 8)
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir, name).outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }

    @Test fun changesStayLocalAcrossModesAndCommitOnlyAfterExit() {
        show()
        hex("80FF9F"); assertPreview("80FF9F")
        mode(1); assertPreview("80FF9F")
        assertBothPreviewsDrawContent()
        capture("appearance-picker-spectrum-dark.png")
        mode(2); assertPreview("80FF9F")
        assertBothPreviewsDrawContent()
        capture("appearance-picker-sliders-dark.png")
        mode(0); assertPreview("80FF9F")
        assertNull(committed)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("appearance_color_save").performClick()
        compose.runOnIdle { assertNull("Retheming must wait for the sheet's exit", committed) }
        compose.mainClock.advanceTimeBy(2000)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5000) { closed }
        assertEquals(0xFF80FF9F, committed)
    }

    @Test fun grayAndBlackKeepSliderHueAndSaturationUntilAnExternalPick() {
        show()
        mode(2)
        progress("appearance_color_hue", 135f)
        progress("appearance_color_saturation", .5f)
        progress("appearance_color_brightness", 1f)
        assertPreview("80FF9F")
        progress("appearance_color_brightness", 0f)
        assertPreview("000000")
        progress("appearance_color_hue", 240f)
        progress("appearance_color_brightness", 1f)
        assertPreview("8080FF")
        progress("appearance_color_saturation", 0f)
        progress("appearance_color_hue", 135f)
        progress("appearance_color_saturation", 1f)
        assertPreview("00FF40")
        compose.onNodeWithTag("appearance_color_cancel").performClick()
        compose.waitUntil(5000) { closed }
        assertNull(committed)
    }

    @Test fun gridUsesSourceColorsAndPadDragsDoNotMoveTheSheet() {
        show()
        val bounds = compose.onNodeWithTag("appearance_color_sheet").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("appearance_color_grid").performSemanticsAction(SemanticsActions.SetProgress) { it(6f) }
        assertPreview("6C6C6C")
        capture("appearance-picker-grid-dark.png")
        mode(1)
        compose.onNodeWithTag("appearance_color_spectrum").performTouchInput {
            down(Offset(width * .2f, height * .2f))
            moveTo(Offset(width * .6f, height * .7f), 300)
            up()
        }
        compose.waitForIdle()
        assertEquals(bounds, compose.onNodeWithTag("appearance_color_sheet").fetchSemanticsNode().boundsInRoot)
        assertFalse(closed)
        assertNull(committed)
        assertPreview("003D99")
        // Picking white and black keeps the spectrum marker at the touched hue.
        compose.onNodeWithTag("appearance_color_spectrum").performTouchInput { click(Offset(width * .6f, 0f)) }
        assertPreview("FFFFFF")
    }

    @Test fun hexValidationAndLightSheetCancelPreserveTheOriginalColor() {
        show(dark = false, editing = true)
        hex("#aBcD12"); assertPreview("ABCD12")
        hex("invalid")
        compose.onNodeWithTag("appearance_color_save").assertIsNotEnabled()
        assertPreview("ABCD12")
        hex("80FF9F")
        compose.onNodeWithTag("appearance_color_save").assertIsEnabled()
        capture("appearance-picker-grid-light.png")
        compose.onNodeWithTag("appearance_color_cancel").performClick()
        compose.waitUntil(5000) { closed }
        assertNull(committed)
    }
}
