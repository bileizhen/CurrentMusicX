package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsSourcePriorityTest {
    private val song = Song(7, "Fixture", "Singer", "Album", durationMs = 191_000)
    private fun lyrics(text: String) = LyricsParser.lrc("[00:01]$text")
    private val reference = lyrics("Native plain lyric")
    private fun rich(offset: Long = 0) = LyricsDocument(listOf(LyricLine(1000 + offset, 3000 + offset,
        "Native plain lyric", listOf(LyricWord("Native plain lyric", 1000 + offset, 3000 + offset, 0, 18)))))
    private fun candidate(source: LyricsMatchSource, id: String = "candidate") =
        LyricsCandidate(source, id, song.name, song.artists, song.album, song.durationMs, id)
    private fun adapters() = LyricsMatchSource.entries.filter { it != LyricsMatchSource.NETEASE }.associateWith {
        LyricsMatchAdapter({ emptyList() }, { error("An empty source must not load") })
    }
    private fun adapter(source: LyricsMatchSource, document: LyricsDocument,
        metadata: LyricsCandidate = candidate(source)) = LyricsMatchAdapter({ listOf(metadata) }, { document })

    @Test fun displayedNinetyPercentAmllCannotOverrideAnElevenSecondAxisMismatch() = runBlocking {
        val metadata = candidate(LyricsMatchSource.AMLL).copy(durationMs = 0) // 55 + 25 + 10 = 90
        val document = rich(11_000)
        val matcher = OnlineLyricsMatcher({ error("An exact native id must not search") }, { reference },
            additionalSources = adapters() + (LyricsMatchSource.AMLL to adapter(LyricsMatchSource.AMLL, document, metadata)))
        val result = matcher.bestMatchDetailed(song)!!
        assertEquals("netease", result.saved.origin.provider)
        assertTrue(result.assessment.accepted)
        assertFalse(result.assessment.preferredWord)
        val checked = matcher.assess(song, metadata, SavedLyrics(document, LyricsOrigin("AMLL", "amll")), reference)
        assertEquals(90, checked.matchPercent)
        assertEquals(1.0, checked.score!!.similarity, 0.0)
        assertFalse(checked.accepted)
        assertNull(result.saved.timingAlignment)
    }

    @Test fun wrongArtistCannotBypassReliableIdentityWithAHighMetadataPercentage() = runBlocking {
        val metadata = candidate(LyricsMatchSource.AMLL).copy(artists = "Different") // 55 + 10 + 10
        assertEquals(75, OnlineLyricsMatcher.metadataMatchPercent(song, metadata))
        assertFalse(OnlineLyricsMatcher.isReliableMatch(song, metadata))
        val matcher = OnlineLyricsMatcher({ emptyList() }, { reference }, additionalSources = adapters() + mapOf(
            LyricsMatchSource.AMLL to adapter(LyricsMatchSource.AMLL, rich(), metadata),
            LyricsMatchSource.QQ_MUSIC to adapter(LyricsMatchSource.QQ_MUSIC, rich())))
        val result = matcher.bestMatchDetailed(song)!!
        assertEquals("qq_music", result.saved.origin.provider)
        assertEquals(100, result.assessment.matchPercent)
    }

    @Test fun belowSeventyFiveIsSkippedBeforeItsLyricsAreLoaded() = runBlocking {
        var loads = 0
        val metadata = candidate(LyricsMatchSource.AMLL).copy(artists = "Different", durationMs = 0)
        assertEquals(65, OnlineLyricsMatcher.metadataMatchPercent(song, metadata))
        val matcher = OnlineLyricsMatcher({ emptyList() }, { reference }, additionalSources = adapters() +
            (LyricsMatchSource.AMLL to LyricsMatchAdapter({ listOf(metadata) }, { loads++; rich() })))
        assertEquals("netease", matcher.bestMatch(song)!!.origin.provider)
        assertEquals(0, loads)
    }

    @Test fun qualifyingAmllAtExactlySeventyFivePercentContentKeepsPriorityOverOtherValidatedWords() = runBlocking {
        val baseline = LyricsDocument(listOf(LyricLine(1000, 3000, "ABCDEFGH")))
        fun words(text: String) = LyricsDocument(listOf(LyricLine(1000, 3000, text,
            listOf(LyricWord(text, 1000, 3000, 0, text.length)))))
        val matcher = OnlineLyricsMatcher({ emptyList() }, { baseline }, additionalSources = adapters() + mapOf(
            LyricsMatchSource.AMLL to adapter(LyricsMatchSource.AMLL, words("ABXDEYGH")),
            LyricsMatchSource.QQ_MUSIC to adapter(LyricsMatchSource.QQ_MUSIC, words("ABCDEFGH"))))
        val result = matcher.bestMatchDetailed(song)!!
        assertEquals("amll", result.saved.origin.provider)
        assertEquals(.75, result.assessment.score!!.similarity, 0.0)
        assertTrue(result.assessment.preferredWord)
    }

    @Test fun amllWinsAgainstNativeWordsWithTrailingCreditsAndShorterFinalReleases() = runBlocking {
        val vocals = LyricsDocument((0 until 6).map { i ->
            val text = "Complete sung line $i"
            val start = 10_000L + i * 10_000
            LyricLine(start, start + 5000, text, listOf(LyricWord(text, start, start + 5000, 0, text.length)))
        })
        val native = vocals.copy(lines = vocals.lines + listOf(
            LyricLine(70_000, 71_000, "吉他：Studio Contributor"),
            LyricLine(71_000, 72_000, "OP：Publisher"),
            LyricLine(72_000, 73_000, "SP：Another Publisher")))
        val amll = vocals.copy(lines = vocals.lines.map { line -> line.copy(
            startTimeMs = line.startTimeMs + 200, endTimeMs = line.endTimeMs + 2800,
            words = line.words.map { it.copy(startTimeMs = it.startTimeMs + 200, endTimeMs = it.endTimeMs + 2800) }) })
        val matcher = OnlineLyricsMatcher({ emptyList() }, { native }, additionalSources = adapters() +
            (LyricsMatchSource.AMLL to adapter(LyricsMatchSource.AMLL, amll)))
        val result = matcher.bestMatchDetailed(song)!!
        assertEquals("amll", result.saved.origin.provider)
        assertEquals(1.0, result.assessment.score!!.similarity, 0.0)
        assertTrue(result.assessment.preferredWord)
    }

    @Test fun aMissingReferenceCannotSilentlyApplyUnvalidatedExternalLyrics() = runBlocking {
        val matcher = OnlineLyricsMatcher({ throw LyricsMatchException("Offline") }, { throw LyricsMatchException("Offline") },
            additionalSources = adapters() + (LyricsMatchSource.AMLL to adapter(LyricsMatchSource.AMLL, rich())))
        for (input in listOf(song, song.copy(musicSource = MusicSource.QQ_MUSIC))) {
            val failure = runCatching { matcher.bestMatch(input) }.exceptionOrNull()
            assertTrue(failure is LyricsMatchException)
            assertTrue(failure!!.message!!.contains("未自动替换"))
        }
    }

    @Test fun lowerMetadataCandidateWithValidTimingBeatsPerfectMetadataWithWrongTiming() = runBlocking {
        val matcher = OnlineLyricsMatcher({ emptyList() }, { reference }, additionalSources = adapters() + mapOf(
            LyricsMatchSource.QQ_MUSIC to adapter(LyricsMatchSource.QQ_MUSIC, rich(),
                candidate(LyricsMatchSource.QQ_MUSIC).copy(durationMs = 0)),
            LyricsMatchSource.QISHUI to adapter(LyricsMatchSource.QISHUI, rich(20_000))))
        assertEquals("qq_music", matcher.bestMatch(song)!!.origin.provider)
    }

    @Test fun completeValidatedLyricsBeatDifferentContentDespiteTheOtherCandidatesHigherMetadata() = runBlocking {
        val matcher = OnlineLyricsMatcher({ emptyList() }, { reference }, additionalSources = adapters() + mapOf(
            LyricsMatchSource.QQ_MUSIC to adapter(LyricsMatchSource.QQ_MUSIC, reference.copy(lines = reference.lines.map {
                it.copy(translation = "Translation", romanization = "Romanization") }), candidate(LyricsMatchSource.QQ_MUSIC).copy(durationMs = 0)),
            LyricsMatchSource.QISHUI to adapter(LyricsMatchSource.QISHUI, lyrics("Different content"))))
        assertEquals("qq_music", matcher.bestMatch(song)!!.origin.provider)
    }

    @Test fun estimatedOrZeroLengthWordsDoNotAcquireTheRealWordPriority() = runBlocking {
        val estimated = rich().copy(lines = rich().lines.map { it.copy(wordsEstimated = true) })
        val zero = rich().copy(lines = rich().lines.map { line -> line.copy(words = line.words.map { it.copy(endTimeMs = it.startTimeMs) }) })
        for (document in listOf(estimated, zero)) {
            val matcher = OnlineLyricsMatcher({ emptyList() }, { reference }, additionalSources = adapters() +
                (LyricsMatchSource.AMLL to adapter(LyricsMatchSource.AMLL, document)))
            assertEquals("netease", matcher.bestMatch(song)!!.origin.provider)
        }
    }

    @Test fun laterCandidatesOnTheSamePlatformAreStillConsidered() = runBlocking {
        val matcher = OnlineLyricsMatcher({ emptyList() }, { reference }, additionalSources = adapters() +
            (LyricsMatchSource.QQ_MUSIC to LyricsMatchAdapter({ listOf(candidate(LyricsMatchSource.QQ_MUSIC, "first"),
                candidate(LyricsMatchSource.QQ_MUSIC, "word")) }, { if (it.id == "first") reference else rich() })))
        assertEquals("word", matcher.bestMatch(song)!!.origin.reference)
    }

    @Test fun allSourcesStartEvenWhenAnEarlierSourceHangsAndTheNativeAlreadyHasWords() = runTest {
        val searched = mutableSetOf<LyricsMatchSource>()
        val supplied = adapters().mapValues { (source, _) -> LyricsMatchAdapter({
            searched += source
            if (source == LyricsMatchSource.CURRENT_MUSIC) awaitCancellation()
            if (source == LyricsMatchSource.AMLL) listOf(candidate(source)) else emptyList()
        }, { rich() }) }
        val matcher = OnlineLyricsMatcher({ emptyList() }, { rich() }, additionalSources = supplied,
            scoreDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals("amll", matcher.bestMatch(song)!!.origin.provider)
        assertEquals(adapters().keys, searched)
    }

    @Test fun callerCancellationCancelsInFlightSourcesRatherThanReturningAPartialWinner() = runTest {
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        var returned = false
        val supplied = adapters() + (LyricsMatchSource.AMLL to LyricsMatchAdapter({
            started.complete(Unit)
            try { awaitCancellation() } finally { cancelled = true }
        }, { rich() }))
        val matcher = OnlineLyricsMatcher({ emptyList() }, { reference }, additionalSources = supplied,
            scoreDispatcher = StandardTestDispatcher(testScheduler))
        val job = launch { matcher.bestMatch(song); returned = true }
        runCurrent()
        assertTrue(started.isCompleted)
        job.cancelAndJoin()
        assertTrue(cancelled)
        assertFalse(returned)
    }

    @Test fun equalPercentagesAreStableRegardlessOfNetworkCompletionOrder() = runTest {
        for (slowQq in listOf(true, false)) {
            fun delayed(source: LyricsMatchSource, delayMs: Long) = LyricsMatchAdapter(
                { delay(delayMs); listOf(candidate(source, "same-id")) }, { rich() })
            val matcher = OnlineLyricsMatcher({ emptyList() }, { reference }, additionalSources = adapters() + mapOf(
                LyricsMatchSource.QQ_MUSIC to delayed(LyricsMatchSource.QQ_MUSIC, if (slowQq) 200 else 100),
                LyricsMatchSource.QISHUI to delayed(LyricsMatchSource.QISHUI, if (slowQq) 100 else 200)),
                scoreDispatcher = StandardTestDispatcher(testScheduler))
            assertEquals("qishui", matcher.bestMatch(song)!!.origin.provider)
        }
    }

    @Test fun manualSourceTabDoesNotOverrideAutomaticPriority() = runBlocking {
        val matcher = OnlineLyricsMatcher({ emptyList() }, { reference }, additionalSources = adapters() +
            (LyricsMatchSource.QISHUI to adapter(LyricsMatchSource.QISHUI, rich())))
        assertEquals("qishui", matcher.bestMatch(song, LyricsMatchSource.NETEASE)!!.origin.provider)
    }

    @Test fun qqAndQishuiAdaptersKeepPlatformIdsAsStringsAndRecordTheirOwnOrigins() = runBlocking {
        val ids = mutableListOf<String>()
        fun adapter(source: LyricsMatchSource, id: String) = LyricsMatchAdapter(
            search = { listOf(LyricsCandidate(source, id, song.name, song.artists, song.album, song.durationMs, id)) },
            load = { candidate -> ids += candidate.id; lyrics(source.name) })
        val matcher = OnlineLyricsMatcher({ emptyList() }, { error("Unexpected native load") }, additionalSources = mapOf(
            LyricsMatchSource.QQ_MUSIC to adapter(LyricsMatchSource.QQ_MUSIC, "001N8FgQ0aepZB"),
            LyricsMatchSource.QISHUI to adapter(LyricsMatchSource.QISHUI, "7123456789012345678")))
        for (source in listOf(LyricsMatchSource.QQ_MUSIC, LyricsMatchSource.QISHUI)) {
            val candidate = matcher.search(source, "Fixture").single()
            val loaded = matcher.load(candidate)
            assertEquals(source.name.lowercase(), loaded.origin.provider)
            assertEquals(source.name, loaded.document.lines.single().text)
        }
        assertEquals(listOf("001N8FgQ0aepZB", "7123456789012345678"), ids)
    }

    @Test fun manualCurrentMusicSearchAndLoadRemainIndependentOfTheNeteaseReference() = runBlocking {
        val calls = mutableListOf<String>()
        val matcher = OnlineLyricsMatcher({ error("Manual project search must not ask NetEase") },
            { error("Manual project load must not ask NetEase") },
            searchCurrentMusic = { calls += "project-search"; listOf(song) },
            loadCurrentMusic = { id -> assertEquals(7L, id); calls += "project-load"; lyrics("project") })
        val saved = matcher.load(matcher.search(LyricsMatchSource.CURRENT_MUSIC, "Fixture").single())
        assertEquals("current_music", saved.origin.provider)
        assertEquals("project", saved.document.lines.single().text)
        assertEquals(listOf("project-search", "project-load"), calls)
    }

    @Test fun unconfiguredOptionalSourcesAreSkippedDuringAutomaticComparison() = runBlocking {
        var partialProjectCalls = 0
        val matcher = OnlineLyricsMatcher({ error("Unexpected metadata search") }, { reference },
            searchCurrentMusic = { partialProjectCalls++; listOf(song) },
            additionalSources = adapters().filterKeys { it == LyricsMatchSource.AMLL || it == LyricsMatchSource.KUGOU })
        assertEquals("netease", matcher.bestMatch(song)!!.origin.provider)
        assertEquals(0, partialProjectCalls)
    }

    @Test fun sourceMismatchAndMissingProjectConfigurationAreExplicitErrors() = runBlocking {
        val matcher = OnlineLyricsMatcher({ emptyList() }, { error("Unexpected native load") }, additionalSources = mapOf(
            LyricsMatchSource.QQ_MUSIC to LyricsMatchAdapter(
                { listOf(LyricsCandidate(LyricsMatchSource.QISHUI, "7", "Wrong platform")) }, { lyrics("wrong") })))
        assertTrue(runCatching { matcher.search(LyricsMatchSource.QQ_MUSIC, "Fixture") }.isFailure)
        assertTrue(runCatching { matcher.search(LyricsMatchSource.CURRENT_MUSIC, "Fixture") }.exceptionOrNull() is LyricsMatchException)
    }
}
