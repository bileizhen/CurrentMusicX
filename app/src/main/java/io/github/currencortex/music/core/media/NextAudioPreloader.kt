package io.github.currencortex.music.core.media

import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.coroutines.*
import java.io.IOException

/** One cancellable next-track plan; refresh URLs without downloading cached bytes again. */
internal class NextAudioPreloader(
    private val resolve: suspend (refresh: Boolean) -> ResolvedAudio,
    private val preload: suspend (ResolvedAudio) -> Boolean,
    private val onFailure: (Exception) -> Unit = {},
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    suspend fun run() {
        var refresh = false
        var failures = 0
        while (currentCoroutineContext().isActive) {
            try {
                val audio = resolve(refresh)
                val started = nowMs()
                if (!preload(audio)) return
                failures = 0
                delay((audio.refreshAfterMs - (nowMs() - started)).coerceAtLeast(250))
                refresh = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                onFailure(e)
                val retryable = if (e is ApiException) e.kind == ErrorKind.Server || e.kind == ErrorKind.RateLimited else e is IOException
                if (!retryable) return
                delay(longArrayOf(2_000, 5_000, 15_000, 30_000)[failures.coerceAtMost(3)])
                failures++
                // A failed CDN URL needs renewal too; existing bytes remain reusable.
                refresh = true
            }
        }
    }
}
