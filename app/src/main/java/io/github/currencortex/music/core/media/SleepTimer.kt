package io.github.currencortex.music.core.media

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sleep timer ("定时关闭"). Counts down on wall-clock deadlines rather than playback position, so it
 * keeps working while the player is paused, the sheet is closed or the app is backgrounded. The
 * player stays the only source of truth for what is playing.
 */
class SleepTimer(private val player: PlayerController, private val scope: CoroutineScope) {
    data class State(
        val enabled: Boolean = false,
        val minutes: Int = 0,
        /** Keep playing until the current song ends instead of pausing the moment the timer runs out. */
        val extendToSongEnd: Boolean = false,
        val remainingMs: Long = 0L,
        /** True once the deadline passed and we are waiting for the current song to finish. */
        val waitingForSongEnd: Boolean = false,
        /** One-shot message for the UI; cleared by [consumeNotice]. */
        val notice: String? = null,
    )

    val state = MutableStateFlow(State())
    private var deadline = 0L
    private var job: Job? = null

    fun start(minutes: Int, extendToSongEnd: Boolean = state.value.extendToSongEnd) {
        val clamped = minutes.coerceIn(1, MAX_MINUTES)
        schedule(clamped * 60_000L, clamped, extendToSongEnd)
    }

    /** Short-duration entry point so tests do not have to wait a whole minute. */
    internal fun startForTesting(durationMs: Long, extendToSongEnd: Boolean = false) =
        schedule(durationMs, 1, extendToSongEnd)

    private fun schedule(durationMs: Long, minutes: Int, extendToSongEnd: Boolean) {
        deadline = SystemClock.elapsedRealtime() + durationMs
        state.value = State(enabled = true, minutes = minutes, extendToSongEnd = extendToSongEnd,
            remainingMs = durationMs)
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) break
                state.value = state.value.copy(remainingMs = remaining)
                delay(500)
            }
            expire()
        }
    }

    fun stop(notice: String? = null) {
        job?.cancel()
        job = null
        deadline = 0L
        state.value = State(notice = notice)
    }

    fun setExtendToSongEnd(value: Boolean) {
        state.value = state.value.copy(extendToSongEnd = value)
    }

    fun consumeNotice() { state.value = state.value.copy(notice = null) }

    private suspend fun expire() {
        val current = state.value
        // A paused player needs no pause, and there is no song to wait for.
        if (current.extendToSongEnd && player.state.value.playing) {
            state.value = current.copy(remainingMs = 0L, waitingForSongEnd = true)
            val anchor = player.state.value.song?.id
            val budget = (player.state.value.durationMs - player.state.value.positionMs).coerceAtLeast(0L) + SONG_END_GRACE_MS
            val boundary = player.completedTracks.value
            // A repeat-one queue never changes song id, so the wait is bounded by the remaining track.
            withTimeoutOrNull(budget) {
                combine(player.state, player.completedTracks) { playing, ended -> playing to ended }.first { (playing, ended) ->
                    !state.value.extendToSongEnd || (!playing.playing && !playing.playRequested && !playing.loading) ||
                        playing.song?.id != anchor || ended != boundary ||
                        (playing.durationMs > 0 && playing.positionMs >= playing.durationMs)
                }
            }
        }
        player.pause()
        job = null
        state.value = State(notice = if (current.extendToSongEnd) "已播完当前歌曲，已暂停播放" else "定时关闭已生效，已暂停播放")
    }

    companion object {
        const val MAX_MINUTES = 24 * 60
        private const val SONG_END_GRACE_MS = 30_000L
    }
}
