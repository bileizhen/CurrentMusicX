package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.library.LibraryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LikeDestination { CURRENT_MUSIC, NETEASE }
data class LikeDestinationState(val liked: Boolean? = null, val busy: Boolean = false, val error: String? = null)
data class SongLikeSelection(val song: Song? = null,
    val currentMusic: LikeDestinationState = LikeDestinationState(),
    val netease: LikeDestinationState = LikeDestinationState())

/** The two libraries are independent; loading or failing one never changes the other. */
class SongLikeController(private val library: LibraryRepository, private val netease: NeteaseSongActionsRepository,
    private val scope: CoroutineScope, private val session: () -> RequestSession) {
    private val mutable = MutableStateFlow(SongLikeSelection())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var loads: Job? = null

    fun dismiss() { generation++; loads?.cancel(); mutable.value = SongLikeSelection() }
    fun open(song: Song) {
        dismiss()
        if (song.video) return
        val epoch = generation
        val expected = session()
        mutable.value = SongLikeSelection(song, LikeDestinationState(busy = true), LikeDestinationState(busy = true))
        loads = scope.launch {
            supervisorScope { LikeDestination.entries.forEach { target -> launch { load(song, target, epoch, expected) } } }
        }
    }
    private fun update(target: LikeDestination, epoch: Long, expected: RequestSession, value: LikeDestinationState) {
        if (epoch != generation || expected != session()) return
        mutable.update { if (target == LikeDestination.CURRENT_MUSIC) it.copy(currentMusic = value) else it.copy(netease = value) }
    }
    private suspend fun load(song: Song, target: LikeDestination, epoch: Long, expected: RequestSession) {
        val result = appResult {
            if (expected.token == null && target == LikeDestination.CURRENT_MUSIC) throw ApiException(ErrorKind.Unauthorized)
            when (target) {
                LikeDestination.CURRENT_MUSIC -> { library.refreshStatus(listOf(song.id)); library.statuses.value[song.id]?.liked ?: throw ApiException(ErrorKind.Parse) }
                LikeDestination.NETEASE -> netease.isLiked(NeteaseSongActionsRepository.songId(song) ?: throw ApiException(ErrorKind.NotFound), fresh = true)
            }
        }
        update(target, epoch, expected, when (result) {
            is AppResult.Success -> LikeDestinationState(liked = result.value)
            is AppResult.Failure -> LikeDestinationState(error = result.kind.message)
        })
    }
    fun toggle(target: LikeDestination) {
        val selection = mutable.value
        val song = selection.song ?: return
        val before = if (target == LikeDestination.CURRENT_MUSIC) selection.currentMusic else selection.netease
        if (before.busy) return
        val epoch = generation
        val expected = session()
        update(target, epoch, expected, before.copy(busy = true, error = null))
        scope.launch {
            // An unknown status must be read first; retry never performs a blind mutation.
            if (before.liked == null) { load(song, target, epoch, expected); return@launch }
            val result = appResult {
                if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
                when (target) {
                    LikeDestination.CURRENT_MUSIC -> {
                        when (val write = library.toggleLike(song)) {
                            is AppResult.Failure -> throw ApiException(write.kind)
                            is AppResult.Success -> library.statuses.value[song.id]?.liked ?: throw ApiException(ErrorKind.Parse)
                        }
                    }
                    LikeDestination.NETEASE -> {
                        val desired = !before.liked
                        netease.setLiked(NeteaseSongActionsRepository.songId(song) ?: throw ApiException(ErrorKind.NotFound), desired, expected, song)
                        library.invalidate()
                        desired
                    }
                }
            }
            update(target, epoch, expected, when (result) {
                is AppResult.Success -> LikeDestinationState(liked = result.value)
                is AppResult.Failure -> before.copy(error = result.kind.message)
            })
        }
    }
}
