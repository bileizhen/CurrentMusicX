package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.domain.LyricsTimingAligner
import io.github.currencortex.music.feature.lyrics.domain.LyricsSynchronizer
import io.github.currencortex.music.feature.lyrics.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricsTimingAlignerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val source = LyricsDocument((0 until 12).map { i ->
        val start = 15_000L + i * 5000
        val text = "Corresponding lyric verse $i"
        LyricLine(start, start + 3000, text,
            listOf(LyricWord(text, start, start + 3000, 0, text.length)),
            backgroundVocals = listOf(LyricLine(start + 100, start + 800, "Backing")))
    })
    private fun reference(offset: Long = -10_868, speed: Double = 1.0) = source.copy(lines = source.lines.map { line ->
        fun time(t: Long) = (t * speed).toLong() + offset
        line.copy(startTimeMs = time(line.startTimeMs), endTimeMs = time(line.endTimeMs), words = emptyList(), backgroundVocals = emptyList())
    })

    @Test fun constantOffsetUsesMultipleCorrespondencesAndKeepsAllOriginalWordData() {
        val alignment = LyricsTimingAligner.align(reference(), source)!!
        assertFalse(alignment.varying)
        assertEquals(-10_868L, alignment.initialOffsetMs)
        val mapped = alignment.applyTo(source)
        assertEquals(4132L, mapped.lines.first().startTimeMs)
        assertEquals(4132L, mapped.lines.first().words.single().startTimeMs)
        assertEquals(4232L, mapped.lines.first().backgroundVocals.single().startTimeMs)
        assertEquals(15_000L, source.lines.first().startTimeMs)
        assertEquals(source.lines.map { it.text }, mapped.lines.map { it.text })
    }

    @Test fun gradualDriftIsCorrectedAcrossTheSongRatherThanApplyingOnlyTheFirstOffset() {
        val reference = reference(-10_000, .97)
        val alignment = LyricsTimingAligner.align(reference, source)!!
        assertTrue(alignment.varying)
        source.lines.zip(reference.lines).forEach { (from, to) -> assertEquals(to.startTimeMs, alignment.mapTime(from.startTimeMs)) }
        val middle = source.lines[5].startTimeMs + 1000
        assertEquals((middle * .97).toLong() - 10_000, alignment.mapTime(middle))
        val mapped = alignment.applyTo(source)
        LyricsFileCodec.validate(mapped)
        assertTrue(mapped.lines.first().words.single().endTimeMs > mapped.lines.first().words.single().startTimeMs)
    }

    @Test fun reorderedOrPartialLyricsDoNotCreateAnAutomaticClockMap() {
        assertNull(LyricsTimingAligner.align(reference().copy(lines = reference().lines.reversed()), source))
        assertNull(LyricsTimingAligner.align(reference().copy(lines = reference().lines.take(3)), source))
        assertNull(LyricsTimingAligner.align(reference().copy(lines = reference().lines.map { it.copy(text = "Unrelated contents") }), source))
    }

    @Test fun punctuationAndRepeatedChorusesKeepTheirOrder() {
        val repeated = source.copy(lines = source.lines.mapIndexed { i, line -> line.copy(text = "Verse ${i % 6}", words = emptyList()) })
        val baseline = repeated.copy(lines = repeated.lines.map { it.copy(startTimeMs = it.startTimeMs - 5000,
            endTimeMs = it.endTimeMs - 5000, text = " ${it.text.uppercase()}! ") })
        val alignment = LyricsTimingAligner.align(baseline, repeated)!!
        assertEquals(-5000L, alignment.initialOffsetMs)
        assertEquals(60_000L, alignment.mapTime(65_000))
    }

    @Test fun anIsolatedBadReferenceTimestampDoesNotWarpTheWholeSong() {
        val rows = reference().lines.toMutableList()
        rows[5] = rows[5].copy(startTimeMs = rows[5].startTimeMs + 2500)
        val alignment = LyricsTimingAligner.align(LyricsDocument(rows), source)!!
        assertEquals(-10_868L, alignment.initialOffsetMs)
        assertEquals(29_132L, alignment.mapTime(40_000))
        assertEquals(11, alignment.matchedLines)
    }

    @Test fun noChangeOrUnsupportedTempoAndHugeInputYieldNoAlignment() {
        assertNull(LyricsTimingAligner.align(source, source))
        assertNull(LyricsTimingAligner.align(reference(0, 1.6), source))
        assertNull(LyricsTimingAligner.align(source.copy(lines = List(2049) { source.lines.first() }), source))
    }

    @Test fun mapIsMonotonicClampsLeadInAndExtrapolatesOnlyTheLastOffset() {
        val alignment = LyricsTimingAlignment(listOf(LyricsTimeAnchor(15_000, 4000), LyricsTimeAnchor(70_000, 57_000)), 12, .99)
        assertEquals(0L, alignment.mapTime(0))
        assertEquals(67_000L, alignment.mapTime(80_000))
        assertTrue((0L..80_000L step 100).map(alignment::mapTime).zipWithNext().all { (a, b) -> a <= b })
        assertTrue(runCatching { LyricsTimingAlignment(listOf(LyricsTimeAnchor(1, 2), LyricsTimeAnchor(1, 3)), 2, .9) }.isFailure)
    }

    @Test fun storedAlignmentAndFiaRoundTripPreserveRawTimingAndIndependentManualOffset() = runBlocking {
        val song = Song(7, "Fixture", "Singer")
        val alignment = LyricsTimingAligner.align(reference(), source)!!
        val saved = SavedLyrics(source, LyricsOrigin("AMLL", "amll"), alignment)
        val directory = temporary.newFolder()
        val store = SongLyricsStore(directory)
        store.setOffset(song, 250)
        store.saveMatched(song, saved)
        val restored = SongLyricsStore(directory)
        assertEquals(saved, restored.active(song))
        assertEquals(250L, restored.settings(song).offsetMs)
        val exported = LyricsFileCodec.encodeFia(song, source, saved.origin, 250, alignment)
        val imported = LyricsFileCodec.decode(exported.toByteArray(), "fixture.fia")
        assertEquals(source, imported.document)
        assertEquals(alignment, imported.timingAlignment)
        assertEquals(250L, imported.offsetMs)
        val provider = LyricsProvider { error("Saved override must win") }
        val repository = LyricsRepository(provider, provider, LyricsCache(temporary.newFolder()), overrides = restored)
        val result = repository.load(song)
        assertEquals(source, result.sourceDocument)
        assertEquals(4132L, result.document.lines.first().startTimeMs)
        assertEquals(4382L, LyricsSynchronizer.seekPosition(result.document.lines.first().startTimeMs, -restored.settings(song).offsetMs))
        assertEquals(15_000L, restored.active(song)!!.document.lines.first().startTimeMs)
    }

    @Test fun olderSavedOnlineMatchesCanBeAlignedWithoutChangingTheirStoredSourceOrManualOffset() = runBlocking {
        val song = Song(7, "Fixture", "Singer")
        val store = SongLyricsStore(temporary.newFolder())
        val saved = SavedLyrics(source, LyricsOrigin("AMLL", "amll"))
        store.saveMatched(song, saved)
        store.setOffset(song, -250)
        val provider = LyricsProvider { error("Saved match must win") }
        val matcher = OnlineLyricsMatcher({ error("Explicit id must not search") }, { reference() })
        val repository = LyricsRepository(provider, provider, LyricsCache(temporary.newFolder()), overrides = store,
            alignMatch = matcher::align)
        val result = repository.load(song)
        assertEquals(4132L, result.document.lines.first().startTimeMs)
        assertNotNull(result.timingAlignment)
        assertEquals(saved, store.active(song))
        assertEquals(-250L, store.settings(song).offsetMs)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun sharedPlaybackKeepsTheAlignedDocumentWhileManualTimingChangesWithoutReloading() = runTest {
        val song = Song(7, "Fixture", "Singer")
        val alignment = LyricsTimingAligner.align(reference(), source)!!
        val choices = MutableStateFlow(mapOf(SongLyricsStore.identity(song) to LyricsSongSettings(offsetMs = 250)))
        var loads = 0
        val playback = PlaybackLyrics(MutableStateFlow(LyricsRequest(song)), {
            loads++
            LyricsResult(alignment.applyTo(source), sourceDocument = source, timingAlignment = alignment)
        }, backgroundScope, choices)
        runCurrent()
        assertEquals(4132L, playback.state.value.document.lines.first().startTimeMs)
        assertEquals(source, playback.state.value.sourceDocument)
        assertEquals(alignment, playback.state.value.timingAlignment)
        assertEquals(250L, playback.state.value.songOffsetMs)
        choices.value = mapOf(SongLyricsStore.identity(song) to LyricsSongSettings(offsetMs = 500))
        runCurrent()
        assertEquals(500L, playback.state.value.songOffsetMs)
        assertEquals(4132L, playback.state.value.document.lines.first().startTimeMs)
        assertEquals(1, loads)
    }
}
