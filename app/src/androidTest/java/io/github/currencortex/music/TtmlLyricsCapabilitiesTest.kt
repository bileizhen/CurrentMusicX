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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.ttml.TtmlParser
import io.github.currencortex.music.feature.lyrics.ui.LyricsScreen
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Silent, namespaced fixtures. No MusicService binding, real account mutation or audio. */
class TtmlLyricsCapabilitiesTest {
    @get:Rule val compose = createComposeRule()
    private val raw = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:amll="http://www.example.com/ns/amll"><head><metadata><ttm:agent xml:id="v1" type="person"/><ttm:agent xml:id="v2" type="person"/><amll:meta key="ncmMusicId" value="123"/></metadata></head><body dur="14"><div>
      <p begin="1" end="4" ttm:agent="v1"><span begin="1" end="2">循着</span><span begin="2" end="4">星光</span><span ttm:role="x-translation">Follow the stars</span><span ttm:role="x-roman">Xun zhe xing guang</span><span ttm:role="x-bg"><span begin="2.2" end="3">微风</span><span begin="3" end="3.8">作伴</span><span ttm:role="x-translation">With the breeze</span></span></p>
      <p begin="2" end="5" ttm:agent="v2"><span begin="2" end="3">向着</span><span begin="3" end="5">远方</span><span ttm:role="x-translation">Toward the horizon</span></p>
      <p begin="9" end="12" ttm:agent="v1"><span begin="9" end="10">让音乐</span><span begin="10" end="12">陪着你</span><span ttm:role="x-translation">Let music stay with you</span></p>
    </div></body></tt>"""

    @Test fun nativeWordsDuetBackgroundInterludeAndOffsetSeekWork() {
        val position = mutableLongStateOf(2300)
        val canSeek = mutableStateOf(true)
        var seek = -1L
        val document = TtmlParser.parse(raw)
        compose.setContent { LyricsScreen(document, position, { seek = it }, Modifier.fillMaxSize().background(Color(0xFF29272C)),
            canSeek = canSeek.value, romanization = true, effects = false, lyricsOffsetMs = 200) }
        compose.onNodeWithTag("lyric_line_0").assertIsSelected()
        compose.onNodeWithTag("lyric_line_1").assertIsSelected()
        compose.onNodeWithTag("lyric_bg_0_0").assertIsSelected()
        val duetLayout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("向着远方", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(duetLayout) }
        assertTrue("Duet glyphs must align on the right, like their translation", duetLayout.single().getBoundingBox(0).left > duetLayout.single().size.width * .3f)
        val translated = compose.onNodeWithText("Follow the stars", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val roman = compose.onNodeWithText("Xun zhe xing guang", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Auxiliary rows must display translation before romanization", translated.bottom <= roman.top)
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        fun brightGlyphPixels(): Int {
            val pixels = compose.onNodeWithText("循着星光", useUnmergedTree = true).captureToImage().toPixelMap()
            var count = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (color.red > .85f && color.green > .85f && color.blue > .85f) count++
            }
            return count
        }
        val quarter = brightGlyphPixels()
        compose.runOnIdle { position.longValue = 3300 }
        compose.waitForIdle()
        assertTrue("The current word must fill progressively, rather than highlight a whole line", brightGlyphPixels() > quarter * 1.1)
        compose.onNodeWithTag("lyrics_panel").captureToImage().asAndroidBitmap().let { bitmap ->
            val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
            java.io.File(context.externalCacheDir, "ttml-duet-background.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithTag("lyric_bg_0_0").performClick()
        assertEquals(2000L, seek) // 2200ms TTML start minus the 200ms display offset.
        compose.runOnIdle { position.longValue = 6000; canSeek.value = false }
        compose.onNodeWithTag("lyrics_interlude").assertExists()
        compose.onNodeWithTag("lyric_line_1").assertIsNotSelected().assertIsNotEnabled()
        compose.runOnIdle { position.longValue = 9100 }
        compose.onNodeWithTag("lyrics_interlude").assertDoesNotExist()
        compose.onNodeWithTag("lyric_line_2").assertIsSelected().assertIsDisplayed()
    }

    @Test fun playerLoadsTtmlAndSwitchesToOriginalLyricsWhenEntryIsMissing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val server = MockWebServer()
        val ttmlCalls = AtomicInteger()
        val originalCalls = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                "/amll/ncm-lyrics/123.ttml" -> { ttmlCalls.incrementAndGet(); MockResponse().setBody(raw) }
                "/amll/ncm-lyrics/124.ttml" -> { ttmlCalls.incrementAndGet(); MockResponse().setResponseCode(404) }
                "/cm/ncm/lyric" -> { originalCalls.incrementAndGet(); MockResponse().setHeader("Content-Type", "application/json").setBody("""{"lines":[{"t":0,"txt":"原有歌词回退"},{"t":5000,"txt":"继续听音乐"}]}""") }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val container = AppContainer(context, "ttml-player-${UUID.randomUUID()}", ttmlProvider = AmllLyricsProvider(GithubTtmlDataSource(server.url("/amll/").toString())))
        try {
            container.ready.await(); container.sessionRestored.await()
            val url = server.url("/cm/").toString()
            container.musicSettings.setServer(url); container.accountRepository.server = url
            container.accountRepository.save("isolated-ttml-token", UserDto(7, nickname = "TTML fixture"))
            container.updateSettings.setAutoCheck(false)
            container.settings.edit { it.copy(blur = false) }
            val song = Song(123, "星光与远方", artists = "CurrentMusic · TTML 预览", durationMs = 14000)
            container.playerController.queue.replace(listOf(song), 0)
            container.playerController.state.value = PlayerState(song = song, positionMs = 2500, durationMs = 14000)
            compose.setContent { CurrentMusicApp(container) }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("mini_cover").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("mini_cover").performClick()
            compose.onNodeWithTag("open_lyrics").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("循着星光", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("lyric_bg_0_0").assertExists()
            assertEquals(1, ttmlCalls.get()); assertEquals(0, originalCalls.get())
            compose.onNodeWithTag("player_screen").captureToImage().asAndroidBitmap().let { bitmap ->
                java.io.File(context.externalCacheDir, "ttml-player-preview.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            val next = song.copy(id = 124, name = "回退预览")
            compose.runOnIdle {
                container.playerController.queue.replace(listOf(next), 0)
                container.playerController.state.value = PlayerState(song = next, positionMs = 1000, durationMs = 14000)
            }
            compose.waitUntil(10000) { compose.onAllNodesWithText("原有歌词回退", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("lyric_bg_0_0").assertDoesNotExist()
            assertEquals(2, ttmlCalls.get()); assertEquals(1, originalCalls.get())
            assertFalse(container.playerController.state.value.playing)
            assertEquals(124L, container.playerController.queue.state.value.current?.id)
        } finally { container.close(); server.shutdown() }
    }
}
