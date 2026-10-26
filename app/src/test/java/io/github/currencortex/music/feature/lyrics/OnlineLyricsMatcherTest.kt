package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.DeflaterOutputStream

class OnlineLyricsMatcherTest {
    private fun matcher(server: MockWebServer,
        search: suspend (String) -> List<Song> = { emptyList() },
        load: suspend (Long) -> LyricsDocument = { error("Unexpected NetEase fetch") },
        client: OkHttpClient = OkHttpClient()) = OnlineLyricsMatcher(search, load, client,
        amllBaseUrl = server.url("/amll/").toString(), kugouSearchBaseUrl = server.url("/mobile/").toString(),
        kugouLyricsBaseUrl = server.url("/lyric/").toString(), kugouCatalogBaseUrl = server.url("/catalog/").toString())

    private fun item(id: String = "123", name: String = "Fixture", artist: String = "Singer") = buildJsonObject {
        put("ncmMusicIds", buildJsonArray { add(id) }); put("qqMusicIds", JsonArray(emptyList()))
        put("musicNames", buildJsonArray { add(name) }); put("artistNames", buildJsonArray { add(artist) })
        put("albumNames", buildJsonArray { add("Album") })
    }
    private fun searchResponse(vararg items: JsonObject) = buildJsonObject {
        put("status", 200); put("data", buildJsonObject { put("items", JsonArray(items.toList())) })
    }.toString()
    private fun lyricResponse(ids: List<String> = listOf("123")) = buildJsonObject {
        put("status", 200); put("data", buildJsonObject {
            put("ncmMusicIds", JsonArray(ids.map(::JsonPrimitive)))
            put("lyrics", """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="3s"><span begin="1s" end="2s">Hello</span><span begin="2s" end="3s"> world</span></p></div></body></tt>""")
        })
    }.toString()
    private val hash = "D9CA820CDA8C4B88DEA1D791D133D686"

