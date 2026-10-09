package io.github.currencortex.music.core.visualizer

/** Optional window-local fallback when Compose's preference is not honored. Never forces a mode.
 * The request remains latched until stopping/changing target, to avoid rate-switch oscillation.
 * A concurrent owner changing the window wins; closing never overwrites that owner's value. */
class VisualizerRefreshPreference(private val read: () -> Float, private val write: (Float) -> Unit) : AutoCloseable {
    private var original: Float? = null
    private var applied: Float? = null
    private var otherOwner = false
    fun update(target: Float, displayHz: Float) {
        if (!target.isFinite() || target <= 0f) { close(); otherOwner = false; return }
        if (otherOwner) return
        val previous = applied
        if (previous != null && read() != previous) { original = null; applied = null; otherOwner = true; return }
        if (previous == null && target <= displayHz + 2f) return
        val value = target.coerceIn(20f, 120f)
        if (previous == value) return
        if (original == null) original = read()
        write(value); applied = value
    }
    override fun close() {
        val prior = original
        if (prior != null && applied == read()) write(prior)
        original = null; applied = null
    }
}
