package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.feature.lyrics.data.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

class QishuiLyricsMatchSourceTest {
    private val id = "9876543210987654321"
    private fun source(server: MockWebServer, client: OkHttpClient = OkHttpClient()) = QishuiLyricsMatchSource(client,
        tokenBaseUrl = server.url("/token/").toString(), platformBaseUrl = server.url("/platform/").toString(),
        deviceId = "1234567890123456789", now = { 1_700_000_000_000 })
    private fun token(value: String = "public-one") = """{"err_no":0,"client_token":"$value","expires_time":1800000000}"""
    private fun searchResult(): String = """{"err_no":0,"data":{"result_groups":[{"id":"tracks","has_more":true,"data":[{"entity":{"track":{"base_info":{"id":$id,"name":"Fixture","duration_ms":191000},"display_info":{"cover_url":{"urls":["https://cover.example/"],"uri":"fixture","template_prefix":"small"}},"related_info":{"artist_links":[{"name":"Singer"}],"album_link":{"name":"Album"}}}}}]}]}}"""
    private fun mediaResult(returnedId: String = id): String = """{"err_no":0,"data":{"media_list":[{"media_id":"$id","track_entity":{"base_info":{"id":"$returnedId"},"display_info":{"lyric_info":{"lyric_entity":{"type":"krc","content":"[1000,2000]<0,500,0>你<500,1500,0>好"},"lang_translations":{"zh-CN":{"type":"lrc","content":"[00:01.000]Hello"}}}}}}]}}"""
    private fun candidate() = LyricsCandidate(LyricsMatchSource.QISHUI, id, "Fixture", "Singer", "Album", 191000)

