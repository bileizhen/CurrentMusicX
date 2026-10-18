package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/** All community writes go to an isolated local server, never the real community. */
class DiscoverSectionsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    @Volatile private var liked = false
    @Volatile private var deleted = false
    @Volatile private var published = ""
    @Volatile private var reply = ""
    @Volatile private var pagingUsers = false
    @Volatile private var failUsers = false
    @Volatile private var failNextUserPage = true
    private val userRequests = ConcurrentLinkedQueue<Map<String, String>>()
    private val chatMessages = java.util.concurrent.ConcurrentHashMap<Long, JsonObject>()
    private val chatEvents = ConcurrentLinkedQueue<JsonObject>()
    private val chatSeq = java.util.concurrent.atomic.AtomicLong()
    private val chatWrites = java.util.concurrent.atomic.AtomicInteger()
    private val streamOpened = java.util.concurrent.atomic.AtomicBoolean()
    @Volatile private var failChatSend = false
    private lateinit var directory: io.github.currencortex.music.feature.profile.DiscoverViewModel
    private var keyboard: androidx.compose.ui.platform.SoftwareKeyboardController? = null
    private val user = """{"id":7,"nickname":"Tester","title":"创作者","ip_region":"安徽","is_owner":1}"""
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun post(id: Int, content: String) = buildJsonObject {
        put("id", id); put("content", content); put("author", ApiJson.parseToJsonElement(user))
        put("likeCount", if (liked) 1 else 0); put("likedByMe", liked); put("commentCount", if (reply.isBlank()) 0 else 1)
        put("canDelete", true)
    }
    private fun chatMessage(id: Long, content: String, uid: Long = 7) = buildJsonObject {
        put("id", id); put("userId", uid); put("content", content); put("createdAt", 1_790_000_000)
        put("user", if (uid == 7L) ApiJson.parseToJsonElement(user) else buildJsonObject {
            put("id", uid); put("nickname", "Friend"); put("title", "听众")
        })
    }
    private fun chatEvent(type: String, payload: JsonObject) = buildJsonObject {
        put("seq", chatSeq.incrementAndGet()); put("type", type); put("payload", payload)
    }.also { chatEvents.add(it) }
    @Before fun setup(): Unit = runBlocking {
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "discover-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await(); container.desktopLyrics.markRecommendationSeen()
        chatMessages[1] = chatMessage(1, "欢迎来到 Chat", 8)
        chatMessages[2] = chatMessage(2, "自己的历史消息")
        server = MockWebServer().apply { dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return when {
                    path == "/cm/chat/messages" && request.method == "GET" -> {
                        val before = request.requestUrl!!.queryParameter("before")?.toLongOrNull() ?: Long.MAX_VALUE
                        json(buildJsonObject { put("messages", JsonArray(chatMessages.values.filter { it["id"]!!.jsonPrimitive.long < before }
                            .sortedBy { it["id"]!!.jsonPrimitive.long }.takeLast(30))) }.toString())
                    }
                    path == "/cm/chat/messages" && request.method == "POST" -> {
                        chatWrites.incrementAndGet()
                        assertEquals("Bearer isolated-token", request.getHeader("Authorization"))
                        if (failChatSend) MockResponse().setResponseCode(502) else {
                            val content = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject["content"]!!.jsonPrimitive.content
                            val id = 10L + chatWrites.get()
                            val message = chatMessage(id, content)
                            chatMessages[id] = message; chatEvent("message", message)
                            json(message.toString())
                        }
                    }
                    path.startsWith("/cm/chat/messages/") && request.method == "DELETE" -> {
                        val id = path.substringAfterLast('/').toLong()
                        chatMessages.remove(id); chatEvent("delete", buildJsonObject { put("id", id) }); json("{}")
                    }
                    path == "/cm/chat/members" -> json("""{"members":[$user,{"id":8,"nickname":"Friend"}]}""")
                    path == "/cm/chat/live" -> {
                        val since = request.requestUrl!!.queryParameter("since")?.toLongOrNull() ?: 0
                        json(buildJsonObject {
                            put("events", JsonArray(chatEvents.filter { it["seq"]!!.jsonPrimitive.long > since }))
                            put("latestSeq", chatSeq.get())
                        }.toString())
                    }
                    path == "/cm/live/chat" -> {
                        if (streamOpened.compareAndSet(false, true)) {
                            val message = chatMessage(3, "来自实时连接的消息", 8)
                            chatMessages[3] = message
                            val event = chatEvent("message", message)
                            MockResponse().setHeader("Content-Type", "text/event-stream")
                                .setBody("event: message\ndata: $event\n\n")
                                .setBodyDelay(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                        } else MockResponse().setHeader("Content-Type", "text/event-stream").setBody(": ping\n\n")
                    }
                    path == "/cm/auth/me" -> json(user)
                    path == "/cm/users/square" -> {
                        val query = request.requestUrl!!
                        val offset = query.queryParameter("offset")!!.toInt()
                        userRequests.add(listOf("query", "sort", "offset", "listening").associateWith { query.queryParameter(it).orEmpty() })
                        when {
                            failUsers -> MockResponse().setResponseCode(503)
                            pagingUsers && offset == 3 && failNextUserPage -> { failNextUserPage = false; MockResponse().setResponseCode(503) }
                            pagingUsers -> {
                                val ids = when (offset) { 0 -> listOf(7, 8, 9); 3 -> listOf(9, 10, 11); else -> listOf(12) }
                                json(buildJsonObject {
                                    put("total", 7); put("users", JsonArray(ids.map { id -> buildJsonObject { put("id", id); put("nickname", "User $id") } }))
                                    put("stats", buildJsonObject { put("users", 7) })
                                }.toString())
                            }
                            else -> json("""{"total":1,"users":[$user],"stats":{"users":1}}""")
                        }
                    }
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
        compose.setContent {
            keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            directory = androidx.lifecycle.viewmodel.compose.viewModel(factory = io.github.currencortex.music.ui.util.viewModelFactory {
                io.github.currencortex.music.feature.profile.DiscoverViewModel(container)
            })
            CurrentMusicApp(container)
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("首页").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("发现").performClick()
        compose.onNodeWithTag("discover_music").assertExists()
    }
    @Test fun chatSupportsLiveMessagesMentionsExplicitRetryAndOwnMessageDeletion() {
        open(); compose.onNodeWithText("Chat").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("chat_message_3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("chat_input").performTextInput("你好 Chat")
        compose.onNodeWithTag("chat_send").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("chat_message_11").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, chatWrites.get())
        compose.onNodeWithTag("chat_members").performClick()
        compose.onNodeWithTag("chat_mention_8").performClick()
        compose.onNodeWithTag("chat_input").assertTextEquals("@Friend ")
        compose.onNodeWithTag("chat_input").performTextReplacement("失败时保留草稿")
        failChatSend = true
        compose.onNodeWithTag("chat_send").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("服务器异常，草稿已保留，请确认后重试").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("chat_input").assertTextEquals("失败时保留草稿")
        assertEquals(2, chatWrites.get())
        failChatSend = false
        compose.onNodeWithTag("chat_send").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("chat_message_13").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("chat_menu_13").performClick()
        compose.onNodeWithTag("chat_delete").performClick()
        assertTrue(chatMessages.containsKey(13))
        compose.onNodeWithTag("chat_confirm_delete").performClick()
        compose.waitUntil(10_000) { !chatMessages.containsKey(13) && compose.onAllNodesWithTag("chat_message_13").fetchSemanticsNodes().isEmpty() }
        assertEquals(3, chatWrites.get())
    }
    @Test fun aiEditsRenderMarkdownAndCardsWithoutReloadingHistory() {
        open(); compose.onNodeWithText("Chat").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("chat_message_3").fetchSemanticsNodes().isNotEmpty() }
        val bot = buildJsonObject {
            put("id", 50); put("userId", 99); put("content", ""); put("streaming", true)
            put("user", buildJsonObject { put("id", 99); put("nickname", "CurrentMusicAI"); put("isBot", true) })
        }
        chatEvent("message", bot)
        compose.waitUntil(10000) { compose.onAllNodesWithText("正在思考…").fetchSemanticsNodes().isNotEmpty() }
        chatEvent("edit", buildJsonObject {
            put("id", 50); put("content", "**最终回复**"); put("streaming", false)
            put("atts", JsonArray(listOf(buildJsonObject { put("type", "song"); put("id", 8); put("name", "推荐歌") })))
        })
        compose.waitUntil(10000) { compose.onAllNodesWithText("最终回复").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("music_attachment_song_8").assertExists()
        compose.onNodeWithText("AI").assertExists()
        assertEquals(0, chatWrites.get())
    }
    @Test fun chatAlignsComposerControlsAndKeepsLongMessagesInsideTheViewport() {
        chatMessages[2] = chatMessage(2, "最近在听电影原声。\n旋律从轻快到安静，配上路上的风景刚刚好。\n也欢迎大家分享自己的歌单，一起发现更多好听的音乐。")
        open(); compose.onNodeWithText("Chat").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("chat_message_3").fetchSemanticsNodes().isNotEmpty() }
        fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val composer = bounds("chat_composer")
        val header = bounds("chat_header")
        val messages = bounds("chat_messages")
        assertTrue("Messages must pass behind the header", messages.top < header.bottom)
        assertTrue("Messages must pass behind the composer", messages.bottom > composer.top)
        assertFalse("Fading messages must not filter the fixed header or composer",
            compose.onNodeWithTag("chat_messages").fetchSemanticsNode().config.contains(
                io.github.currencortex.music.ui.component.ScrollEdgeBlurKey))
        assertEquals(header.left, composer.left, 1f)
        assertEquals(header.right, composer.right, 1f)
        val photo = bounds("chat_pick_image")
        val send = bounds("chat_send")
        assertEquals(photo.width, send.width, 1f)
        assertEquals(photo.center.y, send.center.y, 1f)
        compose.onNodeWithTag("chat_send").assertIsNotEnabled()
        compose.onNodeWithTag("chat_input").performTextInput("第一行\n第二行\n第三行")
        compose.onNodeWithTag("chat_send").assertIsEnabled()
        assertEquals(bounds("chat_pick_image").bottom, bounds("chat_send").bottom, 1f)
        compose.onNodeWithTag("chat_input").performTextClearance()
        compose.runOnIdle { keyboard?.hide() }
        compose.waitForIdle()
        // Both sides use the same gutter; timestamps share the bubble's inner text edge.
        compose.onNodeWithTag("chat_avatar_2", useUnmergedTree = true).performScrollTo()
        val bubble = bounds("chat_bubble_2")
        assertTrue(bubble.left >= composer.left && bubble.right <= composer.right)
        assertTrue(bounds("chat_body_2").right <= bubble.right)
        assertEquals(composer.right, bounds("chat_avatar_2").right, 1f)
        compose.onNodeWithTag("chat_message_3").performScrollTo()
        assertEquals(bounds("chat_body_3").left, bounds("chat_time_3").left, 1f)
        assertEquals(composer.left, bounds("chat_avatar_3").left, 1f)
        compose.onNodeWithTag("discover_chat").captureToImage().asAndroidBitmap().let { bitmap ->
            val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
            java.io.File(context.getExternalFilesDir(null), "chat-alignment.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        assertEquals("Visual checks must not publish chat messages", 0, chatWrites.get())
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
    @Test fun titlesAndPublicRegionsAppearInUsersAndPostsAndMissingFieldsLeaveNoBadge() {
        open(); compose.onNodeWithText("用户").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("discover_user_7").fetchSemanticsNodes().isNotEmpty() }
        fun inUser(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("discover_user_7")), useUnmergedTree = true)
        inUser("创作者").assertIsDisplayed()
        inUser("IP属地：安徽").assertIsDisplayed()
        inUser("站长").assertIsDisplayed()
        compose.onNodeWithTag("discover_user_7").captureToImage().asAndroidBitmap().let { bitmap ->
            val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
            java.io.File(context.getExternalFilesDir(null), "discover-user-labels.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithText("帖子").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("post_1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("创作者") and hasAnyAncestor(hasTestTag("post_1")), useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText("IP属地：安徽") and hasAnyAncestor(hasTestTag("post_1")), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("用户").performClick()
        pagingUsers = true
        compose.runOnIdle { directory.refresh() }
        compose.waitUntil(10_000) { directory.state.value.users.firstOrNull()?.nickname == "User 7" && !directory.state.value.loading }
        compose.onNode(hasTestTag("user_title") and hasAnyAncestor(hasTestTag("discover_user_7")), useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasTestTag("user_ip_region") and hasAnyAncestor(hasTestTag("discover_user_7")), useUnmergedTree = true).assertDoesNotExist()
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

    @Test fun userSortAndListeningFilterDoNotSubmitAnUnsentSearchDraft() {
        open(); compose.onNodeWithText("用户").performClick()
        compose.waitUntil(10_000) { directory.state.value.loaded && !directory.state.value.loading }
        compose.onNodeWithTag("discover_query").performTextInput("unsent")
        compose.onNodeWithTag("discover_sort").performClick()
        compose.onNodeWithTag("discover_sort_name").performClick()
        compose.waitUntil(10_000) { directory.state.value.sort == "name" && !directory.state.value.loading }
        assertEquals("", userRequests.last()["query"])
        compose.onNodeWithTag("discover_query").assertTextEquals("unsent")
        compose.onNodeWithTag("discover_listening").performClick().assertIsOn()
        compose.waitUntil(10_000) { directory.state.value.listening && !directory.state.value.loading }
        assertEquals("", userRequests.last()["query"])
        assertEquals("1", userRequests.last()["listening"])
        compose.onNodeWithTag("discover_query").performClick().performImeAction()
        compose.waitUntil(10_000) { directory.state.value.submitted == "unsent" && !directory.state.value.loading }
        assertEquals("unsent", userRequests.last()["query"])
        compose.onNodeWithTag("discover_clear").performClick()
        compose.waitUntil(10_000) { directory.state.value.submitted.isEmpty() && !directory.state.value.loading }
        compose.onNodeWithTag("discover_query").assertTextEquals("")
    }

    @Test fun automaticPaginationUsesServerOffsetsAndRetryPreservesLoadedUsers(): Unit = runBlocking {
        pagingUsers = true
        open(); compose.onNodeWithText("用户").performClick()
        compose.waitUntil(10_000) { directory.state.value.failedMore }
        assertEquals(listOf(7L, 8L, 9L), directory.state.value.users.map { it.id })
        assertEquals(1, userRequests.count { it["offset"] == "3" })
        compose.onNodeWithTag("discover_retry").performScrollTo().performClick()
        compose.waitUntil(10_000) { !directory.state.value.loading && !directory.state.value.hasMore }
        assertEquals(listOf(7L, 8L, 9L, 10L, 11L, 12L), directory.state.value.users.map { it.id })
        assertEquals(7, directory.state.value.nextOffset)
        assertEquals(2, userRequests.count { it["offset"] == "3" })
        failUsers = true
        withContext(Dispatchers.Main) { directory.refresh() }
        compose.waitUntil(10_000) { !directory.state.value.loading && directory.state.value.error != null }
        assertFalse(directory.state.value.failedMore)
        assertEquals(6, directory.state.value.users.size)
    }

    @Test fun pullingTheUserListRefreshesAndRetainsRowsOnFailure() {
        open(); compose.onNodeWithText("用户").performClick()
        compose.waitUntil(10_000) { directory.state.value.loaded && !directory.state.value.loading }
        failUsers = true
        compose.onNodeWithTag("discover_users_list").performTouchInput {
            swipeDown(startY = 100f, endY = height - 80f, durationMillis = 800)
        }
        compose.waitUntil(10_000) { !directory.state.value.loading && directory.state.value.error != null }
        assertEquals(1, directory.state.value.users.size)
        compose.onNodeWithTag("discover_user_7").assertExists()
        compose.onNodeWithTag("discover_retry").assertExists()
    }
}
