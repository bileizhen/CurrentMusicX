package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class NeteaseCommentWriteTest {
    private fun json(body: String) = MockResponse().setBody(body)
    @Test fun cloudSendAndReplyUseTheBoundAccountAndNeverRetryFailures(): Unit = runBlocking {
        MockWebServer().use { server ->
            val owner = RequestSession(server.url("/cm/").toString(), "project-token")
            val repo = NeteaseSongActionsRepository(ApiClient({ owner.server }, { owner.token }, {})) { owner }
            for ((reply, status) in listOf(null to 200, 90L to 200, null to 502)) {
                server.enqueue(json("""{"bound":true,"profile":{"uid":700}}"""))
                server.enqueue(if (status == 200) json("""{"code":200}""") else MockResponse().setResponseCode(status))
                val result = appResult { repo.sendComment(11, " 评论内容 ", reply) }
                if (status == 200) assertTrue(result is AppResult.Success) else assertTrue(result is AppResult.Failure)
                server.takeRequest()
                val post = server.takeRequest()
                assertEquals("POST", post.method); assertEquals("/cm/ncmbind/comment/11", post.path)
                assertEquals("Bearer project-token", post.getHeader("Authorization"))
                val body = ApiJson.parseToJsonElement(post.body.readUtf8()).jsonObject
                assertEquals("评论内容", body["content"]!!.jsonPrimitive.content)
                assertEquals(reply, body["commentId"]?.jsonPrimitive?.longOrNull)
                assertNull(server.takeRequest(80, TimeUnit.MILLISECONDS))
            }
        }
    }
    @Test fun noBindingOrBlankContentNeverCreatesAComment(): Unit = runBlocking {
        MockWebServer().use { server ->
            val owner = RequestSession(server.url("/cm/").toString(), "project-token")
            val repo = NeteaseSongActionsRepository(ApiClient({ owner.server }, { owner.token }, {})) { owner }
            assertTrue(appResult { repo.sendComment(11, "   ") } is AppResult.Failure)
            assertEquals(0, server.requestCount)
            server.enqueue(json("""{"bound":false}"""))
            assertEquals(ErrorKind.NeteaseBindingRequired, (appResult { repo.sendComment(11, "文字") } as AppResult.Failure).kind)
            assertEquals(1, server.requestCount)
        }
    }
}
