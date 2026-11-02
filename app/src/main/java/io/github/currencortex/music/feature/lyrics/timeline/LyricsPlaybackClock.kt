package io.github.currencortex.music.feature.lyrics.timeline

import kotlin.math.abs
import kotlin.math.ceil

/** Smooths small IPC sampling drift; seeks, track changes and pauses stay authoritative. */
class LyricsPlaybackClock {
    private var songIdentity: Any? = null
    private var sample: PlaybackAnchor? = null
    private var anchor = PlaybackAnchor(0, 0, false)
    private var correctionMs = 0L
    private var correctionDurationMs = 300.0
    /** The caller includes platform/server identity, not just a platform-local numeric id. */
    fun update(id: Any?, next: PlaybackAnchor, fallbackRealtimeMs: Long = next.realtimeMs) {
        if (id == songIdentity && next == sample) return
        val timed = if (next.realtimeMs <= 0 && fallbackRealtimeMs != next.realtimeMs) next.copy(realtimeMs = fallbackRealtimeMs) else next
        val previous = positionAt(timed.realtimeMs)
        val snap = sample == null || id != songIdentity || next.playing != anchor.playing ||
            next.speed != anchor.speed || next.revision != anchor.revision || !next.playing || abs(next.positionMs - previous) > 180
        anchor = if (snap) timed else timed.copy(positionMs = previous)
        correctionMs = if (snap) 0 else next.positionMs - previous
        // Smoothstep has a maximum slope of 1.5. Never let a negative correction
        // outrun playback, including at slow playback speeds.
        correctionDurationMs = maxOf(300.0, abs(correctionMs) * 1.6 / next.speed.coerceAtLeast(.01f))
        songIdentity = id; sample = next
    }
    private fun correctionAt(realtimeMs: Long): Long {
        val progress = ((realtimeMs - anchor.realtimeMs) / correctionDurationMs).coerceIn(0.0, 1.0)
        return (correctionMs * progress * progress * (3 - 2 * progress)).toLong()
    }
    private fun corrected(base: Long, correction: Long) =
        (base + correction).coerceIn(0, anchor.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)

    fun positionAt(realtimeMs: Long): Long = corrected(anchor.positionAt(realtimeMs), correctionAt(realtimeMs))

    /** First possible exit from the contiguous media-time interval containing the current position. */
    internal fun nextIntervalCheckMs(now: Long, contains: (Long) -> Boolean): Long? {
        if (!anchor.playing || anchor.speed <= 0f) return null
        val base = anchor.positionAt(now)
        val correction = correctionAt(now)
        // Base time only advances. The correction is monotonic in either direction, even
        // after the base stops at the track end. These bounds also cover integer rounding.
        fun mayExitBy(time: Long): Boolean {
            val laterCorrection = correctionAt(time)
            return !contains(corrected(base, minOf(correction, laterCorrection))) ||
                !contains(corrected(anchor.positionAt(time), maxOf(correction, laterCorrection)))
        }
        var last = anchor.realtimeMs + maxOf(PlaybackAnchor.MAX_EXTRAPOLATION_MS, ceil(correctionDurationMs).toLong())
        if (last <= now || !mayExitBy(last)) return null
        var first = now + 1
        while (first < last) {
            val mid = first + (last - first) / 2
            if (mayExitBy(mid)) last = mid else first = mid + 1
        }
        return first - now
    }
}
