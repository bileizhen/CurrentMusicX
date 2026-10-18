package io.github.currencortex.music.feature.room

import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.binding.BoundProfile
import io.github.currencortex.music.data.netease.listen.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListenTogetherFateControllerTest {
    private class Protocol : ListenTogetherFateProtocol {
        var revision = 1L
        val expected = NeteaseSession(NeteaseCredential(mapOf("MUSIC_U" to "fixture"), BoundProfile(1, "自己")), 1)
        var read: suspend (Long) -> ListenTogetherFateReport = { report(it) }
        val writes = mutableListOf<Triple<Long, Int, Boolean>>()
        override suspend fun captureSession() = expected
        override fun checkSession(expected: NeteaseSession) { if (expected.revision != revision) throw ApiException(ErrorKind.NeteaseSessionChanged) }
        override suspend fun fate(expected: NeteaseSession, partnerUid: Long, scene: Int) = read(partnerUid)
        override suspend fun deleteFate(expected: NeteaseSession, partnerUid: Long, ltType: Int, deleteRecords: Boolean) {
            checkSession(expected); writes += Triple(partnerUid, ltType, deleteRecords)
        }
    }
    companion object {
        private fun report(uid: Long) = ListenTogetherFateReport(uid, ListenTogetherParticipant(1, "自己"),
            ListenTogetherParticipant(uid, "听友"), ltType = 4)
    }

    @Test fun openingRetryingAndClosingNeverWritesAndOldReadCannotReplaceNewPeer() = runTest {
        val repo = Protocol(); val gate = CompletableDeferred<Unit>()
        repo.read = { uid -> if (uid == 2L) withContext(NonCancellable) { gate.await() }; report(uid) }
        val controller = ListenTogetherFateController(repo, backgroundScope) {}
        controller.open(2); runCurrent(); controller.open(3); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals(3L, controller.state.value.report!!.partnerUid)
        controller.retry(); runCurrent(); controller.close(); runCurrent()
        assertNull(controller.state.value.partnerUid)
        assertTrue(repo.writes.isEmpty())
    }

    @Test fun staleConfirmationCannotDeleteAnotherPageAndRepeatTapsWriteOnce() = runTest {
        val repo = Protocol(); val controller = ListenTogetherFateController(repo, backgroundScope) {}
        controller.open(2); runCurrent()
        val stale = controller.state.value.report!!
        controller.open(3); runCurrent()
        controller.deleteConfirmed(stale, false); runCurrent()
        assertTrue(repo.writes.isEmpty())
        val current = controller.state.value.report!!
        controller.deleteConfirmed(current, false); controller.deleteConfirmed(current, true); runCurrent()
        assertEquals(listOf(Triple(3L, 4, false)), repo.writes)
        assertTrue(controller.state.value.deleted)
        assertNull(controller.state.value.report)
    }

    @Test fun historyDeletionRequiresExplicitOptInAndReadAccountMustStillMatch() = runTest {
        val repo = Protocol(); val errors = mutableListOf<String>()
        val controller = ListenTogetherFateController(repo, backgroundScope, errors::add)
        controller.open(2); runCurrent()
        val stale = controller.state.value.report!!
        repo.revision = 2
        assertThrows(ApiException::class.java) { controller.shareSession(stale) }
        controller.deleteConfirmed(stale, true); runCurrent()
        assertTrue(repo.writes.isEmpty()); assertNull(controller.state.value.report)
        assertEquals(listOf(ErrorKind.NeteaseSessionChanged.message), errors)
        repo.revision = 1
        controller.open(3); runCurrent()
        controller.deleteConfirmed(controller.state.value.report!!, true); runCurrent()
        assertEquals(listOf(Triple(3L, 4, true)), repo.writes)
    }

    @Test fun membershipFailureIsExposedToTheTopNoticeWithoutAReportOrMutation() = runTest {
        val repo = Protocol(); repo.read = { throw ApiException(ErrorKind.Forbidden) }
        val errors = mutableListOf<String>()
        val controller = ListenTogetherFateController(repo, backgroundScope, errors::add)
        controller.open(2); runCurrent()
        assertTrue(controller.state.value.permissionRequired)
        assertFalse(controller.state.value.loading)
        assertNull(controller.state.value.report)
        assertEquals(listOf(ErrorKind.Forbidden.message), errors)
        assertTrue(repo.writes.isEmpty())
    }
}
