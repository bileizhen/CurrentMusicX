package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.feature.lyrics.data.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import java.util.concurrent.TimeUnit

class QqLyricsMatchSourceTest {
    private val mid = "001N8FgQ0aepZB"
    private fun source(server: MockWebServer, client: OkHttpClient = OkHttpClient()) = QqLyricsMatchSource(client, server.url("/qq/").toString())
    private fun candidate() = LyricsCandidate(LyricsMatchSource.QQ_MUSIC, mid, "Fixture", "Singer", durationMs = 191_000, reference = "273534131")
    private fun lyrics(main: String, trans: String = "", roma: String = "") = buildJsonObject {
        put("code", 0); put("retcode", 0); put("subcode", 0); put("lyric", main); put("trans", trans); put("roma", roma)
    }.toString()

    @Test fun publicCatalogueSearchReturnsQqIdentityAndEncodesTheQueryWithoutAccountCookies() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"song":{"list":[{"songid":273534131,"songmid":"$mid","songname":"<em>Fixture</em> &amp; friends","albumname":"Album","interval":191,"singer":[{"name":"Singer"}]},{"songmid":"$mid","songname":"Duplicate"},{"songmid":"../../bad","songname":"Unsafe"}]}}}"""))
            server.start()
            val result = source(server).search("Fixture & friends - Singer - Album (Long deluxe edition)").single()
            assertEquals(LyricsMatchSource.QQ_MUSIC, result.source)
            assertEquals(mid, result.id)
            assertEquals("273534131", result.reference)
            assertEquals("Fixture & friends", result.title)
            assertEquals("Singer", result.artists)
            assertEquals(191_000L, result.durationMs)
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("/qq/soso/fcgi-bin/search_for_qq_cp", request.requestUrl!!.encodedPath)
            assertEquals("Fixture & friends Singer", request.requestUrl!!.queryParameter("w"))
            assertEquals("json", request.requestUrl!!.queryParameter("format"))
            assertEquals("https://y.qq.com/", request.getHeader("Referer"))
            assertNull(request.getHeader("Cookie")); assertNull(request.getHeader("Authorization"))
        } finally { server.shutdown() }
    }

    @Test fun publicLrcMergesAuxiliaryTracksAtTheirActualTimestampsWithoutInventingWords() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(lyrics("[00:01.00]Hello\n[00:03.00]World", "[00:01.00]你好\n[00:02.00]Unmatched translation", "[00:01.00]ni hao")))
            server.start()
            val document = source(server).load(candidate())
            assertEquals(listOf("Hello", "World"), document.lines.map { it.text })
            assertEquals("你好", document.lines.first().translation)
            assertEquals("ni hao", document.lines.first().romanization)
            assertEquals("", document.lines.last().translation)
            assertFalse(document.hasWordTiming)
            assertEquals(listOf(mid), document.metadata.values["qqMusicId"])
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("/qq/lyric/fcgi-bin/fcg_query_lyric_new.fcg", request.requestUrl!!.encodedPath)
            assertEquals(mid, request.requestUrl!!.queryParameter("songmid"))
            assertEquals("1", request.requestUrl!!.queryParameter("nobase64"))
            assertNull(request.getHeader("Cookie"))
        } finally { server.shutdown() }
    }

    @Test fun catalogueAlbumMidsProvideCoversWhileMissingOrInvalidMidsKeepThePlaceholder() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"song":{"list":[
                {"songmid":"001bIzp24ISxYM","songname":"Fixture","albummid":"00017J8H3zbzxe"},
                {"songmid":"missing","songname":"Missing album"},
                {"songmid":"invalid","songname":"Invalid album","albummid":"../../private?x=1"}
            ]}}}"""))
            server.start()
            val results = source(server).search("Fixture")
            assertEquals("https://y.gtimg.cn/music/photo_new/T002R300x300M00000017J8H3zbzxe.jpg", results[0].cover)
            assertEquals("", results[1].cover)
            assertEquals("", results[2].cover)
        } finally { server.shutdown() }
    }

    @Test fun base64AndHtmlEncodedPublicTracksDecodeBeforeTheLrcParser() = runBlocking {
        val server = MockWebServer()
        try {
            val body = Base64.getEncoder().encodeToString("[00:01.00]A &amp; B\r\n[00:03.00]Second".toByteArray())
            server.enqueue(MockResponse().setBody(lyrics(body, "&#91;00:01.00&#93;Translation", Base64.getEncoder().encodeToString("[00:01.00]roma".toByteArray()))))
            server.start()
            val line = source(server).load(candidate()).lines.first()
            assertEquals("A & B", line.text)
            assertEquals("Translation", line.translation)
            assertEquals("roma", line.romanization)
        } finally { server.shutdown() }
    }

    @Test fun businessRefusalsAreErrorsRatherThanEmptySuccessfulMatches() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"code":2001,"data":{"song":{"list":[]}}}"""))
            server.enqueue(MockResponse().setBody("""{"code":0,"retcode":-1301,"lyric":"[00:01]wrong"}"""))
            server.start()
            val searchError = runCatching { source(server).search("Fixture") }.exceptionOrNull()
            assertTrue(searchError is LyricsMatchException)
            assertTrue(searchError!!.message!!.contains("2001"))
            val lyricError = runCatching { source(server).load(candidate()) }.exceptionOrNull()
            assertTrue(lyricError!!.message!!.contains("-1301"))
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun emptyCatalogueSubcodeRetriesTheTitleWithoutAllArtistsOrAlbum() = runBlocking {
        val server = MockWebServer()
        try {
            // Public QQ response for the screenshot's combined title/artist query.
            server.enqueue(MockResponse().setBody("""{"code":0,"subcode":10003,"message":"no results","data":{"song":{"list":[]}}}"""))
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"song":{"list":[{"songmid":"$mid","songname":"MONTAGEM BYE","singer":[{"name":"Another artist"}]}]}}}"""))
            server.start()
            val result = source(server).search("MONTAGEM BYE - broken / Trispect / Su7N1_Regret - Album").single()
            assertEquals(mid, result.id)
            // Retain the catalogue identity so the matching gate can reject a different recording.
            assertEquals("Another artist", result.artists)
            assertEquals("MONTAGEM BYE broken / Trispect / Su7N1_Regret", server.takeRequest().requestUrl!!.queryParameter("w"))
            val fallback = server.takeRequest()
            assertEquals("MONTAGEM BYE", fallback.requestUrl!!.queryParameter("w"))
            assertNull(fallback.getHeader("Cookie")); assertNull(fallback.getHeader("Authorization"))
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun emptySearchesStopAfterDistinctTitleFallbackAndDoNotRepeatFreeText() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"code":0,"subcode":0,"data":{"song":{"list":[]}}}"""))
            repeat(2) {
                server.enqueue(MockResponse().setBody("""{"code":0,"subcode":10003,"data":{"song":{"list":[]}}}"""))
            }
            server.start()
            assertTrue(source(server).search("Missing - Singer").isEmpty())
            assertEquals(2, server.requestCount)
            assertTrue(source(server).search("Missing").isEmpty())
            assertEquals(3, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun subcode10003IsNotIgnoredForMalformedNonemptyOrRejectedSearchesOrLyrics() = runBlocking {
        val server = MockWebServer()
        try {
            val invalid = listOf(
                """{"code":10003,"data":{"song":{"list":[]}}}""",
                """{"code":0,"subcode":10003,"data":{}}""",
                """{"code":0,"subcode":10003,"data":{"song":{"list":[{"songmid":"$mid","songname":"Fixture"}]}}}""",
                """{"code":0,"retcode":2001,"subcode":10003,"data":{"song":{"list":[]}}}""",
                """{"code":0,"subcode":2001,"data":{"song":{"list":[]}}}"""
            )
            invalid.forEach { server.enqueue(MockResponse().setBody(it)) }
            server.enqueue(MockResponse().setBody("""{"code":0,"subcode":10003,"lyric":"[00:01]Wrong"}"""))
            server.start()
            invalid.forEachIndexed { index, _ ->
                assertTrue(runCatching { source(server).search("Fixture - Singer") }.exceptionOrNull() is LyricsMatchException)
                assertEquals(index + 1, server.requestCount)
            }
            assertTrue(runCatching { source(server).load(candidate()) }.exceptionOrNull()!!.message!!.contains("10003"))
            assertEquals(invalid.size + 1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun titleFallbackFailureIsNotHiddenByTheFirstEmptyResult() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"code":0,"subcode":10003,"data":{"song":{"list":[]}}}"""))
            server.enqueue(MockResponse().setResponseCode(503))
            server.start()
            assertTrue(runCatching { source(server).search("Fixture - Singer") }.exceptionOrNull()!!.message!!.contains("503"))
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun qqInstrumentalNoticeWithColonTimestampIsPlainLrcRatherThanUnsupportedEncoding() = runBlocking {
        val server = MockWebServer()
        try {
            // Same public response shape as SUBARU AWA 486 (4), QQ mid 001bIzp24ISxYM.
            server.enqueue(MockResponse().setBody(lyrics("[00:00:00]此歌曲为没有填词的纯音乐，请您欣赏")))
            server.start()
            val document = source(server).load(candidate())
            assertEquals("此歌曲为没有填词的纯音乐，请您欣赏", document.lines.single().text)
            assertEquals(0L, document.lines.single().startTimeMs)
            assertFalse(document.hasWordTiming)
        } finally { server.shutdown() }
    }

    @Test fun colonFractionsInPlainBase64AndHtmlTracksRetainPrecisionAndAuxiliaryAlignment() = runBlocking {
        val server = MockWebServer()
        try {
            val main = "[00:01:2]First\r\n[00:02:34]Second\r[01:03:456]Third\n[01:05.67]Dot"
            val translation = "&#91;00:01:2&#93;译文\n[00:02:34]第二句"
            val roma = Base64.getEncoder().encodeToString("[00:01:2]roma\n[00:02:34]second".toByteArray())
            server.enqueue(MockResponse().setBody(lyrics(main, translation, roma)))
            server.enqueue(MockResponse().setBody(lyrics(Base64.getEncoder().encodeToString(main.toByteArray()), translation, roma)))
            server.start()
            repeat(2) {
                val document = source(server).load(candidate())
                assertEquals(listOf(1200L, 2340L, 63456L, 65670L), document.lines.map { it.startTimeMs })
                assertEquals(listOf("First", "Second", "Third", "Dot"), document.lines.map { it.text })
                assertEquals("译文", document.lines[0].translation)
                assertEquals("roma", document.lines[0].romanization)
                assertEquals("第二句", document.lines[1].translation)
                assertEquals("second", document.lines[1].romanization)
                assertFalse(document.hasWordTiming)
            }
        } finally { server.shutdown() }
    }

    @Test fun malformedPayloadsOversizedResponsesAndUnsupportedQrcAreRejected() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{}}"""))
            server.enqueue(MockResponse().setBody("{}").setHeader("Content-Length", 4 * 1024 * 1024 + 1))
            server.enqueue(MockResponse().setBody(lyrics("C0FFEE00C0FFEE00")))
            server.start()
            assertTrue(runCatching { source(server).search("Fixture") }.exceptionOrNull() is LyricsMatchException)
            assertTrue(runCatching { source(server).search("Fixture") }.exceptionOrNull()!!.message!!.contains("过大"))
            assertTrue(runCatching { source(server).load(candidate()) }.isFailure)
        } finally { server.shutdown() }
    }

    @Test fun cancellationWhileTheResponseBodyIsDelayedStopsWithoutReturningLyrics() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(lyrics("[00:01.00]Fixture")).setBodyDelay(2, TimeUnit.SECONDS))
            server.start()
            var returned = false
            val job = launch(Dispatchers.Default) { source(server).load(candidate()); returned = true }
            assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            delay(50)
            withTimeout(1000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
            assertFalse(returned)
        } finally { server.shutdown() }
    }

    @Test fun inheritedCookiesAndCredentialInterceptorsCannotAttachAnAccountToPublicQqCalls() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"song":{"list":[]}}}"""))
            server.start()
            val jar = object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
                override fun loadForRequest(url: HttpUrl) = listOf(Cookie.Builder().name("session").value("private").domain(url.host).build())
            }
            val client = OkHttpClient.Builder().cookieJar(jar).addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Authorization", "private-app-account")
                    .header("Cookie", "app-account=private").header("X-Application-Credential", "private").build())
            }.addNetworkInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Authorization", "private-network-account")
                    .header("Cookie", "network-account=private").header("X-Network-Credential", "private").build())
            }.build()
            assertTrue(source(server, client).search("Missing fixture").isEmpty())
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertNull(request.getHeader("Cookie")); assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("X-Application-Credential")); assertNull(request.getHeader("X-Network-Credential"))
        } finally { server.shutdown() }
    }

    @Test fun emptyQueriesAndInvalidSongMidsDoNotStartHttpRequests() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            val source = source(server)
            assertTrue(source.search("   ").isEmpty())
            assertTrue(runCatching { source.load(candidate().copy(id = "../../private")) }.isFailure)
            assertEquals(0, server.requestCount)
        } finally { server.shutdown() }
    }
}
