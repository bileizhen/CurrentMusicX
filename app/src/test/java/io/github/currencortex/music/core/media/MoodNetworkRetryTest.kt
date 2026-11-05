package io.github.currencortex.music.core.media

import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MoodNetworkRetryTest {
    @Test fun temporaryNetworkFailureRecoversWithoutAnotherUserToggle() = runTest {
        var calls = 0
        val value = retryMoodNetwork { if (++calls < 3) throw IOException("offline") else "ready" }
        assertEquals("ready", value)
        assertEquals(3, calls)
        assertEquals(2000L, testScheduler.currentTime)
    }

    @Test fun persistentServerFailureStopsAfterThreeAttempts() = runTest {
        var calls = 0
        try {
            retryMoodNetwork { calls++; throw ApiException(ErrorKind.Server, 503) }
            fail("Failure must be reported")
        } catch (failure: ApiException) { assertEquals(503, failure.status) }
        assertEquals(3, calls)
    }

    @Test fun authorizationAndInvalidDataAreNotRetried() = runTest {
        for (kind in listOf(ErrorKind.NeteaseBindingRequired, ErrorKind.NeteaseSessionChanged,
            ErrorKind.Forbidden, ErrorKind.Parse, ErrorKind.RateLimited)) {
            var calls = 0
            try {
                retryMoodNetwork { calls++; throw ApiException(kind) }
                fail("Failure must be reported")
            } catch (failure: ApiException) { assertEquals(kind, failure.kind) }
            assertEquals(1, calls)
        }
    }

    @Test fun cancellationStopsPendingRetries() = runTest {
        var calls = 0
        val job = launch { retryMoodNetwork { calls++; throw IOException("offline") } }
        runCurrent()
        job.cancelAndJoin()
        advanceTimeBy(3000)
        assertEquals(1, calls)
    }
}
