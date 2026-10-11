package io.github.currencortex.music.data.announcement

import io.github.currencortex.music.core.network.ApiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class AnnouncementRepositoryTest {
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
}
