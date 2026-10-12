package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.PlaybackLyricsState
import io.github.currencortex.music.feature.lyrics.desktop.*
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercise the actual desktop host with line-only lyrics, rather than a fabricated long word. */
class DesktopLyricsLineGlowTest {
    @get:Rule val compose = createComposeRule()
    private fun capture() = compose.onNodeWithTag("line_glow_host").captureToImage().toPixelMap()
    private fun greenHalo(pixels: PixelMap): Int {
        var count = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width)
            if (pixels[x, y].green > pixels[x, y].red + .025f && pixels[x, y].green > pixels[x, y].blue + .025f) count++
        return count
    }
    @Test fun lineOnlyLyricsHaveVisibleDefaultBloomIndependentColorsAndNoFakeSweep() {
        val line = LyricLine(1000, 5000, "静かな音楽 Hello", translation = "Music beside you")
        val lyrics = MutableStateFlow(PlaybackLyricsState(LyricsDocument(listOf(line)), songId = 42))
        val player = MutableStateFlow(PlayerState(song = Song(42, "Line timing"), positionMs = 2000))
        val options = MutableStateFlow(DesktopLyricsSettings(locked = true, glowEnabled = false))
        compose.setContent {
            Box(Modifier.size(340.dp, 240.dp).background(Color(0xFF161A25)).testTag("line_glow_host")) {
                DesktopLyricsSurface(lyrics, player, options, { 160f }, { _, _ -> }, {}, {}, {}, {})
            }
        }
        val unlit = capture()
        compose.runOnIdle { options.value = options.value.copy(glowEnabled = true) }
        val lit = capture()
        var visible = 0
        for (y in 0 until lit.height) for (x in 0 until lit.width)
            if (unlit[x, y].red < .3f && lit[x, y].red > unlit[x, y].red + .025f) visible++
        assertTrue("Default grey bloom must be visible around line-only text: $visible", visible > 100)
        compose.runOnIdle { options.value = options.value.copy(glowColor = 0xFF00FF00, glowStrength = 2f) }
        val colored = greenHalo(capture())
        assertTrue("Chosen glow tint must reach the desktop host", colored > 100)
        compose.runOnIdle { player.value = player.value.copy(positionMs = 3500) }
        assertEquals("Line-only timing must not create a moving karaoke sweep", colored, greenHalo(capture()))
        compose.runOnIdle { options.value = options.value.copy(glowStrength = 0f) }
        assertEquals(0, greenHalo(capture()))
        compose.runOnIdle { options.value = options.value.copy(glowStrength = 1f, appearance = DesktopAppearance.CAPTION) }
        assertTrue("Transparent captions must also have bloom", greenHalo(capture()) > 100)
        compose.runOnIdle { player.value = player.value.copy(song = Song(43, "Next song")) }
        compose.onNodeWithText("正在读取歌词").assertExists()
        assertEquals("Late lyrics must not glow for another song", 0, greenHalo(capture()))
    }
    @Test fun maximumBloomHasSoftEdgesInsideCaptionAndCapsuleBounds() {
        val line = LyricLine(0, 5000, "「質」\nAnswer")
        val lyrics = MutableStateFlow(PlaybackLyricsState(LyricsDocument(listOf(line)), songId = 42))
        val player = MutableStateFlow(PlayerState(song = Song(42, "Bloom bounds"), positionMs = 2000))
        val options = MutableStateFlow(DesktopLyricsSettings(locked = true, fontSize = 32f,
            textColor = 0xFFFF0000, glowColor = 0xFF00FF00, glowStrength = 2f, glowSpread = 2f))
        compose.setContent {
            Box(Modifier.size(340.dp, 300.dp).background(Color.Black).testTag("line_glow_host")) {
                DesktopLyricsSurface(lyrics, player, options, { 220f }, { _, _ -> }, {}, {}, {}, {})
            }
        }
        for (appearance in DesktopAppearance.entries) {
            compose.runOnIdle { options.value = options.value.copy(appearance = appearance) }
            val pixels = compose.onNodeWithText(line.text).captureToImage().toPixelMap()
            assertTrue("Bloom must be present in $appearance", greenHalo(pixels) > 100)
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                if (x > 1 && x < pixels.width - 2 && y > 1 && y < pixels.height - 2) continue
                val color = pixels[x, y]
                assertTrue("The halo must fade before a clipped edge in $appearance ($x,$y)",
                    color.green - maxOf(color.red, color.blue) < .015f)
            }
        }
    }
    @Test fun transparentCaptionHasNoDarkOutlineOrTranslationAndStateShadows() {
        val line = LyricLine(0, 5000, "Caption", translation = "Translation")
        val lyrics = MutableStateFlow(PlaybackLyricsState(LyricsDocument(listOf(line)), songId = 42))
        val player = MutableStateFlow(PlayerState(song = Song(42, "Caption"), positionMs = 2000))
        val options = MutableStateFlow(DesktopLyricsSettings(locked = true, glowEnabled = false,
            appearance = DesktopAppearance.CAPTION, textColor = 0xFF0088FF))
        compose.setContent {
            Box(Modifier.size(340.dp, 240.dp).background(Color.White).testTag("line_glow_host")) {
                DesktopLyricsSurface(lyrics, player, options, { 160f }, { _, _ -> }, {}, {}, {}, {})
            }
        }
        fun assertNoDarkInk() {
            val pixels = capture()
            var dark = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width)
                if (pixels[x, y].red < .25f && pixels[x, y].green < .25f && pixels[x, y].blue < .25f) dark++
            assertEquals("Transparent captions must not draw black strokes or shadows", 0, dark)
        }
        assertNoDarkInk()
        compose.runOnIdle { player.value = player.value.copy(song = null) }
        compose.onNodeWithText("等待播放").assertExists()
        assertNoDarkInk()
    }
    @Test fun compactDefaultAlignsTextAndLockRemovesEditingChrome() {
        val line = LyricLine(0, 5000, "音乐 Hello", translation = "Music beside you")
        val lyrics = MutableStateFlow(PlaybackLyricsState(LyricsDocument(listOf(line)), songId = 42))
        val player = MutableStateFlow(PlayerState(song = Song(42, "Compact song"), positionMs = 2000))
        val options = MutableStateFlow(DesktopLyricsSettings(appearance = DesktopAppearance.CAPTION))
        compose.setContent {
            Box(Modifier.width(340.dp).background(Color.White).testTag("line_glow_host")) {
                DesktopLyricsSurface(lyrics, player, options, { 200f }, { _, _ -> }, {}, {}, {}, {})
            }
        }
        val title = compose.onNodeWithTag("desktop_window_title").getUnclippedBoundsInRoot()
        val translation = compose.onNodeWithTag("desktop_window_translation").getUnclippedBoundsInRoot()
        val paragraph = compose.onNodeWithText(line.text).getUnclippedBoundsInRoot()
        assertEquals(title.left.value, translation.left.value, .1f)
        assertEquals(paragraph.left.value + 20f, translation.left.value, .1f)
        val button = compose.onNodeWithText("设置").getUnclippedBoundsInRoot()
        assertTrue(button.bottom - button.top >= 48.dp)
        fun windowHeight() = compose.onNodeWithTag("desktop_lyrics_window").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val editedHeight = windowHeight()
        compose.runOnIdle { options.value = options.value.copy(locked = true) }
        compose.onNodeWithTag("desktop_edit_toolbar").assertDoesNotExist()
        compose.onNodeWithTag("desktop_window_title").assertDoesNotExist()
        compose.onNodeWithText(line.text).assertIsDisplayed()
        compose.onNodeWithTag("desktop_window_translation").assertIsDisplayed()
        val lockedHeight = windowHeight()
        assertTrue("A default single caption should be compact: $lockedHeight", lockedHeight < 100.dp)
        assertTrue(editedHeight - lockedHeight >= 48.dp)
    }
}
