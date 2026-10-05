package io.github.currencortex.music.data.library

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class LibraryRepositoryTest {
    @Test fun likeMembershipUsesTheServerWriteResponse() = runBlocking {
        MockWebServer().use { s ->
            val r=repo(s)
            s.enqueue(MockResponse().setBody("""{"liked":[]}"""))
            s.enqueue(MockResponse().setBody("""{"on":false}"""))
            assertTrue(r.toggleLike(Song(1,"A")) is AppResult.Success)
            assertFalse(r.statuses.value[1]!!.liked)
            assertFalse(r.statuses.value[1]!!.pending)
        }
    }
    @Test fun cancellingLikeDoesNotLeavePendingStateStuck() = runBlocking {
        MockWebServer().use { s ->
            val r = repo(s)
            s.enqueue(MockResponse().setBody("{}"))
            s.enqueue(MockResponse().setBody("{}").setHeadersDelay(2, TimeUnit.SECONDS))
            val job = launch(Dispatchers.Default) { r.toggleLike(Song(1,"A")) }
            withTimeout(3000) { while (r.statuses.value[1]?.pending != true) delay(5) }
            job.cancelAndJoin()
            assertEquals(SongStatus(), r.statuses.value[1])
        }
    }
    private fun repo(server: MockWebServer) = LibraryRepository(ApiClient({ server.url("/cm/").toString() }, { "unit-token" }, {}),
        { 7L }, { RequestSession(server.url("/cm/").toString(), "unit-token") })
    @Test fun dailyRecentAndLibraryContractsKeepNormalizedMetadata() = runBlocking {
        MockWebServer().use { s ->
            val r = repo(s)
            s.enqueue(MockResponse().setBody("""{"daily":[{"ncm_id":1,"name":"A","artist_ids":[12]}],"forYou":[{"ncm_id":2,"name":"B"}],"artists":["Artist"]}"""))
            val daily = r.daily(); assertEquals(listOf(12L), daily.songs.first().artistIds); assertEquals(2L, daily.forYou.first().id)
            assertEquals("Bearer unit-token", s.takeRequest().getHeader("Authorization"))
            s.enqueue(MockResponse().setBody("""{"songs":[{"ncm_id":2,"name":"B"}]}"""))
            assertEquals(2L, r.recent().first().id); assertEquals("50", s.takeRequest().requestUrl!!.queryParameter("limit"))
            s.enqueue(MockResponse().setBody("""{"songs":[{"ncm_id":1,"name":"A"}]}"""))
            assertEquals(1L, r.likedSongs().first().id); assertEquals("/cm/likes/mine", s.takeRequest().path)
        }
    }
    @Test fun optimisticLikeRollsBackOnServerFailureAndPreservesFavoriteState() = runBlocking {
        MockWebServer().use { s ->
            val r = repo(s)
            s.enqueue(MockResponse().setBody("""{"liked":[1],"faved":[1]}"""))
            s.enqueue(MockResponse().setResponseCode(500).setHeadersDelay(200, TimeUnit.MILLISECONDS))
            val job = async(Dispatchers.Default) { r.toggleLike(Song(1,"A")) }
            withTimeout(3000) { while (r.statuses.value[1]?.pending != true) delay(5) }
            assertFalse(r.statuses.value[1]!!.liked); assertTrue(r.statuses.value[1]!!.favorite)
            assertTrue(job.await() is AppResult.Failure)
            assertEquals(SongStatus(true,true), r.statuses.value[1])
            s.takeRequest(); assertEquals("/cm/likes/1", s.takeRequest().path)
        }
    }
    @Test fun successfulLikeInvalidatesListsAndClearsPending() = runBlocking {
        MockWebServer().use { s ->
            val r = repo(s); s.enqueue(MockResponse().setBody("{}")); s.enqueue(MockResponse().setBody("{}"))
            assertTrue(r.toggleLike(Song(1,"A")) is AppResult.Success)
            assertTrue(r.statuses.value[1]!!.liked); assertFalse(r.statuses.value[1]!!.pending); assertEquals(1L,r.revision.value)
        }
    }
    @Test fun ncmAndOtherUsersPlaylistsNeverSendWriteRequests() = runBlocking {
        MockWebServer().use { s ->
            val r = repo(s)
            listOf("ncm" to 7, "local" to 8).forEach { (source, user) ->
                s.enqueue(MockResponse().setBody("""{"id":3,"name":"P","source":"$source","user_id":$user}"""))
                assertTrue(appResult { r.delete(3) } is AppResult.Failure)
                assertEquals("GET", s.takeRequest().method)
                assertNull(s.takeRequest(50,TimeUnit.MILLISECONDS))
            }
        }
    }
    @Test fun ownedPlaylistMutationsUseRealBodiesAndMethods() = runBlocking {
        MockWebServer().use { s ->
            val r=repo(s)
            s.enqueue(MockResponse().setBody("{}")); r.create(" P ","Description")
            assertEquals("P", ApiJson.parseToJsonElement(s.takeRequest().body.readUtf8()).jsonObject["name"]!!.jsonPrimitive.content)
            suspend fun writable() { s.enqueue(MockResponse().setBody("""{"id":3,"name":"P","user_id":7}""")); s.enqueue(MockResponse().setBody("{}")) }
            writable(); r.rename(3,"R"); assertEquals("GET",s.takeRequest().method); assertEquals("PUT",s.takeRequest().method)
            writable(); r.add(3,listOf(Song(2,"Track"))); s.takeRequest()
            val add=s.takeRequest(); assertEquals("POST",add.method); assertEquals(2L,ApiJson.parseToJsonElement(add.body.readUtf8()).jsonObject["songs"]!!.jsonArray[0].jsonObject["ncm_id"]!!.jsonPrimitive.long)
            writable(); r.remove(3,Song(2,"Track")); s.takeRequest(); assertEquals("DELETE",s.takeRequest().method)
        }
    }
    @Test fun accountSwitchDuringPlaylistPreflightCannotWriteToNewSession() = runBlocking {
        MockWebServer().use { s ->
            var token="old"; val url=s.url("/cm/").toString()
            val r=LibraryRepository(ApiClient({url},{token},{}),{7},{RequestSession(url,token)})
            s.dispatcher=object:Dispatcher(){override fun dispatch(request:RecordedRequest):MockResponse {
                token="new";return MockResponse().setBody("""{"id":3,"name":"P","user_id":7}""")
            }}
            assertTrue(appResult { r.add(3,listOf(Song(1,"A"))) } is AppResult.Failure)
            assertEquals("Bearer old",s.takeRequest().getHeader("Authorization"));assertNull(s.takeRequest(50,TimeUnit.MILLISECONDS))
        }
    }
    @Test fun catalogArtistAlbumAndMvUseDocumentedQueryParametersAndNoUrlCache() = runBlocking {
        MockWebServer().use { s ->
            val r=repo(s)
            s.enqueue(MockResponse().setBody("""{"artists":[{"id":12,"name":"Artist"}],"hasMore":{"artist":true}}"""))
            assertTrue(r.catalog("A",false,30).more);val request=s.takeRequest();assertEquals("30",request.requestUrl!!.queryParameter("offset"));assertEquals("artist",request.requestUrl!!.queryParameter("type"));assertNull(request.getHeader("Authorization"))
            s.enqueue(MockResponse().setBody("""{"name":"Artist","songs":[{"ncm_id":1,"name":"A"}],"albums":[{"id":5,"name":"Album"}],"more":true}"""))
            assertEquals(5L,r.artist(12).albums.first().id);s.takeRequest()
            s.enqueue(MockResponse().setBody("""{"name":"Album","songs":[{"ncm_id":1,"name":"A"}]}"""))
            assertEquals(5L,r.album(5).id);s.takeRequest()
            s.enqueue(MockResponse().setBody("""{"data":{"id":8,"name":"MV","duration":1234}}"""))
            assertEquals(1234L,r.mv(8).duration);assertEquals("8",s.takeRequest().requestUrl!!.queryParameter("mvid"))
            repeat(2){s.enqueue(MockResponse().setBody("""{"data":{"url":"https://example.com/video$it"}}"""));assertTrue(r.mvSource(8).endsWith("$it"));assertEquals("1080",s.takeRequest().requestUrl!!.queryParameter("r"))}
        }
    }
    @Test fun statusRequestsAreBatchedAndSessionResetClearsData() = runBlocking {
        MockWebServer().use { s ->
            val r=repo(s);repeat(3){s.enqueue(MockResponse().setBody("{}"))};r.refreshStatus((1L..205L).toList())
            assertEquals(205,r.statuses.value.size);repeat(3){assertTrue(s.takeRequest().requestUrl!!.queryParameter("ids")!!.split(',').size<=100)}
            r.clearSession();assertTrue(r.statuses.value.isEmpty())
        }
    }
    @Test fun listeningReportsAreIncrementalAndVideoDoesNotPolluteSongHistory() = runBlocking {
        MockWebServer().use { s ->
            val r=repo(s);val song=Song(1,"A")
            s.enqueue(MockResponse().setBody("{}"));r.recordPlay(song);assertEquals("A",ApiJson.parseToJsonElement(s.takeRequest().body.readUtf8()).jsonObject["name"]!!.jsonPrimitive.content)
            s.enqueue(MockResponse().setBody("{}"));r.listen(song,1200);val body=ApiJson.parseToJsonElement(s.takeRequest().body.readUtf8()).jsonObject;assertEquals(setOf("ms"),body.keys);assertEquals(1200L,body["ms"]!!.jsonPrimitive.long)
            r.recordPlay(song.copy(video=true));r.listen(song.copy(video=true),3000);assertNull(s.takeRequest(50,TimeUnit.MILLISECONDS))
        }
    }
}
