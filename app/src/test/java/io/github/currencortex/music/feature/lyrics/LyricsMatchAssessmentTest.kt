package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.domain.LyricsReferenceMatcher
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricWord
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsMatchAssessmentTest {
    private val song = Song(7, "Fixture", "Singer", "Album", durationMs = 191_000)
    private val reference = LyricsDocument(listOf(LyricLine(1_000, 3_000, "Hello world")))
    private val rich = reference.copy(lines = reference.lines.map { it.copy(words = listOf(
        LyricWord("Hello", 1_000, 2_000, 0, 5), LyricWord("world", 2_000, 3_000, 6, 11)), translation = "你好世界") })
    private fun saved(document: LyricsDocument = rich) = SavedLyrics(document, LyricsOrigin("AMLL", "amll", "same-id"))
    private fun candidate(source: LyricsMatchSource = LyricsMatchSource.AMLL) =
        LyricsCandidate(source, "same-id", song.name, song.artists, song.album, song.durationMs, "same-id")
    private fun adapters() = LyricsMatchSource.entries.filter { it != LyricsMatchSource.NETEASE }.associateWith {
        LyricsMatchAdapter({ emptyList() }, { error("An empty adapter must not load") })
    }

    @Test fun metadataPercentageUsesTheExactRankWeightsAndStaysWithinItsOwnZeroToHundredScale() {
        val exact = candidate()
        val titleOnly = exact.copy(artists = "Other", album = "Other", durationMs = 1)
        val noTitle = exact.copy(title = "Different")
        val unrelated = titleOnly.copy(title = "Different")
        assertEquals(100, OnlineLyricsMatcher.metadataMatchPercent(song, exact))
        assertEquals(55, OnlineLyricsMatcher.metadataMatchPercent(song, titleOnly))
        assertEquals(45, OnlineLyricsMatcher.metadataMatchPercent(song, noTitle))
        assertEquals(0, OnlineLyricsMatcher.metadataMatchPercent(song, unrelated))
        val input = listOf(unrelated, titleOnly, exact, noTitle)
        assertEquals(listOf(exact, titleOnly, noTitle, unrelated), OnlineLyricsMatcher.rankCandidates(song, input))
        assertTrue(input.all { OnlineLyricsMatcher.metadataMatchPercent(song, it) in 0..100 })
        // A metadata percentage remains unchanged by any fetched document's content.
        assertEquals(100, OnlineLyricsMatcher.metadataMatchPercent(song, exact))
    }

    @Test fun manualAssessmentFetchesTheExactNativeReferenceOnceAndReturnsTheRealContentScore() = runBlocking {
        var loads = 0
        val matcher = OnlineLyricsMatcher({ error("An existing native id must not search for another song") }, {
            assertEquals(7L, it); loads++; reference
        }, additionalSources = adapters())
        val assessment = matcher.assess(song, saved())
        assertEquals(1, loads)
        assertNull(assessment.unavailableReason)
        assertEquals(LyricsReferenceMatcher.compare(reference, rich), assessment.score)
        val score = assessment.score!!
        assertTrue(score.accepted)
        for (value in listOf(score.similarity, score.timingSimilarity,
            score.quality, score.total)) assertTrue(value in 0.0..1.0)
    }

    @Test fun aSuppliedReferenceAvoidsNetworkAndStillRejectsWrongContentWithoutChangingEitherDocument() = runBlocking {
        val matcher = OnlineLyricsMatcher({ error("Unexpected search") }, { error("Unexpected load") }, additionalSources = adapters())
        val wrong = rich.copy(lines = rich.lines.map { it.copy(text = "Unrelated", words = emptyList()) })
        val assessment = matcher.assess(song, saved(wrong), reference)
        assertNull(assessment.unavailableReason)
        val score = assessment.score!!
        assertFalse(score.accepted)
        assertEquals(0.0, score.total, 0.0)
        assertEquals("Hello world", reference.lines.single().text)
        assertEquals("Unrelated", wrong.lines.single().text)
    }

    @Test fun anUnavailableReferenceReturnsAnExplanationInsteadOfAZeroOrInventedPercentage() = runBlocking {
        val matcher = OnlineLyricsMatcher({ error("Unexpected search") }, { throw LyricsMatchException("offline") },
            additionalSources = adapters())
        val assessment = matcher.assess(song, saved())
        assertNull(assessment.score)
        assertTrue(assessment.unavailableReason!!.contains("无法取得网易云基准歌词"))
    }

    @Test fun anUnusableSuppliedReferenceHasNoContentScore() = runBlocking {
        val matcher = OnlineLyricsMatcher({ error("Unexpected search") }, { error("Unexpected load") }, additionalSources = adapters())
        val credits = LyricsDocument(listOf(LyricLine(0, 2_000, "作词：Fixture")))
        val assessment = matcher.assess(song, saved(), credits)
        assertNull(assessment.score)
        assertTrue(assessment.unavailableReason!!.contains("不足以进行可靠比较"))
    }

    @Test fun assessmentTimeoutIsExplicitAndDoesNotReturnAMisleadingContentScore() = runTest {
        var cancelled = false
        val matcher = OnlineLyricsMatcher({ error("Unexpected search") }, {
            try { delay(20_000); reference } finally { cancelled = true }
        }, additionalSources = adapters(), scoreDispatcher = StandardTestDispatcher(testScheduler))
        val assessment = matcher.assess(song, saved())
        assertTrue(cancelled)
        assertNull(assessment.score)
        assertTrue(assessment.unavailableReason!!.contains("超时"))
        assertTrue(currentTime <= 5_000)
    }

    @Test fun assessmentCallerCancellationPropagatesInsteadOfBecomingAnUnavailableReason() = runTest {
        var cancelled = false
        var returned = false
        val started = CompletableDeferred<Unit>()
        val matcher = OnlineLyricsMatcher({ error("Unexpected search") }, {
            started.complete(Unit)
            try { awaitCancellation() } finally { cancelled = true }
        }, additionalSources = adapters(), scoreDispatcher = StandardTestDispatcher(testScheduler))
        val job = launch { matcher.assess(song, saved()); returned = true }
        runCurrent()
        assertTrue(started.isCompleted)
        job.cancelAndJoin()
        assertTrue(cancelled)
        assertFalse(returned)
    }

    @Test fun detailedAutomaticResultCarriesItsExactWinningScoreAndLegacyApiStillReturnsTheSameDocument() = runBlocking {
        var nativeLoads = 0
        var candidateLoads = 0
        val matcher = OnlineLyricsMatcher({ error("Unexpected metadata search") }, { nativeLoads++; reference },
            additionalSources = adapters() + (LyricsMatchSource.AMLL to LyricsMatchAdapter(
                { listOf(candidate()) }, { candidateLoads++; rich })))
        val detailed = matcher.bestMatchDetailed(song)!!
        assertEquals(1, nativeLoads)
        assertEquals(1, candidateLoads)
        assertEquals("amll", detailed.saved.origin.provider)
        assertNull(detailed.assessment.unavailableReason)
        assertEquals(LyricsReferenceMatcher.compare(reference, rich), detailed.assessment.score)
        assertEquals(detailed.saved, matcher.bestMatch(song))
        assertEquals(2, nativeLoads)
        assertEquals(2, candidateLoads)
    }

    @Test fun detailedAndLegacyApisKeepTheExistingNonSongNullContract() = runBlocking {
        val matcher = OnlineLyricsMatcher({ error("Unexpected search") }, { error("Unexpected load") }, additionalSources = adapters())
        assertNull(matcher.bestMatchDetailed(song.copy(video = true)))
        assertNull(matcher.bestMatch(song.copy(name = "")))
    }
}
