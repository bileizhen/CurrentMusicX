package io.github.currencortex.music

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.text.BasicText
import androidx.test.core.app.ApplicationProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
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

    @Test fun appDefaultsToFpsAllowsLyricsAndResetsWhenReturningFromDesktop() {
        val inApp = mutableStateOf(true)
        val lyrics = MutableStateFlow(PlaybackLyricsState(LyricsDocument(listOf(
            LyricLine(0, 100000, "前台与桌面切换"))), songId = 42))
        val player = MutableStateFlow(PlayerState(song = Song(42, "Music"), positionMs = 1000))
        val options = MutableStateFlow(DesktopLyricsSettings())
        compose.setContent { Box(Modifier.width(340.dp)) {
            DesktopLyricsSurface(lyrics, player, options, {}, {}, {}, defaultFpsMode = inApp.value)
        } }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(2600)
        compose.onNodeWithTag("desktop_island_fps").assertIsDisplayed()
        compose.onNodeWithTag("desktop_island").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("desktop_island_fps").assertDoesNotExist()
        compose.onNodeWithText("前台与桌面切换").assertExists()
        compose.runOnIdle { inApp.value = false }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("desktop_island_fps").assertDoesNotExist()
        compose.runOnIdle { inApp.value = true }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("desktop_island_fps").assertIsDisplayed()
    }

    @Test fun coverTapsOnlyTogglePlaybackAndCoverSwipesStillChangeTracks() {
        val lyrics = MutableStateFlow(PlaybackLyricsState(LyricsDocument(listOf(
            LyricLine(0, 100000, "点击封面播放或暂停", translation = "Cover playback controls"))), songId = 42))
        val player = MutableStateFlow(PlayerState(song = Song(42, "Music"), positionMs = 1000))
        val options = MutableStateFlow(DesktopLyricsSettings())
        var toggles = 0; var swipes = 0; var fps = false
        compose.setContent { Box(Modifier.width(340.dp)) {
            DesktopLyricsSurface(lyrics, player, options, {}, {}, {},
                onIslandModeChanged = { fps = it }, changeSong = { swipes++ },
                togglePlayback = { toggles++; player.value = player.value.copy(playing = !player.value.playing) })
        } }
        compose.mainClock.advanceTimeBy(2600)
        // Playback deliberately requests frames continuously; drive this test's clock explicitly.
        compose.mainClock.autoAdvance = false
        fun coverTap() = compose.onNodeWithTag("desktop_island_cover").performTouchInput { click() }
        coverTap(); compose.runOnIdle { assertEquals(1, toggles); assertTrue(player.value.playing); assertFalse(fps) }
        coverTap(); compose.runOnIdle { assertEquals(2, toggles); assertFalse(player.value.playing); assertFalse(fps) }
        compose.onNodeWithTag("desktop_island").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertTrue(fps); options.value = options.value.copy(locked = true) }
        coverTap(); compose.runOnIdle { assertEquals(3, toggles); assertTrue(fps) }
        compose.onNodeWithTag("desktop_island_cover").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(200f, 0f))
        }
        compose.runOnIdle { assertEquals(1, swipes); assertEquals(3, toggles); assertTrue(fps) }
        compose.runOnIdle { player.value = player.value.copy(canControlPlayback = false) }
        coverTap(); compose.runOnIdle { assertEquals(3, toggles); assertTrue(fps) }
        compose.runOnUiThread { player.value = player.value.copy(playing = false) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.autoAdvance = true
    }

    @Test fun bilingualCoverShrinksContinuouslyAlongAnArcWhenTheNextHeadingReplacesIt() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        fun cover(name: String, color: Int): String {
            val file = java.io.File(context.cacheDir, name)
            val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            return android.net.Uri.fromFile(file).toString()
        }
        val first = Song(1, "Bilingual", cover = cover("arc-first.png", android.graphics.Color.RED))
        val second = Song(2, "Heading", cover = cover("arc-second.png", android.graphics.Color.BLUE))
        val song = mutableStateOf(first)
        val reveal = mutableFloatStateOf(1f)
        compose.setContent {
            Box(Modifier.width(340.dp).height(100.dp).background(Color.White).testTag("cover_arc_pixels"),
                contentAlignment = Alignment.TopCenter) {
                DesktopIslandFrame(song.value, Modifier.width(300.dp).padding(vertical = desktopIslandDrawingSpace),
                    presentation = reveal.floatValue, motion = true) { Box(Modifier.height(64.dp)) }
            }
        }
        fun diameter() = compose.onNodeWithTag("desktop_island_cover").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        fun pixels() = compose.onNodeWithTag("cover_arc_pixels").captureToImage().toPixelMap()
        fun center(red: Boolean): Pair<Float, Float>? {
            val p = pixels(); var sx = 0f; var sy = 0f; var count = 0
            for (y in 0 until p.height) for (x in 0 until p.width) {
                val c = p[x, y]
                if (c.green < .15f && if (red) c.red > .8f && c.blue < .15f else c.blue > .8f && c.red < .15f) {
                    sx += x; sy += y; count++
                }
            }
            return if (count > 40) sx / count to sy / count else null
        }
        fun image(name: String) {
            val bitmap = compose.onNodeWithTag("cover_arc_pixels").captureToImage().asAndroidBitmap()
            java.io.File(context.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.waitUntil(5000) { center(true) != null }
        val large = diameter(); val origin = center(true)!!
        image("island-arc-start.png")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { song.value = second; reveal.floatValue = 0f }
        compose.mainClock.advanceTimeByFrame()
        assertEquals("The first new-song frame retains the large cover", large, diameter(), .5f)
        var previous = diameter()
        var intermediateDiameter = 0f
        var intermediateCenter: Pair<Float, Float>? = null
        val sizes = mutableSetOf<Float>()
        repeat(34) { frame ->
            compose.mainClock.advanceTimeByFrame()
            val current = diameter(); sizes += current
            assertTrue("There must be no large-to-small diameter step", kotlin.math.abs(current - previous) < 4.5f)
            previous = current
            if (frame == 5) {
                intermediateDiameter = current; intermediateCenter = center(true)
                image("island-arc-outgoing.png")
            }
            if (frame == 15) image("island-arc-incoming.png")
        }
        compose.waitUntil(5000) { compose.mainClock.advanceTimeByFrame(); center(false) != null }
        val small = diameter(); val destination = center(false)!!
        image("island-arc-end.png")
        assertTrue("Resizing contains many distinct rendered sizes", sizes.size >= 12)
        assertTrue("The heading has a smaller circular cover", small < large - 15f)
        val at = intermediateCenter ?: error("The outgoing face must still be visible along the arc")
        val p = (large - intermediateDiameter) / (large - small)
        val directY = origin.second * (1f - p) + destination.second * p
        assertTrue("Rendered ink rises above the straight endpoint path", at.second < directY - 3f)
        // Retarget after another size change, before the first flight finishes.
        compose.runOnIdle { song.value = first; reveal.floatValue = 1f }
        compose.mainClock.advanceTimeBy(128)
        val interrupted = diameter()
        compose.runOnIdle { song.value = second; reveal.floatValue = 0f }
        compose.mainClock.advanceTimeByFrame()
        assertEquals("An interrupted flight starts from its current diameter", interrupted, diameter(), .5f)
        compose.mainClock.advanceTimeBy(700)
        assertEquals(small, diameter(), .5f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun circularCoverFlipsThroughAnEdgeAndRapidChangesFinishOnTheLatestUnmirroredCover() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        fun cover(name: String, left: Int, right: Int): String {
            val file = java.io.File(context.cacheDir, name)
            val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
            for (y in 0 until 64) for (x in 0 until 64) bitmap.setPixel(x, y, if (x < 32) left else right)
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            return android.net.Uri.fromFile(file).toString()
        }
        val first = Song(1, "First", cover = cover("flip-first.png", android.graphics.Color.RED, android.graphics.Color.RED))
        val second = Song(2, "Second", cover = cover("flip-second.png", android.graphics.Color.BLUE, android.graphics.Color.GREEN))
        val third = Song(3, "Third", cover = cover("flip-third.png", android.graphics.Color.YELLOW, android.graphics.Color.MAGENTA))
        val song = mutableStateOf(first)
        val motion = mutableStateOf(true)
        compose.setContent {
            Box(Modifier.width(48.dp).height(48.dp).background(Color.White).testTag("cover_flip_pixels")) {
                DesktopIslandCover(song.value, null, motion.value)
            }
        }
        fun pixels() = compose.onNodeWithTag("cover_flip_pixels").captureToImage().toPixelMap()
        fun image(name: String) {
            val bitmap = compose.onNodeWithTag("cover_flip_pixels").captureToImage().asAndroidBitmap()
            java.io.File(context.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        fun redCount(): Int {
            val p = pixels(); var count = 0
            for (y in 0 until p.height) for (x in 0 until p.width) if (p[x, y].red > .8f && p[x, y].green < .2f) count++
            return count
        }
        compose.waitUntil(5000) { redCount() > 200 }
        val full = redCount()
        image("island-flip-front.png")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { song.value = second }
        compose.mainClock.advanceTimeBy(128)
        assertTrue("The old face narrows during a Y flip", redCount() in 1 until (full * .85f).toInt())
        image("island-flip-outgoing.png")
        compose.mainClock.advanceTimeBy(160)
        compose.waitUntil(5000) { val p = pixels(); p[p.width / 2 - 2, p.height / 2].blue > .7f }
        image("island-flip-incoming.png")
        compose.mainClock.advanceTimeBy(240)
        val settled = pixels()
        image("island-flip-settled.png")
        assertTrue("The incoming cover is not mirrored", settled[settled.width / 4, settled.height / 2].blue > .8f &&
            settled[settled.width * 3 / 4, settled.height / 2].green > .8f)
        assertTrue("The face remains circular, with clear corners", settled[0, 0].red > .95f && settled[0, 0].green > .95f)
        compose.runOnIdle { song.value = first }
        compose.mainClock.advanceTimeBy(280) // Interrupt the incoming half.
        compose.runOnIdle { song.value = third }
        compose.mainClock.advanceTimeBy(500)
        compose.waitUntil(5000) { val p = pixels(); p[p.width * 3 / 4, p.height / 2].blue > .8f }
        val latest = pixels()
        assertTrue("Rapid switching settles on the newest circular face", latest[latest.width / 4, latest.height / 2].green > .8f &&
            latest[latest.width * 3 / 4, latest.height / 2].green < .2f)
        compose.runOnIdle { motion.value = false; song.value = second }
        compose.mainClock.advanceTimeByFrame()
        compose.waitUntil(5000) { compose.mainClock.advanceTimeByFrame(); val p = pixels(); p[p.width / 4, p.height / 2].blue > .8f }
        assertTrue("Reduced motion swaps directly to a front-facing cover", pixels()[pixels().width * 3 / 4, pixels().height / 2].green > .8f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun lyricFpsSwitchCrossfadesAndChangesHeightThroughAnIntermediateFrame() {
        val fps = mutableStateOf(false)
        compose.setContent {
            Box(Modifier.width(200.dp).background(Color.White).testTag("switch_animation")) {
                DesktopIslandDisplay(fps.value, true,
                    lyrics = { Box(Modifier.fillMaxWidth().height(70.dp).background(Color.Blue).testTag("switch_lyrics")) },
                    fps = { Box(Modifier.fillMaxWidth().height(30.dp).background(Color.Red).testTag("switch_fps")) })
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { fps.value = true }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("switch_lyrics").assertExists()
        compose.onNodeWithTag("switch_fps").assertExists()
        val intermediate = compose.onNodeWithTag("switch_animation").getUnclippedBoundsInRoot()
        assertTrue("The shell height must have an intermediate value", intermediate.bottom - intermediate.top > 30.dp && intermediate.bottom - intermediate.top < 70.dp)
        val pixels = compose.onNodeWithTag("switch_animation").captureToImage().toPixelMap()
        val color = pixels[pixels.width / 2, pixels.height / 2]
        assertTrue("Both content colors contribute during the fade", color.red > .1f && color.blue > .1f)
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("switch_lyrics").assertDoesNotExist()
        val settled = compose.onNodeWithTag("switch_animation").getUnclippedBoundsInRoot()
        assertEquals(30f, (settled.bottom - settled.top).value, .5f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun tapsToggleLyricsAndFpsWithoutInterruptingThePlayerAndResetForANewSong() {
        val lyrics = MutableStateFlow(PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 10000, "可切换歌词", translation = "Translation"))), songId = 42))
        val player = MutableStateFlow(PlayerState(song = Song(42, "Music"), positionMs = 1000))
        val options = MutableStateFlow(DesktopLyricsSettings(appearance = DesktopAppearance.ISLAND))
        compose.setContent { Box(Modifier.width(340.dp)) {
            DesktopLyricsSurface(lyrics, player, options, {}, {}, {})
        } }
        compose.mainClock.advanceTimeBy(2600)
        compose.onNodeWithText("可切换歌词").assertIsDisplayed()
        val initialPlayer = player.value
        val initialOptions = options.value
        compose.onNodeWithTag("desktop_island").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("desktop_island_fps").assertIsDisplayed()
        compose.onNodeWithText("可切换歌词").assertDoesNotExist()
        compose.onNodeWithTag("desktop_window_translation").assertDoesNotExist()
        compose.onNodeWithTag("desktop_island").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("可切换歌词").assertIsDisplayed()
        compose.onNodeWithTag("desktop_window_translation").assertIsDisplayed()
        compose.runOnIdle { assertEquals(initialPlayer, player.value); assertEquals(initialOptions, options.value) }
        compose.onNodeWithTag("desktop_island").performTouchInput { click() }
        compose.runOnIdle {
            player.value = player.value.copy(song = Song(43, "Next"))
            lyrics.value = PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 10000, "新歌歌词"))), songId = 43)
        }
        compose.mainClock.advanceTimeBy(2600)
        compose.onNodeWithText("新歌歌词").assertIsDisplayed()
        compose.onNodeWithTag("desktop_island_fps").assertDoesNotExist()
        compose.runOnIdle { options.value = options.value.copy(locked = true) }
        compose.onNodeWithTag("desktop_island").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("desktop_island_fps").assertIsDisplayed()
        compose.onNodeWithTag("desktop_island").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("新歌歌词").assertIsDisplayed()
        compose.onNodeWithTag("desktop_edit_toolbar").assertDoesNotExist()
    }

    @Test fun lockedIslandSwipesChangeTracksOnceWithoutMovingOrShowingControls() {
        val player = MutableStateFlow(PlayerState(song = Song(42, "Music")))
        val directions = mutableListOf<Int>()
        val options = MutableStateFlow(DesktopLyricsSettings(locked = true, appearance = DesktopAppearance.ISLAND))
        compose.setContent { Box(Modifier.width(340.dp)) {
            DesktopLyricsSurface(MutableStateFlow(PlaybackLyricsState()), player, options, {}, {}, {}, changeSong = { directions += it })
        } }
        compose.mainClock.advanceTimeBy(2600)
        compose.onNodeWithTag("desktop_island").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("desktop_island").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(listOf(1, -1), directions) }
        compose.onNodeWithTag("desktop_edit_toolbar").assertDoesNotExist()
        compose.onNodeWithTag("desktop_island").performTouchInput { longClick() }
        compose.onNodeWithTag("desktop_edit_toolbar").assertDoesNotExist()
        compose.runOnIdle { player.value = player.value.copy(canControlPlayback = false) }
        compose.onNodeWithTag("desktop_island").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(2, directions.size) }
    }

    @Test fun noLyricsAndInstrumentalTracksStayOnFpsAcrossRepeatedTaps() {
        val lyrics = MutableStateFlow(PlaybackLyricsState(songId = 42))
        val player = MutableStateFlow(PlayerState(song = Song(42, "Instrumental")))
        val options = MutableStateFlow(DesktopLyricsSettings(appearance = DesktopAppearance.ISLAND))
        compose.setContent { Box(Modifier.width(340.dp)) {
            DesktopLyricsSurface(lyrics, player, options, {}, {}, {})
        } }
        compose.mainClock.advanceTimeBy(2600)
        repeat(2) {
            compose.onNodeWithTag("desktop_island").performTouchInput { click() }
            compose.onNodeWithTag("desktop_island_fps").assertIsDisplayed()
        }
        compose.runOnIdle { lyrics.value = PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 10000, "纯音乐，请欣赏"))), songId = 42) }
        repeat(2) {
            compose.onNodeWithTag("desktop_island").performTouchInput { click() }
            compose.onNodeWithTag("desktop_island_fps").assertIsDisplayed()
            compose.onNodeWithText("纯音乐，请欣赏").assertDoesNotExist()
        }
        compose.runOnIdle { lyrics.value = PlaybackLyricsState(LyricsDocument(listOf(LyricLine(0, 10000, "加载完成的歌词"))), songId = 42) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("加载完成的歌词").assertIsDisplayed()
    }

    @Test fun nextCueIsPreparedOnceAndPromotedWithoutRecreatingItsRenderer() {
        val first = LyricLine(0, 3000, "First")
        val second = LyricLine(3000, 6000, "Next")
        val current = mutableStateOf(false)
        val creations = mutableMapOf<String, Int>()
        compose.setContent {
            DesktopIslandCueHost(42, if (current.value) 1 else 0, if (current.value) second else first,
                if (current.value) -1 else 1, if (current.value) null else second, Unit, Modifier.width(180.dp)) { cue ->
                DisposableEffect(cue) { creations[cue.text] = (creations[cue.text] ?: 0) + 1; onDispose { } }
                BasicText(cue.text)
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertEquals(1, creations[second.text]) }
        compose.onNodeWithText(first.text).assertIsDisplayed()
        compose.onNodeWithText(second.text).assertDoesNotExist()
        compose.runOnIdle { current.value = true }
        compose.onNodeWithText(second.text).assertIsDisplayed()
        compose.onNodeWithText(first.text).assertDoesNotExist()
        compose.runOnIdle { assertEquals("Promotion must retain the prepared composition", 1, creations[second.text]) }
    }

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
                    DesktopLyricsSurface(lyrics, player, options, {}, {}, {})
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
                DesktopLyricsSurface(lyrics, player, options, {}, {}, {})
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
        compose.onNodeWithTag("desktop_island_progress").assertIsDisplayed()
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
        assertTrue("Short lyrics and translation should fit near status-bar height", short.bottom - short.top < 52.dp)
        // Actual rendered ink must be vertically centered, independent of the
        // transparent halo margins used by the primary paragraph's layout.
        val image = compose.onNodeWithTag("desktop_island").captureToImage().toPixelMap()
        val divider = compose.onNodeWithTag("desktop_island_progress").getUnclippedBoundsInRoot()
        val scale = image.width / (short.right - short.left).value
        val fromX = ((divider.right - short.left).value * scale + 2 * scale).toInt()
        var topInk = image.height; var bottomInk = -1
        for (y in 0 until image.height) for (x in fromX until (image.width - 6 * scale).toInt()) {
            val pixel = image[x, y]
            if (pixel.red > .45f && pixel.green > .45f && pixel.blue > .45f) {
                topInk = minOf(topInk, y); bottomInk = maxOf(bottomInk, y)
            }
        }
        assertTrue("The pixel check must find both lyric lines", bottomInk > topInk)
        assertTrue("Visible bilingual ink is centered inside the pill",
            kotlin.math.abs((topInk + bottomInk) / 2f - image.height / 2f) < 2f * scale)
        val bilingualCover = compose.onNodeWithTag("desktop_island_cover").getUnclippedBoundsInRoot()
        assertTrue("Bilingual cover shrinks with its compact pill", bilingualCover.bottom - bilingualCover.top < 42.dp)
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
