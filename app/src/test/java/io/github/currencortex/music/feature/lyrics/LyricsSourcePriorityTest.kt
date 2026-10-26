package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.SongExternalIds
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricWord
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsSourcePriorityTest {
    private val song = Song(7, "Fixture", "Singer", "Album", durationMs = 191_000)
    private fun lyrics(text: String) = LyricsParser.lrc("[00:01]$text")
    private val reference = LyricsDocument(listOf(
        LyricLine(1_000, 5_000, "The morning light is bright"),
        LyricLine(6_000, 10_000, "We walk together through rain"),
        LyricLine(11_000, 15_000, "Our hopeful voices fill the sky"),
        LyricLine(16_000, 20_000, "We sing until the break of day")))
    private fun rich(document: LyricsDocument = reference) = document.copy(lines = document.lines.map { line ->
        val tokens = Regex("\\S+").findAll(line.text).toList()
        val step = (line.endTimeMs - line.startTimeMs) / tokens.size
        line.copy(words = tokens.mapIndexed { index, token -> LyricWord(token.value,
            line.startTimeMs + index * step, line.startTimeMs + (index + 1) * step,
            token.range.first, token.range.last + 1) }, translation = "对应翻译 ${line.text}")
    })
    private fun candidate(source: LyricsMatchSource, id: String = "candidate", artists: String = song.artists) =
        LyricsCandidate(source, id, song.name, artists, song.album, song.durationMs, id)
    // All public HTTP platforms are overridden so orchestration fixtures cannot access the network.
    private fun adapters() = LyricsMatchSource.entries.filter { it != LyricsMatchSource.NETEASE }.associateWith {
        LyricsMatchAdapter({ emptyList() }, { error("An empty source must not load") })
    }
    private fun adapter(source: LyricsMatchSource, document: LyricsDocument) =
        LyricsMatchAdapter({ listOf(candidate(source)) }, { document })

    @Test fun directNeteaseIdSuppliesTheReferenceWhileACompleteExternalDocumentWins() = runBlocking {
        val loaded = mutableListOf<Long>()
        val matcher = OnlineLyricsMatcher(
            searchNetease = { error("An explicit NetEase id must not be replaced through metadata search") },
            loadNetease = { id -> loaded += id; reference },
            additionalSources = adapters() + (LyricsMatchSource.AMLL to adapter(LyricsMatchSource.AMLL, rich())))
        val result = matcher.bestMatch(song)!!
        assertEquals(listOf(7L), loaded)
        assertEquals("amll", result.origin.provider)
        assertTrue(result.document.hasWordTiming)
        assertEquals(reference.lines.map { it.text }, result.document.lines.map { it.text })
    }

    @Test fun preferredPlatformDoesNotDetermineWhichMatchingDocumentWins() = runBlocking {
        val matcher = OnlineLyricsMatcher({ error("Unexpected metadata search") }, { reference },
            additionalSources = adapters() + (LyricsMatchSource.QQ_MUSIC to adapter(LyricsMatchSource.QQ_MUSIC, rich())))
        for (preferred in listOf(LyricsMatchSource.NETEASE, LyricsMatchSource.KUGOU, LyricsMatchSource.QISHUI))
            assertEquals("qq_music", matcher.bestMatch(song, preferred)!!.origin.provider)
    }

    private suspend fun equallyScoredWinner(amllDelay: Long, qqDelay: Long, dispatcher: CoroutineDispatcher): SavedLyrics {
        fun delayed(source: LyricsMatchSource, wait: Long) = LyricsMatchAdapter(
            { listOf(candidate(source, "same-id")) }, { delay(wait); rich() })
        val matcher = OnlineLyricsMatcher({ error("Unexpected metadata search") }, { reference },
            additionalSources = adapters() + mapOf(
                LyricsMatchSource.AMLL to delayed(LyricsMatchSource.AMLL, amllDelay),
                LyricsMatchSource.QQ_MUSIC to delayed(LyricsMatchSource.QQ_MUSIC, qqDelay)), scoreDispatcher = dispatcher)
        return matcher.bestMatch(song)!!
    }

    @Test fun equalScoresAndCollidingReferencesAreStableWhenAmllReturnsFirst() = runTest {
        val result = equallyScoredWinner(100, 200, StandardTestDispatcher(testScheduler))
        assertEquals("amll", result.origin.provider)
        assertEquals("same-id", result.origin.reference)
    }

    @Test fun equalScoresAndCollidingReferencesAreStableWhenQqReturnsFirst() = runTest {
        val result = equallyScoredWinner(200, 100, StandardTestDispatcher(testScheduler))
        assertEquals("amll", result.origin.provider)
        assertEquals("same-id", result.origin.reference)
    }

    @Test fun unlinkedSongsUseOnlyReliableNeteaseMetadataCandidatesToAcquireTheReference() = runBlocking {
        val loaded = mutableListOf<Long>()
        var searches = 0
        val matcher = OnlineLyricsMatcher(searchNetease = {
            searches++; listOf(song.copy(id = 99, artists = "Other singer"), song.copy(id = 8), song.copy(id = 9))
        }, loadNetease = { id -> loaded += id; if (id == 8L) LyricsDocument() else reference }, additionalSources = adapters())
        val unlinked = song.copy(musicSource = MusicSource.QQ_MUSIC, externalIds = SongExternalIds(qqMusicId = "qqmid"))
        assertEquals(reference, matcher.bestMatch(unlinked)!!.document)
        assertEquals(1, searches)
        assertEquals(listOf(8L, 9L), loaded)
    }

    @Test fun wrongContentWithRealWordCuesIsRejectedEvenWithMatchingMetadata() = runBlocking {
        val unrelated = reference.copy(lines = reference.lines.map { it.copy(text = "Completely unrelated words from a different composition") })
        val matcher = OnlineLyricsMatcher({ error("Unexpected metadata search") }, { reference },
            additionalSources = adapters() + (LyricsMatchSource.AMLL to adapter(LyricsMatchSource.AMLL, rich(unrelated))))
        val result = matcher.bestMatch(song)!!
        assertEquals("netease", result.origin.provider)
        assertEquals(reference, result.document)
    }

    @Test fun aLaterCandidateOnTheSamePlatformCanWinAfterTheFirstCandidateLoadsSuccessfully() = runBlocking {
        val loaded = mutableListOf<String>()
        val matcher = OnlineLyricsMatcher({ error("Unexpected metadata search") }, { reference }, additionalSources = adapters() +
            (LyricsMatchSource.QQ_MUSIC to LyricsMatchAdapter(
                { listOf(candidate(LyricsMatchSource.QQ_MUSIC, "first"), candidate(LyricsMatchSource.QQ_MUSIC, "best")) },
                { loaded += it.id; if (it.id == "first") reference else rich() })))
        val result = matcher.bestMatch(song)!!
        assertEquals("qq_music", result.origin.provider)
        assertEquals("best", result.origin.reference)
        assertEquals(listOf("first", "best"), loaded)
    }

    @Test fun estimatedWordTimesCannotOutrankTheOriginalLineDocument() = runBlocking {
        val estimated = rich().copy(lines = rich().lines.map { it.copy(wordsEstimated = true, translation = "") })
        val matcher = OnlineLyricsMatcher({ error("Unexpected metadata search") }, { reference },
            additionalSources = adapters() + (LyricsMatchSource.AMLL to adapter(LyricsMatchSource.AMLL, estimated)))
        assertEquals("netease", matcher.bestMatch(song)!!.origin.provider)
        assertTrue(estimated.lines.all { it.wordsEstimated && it.words.isNotEmpty() })
        assertFalse(reference.hasWordTiming)
    }

    @Test fun contentIdenticalWordLyricsWithAnUnappliedThreeSecondOffsetAreRejected() = runBlocking {
        val shifted = rich().copy(lines = rich().lines.map { line -> line.copy(
            startTimeMs = line.startTimeMs + 3_000, endTimeMs = line.endTimeMs + 3_000,
            words = line.words.map { it.copy(startTimeMs = it.startTimeMs + 3_000, endTimeMs = it.endTimeMs + 3_000) }) })
        val matcher = OnlineLyricsMatcher({ error("Unexpected metadata search") }, { reference },
            additionalSources = adapters() + (LyricsMatchSource.QISHUI to adapter(LyricsMatchSource.QISHUI, shifted)))
        assertEquals(reference, matcher.bestMatch(song)!!.document)
        assertEquals(4_000L, shifted.lines.first().startTimeMs)
        assertEquals(1_000L, reference.lines.first().startTimeMs)
    }

    @Test fun allConfiguredPlatformsAreScannedEvenWhenTheReferenceAlreadyHasWordTiming() = runBlocking {
        val searched = mutableSetOf<LyricsMatchSource>()
        val supplied = adapters().mapValues { (source, _) -> LyricsMatchAdapter(
            { searched += source; listOf(candidate(source)) }, { rich() }) }
        val matcher = OnlineLyricsMatcher({ error("Unexpected metadata search") }, { rich() }, additionalSources = supplied)
        val result = matcher.bestMatch(song)!!
        assertEquals(LyricsMatchSource.entries.filter { it != LyricsMatchSource.NETEASE }.toSet(), searched)
        assertEquals("netease", result.origin.provider)
    }

    @Test fun wrongArtistIsRejectedBeforeEvenAContentIdenticalExternalDocumentIsLoaded() = runBlocking {
        var loads = 0
        val matcher = OnlineLyricsMatcher({ error("Unexpected metadata search") }, { reference }, additionalSources = adapters() +
            (LyricsMatchSource.QQ_MUSIC to LyricsMatchAdapter(
                { listOf(candidate(LyricsMatchSource.QQ_MUSIC, artists = "Different singer")) }, { loads++; rich() })))
        assertEquals("netease", matcher.bestMatch(song)!!.origin.provider)
        assertEquals(0, loads)
    }

    @Test fun anUnlinkedSongWithoutAReliableReferenceNeverGuessesAnExternalReplacement() = runBlocking {
        var externalSearches = 0
        val supplied = adapters().mapValues { (source, _) -> LyricsMatchAdapter(
            { externalSearches++; listOf(candidate(source)) }, { rich() }) }
        val matcher = OnlineLyricsMatcher({ listOf(song.copy(artists = "Other singer")) },
            { error("An unreliable reference must not load") }, additionalSources = supplied)
        val failure = runCatching { matcher.bestMatch(song.copy(musicSource = MusicSource.QQ_MUSIC)) }.exceptionOrNull()
        assertTrue(failure is LyricsMatchException)
        assertTrue(failure!!.message!!.contains("无法取得网易云基准歌词"))
        assertEquals(0, externalSearches)
    }

    @Test fun failedKnownNeteaseIdDoesNotSearchForAnotherVersionsReference() = runBlocking {
        var searches = 0
        var externalSearches = 0
        val supplied = adapters().mapValues { (source, _) -> LyricsMatchAdapter(
            { externalSearches++; listOf(candidate(source)) }, { rich() }) }
        val matcher = OnlineLyricsMatcher({ searches++; listOf(song.copy(id = 8)) },
            { throw LyricsMatchException("reference unavailable") }, additionalSources = supplied)
        val failure = runCatching { matcher.bestMatch(song) }.exceptionOrNull()
        assertTrue(failure is LyricsMatchException)
        assertEquals(0, searches)
        assertEquals(0, externalSearches)
    }

    @Test fun creditsOnlyOrOversizedReferenceCannotBecomeAnAutomaticFallback() = runBlocking {
        for (invalid in listOf(LyricsDocument(listOf(LyricLine(0, 2_000, "作词：Fixture Author"))),
            LyricsDocument(listOf(LyricLine(0, 2_000, "x".repeat(24_001)))))) {
            var externalSearches = 0
            val supplied = adapters().mapValues { (source, _) -> LyricsMatchAdapter(
                { externalSearches++; listOf(candidate(source)) }, { rich() }) }
            val matcher = OnlineLyricsMatcher({ error("Unexpected metadata search") }, { invalid }, additionalSources = supplied)
            val failure = runCatching { matcher.bestMatch(song) }.exceptionOrNull()
            assertTrue(failure is LyricsMatchException)
            assertTrue(failure!!.message!!.contains("无法取得网易云基准歌词"))
            assertEquals(0, externalSearches)
        }
    }

    @Test fun sourceTimeoutsAndFailuresDoNotStarveOtherPlatforms() = runTest {
        var searchCancelled = false
        var loadCancelled = false
        val supplied = adapters() + mapOf(
            LyricsMatchSource.AMLL to LyricsMatchAdapter({
                try { delay(20_000); emptyList() } finally { searchCancelled = true }
            }, { error("Timed out search must not load") }),
            LyricsMatchSource.KUGOU to LyricsMatchAdapter({ listOf(candidate(LyricsMatchSource.KUGOU)) }, {
                try { delay(20_000); rich() } finally { loadCancelled = true }
            }),
            LyricsMatchSource.CURRENT_MUSIC to LyricsMatchAdapter({ throw LyricsMatchException("offline") }, { rich() }),
            LyricsMatchSource.QISHUI to adapter(LyricsMatchSource.QISHUI, rich()))
        val matcher = OnlineLyricsMatcher({ error("Unexpected metadata search") }, { reference }, additionalSources = supplied,
            scoreDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals("qishui", matcher.bestMatch(song)!!.origin.provider)
        assertTrue(searchCancelled)
        assertTrue(loadCancelled)
        assertTrue(currentTime <= 5_000)
    }

    @Test fun callerCancellationStopsAllPlatformsWithoutReturningTheReferenceAsSuccess() = runTest {
        val started = mutableSetOf<LyricsMatchSource>()
        val cancelled = mutableSetOf<LyricsMatchSource>()
        val supplied = adapters().mapValues { (source, _) -> LyricsMatchAdapter({
            started += source
            try { awaitCancellation() } finally { cancelled += source }
        }, { rich() }) }
        val matcher = OnlineLyricsMatcher({ error("Unexpected metadata search") }, { reference }, additionalSources = supplied,
            scoreDispatcher = StandardTestDispatcher(testScheduler))
        var returned = false
        val job = launch { matcher.bestMatch(song); returned = true }
        runCurrent()
        assertEquals(5, started.size)
        job.cancelAndJoin()
        assertEquals(started, cancelled)
        assertFalse(returned)
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
