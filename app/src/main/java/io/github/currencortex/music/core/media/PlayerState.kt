package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.song.*

enum class PlayerMode { LOCAL, CAST, ROOM }
data class HighSpecWarning(val songId: Long, val source: AudioSource)
data class PlayerState(val song: Song? = null, val playing: Boolean = false, val loading: Boolean = false,
                       val resolving: Boolean = false,
                       val positionMs: Long = 0, val durationMs: Long = 0, val error: String? = null,
                       val warning: HighSpecWarning? = null, val mode: PlayerMode = PlayerMode.LOCAL,
                       val canControlPlayback: Boolean = true, val playRequested: Boolean = false,
                       val playbackSpeed: Float = 1f, val bufferedPositionMs: Long = 0) {
    val showPause get() = playing || playRequested
    fun bufferedProgress(duration: Long = durationMs): Float =
        if (resolving || warning != null || mode == PlayerMode.CAST || duration <= 0) 0f
        else (bufferedPositionMs.coerceAtLeast(0).toDouble() / duration).coerceIn(0.0, 1.0).toFloat()
}
