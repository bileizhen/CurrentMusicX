package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LyricsMatchAssessmentTest {
    private val song = Song(7, "Fixture", "Singer", "Album", durationMs = 191_000)
    private val candidate = LyricsCandidate(LyricsMatchSource.QQ_MUSIC, "id", song.name, song.artists, song.album, song.durationMs)
    private val saved = SavedLyrics(LyricsDocument(listOf(LyricLine(12_000, 13_000, "Different lyric",
        listOf(LyricWord("Different lyric", 12_000, 13_000, 0, 15))))), LyricsOrigin("QQ", "qq_music", "id"))
    private fun matcher() = OnlineLyricsMatcher({ error("Assessment must not search") }, { error("Assessment must not fetch reference lyrics") })

    @Test fun metadataAndRenderableWordsQualifyWithoutAnyReferenceContentOrTimingComparison() = runBlocking {
        val result = matcher().assess(song, candidate, saved)
        assertEquals(100, result.matchPercent)
        assertTrue(result.accepted)
        assertTrue(result.preferredWord)
    }

    @Test fun badMetadataCannotAcquireWordPriorityEvenWithRenderableWords() = runBlocking {
        val result = matcher().assess(song, candidate.copy(title = "Unrelated", artists = "Other", album = "Another"), saved)
        assertFalse(result.accepted)
        assertFalse(result.preferredWord)
    }

    @Test fun aPercentageWithoutRenderableLyricsCannotQualify() {
        assertFalse(LyricsMatchAssessment(100, true, metadataReliable = true).accepted)
    }

    @Test fun lineLyricsCanQualifyAsFallback() = runBlocking {
        val result = matcher().assess(song, candidate, saved.copy(document = LyricsDocument(saved.document.lines.map { it.copy(words = emptyList()) })))
        assertTrue(result.accepted)
        assertFalse(result.preferredWord)
    }

    @Test fun unknownDurationUsesFoliasNinetyPercentMultiplier() = runBlocking {
        val result = matcher().assess(song, candidate.copy(durationMs = 0), saved)
        assertEquals(90, result.matchPercent)
        assertTrue(result.accepted)
    }

    @Test fun nonSongInputsKeepTheNullContract() = runBlocking {
        assertNull(matcher().bestMatchDetailed(song.copy(video = true)))
        assertNull(matcher().bestMatch(song.copy(name = "")))
    }
}
