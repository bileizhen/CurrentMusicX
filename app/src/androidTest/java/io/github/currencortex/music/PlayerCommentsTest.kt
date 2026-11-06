package io.github.currencortex.music

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
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
@OptIn(ExperimentalComposeUiApi::class)
class PlayerCommentsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private lateinit var vm: PlayerViewModel
    private val posted = CopyOnWriteArrayList<JsonObject>()
    @Volatile private var captureIme = false
    @Volatile private var inputConnection: InputConnection? = null
    @Volatile private var failure = false
    @Volatile private var slow = false
    @Volatile private var richComments = false
    @Volatile private var readFailure = false
    private val longComment = "这是值得反复听的一首歌。\n".repeat(18)
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
                    "/cm/ncm/comment/music" -> {
                        if (readFailure) return MockResponse().setResponseCode(503)
                        if (richComments) buildJsonObject {
                            put("code", 200); put("total", 2)
                            putJsonArray("hotComments") { addJsonObject { put("commentId", 1); put("content", "原评论") } }
                            putJsonArray("comments") { addJsonObject {
                                put("commentId", 2); put("content", longComment); put("time", 1729044000000); put("likedCount", 125)
                                putJsonObject("user") { put("nickname", "认真听歌的听友") }
                                putJsonArray("beReplied") { addJsonObject {
                                    put("content", "被回复的内容"); putJsonObject("user") { put("nickname", "另一位听友") }
                                } }
                            } }
                        }.toString() else """{"code":200,"total":1,"comments":[{"commentId":1,"content":"原评论","user":{"nickname":"听友"}}]}"""
                    }
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
            InterceptPlatformTextInput(interceptor = { request, next ->
                if (captureIme) {
                    val connection = request.createInputConnection(EditorInfo())
                    inputConnection = connection
                    try { awaitCancellation() } finally {
                        inputConnection = null
                        connection.closeConnection()
                    }
                } else next.startInputMethod(request)
            }) { CurrentMusicApp(container) }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("mini_cover").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("mini_cover").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("player_comments").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("player_comments").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("原评论").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() { container.close(); server.shutdown() }
    private fun startIme() {
        captureIme = true
        compose.onNodeWithTag("player_comment_draft").performClick()
        compose.waitUntil(5000) { inputConnection != null }
    }

    private fun ime(action: InputConnection.() -> Unit) {
        compose.runOnIdle { requireNotNull(inputConnection).action() }
        compose.waitForIdle()
    }

    @Test fun composingCandidatesReplaceAcrossFramesAndCommitOnlyOnce() {
        startIme()
        ime { setComposingText("听", 1) }
        ime { setComposingText("听这首", 1) }
        ime { setComposingText("听这首歌", 1) }
        compose.runOnIdle { assertEquals("听这首歌", vm.commentComposer.value.draft) }
        ime { commitText("听这首歌", 1) }
        // Handwriting/typing may reopen an already committed candidate for correction.
        ime { setComposingRegion(1, 4) }
        ime { setComposingText("那首歌", 1) }
        ime { finishComposingText() }
        compose.runOnIdle { assertEquals("听那首歌", vm.commentComposer.value.draft) }
        compose.onNodeWithTag("player_comment_send").performClick()
        compose.waitUntil(10000) { posted.size == 1 && !vm.commentComposer.value.sending }
        assertEquals("听那首歌", posted.single()["content"]!!.jsonPrimitive.content)
        assertEquals("", vm.commentComposer.value.draft)
    }

    @Test fun batchedCandidateEditsPreserveSurroundingTextAndDeletion() {
        startIme()
        ime { commitText("前后", 1); setSelection(1, 1) }
        ime {
            beginBatchEdit()
            setComposingText("音", 1)
            setComposingText("音乐", 1)
            endBatchEdit()
        }
        compose.runOnIdle { assertEquals("前音乐后", vm.commentComposer.value.draft) }
        ime { setComposingText("乐", 1) }
        ime { commitText("乐", 1); deleteSurroundingText(1, 0) }
        compose.runOnIdle { assertEquals("前后", vm.commentComposer.value.draft) }
        assertTrue(posted.isEmpty())
    }

    @Test fun sendingImmediatelyAfterImeCommitUsesLatestText() {
        startIme()
        ime { setComposingText("候选", 1) }
        compose.runOnIdle {
            requireNotNull(inputConnection).commitText("最终评论", 1)
            vm.sendComment()
        }
        compose.waitUntil(10000) { posted.size == 1 && !vm.commentComposer.value.sending }
        assertEquals("最终评论", posted.single()["content"]!!.jsonPrimitive.content)
    }

    @Test fun lengthLimitBlocksSendingWithoutInterruptingCandidateReplacement() {
        val prefix = "字".repeat(998)
        startIme()
        ime { commitText(prefix, 1) }
        ime { setComposingText("音乐", 1) }
        ime { setComposingText("音乐会", 1) }
        compose.runOnIdle {
            assertEquals(prefix + "音乐会", vm.commentComposer.value.draft)
            vm.sendComment()
            assertFalse(vm.commentComposer.value.sending)
        }
        compose.onNodeWithTag("player_comment_send").assertIsNotEnabled()
        compose.onNodeWithTag("player_comment_error").assertTextEquals("评论最多 1000 字，请删减后发送")
        ime { setComposingText("歌曲", 1) }
        ime { commitText("歌曲", 1) }
        compose.runOnIdle { assertEquals(prefix + "歌曲", vm.commentComposer.value.draft) }
        compose.onNodeWithTag("player_comment_send").assertIsEnabled()
        compose.onNodeWithTag("player_comment_error").assertDoesNotExist()
        assertTrue(posted.isEmpty())
    }

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
    @Test fun tabsExpandLongCommentsAndReplyWithoutLosingDraft() {
        richComments = true
        compose.onNodeWithTag("refresh_comments").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("comments_latest").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("comments_hot").assertIsSelected()
        compose.onNodeWithTag("comments_latest").performClick()
        compose.onNodeWithText("认真听歌的听友").assertIsDisplayed()
        compose.onNodeWithTag("expand_comment_2").performScrollTo().performClick()
        compose.onNodeWithTag("expand_comment_2").assertTextEquals("收起").performClick()
        compose.onNodeWithText("另一位听友：被回复的内容").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reply_comment_2").performScrollTo().performClick()
        compose.onNodeWithTag("player_comment_draft").assertIsFocused().performTextInput("保留这条回复草稿")
        compose.onNodeWithTag("cancel_comment_reply").performClick()
        compose.onNodeWithTag("player_comment_draft").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("保留这条回复草稿")))
        compose.onNodeWithTag("close_comments").performClick()
        compose.onNodeWithTag("player_comments").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("player_comment_draft").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("player_comment_draft").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("保留这条回复草稿")))
        assertTrue(posted.isEmpty())
    }
    @Test fun refreshFailureKeepsLoadedCommentsAndDraft() {
        compose.onNodeWithTag("player_comment_draft").performTextInput("还没写完")
        readFailure = true
        compose.onNodeWithTag("refresh_comments").performClick()
        compose.waitUntil(5000) { vm.comments.value.error != null }
        compose.onNodeWithText("原评论").assertExists()
        assertEquals("还没写完", vm.commentComposer.value.draft)
        readFailure = false
        compose.onNodeWithTag("retry_comments").performScrollTo().performClick()
        compose.waitUntil(5000) { !vm.comments.value.loading && vm.comments.value.error == null }
        assertEquals("还没写完", vm.commentComposer.value.draft)
        assertTrue(posted.isEmpty())
    }
}
