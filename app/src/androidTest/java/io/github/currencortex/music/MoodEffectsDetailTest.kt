package io.github.currencortex.music

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.currencortex.music.core.media.OriginalMoodState
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.settings.MoodEffectsDetail
import io.github.currencortex.music.ui.component.LocalMusicPageActive
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated presentation: no account, service, native engine or real player controls are touched. */
class MoodEffectsDetailTest {
    @get:Rule val compose = createComposeRule()
    private val visual = Json.parseToJsonElement("""{
        "copywriting":{"emotion":"nostalgic","keyword":"思念藏在歌里","description":"原算法返回的当前歌曲调节说明"},
        "dimensions":[
            {"name":"space_size","value":[0.254,0.661],"rank":1,"intensity":"strong","visual":true},
            {"name":"stereo_width","value":[0.309,0.537],"rank":2,"intensity":"medium","visual":true},
            {"name":"rhythm","value":[0.298,0.455],"rank":3,"intensity":"medium","visual":true},
            {"name":"loudness","value":[0.44,0.534],"rank":4,"visual":false},
            {"name":"vocal","value":[0.829,0.9],"rank":5,"visual":false}
        ]} """).jsonObject
    private val fixture = OriginalMoodState(enabled = true, requestedEnabled = true, processing = true,
        songId = 186016, emotion = "nostalgic", family = "pop-family", visualExtra = visual)

    @Test fun originalInformationSheetTextAndScrollingDoNotDismissUntilExplicitClose() {
        compose.setContent { LeiTheme(AppearanceSettings()) { top.yukonga.miuix.kmp.basic.Scaffold {
            CompositionLocalProvider(LocalMusicPageActive provides false) {
                MoodEffectsDetail(fixture, Song(186016, "当前歌曲"), false, {}, {}, {})
            }
        } } }
        compose.onNodeWithContentDescription("AI情绪调音师说明").performClick()
        compose.onNodeWithText("1.功能说明").performTouchInput { click() }
        compose.onNodeWithText("AI情绪调音师说明").assertIsDisplayed()
        compose.onNodeWithText("5.数据隐私").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("mood_info_done").performClick()
        compose.onNodeWithTag("mood_info_panel").assertDoesNotExist()
    }

    @Test fun actualMatchingSongShowsOriginalKeywordRadarAndCallbacksRemainSeparate() {
        var playback = 0; var toggles = 0
        compose.setContent { LeiTheme(AppearanceSettings()) { top.yukonga.miuix.kmp.basic.Scaffold {
            CompositionLocalProvider(LocalMusicPageActive provides false) {
                MoodEffectsDetail(fixture, Song(186016, "当前歌曲"), true, {}, { toggles++ }, { playback++ })
            }
        } } }
        compose.onNodeWithTag("mood_emotion_title").performScrollTo().assertTextEquals("思念藏在歌里")
        compose.onNodeWithTag("mood_adjustment_radar").performScrollTo().assertExists()
        compose.onNodeWithTag("mood_song_playback").performScrollTo().performClick()
        compose.onNodeWithTag("mood_toggle").performClick()
        assertEquals(1, playback); assertEquals(1, toggles)
    }

    @Test fun staleSongMeasurementsCannotBeShownAsTheNewSongsResults() {
        compose.setContent { LeiTheme(AppearanceSettings()) { top.yukonga.miuix.kmp.basic.Scaffold {
            CompositionLocalProvider(LocalMusicPageActive provides false) {
                MoodEffectsDetail(fixture, Song(2, "下一首歌"), false, {}, {}, {})
            }
        } } }
        compose.onNodeWithTag("mood_emotion_title").performScrollTo().assertTextEquals("情绪调音师")
        compose.onNodeWithTag("mood_adjustment_radar").assertDoesNotExist()
        compose.onNodeWithText("原算法返回的当前歌曲调节说明").assertDoesNotExist()
        compose.onNodeWithText("下一首歌").performScrollTo().assertIsDisplayed()
    }
}
