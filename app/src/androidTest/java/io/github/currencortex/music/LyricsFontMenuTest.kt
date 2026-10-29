package io.github.currencortex.music

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.lyrics.ui.*
import io.github.currencortex.music.feature.player.LyricsFontSelector
import io.github.currencortex.music.feature.player.LyricsDisplaySettings
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Scaffold

class LyricsFontMenuTest {
    @get:Rule val compose = createComposeRule()

    @Test fun previewFollowsCurrentLineSeekOffsetsAndSongChanges() {
        val song = io.github.currencortex.music.data.song.Song(7, "fixture")
        var player by mutableStateOf(io.github.currencortex.music.core.media.PlayerState(song = song, positionMs = 1500))
        var globalOffset by mutableLongStateOf(0)
        var lyrics by mutableStateOf(io.github.currencortex.music.feature.lyrics.data.PlaybackLyricsState(
            document = io.github.currencortex.music.feature.lyrics.model.LyricsDocument(listOf(
                io.github.currencortex.music.feature.lyrics.model.LyricLine(1000, 3000, "当前播放的第一句"),
                io.github.currencortex.music.feature.lyrics.model.LyricLine(5000, 8000, "拖动后的第二句"))), songId = song.id))
        compose.setContent {
            val text = io.github.currencortex.music.feature.player.currentSettingsLyric(lyrics, player, player.song, globalOffset)
            LeiTheme(AppearanceSettings(blur = false)) { Scaffold { MusicDialog("歌词显示", {}) {
                LyricsDisplaySettings(25f, {}, LyricsWeight.CURRENT, {}, LyricsDisplayOptions(), {}, {}, previewText = text)
            } } }
        }
        compose.onNodeWithTag("lyrics_font_preview").assertTextEquals("当前播放的第一句")
        compose.runOnIdle { player = player.copy(positionMs = 5500) }
        compose.onNodeWithTag("lyrics_font_preview").assertTextEquals("拖动后的第二句")
        compose.runOnIdle { player = player.copy(positionMs = 3500); globalOffset = 2000 }
        compose.onNodeWithTag("lyrics_font_preview").assertTextEquals("拖动后的第二句")
        compose.runOnIdle { lyrics = lyrics.copy(songOffsetMs = 2000) }
        compose.onNodeWithTag("lyrics_font_preview").assertTextEquals("间奏")
        compose.runOnIdle { player = player.copy(song = song.copy(id = 8)) }
        compose.onNodeWithTag("lyrics_font_preview").assertTextEquals("正在加载歌词")
        compose.runOnIdle { lyrics = lyrics.copy(document = io.github.currencortex.music.feature.lyrics.model.LyricsDocument(), songId = 8) }
        compose.onNodeWithTag("lyrics_font_preview").assertTextEquals("暂无歌词")
    }

    @Test fun anchoredMenuSwitchesLyricsWithoutFollowingGlobalFont() {
        var settings by mutableStateOf(MusicSettings())
        var resolved: FontFamily? = null
        compose.setContent {
            LeiTheme(AppearanceSettings(blur = false)) { Scaffold {
                CompositionLocalProvider(LocalCustomFont provides FontFamily.Monospace,
                    LocalLyricsFont provides rememberLyricsFont(settings)) {
                    resolved = currentLyricsFontFamily()
                    MusicDialog("歌词显示", {}) {
                        LyricsDisplaySettings(25f, {}, settings.lyricsWeight, {}, settings.lyricsDisplay,
                            {}, {}, settings.lyricsFont, settings.lyricsFontFile,
                            { settings = settings.copy(lyricsFont = it) }, {})
                    }
                }
            } }
        }
        compose.runOnIdle { assertEquals(FontFamily.Default, resolved) }
        compose.onNodeWithTag("lyrics_font_dropdown").performClick()
        compose.onNodeWithText("霞鹜文楷").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(WenkaiLyricsFontFamily, resolved) }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.onNodeWithTag("music_dialog_surface").captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(context.getExternalFilesDir(null), "lyrics-display-aligned.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithTag("lyrics_font_dropdown").assertTextContains("霞鹜文楷").performClick()
        compose.onNodeWithText("系统字体").performClick()
        compose.runOnIdle { assertEquals(FontFamily.Default, resolved) }
    }

    @Test fun importedLyricsFontResolvesIndependentlyOfGlobalFont() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = importCustomFont(context, android.net.Uri.parse("android.resource://${context.packageName}/${R.font.lxgw_wenkai_regular}"))
        try {
            var resolved: FontFamily? = null
            compose.setContent {
                val font = rememberLyricsFont(MusicSettings(lyricsFont = LyricsFont.IMPORTED, lyricsFontFile = name))
                CompositionLocalProvider(LocalCustomFont provides FontFamily.Monospace, LocalLyricsFont provides font) {
                    resolved = currentLyricsFontFamily()
                }
            }
            compose.waitUntil(5000) { resolved != null && resolved != FontFamily.Default }
            compose.runOnIdle { assertNotEquals(FontFamily.Monospace, resolved) }
        } finally { deleteCustomFont(context, name) }
    }
}