    @Test fun anonymousTokenAndCatalogueSearchPreserveLargeIdsAndStructuredMetadata() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(token())); server.enqueue(MockResponse().setBody(searchResult())); server.start()
            val found = source(server).search("Fixture - Singer - Album").single()
            assertEquals(id, found.id); assertEquals(id, found.reference)
            assertEquals("Singer", found.artists); assertEquals("Album", found.album); assertEquals(191000L, found.durationMs)
            assertEquals("https://cover.example/fixture~small-image.image", found.cover)
            val tokenRequest = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("GET", tokenRequest.method)
            assertEquals("/token/api/luna/v1/platform/client/token/get/", tokenRequest.requestUrl!!.encodedPath)
            assertEquals("cold_start", tokenRequest.requestUrl!!.queryParameter("trigger_type"))
            assertEquals("1234567890123456789", tokenRequest.requestUrl!!.queryParameter("device_id"))
            assertEquals("LunaOpenSDK/1.0.10", tokenRequest.getHeader("User-Agent"))
            assertNull(tokenRequest.getHeader("access-token"))
            val search = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("POST", search.method)
            assertEquals("/platform/api/luna/v1/platform/search/", search.requestUrl!!.encodedPath)
            assertEquals("public-one", search.getHeader("access-token"))
            val body = Json.parseToJsonElement(search.body.readUtf8()).jsonObject
            assertEquals("Fixture Singer", body.getValue("q").jsonPrimitive.content)
            assertTrue(body.getValue("common_params").jsonObject.isEmpty())
            assertEquals("track", body.getValue("search_type").jsonPrimitive.content)
        } finally { server.shutdown() }
    }

    @Test fun lyricReadUsesMatchingIdentityAndRealRelativeWordTimingWithTranslation() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(token())); server.enqueue(MockResponse().setBody(mediaResult())); server.start()
            val document = source(server).load(candidate())
            val line = document.lines.single()
            assertEquals("你好", line.text); assertEquals("Hello", line.translation)
            assertEquals(listOf(1000L, 1500L), line.words.map { it.startTimeMs })
            assertEquals(listOf(1500L, 3000L), line.words.map { it.endTimeMs })
            server.takeRequest(1, TimeUnit.SECONDS)
            val read = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("/platform/api/luna/v1/platform/media/mget/", read.requestUrl!!.encodedPath)
            val media = Json.parseToJsonElement(read.body.readUtf8()).jsonObject.getValue("media_list").jsonArray.single().jsonObject
            assertEquals(id, media.getValue("id").jsonPrimitive.content)
            assertEquals("track", media.getValue("type").jsonPrimitive.content)
        } finally { server.shutdown() }
    }

    @Test fun onlyExplicitPublicTokenExpiryReacquiresAndReplaysOnce() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(token()))
            server.enqueue(MockResponse().setBody("""{"err_no":28001008}"""))
            server.enqueue(MockResponse().setBody(token("public-two")))
            server.enqueue(MockResponse().setBody(searchResult())); server.start()
            assertEquals(1, source(server).search("Fixture").size)
            val requests = List(4) { server.takeRequest(1, TimeUnit.SECONDS)!! }
            assertEquals(listOf(null, "public-one", null, "public-two"), requests.map { it.getHeader("access-token") })
            assertEquals(4, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun repeatedExpiryStopsRatherThanLoopingOrRequestingAccountCredentials() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(token())); server.enqueue(MockResponse().setBody("""{"err_no":28001008}"""))
            server.enqueue(MockResponse().setBody(token("public-two"))); server.enqueue(MockResponse().setBody("""{"err_no":28001008}""")); server.start()
            val failure = runCatching { source(server).search("Fixture") }.exceptionOrNull()
            assertTrue(failure is LyricsMatchException); assertTrue(failure!!.message!!.contains("28001008"))
            assertEquals(4, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun httpFailuresAndNestedBusinessErrorsDoNotRefreshTheToken() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(token())); server.enqueue(MockResponse().setResponseCode(403))
            server.enqueue(MockResponse().setBody("""{"err_no":0,"data":{"error_code":28001003}}"""))
            server.enqueue(MockResponse().setBody(searchResult())); server.start()
            val source = source(server)
            assertTrue(runCatching { source.search("Fixture") }.exceptionOrNull()!!.message!!.contains("403"))
            assertTrue(runCatching { source.search("Fixture") }.exceptionOrNull()!!.message!!.contains("28001003"))
            assertEquals(1, source.search("Fixture").size)
            assertEquals(4, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun concurrentExpiredReadsShareOneReplacementToken() = runBlocking {
        val server = MockWebServer()
        val tokenRequests = AtomicInteger()
        val expired = AtomicBoolean(false)
        try {
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.requestUrl!!.encodedPath.contains("client/token/get") -> MockResponse().setBody(token("public-${tokenRequests.incrementAndGet()}"))
                    expired.get() && request.getHeader("access-token") == "public-1" -> MockResponse().setBody("""{"err_no":28001008}""").setBodyDelay(100, TimeUnit.MILLISECONDS)
                    else -> MockResponse().setBody(searchResult())
                }
            }
            server.start()
            val source = source(server)
            source.search("Warmup")
            expired.set(true)
            val first = async(Dispatchers.Default) { source.search("First") }
            val second = async(Dispatchers.Default) { source.search("Second") }
            assertEquals(1, first.await().size); assertEquals(1, second.await().size)
            assertEquals(2, tokenRequests.get())
        } finally { server.shutdown() }
    }

    @Test fun inheritedAccountCookiesAndCredentialInterceptorsAreNeverUsed() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(token())); server.enqueue(MockResponse().setBody(searchResult())); server.start()
            val jar = object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
                override fun loadForRequest(url: HttpUrl) = listOf(Cookie.Builder().name("account").value("private").domain(url.host).build())
            }
            val client = OkHttpClient.Builder().cookieJar(jar).addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Authorization", "private-account").build())
            }.build()
            source(server, client).search("Fixture")
            repeat(2) {
                val request = server.takeRequest(1, TimeUnit.SECONDS)!!
                assertNull(request.getHeader("Cookie")); assertNull(request.getHeader("Authorization"))
                assertNull(request.getHeader("bd-ticket-guard-client-data"))
            }
        } finally { server.shutdown() }
    }

    @Test fun cancellationDuringLyricBodyReadReturnsPromptlyWithoutPublishingADocument() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(token())); server.enqueue(MockResponse().setBody(mediaResult()).setBodyDelay(2, TimeUnit.SECONDS)); server.start()
            var returned = false
            val job = launch(Dispatchers.Default) { source(server).load(candidate()); returned = true }
            assertNotNull(server.takeRequest(1, TimeUnit.SECONDS)); assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            delay(50); withTimeout(1000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled); assertFalse(returned)
        } finally { server.shutdown() }
    }

    @Test fun mismatchedIdentityInvalidIdsAndOversizedResponsesAreRejected() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(token())); server.enqueue(MockResponse().setBody(mediaResult("123")))
            server.enqueue(MockResponse().setBody("{}").setHeader("Content-Length", OnlineLyricsMatcher.MAX_RESPONSE_BYTES + 1)); server.start()
            val source = source(server)
            assertTrue(runCatching { source.load(candidate().copy(id = "../other")) }.isFailure)
            assertEquals(0, server.requestCount)
            assertTrue(runCatching { source.load(candidate()) }.exceptionOrNull()!!.message!!.contains("其他歌曲"))
            assertTrue(runCatching { source.search("Fixture") }.exceptionOrNull()!!.message!!.contains("过大"))
            assertEquals(3, server.requestCount)
        } finally { server.shutdown() }
    }
}
