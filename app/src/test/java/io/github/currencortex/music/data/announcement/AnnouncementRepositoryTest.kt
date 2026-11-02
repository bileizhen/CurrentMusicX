package io.github.currencortex.music.data.announcement

import io.github.currencortex.music.core.network.ApiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class AnnouncementRepositoryTest {
    @Test fun automaticDismissalSurvivesNewCollectorsButNotANewProcessRepository() {
        val server = "https://example.com/cm/"
        val api = ApiClient({ server }, { null }, {})
        val first = Announcement(id = 1, title = "First")
        val fresh = Announcement(id = 2, title = "New")
        val unchecked = AnnouncementReadState(suppressRead = false)
        val repository = AnnouncementRepository(api)
        assertEquals(listOf(first), repository.pendingAutomatic(server, listOf(first), unchecked))
        repository.dismissAutomatic(server, listOf(first))
        repeat(3) { assertTrue(repository.pendingAutomatic(server, listOf(first), unchecked).isEmpty()) }
        assertEquals(listOf(fresh), repository.pendingAutomatic(server, listOf(first, fresh), unchecked))
        assertEquals(listOf(first), repository.pendingAutomatic("https://other.example/cm/", listOf(first), unchecked))
        val nextProcess = AnnouncementRepository(api)
        assertEquals(listOf(first), nextProcess.pendingAutomatic(server, listOf(first), unchecked))
        val checked = AnnouncementReadState(true, setOf(announcementKey(server, first)))
        assertEquals(listOf(fresh), nextProcess.pendingAutomatic(server, listOf(first, fresh), checked))
        // Manual archive browsing is deliberately not filtered by dismissAutomatic.
    }

    @Test fun permanentFailuresAreNotRetriedEvenWhenAutomaticRetryIsEnabled() = runBlocking {
        for ((code, body) in listOf(401 to "{}", 403 to "{}", 404 to "{}", 200 to "not-json")) {
            MockWebServer().use { server ->
                val base = server.url("/cm/").toString()
                repeat(4) { server.enqueue(MockResponse().setResponseCode(code).setBody(body)) }
                val repository = AnnouncementRepository(ApiClient({ base }, { null }, {}))
                assertTrue(repository.observe(flowOf(base), retryCount = 3).last().isEmpty())
                assertEquals("HTTP $code must not retry", 1, server.requestCount)
            }
        }
    }

    @Test fun cooldownRequestAndPayloadByteComparisonUsesTheSameMockScenario() = runBlocking {
        suspend fun scenario(cooldownMillis: Long): Pair<Int, Long> {
            MockWebServer().use { server ->
                val base = server.url("/cm/").toString()
                var clock = 0L
                val bodyBytes = AtomicLong()
                val client = OkHttpClient.Builder().eventListener(object : EventListener() {
                    override fun responseBodyEnd(call: Call, byteCount: Long) { bodyBytes.addAndGet(byteCount) }
                }).build()
                val repository = AnnouncementRepository(ApiClient({ base }, { null }, {}, client = client), nowMillis = { clock })
                repeat(12) { server.enqueue(MockResponse().setBody(fixture)) }
                // Identical resource, 12 foreground collector recreations, one per virtual minute.
                repeat(12) {
                    clock = it * 60_000L
                    assertEquals(2, repository.observe(flowOf(base), minimumRefreshMillis = cooldownMillis).last().size)
                }
                return server.requestCount to bodyBytes.get()
            }
        }
        val control = scenario(0)
        val cached = scenario(15 * 60_000L)
        val payloadBytes = fixture.toByteArray(Charsets.UTF_8).size.toLong()
        assertEquals(12 to payloadBytes * 12, control)
        assertEquals(1 to payloadBytes, cached)
        // Counts body bytes only, not HTTP/TLS overhead, device traffic or energy consumption.
    }

    @Test fun automaticChecksShareCooldownAcrossCollectorsAndManualRefreshBypassesIt() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/cm/").toString()
            var clock = 0L
            val repository = AnnouncementRepository(ApiClient({ base }, { null }, {}), nowMillis = { clock })
            suspend fun check() = repository.observe(flowOf(base), minimumRefreshMillis = 15 * 60_000).last()
            server.enqueue(MockResponse().setBody(fixture))
            val a = async { check() }; val b = async { check() }
            assertEquals(a.await(), b.await()); assertEquals(1, server.requestCount)
            clock = 15 * 60_000L - 1
            repeat(5) { assertEquals(2, check().size) }
            assertEquals(1, server.requestCount)
            server.enqueue(MockResponse().setBody(fixture))
            repository.fetchAll(base)
            assertEquals(2, server.requestCount)
            clock++
            server.enqueue(MockResponse().setBody("""{"items":[{"id":9,"title":"New"}]}"""))
            assertEquals(9L, check().single().id); assertEquals(3, server.requestCount)
        }
    }
    @Test fun failedAutomaticChecksAlsoHaveACooldownButCanRetryAfterIt() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/cm/").toString()
            var clock = 0L
            val repository = AnnouncementRepository(ApiClient({ base }, { null }, {}), nowMillis = { clock })
            suspend fun check() = repository.observe(flowOf(base), minimumRefreshMillis = 1000).last()
            server.enqueue(MockResponse().setResponseCode(502))
            assertTrue(check().isEmpty()); repeat(5) { assertTrue(check().isEmpty()) }
            assertEquals(1, server.requestCount)
            clock = 1000
            server.enqueue(MockResponse().setBody(fixture))
            assertEquals(2, check().size); assertEquals(2, server.requestCount)
        }
    }
    @Test fun automaticCooldownIsScopedToTheSelectedServer() = runBlocking {
        MockWebServer().use { first -> MockWebServer().use { second ->
            var base = first.url("/cm/").toString()
            val repository = AnnouncementRepository(ApiClient({ base }, { null }, {}))
            first.enqueue(MockResponse().setBody(fixture)); second.enqueue(MockResponse().setBody(fixture))
            repository.observe(flowOf(base), minimumRefreshMillis = 900000).last()
            base = second.url("/cm/").toString()
            repository.observe(flowOf(base), minimumRefreshMillis = 900000).last()
            base = first.url("/cm/").toString()
            repository.observe(flowOf(base), minimumRefreshMillis = 900000).last()
            assertEquals(1, first.requestCount); assertEquals(1, second.requestCount)
        } }
    }
    @Test fun manualArchiveIsNotTruncatedToTheStartupBatchAndRemainsPublic() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/cm/").toString()
            val repository = AnnouncementRepository(ApiClient({ base }, { "private-token" }, {}))
            val items = (1..35).joinToString(",") { """{"id":$it,"title":"Notice $it"}""" }
            server.enqueue(MockResponse().setBody("""{"items":[$items]}"""))
            assertEquals((1L..35L).toList(), repository.fetchAll(base).map { it.id })
            val request = server.takeRequest()
            assertEquals("/cm/announcements?limit=1000", request.path)
            assertNull(request.getHeader("Authorization"))
        }
    }
    private val fixture = """{"items":[
        {"id":1,"title":"Pinned notice","body":"**Markdown**","pinned":true,"createdAt":1791646979,"author":"站长Rcst20"},
        {"id":2,"title":"Second notice","link":"https://bilibili.com","updatedAt":1791647770},
        {"id":3,"title":"  "},{"id":4,"title":"Removed","removed":true}],"count":4,"future":true}"""

    @Test fun publicContractKeepsOrderingAndNeverSendsAccountToken() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/cm/").toString()
            val repository = AnnouncementRepository(ApiClient({ base }, { "private-session" }, { fail("Public request expired account") }))
            server.enqueue(MockResponse().setBody(fixture))
            val items = repository.fetch(base)
            assertEquals(listOf(1L, 2L), items.map { it.id })
            assertTrue(items[0].pinned); assertEquals("**Markdown**", items[0].body)
            assertEquals(1791646979L, items[0].createdAt); assertEquals("站长Rcst20", items[0].author)
            assertEquals("https://bilibili.com/", items[1].detailUrl)
            val request = server.takeRequest()
            assertEquals("GET", request.method); assertEquals("/cm/announcements?limit=20", request.path)
            assertNull(request.getHeader("Authorization"))
        }
    }

    @Test fun failuresAndEmptyAnnouncementsAreSilent() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/cm/").toString()
            val repository = AnnouncementRepository(ApiClient({ base }, { "private-session" }, { fail("Public request expired account") }))
            for (response in listOf(MockResponse().setResponseCode(401), MockResponse().setResponseCode(404),
                MockResponse().setResponseCode(502), MockResponse().setBody("not-json"), MockResponse().setBody("{}"))) {
                server.enqueue(response)
                assertTrue(repository.observe(flowOf(base)).toList().all { it.isEmpty() })
            }
        }
    }

    @Test fun slowAnnouncementsDoNotKeepStartupWaiting() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/cm/").toString()
            server.enqueue(MockResponse().setBody(fixture).setHeadersDelay(2, TimeUnit.SECONDS))
            val repository = AnnouncementRepository(ApiClient({ base }, { null }, {}), timeoutMillis = 150)
            val start = System.nanoTime()
            assertTrue(repository.observe(flowOf(base)).toList().all { it.isEmpty() })
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1_000)
        }
    }

    @Test fun changedServerCancelsLateAnnouncementAndDuplicateServerDoesNotRefetch() = runBlocking {
        MockWebServer().use { old -> MockWebServer().use { fresh ->
            val servers = MutableStateFlow(old.url("/cm/").toString())
            val repository = AnnouncementRepository(ApiClient({ servers.value }, { null }, {}))
            old.enqueue(MockResponse().setBody("""{"items":[{"title":"Stale"}]}""").setHeadersDelay(2, TimeUnit.SECONDS))
            fresh.enqueue(MockResponse().setBody("""{"items":[{"title":"Fresh"}]}"""))
            val collected = mutableListOf<List<Announcement>>()
            val job = launch { repository.observe(servers).collect { collected += it } }
            withTimeout(2_000) { while (old.requestCount == 0) delay(5) }
            servers.value = fresh.url("/cm/").toString()
            withTimeout(2_000) { while (collected.none { it.any { a -> a.title == "Fresh" } }) delay(5) }
            servers.value = fresh.url("/cm/").toString()
            delay(100)
            assertEquals(1, fresh.requestCount)
            assertFalse(collected.flatten().any { it.title == "Stale" })
            job.cancelAndJoin()
        } }
    }

    @Test fun eachNewStartupReadsAnnouncementsAgain() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/cm/").toString()
            val repository = AnnouncementRepository(ApiClient({ base }, { null }, {}))
            repeat(2) {
                server.enqueue(MockResponse().setBody(fixture))
                assertEquals(2, repository.observe(flowOf(base)).last().size)
            }
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun responseBodyStallAlsoHasBoundedTimeout() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/cm/").toString()
            server.enqueue(MockResponse().setBody(fixture).setBodyDelay(2, TimeUnit.SECONDS))
            val repository = AnnouncementRepository(ApiClient({ base }, { null }, {}), timeoutMillis = 200)
            val start = System.nanoTime()
            assertTrue(repository.observe(flowOf(base)).toList().all { it.isEmpty() })
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1_000)
        }
    }

    @Test fun detailsOnlyAcceptWebLinksWithoutEmbeddedCredentials() {
        for (link in listOf("javascript:alert(1)", "file:///sdcard/file", "content://private", "https://user:secret@example.com", ""))
            assertNull(Announcement(link = link).detailUrl)
        assertEquals("http://example.com/", Announcement(link = "http://example.com").detailUrl)
    }

    @Test fun refreshFindsNewNoticesAndFailureDoesNotErasePendingContent() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/cm/").toString()
            val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
            val repository = AnnouncementRepository(ApiClient({ base }, { null }, {}))
            val collected = mutableListOf<List<Announcement>>()
            server.enqueue(MockResponse().setBody(fixture))
            val job = launch { repository.observe(flowOf(base), refreshes, clearBeforeFetch = false).collect { collected += it } }
            try {
                withTimeout(3_000) { while (collected.isEmpty()) delay(5) }
                server.enqueue(MockResponse().setResponseCode(502))
                refreshes.emit(Unit)
                withTimeout(3_000) { while (server.requestCount < 2) delay(5) }
                delay(100)
                assertEquals(1, collected.size)
                server.enqueue(MockResponse().setBody("""{"items":[{"id":5,"title":"New"}]}"""))
                refreshes.emit(Unit)
                withTimeout(3_000) { while (collected.last().first().id != 5L) delay(5) }
                assertEquals(3, server.requestCount)
            } finally { job.cancelAndJoin() }
        }
    }

    @Test fun temporaryFailureRetriesAndRecoversWithoutAnotherStartup() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/cm/").toString()
            server.enqueue(MockResponse().setResponseCode(502))
            server.enqueue(MockResponse().setBody(fixture))
            val repository = AnnouncementRepository(ApiClient({ base }, { null }, {}))
            assertEquals(2, repository.observe(flowOf(base), retryCount = 1).last().size)
            assertEquals(2, server.requestCount)
        }
    }
}
