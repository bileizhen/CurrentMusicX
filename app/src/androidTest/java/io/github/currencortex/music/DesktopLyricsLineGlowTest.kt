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
                DesktopLyricsSurface(lyrics, player, options, {}, {}, {})
            }
        }
        compose.mainClock.advanceTimeBy(2600)
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
        compose.runOnIdle { options.value = options.value.copy(glowStrength = 1f) }
        assertTrue("Restored island bloom must be visible", greenHalo(capture()) > 100)
        compose.runOnIdle { player.value = player.value.copy(song = Song(43, "Next song")) }
        compose.mainClock.advanceTimeBy(2600)
        compose.onNodeWithTag("desktop_island_fps").assertIsDisplayed()
        assertEquals("Late lyrics must not glow for another song", 0, greenHalo(capture()))
    }
    @Test fun maximumBloomHasSoftEdgesInsideIslandBounds() {
        val line = LyricLine(0, 5000, "「質」\nAnswer")
        val lyrics = MutableStateFlow(PlaybackLyricsState(LyricsDocument(listOf(line)), songId = 42))
        val player = MutableStateFlow(PlayerState(song = Song(42, "Bloom bounds"), positionMs = 2000))
        val options = MutableStateFlow(DesktopLyricsSettings(locked = true, islandFontSize = 24f,
            textColor = 0xFFFF0000, glowColor = 0xFF00FF00, glowStrength = 2f, glowSpread = 2f))
        compose.setContent {
            Box(Modifier.size(340.dp, 300.dp).background(Color.Black).testTag("line_glow_host")) {
                DesktopLyricsSurface(lyrics, player, options, {}, {}, {})
            }
        }
        for (appearance in DesktopAppearance.entries) {
            compose.runOnIdle { options.value = options.value.copy(appearance = appearance) }
            if (appearance == DesktopAppearance.ISLAND) {
                compose.mainClock.advanceTimeBy(2400)
                compose.waitUntil(5000) {
                    compose.onAllNodesWithTag("desktop_island_lyrics").fetchSemanticsNodes().any { it.config.getOrElse(DesktopIslandReveal) { 0f } == 1f }
                }
            }
            val pixels = compose.onNodeWithText(if (appearance == DesktopAppearance.ISLAND) islandSingleLine(line.text) else line.text).captureToImage().toPixelMap()
            assertTrue("Bloom must be present in $appearance", greenHalo(pixels) > 100)
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                if (x > 1 && x < pixels.width - 2 && y > 1 && y < pixels.height - 2) continue
                val color = pixels[x, y]
                assertTrue("The halo must fade before a clipped edge in $appearance ($x,$y)",
                    color.green - maxOf(color.red, color.blue) < .015f)
            }
        }
    }
}
