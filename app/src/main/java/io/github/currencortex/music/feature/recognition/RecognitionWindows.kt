package io.github.currencortex.music.feature.recognition

/** Cumulative query milestones observed in the NetEase 9.6.15 recognition configuration. */
internal class RecognitionWindows {
    private var next = 0

    /** The consumer owns each copy; encoding must be queued rather than block the capture thread. */
    fun emitAvailable(sampleCount: Int, copyPrefix: (Int) -> ShortArray, onWindow: (ShortArray) -> Unit) {
        require(sampleCount in 0..MAX_SAMPLES)
        while (next < milestones.size && sampleCount >= milestones[next]) {
            val count = milestones[next++]
            val window = copyPrefix(count)
            check(window.size == count)
            try {
                onWindow(window)
            } catch (failure: Throwable) {
                window.fill(0)
                throw failure
            }
        }
    }

    companion object {
        const val SAMPLE_RATE = 8000
        const val MAX_SAMPLES = 15 * SAMPLE_RATE
        private val milestones = intArrayOf(2, 3, 6, 9, 10, 12, 15).map { it * SAMPLE_RATE }
    }
}
