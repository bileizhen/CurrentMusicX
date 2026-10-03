package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.flow.MutableStateFlow

class SilentExternalPlayer : ExternalPlayer {
    override val state = MutableStateFlow(PlayerState())
    override val queue = PlaybackQueue()
    override var external: ExternalPlayback? = null
    private var saved: QueueSnapshot? = null
    override suspend fun beginExternal(mode: PlayerMode, controls: ExternalPlayback): Boolean {
        if (state.value.mode != PlayerMode.LOCAL) return false
        saved = queue.state.value; external = controls; state.value = state.value.copy(mode = mode, playing = false)
        return true
    }
    override fun endExternal(controls: ExternalPlayback) {
        if (external !== controls) return
        external = null; saved?.let(queue::restore); state.value = PlayerState(song = queue.state.value.current); saved = null
    }
    override suspend fun roomTrack(song: Song?, url: String?, position: Long, playing: Boolean) {
        queue.replace(listOfNotNull(song), 0); state.value = PlayerState(song, playing, positionMs = position, mode = PlayerMode.ROOM)
    }
    override suspend fun roomCorrection(position: Long, seek: Boolean, speed: Float, playing: Boolean) {
        state.value = state.value.copy(positionMs = if (seek) position else state.value.positionMs, playing = playing)
    }
}
