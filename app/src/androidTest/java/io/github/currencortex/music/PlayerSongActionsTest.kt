package io.github.currencortex.music

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Independent app/container, mock NetEase actions, no audio service or production mutations. */
class PlayerSongActionsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun fourActionsUseRealBadgesCommentsAndExclusivePlaybackModes(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val server = MockWebServer()
        val liked = AtomicBoolean(false)
        val cmLiked = AtomicBoolean(false)
        val mutations = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return MockResponse().setBody(when (path) {
                    "/cm/auth/me" -> """{"id":7,"username":"fixture","nickname":"按钮测试"}"""
                    "/cm/ncmbind" -> """{"bound":true,"profile":{"uid":7},"ncmLikedPlId":42}"""
                    "/cm/ncm/user/playlist" -> """{"code":200,"playlist":[{"id":9918106457,"specialType":5,"creator":{"userId":7}}]}"""
                    "/cm/ncm/song/red/count" -> """{"code":200,"data":{"count":${if (liked.get()) 10002 else 10001}}}"""
                    "/cm/ncmbind/likelist" -> """{"ids":[]}""" // Upstream list deliberately lags acknowledged writes.
                    "/cm/ncmbind/like/11" -> {
                        mutations.incrementAndGet()
                        liked.set(io.github.currencortex.music.core.network.ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject["like"]!!.jsonPrimitive.boolean)
                        """{"code":200}"""
                    }
                    "/cm/likes/11" -> { cmLiked.set(!cmLiked.get()); """{"on":${cmLiked.get()}}""" }
                    "/cm/ncm/comment/music" -> when {
                        request.requestUrl!!.queryParameter("limit") == "1" -> """{"code":200,"total":20001}"""
                        request.requestUrl!!.queryParameter("offset") == "0" -> """{"code":200,"total":20001,"more":true,"hotComments":[{"commentId":90,"content":"网易云热门评论","user":{"nickname":"热评听友"}}],"comments":[{"commentId":1,"content":"网易云最新评论","user":{"nickname":"听友"}}]}"""
                        else -> """{"code":200,"total":20001,"more":false,"comments":[{"commentId":2,"content":"下一页网易云评论","user":{"nickname":"下一位听友"}}]}"""
                    }
                    "/cm/ncm/playmode/intelligence/list" -> if (request.requestUrl!!.queryParameter("pid") == "9918106457")
                        """{"code":200,"data":[${(12..22).joinToString(",") { "{\"songInfo\":{\"id\":$it,\"name\":\"心动推荐$it\",\"dt\":30000}}" }}]}"""
                        else """{"code":400,"message":"歌单不存在","data":null}"""
                    "/cm/ncm/lyric" -> """{"lines":[{"t":0,"txt":"当前歌词"},{"t":10000,"txt":"下一行歌词"}]}"""
                    "/cm/daily" -> "{}"
                    "/cm/room/active" -> "{\"room\":null}"
                    "/cm/songs/status" -> """{"liked":${if (cmLiked.get()) "[11]" else "[]"}}"""
                    else -> return MockResponse().setResponseCode(404)
                })
            }
        }
        server.start()
        val container = AppContainer(context, "four-actions-${UUID.randomUUID()}")
        try {
            container.ready.await(); container.sessionRestored.await()
            container.desktopLyrics.markRecommendationSeen()
            val url = server.url("/cm/").toString()
            container.musicSettings.setServer(url); container.accountRepository.server = url
            container.accountRepository.save("fixture-personal", UserDto(7, nickname = "按钮测试"))
            container.updateSettings.setAutoCheck(false)
            container.settings.edit { it.copy(themeMode = ThemeMode.LIGHT) }
            val song = Song(11, "四个播放功能", "测试歌手", durationMs = 30000)
            container.playbackQueue.replace(listOf(song, Song(23, "原列表歌曲")), 0)
            container.playerController.state.value = PlayerState(song = song, positionMs = 1500, durationMs = 30000)
            lateinit var activity: androidx.activity.ComponentActivity
            compose.setContent {
                activity = androidx.activity.compose.LocalActivity.current as androidx.activity.ComponentActivity
                CurrentMusicApp(container)
            }
            compose.onNodeWithTag("mini_cover").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("like_count", useUnmergedTree = true).fetchSemanticsNodes().any {
                it.config[SemanticsProperties.Text].any { text -> text.text == "1w+" }
            } }
            compose.onNodeWithTag("comment_count", useUnmergedTree = true).assertTextEquals("2w+")
            val tags = listOf("player_like", "player_comments", "player_sleep_timer", "player_effects", "lyrics_options")
            compose.onNodeWithTag("player_heart_mode").assertDoesNotExist()
            val bounds = tags.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
            bounds.zipWithNext().forEach { (left, right) -> assertTrue("Action touch targets cannot overlap", left.right <= right.left + 1) }
            val bar = compose.onNodeWithTag("player_function_bar").fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.first().left >= bar.left && bounds.last().right <= bar.right)
            val transportButtons = listOf(compose.onNodeWithTag("player_cycle_mode"),
                compose.onNodeWithContentDescription("上一首"), compose.onNodeWithTag("player_toggle"),
                compose.onNodeWithContentDescription("下一首"), compose.onNodeWithTag("open_player_queue"))
                .map { it.fetchSemanticsNode().boundsInRoot }
            transportButtons.zipWithNext().forEach { (left, right) ->
                assertTrue("Transport buttons must not overlap", left.right <= right.left + 1)
                assertEquals("Mode and queue share the transport row", left.center.y, right.center.y, 1f)
            }
            assertEquals("Play stays centered", bar.center.x, transportButtons[2].center.x, 1f)
            bounds.zip(transportButtons).forEachIndexed { index, (action, transport) ->
                assertEquals("Upper action $index aligns with its transport button", transport.center.x, action.center.x, 1f)
            }
            val share = compose.onNodeWithTag("player_share").fetchSemanticsNode().boundsInRoot
            assertTrue("Share stays in the top-right header", share.bottom < bar.top && share.center.x > bar.center.x)
            val badge = compose.onNodeWithTag("like_count", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("The real quantity belongs at the icon's upper right", badge.top < bounds.first().center.y && badge.center.x > bounds.first().center.x)
            // A tap toggles the selected library directly; wait for the acknowledged UI state.
            fun likeReady(selected: Boolean): Boolean {
                val node = compose.onAllNodesWithTag("player_like").fetchSemanticsNodes().singleOrNull() ?: return false
                return node.config[SemanticsProperties.Selected] == selected && !node.config.contains(SemanticsProperties.Disabled)
            }
            compose.onNodeWithTag("player_like").performClick()
            compose.waitUntil(10000) { liked.get() && mutations.get() == 1 && likeReady(true) }
            assertFalse(cmLiked.get())
            compose.onNodeWithTag("song_like_sheet").assertDoesNotExist()
            compose.onNodeWithTag("player_like").performClick()
            compose.waitUntil(10000) { !liked.get() && mutations.get() == 2 && likeReady(false) }
            compose.onNodeWithTag("player_like").performClick()
            compose.waitUntil(10000) { liked.get() && mutations.get() == 3 && likeReady(true) }
            container.musicSettings.setLikeMode(io.github.currencortex.music.data.settings.LikeMode.CURRENT_MUSIC)
            compose.waitUntil(10000) { !container.primaryLibrary.likesUseNetease.value && likeReady(false) }
            compose.onNodeWithTag("player_like").performClick()
            compose.waitUntil(10000) { cmLiked.get() && container.libraryRepository.statuses.value[11]?.pending == false && likeReady(true) }
            assertTrue(liked.get())
            compose.onNodeWithTag("player_like").performClick()
            compose.waitUntil(10000) { !cmLiked.get() && container.libraryRepository.statuses.value[11]?.pending == false && likeReady(false) }
            container.musicSettings.setLikeMode(io.github.currencortex.music.data.settings.LikeMode.AUTO)
            compose.waitUntil(10000) { likeReady(true) }
            assertEquals(3, mutations.get())
            compose.onNodeWithTag("player_comments").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("网易云热门评论").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("comments_latest").performClick()
            compose.onNodeWithText("网易云最新评论").assertIsDisplayed()
            compose.onNodeWithTag("more_comments").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("下一页网易云评论").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
            for (mode in listOf(PlaybackMode.ONE, PlaybackMode.SHUFFLE, PlaybackMode.HEART, PlaybackMode.LIST)) {
                compose.onNodeWithTag("player_cycle_mode").performClick()
                compose.waitUntil(5000) { container.playbackQueue.state.value.mode == mode }
                compose.onNodeWithTag("player_cycle_mode").assertContentDescriptionEquals(mode.label)
                if (mode == PlaybackMode.HEART) compose.onNodeWithTag("player_cycle_mode").assertIsSelected()
            }
            compose.onNodeWithTag("player_cycle_mode").assertIsNotSelected()
            compose.onNodeWithTag("open_player_queue").performClick()
            compose.onNodeWithTag("open_player_mode").performClick()
            PlaybackMode.entries.forEach { compose.onNodeWithTag("playback_mode_${it.name}").assertIsDisplayed() }
            compose.onNodeWithTag("playback_mode_LIST").assertIsSelected()
            compose.onNodeWithTag("playback_mode_HEART").performClick()
            compose.waitUntil(5000) { container.playbackQueue.state.value.mode == PlaybackMode.HEART }
            compose.runOnIdle {
                assertEquals(11L, container.playbackQueue.state.value.current!!.id)
                assertEquals(9918106457L, container.playbackQueue.state.value.heartPlaylistId)
                assertEquals(1500L, container.playerController.state.value.positionMs)
                assertFalse(container.playerController.state.value.playing)
                assertEquals(12L, container.playbackQueue.previewNext()!!.id)
            }
            compose.onNodeWithTag("open_player_mode").performClick()
            compose.onNodeWithTag("playback_mode_HEART").assertIsSelected()
            compose.onNodeWithTag("playback_mode_ONE").performClick()
            compose.runOnIdle { assertEquals(PlaybackMode.ONE, container.playbackQueue.state.value.mode) }
            compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("player_cycle_mode").assertContentDescriptionEquals(PlaybackMode.ONE.label).assertIsNotSelected()
            compose.onNodeWithTag("player_screen").captureToImage().asAndroidBitmap().let { bitmap ->
                java.io.File(context.externalCacheDir, "player-four-actions.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            compose.onNodeWithTag("lyrics_options").performClick()
            compose.onNodeWithTag("open_lyrics").assertIsDisplayed()
            compose.onNodeWithTag("open_player_cast").assertIsDisplayed()
            compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("player_share").performClick()
            compose.onNodeWithTag("song_share_dialog").assertExists()
            compose.onNodeWithTag("song_share_copy").assertExists()
            compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("player_effects").performClick()
            compose.onNodeWithTag("sound_effects_screen").assertExists()
            compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("player_screen").assertIsDisplayed()
        } finally { container.close(); server.shutdown() }
    }
}
