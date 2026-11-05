package io.github.currencortex.music.core.media

import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.coroutines.delay
import java.io.IOException

/** Only read-only mood requests: never retry authorization, parsing or native engine failures. */
internal suspend fun <T> retryMoodNetwork(request: suspend () -> T): T {
    var retries = 0
    while (true) {
        try { return request() }
        catch (failure: IOException) {
            val transient = failure !is ApiException || failure.kind in setOf(
                ErrorKind.Network, ErrorKind.Timeout, ErrorKind.Server)
            if (!transient || retries >= 2) throw failure
            delay(if (retries++ == 0) 500L else 1500L)
        }
    }
}
