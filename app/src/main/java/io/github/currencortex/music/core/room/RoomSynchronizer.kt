package io.github.currencortex.music.core.room

import io.github.currencortex.music.data.room.RoomTimeline
import kotlin.math.abs

/** The clock is anchored to monotonic time so changing the phone clock cannot seek audio. */
class RoomSynchronizer {
    private var serverAnchor = 0L
    private var monotonicAnchor = 0L
    private var bestRtt = Long.MAX_VALUE
    val ready get() = bestRtt != Long.MAX_VALUE
    fun sample(serverNow: Long, sent: Long, received: Long) {
        val rtt = received - sent
        if (rtt < 0 || rtt > bestRtt) return
        bestRtt = rtt; serverAnchor = serverNow + rtt / 2; monotonicAnchor = received
    }
    fun reset() { bestRtt = Long.MAX_VALUE }
    fun position(timeline: RoomTimeline, now: Long): Long = (timeline.basePosition + if (timeline.playing)
        (serverAnchor + (now - monotonicAnchor).coerceAtLeast(0) - timeline.baseAt).coerceAtLeast(0) else 0).coerceAtLeast(0)
    data class Correction(val position: Long, val seek: Boolean, val speed: Float)
    fun correction(timeline: RoomTimeline, now: Long, actual: Long): Correction {
        val position = position(timeline, now); val drift = position - actual
        return Correction(position, abs(drift) > 1000 || !timeline.playing && abs(drift) >= 250,
            if (!timeline.playing || abs(drift) < 250 || abs(drift) > 1000) 1f else if (drift > 0) 1.04f else .96f)
    }
}
