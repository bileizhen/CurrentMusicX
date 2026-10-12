package io.github.currencortex.music

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.foundation.text.BasicText
import androidx.test.core.app.ApplicationProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

class DesktopLyricsIslandTest {
    @get:Rule val compose = createComposeRule()

    @Test fun resizeRetargetsContinuouslyAndReturningWindowFadesIn() {
        val owner = object : androidx.lifecycle.LifecycleOwner {
            val registry = androidx.lifecycle.LifecycleRegistry(this)
            override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
        }
        compose.runOnUiThread { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.STARTED }
        val player = MutableStateFlow(PlayerState(song = Song(42, "音乐")))
        val lyrics = MutableStateFlow(PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 8000, "音乐与你"))), songId = 42))
        val options = MutableStateFlow(DesktopLyricsSettings(locked = true, appearance = DesktopAppearance.ISLAND))
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                Box(Modifier.width(340.dp).background(Color.White)) {
                    DesktopLyricsSurface(lyrics, player, options, { 200f }, { _, _ -> }, {}, {}, {}, {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(2600)
        compose.waitUntil(5000) { compose.onAllNodesWithTag("desktop_island_lyrics").fetchSemanticsNodes().any { it.config.getOrElse(DesktopIslandReveal) { 0f } == 1f } }
        compose.mainClock.autoAdvance = false
        fun bounds() = compose.onNodeWithTag("desktop_island").getUnclippedBoundsInRoot()
        fun width() = (bounds().right - bounds().left).value
        val narrow = width()
        compose.runOnIdle { lyrics.value = PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 8000, "音乐带着我们走过每一个明天"))), songId = 42) }
        compose.mainClock.advanceTimeBy(80)
        val expanding = width()
        assertTrue("Resizing has an intermediate width", expanding > narrow + 2f && expanding < 335f)
        val centered = bounds()
        assertTrue("An intermediate width remains centered", kotlin.math.abs(((centered.left + centered.right) / 2).value - 170f) < 2f)
        compose.runOnIdle { lyrics.value = PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 8000, "光"))), songId = 42) }
        compose.mainClock.advanceTimeByFrame()
        assertTrue("Retargeting does not snap to the new width", kotlin.math.abs(width() - expanding) < 25f)
        compose.mainClock.advanceTimeBy(1000)
        assertTrue("The shortened cue eventually settles narrower", width() < narrow)
        fun edgeRed(): Float {
            val image = compose.onNodeWithTag("desktop_island").captureToImage().toPixelMap()
            return image[image.width - 10, image.height / 2].red
        }
        val opaque = edgeRed()
        compose.runOnIdle { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.CREATED }
        compose.mainClock.advanceTimeByFrame()
        assertTrue("A hidden lifecycle resets the appearance", edgeRed() > .95f)
        compose.runOnIdle { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.STARTED }
        compose.mainClock.advanceTimeBy(80)
        val appearing = edgeRed()
        assertTrue("Return renders an intermediate fade", appearing > opaque + .1f && appearing < .95f)
        compose.mainClock.advanceTimeBy(500)
        assertTrue("Appearance settles to the opaque capsule", edgeRed() < .2f)
        compose.mainClock.autoAdvance = true
    }
    @Test fun narrowIslandKeepsSingleLinesAndSeeksBothMarqueesWithoutShrinking() {
        val line = LyricLine(0, 80000, "光と音楽の世界をどこまでも歩いてゆこう Hello world 👩🏽‍🚀", translation = "光与音乐一直陪着你走过这个世界直到每个明天 The music stays with you".repeat(2))
        val lyrics = MutableStateFlow(PlaybackLyricsState(LyricsDocument(listOf(line)), songId = 42))
        val player = MutableStateFlow(PlayerState(song = Song(42, "花 feat. 花譜", artists = "Guiano, 花譜"), positionMs = 1000))
        val options = MutableStateFlow(DesktopLyricsSettings(locked = true, appearance = DesktopAppearance.ISLAND))
        compose.setContent {
            Box(Modifier.width(340.dp)) {
                DesktopLyricsSurface(lyrics, player, options, { 200f }, { _, _ -> }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("花 feat. 花譜").assertIsDisplayed()
        compose.onNodeWithText("Guiano, 花譜").assertIsDisplayed()
        compose.onNodeWithText(line.text).assertDoesNotExist()
        val heading = compose.onNodeWithTag("desktop_island").getUnclippedBoundsInRoot()
        assertTrue("Metadata-only pill does not reserve a lyric-sized minimum width", heading.right - heading.left < 160.dp)
        fun awaitLyrics() {
            compose.mainClock.advanceTimeBy(2400)
            compose.waitUntil(5000) {
                compose.onAllNodesWithTag("desktop_island_lyrics").fetchSemanticsNodes().any { it.config.getOrElse(DesktopIslandReveal) { 0f } == 1f }
            }
        }
        awaitLyrics()
        compose.onNodeWithTag("desktop_island_title").assertDoesNotExist()
        compose.onNodeWithTag("desktop_island_cover").assertIsDisplayed()
        compose.onNodeWithTag("desktop_island_divider").assertIsDisplayed()
        compose.onNodeWithText(line.text).assertIsDisplayed()
        val metadata = compose.onNodeWithTag("desktop_island_metadata").getUnclippedBoundsInRoot()
        val text = compose.onNodeWithTag("desktop_island_lyrics").getUnclippedBoundsInRoot()
        assertTrue(metadata.right < text.left)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(line.text).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(14.sp, layouts.single().layoutInput.style.fontSize)
        assertEquals("Lyrics never wrap", 1, layouts.single().lineCount)
        compose.onNodeWithTag("desktop_window_translation").assertIsDisplayed()
        val subtitleLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("desktop_window_translation").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(subtitleLayouts) }
        assertEquals("Translation never wraps", 1, subtitleLayouts.single().lineCount)
        val lyricStart = compose.onNodeWithText(line.text).getUnclippedBoundsInRoot().left
        val subtitleStart = compose.onNodeWithTag("desktop_window_translation").getUnclippedBoundsInRoot().left
        compose.runOnIdle { player.value = player.value.copy(positionMs = 6000, timelineRevision = 1) }
        val lyricMoved = compose.onNodeWithText(line.text).getUnclippedBoundsInRoot().left
        val subtitleMoved = compose.onNodeWithTag("desktop_window_translation").getUnclippedBoundsInRoot().left
        assertTrue("Seeking pans original text directly", lyricMoved < lyricStart - 20.dp)
        assertTrue("Translation has its own overflow pan", subtitleMoved < subtitleStart - 20.dp)
        compose.mainClock.advanceTimeBy(1000)
        assertEquals("Pause freezes scrolling", lyricMoved, compose.onNodeWithText(line.text).getUnclippedBoundsInRoot().left)
        compose.runOnIdle { player.value = player.value.copy(positionMs = 0, timelineRevision = 2) }
        assertTrue("Back seek returns to the new cue position", compose.onNodeWithText(line.text).getUnclippedBoundsInRoot().left > lyricMoved + 20.dp)
        compose.runOnIdle { lyrics.value = PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 8000, "光と音楽", translation = "光与音乐"))), songId = 42) }
        val region = compose.onNodeWithTag("desktop_island_lyrics").getUnclippedBoundsInRoot()
        val original = compose.onNodeWithText("光と音楽").getUnclippedBoundsInRoot()
        val translated = compose.onNodeWithTag("desktop_window_translation").getUnclippedBoundsInRoot()
        val center = (region.left + region.right) / 2
        assertTrue("Short original must be centered in the lyric region", kotlin.math.abs(((original.left + original.right) / 2 - center).value) < 2f)
        assertTrue("Translation must share the original's horizontal center", kotlin.math.abs(((translated.left + translated.right) / 2 - center).value) < 2f)
        val short = compose.onNodeWithTag("desktop_island").getUnclippedBoundsInRoot()
        assertTrue("Short lyrics and translation should fit near status-bar height", short.bottom - short.top < 60.dp)
        val bilingualCover = compose.onNodeWithTag("desktop_island_cover").getUnclippedBoundsInRoot()
        compose.runOnIdle { options.value = options.value.copy(translation = false) }
        val monoShell = compose.onNodeWithTag("desktop_island").getUnclippedBoundsInRoot()
        val monoCover = compose.onNodeWithTag("desktop_island_cover").getUnclippedBoundsInRoot()
        assertTrue("Translation removal shrinks the shell", monoShell.bottom - monoShell.top < short.bottom - short.top - 5.dp)
        assertTrue("Cover follows actual shell height", monoCover.bottom - monoCover.top < bilingualCover.bottom - bilingualCover.top - 5.dp)
        assertTrue("Cover remains circular within one physical pixel", kotlin.math.abs(((monoCover.right - monoCover.left) - (monoCover.bottom - monoCover.top)).value) < .3f)
        fun assertEqualCoverInsets(cover: androidx.compose.ui.unit.DpRect, shell: androidx.compose.ui.unit.DpRect) {
            val left = cover.left - shell.left
            assertTrue("Cover left and top insets must match", kotlin.math.abs((left - (cover.top - shell.top)).value) < .6f)
            assertTrue("Cover left and bottom insets must match", kotlin.math.abs((left - (shell.bottom - cover.bottom)).value) < .6f)
        }
        assertEqualCoverInsets(bilingualCover, short)
        assertEqualCoverInsets(monoCover, monoShell)
        assertTrue("Cover has symmetric vertical inset", kotlin.math.abs(((monoCover.top + monoCover.bottom - monoShell.top - monoShell.bottom) / 2).value) < 1f)
        compose.onNodeWithTag("desktop_window_translation").assertDoesNotExist()
        compose.runOnIdle { player.value = player.value.copy(song = Song(43, "Next track", artists = "Another artist")) }
        compose.onNodeWithText("Next track", substring = true).assertIsDisplayed()
        compose.onNodeWithText(line.text).assertDoesNotExist()
        compose.onNodeWithText("光と音楽").assertDoesNotExist()
        compose.runOnIdle {
            lyrics.value = PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 8000, "纯音乐，请欣赏"))), songId = 43)
        }
        awaitLyrics()
        compose.onNodeWithTag("desktop_island_fps").assertIsDisplayed()
        compose.waitUntil(5000) { compose.onAllNodes(SemanticsMatcher("A sampled FPS value") { n -> n.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.any { it.text.matches(Regex("[0-9]+\\.[0-9] FPS")) } == true }).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("纯音乐，请欣赏").assertDoesNotExist()
        compose.onNodeWithText("♪").assertDoesNotExist()
        compose.onNodeWithTag("desktop_island").assertIsDisplayed()
        compose.runOnIdle {
            player.value = player.value.copy(positionMs = 1500, timelineRevision = 3)
            lyrics.value = PlaybackLyricsState(LyricsDocument(listOf(LyricLine(1000, 2000, "第一句"), LyricLine(8000, 12000, "下一句"))), songId = 43)
        }
        compose.onNodeWithText("第一句").assertIsDisplayed()
        compose.runOnIdle { player.value = player.value.copy(positionMs = 6000, timelineRevision = 4) }
        compose.onNodeWithText("第一句").assertIsDisplayed()
        compose.onNodeWithText("间奏").assertDoesNotExist()
        compose.onNodeWithText("下一句").assertDoesNotExist()
        compose.runOnIdle { player.value = player.value.copy(positionMs = 8500, timelineRevision = 5) }
        compose.onNodeWithText("下一句").assertIsDisplayed()
        compose.runOnIdle { player.value = player.value.copy(positionMs = 0, timelineRevision = 6) }
        compose.onNodeWithText("第一句").assertIsDisplayed()
        compose.onNodeWithText("下一句").assertDoesNotExist()
        compose.onNodeWithText("前奏").assertDoesNotExist()
        compose.runOnIdle { player.value = player.value.copy(song = null) }
        compose.onNodeWithContentDescription("应用图标").assertIsDisplayed()
        compose.onNodeWithTag("desktop_island_fps").assertIsDisplayed()
    }
    @Test fun coverDrawsRotationFromSharedPositionAndFreezesWhenPositionStops() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = java.io.File(context.cacheDir, "island-asymmetric-cover.png")
        val bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
        for (y in 0 until 48) for (x in 0 until 48) bitmap.setPixel(x, y,
            if (x < 24) android.graphics.Color.RED else android.graphics.Color.BLUE)
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val position = mutableLongStateOf(0L)
        val motion = mutableStateOf(true)
        compose.setContent {
            DesktopIslandFrame(Song(77, "Rotation", cover = android.net.Uri.fromFile(file).toString()),
                Modifier.width(200.dp), coverPosition = position, motion = motion.value) {
                BasicText("Cover rotation")
            }
        }
        fun pixels() = compose.onNodeWithTag("desktop_island_cover").captureToImage().toPixelMap()
        fun redCenter(): Pair<Float, Float>? {
            val image = pixels(); var xSum = 0f; var ySum = 0f; var n = 0
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val p = image[x, y]
                if (p.red > .7f && p.blue < .2f) { xSum += x; ySum += y; n++ }
            }
            return if (n > 50) xSum / n to ySum / n else null
        }
        compose.waitUntil(5000) { redCenter() != null }
        val start = redCenter()!!
        compose.runOnIdle { position.longValue = 5000L }
        val quarterTurn = redCenter()!!
        assertTrue("The rendered cover rotates, rather than just advancing a timer",
            kotlin.math.abs(start.first - quarterTurn.first) > 5f && kotlin.math.abs(start.second - quarterTurn.second) > 5f)
        compose.mainClock.advanceTimeBy(1000)
        assertEquals("A frozen playback position freezes the cover", quarterTurn, redCenter())
        compose.runOnIdle { motion.value = false }
        assertEquals("Reduced motion suppresses rotation", start, redCenter())
        file.delete()
    }

    @Test fun overflowViewportFadesBothEdgesWithoutMaskingTheCapsuleBackground() {
        val position = mutableLongStateOf(0L)
        compose.setContent {
            Box(Modifier.width(180.dp).height(24.dp).background(Color.Black).testTag("island_fade")) {
                DesktopIslandMarquee("A very long lyric ".repeat(20), TextStyle(fontSize = 14.sp), position, 0, false) {
                    Box(Modifier.fillMaxWidth().height(24.dp).background(Color.White))
                }
            }
        }
        val pixels = compose.onNodeWithTag("island_fade").captureToImage().toPixelMap()
        val y = pixels.height / 2
        assertTrue("Left overflow edge softly reaches transparent", pixels[0, y].red < .1f)
        assertTrue("Right overflow edge softly reaches transparent", pixels[pixels.width - 1, y].red < .1f)
        assertTrue("Center remains fully readable", pixels[pixels.width / 2, y].red > .95f)
        assertTrue("Left fade is gradual", pixels[10, y].red > pixels[0, y].red)
        assertTrue("Right fade is gradual", pixels[pixels.width - 11, y].red > pixels[pixels.width - 1, y].red)
    }

}
