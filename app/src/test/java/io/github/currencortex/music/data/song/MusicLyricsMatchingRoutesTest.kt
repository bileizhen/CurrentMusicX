package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.binding.BoundProfile
import io.github.currencortex.music.data.settings.LyricsSource
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/** Matching source labels name an actual transport, independently of the chosen audio source. */
class MusicLyricsMatchingRoutesTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val vault = object : TokenStore {
        private var record: String? = null
        override fun read() = record
        override fun write(token: String) { record = token }
        override fun clear() { record = null }
    }
    private lateinit var project: MockWebServer
    private lateinit var native: MockWebServer
    private lateinit var repository: MusicRepository

    @Before fun setup() = runBlocking {
        project = MockWebServer().apply { start() }
        native = MockWebServer().apply { start() }
        val sessions = NeteaseSessionStore(vault, scope)
        sessions.ready.await()
        sessions.login(mapOf("MUSIC_U" to "native-test-cookie"), BoundProfile(42, "Native"), sessions.snapshot())
        val endpoint = native.url("/").toString()
        val gateway = NeteaseGateway(NeteaseTransport(sessions, NeteaseEndpoints(endpoint, endpoint, endpoint)))
        repository = MusicRepository(ApiClient({ project.url("/configured/").toString() }, { "project-test-token" },
            {}, netease = gateway, nativeMedia = { true }))
    }

    @After fun cleanup() {
        scope.cancel()
        project.shutdown()
        native.shutdown()
    }

    @Test fun automaticPolicyCannotSilentlyRouteToTheProjectOrNativeEndpoint() = runBlocking {
        assertTrue(repository.searchForLyricsMatching("Song - Singer", LyricsSource.AUTO) is AppResult.Failure)
        assertTrue(repository.lyricsForMatching(7, LyricsSource.AUTO) is AppResult.Failure)
        assertTrue(repository.lyricsDocument(7, LyricsSource.AUTO) is AppResult.Failure)
        assertEquals(0, project.requestCount)
        assertEquals(0, native.requestCount)
    }

    private fun projectSearch() = MockResponse().setBody("""{"songs":[{"ncm_id":7,"name":"Project candidate","artists":"Singer"}],"totals":{"song":1},"hasMore":{"song":false}}""")
    private fun nativeSearch() = MockResponse().setBody("""{"code":200,"result":{"songs":[{"id":7,"name":"Native candidate","ar":[{"id":8,"name":"Singer"}],"al":{"name":"Album"},"dt":180000}],"songCount":1}}""")

    @Test fun sameCandidateIdSearchesTheExplicitSourceEvenWhenNativeMediaIsSelected() = runBlocking {
        project.enqueue(projectSearch())
        native.enqueue(nativeSearch())
        val fromProject = (repository.searchForLyricsMatching("Song - Singer - Album", LyricsSource.CURRENT_MUSIC) as AppResult.Success).value
        val fromNative = (repository.searchForLyricsMatching("Song - Singer - Album", LyricsSource.NETEASE) as AppResult.Success).value
        assertEquals(7L, fromProject.songs.single().id)
        assertEquals(7L, fromNative.songs.single().id)
        assertEquals("Project candidate", fromProject.songs.single().name)
        assertEquals("Native candidate", fromNative.songs.single().name)
        val projectRequest = project.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/configured/ncm/search", projectRequest.requestUrl!!.encodedPath)
        assertEquals("Song Singer", projectRequest.requestUrl!!.queryParameter("keywords"))
        assertEquals("song", projectRequest.requestUrl!!.queryParameter("type"))
        assertEquals("30", projectRequest.requestUrl!!.queryParameter("limit"))
        assertNull(projectRequest.getHeader("Authorization"))
        assertFalse(projectRequest.getHeader("Cookie").orEmpty().contains("native-test-cookie"))
        val nativeRequest = native.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/eapi/cloudsearch/pc", nativeRequest.path)
        assertEquals("Song Singer", payload(nativeRequest)["s"]!!.jsonPrimitive.content)
        assertNull(nativeRequest.getHeader("Authorization"))
        assertTrue(nativeRequest.getHeader("Cookie").orEmpty().contains("native-test-cookie"))
    }

    @Test fun sameLyricIdLoadsProjectNormalizedTimingOrOfficialNativeLyrics() = runBlocking {
        project.enqueue(MockResponse().setBody("""{"lines":[{"t":1000,"end":3000,"txt":"Project lyric"}]}"""))
        native.enqueue(MockResponse().setBody("""{"code":200,"yrc":{"lyric":"[1000,1000](1000,500,0)Na(1500,500,0)tive"}}"""))
        val fromProject = (repository.lyricsForMatching(7, LyricsSource.CURRENT_MUSIC) as AppResult.Success).value
        val fromNative = (repository.lyricsForMatching(7, LyricsSource.NETEASE) as AppResult.Success).value
        assertEquals("Project lyric", fromProject.lines.single().text)
        assertEquals("Native", fromNative.lines.single().text)
        assertTrue(fromNative.hasWordTiming)
        val projectRequest = project.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/configured/ncm/lyric?id=7", projectRequest.path)
        assertNull(projectRequest.getHeader("Authorization"))
        val nativeRequest = native.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/eapi/song/lyric/v1", nativeRequest.path)
        assertEquals("7", payload(nativeRequest)["id"]!!.jsonPrimitive.content)
        assertNull(nativeRequest.getHeader("Authorization"))
        assertEquals(1, project.requestCount)
        assertEquals(1, native.requestCount)
    }

    @Test fun missingNativeGatewayFailsExplicitlyWithoutCallingTheProject() = runBlocking {
        val noGateway = MusicRepository(ApiClient({ project.url("/configured/").toString() }, { null }, {}))
        project.enqueue(projectSearch())
        val search = noGateway.searchForLyricsMatching("Song", LyricsSource.NETEASE) as AppResult.Failure
        val lyric = noGateway.lyricsForMatching(7, LyricsSource.NETEASE) as AppResult.Failure
        assertEquals(ErrorKind.NotFound, search.kind)
        assertEquals(ErrorKind.NotFound, lyric.kind)
        assertTrue(search.message.contains("网易云"))
        assertEquals(0, project.requestCount)
        assertEquals("Project candidate", (noGateway.searchForLyricsMatching("Song", LyricsSource.CURRENT_MUSIC)
            as AppResult.Success).value.songs.single().name)
    }

    @Test fun nativeEnhancedFallbackStaysOnOfficialEndpoints() = runBlocking {
        native.enqueue(MockResponse().setResponseCode(404))
        native.enqueue(MockResponse().setBody("""{"code":200,"lrc":{"lyric":"[00:01]Native fallback"}}"""))
        val result = repository.lyricsForMatching(7, LyricsSource.NETEASE) as AppResult.Success
        assertEquals("Native fallback", result.value.lines.single().text)
        assertEquals("/eapi/song/lyric/v1", native.takeRequest(1, TimeUnit.SECONDS)!!.path)
        assertEquals("/eapi/song/lyric", native.takeRequest(1, TimeUnit.SECONDS)!!.path)
        assertEquals(0, project.requestCount)
    }

    @Test fun ordinarySearchRetainsItsExistingQueryAndRouting() = runBlocking {
        native.enqueue(nativeSearch())
        repository.search("Song - Singer - Album") as AppResult.Success
        val request = native.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("Song - Singer - Album", payload(request)["s"]!!.jsonPrimitive.content)
        assertEquals(0, project.requestCount)
    }

    @Test fun rejectedSearchResponsesAreFailuresRatherThanEmptySuccess() = runBlocking {
        project.enqueue(MockResponse().setBody("""{"code":503}"""))
        native.enqueue(MockResponse().setBody("""{"code":403}"""))
        val current = repository.searchForLyricsMatching("Song", LyricsSource.CURRENT_MUSIC) as AppResult.Failure
        val official = repository.searchForLyricsMatching("Song", LyricsSource.NETEASE) as AppResult.Failure
        assertEquals(ErrorKind.Server, current.kind)
        assertEquals(503, current.status)
        assertEquals(ErrorKind.Forbidden, official.kind)
        assertEquals(403, official.status)
        assertEquals(1, project.requestCount)
        assertEquals(1, native.requestCount)
    }

    @Test fun cancellingMatchingSearchCancelsTheRequestWithoutTryingAnotherSource() = runBlocking {
        project.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val pending = async(Dispatchers.Default) { repository.searchForLyricsMatching("Song", LyricsSource.CURRENT_MUSIC) }
        withContext(Dispatchers.IO) { assertNotNull(project.takeRequest(2, TimeUnit.SECONDS)) }
        withTimeout(1500) { pending.cancelAndJoin() }
        assertTrue(pending.isCancelled)
        assertEquals(1, project.requestCount)
        assertEquals(0, native.requestCount)
    }

    private fun payload(request: RecordedRequest): JsonObject {
        val encoded = request.body.readUtf8().split('&').associate { part ->
            val values = part.split('=', limit = 2)
            values[0] to URLDecoder.decode(values[1], "UTF-8")
        }.getValue("params")
        val bytes = encoded.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val plain = NeteaseCrypto.decryptResponse(bytes).toString(Charsets.UTF_8)
        return ApiJson.parseToJsonElement(plain.split("-36cd479b6b5-")[1]).jsonObject
    }
}
