package io.github.currencortex.music.feature.lyrics.timeline

import io.github.currencortex.music.feature.lyrics.model.LyricsDocument

class LyricsTimeline(private val document: LyricsDocument) {
    private val synchronizer = io.github.currencortex.music.feature.lyrics.domain.LyricsSynchronizer(document)
    /** -1 in instrumental gaps; end time is meaningful for TTML and YRC. */
    fun lineAt(positionMs: Long): Int = synchronizer.findCurrentLine(positionMs)
}

/** Monotonic UI interpolation only; Media3 remains the authoritative clock. */
data class PlaybackAnchor(val positionMs: Long, val realtimeMs: Long, val playing: Boolean,
    val durationMs: Long = 0, val speed: Float = 1f) {
    fun positionAt(nowMs: Long): Long {
        // DLNA samples every two seconds; allow that interval while bounding stale anchors.
        val elapsed = if (playing) ((nowMs - realtimeMs).coerceIn(0, 3000) * speed).toLong() else 0
        return (positionMs + elapsed).coerceIn(0, durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
    }
}
