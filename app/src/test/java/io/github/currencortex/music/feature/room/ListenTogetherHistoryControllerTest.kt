package io.github.currencortex.music.feature.room

import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.binding.BoundProfile
import io.github.currencortex.music.data.netease.listen.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListenTogetherHistoryControllerTest {
    private class Protocol : ListenTogetherHistoryProtocol {
        var revision = 1L
        val expected = NeteaseSession(NeteaseCredential(mapOf("MUSIC_U" to "fixture"), BoundProfile(1, "自己")), 1)
        var page: suspend (String) -> ListenTogetherHistoryPage = { ListenTogetherHistoryPage() }
        var songs: suspend () -> List<Long> = { listOf(10, 20) }
        val cursors = mutableListOf<String>()
        override suspend fun captureSession() = expected
        override fun checkSession(expected: NeteaseSession) { if (expected.revision != revision) throw ApiException(ErrorKind.NeteaseSessionChanged) }
        override suspend fun history(expected: NeteaseSession, cursor: String, size: Int): ListenTogetherHistoryPage { cursors += cursor; return page(cursor) }
        override suspend fun ranking(expected: NeteaseSession) = ListenTogetherRanking()
        override suspend fun record(expected: NeteaseSession, roomId: String) = ListenTogetherRecordReport(roomId, showCollectButton = true)
        override suspend fun roomSongs(expected: NeteaseSession, roomId: String) = songs()
    }
    private fun row(id: String) = ListenTogetherHistoryRecord(id, "room-$id", ListenTogetherParticipant(2, "朋友"))

    @Test fun staleRequestCannotReplaceAnotherPageEvenWhenTheTransportIgnoresCancellation() = runTest {
        val repo = Protocol(); val ready = CompletableDeferred<Unit>()
        repo.page = { withContext(NonCancellable) { ready.await() }; ListenTogetherHistoryPage(listOf(row("old"))) }
        val controller = ListenTogetherHistoryController(repo, backgroundScope) { emptyList() }
        controller.history(); runCurrent()
        controller.report("new-room"); runCurrent()
        ready.complete(Unit); runCurrent()
        assertEquals(ListenArchivePage.REPORT, controller.state.value.page)
        assertEquals("new-room", controller.state.value.report!!.roomId)
        assertNull(controller.state.value.history)
    }

    @Test fun pagesKeepServerOrderDeduplicateRecordsAndStopARepeatedCursor() = runTest {
        val repo = Protocol()
        repo.page = { cursor -> if (cursor.isEmpty()) ListenTogetherHistoryPage(listOf(row("1"), row("2")), hasMore = true, nextCursor = "next", vip = true)
            else ListenTogetherHistoryPage(listOf(row("2"), row("3")), hasMore = true, nextCursor = "next", vip = true) }
        val controller = ListenTogetherHistoryController(repo, backgroundScope) { emptyList() }
        controller.history(); runCurrent(); controller.more(); runCurrent(); controller.more(); runCurrent()
        assertEquals(listOf("1", "2", "3"), controller.state.value.history!!.records.map { it.id })
        assertFalse(controller.state.value.history!!.hasMore)
        assertEquals(listOf("", "next"), repo.cursors)
    }

    @Test fun collectionUsesServerSongIdsAndOrderRatherThanTheCurrentPlaybackQueue() = runTest {
        val repo = Protocol()
        val controller = ListenTogetherHistoryController(repo, backgroundScope) { ids -> assertEquals(listOf(10L, 20L), ids); listOf(Song(20, "二"), Song(10, "一")) }
        controller.report("old-room"); runCurrent(); controller.collectSongs(); runCurrent()
        assertEquals(listOf(10L, 20L), controller.state.value.collectionSongs!!.map { it.id })
        controller.dismissCollection()
        assertNull(controller.state.value.collectionSongs)
    }

    @Test fun dismissedCollectionCannotReopenAfterSlowMetadataResolution() = runTest {
        val repo = Protocol(); val ready = CompletableDeferred<Unit>()
        val controller = ListenTogetherHistoryController(repo, backgroundScope) { ids -> withContext(NonCancellable) { ready.await() }; ids.map { Song(it, "$it") } }
        controller.report("old-room"); runCurrent(); controller.collectSongs(); runCurrent()
        controller.dismissCollection(); ready.complete(Unit); runCurrent()
        assertNull(controller.state.value.collectionSongs)
        assertFalse(controller.state.value.collecting)
    }

    @Test fun switchedAccountResponseIsRejectedAndMissingSongMetadataNeverCreatesAPartialCollection() = runTest {
        val repo = Protocol(); val controller = ListenTogetherHistoryController(repo, backgroundScope) { emptyList() }
        controller.report("old-room"); runCurrent(); controller.collectSongs(); runCurrent()
        assertNull(controller.state.value.collectionSongs)
        assertNotNull(controller.state.value.collectionError)
        repo.page = { repo.revision++; ListenTogetherHistoryPage(listOf(row("private"))) }
        controller.history(); runCurrent()
        assertNull(controller.state.value.history)
        assertEquals(ErrorKind.NeteaseSessionChanged.message, controller.state.value.error)
    }
}
