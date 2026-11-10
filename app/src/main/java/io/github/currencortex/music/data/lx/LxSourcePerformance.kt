package io.github.currencortex.music.data.lx

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable

@Serializable
internal data class LxSourcePerformance(
    val resolveMs: Long? = null,
    val measuredAtMs: Long = 0,
    val failedAtMs: Long = 0,
) {
    fun recentTime(now: Long): Long? = resolveMs?.takeIf {
        it >= 0 && measuredAtMs > 0 && now >= measuredAtMs && now - measuredAtMs < 7 * 86_400_000L
    }

    fun coolingDown(now: Long) = failedAtMs > 0 && now >= failedAtMs && now - failedAtMs < 60_000
}

/** Stable ordering keeps the manually selected plugin first when no measurements exist. */
internal fun lxOrderedSources(sources: List<LxStoredSource>, selected: String, now: Long): List<LxStoredSource> =
    sources.sortedWith(compareBy<LxStoredSource> { it.performance.coolingDown(now) }
        .thenBy { it.performance.recentTime(now) ?: Long.MAX_VALUE }
        .thenBy { it.info.id != selected })

/** Cancellation belongs to the caller and must never start another plugin. */
internal suspend fun <T> lxTrySources(sources: List<LxStoredSource>, resolve: suspend (LxStoredSource) -> T): T {
    if (sources.isEmpty()) throw LxSourceException("请先在音源与网络中导入落雪音源")
    for ((index, source) in sources.withIndex()) {
        currentCoroutineContext().ensureActive()
        try {
            return resolve(source)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: LxSourceException) {
            if (index == sources.lastIndex) throw error
        }
    }
    error("No LX sources")
}
