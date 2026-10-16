package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class PostsRepositoryTest {
    @Test fun publicFeedRetainsTitlesAttachmentsAndPaginationWithoutRequiringLogin(): Unit = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/cm/").toString()
            val repo = PostsRepository(ApiClient({ base }, { null }, {})) { RequestSession(base, null) }
            server.enqueue(MockResponse().setBody("""{"total":21,"posts":[{"id":2,"content":"**音乐**","author":{"id":7,"title":"创作者"},"attachments":[{"type":"song","id":11,"name":"Song"}]}]}"""))
            val page = repo.list(20)
            assertEquals(21, page.total); assertEquals("创作者", page.posts.single().author.title)
            assertEquals(11L, page.posts.single().attachments.single().id)
            val request = server.takeRequest()
            assertEquals("/cm/posts?offset=20&limit=20", request.path); assertNull(request.getHeader("Authorization"))
            assertTrue(appResult { repo.create("body") } is AppResult.Failure)
            assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        }
    }
    @Test fun writesUseCurrentCredentialsAndExplicitLikeStateAndCommentEndpoints(): Unit = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/cm/").toString()
            val repo = PostsRepository(ApiClient({ base }, { "token" }, {})) { RequestSession(base, "token") }
            server.enqueue(MockResponse().setBody("{}"))
            repo.create("  正文  ", listOf(PostAttachment("song", 11, "Song")))
            val create = server.takeRequest()
            assertEquals("Bearer token", create.getHeader("Authorization"))
            val body = ApiJson.parseToJsonElement(create.body.readUtf8()).jsonObject
            assertEquals("正文", body["content"]!!.jsonPrimitive.content)
            assertEquals(11L, body["attachments"]!!.jsonArray.single().jsonObject["id"]!!.jsonPrimitive.long)
            server.enqueue(MockResponse().setBody("{}")); repo.like(2, false)
            assertEquals(false, ApiJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["on"]!!.jsonPrimitive.boolean)
            server.enqueue(MockResponse().setBody("""{"comments":[{"id":4,"author":{"id":7},"content":"好听","canDelete":true}]}"""))
            assertTrue(repo.comments(2).single().canDelete); assertEquals("/cm/posts/2/comments", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("{}")); repo.comment(2, " 回复 ")
            assertEquals("回复", ApiJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["content"]!!.jsonPrimitive.content)
            server.enqueue(MockResponse().setBody("{}")); repo.deleteComment(4)
            val comment = server.takeRequest(); assertEquals("DELETE", comment.method); assertEquals("/cm/posts/comments/4", comment.path)
            server.enqueue(MockResponse().setBody("{}")); repo.delete(2)
            assertEquals("/cm/posts/2", server.takeRequest().path)
        }
    }
    @Test fun lateFeedCannotBePublishedUnderADifferentAccount(): Unit = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/cm/").toString(); var token: String? = "first"
            val repo = PostsRepository(ApiClient({ base }, { token }, {})) { RequestSession(base, token) }
            server.enqueue(MockResponse().setBody("""{"posts":[],"total":0}""").setBodyDelay(250, TimeUnit.MILLISECONDS))
            val pending = async { appResult { repo.list() } }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            token = "second"
            assertTrue(pending.await() is AppResult.Failure)
        }
    }
}
