package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.ui.KaraokeText
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LyricsEffectsRenderingTest {
    @get:Rule val compose = createComposeRule()
    private fun capture() = compose.onNodeWithTag("effect_sample").captureToImage().toPixelMap()
    private fun ink(pixels: PixelMap): Pair<Double, Double> {
        var total = 0.0; var vertical = 0.0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val value = pixels[x, y].red.toDouble()
            total += value; vertical += value * y
        }
        return total to vertical / total
    }
    @Test fun partiallySungGlyphHasAnUncutHaloWithoutPrematurelyWhiteningItsUnsungInk() {
        val glow = mutableStateOf(false)
        val position = mutableLongStateOf(1800)
        val line = LyricLine(1000, 3400, "O N", listOf(
            LyricWord("O", 1000, 3000, 0, 1), LyricWord("N", 3000, 3400, 2, 3)))
        compose.setContent {
            Box(Modifier.size(240.dp, 120.dp).background(Color.Black).testTag("effect_sample")) {
                KaraokeText(line, position, true, Modifier.padding(24.dp).testTag("effect_text"),
                    fontSize = 40f, glow = glow.value)
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(line.text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val glyph = layouts.single().getBoundingBox(0)
        val normal = capture()
        compose.runOnIdle { glow.value = true }
        val shining = capture()
        val density = normal.width / 240f
        // The previous implementation cut the blurred bitmap at sweep + 6 dp.
        val oldCut = ((24f + 6f) * density + glyph.left + glyph.width * .4f).toInt()
        var outside = 0
        var unsungInk = 0
        var excessiveInk = 0
        for (y in 0 until normal.height) for (x in 0 until normal.width) {
            val base = normal[x, y].red
            val added = shining[x, y].red - base
            if (x > oldCut && base < .005f && added > .01f) outside++
            if (x > oldCut && base in .30f.. .34f) {
                unsungInk++
                if (added > .10f) excessiveInk++
            }
        }
        assertTrue("The blur footprint must extend softly beyond the karaoke boundary", outside > 40)
        assertTrue("Unsung stroke pixels must be sampled", unsungInk > 20)
        assertTrue("The glow cannot substitute white ink for the unsung stroke", excessiveInk < unsungInk / 10)
    }
    @Test fun glowOnlySurroundsTheCurrentlySungLongNoteAndCanBeDisabled() {
        val glow = mutableStateOf(false)
        val position = mutableLongStateOf(2300)
        val line = LyricLine(1000, 3500, "星光", listOf(
            LyricWord("星", 1000, 1400, 0, 1), LyricWord("光", 1400, 3500, 1, 2)))
        compose.setContent {
            Box(Modifier.size(240.dp, 120.dp).background(Color.Black).testTag("effect_sample")) {
                KaraokeText(line, position, false,
                    Modifier.padding(24.dp), fontSize = 40f, glow = glow.value)
            }
        }
        val normal = capture()
        compose.runOnIdle { glow.value = true }
        val shining = capture()
        var haloPixels = 0
        for (y in 0 until normal.height) for (x in 0 until normal.width)
            if (normal[x, y].red < .005f && shining[x, y].red > .02f) haloPixels++
        assertTrue("A visible halo must surround the glyph contours", haloPixels > 100)
        compose.runOnIdle { glow.value = false }
        assertEquals(ink(normal).first, ink(capture()).first, 1.0)
        compose.runOnIdle { glow.value = true; position.longValue = 1200 }
        assertEquals("Ordinary short syllables must not glow", ink(normal).first, ink(capture()).first, 1.0)
        compose.runOnIdle { position.longValue = 4000 }
        assertEquals("Sung words must not keep glowing", ink(normal).first, ink(capture()).first, 1.0)
    }
    @Test fun mediumHeldNotesNowProduceVisibleBloomWhileShortNotesRemainUnlit() {
        val glow = mutableStateOf(false)
        val position = mutableLongStateOf(1400)
        val line = LyricLine(1000, 2000, "光点", listOf(
            LyricWord("光", 1000, 1750, 0, 1), LyricWord("点", 1750, 1950, 1, 2)))
        compose.setContent {
            Box(Modifier.size(240.dp, 120.dp).background(Color.Black).testTag("effect_sample")) {
                KaraokeText(line, position, true, Modifier.padding(24.dp), fontSize = 40f, glow = glow.value)
            }
        }
        val normal = capture()
        compose.runOnIdle { glow.value = true }
        val shining = capture()
        var haloPixels = 0
        for (y in 0 until normal.height) for (x in 0 until normal.width)
            if (normal[x, y].red < .005f && shining[x, y].red > .01f) haloPixels++
        assertTrue("A 750 ms note must now show a soft halo: $haloPixels", haloPixels > 40)
        compose.runOnIdle { position.longValue = 1850; glow.value = false }
        val shortNote = ink(capture()).first
        compose.runOnIdle { glow.value = true }
        assertEquals("The adjacent short syllable still does not glow", shortNote, ink(capture()).first, 1.0)
    }
    @Test fun wordLiftStaysRaisedAfterSingingAndLosingFocusWithoutReflowOrDuplicateInk() {
        val lift = mutableStateOf(false)
        val animate = mutableStateOf(true)
        val position = mutableLongStateOf(1650)
        val line = LyricLine(1000, 2000, "光", listOf(LyricWord("光", 1000, 1600, 0, 1)))
        compose.setContent {
            Box(Modifier.size(240.dp, 120.dp).background(Color.Black).testTag("effect_sample")) {
                KaraokeText(line, position, animate.value, Modifier.padding(24.dp).testTag("effect_text"),
                    fontSize = 40f, wordLift = lift.value)
            }
        }
        val normal = ink(capture())
        val bounds = compose.onNodeWithTag("effect_text").getUnclippedBoundsInRoot()
        compose.runOnIdle { lift.value = true }
        val lifted = ink(capture())
        assertTrue("The actual glyph must move upward", lifted.second < normal.second - 1)
        assertEquals("No duplicate glyph remains underneath", normal.first, lifted.first, normal.first * .1)
        assertEquals(bounds, compose.onNodeWithTag("effect_text").getUnclippedBoundsInRoot())
        compose.runOnIdle { position.longValue = 2300 }
        assertEquals("The sung glyph stays raised", lifted.second, ink(capture()).second, .5)
        compose.runOnIdle { animate.value = false }
        assertEquals("Losing current-line focus must not drop the glyph", lifted.second, ink(capture()).second, .5)
        compose.runOnIdle { position.longValue = 900 }
        assertEquals("Seeking before singing resets the lift", normal.second, ink(capture()).second, .5)
        assertEquals(bounds, compose.onNodeWithTag("effect_text").getUnclippedBoundsInRoot())
    }
}
