package io.github.currencortex.music

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.feature.player.AudioQualitySheet
import io.github.currencortex.music.feature.settings.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import top.yukonga.miuix.kmp.basic.Scaffold

class AudioQualitySheetTest {
    @get:Rule val compose = createComposeRule()
    @Test fun pickerShowsAllLevelsFollowsAppearanceAndPreventsRedundantSelection() {
        var selected by mutableStateOf(AudioQuality.STANDARD)
        var enabled by mutableStateOf(true)
        var theme by mutableStateOf(ThemeMode.LIGHT)
        var changes = 0
        var closes = 0
        var open by mutableStateOf(true)
        compose.setContent {
            LeiTheme(AppearanceSettings(themeMode = theme)) { Scaffold { Box(Modifier.fillMaxSize().testTag("quality_host")) {
                Box(Modifier.size(48.dp).testTag("reopen_quality").clickable { open = true })
                if (open) AudioQualitySheet(selected, { selected = it; changes++ }, { closes++; open = false }, enabled)
            } } }
        }
        fun assertBottomAnchored() {
            val host = compose.onNodeWithTag("quality_host").fetchSemanticsNode().boundsInRoot
            val sheet = compose.onNodeWithTag("audio_quality_sheet").fetchSemanticsNode().boundsInRoot
            assertEquals("The picker is anchored to the bottom", host.bottom, sheet.bottom, 2f)
            assertTrue("There is a backdrop above the sheet", sheet.top > host.top)
        }
        fun assertMaterialFollowsPanel() {
            val sheet = compose.onNodeWithTag("audio_quality_sheet").fetchSemanticsNode().boundsInRoot
            val material = compose.onNodeWithTag("bottom_sheet_material").fetchSemanticsNode().boundsInRoot
            assertEquals("Material covers the native title and drag handle", sheet.top, material.top, 2f)
            assertEquals(sheet.left, material.left, 2f)
            assertEquals(sheet.right, material.right, 2f)
            assertEquals(sheet.bottom, material.bottom, 2f)
        }
        assertBottomAnchored()
        assertMaterialFollowsPanel()
        compose.onNodeWithTag("close_quality_sheet").assertDoesNotExist()
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        compose.onNodeWithTag("quality_choices").captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(context.externalCacheDir, "quality-light.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        AudioQuality.entries.forEach { quality ->
            compose.onNodeWithTag("quality_choices").performScrollToNode(hasTestTag("quality_${quality.name}"))
            compose.onNodeWithTag("quality_${quality.name}").assertIsDisplayed()
        }
        compose.onNodeWithTag("quality_choices").performScrollToNode(hasTestTag("quality_STANDARD"))
        compose.onNodeWithTag("quality_STANDARD").assertIsSelected().performClick()
        compose.runOnIdle { assertEquals(0, changes); assertEquals(1, closes) }
        compose.onNodeWithTag("quality_choices").assertDoesNotExist()
        compose.onNodeWithTag("reopen_quality").performClick()
        compose.onNodeWithTag("quality_choices").performScrollToNode(hasTestTag("quality_JYMASTER"))
        compose.onNodeWithTag("quality_JYMASTER").performClick()
        compose.runOnIdle { assertEquals(AudioQuality.JYMASTER, selected); assertEquals(1, changes) }
        compose.onNodeWithTag("reopen_quality").performClick()
        compose.onNodeWithTag("quality_choices").performScrollToNode(hasTestTag("quality_JYMASTER"))
        compose.onNodeWithTag("quality_JYMASTER").assertIsSelected()
        fun titleRed(): Float {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("当前歌曲音质", useUnmergedTree = true)
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.first().layoutInput.style.color.red
        }
        assertTrue(titleRed() < .4f)
        compose.runOnIdle { theme = ThemeMode.DARK; enabled = false }
        assertTrue(titleRed() > .6f)
        compose.onNodeWithTag("quality_JYMASTER").assertIsNotEnabled()
        compose.onNodeWithTag("quality_choices").captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(context.externalCacheDir, "quality-dark.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        assertBottomAnchored()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("audio_quality_sheet").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(centerX, 12.dp.toPx()))
            moveBy(androidx.compose.ui.geometry.Offset(0f, 60.dp.toPx()), delayMillis = 300)
        }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        assertMaterialFollowsPanel()
        compose.onNodeWithTag("audio_quality_sheet").performTouchInput { advanceEventTime(400); up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("audio_quality_sheet").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(centerX, 12.dp.toPx()),
                androidx.compose.ui.geometry.Offset(centerX, height * .8f), 300)
        }
        compose.onNodeWithTag("quality_choices").assertDoesNotExist()
        compose.runOnIdle { assertEquals(3, closes) }
    }
    @Test fun networkSettingsUsesTheSamePickerAndPersistsDefaultWithoutStartingAudio(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = AppContainer(context, "quality-${UUID.randomUUID()}")
        val store = ViewModelStore()
        try {
            container.ready.await(); container.sessionRestored.await()
            val vm = MusicSettingsViewModel(container)
            store.put("quality", vm)
            compose.setContent { LeiTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT)) { Scaffold { MusicSettingsScreen(vm) {} } } }
            compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("default_quality"))
            compose.onNodeWithTag("default_quality").performClick()
            compose.onNodeWithTag("quality_choices").performScrollToNode(hasTestTag("quality_LOSSLESS"))
            compose.onNodeWithTag("quality_LOSSLESS").performClick()
            compose.waitUntil(5000) { container.musicSettings.state.value.quality == AudioQuality.LOSSLESS }
            compose.onNodeWithTag("quality_choices").assertDoesNotExist()
            compose.onNodeWithTag("default_quality").performClick()
            compose.onNodeWithTag("quality_choices").performScrollToNode(hasTestTag("quality_LOSSLESS"))
            compose.onNodeWithTag("quality_LOSSLESS").assertIsSelected()
            compose.onNodeWithTag("close_quality_sheet").assertDoesNotExist()
            compose.onNodeWithTag("audio_quality_sheet").performTouchInput {
                swipe(androidx.compose.ui.geometry.Offset(centerX, 12.dp.toPx()),
                    androidx.compose.ui.geometry.Offset(centerX, height * .8f), 300)
            }
            compose.onNodeWithTag("quality_choices").assertDoesNotExist()
            assertEquals(AudioQuality.LOSSLESS, container.musicSettings.snapshot().quality)
            assertNull(container.playbackQueue.state.value.current)
        } finally { compose.runOnUiThread { store.clear() }; container.close() }
    }
}
