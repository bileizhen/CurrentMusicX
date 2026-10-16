package io.github.currencortex.music

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.currencortex.music.core.media.OriginalAiMasterState
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.settings.AiMasterEffectsDetail
import io.github.currencortex.music.ui.component.LocalMusicPageActive
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Native detail-only checks; no account, playback service, or saved user preferences are touched. */
class AiMasterEffectsDetailTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = OriginalAiMasterState(enabled = true, requestedEnabled = true,
        songId = 186016, baseName = "温润", longText = "真实接口长说明", selectedDiy = setOf("en_treble"))

    @Test fun closingDraftDoesNotSaveAndExplicitSaveCommitsOnlyCurrentFiveChoices() {
        val saved = mutableListOf<Set<String>>()
        compose.setContent { LeiTheme(AppearanceSettings()) {
            CompositionLocalProvider(LocalMusicPageActive provides false) {
                AiMasterEffectsDetail(fixture, Song(186016, "歌曲"), false, {}, {}, {}, { saved += it })
            }
        } }
        compose.onNodeWithTag("sound_ai_master_diy_open").performClick()
        compose.onNodeWithTag("sound_ai_master_diy_treble").assertIsOn()
        compose.onNodeWithTag("sound_ai_master_diy_bass").performClick()
        compose.onNodeWithTag("sound_ai_master_diy_close").performClick()
        assertTrue(saved.isEmpty())
        compose.onNodeWithTag("sound_ai_master_diy_open").performClick()
        compose.onNodeWithTag("sound_ai_master_diy_bass").assertIsOff()
        compose.onNodeWithTag("sound_ai_master_diy_vibe").performClick()
        compose.onNodeWithTag("sound_ai_master_diy_save").performScrollTo().performClick()
        assertEquals(listOf(setOf("en_treble", "en_vibe")), saved)
        compose.onNodeWithTag("sound_ai_master_diy_save").assertDoesNotExist()
    }

    @Test fun trueMetadataAndNativeInformationSheetAreVisibleAndDismissible() {
        compose.setContent { LeiTheme(AppearanceSettings()) {
            CompositionLocalProvider(LocalMusicPageActive provides false) {
                AiMasterEffectsDetail(fixture, Song(186016, "歌曲"), false, {}, {}, {}, {})
            }
        } }
        compose.onNodeWithTag("sound_ai_master_keyword").assertTextEquals("温润")
        compose.onNodeWithTag("sound_ai_master_description").assertTextEquals("真实接口长说明")
        compose.onNodeWithTag("sound_ai_master_info").performClick()
        compose.onNodeWithText("AI调音大师说明").assertIsDisplayed()
        compose.onNodeWithText("3.DIY偏好").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("sound_ai_master_info_close").performScrollTo().performClick()
        compose.onNodeWithText("AI调音大师说明").assertDoesNotExist()
    }

    @Test fun originalAndProcessedPreviewControlsStartAndStopSeparateCallbacks() {
        val active = mutableStateOf(false); val processed = mutableStateOf(false)
        val starts = mutableListOf<Boolean>(); var stops = 0
        compose.setContent { LeiTheme(AppearanceSettings()) {
            CompositionLocalProvider(LocalMusicPageActive provides false) {
                AiMasterEffectsDetail(fixture.copy(enabled = false, requestedEnabled = false), Song(186016, "歌曲"),
                    false, {}, {}, {}, {}, onPreview = { starts += it; active.value = true; processed.value = it },
                    previewing = active.value, previewProcessed = processed.value,
                    onPreviewStop = { stops++; active.value = false })
            }
        } }
        compose.onNodeWithTag("sound_ai_master_preview_original").performScrollTo().performClick()
        compose.onNodeWithTag("sound_ai_master_preview_original").performClick()
        compose.onNodeWithTag("sound_ai_master_preview_processed").performClick()
        assertEquals(listOf(false, true), starts)
        assertEquals(1, stops)
    }
}
