package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.data.settings.KaraokeScope
import io.github.currencortex.music.data.settings.LyricsDisplayOptions
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.ui.LyricsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LyricsSilenceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun introGapsAndOutroKeepAClearStableReadingAnchorWithoutPretendingToSing() {
        val position = mutableLongStateOf(0)
        val effects = mutableStateOf(true)
        val lines = listOf("星光仍在", "音乐继续", "温柔落幕").mapIndexed { index, text ->
            val start = 5000L + index * 7000
            LyricLine(start, start + 2000, text, text.indices.map { offset ->
                LyricWord(text.substring(offset, offset + 1), start + offset * 500,
                    start + (offset + 1) * 500, offset, offset + 1)
            }, translation = "Translation $index")
        }
        compose.setContent {
            LyricsScreen(LyricsDocument(lines, LyricsMetadata(durationMs = 26000)), position, {},
                Modifier.width(340.dp).fillMaxHeight().background(Color.Black), minimal = true,
                effects = effects.value, fontSize = 30f,
                display = LyricsDisplayOptions(karaokeScope = KaraokeScope.ALWAYS))
        }
        fun row(index: Int) = compose.onNodeWithTag("lyric_line_$index")
        fun ink(index: Int): Pair<Float, Double> {
            val pixels = row(index).captureToImage().toPixelMap()
            var brightest = 0f; var total = 0.0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                brightest = maxOf(brightest, pixels[x, y].red)
                total += pixels[x, y].red
            }
            return brightest to total
        }
        fun assertResting(index: Int) {
            compose.waitForIdle()
            row(index).assertIsNotSelected()
            lines.forEach { compose.onNodeWithText(it.translation, useUnmergedTree = true).assertDoesNotExist() }
            val bounds = row(index).getUnclippedBoundsInRoot()
            val viewport = compose.onNodeWithTag("lyrics_panel").getUnclippedBoundsInRoot()
            assertEquals("The resting paragraph keeps its reading anchor",
                (viewport.top.value + viewport.bottom.value) / 2,
                (bounds.top.value + bounds.bottom.value) / 2, 1f)
            val clear = ink(index)
            assertTrue("Silence must not dim every paragraph into the background",
                clear.first > if (position.longValue < lines[index].startTimeMs) .30f else .55f)
            compose.runOnIdle { effects.value = false }
            assertEquals("The resting glyphs have no background blur", clear.second, ink(index).second, clear.second * .01)
            compose.runOnIdle { effects.value = true }
        }
        assertResting(0) // Intro preview is readable, but no vocal is active.
        compose.runOnIdle { position.longValue = 5500 }
        row(0).assertIsSelected()
        compose.onNodeWithText(lines[0].translation, useUnmergedTree = true).assertExists()
        val beforeGap = row(0).getUnclippedBoundsInRoot()
        val beforeVocalHeight = compose.onNodeWithTag("lyric_vocal_0", useUnmergedTree = true).getUnclippedBoundsInRoot().let { it.bottom.value - it.top.value }
        compose.runOnIdle { position.longValue = 8000 }
        assertResting(0)
        assertTrue("The hidden translation no longer leaves an empty slot", row(0).getUnclippedBoundsInRoot().let { it.bottom.value - it.top.value } < beforeGap.bottom.value - beforeGap.top.value - 4f)
        assertEquals("Ending the vocal must not shrink or reflow the main lyric", beforeVocalHeight,
            compose.onNodeWithTag("lyric_vocal_0", useUnmergedTree = true).getUnclippedBoundsInRoot().let { it.bottom.value - it.top.value }, .5f)
        val duringGap = ink(0)
        compose.runOnIdle { position.longValue = 11000 }
        assertEquals("No artificial karaoke sweep runs during silence", duringGap.second, ink(0).second, 1.0)
        compose.runOnIdle { position.longValue = 12500 }
        row(1).assertIsSelected()
        row(0).assertIsNotSelected()
        compose.onNodeWithText(lines[1].translation, useUnmergedTree = true).assertExists()
        compose.runOnIdle { position.longValue = 22500 }
        assertResting(2)
        compose.runOnIdle { position.longValue = 8000 }
        assertResting(0) // Backward seeking also restores the correct resting paragraph.
    }
}
