package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.LyricsWeight
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

/** Only mock lyrics and namespaced preferences; no audio or production queue changes. */
class LyricsWeightCapabilitiesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun weightOptionsPersistAndFollowThePlayingLine() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = AppContainer(context, "lyrics-weight-${UUID.randomUUID()}")
        val server = MockWebServer()
        try {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = if (request.path?.startsWith("/cm/ncm/lyric") == true)
                    MockResponse().setHeader("Content-Type", "application/json").setBody("""{"lines":[
                        {"t":0,"txt":"循着星光慢慢走","trans":"Walk beneath the stars"},
                        {"t":3000,"txt":"让音乐陪着你","trans":"Let music stay with you"},
                        {"t":6000,"txt":"明天依然有光","trans":"Tomorrow still shines"}]}""")
                else MockResponse().setResponseCode(404)
            }
            server.start()
            container.ready.await(); container.sessionRestored.await()
            val url = server.url("/cm/").toString()
            container.musicSettings.setServer(url); container.accountRepository.server = url
            container.accountRepository.save("isolated-weight-token", UserDto(7, nickname = "Weight fixture"))
            container.updateSettings.setAutoCheck(false)
            container.settings.edit { it.copy(themeMode = ThemeMode.LIGHT, blur = false) }
            val song = Song(828, "星光", artists = "霞鹜文楷 · 字重预览", durationMs = 9000)
            container.playerController.queue.replace(listOf(song), 0)
            container.playerController.state.value = PlayerState(song = song, positionMs = 1500, durationMs = 9000)
            lateinit var activity: androidx.activity.ComponentActivity
            compose.setContent {
                activity = androidx.activity.compose.LocalActivity.current as androidx.activity.ComponentActivity
                CurrentMusicApp(container)
            }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("mini_cover").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("mini_cover").performClick()
            compose.onNodeWithTag("open_lyrics").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("lyric_line_0").fetchSemanticsNodes().isNotEmpty() }
            fun weight(text: String): FontWeight? {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                return layouts.single().layoutInput.style.fontWeight
            }
            fun assertWeights(current: FontWeight, other: FontWeight) {
                assertEquals(current, weight("循着星光慢慢走"))
                assertEquals(other, weight("让音乐陪着你"))
                assertEquals(current, weight("Walk beneath the stars"))
                assertEquals(other, weight("Let music stay with you"))
            }
            assertWeights(FontWeight.Medium, FontWeight.Normal)
            suspend fun choose(mode: LyricsWeight) {
                compose.onNodeWithTag("lyrics_options").performClick()
                compose.onNodeWithTag("open_lyrics_display").performClick()
                compose.onNodeWithTag("open_lyrics_weight").performScrollTo().performClick()
                compose.onNodeWithTag("lyrics_weight_${mode.name}").performClick()
                compose.waitUntil(5000) { container.musicSettings.state.value.lyricsWeight == mode }
                assertEquals(mode, container.musicSettings.snapshot().lyricsWeight)
                compose.onNodeWithTag("lyrics_display_settings").assertIsDisplayed()
                compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
                // Closing the display overlay returns to the current player's lyric view.
                compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
                compose.onNodeWithTag("lyrics_display_settings").assertDoesNotExist()
            }
            choose(LyricsWeight.ALL); assertWeights(FontWeight.Medium, FontWeight.Medium)
            choose(LyricsWeight.NORMAL); assertWeights(FontWeight.Normal, FontWeight.Normal)
            choose(LyricsWeight.CURRENT); assertWeights(FontWeight.Medium, FontWeight.Normal)
            compose.runOnIdle { container.playerController.state.value = container.playerController.state.value.copy(positionMs = 3500) }
            compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
            compose.onNodeWithTag("lyric_line_1").assertIsSelected()
            assertWeights(FontWeight.Normal, FontWeight.Medium)
            compose.onNodeWithTag("player_screen").captureToImage().asAndroidBitmap().let { bitmap ->
                java.io.File(context.externalCacheDir, "lyrics-weight-preview.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            assertFalse(container.playerController.state.value.playing)
            assertEquals(828L, container.playerController.queue.state.value.current?.id)
        } finally { container.close(); server.shutdown() }
    }
}
