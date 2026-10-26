package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SongLyricsStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val song = Song(123, "Song")
    private fun saved(text: String, provider: String = text) = SavedLyrics(
        LyricsDocument(listOf(LyricLine(1000, 3000, text))), LyricsOrigin(text, provider, "reference"))

    @Test fun importedAndMatchedDocumentsSurviveRestartAndSwitchIndependently() = runBlocking {
        val directory = temporary.newFolder()
        val store = SongLyricsStore(directory)
        store.saveMatched(song, saved("online"))
        assertEquals("online", store.active(song)?.document?.lines?.single()?.text)
        store.saveImported(song, saved("file"))
        store.saveMatched(song, saved("replacement"))
        assertEquals("replacement", store.active(song)?.document?.lines?.single()?.text)
        assertFalse(store.settings(song).useImported)
        val reopened = SongLyricsStore(directory)
        assertEquals(store.state.value, reopened.state.value)
        assertEquals("replacement", reopened.active(song)?.document?.lines?.single()?.text)
        reopened.selectImported(song, true)
        assertEquals("file", reopened.active(song)?.document?.lines?.single()?.text)
    }

    @Test fun timingChangesDoNotInvalidateContentAndResetPreservesTiming() = runBlocking {
        val store = SongLyricsStore(temporary.newFolder())
        store.saveImported(song, saved("file"), offsetMs = 350)
        val revision = store.settings(song).contentRevision
        store.setOffset(song, 500)
        assertEquals(revision, store.settings(song).contentRevision)
        assertEquals(1000L, store.active(song)?.document?.lines?.single()?.startTimeMs)
        store.reset(song)
        assertEquals(500L, store.settings(song).offsetMs)
        assertEquals(revision + 1, store.settings(song).contentRevision)
        assertNull(store.settings(song).activeOrigin)
        assertNull(store.active(song))
        store.setOffset(song, Long.MAX_VALUE)
        assertEquals(60_000L, store.settings(song).offsetMs)
        store.setOffset(song, Long.MIN_VALUE)
        assertEquals(-60_000L, store.settings(song).offsetMs)
    }

    @Test fun sameNumericIdAcrossSourcesAndPrivateLibrariesNeverSharesSettings() = runBlocking {
        val store = SongLyricsStore(temporary.newFolder())
        val qq = song.copy(musicSource = MusicSource.QQ_MUSIC)
        val privateOne = song.copy(musicSource = MusicSource.NAVIDROME, navidrome = NavidromeTrackRef("track", "server/a"))
        val privateTwo = privateOne.copy(navidrome = NavidromeTrackRef("track", "server/b"))
        store.setOffset(song, 100)
        store.setOffset(qq, 200)
        store.setOffset(privateOne, 300)
        assertEquals(100L, store.settings(song).offsetMs)
        assertEquals(200L, store.settings(qq).offsetMs)
        assertEquals(300L, store.settings(privateOne).offsetMs)
        assertEquals(0L, store.settings(privateTwo).offsetMs)
        assertNotEquals(SongLyricsStore.identity(privateOne), SongLyricsStore.identity(privateTwo))
        assertFalse(SongLyricsStore.identity(privateOne).contains("server/a"))
    }

    @Test fun explicitlyMatchedEmptyDocumentOverridesProviderLookup() = runBlocking {
        val directory = temporary.newFolder()
        val store = SongLyricsStore(directory)
        store.saveMatched(song, SavedLyrics(LyricsDocument(), LyricsOrigin("纯音乐", "kugou")))
        val restored = SongLyricsStore(directory).active(song)
        assertNotNull(restored)
        assertTrue(restored!!.document.lines.isEmpty())
        assertEquals("纯音乐", restored.origin.label)
    }

    @Test fun invalidReplacementCannotRemoveThePreviousDocumentOrPublishNewMetadata() = runBlocking {
        val directory = temporary.newFolder()
        val store = SongLyricsStore(directory)
        store.saveImported(song, saved("good"))
        val before = store.state.value
        val bad = SavedLyrics(LyricsDocument(listOf(LyricLine(3000, 1000, "bad"))), LyricsOrigin("bad"))
        assertTrue(runCatching { store.saveImported(song, bad) }.isFailure)
        assertEquals(before, store.state.value)
        assertEquals("good", store.active(song)?.document?.lines?.single()?.text)
        assertEquals(before, SongLyricsStore(directory).state.value)
    }

    @Test fun cancelledMutationLeavesDurableChoiceIntact() = runBlocking {
        val directory = temporary.newFolder()
        val store = SongLyricsStore(directory)
        store.saveMatched(song, saved("good"))
        val before = store.state.value
        val job = launch(start = CoroutineStart.LAZY) { store.saveMatched(song, saved("cancelled")) }
        job.cancel(); job.join()
        assertEquals(before, store.state.value)
        assertEquals("good", SongLyricsStore(directory).active(song)?.document?.lines?.single()?.text)
    }

    @Test fun unavailableImportedSourceDoesNotChangeSelection() = runBlocking {
        val store = SongLyricsStore(temporary.newFolder())
        store.saveMatched(song, saved("online"))
        val before = store.state.value
        assertTrue(runCatching { store.selectImported(song, true) }.isFailure)
        assertEquals(before, store.state.value)
    }

    @Test fun metadataDoesNotRetainDocumentsAndOnlyTwoFileGenerationsAreKept() = runBlocking {
        val directory = temporary.newFolder()
        val store = SongLyricsStore(directory)
        store.saveImported(song, saved("first"))
        store.saveImported(song, saved("second"))
        assertEquals(2, directory.listFiles()!!.count { it.name.matches(Regex("[0-9a-f-]{36}\\.json")) })
        store.saveImported(song, saved("third"))
        assertEquals(2, directory.listFiles()!!.count { it.name.matches(Regex("[0-9a-f-]{36}\\.json")) })
        assertFalse(java.io.File(directory, "index.json").readText().contains("\"document\""))
        store.reset(song)
        assertEquals(1, directory.listFiles()!!.count { it.name.matches(Regex("[0-9a-f-]{36}\\.json")) })
        store.setOffset(song, 100)
        assertEquals(0, directory.listFiles()!!.count { it.name.matches(Regex("[0-9a-f-]{36}\\.json")) })
    }

    @Test fun corruptedPrimaryRestoresPreviousDocumentFromBackupAndPreservesDamagedBytes() = runBlocking {
        val directory = temporary.newFolder()
        val store = SongLyricsStore(directory)
        store.saveMatched(song, saved("first"))
        store.saveMatched(song, saved("second"))
        val damaged = "{ damaged index content"
        java.io.File(directory, "index.json").writeText(damaged)
        val recovered = SongLyricsStore(directory)
        assertNotNull(recovered.recoveryMessage)
        assertEquals("first", recovered.active(song)?.document?.lines?.single()?.text)
        assertEquals(damaged, directory.listFiles()!!.single { ".corrupt-" in it.name }.readText())
        recovered.saveMatched(song, saved("after recovery"))
        assertEquals("after recovery", SongLyricsStore(directory).active(song)?.document?.lines?.single()?.text)
    }

    @Test fun noReadableIndexKeepsApplicationOpenButBlocksWritesAcrossRestart() = runBlocking {
        val directory = temporary.newFolder()
        val store = SongLyricsStore(directory)
        store.saveImported(song, saved("saved"))
        val damaged = "{ broken"
        java.io.File(directory, "index.json").writeText(damaged)
        repeat(2) {
            val failed = SongLyricsStore(directory)
            assertNotNull(failed.recoveryMessage)
            assertTrue(failed.state.value.isEmpty())
            assertNull(failed.active(song))
            assertTrue(runCatching { failed.saveMatched(song, saved("must not replace")) }.isFailure)
            assertTrue(runCatching { failed.setOffset(song, 100) }.isFailure)
        }
        assertEquals(damaged, directory.listFiles()!!.single { ".corrupt-" in it.name }.readText())
        assertEquals(1, directory.listFiles()!!.count { it.name.matches(Regex("[0-9a-f-]{36}\\.json")) })
    }

    @Test fun oversizedPrimaryIsNotReadAndCanRecoverFromBoundedBackup() = runBlocking {
        val directory = temporary.newFolder()
        val store = SongLyricsStore(directory)
        store.saveMatched(song, saved("good"))
        store.setOffset(song, 200)
        java.io.RandomAccessFile(java.io.File(directory, "index.json"), "rw").use { it.setLength(8L * 1024 * 1024 + 1) }
        val recovered = SongLyricsStore(directory)
        assertEquals("good", recovered.active(song)?.document?.lines?.single()?.text)
        assertNotNull(recovered.recoveryMessage)
        assertEquals(8L * 1024 * 1024 + 1, directory.listFiles()!!.single { ".corrupt-" in it.name }.length())
    }

    @Test fun malformedBackupCannotPreventReadingHealthyPrimary() = runBlocking {
        val directory = temporary.newFolder()
        val store = SongLyricsStore(directory)
        store.saveMatched(song, saved("healthy"))
        store.setOffset(song, 200)
        java.io.File(directory, "index.backup.json").writeText("broken backup")
        val recovered = SongLyricsStore(directory)
        assertEquals("healthy", recovered.active(song)?.document?.lines?.single()?.text)
        assertEquals(200L, recovered.settings(song).offsetMs)
        assertNotNull(recovered.recoveryMessage)
        recovered.setOffset(song, 300)
        assertEquals(300L, SongLyricsStore(directory).settings(song).offsetMs)
        assertEquals("broken backup", directory.listFiles()!!.single { ".corrupt-" in it.name }.readText())
    }
}
