package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class SongInsightsRepositoryTest {
    private val song = Song(7, "Original", durationMs = 180_000)
    private fun api(server: MockWebServer) = ApiClient({ server.url("/cm/").toString() }, { "private-token" }, {})
    private fun response(body: String) = MockResponse().setBody(body)

    @Test fun metadataIsAnonymousCachedAndKeepsUnknownQualityVariants() = runBlocking {
        MockWebServer().use { server ->
            var time = 0L
            val repo = SongInsightsRepository(api(server), now = { time })
            val body = """{"id":7,"qualities":[{"level":"new","label":"New format","br":320000,"size":1048576,"sr":48000,"variants":[{"immerseType":"ste","br":-1}]}],"future":true}"""
            server.enqueue(response(body))
            val value = repo.qualities(song).single()
            assertEquals("new", value.level); assertEquals(0L, value.variants.single().br)
            assertEquals(listOf(value), repo.qualities(song))
            val request = server.takeRequest()
            assertEquals("/cm/song/qualities?id=7", request.path)
            assertNull(request.getHeader("Authorization")); assertNull(request.getHeader("Cookie"))
            assertEquals(1, server.requestCount)
            time = 60_001
            server.enqueue(response(body))
            repo.qualities(song)
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun segmentsAreSortedDeduplicatedAndVersionsNeverReplaceTheOriginal() = runBlocking {
        MockWebServer().use { server ->
            val repo = SongInsightsRepository(api(server))
            server.enqueue(response("""{"id":7,"segments":[{"startMs":5000,"endMs":9000},{"startMs":-1,"endMs":3000},{"startMs":1000,"endMs":2000},{"startMs":5000,"endMs":9000}]}"""))
            assertEquals(listOf(ChorusSegment(1000, 2000), ChorusSegment(5000, 9000)), repo.chorus(song))
            server.enqueue(response("""{"id":7,"songs":[{"ncm_id":7,"name":"Original"},{"ncm_id":8,"name":"Other","duration":1234},{"ncm_id":8,"name":"Duplicate"},{"ncm_id":-1,"name":"Invalid"}]}"""))
            val version = repo.versions(song).single()
            assertEquals(8L, version.id); assertEquals("8", version.externalIds.neteaseId)
            assertEquals(1234L, version.durationMs); assertEquals(MusicSource.NETEASE, version.musicSource)
        }
    }
    @Test fun malformedAndMismatchedResponsesAreNotCachedAsEmptySuccess() = runBlocking {
        MockWebServer().use { server ->
            val repo = SongInsightsRepository(api(server))
            for (body in listOf("{}", """{"id":8,"segments":[]}""", """{"id":7,"segments":null}""")) {
                server.enqueue(response(body))
                assertEquals(ErrorKind.Parse, (appResult { repo.chorus(song) } as AppResult.Failure).kind)
            }
            server.enqueue(response("""{"id":7,"segments":[]}"""))
            assertTrue(repo.chorus(song).isEmpty()); assertTrue(repo.chorus(song).isEmpty())
            assertEquals(4, server.requestCount)
        }
    }
    @Test fun serverErrorsCanRetryAndPreviewFallsBackWithoutCallingOldRoutes() = runBlocking {
        MockWebServer().use { server ->
            val repo = MusicRepository(api(server))
            server.enqueue(MockResponse().setResponseCode(502).setBody("""{"error":"upstream"}"""))
            assertFalse(repo.previewClip(song).chorus)
            server.enqueue(response("""{"id":7,"segments":[{"startMs":47500,"endMs":88500}]}"""))
            assertEquals(SongPreviewClip(47500, 67500, true), repo.previewClip(song))
            repeat(2) { assertEquals("/cm/song/chorus?id=7", server.takeRequest().path) }
        }
    }
    @Test fun nonNeteaseAndVideoSongsNeverQueryMetadata() = runBlocking {
        MockWebServer().use { server ->
            val repo = SongInsightsRepository(api(server))
            val unsupported = MusicSource.entries.filter { it != MusicSource.NETEASE }.map { song.copy(musicSource = it) } + song.copy(video = true)
            for (value in unsupported) assertTrue(appResult { repo.versions(value) } is AppResult.Failure)
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun timeoutsRemainRetryableAndDoNotBecomeSuccessfulEmptyData() = runBlocking {
        MockWebServer().use { server ->
            val repo = SongInsightsRepository(api(server), timeoutMillis = 150)
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            assertEquals(ErrorKind.Timeout, (appResult { repo.chorus(song) } as AppResult.Failure).kind)
            server.enqueue(response("""{"id":7,"segments":[]}"""))
            assertTrue(repo.chorus(song).isEmpty())
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun simultaneousReadsShareSuccessAndCancellationDoesNotPoisonTheCache() = runBlocking {
        MockWebServer().use { server ->
            val repo = SongInsightsRepository(api(server))
            server.enqueue(response("""{"id":7,"segments":[]}""").setBodyDelay(100, TimeUnit.MILLISECONDS))
            val first = async { repo.chorus(song) }; val second = async { repo.chorus(song) }
            assertTrue(first.await().isEmpty()); assertTrue(second.await().isEmpty())
            assertEquals(1, server.requestCount)
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val canceled = launch { repo.chorus(song.copy(id = 9)) }
            withContext(Dispatchers.IO) { server.takeRequest(); server.takeRequest(2, TimeUnit.SECONDS) }
            canceled.cancelAndJoin()
            server.enqueue(response("""{"id":9,"segments":[]}"""))
            assertTrue(repo.chorus(song.copy(id = 9)).isEmpty())
            assertEquals(3, server.requestCount)
        }
    }
    @Test fun switchingServersRejectsAnInflightOldResponseAndDoesNotReuseItsCache() = runBlocking {
        MockWebServer().use { first -> MockWebServer().use { second ->
            var address = first.url("/cm/").toString()
            val repo = SongInsightsRepository(ApiClient({ address }, { null }, {}))
            first.enqueue(response("""{"id":7,"segments":[]}""").setBodyDelay(150, TimeUnit.MILLISECONDS))
            val old = async { appResult { repo.chorus(song) } }
            withContext(Dispatchers.IO) { first.takeRequest(2, TimeUnit.SECONDS) }
            address = second.url("/cm/").toString()
            assertTrue(old.await() is AppResult.Failure)
            second.enqueue(response("""{"id":7,"segments":[{"startMs":1000,"endMs":3000}]}"""))
            assertEquals(listOf(ChorusSegment(1000, 3000)), repo.chorus(song))
        } }
    }
}
