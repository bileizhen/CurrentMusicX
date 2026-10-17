package io.github.currencortex.music.data.library

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.NeteaseSongActionsRepository
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlaylistLikeTarget(val uid: Long, val likedIds: Set<Long>)
data class PlaylistLikeImportState(val playlistId: Long = 0, val name: String = "", val running: Boolean = false,
    val total: Int = 0, val processed: Int = 0, val imported: Int = 0, val skipped: Int = 0,
    val failed: Int = 0, val error: String? = null, val failures: List<String> = emptyList())

/** Explicit adds are idempotent. Closing a page never cancels the application-owned job. */
class PlaylistLikeImporter(private val scope: CoroutineScope, private val session: () -> RequestSession,
    private val accountId: () -> Long, private val load: suspend (Long) -> Playlist,
    private val target: suspend (RequestSession) -> PlaylistLikeTarget,
    private val like: suspend (Song, RequestSession, Long) -> Unit,
    private val pause: suspend () -> Unit = { delay(300) }) {
    private val mutable = MutableStateFlow(PlaylistLikeImportState())
    val state = mutable.asStateFlow()
    private var job: Job? = null

    @Synchronized fun start(id: Long, name: String) {
        if (mutable.value.running) return
        val expected = session()
        val owner = accountId()
        mutable.value = PlaylistLikeImportState(id, name, running = true)
        job = scope.launch {
            fun checkOwner() {
                ensureActive()
                if (expected != session() || owner != accountId()) throw ApiException(ErrorKind.NeteaseSessionChanged)
            }
            try {
                if (owner <= 0) throw ApiException(ErrorKind.Unauthorized)
                val playlist = load(id)
                checkOwner()
                if (!playlist.editable(owner)) throw ApiException(ErrorKind.Forbidden)
                mutable.update { it.copy(name = playlist.name, total = playlist.songs.size) }
                val destination = target(expected)
                checkOwner()
                val seen = destination.likedIds.toMutableSet()
                var wrote = false
                for (song in playlist.songs) {
                    checkOwner()
                    val songId = NeteaseSongActionsRepository.songId(song)
                    if (songId == null || !seen.add(songId)) {
                        mutable.update { it.copy(processed = it.processed + 1, skipped = it.skipped + 1) }
                        continue
                    }
                    if (wrote) { pause(); checkOwner() }
                    wrote = true
                    when (val result = appResult { like(song, expected, destination.uid) }) {
                        is AppResult.Success -> mutable.update { it.copy(processed = it.processed + 1, imported = it.imported + 1) }
                        is AppResult.Failure -> {
                            mutable.update { it.copy(processed = it.processed + 1, failed = it.failed + 1,
                                failures = (it.failures + "${song.name}：${result.kind.message}").take(10)) }
                            if (result.kind in setOf(ErrorKind.Unauthorized, ErrorKind.NeteaseBindingRequired,
                                    ErrorKind.NeteaseSessionChanged, ErrorKind.Forbidden, ErrorKind.RateLimited,
                                    ErrorKind.Network, ErrorKind.Timeout)) throw ApiException(result.kind)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                mutable.update { it.copy(error = "已停止，已导入的歌曲会保留") }
                throw cancelled
            } catch (failure: Exception) {
                mutable.update { it.copy(error = (failure as? ApiException)?.kind?.message ?: "导入失败，请稍后重试") }
            } finally {
                mutable.update { it.copy(running = false) }
            }
        }
    }

    fun stop() { job?.cancel() }
}
