package io.github.currencortex.music.data.style

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class MusicStyleRepositoryTest {
    private fun repository(server: MockWebServer): MusicStyleRepository {
        val url = server.url("/cm/").toString()
        val api = ApiClient({ url }, { "must-not-be-sent" }, {})
        return MusicStyleRepository(api) { RequestSession(url, null) }
    }
    @Test fun catalogPreservesNestedCategoriesAndUsesConfiguredGatewayWithoutCredentials() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":200,"data":[{"tagId":1000,"tagName":"流行","enName":"Pop","childrenTags":[{"tagId":1020,"tagName":"华语流行","childrenTags":null}]}]}"""))
            val repo = repository(server)
            val catalog = repo.list()
            assertEquals("Pop", catalog.single().enName)
            assertEquals("华语流行", catalog.single().find(1020)?.tagName)
            assertNull(catalog.single().find(99))
            assertEquals(catalog, repo.list())
            assertEquals(1, server.requestCount)
            val request = server.takeRequest()
            assertEquals("/cm/ncm/style/list", request.path)
            assertNull(request.getHeader("Authorization"))
        }
    }
    @Test fun songPagesAdvanceEchoedOffsetsAndKeepPlaybackMetadata() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":200,"data":{"page":{"cursor":0,"size":2,"total":3,"more":true},"songs":[{"id":11,"name":"A","ar":[{"id":7,"name":"Artist"}],"al":{"name":"Album","picUrl":"http://p1.music.126.net/cover.jpg"},"dt":10000,"mv":22},{"id":12,"name":"B"}]}}"""))
            server.enqueue(MockResponse().setBody("""{"code":200,"data":{"page":{"cursor":2,"size":2,"total":3,"more":false},"songs":[{"id":13,"name":"C"}]}}"""))
            val repo = repository(server)
            val first = repo.songs(1000, 1, size = 2)
            assertEquals(2, first.nextCursor); assertTrue(first.more)
            assertEquals(listOf(11L, 12L), first.songs.map { it.id })
            with(first.songs.first()) {
                assertEquals("Artist", artists); assertEquals(listOf(7L), artistIds)
                assertEquals("Album", album); assertTrue(cover.startsWith("https:"))
                assertEquals(10000L, durationMs); assertEquals(22L, mv)
            }
            val second = repo.songs(1000, 1, first.nextCursor, size = 2)
            assertEquals(listOf(13L), second.songs.map { it.id }); assertFalse(second.more)
            server.takeRequest().requestUrl!!.let {
                assertEquals("1000", it.queryParameter("tagId")); assertEquals("1", it.queryParameter("sort")); assertEquals("0", it.queryParameter("cursor"))
            }
            assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("cursor"))
        }
    }
    @Test fun emptyPagesEndPaginationAndBadResponsesDoNotBecomeAnEmptyCatalog() = runBlocking {
        MockWebServer().use { server ->
            val repo = repository(server)
            server.enqueue(MockResponse().setBody("""{"data":{"page":{"cursor":0,"size":30,"more":true},"songs":[]}}"""))
            assertFalse(repo.songs(1000, 0).more)
            server.enqueue(MockResponse().setBody("""{"code":500,"data":[]}"""))
            assertEquals(ErrorKind.Server, (appResult { repo.list() } as AppResult.Failure).kind)
            server.enqueue(MockResponse().setBody("""{"data":[{"tagId":1008,"tagName":"摇滚"}]}"""))
            assertEquals("摇滚", repo.list().single().tagName)
            server.enqueue(MockResponse().setBody("""{"data":{"songs":[]}}"""))
            assertEquals(ErrorKind.Parse, (appResult { repo.songs(1008, 0) } as AppResult.Failure).kind)
        }
    }
    @Test fun serverChangesCannotReuseAnotherServersCatalog() = runBlocking {
        MockWebServer().use { first -> MockWebServer().use { second ->
            var url = first.url("/cm/").toString()
            val repo = MusicStyleRepository(ApiClient({ url }, { null }, {})) { RequestSession(url, null) }
            first.enqueue(MockResponse().setBody("""{"data":[{"tagId":1000,"tagName":"流行"}]}"""))
            second.enqueue(MockResponse().setBody("""{"data":[{"tagId":1008,"tagName":"摇滚"}]}"""))
            assertEquals(1000L, repo.list().single().tagId)
            url = second.url("/cm/").toString()
            assertEquals(1008L, repo.list().single().tagId)
            assertEquals(1, first.requestCount); assertEquals(1, second.requestCount)
        } }
    }
}
