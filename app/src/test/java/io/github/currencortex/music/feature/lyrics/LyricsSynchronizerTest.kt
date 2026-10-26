package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.feature.lyrics.domain.LyricsSynchronizer
import io.github.currencortex.music.feature.lyrics.model.*
import org.junit.Assert.*
import org.junit.Test

class LyricsSynchronizerTest {
    private val word = LyricWord("Hello", 1000, 2000, 0, 5)
    private val line = LyricLine(1000, 2500, "Hello", listOf(word))
    @Test fun endExclusiveGapsAndBackwardSeekWork() {
        val sync = LyricsSynchronizer(LyricsDocument(listOf(line, LyricLine(6000, 8000, "World"))))
        assertEquals(-1, sync.findCurrentLine(999))
        assertEquals(0, sync.findCurrentLine(1000))
        assertEquals(-1, sync.findCurrentLine(2500))
        assertEquals("间奏", sync.interlude(4000)!!.label)
        assertEquals(0, sync.scrollTarget(4000))
        assertEquals(1, sync.findCurrentLine(6000))
        assertEquals(0, sync.findCurrentLine(1200))
    }
    @Test fun duetOverlapsAndBackgroundAfterMainRemainActive() {
        val sync = LyricsSynchronizer(LyricsDocument(listOf(line.copy(backgroundVocals = listOf(LyricLine(2000, 4000, "Echo", isBackground = true))), LyricLine(2000, 3000, "Duet", isDuet = true))))
        assertEquals(listOf(0, 1), sync.activeLines(2200))
        assertEquals(listOf(0), sync.activeLines(3500))
        assertTrue(sync.activeLines(4000).isEmpty())
    }
    @Test fun wordProgressAndGapsClampAndAvoidZeroDivision() {
        val sync = LyricsSynchronizer(LyricsDocument(listOf(line)))
        assertEquals(-1, sync.findCurrentWord(line, 999))
        assertEquals(0, sync.findCurrentWord(line, 1500))
        assertEquals(-1, sync.findCurrentWord(line, 2000))
        assertEquals(0f, LyricsSynchronizer.wordProgress(word, 0), 0f)
        assertEquals(.5f, LyricsSynchronizer.wordProgress(word, 1500), 0f)
        assertEquals(1f, LyricsSynchronizer.wordProgress(word, 3000), 0f)
        assertEquals(1f, LyricsSynchronizer.wordProgress(word.copy(endTimeMs = 1000), 1000), 0f)
    }
    @Test fun silenceKeepsTheLastEndingVocalWithoutInventingAnActiveLine() {
        val sync = LyricsSynchronizer(LyricsDocument(listOf(
            line.copy(backgroundVocals = listOf(LyricLine(2000, 5000, "Echo", isBackground = true))),
            LyricLine(2000, 3000, "Duet", isDuet = true),
            LyricLine(10000, 12000, "Next")), LyricsMetadata(durationMs = 18000)))
        assertEquals(0, sync.scrollTarget(999))
        assertEquals(0, sync.scrollTarget(4999))
        assertTrue(sync.activeLines(5000).isEmpty())
        assertEquals(0, sync.scrollTarget(5000))
        assertEquals(0, sync.scrollTarget(9999))
        assertEquals("间奏", sync.interlude(5000)!!.label)
        assertEquals(2, sync.scrollTarget(10000))
        assertTrue(sync.activeLines(15000).isEmpty())
        assertEquals(2, sync.scrollTarget(15000))
        assertEquals("尾奏", sync.interlude(15000)!!.label)
        assertEquals(0, sync.scrollTarget(6000))
    }
    @Test fun offsetsInvertSeekAndSaturate() {
        assertEquals(1200L, LyricsSynchronizer.effectivePosition(1000, 200))
        assertEquals(800L, LyricsSynchronizer.seekPosition(1000, 200))
        assertEquals(0L, LyricsSynchronizer.effectivePosition(1000, -2000))
        assertEquals(Long.MAX_VALUE, LyricsSynchronizer.effectivePosition(Long.MAX_VALUE, 1))
        assertEquals(-1, LyricsSynchronizer(LyricsDocument()).findCurrentLine(0))
    }

    @Test fun delayedFirstCueUsesNegativeLyricTimeUntilTheActualStart() {
        val sync = LyricsSynchronizer(LyricsDocument(listOf(LyricLine(0, 1000, "First"))))
        assertEquals(-250L, LyricsSynchronizer.lyricPosition(0, -250))
        assertEquals(-1, sync.findCurrentLine(LyricsSynchronizer.lyricPosition(249, -250)))
        assertEquals(0, sync.findCurrentLine(LyricsSynchronizer.lyricPosition(250, -250)))
        assertEquals(Long.MIN_VALUE, LyricsSynchronizer.lyricPosition(Long.MIN_VALUE, -1))
        assertEquals(Long.MAX_VALUE, LyricsSynchronizer.lyricPosition(Long.MAX_VALUE, 1))
    }

    @Test fun songAndDeviceOffsetsInvertWithoutClippingAndNeverSeekToTrackEnd() {
        assertEquals(125_000L, LyricsSynchronizer.seekPosition(5_000, -120_000))
        assertEquals(5_000L, LyricsSynchronizer.seekPosition(125_000, 120_000))
        assertEquals(0L, LyricsSynchronizer.seekPosition(1_000, 120_000))
        assertNull(LyricsSynchronizer.seekPositionOrNull(9_500, -500, 10_000))
        assertNull(LyricsSynchronizer.seekPositionOrNull(10_000, 0, 10_000))
        assertEquals(9_999L, LyricsSynchronizer.seekPositionOrNull(9_500, -499, 10_000))
        assertEquals(125_000L, LyricsSynchronizer.seekPositionOrNull(5_000, -120_000, 0))
        assertEquals(Long.MAX_VALUE, LyricsSynchronizer.seekPosition(0, Long.MIN_VALUE))
        assertEquals(0L, LyricsSynchronizer.seekPosition(Long.MIN_VALUE, Long.MAX_VALUE))
    }
}
