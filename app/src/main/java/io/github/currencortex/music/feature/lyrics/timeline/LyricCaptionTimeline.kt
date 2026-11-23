package io.github.currencortex.music.feature.lyrics.timeline

import io.github.currencortex.music.feature.lyrics.domain.LyricsSynchronizer
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument

/**
 * One caption line for compact surfaces such as the mini player, the system media card and
 * Bluetooth AVRCP. A caption holds through silence until the next visible line, including after
 * a seek, so those surfaces never flash empty text between phrases.
 */
internal class LyricCaptionTimeline(document: LyricsDocument) {
    private data class Cue(val startMs: Long, val text: String)

    private val cues = buildList<Cue> {
        val synchronizer = LyricsSynchronizer(document)
        val boundaries = sortedSetOf<Long>()
        for (line in document.lines) {
            boundaries.add(line.startTimeMs)
            boundaries.add(line.endTimeMs)
            for (vocal in line.backgroundVocals) {
                boundaries.add(vocal.startTimeMs)
                boundaries.add(vocal.endTimeMs)
            }
        }
        for (time in boundaries) {
            val text = synchronizer.activeLines(time).asReversed().firstNotNullOfOrNull { index ->
                val line = document.lines[index]
                line.text.takeIf { it.isNotBlank() } ?: line.backgroundVocals.firstOrNull {
                    time >= it.startTimeMs && time < it.endTimeMs && it.text.isNotBlank()
                }?.text
            }
            if (text != null && text != lastOrNull()?.text) add(Cue(time, text))
        }
    }

    private fun cueIndex(position: Long): Int {
        val index = cues.binarySearchBy(position) { it.startMs }
        return if (index >= 0) index else -index - 2
    }

    fun textAt(position: Long): String = cues.getOrNull(cueIndex(position))?.text.orEmpty()

    fun nextCheckMs(clock: LyricsPlaybackClock, now: Long, offset: Long): Long? {
        val index = cueIndex(LyricsSynchronizer.lyricPosition(clock.positionAt(now), offset))
        val start = cues.getOrNull(index)?.startMs ?: Long.MIN_VALUE
        val end = cues.getOrNull(index + 1)?.startMs
        return clock.nextIntervalCheckMs(now) { playbackPosition ->
            val position = LyricsSynchronizer.lyricPosition(playbackPosition, offset)
            position >= start && (end == null || position < end)
        }
    }
}
