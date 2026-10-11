package io.github.currencortex.music

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.data.settings.LyricsDisplayOptions
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import io.github.currencortex.music.feature.lyrics.ui.LyricsPerspectiveAngle
import io.github.currencortex.music.feature.lyrics.ui.LyricsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LyricsMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun currentTranslationCollapsesAndLineChangesScrollContinuously() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(5000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        val position = mutableLongStateOf(1500)
        val document = LyricsDocument(listOf(
            LyricLine(1000, 3000, "循着星光慢慢向前走", translation = "Walk beneath the stars"),
            LyricLine(3000, 5000, "让音乐一直陪着你", translation = "Let music stay with you"),
            LyricLine(5000, 7000, "明天依然有温柔的光", translation = "Tomorrow still shines")))
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF171B22))) {
                LyricsScreen(document, position, {}, Modifier.width(360.dp).fillMaxHeight(),
                    effects = false, fontSize = 40f,
                    display = LyricsDisplayOptions(fontWeight = 900, stagger = true))
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(document.lines[0].translation, useUnmergedTree = true).assertExists()
        compose.onNodeWithText(document.lines[1].translation, useUnmergedTree = true).assertDoesNotExist()
        fun rowHeight(): Float = compose.onNodeWithTag("lyric_line_0").getUnclippedBoundsInRoot().let { it.bottom.value - it.top.value }
        val oldHeight = rowHeight()
        val before = compose.onNodeWithTag("lyric_line_1").getUnclippedBoundsInRoot().top.value
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { position.longValue = 3500 }
        val samples = mutableListOf<Float>()
        repeat(12) {
            compose.mainClock.advanceTimeBy(32)
            samples += compose.onNodeWithTag("lyric_line_1").getUnclippedBoundsInRoot().top.value
        }
        assertTrue("Scrolling starts within two animation frames: before=$before, samples=$samples", samples[1] < before - .1f)
        assertTrue("The active row moves continuously toward its anchor", samples.zipWithNext().all { (a, b) -> b <= a + .5f })
        compose.mainClock.advanceTimeBy(1000)
        compose.mainClock.autoAdvance = true
        assertTrue("Hidden translation and its padding must release their height", rowHeight() < oldHeight - 4f)
        compose.onNodeWithText(document.lines[0].translation, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(document.lines[1].translation, useUnmergedTree = true).assertExists()
    }

    @Test fun hiddenWrappedTranslationReleasesItsGapWhileTheParagraphStaysCentered() {
        val position = mutableLongStateOf(1500)
        val translate = androidx.compose.runtime.mutableStateOf(true)
        val document = LyricsDocument(listOf(
            LyricLine(1000, 3000, "Long lyric paragraph that wraps across several lines",
                translation = "这里有一段会换行的翻译，隐藏后应把整块区域和上方间距一起收起"),
            LyricLine(10000, 13000, "The next paragraph also has a translation", translation = "下一句的翻译")))
        compose.setContent {
            LyricsScreen(document, position, {}, Modifier.width(340.dp).fillMaxHeight().background(Color.Black),
                effects = false, fontSize = 30f, minimal = true, perspective = true, translation = translate.value)
        }
        fun row() = compose.onNodeWithTag("lyric_line_0").getUnclippedBoundsInRoot()
        fun vocal() = compose.onNodeWithTag("lyric_vocal_0", useUnmergedTree = true).getUnclippedBoundsInRoot()
        fun rowHeight() = row().let { it.bottom.value - it.top.value }
        fun vocalHeight() = vocal().let { it.bottom.value - it.top.value }
        fun centered(index: Int) {
            val panel = compose.onNodeWithTag("lyrics_panel").getUnclippedBoundsInRoot()
            val bounds = compose.onNodeWithTag("lyric_line_$index").getUnclippedBoundsInRoot()
            assertEquals("Follow the visible paragraph as its translation resizes",
                (panel.top.value + panel.bottom.value) / 2, (bounds.top.value + bounds.bottom.value) / 2, 2f)
        }
        compose.waitForIdle()
        centered(0)
        val expanded = rowHeight()
        val mainHeight = vocalHeight()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { position.longValue = 4000 }
        val closing = mutableListOf<Float>()
        repeat(20) {
            compose.mainClock.advanceTimeBy(16)
            closing += rowHeight()
            centered(0)
        }
        assertTrue("Hide the gap over several frames, not in one layout jump: expanded=$expanded, samples=$closing",
            closing.any { it < expanded - .5f && it > closing.last() + 1f })
        assertTrue("The translation slot closes continuously", closing.zipWithNext().all { (a, b) -> b <= a + .5f })
        assertTrue("A wrapped translation must no longer occupy space", expanded - closing.last() > 20f)
        assertEquals("The lyric itself does not rewrap or shrink", mainHeight, vocalHeight(), .5f)
        compose.onNodeWithText(document.lines[0].translation, useUnmergedTree = true).assertDoesNotExist()
        val collapsed = rowHeight()
        compose.runOnIdle { translate.value = false }
        compose.mainClock.advanceTimeBy(600)
        assertEquals("A hidden translation uses the same space as switching translation off", collapsed, rowHeight(), .5f)
        compose.runOnIdle { translate.value = true; position.longValue = 1500 }
        val opening = mutableListOf<Float>()
        repeat(20) {
            compose.mainClock.advanceTimeBy(16)
            opening += rowHeight()
            centered(0)
        }
        assertTrue("The translation slot opens continuously", opening.zipWithNext().all { (a, b) -> b >= a - .5f })
        assertEquals(expanded, opening.last(), .5f)
        compose.runOnIdle { position.longValue = 10500 }
        compose.mainClock.advanceTimeBy(700)
        centered(1)
        compose.mainClock.autoAdvance = true
    }

    @Test fun minimalPerspectiveLyricsHaveNoInterludeOrExtraButtons() {
        val position = mutableLongStateOf(0)
        compose.setContent {
            LyricsScreen(LyricsDocument(listOf(LyricLine(5000, 8000, "倾斜的歌词"))), position, {},
                Modifier.fillMaxSize().background(Color.Black), minimal = true, perspective = true)
        }
        compose.onNodeWithTag("lyrics_list").assertExists()
        assertEquals(-32f, compose.onNodeWithTag("lyrics_list").fetchSemanticsNode().config[LyricsPerspectiveAngle], .01f)
        compose.onNodeWithText("前奏").assertDoesNotExist()
        compose.onNodeWithTag("lyrics_list").performTouchInput { swipeUp() }
        compose.onNodeWithTag("lyrics_follow").assertDoesNotExist()
    }

    @Test fun landscapeCentersTheWholeWrappedParagraphAndTranslation() {
        val position = mutableLongStateOf(1500)
        val document = LyricsDocument(listOf(
            LyricLine(1000, 3000, "Long lyrics that wrap across several lines in the landscape pane",
                translation = "横屏长句和翻译一起居中"),
            LyricLine(3000, 5000, "A short line", translation = "短句"),
            LyricLine(5000, 7000, "Another wrapped lyric paragraph that should stay centered on screen",
                translation = "再一段较长的歌词")))
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                LyricsScreen(document, position, {}, Modifier.width(340.dp).fillMaxHeight(),
                    effects = false, fontSize = 30f, minimal = true, perspective = true)
            }
        }
        fun assertCentered(index: Int) {
            val viewport = compose.onNodeWithTag("lyrics_panel").getUnclippedBoundsInRoot()
            val row = compose.onNodeWithTag("lyric_line_$index").getUnclippedBoundsInRoot()
            assertEquals("The complete current paragraph must be centered",
                (viewport.top.value + viewport.bottom.value) / 2f, (row.top.value + row.bottom.value) / 2f, 1f)
        }
        compose.waitForIdle()
        assertCentered(0)
        compose.runOnIdle { position.longValue = 3500 }
        compose.waitForIdle()
        assertCentered(1)
        compose.runOnIdle { position.longValue = 5500 }
        compose.waitForIdle()
        assertCentered(2)
    }
}
