package io.github.currencortex.music.core.netease

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.binding.*
import io.github.currencortex.music.data.library.*
import io.github.currencortex.music.data.song.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class NeteaseGatewayTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val vault = object : TokenStore {
        var value: String? = null
        override fun read() = value
        override fun write(token: String) { value = token }
        override fun clear() { value = null }
    }
    private lateinit var upstream: MockWebServer
    private lateinit var current: MockWebServer
    private lateinit var sessions: NeteaseSessionStore
    private lateinit var gateway: NeteaseGateway
    private lateinit var api: ApiClient
    private lateinit var binding: BindingRepository
    private var expired = 0
    private fun owner() = RequestSession(current.url("/cm/").toString(), "current-secret", sessions.state.value.revision)
    private fun response(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")
    private fun enqueue(body: String) = upstream.enqueue(response(body))
    private fun rawSong(id: Long) = """{"id":$id,"name":"Song $id","ar":[{"id":22,"name":"Artist"}],"al":{"name":"Album","picUrl":"http://p1.music.126.net/cover"},"dt":180000}"""
    @Before fun setup() = runBlocking {
        upstream = MockWebServer().apply { start() }; current = MockWebServer().apply { start() }
        sessions = NeteaseSessionStore(vault, scope); sessions.ready.await()
        val endpoint = upstream.url("/").toString()
        gateway = NeteaseGateway(NeteaseTransport(sessions, NeteaseEndpoints(endpoint, endpoint, endpoint)))
        api = ApiClient({ current.url("/cm/").toString() }, { "current-secret" }, { expired++ }, netease = gateway)
        binding = BindingRepository(api, ::owner) {}
    }
    @After fun cleanup() { scope.cancel(); upstream.shutdown(); current.shutdown() }
    private suspend fun login(uid: Long = 42) = sessions.login(mapOf("MUSIC_U" to "native-cookie-$uid"), BoundProfile(uid, "Native $uid"), sessions.snapshot())
    private suspend fun call(path: String, query: Map<String, String> = emptyMap()) = api.request("GET", path, query, authenticated = true, expectedSession = owner())
    private fun payload(request: RecordedRequest): JsonObject {
        val params = request.body.readUtf8().split('&').associate { part -> part.split('=', limit = 2).let { it[0] to URLDecoder.decode(it[1], "UTF-8") } }.getValue("params")
        val bytes = params.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return ApiJson.parseToJsonElement(NeteaseCrypto.decryptResponse(bytes).toString(Charsets.UTF_8).split("-36cd479b6b5-")[1]).jsonObject
    }
    @Test fun searchBypassesCurrentMusicAndPreservesQueryAndArtistMetadata() = runBlocking {
        login(); enqueue("""{"code":200,"result":{"songCount":10,"songs":[${rawSong(11)}]}}""")
        val result = api.decode<SearchDto>(call("ncm/search", mapOf("keywords" to "Die For You", "offset" to "0")))
        assertEquals(11L, result.songs.single().id); assertEquals("Artist", result.songs.single().artists)
        assertEquals(listOf(22L), result.songs.single().artistIds); assertTrue(result.hasMore.song)
        val request = upstream.takeRequest(); assertEquals("/eapi/cloudsearch/pc", request.path)
        assertNull(request.getHeader("Authorization")); assertTrue(request.getHeader("Cookie")!!.contains("MUSIC_U=native-cookie-42"))
        assertFalse(request.getHeader("Cookie")!!.contains("current-secret"))
        assertEquals("Die For You", payload(request)["s"]!!.jsonPrimitive.content)
        assertEquals(0, current.requestCount)
    }
    @Test fun anonymousRecommendationsDoNotNeedEitherAccount() = runBlocking {
        enqueue("""{"code":200,"result":[{"song":${rawSong(11)}}]}""")
        assertEquals(11L, api.decode<DailyDto>(gateway.daily()).daily.single().id)
        assertEquals("/weapi/personalized/newsong", upstream.takeRequest().path)
        assertEquals(0, current.requestCount)
    }
    @Test fun boundRecommendationsAndRecentTracksUseNativeResponses() = runBlocking {
        login(); enqueue("""{"code":200,"data":{"dailySongs":[${rawSong(11)}]}}""")
        enqueue("""{"code":200,"data":[${rawSong(12)}]}""")
        val daily = api.decode<DailyDto>(gateway.daily()); assertEquals(11L, daily.daily.single().id); assertEquals(12L, daily.forYou.single().id)
        enqueue("""{"code":200,"data":{"list":[{"data":${rawSong(13)}}]}}""")
        assertEquals(13L, api.decode<SongListDto>(gateway.recent()).songs.single().id)
        assertEquals(0, current.requestCount)
    }
    @Test fun rawLrcTranslationAndWordTimingReachTheExistingParser() = runBlocking {
        enqueue("""{"code":200,"lrc":{"lyric":"[00:01.00]Hello"},"tlyric":{"lyric":"[00:01.00]你好"},"yrc":{"lyric":"[1000,1000](1000,500,0)Hel(1500,500,0)lo"}}""")
        val result = MusicRepository(api).lyricsDocument(11) as AppResult.Success
        assertEquals("网易云", result.value.metadata.source); assertEquals("你好", result.value.lines.single().translation)
        assertEquals(2, result.value.lines.single().words.size)
        assertEquals("/eapi/song/lyric/v1", upstream.takeRequest().path)
    }
    @Test fun qrConfirmationCommitsLocalSessionAndSurvivesRecreation() = runBlocking {
        val plainOwner = owner().let { RequestSession(it.server, it.token) }
        enqueue("""{"code":200,"unikey":"test-key"}"""); assertEquals("test-key", binding.qrKey(plainOwner))
        enqueue("""{"code":802}"""); assertEquals(802, binding.qrStatus("test-key", plainOwner).code)
        upstream.enqueue(response("""{"code":803,"profile":{"userId":42,"nickname":"Native","avatarUrl":"https://p1.music.126.net/avatar"}}""").addHeader("Set-Cookie", "MUSIC_U=confirmed-native; Path=/; HttpOnly"))
        assertEquals(803, binding.qrStatus("test-key", plainOwner).code)
        assertEquals(42L, binding.status().profile?.uid)
        val restored = NeteaseSessionStore(vault, scope); restored.ready.await()
        assertTrue(restored.state.value.loggedIn); assertEquals(42L, restored.state.value.profile?.uid)
        assertFalse(restored.snapshot().toString().contains("confirmed-native")); assertEquals(0, current.requestCount)
    }
    @Test fun phoneLoginAndUnbindPermitTheirOwnCredentialRevisionChange() = runBlocking {
        upstream.enqueue(response("""{"code":200,"profile":{"userId":42,"nickname":"Native"}}""").addHeader("Set-Cookie", "MUSIC_U=confirmed-native; Path=/"))
        binding.bindPhone("13800000000", "123456", "86", owner())
        assertTrue(binding.status().bound)
        binding.unbind(owner()); assertFalse(binding.status().bound); assertFalse(sessions.state.value.loggedIn)
        assertEquals(1, upstream.requestCount); assertEquals(0, current.requestCount)
    }
    @Test fun sms502IsSingleAttemptAndDoesNotInvalidateCurrentMusic() = runBlocking {
        upstream.enqueue(MockResponse().setResponseCode(502))
        val failure = appResult { binding.sendCode("13800000000", "86", expected = RequestSession(owner().server, owner().token)) } as AppResult.Failure
        assertEquals(ErrorKind.Server, failure.kind); assertEquals(502, failure.status)
        assertEquals(1, upstream.requestCount); assertEquals(0, expired); assertEquals(0, current.requestCount)
    }
    @Test fun privateDataRequiresNeteaseLoginWithoutFallback() = runBlocking {
        val failure = appResult { call("ncm/user/playlist", mapOf("uid" to "42")) } as AppResult.Failure
        assertEquals(ErrorKind.NeteaseBindingRequired, failure.kind); assertEquals(0, upstream.requestCount); assertEquals(0, current.requestCount)
    }
    @Test fun expiredNativeLoginDoesNotLogOutCurrentMusic() = runBlocking {
        login(); enqueue("""{"code":301}""")
        val failure = appResult { call("ncmbind/likelist") } as AppResult.Failure
        assertEquals(ErrorKind.NeteaseBindingRequired, failure.kind); assertTrue(sessions.state.value.stale)
        assertEquals(0, expired); assertEquals(0, current.requestCount)
    }
    @Test fun directPlaylistLoadsEveryTrackInOriginalOrder() = runBlocking {
        login(); val actions = NeteaseSongActionsRepository(api, session = ::owner)
        val library = NeteaseLibraryRepository(api, binding, actions, ::owner)
        enqueue("""{"code":200,"playlist":{"id":88,"name":"Native","creator":{"userId":42},"trackCount":3,"trackIds":[{"id":13},{"id":11},{"id":12}],"tracks":[${rawSong(11)}]}}""")
        enqueue("""{"code":200,"songs":[${rawSong(12)},${rawSong(13)}]}""")
        assertEquals(listOf(13L,11L,12L), library.playlist(88).songs.map { it.id })
        assertEquals(2, upstream.requestCount); assertEquals(0, current.requestCount)
    }
    @Test fun lateOldAccountResponseCannotRestoreCookiesAfterUnbind() = runBlocking {
        login(); val expected = owner()
        upstream.enqueue(response("""{"code":200,"ids":[11]}""").setBodyDelay(350, TimeUnit.MILLISECONDS).addHeader("Set-Cookie", "MUSIC_U=old-session; Path=/"))
        val result = async { appResult { api.request("GET", "ncmbind/likelist", authenticated = true, expectedSession = expected) } }
        withContext(Dispatchers.IO) { upstream.takeRequest(2, TimeUnit.SECONDS) }; sessions.clear()
        assertEquals(ErrorKind.NeteaseSessionChanged, (result.await() as AppResult.Failure).kind)
        assertFalse(sessions.state.value.loggedIn); assertFalse(vault.value!!.contains("old-session"))
    }
    @Test fun staleSessionIsRejectedBeforeStartingAnyRequest() = runBlocking {
        login(); val expected = owner(); sessions.clear()
        val result = appResult { api.request("GET", "ncm/search", expectedSession = expected, authenticated = true) } as AppResult.Failure
        assertEquals(ErrorKind.NeteaseSessionChanged, result.kind); assertEquals(0, upstream.requestCount)
    }
    @Test fun redirectCannotLeakNativeCookieOrCurrentMusicToken() = runBlocking {
        login(); upstream.enqueue(MockResponse().setResponseCode(302).addHeader("Location", current.url("/steal")))
        assertTrue(appResult { call("ncmbind/likelist") } is AppResult.Failure)
        assertEquals(0, current.requestCount); assertEquals(0, expired)
    }
    @Test fun audioRemainsCurrentMusicWithoutSendingTheNativeCookie() = runBlocking {
        login(); current.enqueue(response("""{"url":"https://m.music.126.net/full.mp3","level":"lossless","type":"flac"}"""))
        val source = MusicRepository(api).source(11, io.github.currencortex.music.core.media.AudioQuality.LOSSLESS, owner())
        assertEquals("lossless", source.level); val request = current.takeRequest()
        assertEquals("/cm/ncm/song/url", request.requestUrl!!.encodedPath)
        assertEquals("Bearer current-secret", request.getHeader("Authorization")); assertNull(request.getHeader("Cookie"))
        assertEquals("lossless", request.requestUrl!!.queryParameter("level")); assertEquals(0, upstream.requestCount)
    }
    @Test fun listeningDeltasProduceOneQualifiedListenAndOneStart() = runBlocking {
        login(); enqueue("""{"code":200}"""); enqueue("""{"code":200}""")
        gateway.recordPlay(11)
        repeat(12) { gateway.recordPlay(11, 15_000) }
        assertEquals(2, upstream.requestCount)
        upstream.takeRequest(); val report = upstream.takeRequest()
        assertEquals("/eapi/feedback/weblog", report.path)
        val event = ApiJson.parseToJsonElement(payload(report)["logs"]!!.jsonPrimitive.content).jsonArray.single().jsonObject
        assertEquals("play", event["action"]!!.jsonPrimitive.content)
        assertEquals(60L, event["json"]!!.jsonObject["time"]!!.jsonPrimitive.long)
    }
    @Test fun ownPlaylistRenameUsesNativeOwnerAndEndpoint() = runBlocking {
        login(); val actions = NeteaseSongActionsRepository(api, session = ::owner)
        val library = NeteaseLibraryRepository(api, binding, actions, ::owner)
        enqueue("""{"code":200,"playlist":[{"id":88,"name":"Old","creator":{"userId":42}}]}""")
        enqueue("""{"code":200}"""); library.rename(88, "New name")
        upstream.takeRequest(); val write = upstream.takeRequest()
        assertEquals("/eapi/playlist/update/name", write.path); assertEquals("New name", payload(write)["name"]!!.jsonPrimitive.content)
        assertEquals(0, current.requestCount)
    }
    @Test fun foreignAndLikedPlaylistsCannotBeRenamedOrDeleted() = runBlocking {
        login(); val library = NeteaseLibraryRepository(api, binding, NeteaseSongActionsRepository(api, session = ::owner), ::owner)
        val page = """{"code":200,"playlist":[{"id":88,"specialType":5,"creator":{"userId":42}},{"id":89,"creator":{"userId":99}}]}"""
        enqueue(page); assertEquals(ErrorKind.Forbidden, (appResult { library.delete(88) } as AppResult.Failure).kind)
        enqueue(page); assertEquals(ErrorKind.Forbidden, (appResult { library.rename(89, "Foreign") } as AppResult.Failure).kind)
        assertEquals(2, upstream.requestCount); assertEquals(0, current.requestCount)
    }
    @Test fun removingTrackFromOwnPlaylistUsesOfficialDeleteOperation() = runBlocking {
        login(); val library = NeteaseLibraryRepository(api, binding, NeteaseSongActionsRepository(api, session = ::owner), ::owner)
        enqueue("""{"code":200,"playlist":[{"id":88,"creator":{"userId":42}}]}"""); enqueue("""{"code":200}""")
        library.remove(88, Song(11, "Native")); upstream.takeRequest()
        val data = payload(upstream.takeRequest()); assertEquals("del", data["op"]!!.jsonPrimitive.content)
        assertEquals("[\"11\"]", data["trackIds"]!!.jsonPrimitive.content)
        assertEquals(0, current.requestCount)
    }
    @Test fun canceledNetworkCallDoesNotPublishLateCredentialUpdates() = runBlocking {
        upstream.enqueue(response("""{"code":200,"unikey":"ignored"}""").setBodyDelay(400, TimeUnit.MILLISECONDS).addHeader("Set-Cookie", "MUSIC_U=ignored; Path=/"))
        val task = launch { call("ncmbind/qr/key") }
        withContext(Dispatchers.IO) { upstream.takeRequest(2, TimeUnit.SECONDS) }; task.cancelAndJoin()
        delay(450); assertFalse(sessions.state.value.loggedIn); assertFalse(vault.value!!.contains("ignored"))
    }
}
