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

    @Test fun defaultQqWordsWinImmediatelyWithoutNeteaseOrAmllRequests() = runBlocking {
        val matcher = OnlineLyricsMatcher({ error("Must stop at QQ") }, { error("Must stop at QQ") }, additionalSources = adapters() +
            (LyricsMatchSource.QQ_MUSIC to adapter(LyricsMatchSource.QQ_MUSIC, rich(11_000))))
        val result = matcher.bestMatchDetailed(song)!!
        assertEquals("qq_music", result.saved.origin.provider)
        assertTrue(result.assessment.preferredWord)
        assertEquals(12_000L, result.saved.document.lines.single().startTimeMs)
        assertNull(result.saved.timingAlignment)
    }

    @Test fun firstLinesAreKeptWhileLaterWordSourcesAreCheckedInFoliaOrder() = runBlocking {
        val calls = mutableListOf<String>()
        val supplied = adapters().mapValues { (source, _) -> LyricsMatchAdapter({ calls += "search:$source"; listOf(candidate(source)) }, {
            calls += "load:$source"; if (source == LyricsMatchSource.KUGOU) rich() else lyrics(source.name)
        }) }
        val matcher = OnlineLyricsMatcher({ emptyList() }, { calls += "load:NETEASE"; reference }, additionalSources = supplied)
        assertEquals("kugou", matcher.bestMatch(song)!!.origin.provider)
        assertEquals(listOf("search:QQ_MUSIC", "load:QQ_MUSIC", "load:NETEASE", "load:AMLL", "search:KUGOU", "load:KUGOU"), calls)
    }

    @Test fun allLineSourcesKeepTheFirstQqFallbackRatherThanReRankingQuality() = runBlocking {
        val matcher = OnlineLyricsMatcher({ emptyList() }, { reference }, additionalSources = adapters() + mapOf(
            LyricsMatchSource.QQ_MUSIC to adapter(LyricsMatchSource.QQ_MUSIC, lyrics("QQ")),
            LyricsMatchSource.AMLL to adapter(LyricsMatchSource.AMLL, reference.copy(lines = reference.lines.map { it.copy(translation = "More auxiliary tracks") }))))
        assertEquals("qq_music", matcher.bestMatch(song)!!.origin.provider)
    }

    @Test fun explicitPreferenceChangesSourceOrderAndWordsStillBeatEarlierLines() = runBlocking {
        val matcher = OnlineLyricsMatcher({ emptyList() }, { reference }, additionalSources = adapters() + mapOf(
            LyricsMatchSource.QQ_MUSIC to adapter(LyricsMatchSource.QQ_MUSIC, rich()),
            LyricsMatchSource.QISHUI to adapter(LyricsMatchSource.QISHUI, rich())))
        assertEquals("qishui", matcher.bestMatch(song, LyricsMatchSource.QISHUI)!!.origin.provider)
    }

    @Test fun nativeWordsEndTheSearchBeforeAmllEvenIfAmllMightBeBetter() = runBlocking {
        val matcher = OnlineLyricsMatcher({ emptyList() }, { rich() }, additionalSources = adapters() +
            (LyricsMatchSource.AMLL to LyricsMatchAdapter({ error("No AMLL search") }, { error("Must stop at native words") })))
        assertEquals("netease", matcher.bestMatch(song)!!.origin.provider)
    }

    @Test fun amllIsLookedUpByNeteaseIdentityWithoutLoadingNativeReferenceOrSearchingAmll() = runBlocking {
        val matcher = OnlineLyricsMatcher({ error("Known NCM id must not search") }, { throw LyricsMatchException("No native lyrics") }, additionalSources = adapters() +
            (LyricsMatchSource.AMLL to LyricsMatchAdapter({ error("AMLL automatic lookup must not search") }, {
                assertEquals("ncm:7", it.reference); rich()
            })))
        assertEquals("amll", matcher.bestMatch(song)!!.origin.provider)
    }

    @Test fun failedNeteaseDoesNotBlockIndependentLaterProviders() = runBlocking {
        val matcher = OnlineLyricsMatcher({ throw LyricsMatchException("Offline") }, { throw LyricsMatchException("Offline") }, additionalSources = adapters() +
            (LyricsMatchSource.QISHUI to adapter(LyricsMatchSource.QISHUI, rich())))
        assertEquals("qishui", matcher.bestMatch(song.copy(musicSource = MusicSource.LOCAL))!!.origin.provider)
    }

    @Test fun onlyTheBestReliableSongOfEachSourcesFirstTenResultsIsLoaded() = runBlocking {
        val loads = mutableListOf<String>()
        val matcher = OnlineLyricsMatcher({ emptyList() }, { reference }, additionalSources = adapters() +
            (LyricsMatchSource.QQ_MUSIC to LyricsMatchAdapter({ listOf(candidate(LyricsMatchSource.QQ_MUSIC, "best"),
                candidate(LyricsMatchSource.QQ_MUSIC, "second")) }, { loads += it.id; if (it.id == "best") reference else rich() })))
        assertEquals("qq_music", matcher.bestMatch(song)!!.origin.provider)
        assertEquals(listOf("best"), loads)
    }

    @Test fun candidatesOutsideTheFirstTenAreNotUsedAutomatically() = runBlocking {
        val rows = (1..10).map { candidate(LyricsMatchSource.QQ_MUSIC, "$it").copy(title = "Wrong", artists = "Nobody", album = "Other") } + candidate(LyricsMatchSource.QQ_MUSIC, "eleventh")
        val matcher = OnlineLyricsMatcher({ emptyList() }, { reference }, additionalSources = adapters() +
            (LyricsMatchSource.QQ_MUSIC to LyricsMatchAdapter({ rows }, { error("Outside top ten") })))
        assertEquals("netease", matcher.bestMatch(song)!!.origin.provider)
    }

    @Test fun estimatedAndInvalidWordsStayLineFallbacks() = runBlocking {
        for (document in listOf(rich().copy(lines = rich().lines.map { it.copy(wordsEstimated = true) }),
            rich().copy(lines = rich().lines.map { line -> line.copy(words = line.words.map { it.copy(endTimeMs = it.startTimeMs) }) }))) {
            val matcher = OnlineLyricsMatcher({ emptyList() }, { reference }, additionalSources = adapters() + mapOf(
                LyricsMatchSource.QQ_MUSIC to adapter(LyricsMatchSource.QQ_MUSIC, document),
                LyricsMatchSource.QISHUI to adapter(LyricsMatchSource.QISHUI, rich())))
            assertEquals("qishui", matcher.bestMatch(song)!!.origin.provider)
        }
    }

    @Test fun slowSourceTimesOutAndTheNextSourceCanWin() = runTest {
        val matcher = OnlineLyricsMatcher({ emptyList() }, { rich() }, additionalSources = adapters() +
            (LyricsMatchSource.QQ_MUSIC to LyricsMatchAdapter({ awaitCancellation() }, { error("No candidate") })),
            scoreDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals("netease", matcher.bestMatch(song)!!.origin.provider)
        assertEquals(3500L, testScheduler.currentTime)
    }

    @Test fun cancellationStopsSourceTraversal() = runTest {
        var later = false
        val matcher = OnlineLyricsMatcher({ emptyList() }, { later = true; reference }, additionalSources = adapters() +
            (LyricsMatchSource.QQ_MUSIC to LyricsMatchAdapter({ awaitCancellation() }, { error("No candidate") })),
            scoreDispatcher = StandardTestDispatcher(testScheduler))
        val job = launch { matcher.bestMatch(song) }
        runCurrent(); job.cancelAndJoin()
        assertFalse(later)
    }

    @Test fun explicitProviderPureMusicStopsAlternativeSources() = runBlocking {
        val pure = LyricsParser.parse(kotlinx.serialization.json.Json.parseToJsonElement("""{"pureMusic":true}"""))
        val matcher = OnlineLyricsMatcher({ emptyList() }, { pure }, additionalSources = adapters() +
            (LyricsMatchSource.AMLL to LyricsMatchAdapter({ error("Not reached") }, { error("Not reached") })))
        assertEquals("netease", matcher.bestMatch(song)!!.origin.provider)
        assertEquals(listOf("true"), pure.metadata.values["pureMusic"])
        assertTrue(pure.lines.isNotEmpty())
    }

    @Test fun absentLyricsAreNotTreatedAsAnInstrumentalVerdict() = runBlocking {
        val matcher = OnlineLyricsMatcher({ emptyList() }, { LyricsDocument() }, additionalSources = adapters() +
            (LyricsMatchSource.QISHUI to adapter(LyricsMatchSource.QISHUI, rich())))
        assertEquals("qishui", matcher.bestMatch(song)!!.origin.provider)
    }

    @Test fun wholeLineInstrumentalNoticeStopsButAnOrdinaryLyricMentionDoesNot() = runBlocking {
        for ((text, expected) in listOf("此歌曲为没有填词的纯音乐，请您欣赏" to "netease", "听着纯音乐，我想起了你" to "qishui")) {
            val matcher = OnlineLyricsMatcher({ emptyList() }, { lyrics(text) }, additionalSources = adapters() +
                (LyricsMatchSource.QISHUI to adapter(LyricsMatchSource.QISHUI, rich())))
            assertEquals(expected, matcher.bestMatch(song)!!.origin.provider)
        }
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

    @Test fun sourceMismatchAndMissingProjectConfigurationAreExplicitErrors() = runBlocking {
        val matcher = OnlineLyricsMatcher({ emptyList() }, { error("Unexpected native load") }, additionalSources = mapOf(
            LyricsMatchSource.QQ_MUSIC to LyricsMatchAdapter(
                { listOf(LyricsCandidate(LyricsMatchSource.QISHUI, "7", "Wrong platform")) }, { lyrics("wrong") })))
        assertTrue(runCatching { matcher.search(LyricsMatchSource.QQ_MUSIC, "Fixture") }.isFailure)
        assertTrue(runCatching { matcher.search(LyricsMatchSource.CURRENT_MUSIC, "Fixture") }.exceptionOrNull() is LyricsMatchException)
    }
}
