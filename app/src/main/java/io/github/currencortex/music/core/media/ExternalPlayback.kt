package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.song.Song

/** Commands go to the room authority or renderer; they never play another local engine. */
interface ExternalPlayback {
    fun play(playing: Boolean)
    fun seek(position: Long)
    fun next()
    fun previous()
    fun request(song: Song)
    fun stop()
    fun ended() {}
    fun failed() {}
}
