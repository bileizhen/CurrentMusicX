package io.github.currencortex.music.feature.lyrics.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

@Serializable data class LyricsTimeAnchor(val sourceMs: Long, val playbackMs: Long)

/** A separate timing map: the provider's original timestamps and manual song correction survive. */
@Serializable data class LyricsTimingAlignment(val anchors: List<LyricsTimeAnchor>, val matchedLines: Int,
    val confidence: Double) {
    init {
        require(anchors.size in 1..128 && matchedLines in 1..2048 && confidence.isFinite() && confidence in 0.0..1.0)
        require(anchors.all { it.sourceMs in 0..86_400_000 && it.playbackMs in 0..86_400_000 &&
            kotlin.math.abs(it.playbackMs - it.sourceMs) <= 60_000 })
        require(anchors.zipWithNext().all { (a, b) -> a.sourceMs < b.sourceMs && a.playbackMs < b.playbackMs })
    }

    val initialOffsetMs: Long get() = anchors.first().playbackMs - anchors.first().sourceMs
    val varying: Boolean get() = anchors.size > 1

    fun mapTime(timeMs: Long): Long {
        val first = anchors.first()
        val last = anchors.last()
        if (timeMs <= first.sourceMs) return (timeMs + initialOffsetMs).coerceAtLeast(0)
        if (timeMs >= last.sourceMs) return (timeMs + last.playbackMs - last.sourceMs).coerceAtLeast(0)
        var low = 0
        var high = anchors.lastIndex
        while (low + 1 < high) {
            val middle = (low + high) ushr 1
            if (anchors[middle].sourceMs <= timeMs) low = middle else high = middle
        }
        val a = anchors[low]
        val b = anchors[high]
        return (a.playbackMs + (timeMs - a.sourceMs).toDouble() *
            (b.playbackMs - a.playbackMs) / (b.sourceMs - a.sourceMs)).roundToLong().coerceAtLeast(0)
    }

    fun applyTo(document: LyricsDocument): LyricsDocument {
        fun line(value: LyricLine): LyricLine = value.copy(startTimeMs = mapTime(value.startTimeMs),
            endTimeMs = mapTime(value.endTimeMs), words = value.words.map {
                it.copy(startTimeMs = mapTime(it.startTimeMs), endTimeMs = mapTime(it.endTimeMs))
            }, backgroundVocals = value.backgroundVocals.map(::line))
        return document.copy(lines = document.lines.map(::line),
            metadata = document.metadata.copy(durationMs = document.metadata.durationMs?.let(::mapTime)))
    }
}
