package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

/** All community writes go to an isolated local server, never the real community. */
class DiscoverSectionsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    @Volatile private var liked = false
    @Volatile private var deleted = false
    @Volatile private var published = ""
    @Volatile private var reply = ""
    private val user = """{"id":7,"nickname":"Tester","title":"创作者"}"""
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun post(id: Int, content: String) = buildJsonObject {
        put("id", id); put("content", content); put("author", ApiJson.parseToJsonElement(user))
        put("likeCount", if (liked) 1 else 0); put("likedByMe", liked); put("commentCount", if (reply.isBlank()) 0 else 1)
        put("canDelete", true)
    }
    @Before fun setup(): Unit = runBlocking {
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "discover-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await(); container.desktopLyrics.markRecommendationSeen()
        server = MockWebServer().apply { dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return when {
                    path == "/cm/auth/me" -> json(user)
                    path == "/cm/users/square" -> json("""{"total":1,"users":[$user],"stats":{"users":1}}""")
                    path == "/cm/decorations/scales" -> json("""{"scales":{}}""")
                    path == "/cm/posts" && request.method == "POST" -> {
                        assertEquals("Bearer isolated-token", request.getHeader("Authorization"))
                        published = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject["content"]!!.jsonPrimitive.content
                        json("{}")
                    }
                    path == "/cm/posts" -> json(buildJsonObject {
                        val posts = listOfNotNull(if (!deleted) post(1, "社区测试帖子") else null,
                            if (published.isNotBlank()) post(2, published) else null)
                        put("posts", JsonArray(posts)); put("total", posts.size)
                    }.toString())
                    path == "/cm/posts/1/like" -> { liked = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject["on"]!!.jsonPrimitive.boolean; json("{}") }
                    path == "/cm/posts/1/comments" && request.method == "POST" -> {
                        reply = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject["content"]!!.jsonPrimitive.content; json("{}")
                    }
                    path == "/cm/posts/1/comments" -> json(buildJsonObject {
                        put("comments", JsonArray(if (reply.isBlank()) emptyList() else listOf(buildJsonObject {
                            put("id", 10); put("content", reply); put("author", ApiJson.parseToJsonElement(user)); put("canDelete", true)
                        })))
                    }.toString())
                    path == "/cm/posts/1" && request.method == "DELETE" -> { deleted = true; json("{}") }
                    path == "/cm/posts/comments/10" && request.method == "DELETE" -> { reply = ""; json("{}") }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }; start() }
        val base = server.url("/cm/").toString()
        container.musicSettings.setServer(base); container.accountRepository.server = base
        container.accountRepository.save("isolated-token", UserDto(7, nickname = "Tester", title = "创作者"))
        container.updateSettings.setAutoCheck(false)
    }
    @After fun cleanup(): Unit = runBlocking {
        if (::container.isInitialized) { container.accountRepository.clear(); container.close() }
        if (::server.isInitialized) server.shutdown()
    }
    private fun open() {
        compose.setContent { CurrentMusicApp(container) }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("首页").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("发现").performClick()
        compose.onNodeWithTag("discover_music").assertExists()
    }
    @Test fun tabsSeparateMusicUsersAndPostsAndKeepUserSearchWhenReturning() {
        open()
        compose.onNodeWithText("用户").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("discover_user_7").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("discover_query").performTextInput("Tester")
        compose.onNodeWithText("帖子").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("post_1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("社区测试帖子").assertExists()
        compose.onNodeWithText("用户").performClick()
        compose.onNodeWithTag("discover_query").assertTextEquals("Tester")
        compose.onNodeWithText("音乐").performClick()
        compose.onNodeWithTag("discover_music").assertExists()
    }
    @Test fun postLikesCommentsPublishingAndConfirmedDeletionWorkAgainstTheFixture() {
        open(); compose.onNodeWithText("帖子").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("post_1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("post_like_1").performScrollTo().performClick()
        compose.waitUntil(10_000) { liked && compose.onAllNodesWithText("已赞 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("post_comments_1").performClick()
        compose.onNodeWithTag("post_reply").performTextInput("测试回复")
        compose.onNodeWithTag("post_reply_send").performClick()
        compose.waitUntil(10_000) { reply == "测试回复" && compose.onAllNodesWithText("评论已发布").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("post_comment_delete_10").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("post_comment_delete_10").performClick()
        compose.onNodeWithTag("post_comment_confirm_delete").performClick()
        compose.waitUntil(10_000) { reply.isEmpty() && compose.onAllNodesWithText("评论已删除").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("post_create").performScrollTo().performClick()
        compose.onNodeWithTag("post_draft").performTextInput("新的测试帖子")
        compose.onNodeWithTag("post_publish").performClick()
        compose.waitUntil(10_000) { published == "新的测试帖子" && compose.onAllNodesWithTag("post_2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("post_delete_1").performScrollTo().performClick()
        assertFalse(deleted)
        compose.onNodeWithTag("post_confirm_delete").performClick()
        compose.waitUntil(10_000) { deleted && compose.onAllNodesWithTag("post_1").fetchSemanticsNodes().isEmpty() }
    }
}
