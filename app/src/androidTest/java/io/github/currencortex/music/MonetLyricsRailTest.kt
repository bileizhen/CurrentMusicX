package io.github.currencortex.music

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.ui.LyricsScreen
import io.github.currencortex.music.feature.lyrics.ui.MonetLyricsRail
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class MonetLyricsRailTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun expandedLyricsUseTheNewSpaceWithoutMountingTheWholeSong() {
        val expanded = mutableStateOf(false)
        val document = LyricsDocument((0..99).map { LyricLine(it * 2000L, (it + 1) * 2000L, "歌词 $it") })
        compose.setContent {
            MonetLyricsRail(document, 50, setOf(50),
                Modifier.width(340.dp).height(if (expanded.value) 600.dp else 300.dp).background(Color.Black),
                30f, centered = false, minimal = false, perspective = false, effects = false,
                followRequest = 0, onBrowsing = {}, expandWindow = true) { index, _, _ ->
                Canvas(Modifier.fillMaxWidth().height(30.dp).testTag("viewport_line_$index")) { drawRect(Color.White) }
            }
        }
        compose.onNodeWithTag("viewport_line_57").assertDoesNotExist()
        compose.runOnIdle { expanded.value = true }
        compose.onNodeWithTag("viewport_line_57").assertIsDisplayed()
        compose.onNodeWithTag("viewport_line_0").assertDoesNotExist()
        compose.onNodeWithTag("viewport_line_99").assertDoesNotExist()
        val viewport = compose.onNodeWithTag("lyrics_list").fetchSemanticsNode().boundsInRoot
        val extra = compose.onNodeWithTag("viewport_line_57").fetchSemanticsNode().boundsInRoot
        assertTrue("Extra lyrics occupy the lower half of the expanded area", extra.top > viewport.center.y && extra.bottom < viewport.bottom)
        compose.runOnIdle { expanded.value = false }
        compose.waitForIdle()
        compose.onNodeWithTag("viewport_line_57").assertDoesNotExist()
    }

    @Test fun lyricBloomFadesIntoBothViewportEdgesWithoutMovingTheText() {
        val duet = mutableStateOf(false)
        val position = mutableLongStateOf(4800)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val text = "怅然若失流连忘返啊"
            val line = LyricLine(1000, 5000, text,
                listOf(LyricWord(text, 1000, 5000, 0, text.length)), isDuet = duet.value)
            LyricsScreen(LyricsDocument(listOf(line)), position, {},
                Modifier.width(340.dp).height(600.dp).background(Color.Black), fontSize = 30f)
        }
        for (right in listOf(false, true)) {
            compose.runOnIdle { duet.value = right }
            compose.mainClock.advanceTimeBy(2000)
            compose.waitForIdle()
            val image = compose.onNodeWithTag("lyrics_panel").captureToImage().asAndroidBitmap()
            val edgeX = if (right) image.width - 1 else 0
            val gutterX = if (right) image.width - 1 - (image.width * 6f / 340f).toInt()
                else (image.width * 6f / 340f).toInt()
            fun peak(x: Int) = (image.height / 4..image.height * 3 / 4).maxOf { y ->
                android.graphics.Color.red(image.getPixel(x, y))
            }
            val edge = peak(edgeX)
            val gutter = peak(gutterX)
            println("Monet edge right=$right boundary=$edge gutter=$gutter")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            java.io.File(context.filesDir, "monet-edge-${if (right) "right" else "left"}.png").outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            assertTrue("Bloom must reach the gutter, not stop at the paragraph: $gutter", gutter > 4)
            assertTrue("Bloom must fade before the viewport boundary: $edge vs $gutter", edge <= 3 && edge < gutter)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun fadingBlurredTracksKeepInkOutsideTheirParagraphBounds() {
        val document = LyricsDocument((0..2).map { LyricLine(it * 2000L, (it + 1) * 2000L, "边缘") })
        compose.setContent {
            MonetLyricsRail(document, 1, setOf(1),
                Modifier.width(340.dp).height(600.dp).background(Color.Black), 30f,
                centered = false, minimal = false, perspective = false, effects = true,
                followRequest = 0, onBrowsing = {}) { index, _, _ ->
                Canvas(Modifier.fillMaxWidth().height(60.dp).testTag("edge_probe_$index")) {
                    if (index == 0) drawCircle(Color.White, 20.dp.toPx(), androidx.compose.ui.geometry.Offset(0f, size.height / 2))
                }
            }
        }
        compose.waitForIdle()
        val panel = compose.onNodeWithTag("lyrics_list").fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("edge_probe_0").fetchSemanticsNode().boundsInRoot
        val image = compose.onNodeWithTag("lyrics_list").captureToImage().asAndroidBitmap()
        val y = (row.center.y - panel.top).toInt().coerceIn(0, image.height - 1)
        val x = (image.width * 4f / 340f).toInt()
        val ink = android.graphics.Color.red(image.getPixel(x, y))
        assertTrue("Blur/alpha must retain overscan inside the viewport gutter: $ink", ink > 15)
    }

    @Test fun approachingTheReadingAnchorDoesNotSnapTheParagraphScale() {
        val position = mutableLongStateOf(500)
        val document = LyricsDocument((0..3).map { i ->
            LyricLine(i * 2000L, (i + 1) * 2000L, "让音乐一直陪你向前走 $i")
        })
        compose.setContent { LyricsScreen(document, position, {},
            Modifier.width(340.dp).height(600.dp).background(Color.Black),
            effects = false, translation = false, fontSize = 30f) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { position.longValue = 2500 }
        val widths = mutableListOf<Float>()
        repeat(100) {
            compose.mainClock.advanceTimeBy(16)
            val bounds = compose.onNodeWithTag("lyric_vocal_1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            widths += bounds.width
        }
        val finalWidth = widths.last()
        val tail = widths.zipWithNext().drop(28).map { (a, b) -> abs(b - a) / finalWidth }
        assertTrue("Scale must decelerate through the old ~600ms cutoff without a one-percent step: $tail",
            tail.zipWithNext().all { (a, b) -> b <= a + .0005f })
        assertTrue("The reading paragraph still reaches its complete size", finalWidth > widths.first() * 1.3f)
        compose.mainClock.autoAdvance = true
    }

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
