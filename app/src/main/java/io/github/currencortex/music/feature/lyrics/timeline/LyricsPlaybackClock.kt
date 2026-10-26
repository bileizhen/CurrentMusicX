package io.github.currencortex.music.feature.lyrics.timeline

import kotlin.math.abs

/** Smooths small IPC sampling drift; seeks, track changes and pauses stay authoritative. */
class LyricsPlaybackClock {
    private var songIdentity: Any? = null
    private var sample: PlaybackAnchor? = null
    private var anchor = PlaybackAnchor(0, 0, false)
    private var correctionMs = 0L
    private var correctionDurationMs = 300.0
    /** The caller includes platform/server identity, not just a platform-local numeric id. */
    fun update(id: Any?, next: PlaybackAnchor) {
        if (id == songIdentity && next == sample) return
        val previous = positionAt(next.realtimeMs)
        val snap = sample == null || id != songIdentity || next.playing != anchor.playing ||
            next.speed != anchor.speed || next.revision != anchor.revision || !next.playing || abs(next.positionMs - previous) > 180
        anchor = if (snap) next else next.copy(positionMs = previous)
        correctionMs = if (snap) 0 else next.positionMs - previous
        // Smoothstep has a maximum slope of 1.5. Never let a negative correction
        // outrun playback, including at slow playback speeds.
        correctionDurationMs = maxOf(300.0, abs(correctionMs) * 1.6 / next.speed.coerceAtLeast(.01f))
        songIdentity = id; sample = next
    }
    fun positionAt(realtimeMs: Long): Long {
        val progress = ((realtimeMs - anchor.realtimeMs) / correctionDurationMs).coerceIn(0.0, 1.0)
        val correction = (correctionMs * progress * progress * (3 - 2 * progress)).toLong()
        return (anchor.positionAt(realtimeMs) + correction).coerceIn(0, anchor.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
    }
}
