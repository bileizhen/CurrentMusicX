package io.github.currencortex.music.feature.lyrics.timeline

import kotlin.math.abs

/** Smooths small IPC sampling drift; seeks, track changes and pauses stay authoritative. */
class LyricsPlaybackClock {
    private var songId: Long? = null
    private var sample: PlaybackAnchor? = null
    private var anchor = PlaybackAnchor(0, 0, false)
    private var correctionMs = 0L
    private var correctionDurationMs = 300.0
    fun update(id: Long?, next: PlaybackAnchor) {
        if (id == songId && next == sample) return
        val previous = positionAt(next.realtimeMs)
        val snap = sample == null || id != songId || next.playing != anchor.playing ||
            next.speed != anchor.speed || !next.playing || abs(next.positionMs - previous) > 180
        anchor = if (snap) next else next.copy(positionMs = previous)
        correctionMs = if (snap) 0 else next.positionMs - previous
        // Smoothstep has a maximum slope of 1.5. Never let a negative correction
        // outrun playback, including at slow playback speeds.
        correctionDurationMs = maxOf(300.0, abs(correctionMs) * 1.6 / next.speed.coerceAtLeast(.01f))
        songId = id; sample = next
    }
    fun positionAt(realtimeMs: Long): Long {
        val progress = ((realtimeMs - anchor.realtimeMs) / correctionDurationMs).coerceIn(0.0, 1.0)
        val correction = (correctionMs * progress * progress * (3 - 2 * progress)).toLong()
        return (anchor.positionAt(realtimeMs) + correction).coerceIn(0, anchor.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
    }
}
