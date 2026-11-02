package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import io.github.currencortex.music.feature.lyrics.ui.AppleLyrics
import org.junit.Rule
import org.junit.Test

class InlineTranslationRenderingTest {
    @get:Rule val compose = createComposeRule()
    @Test fun inlineTranslationIsSeparateAndFollowsTheExistingSwitch() {
        val enabled = mutableStateOf(true)
        val document = LyricsParser.lrc("[00:01]Not a single day goes by [tr]没有一天不思念你[/tr]\n[00:08]Show me")
        compose.setContent {
            AppleLyrics(document, remember { mutableLongStateOf(2000L) }, {},
                Modifier.fillMaxSize().background(Color(0xFF101444)), translation = enabled.value, effects = false)
        }
        compose.onNodeWithTag("lyric_translation", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("没有一天不思念你", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Not a single day goes by", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("[tr]", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithTag("lyric_translation", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Not a single day goes by", useUnmergedTree = true).assertExists()
        compose.runOnIdle { enabled.value = true }
        compose.onNodeWithTag("lyric_translation", useUnmergedTree = true).assertIsDisplayed()
    }
}
