package io.github.currencortex.music

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
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
        compose.setContent {
            LeiTheme(AppearanceSettings(themeMode = theme)) { Scaffold { Box(Modifier.fillMaxSize()) {
                AudioQualitySheet(selected, { selected = it; changes++ }, { closes++ }, enabled)
            } } }
        }
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
        compose.onNodeWithTag("quality_choices").performScrollToNode(hasTestTag("quality_JYMASTER"))
        compose.onNodeWithTag("quality_JYMASTER").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(AudioQuality.JYMASTER, selected); assertEquals(1, changes) }
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
            compose.onNodeWithTag("close_quality_sheet").performClick()
            assertEquals(AudioQuality.LOSSLESS, container.musicSettings.snapshot().quality)
            assertNull(container.playbackQueue.state.value.current)
        } finally { compose.runOnUiThread { store.clear() }; container.close() }
    }
}
