package io.github.currencortex.music

import android.content.pm.ActivityInfo
import android.view.FrameMetrics
import android.view.Window
import android.os.Handler
import android.os.HandlerThread
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.LyricsDisplayOptions
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.ui.LyricsScreen
import io.github.currencortex.music.feature.player.PlayerSeekBar
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.Collections

class LyricsPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun smokeKeepsOnlyCurrentAndNextAndAuxiliaryTogglesNeverMoveTheCurrentInk() {
        val position = mutableLongStateOf(1500)
        val auxiliary = mutableStateOf(true)
        var seek = -1L
        val document = LyricsDocument(listOf(
            LyricLine(1000, 4000, "当前歌词", translation = "当前句的翻译", romanization = "dang qian ge ci"),
            LyricLine(4000, 7000, "下一句预览"), LyricLine(7000, 9000, "最后一句")))
        compose.setContent {
            LyricsScreen(document, position, { seek = it }, Modifier.width(360.dp).height(600.dp).background(Color(0xff111722)),
                translation = auxiliary.value, romanization = auxiliary.value, lyricsOffsetMs = 500,
                perspective = true, display = LyricsDisplayOptions(smoke = true, centered = false, perspective = true))
        }
        compose.onNodeWithTag("lyrics_list").assertDoesNotExist()
        compose.onNodeWithTag("lyric_line_2").assertDoesNotExist()
        val panel = compose.onNodeWithTag("lyrics_panel").getUnclippedBoundsInRoot()
        fun current() = compose.onNodeWithTag("lyric_line_0").getUnclippedBoundsInRoot()
        val initial = current()
        assertEquals((panel.top.value + panel.bottom.value) / 2, (initial.top.value + initial.bottom.value) / 2, 1f)
        val preview = compose.onNodeWithTag("smoke_next_line").getUnclippedBoundsInRoot()
        assertTrue(preview.top > initial.bottom)
        val translated = compose.onNodeWithTag("lyric_translation", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("Translation stays inside the lyric viewport", translated.top >= panel.top)
        assertTrue("Translation is pinned to the top of the lyric viewport", translated.top - panel.top in 16.dp..24.dp)
        assertTrue("Translation is separate from the centered current line", translated.bottom + 40.dp < initial.top)
        assertTrue("The preview is visibly smaller",
            compose.onNodeWithTag("smoke_next_line").fetchSemanticsNode().boundsInRoot.height <
                compose.onNodeWithTag("lyric_line_0").fetchSemanticsNode().boundsInRoot.height)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { auxiliary.value = false }
        repeat(20) {
            compose.mainClock.advanceTimeBy(16); assertEquals(initial, current())
            assertEquals(preview, compose.onNodeWithTag("smoke_next_line").getUnclippedBoundsInRoot())
        }
        compose.onNodeWithTag("lyric_translation").assertDoesNotExist()
        compose.onNodeWithTag("lyric_romanization").assertDoesNotExist()
        compose.runOnUiThread { auxiliary.value = true }
        repeat(20) { compose.mainClock.advanceTimeBy(16); assertEquals(initial, current()) }
        compose.onNodeWithTag("lyrics_panel").captureToImage().asAndroidBitmap().let { bitmap ->
            val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
            File(context.externalCacheDir, "smoke-viewport-top-translation.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        compose.onNodeWithTag("smoke_next_line").performClick(); assertEquals(3500L, seek)
        compose.runOnUiThread { position.longValue = 7500 }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("lyric_line_2").assertExists()
        compose.onNodeWithTag("smoke_next_line").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }
    @Test fun charactersRiseOneByOneFromThePreviewWithoutRewrapping() {
        val position = mutableLongStateOf(1500)
        val document = LyricsDocument(listOf(
            LyricLine(1000, 4000, "此刻的旋律", translation = "The melody now"),
            LyricLine(4000, 8000, "让下一句歌词从底部轻轻飘向中间", translation = "Let the next line rise"),
            LyricLine(8000, 11000, "继续向前")))
        compose.setContent {
            LyricsScreen(document, position, {}, Modifier.width(360.dp).height(600.dp).background(Color(0xff111722)),
                display = LyricsDisplayOptions(smoke = true))
        }
        val source = compose.onNodeWithTag("smoke_next_line").fetchSemanticsNode().boundsInRoot
        val previewLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(document.lines[1].text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(previewLayouts) }
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { position.longValue = 4000 }
        compose.mainClock.advanceTimeByFrame()
        val start = compose.onNodeWithTag("lyric_line_1").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("lyrics_panel").fetchSemanticsNode().boundsInRoot
        val layout = previewLayouts.single()
        fun lit(bitmap: android.graphics.Bitmap, index: Int, bottom: Boolean): Int {
            val box = layout.getBoundingBox(index)
            val scale = if (bottom) source.width / layout.size.width else 1f
            val anchor = if (bottom) source else start
            val left = ((anchor.left - panel.left) + box.left * scale).toInt().coerceAtLeast(0)
            val top = ((anchor.top - panel.top) + box.top * scale).toInt().coerceAtLeast(0)
            val right = ((anchor.left - panel.left) + box.right * scale).toInt().coerceAtMost(bitmap.width)
            val endY = ((anchor.top - panel.top) + box.bottom * scale).toInt().coerceAtMost(bitmap.height)
            var count = 0
            for (y in top until endY) for (x in left until right)
                if (android.graphics.Color.red(bitmap.getPixel(x, y)) > 90) count++
            return count
        }
        val last = document.lines[1].text.lastIndex
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val frames = File(context.externalCacheDir, "smoke-character-rise").apply { mkdirs() }
        repeat(61) { frame ->
            compose.runOnUiThread { position.longValue = 4000 + frame * 40L }
            compose.mainClock.advanceTimeBy(40)
            compose.onNodeWithTag("lyrics_panel").captureToImage().asAndroidBitmap().let { bitmap ->
                File(frames, "frame-%03d.png".format(frame)).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                if (frame == 25) {
                    assertTrue("The first character has arrived in the center", lit(bitmap, 0, false) > 30)
                    assertTrue("The last character still waits at the bottom", lit(bitmap, last, true) > 30)
                    // Use the first row: flying glyphs from that row may cross the
                    // second row's eventual destination during a wrapped transition.
                    assertEquals("Do not reveal the whole central sentence at once", 0,
                        lit(bitmap, layout.getLineEnd(0, visibleEnd = true) - 1, false))
                    compose.mainClock.advanceTimeBy(400)
                    val paused = compose.onNodeWithTag("lyrics_panel").captureToImage().asAndroidBitmap()
                    assertTrue("Character travel freezes with media time", bitmap.sameAs(paused))
                    paused.recycle()
                }
                if (frame == 60) assertTrue("The last character eventually settles in the center", lit(bitmap, last, false) > 30)
                bitmap.recycle()
            }
        }
        val end = compose.onNodeWithTag("lyric_line_1").fetchSemanticsNode().boundsInRoot
        assertEquals((panel.top + panel.bottom) / 2, (end.top + end.bottom) / 2, 1f)
        val currentLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(document.lines[1].text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(currentLayouts) }
        assertEquals("Keep paragraph wrapping through the entire flight", previewLayouts.single().size, currentLayouts.single().size)
        // Interrupt a second promotion with a backward seek; the stale flight must stop.
        compose.runOnUiThread { position.longValue = 8000 }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnUiThread { position.longValue = 1500 }
        compose.mainClock.advanceTimeByFrame()
        val back = compose.onNodeWithTag("lyric_line_0").fetchSemanticsNode().boundsInRoot
        assertEquals((panel.top + panel.bottom) / 2, (back.top + back.bottom) / 2, 1f)
        compose.runOnUiThread { position.longValue = 4000 }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnUiThread { position.longValue = 8000 }
        compose.mainClock.advanceTimeBy(800)
        val finalBounds = compose.onNodeWithTag("lyric_line_2").fetchSemanticsNode().boundsInRoot
        assertEquals("Rapid promotions settle at the current line", panel.center.y, finalBounds.center.y, 1f)
        compose.onNodeWithTag("smoke_next_line").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }
    @Test fun recordAuxiliaryTransitions() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == 1 }
        val position = mutableLongStateOf(0)
        val auxiliary = mutableStateOf(true)
        val smoke = mutableStateOf(false)
        val document = LyricsDocument((0..16).map { i -> LyricLine(i * 2200L, (i + 1) * 2200L,
            listOf("星の光を追いかけて", "君と歩いた帰り道", "夜空に響くこのメロディー")[i % 3],
            translation = listOf("追逐着繁星的光芒", "与你走过的归途，仍然记得那些温柔的时光", "在夜空中回响的旋律")[i % 3],
            romanization = listOf("Hoshi no hikari o oikakete", "Kimi to aruita kaerimichi", "Yozora ni hibiku kono merodii")[i % 3]) })
        val samples = Collections.synchronizedList(mutableListOf<String>())
        val thread = HandlerThread("lyrics-frame-probe").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            samples += "${if (smoke.value) "smoke" else "list"},${metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1000000.0},$dropped"
        }
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val name = InstrumentationRegistry.getArguments().getString("captureName") ?: "lyrics-presentation"
        val frames = File(context.externalCacheDir, name).apply { mkdirs() }
        compose.runOnUiThread { compose.activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
        try {
            compose.setContent {
                Column(Modifier.fillMaxSize().background(Color(0xff111722)).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
                    LyricsScreen(document, position, {}, Modifier.weight(1f).fillMaxWidth(),
                        translation = auxiliary.value, romanization = auxiliary.value, effects = false,
                        display = LyricsDisplayOptions(centered = true, perspective = false, smoke = smoke.value))
                    PlayerSeekBar(.4f, true, {}, {}, {})
                    Spacer(Modifier.height(72.dp))
                }
            }
            compose.mainClock.autoAdvance = false
            repeat(400) { frame ->
                val elapsed = frame * 50L
                compose.runOnUiThread {
                    position.longValue = elapsed % 17600
                    smoke.value = elapsed >= 10000
                    auxiliary.value = elapsed % 4400 < 3000
                }
                compose.mainClock.advanceTimeBy(50)
                compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                    File(frames, "frame-%04d.png".format(frame)).outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        } finally {
            compose.runOnUiThread { compose.activity.window.removeOnFrameMetricsAvailableListener(listener) }
            thread.quitSafely(); thread.join()
            File(context.externalCacheDir, "$name.csv").writeText("mode,totalMs,dropped\n" + samples.joinToString("\n"))
            compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
}
