package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class SongCommentsRepositoryTest {
    @Test fun pagingPreservesOpaqueCommentIdsAndCursor() = runBlocking {
        MockWebServer().use { server ->
            val owner = RequestSession(server.url("/cm/").toString(), "account")
            val repo = SongCommentsRepository(ApiClient({ owner.server }, { owner.token }, {})) { owner }
            val body = """{"comments":[{"id":"999999999999999999999","nickname":"听友","replyCount":3,"likedByMe":true}],"more":true,"nextCursor":"abc#123+next","meUid":42,"total":80}"""
            server.enqueue(MockResponse().setBody(body))
            val first = repo.comments(7, CommentSort.LATEST)
            assertEquals("999999999999999999999", first.comments.single().id)
            assertTrue(first.comments.single().likedByMe)
            assertEquals("Bearer account", server.takeRequest().getHeader("Authorization"))
            server.enqueue(MockResponse().setBody(body))
            repo.comments(7, CommentSort.LATEST, 2, first.nextCursor)
            val request = server.takeRequest().requestUrl!!
            assertEquals("2", request.queryParameter("page")); assertEquals("20", request.queryParameter("limit"))
            assertEquals("abc#123+next", request.queryParameter("cursor"))
            server.enqueue(MockResponse().setBody(body))
            repo.replies(7, first.comments.single().id, first.nextCursor)
            val floor = server.takeRequest().requestUrl!!
            assertEquals(first.comments.single().id, floor.queryParameter("commentId"))
            assertEquals(first.nextCursor, floor.queryParameter("cursor"))
        }
    }
    @Test fun writesCarryConfirmationAndNeverRetryBusinessFailures() = runBlocking {
        MockWebServer().use { server ->
            val owner = RequestSession(server.url("/cm/").toString(), "account")
            val repo = SongCommentsRepository(ApiClient({ owner.server }, { owner.token }, {})) { owner }
            server.enqueue(MockResponse().setBody("""{"code":200}"""))
            repo.like(7, "opaque-id", true)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            val body = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("opaque-id", body["cid"]!!.jsonPrimitive.content)
            assertEquals(1, body["confirm"]!!.jsonPrimitive.int)
            server.enqueue(MockResponse().setBody("""{"code":502}"""))
            assertTrue(appResult { repo.send(7, "Reply", "opaque-id") } is AppResult.Failure)
            assertEquals(2, server.requestCount)
            val reply = ApiJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertEquals(2, reply["t"]!!.jsonPrimitive.int)
            assertEquals("opaque-id", reply["commentId"]!!.jsonPrimitive.content)
            server.enqueue(MockResponse().setBody("{}"))
            assertEquals(ErrorKind.Parse, (appResult { repo.delete(7, "opaque-id") } as AppResult.Failure).kind)
        }
    }
    @Test fun anonymousReadsDoNotRequireAccountAndAnonymousWritesStopBeforeHttp() = runBlocking {
        MockWebServer().use { server ->
            val owner = RequestSession(server.url("/cm/").toString(), null)
            val repo = SongCommentsRepository(ApiClient({ owner.server }, { null }, {})) { owner }
            server.enqueue(MockResponse().setBody("""{"comments":[],"meUid":0}"""))
            assertEquals(0L, repo.comments(7, CommentSort.RECOMMENDED).meUid)
            assertNull(server.takeRequest().getHeader("Authorization"))
            assertTrue(appResult { repo.like(7, "1", true) } is AppResult.Failure)
            assertEquals(1, server.requestCount)
        }
    }
}
