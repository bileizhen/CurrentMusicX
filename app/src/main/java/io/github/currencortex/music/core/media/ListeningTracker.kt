package io.github.currencortex.music.core.media

/** Monotonic elapsed playing time, independent of seek position and media duration. */
class ListeningTracker {
    private var lastTime = 0L
    private var playing = false
    private var pending = 0L
    fun update(now: Long, isPlaying: Boolean) {
        if (playing) pending += (now - lastTime).coerceAtLeast(0)
        lastTime = maxOf(lastTime, now)
        playing = isPlaying
    }
    fun drain(now: Long): Long {
        update(now, playing)
        return pending.also { pending = 0 }
    }
}
