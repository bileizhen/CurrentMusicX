package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsManagementFlowTest {
    @get:Rule val temporary = TemporaryFolder()
    private val song = Song(7, "Original", "Singer")
    private fun document(text: String) = LyricsDocument(listOf(LyricLine(0, 3000, text)))

    @Test fun automaticSelectionStillHonorsBothManualSourcesAndFreshlyMatchesAgainAfterReset() = runBlocking {
        val choices = SongLyricsStore(temporary.newFolder())
        val forbidden = LyricsProvider { error("Automatic mode must not load the old default") }
        var automaticCalls = 0
        val repository = LyricsRepository(forbidden, forbidden, LyricsCache(temporary.newFolder()),
            selection = { LyricsSelection(listOf("auto"), forbidden, automatic = true) }, overrides = choices,
            autoMatch = {
                automaticCalls++
                SavedLyrics(document("automatic $automaticCalls"), LyricsOrigin("网易云音乐", "netease"))
            })
        assertEquals("automatic 1", repository.load(song).document.lines.single().text)
        assertNull(choices.active(song))
        choices.saveImported(song, SavedLyrics(document("imported"), LyricsOrigin("local.lrc", "imported")), 250)
        assertEquals("imported", repository.load(song).document.lines.single().text)
        choices.saveMatched(song, SavedLyrics(document("matched"), LyricsOrigin("QQ 音乐", "qq")))
        assertEquals("matched", repository.load(song).document.lines.single().text)
        choices.selectImported(song, true)
        assertEquals("imported", repository.load(song).document.lines.single().text)
        assertEquals(1, automaticCalls)
        choices.reset(song)
        val reset = repository.load(song)
        assertEquals("automatic 2", reset.document.lines.single().text)
        assertEquals("netease", reset.origin?.provider)
        assertEquals(2, automaticCalls)
        assertNull(choices.active(song))
        assertEquals(250L, choices.settings(song).offsetMs)
        assertEquals(Song(7, "Original", "Singer"), song)
    }

    @Test fun automaticSelectionTreatsAnExplicitEmptyManualDocumentAsInstrumental() = runBlocking {
        val choices = SongLyricsStore(temporary.newFolder())
        choices.saveImported(song, SavedLyrics(LyricsDocument(), LyricsOrigin("纯音乐.fia", "imported")))
        val forbidden = LyricsProvider { error("The instrumental override must skip providers") }
        val repository = LyricsRepository(forbidden, forbidden, LyricsCache(temporary.newFolder()),
            selection = { LyricsSelection(listOf("auto"), forbidden, automatic = true) }, overrides = choices,
            autoMatch = { error("The instrumental override must skip matching") })
        val result = repository.load(song)
        assertTrue(result.document.lines.isEmpty())
        assertNull(result.error)
        assertEquals("纯音乐.fia", result.origin?.label)
    }

    @Test fun selectedOverridesBeatMemoryAndResetRestoresProviderWithoutChangingSong() = runBlocking {
        val choices = SongLyricsStore(temporary.newFolder())
        var calls = 0
        val provider = LyricsProvider { calls++; LyricsProviderResult.Parsed(document("original")) }
        val repository = LyricsRepository(provider, provider, LyricsCache(temporary.newFolder()),
            selection = { LyricsSelection(listOf("source"), provider, LyricsOrigin("原来源")) }, overrides = choices)
        assertEquals("original", repository.load(song).document.lines.single().text)
        choices.saveImported(song, SavedLyrics(document("imported"), LyricsOrigin("local.lrc", "imported")), 250)
        assertEquals("imported", repository.load(song).document.lines.single().text)
        choices.saveMatched(song, SavedLyrics(document("matched"), LyricsOrigin("酷狗音乐", "kugou")))
        assertEquals("matched", repository.load(song).document.lines.single().text)
        choices.selectImported(song, true)
        assertEquals("imported", repository.load(song).document.lines.single().text)
        choices.reset(song)
        assertEquals("original", repository.load(song).document.lines.single().text)
        assertEquals("原来源", repository.load(song).origin?.label)
        assertEquals(250L, choices.settings(song).offsetMs)
        assertEquals(1, calls)
        assertEquals(Song(7, "Original", "Singer"), song)
    }

    @Test fun explicitInstrumentalOverrideNeverFallsBackToAnotherSongsLyrics() = runBlocking {
        val choices = SongLyricsStore(temporary.newFolder())
        choices.saveMatched(song, SavedLyrics(LyricsDocument(), LyricsOrigin("纯音乐", "imported")))
        val forbidden = LyricsProvider { error("An explicit instrumental choice is authoritative") }
        val result = LyricsRepository(forbidden, forbidden, LyricsCache(temporary.newFolder()), overrides = choices).load(song)
        assertTrue(result.document.lines.isEmpty())
        assertNull(result.error)
        assertEquals("纯音乐", result.origin?.label)
    }

    @Test fun privateLibraryLyricsKeepTheirActualOriginRegardlessOfGlobalNeteaseSelection() = runBlocking {
        val provider = LyricsProvider { LyricsProviderResult.Parsed(document("private library")) }
        val repository = LyricsRepository(provider, provider, LyricsCache(temporary.newFolder()), selection = {
            LyricsSelection(listOf("netease"), provider, LyricsOrigin("网易云音乐", "netease"))
        })
        val privateSong = song.copy(musicSource = MusicSource.NAVIDROME,
            navidrome = NavidromeTrackRef("track", "library-owner"))
        assertEquals("Navidrome", repository.load(privateSong).origin?.label)
    }

    @Test fun missingDefaultLyricsAutomaticallyMatchWithoutTurningTheResultIntoAManualOverride() = runBlocking {
        val choices = SongLyricsStore(temporary.newFolder())
        val missing = LyricsProvider { LyricsProviderResult.Failure(LyricsErrorCode.NO_LYRICS, "missing") }
        var automatic = 0
        val repository = LyricsRepository(missing, missing, LyricsCache(temporary.newFolder()), overrides = choices,
            autoMatch = { automatic++; SavedLyrics(document("automatic native"), LyricsOrigin("网易云音乐", "netease")) })
        val result = repository.load(song)
        assertEquals("automatic native", result.document.lines.single().text)
        assertEquals("netease", result.origin?.provider)
        assertNull(choices.active(song))
        assertEquals(result, repository.load(song))
        assertEquals(1, automatic)
        choices.saveImported(song, SavedLyrics(document("chosen file"), LyricsOrigin("local.lrc", "imported")))
        assertEquals("chosen file", repository.load(song).document.lines.single().text)
        assertEquals(1, automatic)
    }

    @Test fun existingProviderLyricsDoNotTriggerAutomaticCrossPlatformSearch() = runBlocking {
        val provider = LyricsProvider { LyricsProviderResult.Parsed(document("original source")) }
        val repository = LyricsRepository(provider, provider, LyricsCache(temporary.newFolder()),
            autoMatch = { error("A successful original lookup needs no automatic search") })
        assertEquals("original source", repository.load(song).document.lines.single().text)
    }

    @Test fun automaticServiceFailureIsNotReportedAsAnOrdinaryMissingLyric() = runBlocking {
        val missing = LyricsProvider { LyricsProviderResult.Failure(LyricsErrorCode.NO_LYRICS, "missing") }
        val repository = LyricsRepository(missing, missing, LyricsCache(temporary.newFolder()),
            autoMatch = { throw LyricsMatchException("providers unavailable") })
        val result = repository.load(song)
        assertTrue(result.document.lines.isEmpty())
        assertTrue(result.error!!.contains("暂时无法读取"))
        assertEquals(LyricsErrorCode.NETWORK_ERROR, result.issues.last().code)
    }

    @Test fun offsetAndOtherSongEditsDoNotReloadButCurrentContentChangesDo() = runTest {
        val requests = MutableStateFlow(LyricsRequest(song, listOf("account")))
        val options = MutableStateFlow<Map<String, LyricsSongSettings>>(emptyMap())
        var loads = 0
        val playback = PlaybackLyrics(managedLyricsRequests(requests, options), {
            loads++; LyricsResult(document("loaded $loads"), origin = LyricsOrigin("source"))
        }, backgroundScope, options)
        runCurrent()
        assertEquals(1, loads)
        val currentKey = SongLyricsStore.identity(song)
        options.value = mapOf(currentKey to LyricsSongSettings(offsetMs = 250))
        runCurrent()
        assertEquals(1, loads)
        assertEquals(250L, playback.state.value.songOffsetMs)
        assertFalse(playback.state.value.loading)
        options.value += SongLyricsStore.identity(song.copy(id = 8)) to LyricsSongSettings(contentRevision = 12)
        runCurrent()
        assertEquals(1, loads)
        options.value += currentKey to LyricsSongSettings(offsetMs = 250, contentRevision = 1)
        runCurrent()
        assertEquals(2, loads)
        assertEquals("loaded 2", playback.state.value.document.lines.single().text)
    }

    @Test fun sameNumericIdFromAnotherSourceRejectsOldResultsAndHasIndependentOffset() = runTest {
        val qq = song.copy(musicSource = MusicSource.QQ_MUSIC)
        val gate = CompletableDeferred<Unit>()
        val requests = MutableStateFlow(LyricsRequest(song))
        val options = MutableStateFlow(mapOf(SongLyricsStore.identity(song) to LyricsSongSettings(offsetMs = 500),
            SongLyricsStore.identity(qq) to LyricsSongSettings(offsetMs = -250)))
        val playback = PlaybackLyrics(managedLyricsRequests(requests, options), { current ->
            if (current.musicSource == MusicSource.NETEASE) withContext(NonCancellable) { gate.await() }
            LyricsResult(document(current.musicSource.name))
        }, backgroundScope, options)
        runCurrent()
        requests.value = LyricsRequest(qq)
        runCurrent()
        assertTrue(playback.state.value.matches(qq))
        assertFalse(playback.state.value.matches(song))
        assertEquals(-250L, playback.state.value.songOffsetMs)
        gate.complete(Unit)
        runCurrent()
        assertEquals("QQ_MUSIC", playback.state.value.document.lines.single().text)
        assertEquals(-250L, playback.state.value.songOffsetMs)
    }
}
