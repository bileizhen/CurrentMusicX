package io.github.currencortex.music.core.media.engine

import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import io.github.currencortex.music.data.settings.AudioBufferPolicy

data class AudioBufferBudget(val minMs: Int, val maxMs: Int, val startMs: Int, val resumeMs: Int, val maxBytes: Int)
/** Network decoder buffering and native device/ring buffering are different budgets. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioBufferController {
    @Volatile var policy: AudioBufferPolicy = AudioBufferPolicy.BALANCED
    fun budget() = budget(policy)
    fun createLoadControl(): LoadControl {
        val original = DefaultLoadControl.Builder().setBufferDurationsMs(20_000, 60_000, 2_500, 5_000)
            .setTargetBufferBytes(32 * 1024 * 1024).setPrioritizeTimeOverSizeThresholds(true).build()
        // Java default interface methods are not forwarded by Kotlin `by` delegation.
        // Forward the current Media3 lifecycle explicitly so its allocator stays registered.
        return object : LoadControl {
            private var loading = false
            override fun onPrepared(playerId: PlayerId) { loading = false; original.onPrepared(playerId) }
            override fun onTracksSelected(parameters: LoadControl.Parameters, trackGroups: TrackGroupArray,
                trackSelections: Array<out ExoTrackSelection?>) = original.onTracksSelected(parameters, trackGroups, trackSelections)
            override fun onStopped(playerId: PlayerId) { loading = false; original.onStopped(playerId) }
            override fun onReleased(playerId: PlayerId) = original.onReleased(playerId)
            override fun getAllocator(playerId: PlayerId) = original.getAllocator(playerId)
            override fun getBackBufferDurationUs(playerId: PlayerId) = original.getBackBufferDurationUs(playerId)
            override fun retainBackBufferFromKeyframe(playerId: PlayerId) = original.retainBackBufferFromKeyframe(playerId)
            override fun shouldContinuePreloading(playerId: PlayerId, timeline: Timeline, mediaPeriodId: MediaPeriodId,
                bufferedDurationUs: Long) = original.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs)
            override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
                val budget = budget()
                val runtime = Runtime.getRuntime()
                val headroom = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()
                loading = shouldLoad(budget, parameters.bufferedDurationUs, parameters.playbackSpeed,
                    original.getAllocator(parameters.playerId).totalBytesAllocated, loading, headroom)
                return loading
            }
            override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
                val budget = budget()
                var minimum = (if (parameters.rebuffering) budget.resumeMs else budget.startMs) * 1000L
                if (parameters.targetLiveOffsetUs != C.TIME_UNSET) minimum = minOf(minimum, parameters.targetLiveOffsetUs / 2)
                return parameters.bufferedDurationUs / parameters.playbackSpeed.coerceAtLeast(.1f) >= minimum
            }
        }
    }
    companion object {
        /** Permit time-first buffering only with heap headroom, and bound high-bitrate overshoot. */
        internal fun shouldLoad(budget: AudioBufferBudget, bufferedUs: Long, speed: Float,
            allocatedBytes: Int, wasLoading: Boolean, heapHeadroomBytes: Long): Boolean {
            if (allocatedBytes.toLong() >= budget.maxBytes.toLong() * 2) return false
            val atTarget = allocatedBytes >= budget.maxBytes
            if (atTarget && heapHeadroomBytes < 24L * 1024 * 1024) return false
            val minimum = (budget.minMs * 1000L * speed.coerceAtLeast(.1f)).toLong().coerceAtMost(budget.maxMs * 1000L)
            return when {
                bufferedUs < minimum -> true
                bufferedUs >= budget.maxMs * 1000L || atTarget -> false
                else -> wasLoading
            }
        }
        fun budget(policy: AudioBufferPolicy): AudioBufferBudget = when (policy) {
            AudioBufferPolicy.BALANCED -> AudioBufferBudget(20_000, 60_000, 2_500, 5_000, 32 * 1024 * 1024)
            AudioBufferPolicy.RESPONSIVE -> AudioBufferBudget(8_000, 30_000, 1_000, 2_500, 24 * 1024 * 1024)
            AudioBufferPolicy.RESILIENT -> AudioBufferBudget(30_000, 90_000, 5_000, 8_000, 48 * 1024 * 1024)
        }
    }
}
