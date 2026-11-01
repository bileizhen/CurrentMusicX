package io.github.currencortex.music.feature.recognition

/** Integrates unsigned 8-bit samples into exact 8 kHz bins without losing fractional phase. */
internal class Unsigned8PcmResampler(private val inputRateHz: Int, maximumSamples: Int = RecognitionWindows.MAX_SAMPLES) {
    init {
        require(inputRateHz in 1..384_000)
        require(maximumSamples in 1..RecognitionWindows.MAX_SAMPLES)
    }

    private val output = ShortArray(maximumSamples)
    private var filledTicks = 0
    private var weightedSum = 0L
    var size = 0
        private set
    var inputSamples = 0L
        private set
    var hasSignal = false
        private set
    var lastLevel = 0f
        private set
    val full get() = size == output.size

    fun append(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        var peak = 0
        for (index in offset until offset + length) {
            if (full) break
            val signed = ((bytes[index].toInt() and 0xff) - 128) * 256
            peak = maxOf(peak, kotlin.math.abs(signed))
            hasSignal = hasSignal || signed != 0
            inputSamples++
            // One source sample spans 8,000 integer ticks; one output bin spans
            // inputRateHz ticks. This is exact for both 44.1 kHz and 48 kHz.
            var remaining = 8000
            while (remaining > 0 && !full) {
                val weight = minOf(remaining, inputRateHz - filledTicks)
                weightedSum += signed.toLong() * weight
                filledTicks += weight
                remaining -= weight
                if (filledTicks == inputRateHz) {
                    output[size++] = (weightedSum / inputRateHz).toShort()
                    filledTicks = 0
                    weightedSum = 0
                }
            }
        }
        lastLevel = peak / 32768f
    }

    fun copyPcm(count: Int = size): ShortArray {
        require(count in 0..size)
        return output.copyOf(count)
    }

    fun clear() {
        output.fill(0)
        filledTicks = 0; weightedSum = 0; size = 0; inputSamples = 0
        hasSignal = false; lastLevel = 0f
    }
}

/** Visualizer returns overlapping snapshots, not a stream of disjoint buffers. */
internal class SystemMixPcmAccumulator(inputRateHz: Int, maximumSamples: Int = RecognitionWindows.MAX_SAMPLES) {
    private val resampler = Unsigned8PcmResampler(inputRateHz, maximumSamples)
    private var previous = ByteArray(0)
    private var prefix = IntArray(0)
    val size get() = resampler.size
    val inputSamples get() = resampler.inputSamples
    val hasSignal get() = resampler.hasSignal
    val lastLevel get() = resampler.lastLevel
    val full get() = resampler.full

    /** Returns output samples added. Identical snapshots never advance the recording clock. */
    fun appendSnapshot(frame: ByteArray): Int {
        require(frame.isNotEmpty())
        if (full || previous.contentEquals(frame)) return 0
        val before = size
        val overlap = if (previous.isEmpty()) 0 else suffixPrefixOverlap(frame)
        resampler.append(frame, overlap, frame.size - overlap)
        if (previous.size != frame.size) {
            previous.fill(0)
            previous = ByteArray(frame.size)
        }
        frame.copyInto(previous)
        return size - before
    }

    /** KMP avoids quadratic scans on long nearly silent or repetitive waveforms. */
    private fun suffixPrefixOverlap(frame: ByteArray): Int {
        if (prefix.size != frame.size) {
            prefix.fill(0)
            prefix = IntArray(frame.size)
        } else prefix.fill(0)
        var matched = 0
        for (index in 1 until frame.size) {
            while (matched > 0 && frame[index] != frame[matched]) matched = prefix[matched - 1]
            if (frame[index] == frame[matched]) matched++
            prefix[index] = matched
        }
        matched = 0
        for (value in previous) {
            while (matched > 0 && (matched == frame.size || value != frame[matched])) matched = prefix[matched - 1]
            if (value == frame[matched]) matched++
        }
        return matched
    }

    fun copyPcm(count: Int = size): ShortArray = resampler.copyPcm(count)

    fun clear() {
        previous.fill(0); prefix.fill(0); resampler.clear()
        previous = ByteArray(0); prefix = IntArray(0)
    }
}
