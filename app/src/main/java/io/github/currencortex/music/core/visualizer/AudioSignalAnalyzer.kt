package io.github.currencortex.music.core.visualizer

import kotlin.math.*

/** Immutable analysis snapshots, independent of Android and of the rendering clock. */
data class AudioAnalysisFrame(
    val timestampNanos: Long = 0,
    val sampleRateHz: Int = 0,
    val captureSize: Int = 0,
    val rms: Float = 0f,
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val spectrum: List<Float> = List(48) { 0f },
    val waveform: List<Float> = emptyList(),
    val kick: Boolean = false,
    val transient: Boolean = false,
    val beatPulse: Float = 0f,
    val peakFrequencyHz: Float = 0f,
)

class AudioSignalAnalyzer(private val bands: Int = 48) {
    init { require(bands in 8..128) }
    private var lastTime = 0L
    private var startTime = 0L
    private var lastKick = 0L
    private var lastTransient = 0L
    private var meanBass = 0f
    private var varianceBass = 0f
    private var meanFlux = 0f
    private var previousBass = 0f
    private var previousBins = FloatArray(0)
    private var previousRate = 0
    private var smooth = AudioAnalysisFrame(spectrum = List(bands) { 0f })

    fun analyze(packet: AudioCapturePacket, bassSensitivity: Float = 1f): AudioAnalysisFrame {
        val n = packet.fft.size
        require(n >= 16 && n.countOneBits() == 1 && packet.waveform.size == n && packet.sampleRateHz > 0)
        require(packet.timestampNanos > 0)
        if (previousBins.size != n / 2 + 1 || previousRate != packet.sampleRateHz) reset()
        require(lastTime == 0L || packet.timestampNanos > lastTime) { "Capture timestamps must increase" }
        val dt = if (lastTime == 0L) 1f / 30 else ((packet.timestampNanos - lastTime) / 1e9f).coerceAtMost(1f)
        if (lastTime == 0L) startTime = packet.timestampNanos
        val bins = FloatArray(n / 2 + 1)
        // Android packs DC at [0], Nyquist at [1], then signed real/imaginary pairs.
        bins[n / 2] = abs(packet.fft[1].toInt()) / 128f
        for (k in 1 until n / 2) bins[k] = (hypot(packet.fft[k * 2].toFloat(), packet.fft[k * 2 + 1].toFloat()) / 128f).coerceAtMost(1f)
        val binHz = packet.sampleRateHz.toFloat() / n
        fun energy(low: Float, high: Float): Float {
            var sum = 0f
            for (k in 1..n / 2) if (k * binHz >= low && k * binHz < high) sum += bins[k] * bins[k]
            return sqrt(sum).coerceAtMost(1f)
        }
        val bass = energy(30f, 250f)
        val mid = energy(250f, 4000f)
        val treble = energy(4000f, minOf(16000f, packet.sampleRateHz / 2f) + 1f)
        val rms = sqrt(packet.waveform.sumOf { val sample = ((it.toInt() and 255) - 128) / 128.0; sample * sample } / n).toFloat()
        val maxHz = minOf(16000f, packet.sampleRateHz / 2f)
        val minHz = minOf(30f, maxHz / 2f)
        val spectrum = List(bands) { band ->
            val low = minHz * (maxHz / minHz).pow(band.toFloat() / bands)
            val high = minHz * (maxHz / minHz).pow((band + 1f) / bands)
            // Log bands narrower than one FFT bin share that bin instead of disappearing.
            val first = ceil(low / binHz).toInt().coerceIn(1, n / 2)
            val end = (ceil(high / binHz).toInt() - 1).coerceIn(first, n / 2)
            var magnitude = 0f
            for (k in first..end) magnitude = maxOf(magnitude, bins[k])
            ln(1 + 15 * magnitude) / ln(16f)
        }
        val flux = if (previousBins.size == bins.size) bins.indices.drop(1).sumOf { maxOf(0f, bins[it] - previousBins[it]).toDouble() }.toFloat() else 0f
        val warmed = packet.timestampNanos - startTime >= 400_000_000L
        val sensitivity = if (bassSensitivity.isFinite()) bassSensitivity.coerceIn(.5f, 2f) else 1f
        val kick = warmed && bass > .025f && bass - previousBass > .015f / sensitivity &&
            bass > meanBass + maxOf(.02f, 1.8f * sqrt(varianceBass)) / sensitivity &&
            packet.timestampNanos - lastKick >= 180_000_000L
        val transient = warmed && flux > maxOf(.08f, meanFlux * 1.8f) && packet.timestampNanos - lastTransient >= 100_000_000L
        if (kick) lastKick = packet.timestampNanos
        if (transient) lastTransient = packet.timestampNanos
        val alpha = 1f - exp(-dt / .8f)
        val deviation = bass - meanBass
        meanBass += alpha * deviation
        varianceBass += alpha * (deviation * deviation - varianceBass)
        meanFlux += alpha * (flux - meanFlux)
        val peak = (1..n / 2).maxByOrNull { bins[it] } ?: 1
        smooth = AudioAnalysisFrame(packet.timestampNanos, packet.sampleRateHz, n,
            envelope(smooth.rms, rms, dt), envelope(smooth.bass, bass, dt),
            envelope(smooth.mid, mid, dt), envelope(smooth.treble, treble, dt),
            spectrum.mapIndexed { i, value -> envelope(smooth.spectrum[i], value, dt) },
            List(minOf(128, n)) { i -> ((packet.waveform[i * n / minOf(128, n)].toInt() and 255) - 128) / 128f },
            kick, transient, if (kick) 1f else smooth.beatPulse * exp(-dt / .22f),
            if (bins[peak] > 0f) peak * binHz else 0f)
        previousBins = bins; previousBass = bass; previousRate = packet.sampleRateHz; lastTime = packet.timestampNanos
        return smooth
    }

    fun reset() {
        lastTime = 0; startTime = 0; lastKick = 0; lastTransient = 0
        meanBass = 0f; varianceBass = 0f; meanFlux = 0f; previousBass = 0f
        previousBins = FloatArray(0); previousRate = 0
        smooth = AudioAnalysisFrame(spectrum = List(bands) { 0f })
    }

    companion object {
        fun envelope(current: Float, target: Float, seconds: Float): Float {
            val tau = if (target > current) .025f else .18f
            return current + (target - current) * (1f - exp(-seconds.coerceAtLeast(0f) / tau))
        }
    }
}
