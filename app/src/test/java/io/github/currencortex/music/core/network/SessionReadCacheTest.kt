package io.github.currencortex.music.core.network

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SessionReadCacheTest {
    @Test fun parallelReadersShareFetchAndExpiryOrRefreshFetchesAgain() = runBlocking {
        var now = 0L
        val session = RequestSession("https://fixture/", "fixture")
        val cache = SessionReadCache({ session }, { 0L }, { now })
        val requests = AtomicInteger()
        suspend fun load() = cache.read("daily") { delay(50); requests.incrementAndGet() }
        assertEquals(listOf(1, 1), awaitAll(async { load() }, async { load() }))
        assertEquals(1, load())
        now = 61_000_000_000L
        assertEquals(2, load())
        cache.clear()
        assertEquals(3, load())
    }
    @Test fun accountServerAndMutationRevisionInvalidatePrefetchedMetadata() = runBlocking {
        var session = RequestSession("https://a/", "first")
        var revision = 0L
        val cache = SessionReadCache({ session }, { revision })
        var calls = 0
        suspend fun load() = cache.read("likes") { ++calls }
        assertEquals(1, load()); assertEquals(1, load())
        session = RequestSession("https://a/", "second"); assertEquals(2, load())
        session = RequestSession("https://b/", "second"); assertEquals(3, load())
        revision++; assertEquals(4, load())
    }
    @Test fun failedFetchIsRetryableAndInvalidatedFlightCannotRefillCache() = runBlocking {
        val session = RequestSession("https://fixture/", "fixture")
        val cache = SessionReadCache({ session }, { 0L })
        assertTrue(appResult { cache.read<String>("daily") { throw ApiException(ErrorKind.Server) } } is AppResult.Failure)
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val flight = async { cache.read("daily") { started.complete(Unit); finish.await(); "old" } }
        started.await(); cache.clear(); finish.complete(Unit)
        assertEquals("old", flight.await())
        assertEquals("new", cache.read("daily") { "new" })
    }
    @Test fun responseFromOldAccountIsRejectedAndPermissionReadsBypassCache() = runBlocking {
        var session = RequestSession("https://fixture/", "first")
        val cache = SessionReadCache({ session }, { 0L })
        assertTrue(appResult { cache.read("daily") { session = RequestSession(session.server, "second"); "private old data" } } is AppResult.Failure)
        assertEquals("new", cache.read("daily") { "new" })
        assertEquals("fresh", cache.read("daily", fresh = true) { "fresh" })
    }
}
