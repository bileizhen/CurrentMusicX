package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.ttml.TtmlParser
import io.github.currencortex.music.feature.lyrics.ui.LyricsScreen
import io.github.currencortex.music.feature.lyrics.ui.LyricsPerspectiveAngle
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Paused mock tracks and isolated preferences; never bind the real audio service. */
class LyricsDisplayCapabilitiesTest {
    @get:Rule val compose = createComposeRule()
    private val raw = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:amll="http://www.example.com/ns/amll"><head><metadata><amll:meta key="ncmMusicId" value="123"/></metadata></head><body dur="15"><div>
      <p begin="1" end="3"><span begin="1" end="2">循着</span><span begin="2" end="3">星光</span></p>
      <p begin="3" end="6"><span begin="3" end="4">让音乐</span><span begin="4" end="6">陪着你</span></p>
      <p begin="7" end="10"><span begin="7" end="8">向着</span><span begin="8" end="10">远方</span></p>
      <p begin="11" end="14"><span begin="11" end="12">明天</span><span begin="12" end="14">有光</span></p>
    </div></body></tt>"""

    private fun save(tag: String, name: String) {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(context.externalCacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun blurKeepsOnlyCurrentlySungVocalsClear() {
        val document = LyricsDocument(listOf(
            LyricLine(1000, 2000, "当前主唱清晰", backgroundVocals = listOf(LyricLine(2000, 3500, "背景人声清晰", isBackground = true))),
            LyricLine(4000, 6500, "下一句也要模糊"),
            LyricLine(7000, 10000, "远处歌词模糊"),
        ))
        val position = mutableLongStateOf(1500)
        val effects = mutableStateOf(false)
        compose.setContent { LyricsScreen(document, position, {}, Modifier.fillMaxSize().background(Color(0xFF29272C)),
            effects = effects.value, display = LyricsDisplayOptions(stagger = false)) }
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        fun edgeEnergy(text: String): Double {
            val pixels = compose.onNodeWithText(text, useUnmergedTree = true).captureToImage().toPixelMap()
            var energy = 0.0
            for (y in 0 until pixels.height) for (x in 1 until pixels.width)
                energy += kotlin.math.abs(pixels[x, y].red - pixels[x - 1, y].red)
            return energy
        }
        val clearMain = edgeEnergy("当前主唱清晰")
        val clearNext = edgeEnergy("下一句也要模糊")
        val clearBg = edgeEnergy("背景人声清晰")
        compose.runOnIdle { effects.value = true }
        compose.waitForIdle()
        assertEquals("Current vocals must remain sharp", clearMain, edgeEnergy("当前主唱清晰"), .01)
        assertTrue("Even the immediate next row must be blurred", edgeEnergy("下一句也要模糊") < clearNext * .8)
        assertTrue("Inactive background vocals must be blurred too", edgeEnergy("背景人声清晰") < clearBg * .8)
        compose.runOnIdle { position.longValue = 2500; effects.value = false }
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        val activeBg = edgeEnergy("背景人声清晰")
        val endedMain = edgeEnergy("当前主唱清晰")
        compose.runOnIdle { effects.value = true }
        compose.waitForIdle()
        assertEquals("Active background vocals cannot inherit main-vocal blur", activeBg, edgeEnergy("背景人声清晰"), .01)
        assertTrue("Ended main vocals are blurred while their background vocals sing", edgeEnergy("当前主唱清晰") < endedMain * .8)
        compose.runOnIdle { position.longValue = 4500 }
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        compose.onNodeWithTag("lyric_line_1").assertIsSelected()
        save("lyrics_panel", "lyrics-blur-preview.png")
        val activeNext = edgeEnergy("下一句也要模糊")
        compose.runOnIdle { effects.value = false }
        compose.waitForIdle()
        assertEquals("Focus changes must remove blur from the new current row", activeNext, edgeEnergy("下一句也要模糊"), .01)
    }

    @Test fun centeredWeightAndAllLineKaraokeRenderActualGlyphs() {
        val document = TtmlParser.parse(raw)
        val position = mutableLongStateOf(1500)
        val display = mutableStateOf(LyricsDisplayOptions(centered = true, fontWeight = 700, stagger = false, karaokeScope = KaraokeScope.CURRENT))
        compose.setContent { LyricsScreen(document, position, {}, Modifier.fillMaxSize().background(Color(0xFF29272C)),
            effects = false, display = display.value) }
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("循着星光", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals(TextAlign.Center, layout.layoutInput.style.textAlign)
        assertEquals(FontWeight(700), layout.layoutInput.style.fontWeight)
        assertEquals(FontSynthesis.Weight, layout.layoutInput.style.fontSynthesis)
        val left = layout.getBoundingBox(0).left
        val right = layout.size.width - layout.getBoundingBox(3).right
        assertTrue("Glyphs must be centered inside the measured viewport", kotlin.math.abs(left - right) < 3)
        fun futureBrightness(): Double {
            val pixels = compose.onNodeWithText("让音乐陪着你", useUnmergedTree = true).captureToImage().toPixelMap()
            var total = 0.0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) total += (pixels[x, y].red - .17f).coerceAtLeast(0f)
            return total
        }
        val currentOnly = futureBrightness()
        compose.runOnIdle { display.value = display.value.copy(karaokeScope = KaraokeScope.ALL) }
        compose.waitForIdle()
        assertTrue("All-line compatibility must leave unsung words visibly dim", futureBrightness() < currentOnly * .65)
        compose.runOnIdle { position.longValue = 4500; display.value = display.value.copy(stagger = true) }
        compose.mainClock.advanceTimeBy(1500); compose.waitForIdle()
        compose.onNodeWithTag("lyric_line_1").assertIsSelected().assertIsDisplayed()
        save("lyrics_panel", "lyrics-centered-preview.png")
    }

    @Test fun alwaysSweepsPlainLinesWithoutReplacingRealWordTiming() {
        val plain = LyricLine(1000, 3000, "普通歌词近似扫亮")
        val timed = LyricLine(4000, 6000, "真实逐字时间", words = listOf(
            LyricWord("真实", 4000, 5800, 0, 2), LyricWord("逐字时间", 5800, 6000, 2, 6)))
        val document = LyricsDocument(listOf(plain, timed))
        val position = mutableLongStateOf(1500)
        val strategy = mutableStateOf(KaraokeScope.CURRENT)
        val enabled = mutableStateOf(true)
        compose.setContent { LyricsScreen(document, position, {}, Modifier.fillMaxSize().background(Color.Black),
            effects = false, wordAnimation = enabled.value, weightMode = LyricsWeight.NORMAL,
            display = LyricsDisplayOptions(stagger = false, karaokeScope = strategy.value)) }
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        fun brightness(text: String): Double {
            val pixels = compose.onNodeWithText(text, useUnmergedTree = true).captureToImage().toPixelMap()
            var total = 0.0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) total += pixels[x, y].red
            return total
        }
        val wholeLine = brightness(plain.text)
        compose.runOnIdle { strategy.value = KaraokeScope.ALL }
        compose.waitForIdle()
        assertEquals("All-line mode retains plain line display", wholeLine, brightness(plain.text), .01)
        compose.runOnIdle { strategy.value = KaraokeScope.ALWAYS }
        compose.waitForIdle()
        val early = brightness(plain.text)
        assertTrue("Always mode displays a partial sweep on an untimed line", early < wholeLine * .8)
        compose.runOnIdle { position.longValue = 2500 }
        compose.waitForIdle()
        val late = brightness(plain.text)
        assertTrue("Approximate highlighting advances with line time", late > early * 1.1)
        compose.runOnIdle { enabled.value = false }
        compose.waitForIdle()
        val disabled = brightness(plain.text)
        assertTrue("Disabling word animation restores full line brightness", disabled > late)
        compose.runOnIdle { strategy.value = KaraokeScope.CURRENT }
        compose.waitForIdle()
        assertEquals("The disabled fallback matches plain text at the same playback position", disabled, brightness(plain.text), .01)
        compose.runOnIdle { enabled.value = true; position.longValue = 4500; strategy.value = KaraokeScope.ALL }
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        val realTiming = brightness(timed.text)
        compose.runOnIdle { strategy.value = KaraokeScope.ALWAYS }
        compose.waitForIdle()
        assertEquals("Always mode must retain the original word timing", realTiming, brightness(timed.text), .01)
        assertTrue("The source document is never rewritten", plain.words.isEmpty())
    }

    @Test fun miniLyricsFollowPositionAndSettingsCanHideAndRestoreControls() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = when (request.requestUrl!!.encodedPath) {
                "/amll/ncm-lyrics/123.ttml" -> MockResponse().setBody(raw)
                "/amll/ncm-lyrics/124.ttml" -> MockResponse().setBody(raw.replace("value=\"123\"", "value=\"124\"").replace("循着", "新的"))
                    .setBodyDelay(800, TimeUnit.MILLISECONDS)
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val container = AppContainer(context, "lyrics-display-${UUID.randomUUID()}", ttmlProvider = AmllLyricsProvider(GithubTtmlDataSource(server.url("/amll/").toString())))
        try {
            container.ready.await(); container.sessionRestored.await()
            val url = server.url("/cm/").toString()
            container.musicSettings.setServer(url); container.accountRepository.server = url
            container.accountRepository.save("isolated-display-token", UserDto(7, nickname = "Display fixture"))
            container.updateSettings.setAutoCheck(false)
            container.settings.edit { it.copy(themeMode = ThemeMode.LIGHT) }
            val song = Song(123, "星光与远方", artists = "歌词显示预览", durationMs = 15000)
            container.playerController.queue.replace(listOf(song), 0)
            container.playerController.state.value = PlayerState(song = song, positionMs = 1500, durationMs = 15000)
            lateinit var activity: androidx.activity.ComponentActivity
            compose.setContent {
                activity = androidx.activity.compose.LocalActivity.current as androidx.activity.ComponentActivity
                CurrentMusicApp(container)
            }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("mini_lyric", useUnmergedTree = true).fetchSemanticsNodes().any { it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].any { text -> text.text == "循着星光" } } }
            compose.onNodeWithTag("mini_title", useUnmergedTree = true).assertTextEquals("星光与远方")
            save("mini_player", "mini-lyrics-preview.png")
            compose.runOnIdle { container.playerController.state.value = container.playerController.state.value.copy(positionMs = 4500) }
            compose.onNodeWithTag("mini_lyric", useUnmergedTree = true).assertTextEquals("让音乐陪着你")
            compose.runOnIdle { container.playerController.state.value = container.playerController.state.value.copy(positionMs = 6500) }
            compose.onNodeWithTag("mini_lyric", useUnmergedTree = true).assertTextEquals("歌词显示预览") // Real end-time gap, not a stale lyric.
            compose.runOnIdle { container.playerController.state.value = container.playerController.state.value.copy(positionMs = 4500) }
            compose.onNodeWithTag("mini_cover").performClick()
            compose.onNodeWithTag("lyrics_options").performClick()
            compose.onNodeWithTag("open_lyrics").performClick()
            assertEquals("The portrait player must enable the actual perspective renderer", -32f,
                compose.onNodeWithTag("lyrics_list").fetchSemanticsNode().config[LyricsPerspectiveAngle], .01f)
            compose.onNodeWithTag("lyrics_options").performClick()
            compose.onNodeWithTag("open_lyrics_display").performScrollTo().performClick()
            compose.onNodeWithTag("lyrics_perspective").performScrollTo().performClick()
            compose.waitUntil(5000) { !container.musicSettings.state.value.lyricsDisplay.perspective }
            compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
            assertEquals("Turning the option off restores flat portrait lyrics", 0f,
                compose.onNodeWithTag("lyrics_list").fetchSemanticsNode().config[LyricsPerspectiveAngle], .01f)
            compose.onNodeWithTag("lyrics_options").performClick()
            compose.onNodeWithTag("open_lyrics_display").performScrollTo().performClick()
            compose.onNodeWithTag("lyrics_perspective").performScrollTo().performClick()
            compose.waitUntil(5000) { container.musicSettings.state.value.lyricsDisplay.perspective }
            compose.onNodeWithTag("lyrics_centered").performScrollTo().performClick()
            compose.onNodeWithTag("lyrics_font_strength").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(600f) }
            compose.onNodeWithTag("lyrics_stagger").performScrollTo().performClick()
            compose.onNodeWithTag("lyrics_blur").performScrollTo().performClick()
            compose.onNodeWithTag("lyrics_glow").performScrollTo().performClick()
            compose.onNodeWithTag("lyrics_word_lift").performScrollTo().performClick()
            compose.onNodeWithTag("open_karaoke_scope").performScrollTo().performClick()
            compose.onNodeWithTag("karaoke_scope_CURRENT").assertIsDisplayed()
            compose.onNodeWithTag("karaoke_scope_ALL").assertIsDisplayed()
            compose.onNodeWithTag("karaoke_scope_ALWAYS").assertIsDisplayed().performClick()
            compose.waitUntil(5000) { container.musicSettings.state.value.lyricsDisplay.karaokeScope == KaraokeScope.ALWAYS }
            compose.onNodeWithTag("open_karaoke_scope").performScrollTo().performClick()
            compose.onNodeWithTag("karaoke_scope_CURRENT").performClick()
            compose.onNodeWithTag("lyrics_hide_controls").performScrollTo().performClick()
            compose.waitUntil(5000) {
                val d = container.musicSettings.state.value.lyricsDisplay
                d.centered && d.fontWeight == 600 && !d.stagger && !d.blur && !d.glow && !d.wordLift &&
                    d.karaokeScope == KaraokeScope.CURRENT && d.hideControls
            }
            compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
            compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("player_transport").assertDoesNotExist()
            compose.onNodeWithTag("lyrics_reveal_controls").assertIsDisplayed().performClick()
            compose.onNodeWithTag("player_transport").assertIsDisplayed()
            save("player_screen", "lyrics-display-player-preview.png")
            compose.onNodeWithTag("lyrics_options").performClick()
            compose.onNodeWithTag("lyrics_conceal_controls").performClick()
            compose.onNodeWithTag("player_transport").assertDoesNotExist()
            compose.onNodeWithTag("navigate_back").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("mini_lyric", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            val next = song.copy(id = 124, name = "新歌")
            compose.runOnIdle {
                container.playerController.queue.replace(listOf(next), 0)
                container.playerController.state.value = PlayerState(song = next, positionMs = 1500, durationMs = 15000)
            }
            compose.onNodeWithTag("mini_lyric", useUnmergedTree = true).assertTextEquals("歌词显示预览")
            compose.waitUntil(10000) { compose.onAllNodesWithText("新的星光").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("mini_lyric", useUnmergedTree = true).assertTextEquals("新的星光")
            assertFalse(container.playerController.state.value.playing)
            assertTrue(container.musicSettings.snapshot().lyricsDisplay.hideControls)
        } finally { container.close(); server.shutdown() }
    }
}
