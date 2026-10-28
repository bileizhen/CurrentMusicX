package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.domain.LyricsReferenceMatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LyricsMatchAssessmentTest {
    private val song = Song(7, "Fixture", "Singer", "Album", durationMs = 191_000)
    private val candidate = LyricsCandidate(LyricsMatchSource.AMLL, "id", song.name, song.artists, song.album,
        0, "ncm:id")
    private val reference = LyricsDocument(listOf(LyricLine(1000, 3000, "Word")))
    private val saved = SavedLyrics(LyricsDocument(listOf(LyricLine(12_000, 13_000, "Word",
        listOf(LyricWord("Word", 12_000, 13_000, 0, 4))))), LyricsOrigin("AMLL", "amll", "ncm:id"))
    private fun matcher() = OnlineLyricsMatcher({ error("An explicit native id must not search") }, { reference })

    @Test fun highMetadataIsOnlyAHintAndCannotOverrideFailedOriginalTiming() = runBlocking {
        val result = matcher().assess(song, candidate, saved)
        assertEquals(90, result.matchPercent)
        assertEquals(1.0, result.score!!.similarity, 0.0)
        assertFalse(result.accepted)
        assertFalse(result.preferredWord)
        assertNull(result.unavailableReason)
    }

    @Test fun precomputedAdaptiveAlignmentCannotRescueAnOriginallyRejectedRecording() = runBlocking {
        val aligned = saved.copy(timingAlignment = LyricsTimingAlignment(listOf(LyricsTimeAnchor(12_000, 1000)), 6, 1.0))
        val result = matcher().assess(song, candidate, aligned, reference)
        assertEquals(1000L, aligned.timingAlignment!!.applyTo(aligned.document).lines.single().startTimeMs)
        assertFalse(result.accepted)
        assertEquals(LyricsReferenceMatcher.compare(reference, saved.document), result.score)
        assertEquals(12_000L, aligned.document.lines.single().startTimeMs)
    }

    @Test fun aPercentageWithoutReferenceValidationNeverQualifiesEvenAtOneHundred() {
        assertFalse(LyricsMatchAssessment(100, true).accepted)
        assertFalse(LyricsMatchAssessment(75, true).preferredWord)
        assertFalse(LyricsMatchAssessment(null, true).accepted)
    }

    @Test fun validContentAndOriginalTimingCanQualifyAndHaveWordPriority() = runBlocking {
        val good = saved.copy(document = LyricsDocument(listOf(LyricLine(1000, 3000, "Word",
            listOf(LyricWord("Word", 1000, 3000, 0, 4))))))
        val result = matcher().assess(song, candidate, good, reference)
        assertTrue(result.accepted)
        assertTrue(result.preferredWord)
        assertEquals(90, result.matchPercent)
    }

    @Test fun lineLyricsCanQualifyButDoNotReceiveWordPriority() = runBlocking {
        val result = matcher().assess(song, candidate, saved.copy(document = reference), reference)
        assertTrue(result.accepted)
        assertFalse(result.preferredWord)
    }

    @Test fun missingReferenceIsReportedAsUnvalidatedAndCannotBeAutomaticallyApplied() = runBlocking {
        val offline = OnlineLyricsMatcher({ error("No search") }, { throw LyricsMatchException("Offline") })
        val result = offline.assess(song, candidate, saved)
        assertNull(result.score)
        assertFalse(result.accepted)
        assertTrue(result.unavailableReason!!.contains("未自动替换"))
    }

    @Test fun nonSongInputsKeepTheNullContract() = runBlocking {
        assertNull(matcher().bestMatchDetailed(song.copy(video = true)))
        assertNull(matcher().bestMatch(song.copy(name = "")))
    }
}
