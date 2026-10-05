package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import io.github.currencortex.music.feature.lyrics.ui.AppleLyrics
import io.github.currencortex.music.feature.player.PlayerScreen
import io.github.currencortex.music.feature.player.PlayerViewModel
import io.github.currencortex.music.feature.player.PlayerSeekBar
import io.github.currencortex.music.ui.CurrentMusicApp
import io.github.currencortex.music.ui.theme.LeiTheme
import io.github.currencortex.music.ui.util.viewModelFactory
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

/** Isolated storage and mock data: never plays audio or modifies the user's account or queue. */
class LyricsCapabilitiesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun activeLyricEnlargesWithoutReflowOrClippingAtLargestFont() {
        val position = mutableLongStateOf(1500L)
        val phrase = "沿着夜色慢慢走向明天"
        val document = LyricsParser.lrc("[00:01.00]$phrase\n[00:03.00]$phrase\n[00:05.00]$phrase")
        compose.setContent {
            AppleLyrics(document, position, {}, Modifier.fillMaxSize().background(Color(0xFF29272C)), effects = false, fontSize = 40f)
        }
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        fun textBounds(index: Int) = compose.onAllNodesWithText(phrase, useUnmergedTree = true)[index].fetchSemanticsNode().boundsInRoot
        fun textLayout(index: Int): androidx.compose.ui.text.TextLayoutResult {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onAllNodesWithText(phrase, useUnmergedTree = true)[index]
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single()
        }
        val panel = compose.onNodeWithTag("lyrics_panel").fetchSemanticsNode().boundsInRoot
        val originalLayout = textLayout(0)
        val rowHeight = compose.onNodeWithTag("lyric_line_0").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Current lyric should be visibly larger than adjacent lines", textBounds(0).width > textBounds(1).width * 1.15f)
        assertTrue("Enlarged text must fit inside the viewport", textBounds(0).right <= panel.right + 1f)
        compose.runOnIdle { position.longValue = 3500L }
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        compose.onNodeWithTag("lyric_line_1").assertIsSelected().assertIsDisplayed()
        assertTrue(textBounds(1).width > textBounds(0).width * 1.15f)
        assertEquals("Focus must not rewrap glyphs", originalLayout.lineCount, textLayout(0).lineCount)
        assertEquals("Focus must not resize rows", rowHeight, compose.onNodeWithTag("lyric_line_0").fetchSemanticsNode().boundsInRoot.height, 1f)
        assertTrue(textBounds(1).right <= panel.right + 1f)
        compose.onNodeWithTag("lyrics_panel").captureToImage().asAndroidBitmap().let { bitmap ->
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            java.io.File(context.externalCacheDir, "lyrics-focus-preview.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test fun timelineFollowsSeekAndBrowsingCanReturnToCurrentLine() {
        val position = mutableLongStateOf(1500L)
        var seek = -1L
        val document = LyricsParser.lrc((1..25).joinToString("\n") { "[00:${(it * 2).toString().padStart(2, '0')}.00]第 $it 句：沿着夜色慢慢向前" })
        compose.setContent { AppleLyrics(document, position, { seek = it }, Modifier.fillMaxSize().background(Color(0xFF29272C)), effects = false) }
        compose.onNodeWithTag("lyric_line_0").assertIsNotSelected()
        compose.runOnIdle { position.longValue = 4100 }
        compose.onNodeWithTag("lyric_line_1").assertIsSelected().performClick()
        assertEquals(4000L, seek)
        compose.onNodeWithTag("lyrics_list").performTouchInput { swipeUp(durationMillis = 450) }
        compose.onNodeWithTag("lyrics_follow").assertExists()
        compose.onNodeWithTag("lyrics_follow").performClick()
        compose.onNodeWithTag("lyrics_follow").assertDoesNotExist()
        compose.onNodeWithTag("lyric_line_1").assertIsDisplayed()
        compose.runOnIdle { position.longValue = 2100 }
        compose.onNodeWithTag("lyric_line_0").assertIsSelected().assertIsDisplayed()
    }

    @Test fun roomPermissionsDisableLyricSeeking() {
        val document = LyricsParser.lrc("[00:01.00]只听音乐\n[00:03.00]房主控制播放")
        var sought = false
        val position = mutableLongStateOf(1200)
        compose.setContent { AppleLyrics(document, position, { sought = true }, Modifier.fillMaxSize(), canSeek = false) }
        compose.onNodeWithTag("lyric_line_0").assertIsNotEnabled()
        assertFalse(sought)
    }

    @Test fun seekDragPreviewsContinuouslyAndCommitsOnce() {
        val progress = mutableFloatStateOf(.2f)
        var commits = 0
        var committed = 0f
        compose.setContent { Box(Modifier.fillMaxSize()) {
            PlayerSeekBar(progress.floatValue, true, { progress.floatValue = it }, { commits++; committed = it }, {})
        } }
        compose.onNodeWithTag("player_seek").performTouchInput {
            swipe(start = androidx.compose.ui.geometry.Offset(width * .2f, height / 2f),
                end = androidx.compose.ui.geometry.Offset(width * .8f, height / 2f), durationMillis = 500)
        }
        assertEquals(1, commits)
        assertEquals(.8f, committed, .04f)
    }

    @Test fun playerKeepsTransportAndQueueInLyricsModeAndRendersWrappedWords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = AppContainer(context, "lyrics-test-${UUID.randomUUID()}")
        val server = MockWebServer()
        try {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = if (request.path?.startsWith("/cm/ncm/lyric") == true)
                    MockResponse().setHeader("Content-Type", "application/json").setBody("""{"lines":[
                      {"t":0,"end":8000,"txt":"灯火渐渐远去","trans":"The lights fade into the distance"},
                      {"t":8000,"end":16000,"txt":"我们沿着夜色 慢慢走向明天","trans":"We walk through the night, toward tomorrow","roma":"Wo men yan zhe ye se","words":[{"text":"我们","start":8000,"end":10000},{"text":"沿着夜色 ","start":10000,"end":14000},{"text":"慢慢走向明天","start":14000,"end":16000}]},
                      {"t":16000,"end":24000,"txt":"And the world keeps turning","trans":"世界依然转动"},
                      {"t":24000,"txt":"风会记得我们的声音"}]}""")
                else MockResponse().setResponseCode(404)
            }
            server.start()
            container.ready.await(); container.sessionRestored.await()
            val url = server.url("/cm/").toString()
            container.musicSettings.setServer(url); container.accountRepository.server = url
            container.accountRepository.save("isolated-lyrics-token", UserDto(7, nickname = "Lyrics fixture"))
            container.updateSettings.setAutoCheck(false)
            val song = Song(818, "夜航", artists = "CurrentMusic · 歌词预览", durationMs = 180000)
            container.playerController.queue.replace(listOf(song), 0)
            container.playerController.state.value = PlayerState(song = song, positionMs = 12500, durationMs = 180000)
            container.settings.edit { it.copy(themeMode = io.github.currencortex.music.data.settings.ThemeMode.LIGHT, blur = false) }
            lateinit var activity: androidx.activity.ComponentActivity
            compose.setContent {
                activity = androidx.activity.compose.LocalActivity.current as androidx.activity.ComponentActivity
                LaunchedEffect(activity) {
                    activity.enableEdgeToEdge()
                    if (android.os.Build.VERSION.SDK_INT >= 29) activity.window.isNavigationBarContrastEnforced = false
                }
                CurrentMusicApp(container)
            }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("mini_cover").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("mini_cover").performClick()
            val viewport = compose.onNodeWithTag("music_navigation").fetchSemanticsNode().boundsInRoot
            val fullPlayer = compose.onNodeWithTag("player_screen").fetchSemanticsNode().boundsInRoot
            assertEquals("Player background must reach the entire navigation viewport", viewport, fullPlayer)
            val safe = compose.onNodeWithTag("player_safe_content").fetchSemanticsNode().boundsInRoot
            assertTrue("Transport must stay above the system gesture area", safe.bottom < fullPlayer.bottom)
            compose.runOnIdle {
                val bars = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                assertFalse("Gesture handle must be light over the dark player", bars.isAppearanceLightNavigationBars)
                assertFalse("Status icons must be light over the dark player", bars.isAppearanceLightStatusBars)
            }
            compose.onNodeWithTag("lyrics_options").performClick()
            compose.onNodeWithTag("open_lyrics").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("lyric_line_1").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("lyric_line_1").assertIsSelected()
            compose.onNodeWithTag("player_toggle").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithTag("player_seek").assertIsDisplayed()
            compose.onNodeWithTag("open_player_queue").assertIsDisplayed()
            fun lyricLayout(): androidx.compose.ui.text.TextLayoutResult {
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                compose.onNodeWithText("我们沿着夜色 慢慢走向明天", useUnmergedTree = true)
                    .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
                return layouts.single()
            }
            val originalHeight = lyricLayout().size.height
            assertEquals(30f, lyricLayout().layoutInput.style.fontSize.value, 0f)
            assertEquals(io.github.currencortex.music.feature.lyrics.ui.LyricsFontFamily, lyricLayout().layoutInput.style.fontFamily)
            compose.onNodeWithTag("lyrics_options").performClick()
            compose.onNodeWithTag("open_lyrics_display").performScrollTo().assertIsDisplayed()
            compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(context.externalCacheDir, "player-options-preview.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            compose.onNodeWithTag("open_lyrics_display").performScrollTo().performClick()
            compose.onNodeWithTag("lyrics_font_size").performTouchInput {
                swipe(start = androidx.compose.ui.geometry.Offset(width * .4f, height / 2f),
                    end = androidx.compose.ui.geometry.Offset(width - 1f, height / 2f), durationMillis = 500)
            }
            compose.waitUntil(5000) { container.musicSettings.state.value.lyricsFontSize == 40f }
            compose.onNodeWithTag("lyrics_font_size").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(22f) }
            compose.waitUntil(5000) { container.musicSettings.state.value.lyricsFontSize == 22f }
            compose.onNodeWithTag("lyrics_font_size").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(40f) }
            compose.waitUntil(5000) { container.musicSettings.state.value.lyricsFontSize == 40f }
            assertEquals(40f, container.musicSettings.snapshot().lyricsFontSize, 0f)
            compose.onNodeWithText("字号 40").assertIsDisplayed()
            compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
            fun previewInk(): Float {
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                compose.onNodeWithTag("lyrics_font_preview")
                    .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
                return layouts.single().layoutInput.style.color.red
            }
            assertTrue("Lyric display settings must use dark ink in light appearance", previewInk() < .4f)
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(context.externalCacheDir, "lyrics-font-settings-preview.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            container.settings.edit { it.copy(themeMode = io.github.currencortex.music.data.settings.ThemeMode.DARK) }
            compose.waitUntil(5000) { previewInk() > .8f }
            compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(context.externalCacheDir, "lyrics-font-settings-dark-preview.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            container.settings.edit { it.copy(themeMode = io.github.currencortex.music.data.settings.ThemeMode.LIGHT) }
            compose.waitUntil(5000) { previewInk() < .4f }
            compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("lyrics_display_settings").assertDoesNotExist()
            assertEquals(40f, lyricLayout().layoutInput.style.fontSize.value, 0f)
            assertTrue("Changing the font size must remeasure cached word geometry", lyricLayout().size.height > originalHeight)
            compose.onNodeWithTag("lyric_line_1").assertIsSelected().assertIsDisplayed()
            compose.onNodeWithTag("player_toggle").assertIsDisplayed()
            compose.onNodeWithTag("lyrics_options").performClick()
            compose.onNodeWithTag("open_lyrics_display").performScrollTo().performClick()
            compose.onNodeWithTag("lyrics_font_reset").performClick()
            compose.waitUntil(5000) { container.musicSettings.state.value.lyricsFontSize == 30f }
            compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
            assertEquals(30f, lyricLayout().layoutInput.style.fontSize.value, 0f)
            compose.onNodeWithTag("open_player_queue").performClick()
            compose.onNodeWithTag("player_queue_sheet").assertExists()
            val rowLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText("▶ 夜航", useUnmergedTree = true)
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(rowLayouts) }
            assertTrue("Queue dialogs must follow light app appearance", rowLayouts.first().layoutInput.style.color.red < .4f)
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(context.externalCacheDir, "player-queue-preview.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("player_queue_sheet").assertDoesNotExist()
            compose.onNodeWithTag("lyrics_panel").assertExists()
            compose.onNodeWithTag("player_screen").captureToImage().asAndroidBitmap().let { bitmap ->
                java.io.File(context.externalCacheDir, "lyrics-player-preview.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(context.externalCacheDir, "lyrics-window-preview.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            compose.onNodeWithTag("lyrics_options").performClick()
            compose.onNodeWithTag("open_lyrics").performClick()
            compose.onNodeWithTag("lyrics_panel").assertDoesNotExist()
            compose.onNodeWithTag("player_screen").captureToImage().asAndroidBitmap().let { bitmap ->
                java.io.File(context.externalCacheDir, "cover-player-preview.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            compose.onNodeWithTag("navigate_back").performClick()
            compose.onNodeWithTag("mini_cover").assertExists()
            compose.runOnIdle {
                val bars = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                assertTrue("Closing player must restore dark system icons on the light page", bars.isAppearanceLightNavigationBars)
            }
            assertEquals(818L, container.playerController.queue.state.value.current?.id)
            assertFalse(container.playerController.state.value.playing)
        } finally { container.close(); server.shutdown() }
    }
}
