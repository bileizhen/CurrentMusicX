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
    @Test fun chorusUsesPublicNormalizedProjectMetadataEvenWithNativeGateway() = runBlocking {
        current.enqueue(response("""{"id":33894312,"segments":[{"startMs":47500,"endMs":88500}]}"""))
        val clip = MusicRepository(api).previewClip(Song(33894312, "Sample", durationMs = 180000))
        assertEquals(SongPreviewClip(47500, 67500, true), clip)
        val request = current.takeRequest()
        assertEquals("/cm/song/chorus?id=33894312", request.path)
        assertNull(request.getHeader("Authorization"))
        assertNull(upstream.takeRequest(50, TimeUnit.MILLISECONDS))
    }
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
    @Test fun bindingImportRejectsAStaleLocalLoginBeforeHttp() = runBlocking {
        login()
        val previous = owner()
        login(43)
        val result = appResult {
            api.request("POST", "ncmbind/import", body = buildJsonObject {
                put("cookie", "MUSIC_U=old-device-login"); put("expectedNcmUid", 42)
            }, authenticated = true, expectedSession = previous, projectOnly = true)
        }
        assertTrue(result is AppResult.Failure)
        assertEquals(0, current.requestCount)
    }
    @Test fun bindingImportAlwaysUsesAuthenticatedProjectServer() = runBlocking {
        login()
        current.enqueue(response("""{"ok":true,"profile":{"uid":42}}"""))
        api.request("POST", "ncmbind/import", body = buildJsonObject {
            put("cookie", "MUSIC_U=example-device-login"); put("expectedNcmUid", 42)
        }, authenticated = true, expectedSession = owner(), projectOnly = true, retryConnection = false)
        val request = current.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/cm/ncmbind/import", request.path)
        assertEquals("Bearer current-secret", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("expectedNcmUid"))
        assertEquals(0, upstream.requestCount)
    }
    private suspend fun login(uid: Long = 42) = sessions.login(mapOf("MUSIC_U" to "native-cookie-$uid"), BoundProfile(uid, "Native $uid"), sessions.snapshot())
    private suspend fun call(path: String, query: Map<String, String> = emptyMap()) = api.request("GET", path, query, authenticated = true, expectedSession = owner())
    private fun nativeApi(): ApiClient {
        // Any accidental project fallback must fail immediately rather than stall the test.
        current.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        return ApiClient({ current.url("/cm/").toString() }, { "current-secret" }, { expired++ },
            netease = gateway, nativeMedia = { true })
    }

    @Test fun searchChartsUseOriginalSearchHomepageProtocolAndOpaqueIds(): Unit = runBlocking {
        login()
        enqueue("""{"code":200,"data":{"tabInfo":{"list":[{"id":"HOT_SEARCH_SONG#@#","name":"热搜榜"}]},"items":{"HOT_SEARCH_SONG#@#":[{"searchWord":"Song","toUserWord":"Visible label"}]}}}""")
        val chart = io.github.currencortex.music.feature.search.parseSearchCharts(call("ncm/search/chart/list"))
        assertEquals("HOT_SEARCH_SONG#@#", chart.charts.single().id)
        assertEquals("Song", chart.pages.values.single().words.single().text)
        assertEquals("Visible label", chart.pages.values.single().words.single().displayText)
        assertEquals("/eapi/search/chart/list", upstream.takeRequest().path)
        enqueue("""{"code":200,"data":{"itemList":[{"searchWord":"ACG word"}]}}""")
        val words = io.github.currencortex.music.feature.search.parseSearchChartWords(
            call("ncm/search/chart/detail", mapOf("id" to "HOT_SEARCH_STYLE_ACG#@#")))
        assertEquals("ACG word", words.single().text)
        assertEquals("/eapi/search/chart/detail", upstream.takeRequest().path)
        assertEquals(0, current.requestCount)
    }

    @Test fun explicitProjectLyricsBypassNativeAudioAndNeverSendAccountTokens(): Unit = runBlocking {
        val repository = MusicRepository(ApiClient({ current.url("/cm/").toString() }, { "current-secret" },
            { expired++ }, netease = gateway, nativeMedia = { true }))
        current.enqueue(response("""{"lines":[{"t":1000,"txt":"LDDC","w":[[1000,"LD"],[1700,"DC"]],"end":3000}],"source":"lddc"}"""))
        val result = repository.lyricsDocument(7, io.github.currencortex.music.data.settings.LyricsSource.CURRENT_MUSIC)
        val document = (result as AppResult.Success).value
        assertEquals("LDDC", document.lines.single().text)
        assertTrue(document.hasWordTiming)
        val request = current.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/cm/ncm/lyric?id=7", request.path)
        assertNull(request.getHeader("Authorization"))
        assertFalse(request.getHeader("Cookie").orEmpty().contains("native-cookie"))
        assertEquals(0, upstream.requestCount)
        assertEquals(0, expired)
    }

    @Test fun nativeRecommendationsDoNotUseProjectLoginAsAnImplicitServerFallback(): Unit = runBlocking {
        enqueue("""{"code":200,"result":[{"song":${rawSong(11)}}]}""")
        val library = LibraryRepository(nativeApi(), { 7L }, ::owner)
        assertEquals(11L, library.daily().songs.single().id)
        assertEquals("/weapi/personalized/newsong", upstream.takeRequest().path)
        assertTrue(library.recent().isEmpty())
        assertEquals(0, current.requestCount)
    }

    @Test fun nativeSourceUsesOnlyNeteaseAndReturnsItsActualQualityAndLifetime(): Unit = runBlocking {
        login()
        enqueue("""{"code":200,"data":[{"id":11,"url":"https://m10.music.126.net/song.flac","level":"lossless","sampleRate":48000,"channelCount":2,"type":"flac","expi":1200}]}""")
        val repository = MusicRepository(nativeApi(), { io.github.currencortex.music.data.settings.AudioSourceAccess(io.github.currencortex.music.data.settings.AudioProvider.NETEASE) })
        val source = repository.source(11, io.github.currencortex.music.core.media.AudioQuality.AUTO,
            RequestSession("https://music.163.com/", null, sessions.state.value.revision))
        assertEquals("lossless", source.level); assertEquals(48000, source.sampleRate)
        assertEquals(2, source.channelCount); assertEquals(1200L, source.validForSeconds)
        val request = upstream.takeRequest()
        assertEquals("/eapi/song/enhance/player/url/v1", request.path)
        val data = payload(request)
        assertEquals("[11]", data["ids"]!!.jsonPrimitive.content)
        assertEquals("jymaster", data["level"]!!.jsonPrimitive.content)
        assertTrue(request.getHeader("Cookie")!!.contains("MUSIC_U=native-cookie-42"))
        assertNull(request.getHeader("Authorization")); assertEquals(0, current.requestCount)
    }

    @Test fun manualQualityLevelsReachUpstreamWithoutAliasOrAssumedResponseQuality(): Unit = runBlocking {
        val repository = MusicRepository(nativeApi(), { io.github.currencortex.music.data.settings.AudioSourceAccess(io.github.currencortex.music.data.settings.AudioProvider.NETEASE) })
        for (quality in io.github.currencortex.music.core.media.AudioQuality.entries.filter { it != io.github.currencortex.music.core.media.AudioQuality.AUTO }) {
            enqueue("""{"code":200,"data":[{"id":11,"url":"https://example.com/song.flac","level":"lossless"}]}""")
            val source = repository.source(11, quality,
                RequestSession("https://music.163.com/", null, sessions.state.value.revision))
            assertEquals(quality.value, payload(upstream.takeRequest())["level"]!!.jsonPrimitive.content)
            assertEquals("lossless", source.level)
        }
        assertEquals(0, current.requestCount)
    }

    @Test fun nativeModeReadsLyricsAndMvAnonymouslyWithoutProjectFallback(): Unit = runBlocking {
        val native = nativeApi()
        enqueue("""{"code":200,"yrc":{"lyric":"[1000,1000](1000,500,0)Hel(1500,500,0)lo"}}""")
        val document = (MusicRepository(native).lyricsDocument(11) as AppResult.Success).value
        assertTrue(document.hasWordTiming)
        assertEquals("/eapi/song/lyric/v1", upstream.takeRequest().path)
        enqueue("""{"code":200,"data":{"url":"https://vod.126.net/video.mp4"}}""")
        assertEquals("https://vod.126.net/video.mp4", LibraryRepository(native, { 0L }, ::owner).mvSource(55, 720))
        assertEquals("/weapi/song/enhance/play/mv/url", upstream.takeRequest().path)
        enqueue("""{"code":404}""")
        assertEquals(ErrorKind.NotFound, (appResult { native.request("GET", "ncm/mv/url", mapOf("id" to "55")) } as AppResult.Failure).kind)
        assertEquals(0, current.requestCount); assertEquals(0, expired)
    }

    @Test fun deniedNativeSourcesNeverFallBackAndAccountSwitchRejectsAnInflightUrl(): Unit = runBlocking {
        login()
        var access = io.github.currencortex.music.data.settings.AudioSourceAccess(io.github.currencortex.music.data.settings.AudioProvider.NETEASE)
        val repository = MusicRepository(nativeApi(), { access })
        enqueue("""{"code":200,"data":[{"id":11,"url":null}]}""")
        assertEquals(ErrorKind.NeteaseAudioUnavailable, (appResult { repository.source(11, io.github.currencortex.music.core.media.AudioQuality.STANDARD) } as AppResult.Failure).kind)
        upstream.takeRequest()
        upstream.enqueue(response("""{"code":200,"data":[{"id":11,"url":"https://m10.music.126.net/song.mp3"}]}""").setBodyDelay(200, TimeUnit.MILLISECONDS))
        val old = RequestSession("https://music.163.com/", null, sessions.state.value.revision)
        val pending = async { appResult { repository.source(11, io.github.currencortex.music.core.media.AudioQuality.STANDARD, old) } }
        withContext(Dispatchers.IO) { assertNotNull(upstream.takeRequest(2, TimeUnit.SECONDS)) }
        login(99)
        assertEquals(ErrorKind.NeteaseSessionChanged, (pending.await() as AppResult.Failure).kind)
        upstream.enqueue(response("""{"code":200,"data":[{"id":11,"url":"https://m10.music.126.net/song.mp3"}]}""").setBodyDelay(200, TimeUnit.MILLISECONDS))
        val changingSource = async { appResult { repository.source(11, io.github.currencortex.music.core.media.AudioQuality.STANDARD) } }
        withContext(Dispatchers.IO) { assertNotNull(upstream.takeRequest(2, TimeUnit.SECONDS)) }
        access = io.github.currencortex.music.data.settings.AudioSourceAccess(io.github.currencortex.music.data.settings.AudioProvider.CURRENT_MUSIC)
        assertEquals(ErrorKind.AudioSourceChanged, (changingSource.await() as AppResult.Failure).kind)
        assertEquals(0, current.requestCount)
    }
    private fun payload(request: RecordedRequest): JsonObject {
        val params = request.body.readUtf8().split('&').associate { part -> part.split('=', limit = 2).let { it[0] to URLDecoder.decode(it[1], "UTF-8") } }.getValue("params")
        val bytes = params.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return ApiJson.parseToJsonElement(NeteaseCrypto.decryptResponse(bytes).toString(Charsets.UTF_8).split("-36cd479b6b5-")[1]).jsonObject
    }
    @Test fun accountMembershipUsesCurrentNativeAccountAndAndroidProfileWithoutProxyCredentials() = runBlocking {
        login()
        enqueue("""{"code":200,"data":{"userId":42,"redVipLevel":6,"redplus":{"vipCode":300,"expireTime":9999999999999}}}""")
        assertEquals("黑胶 SVIP · Lv.6", binding.membership().label)
        val request = upstream.takeRequest()
        assertEquals("/eapi/music-vip-membership/client/vip/info", request.path)
        assertTrue(request.getHeader("Cookie")!!.contains("os=android"))
        assertNull(request.getHeader("Authorization"))
        assertEquals(42L, payload(request)["userId"]!!.jsonPrimitive.long)
        assertEquals(0, current.requestCount)
        enqueue("""{"code":200,"data":{"userId":99}}""")
        assertEquals(ErrorKind.Parse, (appResult { binding.membership() } as AppResult.Failure).kind)
    }
    @Test fun earnedMedalsBelongToTheCurrentNativeAccountAndNeverUseProxyCredentials(): Unit = runBlocking {
        assertTrue(appResult { binding.medals() } is AppResult.Failure)
        assertEquals(0, upstream.requestCount)
        login()
        enqueue("""{"code":200,"data":{"medalNum":1,"obtainMedals":[{"medalCode":1,"medalName":"音乐达人","medalPicUrl":"https://p1.music.126.net/m.png"}]}}""")
        val medals = binding.medals()
        assertEquals(42L, medals.userId)
        assertEquals("音乐达人", medals.medals.single().name)
        val request = upstream.takeRequest()
        assertEquals("/eapi/medal/user/page", request.path)
        assertEquals(42L, payload(request)["uid"]!!.jsonPrimitive.long)
        assertNull(request.getHeader("Authorization"))
        assertEquals(0, current.requestCount)
        upstream.enqueue(response("""{"code":200,"data":{"obtainMedals":[]}}""").setBodyDelay(300, TimeUnit.MILLISECONDS))
        val pending = async { appResult { binding.medals() } }
        withContext(Dispatchers.IO) { assertNotNull(upstream.takeRequest(2, TimeUnit.SECONDS)) }
        sessions.clear()
        assertTrue(pending.await() is AppResult.Failure)
    }
    @Test fun membershipCannotBeReadAnonymouslyOrPublishedAfterLogout() = runBlocking {
        assertTrue(appResult { binding.membership() } is AppResult.Failure)
        assertEquals(0, upstream.requestCount)
        login()
        upstream.enqueue(response("""{"code":200,"data":{"userId":42}}""").setBodyDelay(300, TimeUnit.MILLISECONDS))
        val pending = async { appResult { binding.membership() } }
        withContext(Dispatchers.IO) { assertNotNull(upstream.takeRequest(2, TimeUnit.SECONDS)) }
        sessions.clear()
        assertTrue(pending.await() is AppResult.Failure)
    }
    @Test fun playlistReferenceSearchUsesNativeCloudSearchAndKeepsThePlaylistNamespace() = runBlocking {
        login()
        enqueue("""{"code":200,"result":{"playlistCount":1,"playlists":[{"id":42,"name":"歌单","coverImgUrl":"https://example.com/p.jpg","creator":{"nickname":"作者"},"trackCount":80}]}}""")
        val result = call("ncm/search", mapOf("keywords" to "歌单", "type" to "playlist")).jsonObject
        val card = result["playlists"]!!.jsonArray.single().jsonObject
        assertEquals(42L, card["id"]!!.jsonPrimitive.long)
        assertEquals("作者", card["creator"]!!.jsonPrimitive.content)
        assertEquals(80, card["trackCount"]!!.jsonPrimitive.int)
        assertEquals("/eapi/cloudsearch/pc", upstream.takeRequest().path)
        assertEquals(0, current.requestCount)
    }
    @Test fun mobileSuggestionsAreAnonymousNativeRequestsAndRemoveEmptyDuplicates() = runBlocking {
        enqueue("""{"code":200,"result":{"allMatch":[{"keyword":" 海阔天空 "},{"keyword":"海阔天空"},{"keyword":" "},{"keyword":"海阔天空 live"}]}}""")
        val result = MusicRepository(api).searchSuggestions("海阔") as AppResult.Success
        assertEquals(listOf("海阔天空", "海阔天空 live"), result.value)
        val request = upstream.takeRequest()
        assertEquals("/weapi/search/suggest/keyword", request.path)
        assertNull(request.getHeader("Authorization"))
        assertEquals(0, current.requestCount)
    }
    @Test fun lyricSearchPrefersHighlightedLinesFromTheOfficialArrayShape() = runBlocking {
        val hit = rawSong(11).dropLast(1) + """, "lyrics":["context", "<b>命中歌词</b>", "<b>命中歌词</b>", "after"]}"""
        enqueue("""{"code":200,"result":{"songCount":1,"songs":[$hit]}}""")
        val result = MusicRepository(api).searchLyrics("命中歌词") as AppResult.Success
        val match = result.value.matchedLyrics[11]!!
        assertEquals("context", match.before)
        assertEquals("命中歌词", match.matched)
        assertEquals("after", match.after)
        assertEquals(0, current.requestCount)
    }
    @Test fun lyricSearchUsesOfficialTypeAndPreservesReadableMatchMetadata() = runBlocking {
        val hit = rawSong(11).dropLast(1) + """, "lyrics":{"txt":"<b>你好</b> &amp; 世界\n下一句"}}"""
        enqueue("""{"code":200,"result":{"songCount":31,"songs":[$hit]}}""")
        val result = MusicRepository(api).searchLyrics("你好", 30) as AppResult.Success
        val match = result.value.matchedLyrics[11]!!
        assertNull(match.before)
        assertEquals("你好 & 世界", match.matched)
        assertEquals("下一句", match.after)
        assertEquals("Artist", result.value.songs.single().artists)
        assertFalse(result.value.hasMore)
        val request = upstream.takeRequest()
        val sent = payload(request)
        assertEquals(1006, sent["type"]!!.jsonPrimitive.int)
        assertEquals(30, sent["offset"]!!.jsonPrimitive.int)
        assertEquals(0, current.requestCount)
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
        enqueue("""{"code":200,"data":[]}""")
        val daily = api.decode<DailyDto>(gateway.daily()); assertEquals(11L, daily.daily.single().id); assertEquals(12L, daily.forYou.single().id)
        enqueue("""{"code":200,"data":{"list":[{"data":${rawSong(13)}}]}}""")
        assertEquals(13L, api.decode<SongListDto>(gateway.recent()).songs.single().id)
        assertEquals(0, current.requestCount)
    }
    @Test fun homeRecommendationsCollectFourBatchesWithExclusionsAndKeepServerOrder() = runBlocking {
        login()
        enqueue("""{"code":200,"data":{"dailySongs":[${rawSong(99)}]}}""")
        for (batch in 0 until 4) enqueue("""{"code":200,"data":[${(1L + batch * 3..3L + batch * 3).joinToString(",") { rawSong(it) }}]}""")
        val daily = api.decode<DailyDto>(gateway.daily())
        assertEquals((1L..12L).toList(), daily.forYou.map { it.id })
        assertEquals(listOf(99L), daily.daily.map { it.id })
        assertEquals("/weapi/v3/discovery/recommend/songs", upstream.takeRequest().path)
        for (batch in 0 until 4) {
            val request = upstream.takeRequest()
            assertEquals("/eapi/v1/radio/get", request.path)
            assertNull(request.getHeader("Authorization"))
            val sent = payload(request)
            assertEquals("DEFAULT", sent["mode"]!!.jsonPrimitive.content)
            assertEquals(3, sent["limit"]!!.jsonPrimitive.int)
            assertEquals((1L until 1L + batch * 3).joinToString(","), sent["unplaySongIds"]!!.jsonPrimitive.content)
            assertEquals("android", sent["header"]!!.jsonObject["os"]!!.jsonPrimitive.content)
        }
        assertEquals(5, upstream.requestCount)
        assertEquals(0, current.requestCount)
    }
    @Test fun homeRecommendationsStopWhenABatchAddsNoNewSongs() = runBlocking {
        login()
        enqueue("""{"code":200,"data":{"dailySongs":[${rawSong(99)}]}}""")
        repeat(2) { enqueue("""{"code":200,"data":[${rawSong(1)},${rawSong(1)},${rawSong(2)}]}""") }
        assertEquals(listOf(1L, 2L), api.decode<DailyDto>(gateway.daily()).forYou.map { it.id })
        assertEquals(3, upstream.requestCount)
    }
    @Test fun homeRecommendationsKeepCompletedBatchesWhenTheNextRequestFails() = runBlocking {
        login()
        enqueue("""{"code":200,"data":{"dailySongs":[${rawSong(99)}]}}""")
        enqueue("""{"code":200,"data":[${rawSong(1)},${rawSong(2)},${rawSong(3)}]}""")
        enqueue("""{"code":429}""")
        assertEquals(listOf(1L, 2L, 3L), api.decode<DailyDto>(gateway.daily()).forYou.map { it.id })
        assertEquals(3, upstream.requestCount)
    }
    @Test fun homeRecommendationsBoundRequestsEvenWhenEachBatchIsShort() = runBlocking {
        login()
        enqueue("""{"code":200,"data":{"dailySongs":[${rawSong(99)}]}}""")
        for (id in 1L..4L) enqueue("""{"code":200,"data":[${rawSong(id)}]}""")
        assertEquals((1L..4L).toList(), api.decode<DailyDto>(gateway.daily()).forYou.map { it.id })
        assertEquals(5, upstream.requestCount)
    }
    @Test fun homeRecommendationsKeepCompletedBatchesAfterTheSharedDeadline() = runBlocking {
        login()
        enqueue("""{"code":200,"data":{"dailySongs":[${rawSong(99)}]}}""")
        enqueue("""{"code":200,"data":[${rawSong(1)}]}""")
        upstream.enqueue(response("""{"code":200,"data":[${rawSong(2)}]}""").setBodyDelay(4, TimeUnit.SECONDS))
        val started = System.nanoTime()
        assertEquals(listOf(1L), api.decode<DailyDto>(gateway.daily()).forYou.map { it.id })
        assertTrue("The whole FM refill shares one deadline", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 3_800)
        assertEquals(3, upstream.requestCount)
    }
    @Test fun homeRecommendationsDiscardEarlierBatchesWhenTheAccountChanges() = runBlocking {
        login()
        enqueue("""{"code":200,"data":{"dailySongs":[${rawSong(99)}]}}""")
        enqueue("""{"code":200,"data":[${rawSong(1)}]}""")
        upstream.enqueue(response("""{"code":200,"data":[${rawSong(2)}]}""").setBodyDelay(300, TimeUnit.MILLISECONDS))
        val pending = async { appResult { gateway.daily() } }
        withContext(Dispatchers.IO) { repeat(3) { assertNotNull(upstream.takeRequest(2, TimeUnit.SECONDS)) } }
        login(43)
        assertEquals(ErrorKind.NeteaseSessionChanged, (pending.await() as AppResult.Failure).kind)
        assertEquals(0, current.requestCount)
    }
    @Test fun lyricsUseNativeWordTimingAndAuxiliaryTextEvenWithProjectAudio() = runBlocking {
        login()
        enqueue("""{"code":200,"lrc":{"lyric":"[00:01.00]Hello"},"tlyric":{"lyric":"[00:01.00]你好"},"yromalrc":{"lyric":"[00:01.00]test romanization"},"yrc":{"lyric":"[1000,1000](1000,500,0)Hel(1500,500,0)lo"}}""")
        val result = MusicRepository(api).lyricsDocument(11) as AppResult.Success
        assertEquals("CurrentMusic", result.value.metadata.source); assertEquals("你好", result.value.lines.single().translation)
        assertEquals("test romanization", result.value.lines.single().romanization)
        assertEquals(2, result.value.lines.single().words.size)
        val request = upstream.takeRequest()
        assertEquals("/eapi/song/lyric/v1", request.path)
        assertEquals("11", payload(request)["id"]!!.jsonPrimitive.content)
        assertNull(request.getHeader("Authorization"))
        assertEquals(0, current.requestCount)
    }
    @Test fun lyricFallbackAndLegacyEntryStayOnNativeNetease() = runBlocking {
        login()
        upstream.enqueue(MockResponse().setResponseCode(404))
        val body = """{"code":200,"lrc":{"lyric":"[00:01.00]Hello"},"tlyric":{"lyric":"[00:01.00]你好"},"romalrc":{"lyric":"[00:01.00]test romanization"}}"""
        enqueue(body)
        val repository = MusicRepository(api)
        val document = (repository.lyricsDocument(11) as AppResult.Success).value
        assertEquals("你好", document.lines.single().translation)
        assertEquals("test romanization", document.lines.single().romanization)
        enqueue(body)
        assertEquals("Hello", (repository.lyrics(11) as AppResult.Success).value.single().txt)
        for (path in listOf("/eapi/song/lyric/v1", "/eapi/song/lyric", "/eapi/song/lyric")) {
            val request = upstream.takeRequest()
            assertEquals(path, request.path)
            assertNull(request.getHeader("Authorization"))
        }
        assertEquals(0, current.requestCount)
    }
    @Test fun nativeLyricFailureNeverFallsBackToProjectEstimatedLyrics() = runBlocking {
        upstream.enqueue(MockResponse().setResponseCode(503))
        enqueue("""{"code":500,"lrc":{"lyric":"[00:01.00]Rejected"}}""")
        val result = MusicRepository(api).lyricsDocument(11) as AppResult.Failure
        assertEquals(ErrorKind.Server, result.kind)
        assertEquals(500, result.status)
        assertEquals(2, upstream.requestCount)
        assertEquals(0, current.requestCount)
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
        binding.unbind(owner()); assertFalse(binding.state.value!!.bound); assertFalse(sessions.state.value.loggedIn)
        assertEquals(1, upstream.requestCount); assertEquals(0, current.requestCount)
    }
    @Test fun sms502IsSingleAttemptAndDoesNotInvalidateCurrentMusic() = runBlocking {
        upstream.enqueue(MockResponse().setResponseCode(502))
        val failure = appResult { binding.sendCode("13800000000", "86", expected = RequestSession(owner().server, owner().token)) } as AppResult.Failure
        assertEquals(ErrorKind.Server, failure.kind); assertEquals(502, failure.status)
        assertEquals(1, upstream.requestCount); assertEquals(0, expired); assertEquals(0, current.requestCount)
    }
    @Test fun cloudBindingAndLibraryWorkWithoutCopyingCredentialsToTheDevice() = runBlocking {
        current.enqueue(response("""{"bound":true,"profile":{"uid":700,"nickname":"Cloud account"}}"""))
        assertEquals(700L, binding.status().profile!!.uid)
        current.enqueue(response("""{"code":200,"playlist":[]}"""))
        call("ncm/user/playlist", mapOf("uid" to "700"))
        repeat(2) {
            val request = current.takeRequest()
            assertEquals("Bearer current-secret", request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
        }
        assertFalse(sessions.state.value.loggedIn)
        assertFalse(vault.value!!.contains("MUSIC_U"))
        assertEquals(0, upstream.requestCount)
    }
    @Test fun unboundCloudAccountDoesNotBecomeANativeLoginOrLogOutCurrentMusic() = runBlocking {
        current.enqueue(response("""{"bound":false}"""))
        assertFalse(binding.status().bound)
        current.enqueue(MockResponse().setResponseCode(401).setBody("需要绑定网易云账号"))
        assertTrue(appResult { call("ncmbind/likelist") } is AppResult.Failure)
        assertEquals(0, expired); assertEquals(0, upstream.requestCount)
    }
    @Test fun localBindingTakesPriorityButSnapshotSyncStillUsesTheProjectServer() = runBlocking {
        login()
        assertEquals(42L, binding.status().profile!!.uid)
        assertEquals(0, current.requestCount)
        current.enqueue(response("""{"imported":2,"tracks":10}"""))
        assertEquals(2, binding.sync(owner()).imported)
        val request = current.takeRequest()
        assertEquals("/cm/ncmbind/sync", request.path)
        assertEquals("Bearer current-secret", request.getHeader("Authorization"))
        assertNull(request.getHeader("Cookie")); assertEquals(0, upstream.requestCount)
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
    @Test fun protectedMediaFamiliesAndMvResolutionUseProjectAuthentication(): Unit = runBlocking {
        login()
        for (path in listOf("ncm/song/url/v1", "ncm/song/url/match", "ncm/mv/url", "ncm/video/url", "ncm/song/cloud/download", "ncm/voice/detail"))
            assertFalse(path, gateway.handles(path))
        current.enqueue(response("""{"data":{"url":"https://example.com/video.mp4"}}"""))
        val library = LibraryRepository(api, { 7 }, ::owner)
        library.mvSource(11)
        val request = current.takeRequest()
        assertEquals("/cm/ncm/mv/url", request.requestUrl!!.encodedPath)
        assertEquals("Bearer current-secret", request.getHeader("Authorization"))
        assertNull(request.getHeader("Cookie")); assertEquals(0, upstream.requestCount)
    }
    @Test fun commentsAndRepliesUseLocalCredentialsOnceAndNeverLeakTheProjectToken(): Unit = runBlocking {
        login()
        val repo = NeteaseSongActionsRepository(api, session = ::owner)
        for (reply in listOf(null, 90L)) {
            enqueue("""{"code":200}""")
            repo.sendComment(11, " 测试内容 ", reply)
            val request = upstream.takeRequest()
            assertEquals("/eapi/resource/comments/${if (reply == null) "add" else "reply"}", request.path)
            assertNull(request.getHeader("Authorization"))
            val body = payload(request)
            assertEquals("R_SO_4_11", body["threadId"]!!.jsonPrimitive.content)
            assertEquals("测试内容", body["content"]!!.jsonPrimitive.content)
            assertEquals(reply, body["commentId"]?.jsonPrimitive?.longOrNull)
        }
        upstream.enqueue(MockResponse().setResponseCode(502))
        assertTrue(appResult { repo.sendComment(11, "不可自动重发") } is AppResult.Failure)
        assertEquals(3, upstream.requestCount); assertEquals(0, current.requestCount)
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
    @Test fun nativeLibraryStillReportsCurrentMusicPresenceAndIncrementalStatistics() = runBlocking {
        val library = LibraryRepository(api, { 7 }, ::owner)
        val song = Song(11, "Track")
        current.enqueue(response("{}")); library.recordPlay(song, owner())
        val start = current.takeRequest()
        assertEquals("/cm/plays/11", start.path)
        assertEquals("Bearer current-secret", start.getHeader("Authorization"))
        assertNull(start.getHeader("Cookie"))
        assertEquals("Track", ApiJson.parseToJsonElement(start.body.readUtf8()).jsonObject["name"]!!.jsonPrimitive.content)
        current.enqueue(response("{}")); library.listen(song, 15_000, owner())
        val delta = current.takeRequest()
        assertEquals("{\"ms\":15000}", delta.body.readUtf8())
        assertEquals(0, upstream.requestCount)
    }
    @Test fun failedQualifiedListenCanBeSubmittedWithTheNextDelta() = runBlocking {
        login(); enqueue("""{"code":200}"""); gateway.recordPlay(11)
        upstream.enqueue(response("""{"code":500}"""))
        assertTrue(appResult { gateway.recordPlay(11, 60_000) } is AppResult.Failure)
        enqueue("""{"code":200}"""); gateway.recordPlay(11, 15_000)
        gateway.recordPlay(11, 15_000)
        assertEquals(3, upstream.requestCount)
        upstream.takeRequest(); upstream.takeRequest()
        val event = ApiJson.parseToJsonElement(payload(upstream.takeRequest())["logs"]!!.jsonPrimitive.content).jsonArray.single().jsonObject
        assertEquals(75L, event["json"]!!.jsonObject["time"]!!.jsonPrimitive.long)
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
    @Test fun subscribedPlaylistRemovalUsesUnsubscribeAndInvalidatesTheLibrary() = runBlocking {
        login(); val library = NeteaseLibraryRepository(api, binding, NeteaseSongActionsRepository(api, session = ::owner), ::owner)
        enqueue("""{"code":200,"playlist":[{"id":89,"subscribed":true,"creator":{"userId":99}}]}""")
        assertTrue(library.playlists().single().nativeSubscribed)
        enqueue("""{"code":200,"playlist":[{"id":89,"subscribed":true,"creator":{"userId":99}}]}""")
        enqueue("""{"code":200}""")
        library.unsubscribe(89)
        upstream.takeRequest(); upstream.takeRequest()
        val write = upstream.takeRequest()
        assertEquals("/eapi/playlist/unsubscribe", write.path)
        assertEquals("89", payload(write)["id"]!!.jsonPrimitive.content)
        assertEquals(1L, library.revision.value)
        enqueue("""{"code":200,"playlist":[]}""")
        assertTrue(library.playlists().isEmpty())
        assertEquals(0, current.requestCount)
    }
    @Test fun unsubscribingRejectsOwnedLikedAndUnlistedPlaylistsWithoutWriting() = runBlocking {
        login(); val library = NeteaseLibraryRepository(api, binding, NeteaseSongActionsRepository(api, session = ::owner), ::owner)
        val page = """{"code":200,"playlist":[{"id":88,"creator":{"userId":42}},{"id":90,"specialType":5,"creator":{"userId":42}}]}"""
        for (id in listOf(88L, 90L, 999L)) {
            enqueue(page)
            assertEquals(ErrorKind.Forbidden, (appResult { library.unsubscribe(id) } as AppResult.Failure).kind)
        }
        assertEquals(3, upstream.requestCount)
        assertEquals(0L, library.revision.value)
        assertEquals(0, current.requestCount)
    }
    @Test fun failedUnsubscribeKeepsTheLibraryRevisionAndReportsFailure() = runBlocking {
        login(); val library = NeteaseLibraryRepository(api, binding, NeteaseSongActionsRepository(api, session = ::owner), ::owner)
        enqueue("""{"code":200,"playlist":[{"id":89,"subscribed":true,"creator":{"userId":99}}]}""")
        enqueue("""{"code":500}""")
        assertEquals(ErrorKind.Server, (appResult { library.unsubscribe(89) } as AppResult.Failure).kind)
        assertEquals(0L, library.revision.value)
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
