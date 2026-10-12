package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DesktopLyricsTextTest {
    @get:Rule val compose = createComposeRule()
    @Test fun graphemeBoundariesRetainZwjEmojiFlagsAndCombiningMarks() {
        val text = "中A e\u0301 👩🏽‍🚀 🇨🇳 日本語"
        val clusters = lyricsClusters(text).map { text.substring(it) }
        assertTrue(clusters.contains("e\u0301"))
        assertTrue(clusters.contains("👩🏽‍🚀"))
        assertTrue(clusters.contains("🇨🇳"))
        assertEquals(text, clusters.joinToString(""))
        assertFalse(lyricsCanLift("مرحبا بالعالم"))
        assertFalse(lyricsCanLift("e\u0301"))
    }
    @Test fun untimedMixedAndLongLyricsKeepFontSizeAndDoNotInventASweep() {
        val position = mutableLongStateOf(1000)
        val text = "你好 日本語 👩🏽‍🚀 e\u0301 مرحبا بالعالم Supercalifragilisticexpialidocious"
        val line = LyricLine(0, 10000, text)
        compose.setContent {
            Box(Modifier.width(230.dp).background(Color.Black).testTag("sample")) {
                KaraokeText(line, position, true, fontSize = 28f, forceLineAnimation = false,
                    accurateWordLift = true, wordLift = true, glow = true)
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(28f, layouts.single().layoutInput.style.fontSize.value, .01f)
        assertTrue(layouts.single().lineCount > 2)
        assertFalse(layouts.single().didOverflowWidth)
        val before = compose.onNodeWithTag("sample").captureToImage().toPixelMap()
        compose.runOnIdle { position.longValue = 7000 }
        val after = compose.onNodeWithTag("sample").captureToImage().toPixelMap()
        assertEquals(before.width, after.width); assertEquals(before.height, after.height)
        for (y in 0 until before.height) for (x in 0 until before.width) assertEquals(before[x,y], after[x,y])
    }
    @Test fun captionOutlineKeepsFilledInk() {
        val outlined = mutableStateOf(false)
        val line = LyricLine(0, 4000, "Hello O 世界")
        compose.setContent {
            Box(Modifier.width(230.dp).background(Color.Black).testTag("sample")) {
                KaraokeText(line, remember { mutableLongStateOf(1000) }, false, fontSize = 32f, outline = outlined.value)
            }
        }
        fun whiteInk(): Int {
            val pixels = compose.onNodeWithTag("sample").captureToImage().toPixelMap()
            var count = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) if(pixels[x,y].red > .9f) count++
            return count
        }
        val filled = whiteInk()
        compose.runOnIdle { outlined.value = true }
        assertTrue("Drawing an outline must not leave paragraph paint in Stroke mode", whiteInk() > filled * .9f)
    }
    @Test fun rtlTimedHighlightStartsFromTheRightWithoutReshapingText() {
        val text = "مرحبا"
        val position = mutableLongStateOf(2500)
        val line = LyricLine(1000, 5000, text, listOf(LyricWord(text, 1000, 5000, 0, text.length)))
        compose.setContent {
            Box(Modifier.width(230.dp).background(Color.Black).testTag("sample")) {
                KaraokeText(line, position, true, fontSize = 32f, wordLift = true, accurateWordLift = true)
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val boxes = text.indices.map(layouts.single()::getBoundingBox)
        val left = boxes.minOf { it.left }.toInt(); val right = boxes.maxOf { it.right }.toInt()
        val pixels = compose.onNodeWithTag("sample").captureToImage().toPixelMap()
        var l = 0.0; var r = 0.0
        for (y in 0 until pixels.height) for (x in left until right) {
            if (x < (left + right)/2) l += pixels[x,y].red else r += pixels[x,y].red
        }
        // Compare partially sung output to its unsung baseline to avoid glyph-area bias.
        compose.runOnIdle { position.longValue = 500 }
        val baseline = compose.onNodeWithTag("sample").captureToImage().toPixelMap()
        var baseL = 0.0; var baseR = 0.0
        for (y in 0 until baseline.height) for (x in left until right) {
            if (x < (left+right)/2) baseL += baseline[x,y].red else baseR += baseline[x,y].red
        }
        assertTrue("RTL progress must increase right ink first", r - baseR > l - baseL)
    }
}
