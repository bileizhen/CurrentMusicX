package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.player.PlayerViewModel
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** Writes only to a local fixture; never posts to real users or NetEase. */
class PlayerCommentsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private lateinit var vm: PlayerViewModel
    private val posted = CopyOnWriteArrayList<JsonObject>()
    @Volatile private var failure = false
    @Volatile private var slow = false
    @Before fun setup(): Unit = runBlocking {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = when (request.requestUrl!!.encodedPath) {
                    "/cm/ncmbind" -> """{"bound":true,"profile":{"uid":700}}"""
                    "/cm/ncmbind/comment/11" -> {
                        assertEquals("Bearer fixture", request.getHeader("Authorization"))
                        posted += ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject
                        if (failure) return MockResponse().setResponseCode(502)
                        return MockResponse().setBody("""{"code":200}""").apply { if (slow) setBodyDelay(600, TimeUnit.MILLISECONDS) }
                    }
                    "/cm/ncm/comment/music" -> """{"code":200,"total":1,"comments":[{"commentId":1,"content":"原评论","user":{"nickname":"听友"}}]}"""
                    "/cm/ncmbind/likelist" -> """{"ids":[]}"""
                    "/cm/daily" -> "{}"
                    "/cm/playlists" -> """{"playlists":[]}"""
                    "/cm/plays/recent", "/cm/likes/mine" -> """{"songs":[]}"""
                    "/cm/ncm/user/playlist" -> """{"code":200,"playlist":[]}"""
                    "/cm/room/active" -> """{"room":null}"""
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setBody(body)
            }
        }
        server.start()
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "comments-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        container.desktopLyrics.markRecommendationSeen(); container.updateSettings.setAutoCheck(false)
        val url = server.url("/cm/").toString()
        container.musicSettings.setServer(url); container.accountRepository.server = url
        container.accountRepository.save("fixture", UserDto(7, "listener", "Listener"))
        val song = Song(11, "评论测试", durationMs = 180000)
        container.playbackQueue.replace(listOf(song), 0)
        container.playerController.state.value = PlayerState(song = song)
        compose.setContent {
            vm = androidx.lifecycle.viewmodel.compose.viewModel(factory = io.github.currencortex.music.ui.util.viewModelFactory { PlayerViewModel(container) })
            CurrentMusicApp(container)
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("mini_cover").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("mini_cover").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("player_comments").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("player_comments").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("原评论").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() { container.close(); server.shutdown() }
    @Test fun composerSendsRepliesOnceAndKeepsDraftOnFailure() {
        slow = true
        compose.onNodeWithTag("player_comment_send").assertIsNotEnabled()
        compose.onNodeWithTag("player_comment_draft").performClick().performTextInput("新评论")
        compose.onNodeWithTag("player_comment_send").assertIsDisplayed()
        compose.onNodeWithTag("player_comment_send").performClick()
        compose.waitUntil(5000) { posted.size == 1 }
        compose.onNodeWithTag("player_comment_send").assertIsNotEnabled()
        compose.waitUntil(10000) { vm.commentComposer.value.message == "评论已发送" }
        assertEquals("新评论", posted[0]["content"]!!.jsonPrimitive.content)
        compose.onNodeWithTag("player_comment_draft").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithTag("reply_comment_1").performScrollTo().performClick()
        compose.onNodeWithTag("player_comment_draft").performTextInput("回复内容")
        compose.onNodeWithTag("player_comment_send").performClick()
        compose.waitUntil(10000) { posted.size == 2 && !vm.commentComposer.value.sending }
        assertEquals(1L, posted[1]["commentId"]!!.jsonPrimitive.long)
        failure = true
        compose.onNodeWithTag("player_comment_draft").performTextInput("保留草稿")
        compose.onNodeWithTag("player_comment_send").performClick()
        compose.waitUntil(10000) { vm.commentComposer.value.error != null }
        compose.onNodeWithTag("player_comment_draft").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("保留草稿")))
        assertEquals(3, posted.size)
    }
    @Test fun switchingSongsDiscardsDraftAndLateSendCompletion() {
        slow = true
        compose.onNodeWithTag("player_comment_draft").performTextInput("旧歌曲草稿")
        compose.onNodeWithTag("player_comment_send").performClick()
        compose.waitUntil(5000) { posted.size == 1 }
        compose.runOnIdle {
            val song = Song(22, "新歌曲")
            container.playbackQueue.replace(listOf(song), 0)
            container.playerController.state.value = PlayerState(song = song)
        }
        compose.waitUntil(5000) { vm.comments.value.songId == 22L }
        compose.waitForIdle()
        assertEquals("", vm.commentComposer.value.draft)
        assertNull(vm.commentComposer.value.message)
        assertFalse(vm.commentComposer.value.sending)
    }
}
