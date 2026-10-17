package io.github.currencortex.music.core.media

/** Give foreground playback a time buffer before competing for its network connection. */
internal class AudioPreloadBudget {
    private var owner: String? = null
    private var allowed = false
    fun reset() { owner = null; allowed = false }
    fun allow(key: String, positionMs: Long, bufferedMs: Long, durationMs: Long): Boolean {
        if (key != owner) { owner = key; allowed = false }
        val remaining = (durationMs - positionMs).coerceAtLeast(0)
        val ahead = (bufferedMs - positionMs).coerceAtLeast(0)
        if (durationMs <= 0 || remaining == 0L) { allowed = false; return false }
        // Hysteresis avoids repeatedly cancelling/restarting a writer near the threshold.
        allowed = if (ahead >= remaining) true else if (allowed) ahead >= 8_000 else ahead >= 20_000
        return allowed
    }
}
