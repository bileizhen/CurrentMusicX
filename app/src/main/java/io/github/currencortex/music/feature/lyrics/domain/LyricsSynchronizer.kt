package io.github.currencortex.music.feature.lyrics.domain

import io.github.currencortex.music.feature.lyrics.model.*

data class LyricsInterlude(val startTimeMs: Long, val endTimeMs: Long?, val label: String)

/** Indexed interval lookup supports simultaneous duet rows and end-time gaps. */
class LyricsSynchronizer(private val document: LyricsDocument) {
    private data class Interval(val index: Int, val start: Long, val end: Long)
    private val intervals = document.lines.mapIndexed { index, line ->
        Interval(index, minOf(line.startTimeMs, line.backgroundVocals.minOfOrNull { it.startTimeMs } ?: line.startTimeMs),
            maxOf(line.endTimeMs, line.backgroundVocals.maxOfOrNull { it.endTimeMs } ?: line.endTimeMs))
    }.sortedBy { it.start }
    private val prefixEnd = LongArray(intervals.size).also { result ->
        intervals.forEachIndexed { index, row -> result[index] = maxOf(row.end, result.getOrElse(index - 1) { 0 }) }
    }
    private val prefixEndIndex = IntArray(intervals.size).also { result ->
        intervals.forEachIndexed { index, row ->
            result[index] = if (index == 0 || row.end >= prefixEnd[index - 1]) row.index else result[index - 1]
        }
    }
    private fun lastStarted(positionMs: Long): Int {
        var low = 0; var high = intervals.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (intervals[mid].start <= positionMs) low = mid + 1 else high = mid - 1
        }
        return high
    }
    fun activeLines(positionMs: Long): List<Int> {
        val active = mutableListOf<Int>()
        var index = lastStarted(positionMs)
        while (index >= 0 && prefixEnd[index] > positionMs) {
            if (intervals[index].end > positionMs) active.add(intervals[index].index)
            index--
        }
        return active.sorted()
    }
    fun findCurrentLine(positionMs: Long): Int = activeLines(positionMs).lastOrNull() ?: -1
    fun scrollTarget(positionMs: Long): Int = findCurrentLine(positionMs).takeIf { it >= 0 }
        // An overlapping vocal can end after a later-starting row. Keep the most
        // recently finished paragraph at the reading anchor throughout the silence.
        ?: prefixEndIndex.getOrNull(lastStarted(positionMs)) ?: intervals.firstOrNull()?.index ?: 0
    fun interlude(positionMs: Long): LyricsInterlude? {
        if (intervals.isEmpty() || activeLines(positionMs).isNotEmpty()) return null
        val previous = lastStarted(positionMs)
        val begin = prefixEnd.getOrElse(previous) { 0 }
        val end = intervals.getOrNull(previous + 1)?.start ?: document.metadata.durationMs
        if (end != null && (end - begin < 2000 || positionMs >= end)) return null
        return LyricsInterlude(begin, end, if (previous < 0) "前奏" else if (previous == intervals.lastIndex) "尾奏" else "间奏")
    }
    fun findCurrentWord(line: LyricLine, positionMs: Long): Int {
        var low = 0; var high = line.words.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (line.words[mid].startTimeMs <= positionMs) low = mid + 1 else high = mid - 1
        }
        return high.takeIf { it >= 0 && positionMs < line.words[it].endTimeMs } ?: -1
    }
    companion object {
        fun wordProgress(word: LyricWord, positionMs: Long): Float {
            if (word.endTimeMs <= word.startTimeMs) return if (positionMs >= word.endTimeMs) 1f else 0f
            return ((positionMs.toDouble() - word.startTimeMs) / (word.endTimeMs.toDouble() - word.startTimeMs)).toFloat().coerceIn(0f, 1f)
        }
        fun effectivePosition(positionMs: Long, offsetMs: Long): Long =
            if (offsetMs > 0 && positionMs > Long.MAX_VALUE - offsetMs) Long.MAX_VALUE
            else if (offsetMs < 0 && positionMs < Long.MIN_VALUE - offsetMs) 0
            else (positionMs + offsetMs).coerceAtLeast(0)
        fun seekPosition(lineStartMs: Long, offsetMs: Long): Long = effectivePosition(lineStartMs, -offsetMs.coerceIn(-60000, 60000))
    }
}
