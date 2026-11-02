package io.github.currencortex.music.feature.player

import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import io.github.currencortex.music.feature.lyrics.timeline.LyricsPlaybackClock
import io.github.currencortex.music.feature.lyrics.timeline.PlaybackAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiniLyricTimelineTest {
    @Test fun cuesIncludeOverlappingRowsAndVocalOnlyChanges() {
        val timeline = MiniLyricTimeline(LyricsDocument(lines = listOf(
            LyricLine(100, 400, "first"),
            LyricLine(200, 300, "duet"),
            LyricLine(240, 310, " "),
            LyricLine(600, 800, "", backgroundVocals = listOf(
                LyricLine(580, 650, "backing"), LyricLine(650, 700, "echo"),
            )),
        )))
        val clock = LyricsPlaybackClock().apply { update("track", PlaybackAnchor(0, 0, true)) }
        fun nextAt(now: Long) = timeline.nextCheckMs(clock, now, 0)?.plus(now)
        assertEquals(120L, timeline.nextCheckMs(clock, 0, -20))
        assertEquals("duet", timeline.textAt(200))
        assertEquals(300L, nextAt(200))
        assertEquals("duet", timeline.textAt(250))
        assertEquals("first", timeline.textAt(300))
        assertEquals(580L, nextAt(300))
        assertEquals("first", timeline.textAt(400))
        assertEquals("backing", timeline.textAt(580))
        assertEquals(650L, nextAt(580))
        assertEquals(650L, nextAt(600))
        assertEquals("echo", timeline.textAt(650))
        assertNull(nextAt(650))
        assertEquals("echo", timeline.textAt(700))
        assertEquals("echo", timeline.textAt(5000))
    }

    @Test fun seeksResolvePreludeInterludeAndOutroFromTheRequestedPosition() {
        val timeline = MiniLyricTimeline(LyricsDocument(lines = listOf(
            LyricLine(100, 400, "verse"),
            LyricLine(1000, 1200, ""),
            LyricLine(1400, 1800, "verse"),
            LyricLine(10_000, 10_400, "chorus"),
        )))
        val clock = LyricsPlaybackClock().apply { update("track", PlaybackAnchor(100, 100, true)) }
        assertNull(timeline.nextCheckMs(clock, 100, 0))
        assertEquals("chorus", timeline.textAt(20_000))
        assertEquals("verse", timeline.textAt(9999))
        assertEquals("", timeline.textAt(99))
        assertEquals("chorus", timeline.textAt(10_000))

        val empty = MiniLyricTimeline(LyricsDocument(lines = listOf(LyricLine(0, 300, " "))))
        assertEquals("", empty.textAt(100))
        assertEquals("", empty.textAt(500))
    }

    @Test fun shortCueIsNotSkippedDuringPositiveDriftCorrection() {
        val timeline = MiniLyricTimeline(LyricsDocument(lines = listOf(
            LyricLine(1000, 2000, "verse"),
            LyricLine(1500, 1520, "short"),
        )))
        val clock = LyricsPlaybackClock().apply {
            update("track", PlaybackAnchor(1000, 1000, true))
            update("track", PlaybackAnchor(1480, 1300, true))
        }
        val enter = (1300L..1500L).first { clock.positionAt(it) >= 1500L }
        val leave = (enter..1500L).first { clock.positionAt(it) >= 1520L }
        assertEquals(enter - 1300, timeline.nextCheckMs(clock, 1300, 0))
        assertEquals("short", timeline.textAt(clock.positionAt(enter)))
        assertEquals(leave - enter, timeline.nextCheckMs(clock, enter, 0))
        assertEquals("verse", timeline.textAt(clock.positionAt(leave)))
    }

    @Test fun negativeDriftCanEnterThenLeaveACueAgainAtTheTrackEnd() {
        val timeline = MiniLyricTimeline(LyricsDocument(lines = listOf(LyricLine(1580, 1610, "tail"))))
        val clock = LyricsPlaybackClock().apply {
            update("track", PlaybackAnchor(1250, 1000, true, durationMs = 1600))
            update("track", PlaybackAnchor(1450, 1300, true, durationMs = 1600))
        }
        val expected = (1300L..1600L).map { it to timeline.textAt(clock.positionAt(it)) }
            .zipWithNext().filter { (before, after) -> before.second != after.second }.map { it.second }
        val observed = mutableListOf<Pair<Long, String>>()
        var time = 1300L
        var text = timeline.textAt(clock.positionAt(time))
        while (time < 1600) {
            time += timeline.nextCheckMs(clock, time, 0) ?: break
            val next = timeline.textAt(clock.positionAt(time))
            if (text != next) observed.add(time to next)
            text = next
        }
        assertEquals(listOf("tail", ""), expected.map { it.second })
        assertEquals(expected, observed)
    }

    @Test fun resubscribingDoesNotReanchorAnUntimestampedSample() {
        val sample = PlaybackAnchor(1000, 0, true)
        val clock = LyricsPlaybackClock()
        clock.update("track", sample, fallbackRealtimeMs = 1000)
        clock.update("track", sample, fallbackRealtimeMs = 1250)
        assertEquals(1300L, clock.positionAt(1300))
    }
}
