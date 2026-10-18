package io.github.currencortex.music.feature.room

import io.github.currencortex.music.core.netease.NeteaseSession
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.data.netease.listen.ListenTogetherFateProtocol
import io.github.currencortex.music.data.netease.listen.ListenTogetherFateReport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class ListenTogetherFateState(
    val partnerUid: Long? = null,
    val scene: Int = 0,
    val report: ListenTogetherFateReport? = null,
    val loading: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null,
    val permissionRequired: Boolean = false,
)

/** A confirmation stays bound to the report, account and page that produced it. */
internal class ListenTogetherFateController(
    private val repo: ListenTogetherFateProtocol,
    private val scope: CoroutineScope,
    private val onError: (String) -> Unit,
) {
    private val mutable = MutableStateFlow(ListenTogetherFateState())
    val state = mutable.asStateFlow()
    private var epoch = 0L
    private var request: Job? = null
    private var session: NeteaseSession? = null

    private fun valid(expected: NeteaseSession, generation: Long) {
        if (epoch != generation) throw CancellationException("Fate page replaced")
        repo.checkSession(expected)
    }

    fun open(partnerUid: Long, scene: Int = 0) {
        require(partnerUid > 0 && scene >= 0)
        request?.cancel(); session = null
        val generation = ++epoch
        mutable.value = ListenTogetherFateState(partnerUid, scene, loading = true)
        request = scope.launch {
            try {
                val expected = repo.captureSession()
                valid(expected, generation)
                val report = repo.fate(expected, partnerUid, scene)
                valid(expected, generation)
                session = expected
                mutable.update { it.copy(report = report) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { failed(failure, generation) }
            finally { if (generation == epoch) mutable.update { it.copy(loading = false) } }
        }
    }

    fun retry() { mutable.value.let { it.partnerUid?.let { uid -> open(uid, it.scene) } } }

    fun shareSession(report: ListenTogetherFateReport): NeteaseSession {
        check(mutable.value.report === report && !mutable.value.loading && !mutable.value.deleting)
        val expected = checkNotNull(session)
        valid(expected, epoch)
        return expected
    }

    /** No write happens on page open. Only the explicit confirmation supplies this exact report. */
    fun deleteConfirmed(report: ListenTogetherFateReport, deleteRecords: Boolean) {
        val current = mutable.value
        val expected = session ?: return
        if (current.report !== report || current.loading || current.deleting || current.deleted) return
        val generation = epoch
        mutable.update { it.copy(deleting = true, error = null) }
        request = scope.launch {
            try {
                valid(expected, generation)
                repo.deleteFate(expected, report.partnerUid, report.ltType, deleteRecords)
                valid(expected, generation)
                session = null
                mutable.update { it.copy(report = null, deleted = true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { failed(failure, generation) }
            finally { if (generation == epoch) mutable.update { it.copy(deleting = false) } }
        }
    }

    private fun failed(failure: Exception, generation: Long) {
        if (generation != epoch) return
        val message = when (failure) {
            is ApiException -> failure.kind.message
            else -> "暂时无法处理缘分报告，请重试"
        }
        // On account changes the old person's report must not remain visible or shareable.
        if (failure is ApiException && failure.kind in setOf(ErrorKind.NeteaseSessionChanged, ErrorKind.NeteaseBindingRequired)) {
            session = null
            mutable.update { it.copy(report = null) }
        }
        mutable.update { it.copy(error = message, permissionRequired = failure is ApiException && failure.kind == ErrorKind.Forbidden) }
        onError(message)
    }

    fun close() { ++epoch; request?.cancel(); session = null; mutable.value = ListenTogetherFateState() }
}
