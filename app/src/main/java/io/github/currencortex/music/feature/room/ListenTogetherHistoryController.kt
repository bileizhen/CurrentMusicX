package io.github.currencortex.music.feature.room

import io.github.currencortex.music.core.netease.NeteaseSession
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.data.netease.listen.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class ListenArchivePage { HISTORY, RANKING, REPORT }

internal data class ListenTogetherArchiveState(
    val page: ListenArchivePage? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val history: ListenTogetherHistoryPage? = null,
    val ranking: ListenTogetherRanking? = null,
    val report: ListenTogetherRecordReport? = null,
    val reportRoomId: String = "",
    val sourceRoom: ListenTogetherRoom? = null,
    val self: ListenTogetherParticipant? = null,
    val peerFilter: Long? = null,
    val error: String? = null,
    val permissionRequired: Boolean = false,
    val collecting: Boolean = false,
    val collectionSongs: List<Song>? = null,
    val collectionError: String? = null,
)

/** Page-scoped requests never change playback or silently invite another person. */
internal class ListenTogetherHistoryController(
    private val repo: ListenTogetherHistoryProtocol,
    private val scope: CoroutineScope,
    private val details: suspend (List<Long>) -> List<Song>,
) {
    private val mutable = MutableStateFlow(ListenTogetherArchiveState())
    val state = mutable.asStateFlow()
    private var epoch = 0L
    private var request: Job? = null
    private var collection: Job? = null
    private var collectionEpoch = 0L

    private fun valid(expected: NeteaseSession, generation: Long) {
        if (generation != epoch) throw CancellationException("History page replaced")
        repo.checkSession(expected)
    }

    private fun load(next: ListenTogetherArchiveState, block: suspend (NeteaseSession, Long) -> Unit) {
        request?.cancel(); collection?.cancel()
        val generation = ++epoch
        mutable.value = next.copy(loading = true, error = null, permissionRequired = false)
        request = scope.launch {
            try {
                val expected = repo.captureSession()
                valid(expected, generation)
                val profile = expected.credential.profile
                mutable.update { it.copy(self = profile?.let { user -> ListenTogetherParticipant(user.uid, user.nickname, user.avatar) }) }
                block(expected, generation)
                valid(expected, generation)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (generation == epoch) mutable.update {
                it.copy(error = message(failure), permissionRequired = failure is ApiException && failure.kind == ErrorKind.Forbidden)
            } }
            finally { if (generation == epoch) mutable.update { it.copy(loading = false) } }
        }
    }

    fun history(peer: Long? = null) = load(ListenTogetherArchiveState(page = ListenArchivePage.HISTORY, peerFilter = peer)) { expected, generation ->
        val result = repo.history(expected)
        valid(expected, generation)
        mutable.update { it.copy(history = result.copy(hasMore = result.hasMore && result.nextCursor.isNotBlank())) }
    }

    fun ranking() = load(ListenTogetherArchiveState(page = ListenArchivePage.RANKING)) { expected, generation ->
        val result = repo.ranking(expected)
        valid(expected, generation)
        mutable.update { it.copy(ranking = result) }
    }

    fun report(roomId: String, sourceRoom: ListenTogetherRoom? = null) {
        require(ListenTogetherInvitation.validRoomId(roomId))
        load(ListenTogetherArchiveState(page = ListenArchivePage.REPORT, reportRoomId = roomId, sourceRoom = sourceRoom)) { expected, generation ->
            val result = repo.record(expected, roomId)
            valid(expected, generation)
            mutable.update { it.copy(report = result) }
        }
    }

    fun more() {
        val old = mutable.value
        val page = old.history ?: return
        if (old.page != ListenArchivePage.HISTORY || old.loading || old.loadingMore || !page.hasMore || page.nextCursor.isBlank()) return
        val generation = epoch
        val cursor = page.nextCursor
        mutable.update { it.copy(loadingMore = true, error = null) }
        request = scope.launch {
            try {
                val expected = repo.captureSession()
                valid(expected, generation)
                val result = repo.history(expected, cursor)
                valid(expected, generation)
                mutable.update { it.copy(history = result.copy(
                    records = (page.records + result.records).distinctBy { row -> row.id.ifBlank { "${row.roomId}:${row.startTimeMs}" } },
                    mostListen = page.mostListen,
                    hasMore = result.hasMore && result.nextCursor.isNotBlank() && result.nextCursor != cursor)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (generation == epoch) mutable.update { it.copy(error = message(failure)) } }
            finally { if (generation == epoch) mutable.update { it.copy(loadingMore = false) } }
        }
    }

    fun collectSongs() {
        val current = mutable.value
        if (current.page != ListenArchivePage.REPORT || current.report?.showCollectButton != true || current.collecting) return
        val generation = epoch
        val attempt = ++collectionEpoch
        mutable.update { it.copy(collecting = true, collectionError = null, collectionSongs = null) }
        collection = scope.launch {
            try {
                val expected = repo.captureSession()
                valid(expected, generation)
                val ids = repo.roomSongs(expected, current.reportRoomId)
                valid(expected, generation)
                if (attempt != collectionEpoch) throw CancellationException("Collection dismissed")
                if (ids.isEmpty()) {
                    mutable.update { it.copy(collectionSongs = emptyList(), collectionError = "本次没有可收藏的歌曲") }
                    return@launch
                }
                // Server order is authoritative; CurrentMusic's resolver supplies song metadata only.
                val resolved = details(ids).associateBy { it.id }
                valid(expected, generation)
                if (attempt != collectionEpoch) throw CancellationException("Collection dismissed")
                val songs = ids.mapNotNull(resolved::get)
                check(songs.size == ids.size) { "部分歌曲信息暂不可用，请重试" }
                mutable.update { it.copy(collectionSongs = songs, collectionError = if (songs.isEmpty()) "本次没有可收藏的歌曲" else null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (generation == epoch && attempt == collectionEpoch) mutable.update { it.copy(collectionError = message(failure)) } }
            finally { if (generation == epoch && attempt == collectionEpoch) mutable.update { it.copy(collecting = false) } }
        }
    }

    fun dismissCollection() { ++collectionEpoch; collection?.cancel(); mutable.update { it.copy(collecting = false, collectionSongs = null, collectionError = null) } }
    fun retry() { when (mutable.value.page) {
        ListenArchivePage.HISTORY -> history(mutable.value.peerFilter)
        ListenArchivePage.RANKING -> ranking()
        ListenArchivePage.REPORT -> report(mutable.value.reportRoomId, mutable.value.sourceRoom)
        null -> Unit
    } }
    fun close() { ++epoch; request?.cancel(); collection?.cancel(); mutable.value = ListenTogetherArchiveState() }
    private fun message(failure: Exception): String = when (failure) {
        is ApiException -> failure.kind.message
        is IllegalStateException -> failure.message ?: "暂时无法读取一起听记录"
        else -> "暂时无法读取一起听记录，请重试"
    }
}
