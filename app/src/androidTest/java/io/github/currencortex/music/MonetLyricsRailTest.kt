package io.github.currencortex.music

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.ui.LyricsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MonetLyricsRailTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun independentTracksRetargetAndWrappedParagraphSettlesAtMonetAnchor() {
        val position = mutableLongStateOf(500)
        val lines = (0..12).map { i -> LyricLine(i * 1000L, (i + 1) * 1000L,
            if (i == 2) "一段会自动换行的长歌词 Hello world 音乐陪着你前行" else "向着星光前行 $i",
            translation = "A translation under the active paragraph $i") }
        compose.setContent { LyricsScreen(LyricsDocument(lines), position, {},
            Modifier.width(340.dp).height(600.dp).background(Color(0xFF252B35)), effects = false, fontSize = 30f) }
        fun centered(i: Int) {
            val panel = compose.onNodeWithTag("lyrics_panel").getUnclippedBoundsInRoot()
            val row = compose.onNodeWithTag("lyric_line_$i").getUnclippedBoundsInRoot()
            assertEquals(panel.top.value + (panel.bottom.value - panel.top.value) * .46f,
                (row.top.value + row.bottom.value) / 2, 1.5f)
        }
        compose.waitForIdle(); centered(0)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { position.longValue = 1500 }
        compose.mainClock.advanceTimeBy(96)
        val before = compose.onNodeWithTag("lyric_line_2").getUnclippedBoundsInRoot().top
        compose.runOnIdle { position.longValue = 2500 }
        compose.mainClock.advanceTimeBy(64)
        assertTrue(compose.onNodeWithTag("lyric_line_2").getUnclippedBoundsInRoot().top < before)
        compose.mainClock.advanceTimeBy(1800); centered(2)
        compose.runOnIdle { position.longValue = 500 }
        compose.mainClock.advanceTimeBy(1800); centered(0)
        compose.mainClock.autoAdvance = true
    }

    @Test fun browsingReturnsToPlaybackAndFarSeekUsesABoundedWindow() {
        val position = mutableLongStateOf(500)
        val lines = (0..99).map { i -> LyricLine(i * 1000L, (i + 1) * 1000L, "音乐与星光 $i") }
        var sought = -1L
        compose.setContent { LyricsScreen(LyricsDocument(lines), position, { sought = it },
            Modifier.width(340.dp).height(600.dp).background(Color.Black), effects = false) }
        compose.onNodeWithTag("lyric_line_1").performClick()
        assertEquals(1000L, sought)
        compose.runOnIdle { position.longValue = 50500 }
        compose.waitForIdle()
        compose.onNodeWithTag("lyric_line_50").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithTag("lyric_line_0").assertDoesNotExist()
        compose.onNodeWithTag("lyric_line_55").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("lyrics_list").performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("lyrics_follow").assertExists()
        compose.onNodeWithTag("lyrics_follow").performClick()
        compose.mainClock.advanceTimeBy(1800)
        compose.onNodeWithTag("lyric_line_50").assertIsDisplayed()
        compose.onNodeWithTag("lyrics_follow").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test fun nativeSweepAndGlowRenderMixedWidthWordsAndRewind() {
        val position = mutableLongStateOf(1800)
        val text = "莫奈 Monet 星光"
        val lines = listOf(
            LyricLine(1000, 3000, text, listOf(
                LyricWord("莫奈", 1000, 1600, 0, 2), LyricWord("Monet", 1600, 2600, 3, 8),
                LyricWord("星光", 2600, 3000, 9, 11)), translation = "Music stays with you"),
            LyricLine(3000, 5000, "下一句温柔流动"))
        compose.mainClock.autoAdvance = false
        compose.setContent { LyricsScreen(LyricsDocument(lines), position, {},
            Modifier.width(340.dp).height(600.dp).background(Color(0xFF252B35)), fontSize = 30f) }
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        val image = compose.onNodeWithTag("lyrics_panel").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.filesDir, "monet-port-preview.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.runOnIdle { position.longValue = 3300 }
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        compose.onNodeWithTag("lyric_line_1").assertIsSelected()
        compose.runOnIdle { position.longValue = 1200 }
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        compose.onNodeWithTag("lyric_line_0").assertIsSelected()
        compose.mainClock.autoAdvance = true
    }
}