    @Test fun amllStructuredQueriesKeepEncodedIdentityAndLoadAnExplicitDocument() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(searchResponse()))
            server.enqueue(MockResponse().setBody(searchResponse(item("123", "Song", "A & B"), item("123", "Duplicate"))))
            server.enqueue(MockResponse().setBody(lyricResponse()))
            server.start()
            val matcher = matcher(server)
            val candidates = matcher.search(LyricsMatchSource.AMLL, "Song (TV Size) - A & B - Album")
            assertEquals(1, candidates.size)
            assertEquals("ncm:123", candidates.single().reference)
            assertEquals("", candidates.single().cover)
            val first = server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("Song (TV Size)", first.queryParameter("musicName"))
            assertEquals("A", first.queryParameter("artistName"))
            val second = server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("Song", second.queryParameter("musicName"))
            val saved = matcher.load(candidates.single())
            assertEquals("amll", saved.origin.provider)
            assertTrue(saved.document.hasWordTiming)
            assertEquals(1000L, saved.document.lines.first().startTimeMs)
            val fetch = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("123", fetch.requestUrl!!.queryParameter("ncmMusicId"))
            assertNull(fetch.getHeader("Cookie"))
            assertNull(fetch.getHeader("Authorization"))
        } finally { server.shutdown() }
    }

    @Test fun amllUnstructuredQueryFallsBackToQOnlyAfterAnEmptySuccessfulSearch() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(searchResponse()))
            server.enqueue(MockResponse().setBody(searchResponse(item(name = "A+B & C"))))
            server.start()
            assertEquals(1, matcher(server).search(LyricsMatchSource.AMLL, "A+B & C").size)
            assertEquals("A+B & C", server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!.queryParameter("musicName"))
            assertEquals("A+B & C", server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!.queryParameter("q"))
        } finally { server.shutdown() }
    }

    @Test fun businessErrorsDoNotMasqueradeAsAnEmptySearchAndNeverTriggerMoreQueries() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"status":2001,"data":null}"""))
            server.start()
            val error = runCatching { matcher(server).search(LyricsMatchSource.AMLL, "Fixture") }.exceptionOrNull()
            assertTrue(error is LyricsMatchException)
            assertTrue(error!!.message!!.contains("2001"))
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun mismatchedAmllMetadataAndOversizedResponsesAreRejected() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(lyricResponse(listOf("999"))))
            server.enqueue(MockResponse().setBody("{}").setHeader("Content-Length", OnlineLyricsMatcher.MAX_RESPONSE_BYTES + 1))
            server.start()
            val matcher = matcher(server)
            val wrong = runCatching { matcher.load(LyricsCandidate(LyricsMatchSource.AMLL, "123", "Fixture", reference = "ncm:123")) }.exceptionOrNull()
            assertTrue(wrong!!.message!!.contains("其他歌曲"))
            val huge = runCatching { matcher.search(LyricsMatchSource.AMLL, "Fixture") }.exceptionOrNull()
            assertTrue(huge!!.message!!.contains("过大"))
        } finally { server.shutdown() }
    }

    @Test fun unavailableNewKugouCatalogueFallsBackToItsSeparateMobileCatalogue() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody("""{"status":1,"errcode":0,"data":{"info":[{"hash":"${hash.lowercase()}","songname":"Fixture","singername":"Singer","album_name":"Album","duration":191,"imgurl":"https://cover.example/{size}/fixture.jpg"}]}}"""))
            server.start()
            val found = matcher(server).search(LyricsMatchSource.KUGOU, "Fixture - Singer - Album").single()
            assertEquals(hash, found.id)
            assertEquals(191_000L, found.durationMs)
            assertEquals("Singer", found.artists)
            assertEquals("https://cover.example/480/fixture.jpg", found.cover)
            val catalog = server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("/catalog/v2/search/song", catalog.encodedPath)
            assertEquals("Fixture", catalog.queryParameter("keyword"))
            assertSignature(catalog)
            val fallback = server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("/mobile/api/v3/search/song", fallback.encodedPath)
            assertEquals("Fixture", fallback.queryParameter("keyword"))
        } finally { server.shutdown() }
    }

    @Test fun kugouDownloadUsesTheCandidateAccessKeyAndDecodesRealCompressedWordTiming() = runBlocking {
        val server = MockWebServer()
        try {
            val raw = "[1000,2000]<0,500,0>你<500,1500,0>好"
            val encoded = Base64.getEncoder().encodeToString(krc(raw))
            server.enqueue(MockResponse().setBody("""{"status":200,"errcode":0,"candidates":[{"id":"99","accesskey":"access&=+"}]}"""))
            server.enqueue(MockResponse().setBody("""{"status":200,"error_code":0,"contenttype":0,"content":"$encoded"}"""))
            server.start()
            val result = matcher(server).load(LyricsCandidate(LyricsMatchSource.KUGOU, hash, "Fixture", "Singer", durationMs = 191_000))
            assertEquals("你好", result.document.lines.single().text)
            assertEquals(listOf(1000L, 1500L), result.document.lines.single().words.map { it.startTimeMs })
            assertEquals("kugou", result.origin.provider)
            val search = server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals(hash, search.queryParameter("hash"))
            assertEquals("191000", search.queryParameter("duration"))
            assertSignature(search)
            val download = server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("/lyric/download", download.encodedPath)
            assertEquals("access&=+", download.queryParameter("accesskey"))
            assertEquals("krc", download.queryParameter("fmt"))
            assertSignature(download)
        } finally { server.shutdown() }
    }

    @Test fun kugouBusinessFailureIsAnErrorRatherThanLyricsFromAnotherCandidate() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"status":200,"error_code":2001,"candidates":[]}"""))
            server.start()
            val error = runCatching { matcher(server).load(LyricsCandidate(LyricsMatchSource.KUGOU, hash, "Fixture")) }.exceptionOrNull()
            assertTrue(error is LyricsMatchException)
            assertTrue(error!!.message!!.contains("2001"))
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun cancellationWhileReadingABodyCompletesPromptlyWithoutReturningLyrics() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(lyricResponse()).setBodyDelay(2, TimeUnit.SECONDS))
            server.start()
            var completed = false
            val job = launch(Dispatchers.Default) {
                matcher(server).load(LyricsCandidate(LyricsMatchSource.AMLL, "123", "Fixture", reference = "ncm:123"))
                completed = true
            }
            assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            delay(50)
            withTimeout(1_000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
            assertFalse(completed)
        } finally { server.shutdown() }
    }

    @Test fun realAmllWordTimingWinsWhenItsTextMatchesTheNeteaseReference() = runBlocking {
        val server = MockWebServer()
        try {
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                    "/amll/v1/lyrics/search" -> MockResponse().setBody(searchResponse(item()))
                    "/amll/v1/lyrics/get" -> MockResponse().setBody(lyricResponse())
                    "/catalog/v2/search/song" -> MockResponse().setBody("""{"status":1,"data":{"lists":[]}}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
            server.start()
            var neteaseLoads = 0
            val song = Song(123, "Fixture", "Singer", "Album", durationMs = 191_000)
            val matcher = matcher(server, search = { listOf(song) }, load = {
                neteaseLoads++; LyricsParser.lrc("[00:01.00]Hello world\n[00:03.00]")
            })
            val result = matcher.bestMatch(song, LyricsMatchSource.NETEASE)!!
            assertEquals(1, neteaseLoads)
            assertTrue(result.document.hasWordTiming)
            assertEquals("amll", result.origin.provider)
            assertTrue(server.requestCount >= 3)
        } finally { server.shutdown() }
    }

    @Test fun automaticMatchingNeverAcceptsTheFirstResultWithTheWrongArtist() = runBlocking {
        val server = MockWebServer()
        try {
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                    "/amll/v1/lyrics/search" -> MockResponse().setBody(searchResponse(item(artist = "Other singer")))
                    "/catalog/v2/search/song" -> MockResponse().setBody("""{"status":1,"data":{"lists":[]}}""")
                    else -> MockResponse().setResponseCode(500)
                }
            }
            server.start()
            var neteaseLoads = 0
            val matcher = matcher(server, search = { listOf(Song(123, "Fixture", "Other singer", "Album", durationMs = 191_000)) },
                load = { neteaseLoads++; LyricsParser.lrc("[00:01]wrong") })
            val unlinked = Song(1, "Fixture", "Singer", "Album", durationMs = 191_000,
                musicSource = io.github.currencortex.music.data.song.MusicSource.QQ_MUSIC)
            val failure = runCatching { matcher.bestMatch(unlinked) }.exceptionOrNull()
            assertTrue(failure is LyricsMatchException)
            assertTrue(failure!!.message!!.contains("无法取得网易云基准歌词"))
            assertEquals(0, neteaseLoads)
            assertEquals(0, server.requestCount)
            assertFalse(OnlineLyricsMatcher.isReliableMatch(Song(1, "Fixture", "Singer", durationMs = 200_000),
                LyricsCandidate(LyricsMatchSource.NETEASE, "2", "Fixture", "Singer", durationMs = 191_000)))
            assertFalse(OnlineLyricsMatcher.isReliableMatch(Song(1, "Fixture", "Singer"),
                LyricsCandidate(LyricsMatchSource.NETEASE, "2", "Fixture (Live)", "Singer")))
        } finally { server.shutdown() }
    }

    @Test fun inheritedCookieJarAndCredentialInterceptorsAreNotUsedForPublicLyricRequests() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(searchResponse(item())))
            server.start()
            val jar = object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
                override fun loadForRequest(url: HttpUrl) = listOf(Cookie.Builder().name("account").value("private").domain(url.host).build())
            }
            val client = OkHttpClient.Builder().cookieJar(jar).addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Authorization", "private-app")
                    .header("Cookie", "private=app").build())
            }.addNetworkInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Authorization", "private-network")
                    .header("Cookie", "private=network").build())
            }.build()
            matcher(server, client = client).search(LyricsMatchSource.AMLL, "Fixture")
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertNull(request.getHeader("Cookie"))
            assertNull(request.getHeader("Authorization"))
        } finally { server.shutdown() }
    }

    @Test fun allSourceFailuresAreNotPresentedAsAnOrdinaryNoMatch() = runBlocking {
        val server = MockWebServer()
        try {
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(503)
            }
            server.start()
            val matcher = matcher(server, search = { throw LyricsMatchException("native unavailable") })
            val failure = runCatching { matcher.bestMatch(Song(1, "Fixture", "Singer")) }.exceptionOrNull()
            assertTrue(failure is LyricsMatchException)
            assertTrue(failure!!.message!!.contains("无法取得网易云基准歌词"))
        } finally { server.shutdown() }
    }

    private fun assertSignature(url: HttpUrl) {
        val params = url.queryParameterNames.filter { it != "signature" }.sorted().joinToString("") { "$it=${url.queryParameter(it)}" }
        val seed = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
        val digest = MessageDigest.getInstance("MD5").digest((seed + params + seed).toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(digest, url.queryParameter("signature"))
    }

    private fun krc(text: String): ByteArray {
        val compressed = ByteArrayOutputStream().also { buffer -> DeflaterOutputStream(buffer).use { it.write(text.toByteArray()) } }.toByteArray()
        val key = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
        return byteArrayOf(107, 114, 99, 49) + ByteArray(compressed.size) { (compressed[it].toInt() xor key[it % key.size]).toByte() }
    }
}
