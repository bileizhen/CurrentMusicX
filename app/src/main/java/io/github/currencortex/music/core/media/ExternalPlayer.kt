package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.flow.MutableStateFlow

/** Playback boundary also lets protocol tests use a silent player without binding MusicService. */
interface ExternalPlayer {
    val state: MutableStateFlow<PlayerState>
    val queue: PlaybackQueue
    val external: ExternalPlayback?
    suspend fun beginExternal(mode: PlayerMode, controls: ExternalPlayback): Boolean
    fun endExternal(controls: ExternalPlayback)
    suspend fun roomTrack(song: Song?, url: String?, position: Long, playing: Boolean)
    suspend fun roomCorrection(position: Long, seek: Boolean, speed: Float, playing: Boolean)
}
