package io.github.currencortex.music.core.media

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

/**
 * ITU-R BS.1770-4 K-weighting pre-filter (high shelf + RLB high-pass) computed for any sample rate.
 * Uses transposed direct-form-II biquads with independent channel delay lines.
 */
internal class KWeightingFilter(val sampleRate: Int, val channels: Int) {
    private val b0_1: Double
    private val b1_1: Double
    private val b2_1: Double
    private val a1_1: Double
    private val a2_1: Double

    private val b0_2: Double
    private val b1_2: Double
    private val b2_2: Double
    private val a1_2: Double
    private val a2_2: Double

    private val z1_1 = DoubleArray(channels)
    private val z2_1 = DoubleArray(channels)
    private val z1_2 = DoubleArray(channels)
    private val z2_2 = DoubleArray(channels)

    init {
        require(sampleRate > 0 && channels > 0)
        // Stage 1: acoustic head high-shelf pre-filter.
        val f1 = min(1681.974450955533, sampleRate * 0.45)
        val vh = 10.0.pow(3.999843853973347 / 20.0)
        val vb = vh.pow(0.4996667741545416)
        val q1 = 0.7071752369554196
        val k1 = tan(PI * f1 / sampleRate)
        val a0_1 = 1.0 + k1 / q1 + k1 * k1
        b0_1 = (vh + vb * k1 / q1 + k1 * k1) / a0_1
        b1_1 = 2.0 * (k1 * k1 - vh) / a0_1
        b2_1 = (vh - vb * k1 / q1 + k1 * k1) / a0_1
        a1_1 = 2.0 * (k1 * k1 - 1.0) / a0_1
        a2_1 = (1.0 - k1 / q1 + k1 * k1) / a0_1

        // Stage 2: RLB low-frequency high-pass filter.
        val f2 = min(38.13547087602444, sampleRate * 0.45)
        val q2 = 0.5003270373238773
        val k2 = tan(PI * f2 / sampleRate)
        val a0_2 = 1.0 + k2 / q2 + k2 * k2
        b0_2 = 1.0 / a0_2
        b1_2 = -2.0 / a0_2
        b2_2 = 1.0 / a0_2
        a1_2 = 2.0 * (k2 * k2 - 1.0) / a0_2
        a2_2 = (1.0 - k2 / q2 + k2 * k2) / a0_2
    }

    fun processSample(input: Double, channel: Int): Double {
        val y1 = b0_1 * input + z1_1[channel]
        val n1_1 = b1_1 * input - a1_1 * y1 + z2_1[channel]
        val n2_1 = b2_1 * input - a2_1 * y1
        if (y1 != y1 || n1_1 != n1_1 || n2_1 != n2_1) {
            resetChannel(channel)
            return 0.0
        }
        z1_1[channel] = flushDenormal(n1_1)
        z2_1[channel] = flushDenormal(n2_1)

        val y2 = b0_2 * y1 + z1_2[channel]
        val n1_2 = b1_2 * y1 - a1_2 * y2 + z2_2[channel]
        val n2_2 = b2_2 * y1 - a2_2 * y2
        if (y2 != y2 || n1_2 != n1_2 || n2_2 != n2_2) {
            resetChannel(channel)
            return 0.0
        }
        z1_2[channel] = flushDenormal(n1_2)
        z2_2[channel] = flushDenormal(n2_2)
        return y2
    }

    fun reset() {
        z1_1.fill(0.0)
        z2_1.fill(0.0)
        z1_2.fill(0.0)
        z2_2.fill(0.0)
    }

    private fun resetChannel(channel: Int) {
        z1_1[channel] = 0.0
        z2_1[channel] = 0.0
        z1_2[channel] = 0.0
        z2_2[channel] = 0.0
    }

    private fun flushDenormal(value: Double): Double =
        if (value > -1e-30 && value < 1e-30) 0.0 else value
}

/**
 * EBU R128 / ITU-R BS.1770-4 gated loudness meter with O(1) memory histogram and zero audio-thread
 * allocations during processing.
 */
internal class EbuR128Meter(val sampleRate: Int, val channels: Int) {
    private val filter = KWeightingFilter(sampleRate, channels)
    private val channelWeights = DoubleArray(channels) { channel -> channelWeight(channels, channel) }
    private val stepFrames = (sampleRate / 10).coerceAtLeast(1)
    private val stepSquares = DoubleArray(channels)
    private var framesInStep = 0

    private val ring400 = DoubleArray(4)
    private var ring400Cursor = 0
    private var ring400Count = 0

    private val ring3s = DoubleArray(30)
    private var ring3sCursor = 0
    private var ring3sCount = 0

    private val binPower = DoubleArray(BIN_COUNT)
    private val binCount = IntArray(BIN_COUNT)
    private var totalUngatedPower = 0.0
    private var totalUngatedBlocks = 0

    var activeSignalBlocks = 0
        private set
    private var activeSignalPowerSum = 0.0

    var gatedIntegratedBlocks = 0
        private set
    var integratedLufs: Double? = null
        private set
    var shortTermLufs: Double? = null
        private set
    var momentaryLufs: Double? = null
        private set
    var activeAverageLufs: Double? = null
        private set
    var samplePeak = 0.0
        private set

    /** True once a new 100 ms sub-block completed in [processFrame]. */
    fun processFrame(frame: DoubleArray): Boolean {
        for (channel in 0 until channels) {
            val sample = frame[channel]
            val absSample = if (sample >= 0.0) sample else -sample
            if (absSample > samplePeak) samplePeak = absSample
            val weighted = filter.processSample(sample, channel)
            stepSquares[channel] += weighted * weighted
        }
        framesInStep++
        if (framesInStep < stepFrames) return false
        finishStep()
        return true
    }

    private fun finishStep() {
        var stepPower = 0.0
        for (channel in 0 until channels) {
            stepPower += channelWeights[channel] * (stepSquares[channel] / framesInStep)
            stepSquares[channel] = 0.0
        }
        framesInStep = 0

        ring400[ring400Cursor] = stepPower
        ring400Cursor = (ring400Cursor + 1) % ring400.size
        if (ring400Count < ring400.size) ring400Count++

        ring3s[ring3sCursor] = stepPower
        ring3sCursor = (ring3sCursor + 1) % ring3s.size
        if (ring3sCount < ring3s.size) ring3sCount++

        val currentMomentaryPower = if (ring400Count == ring400.size) {
            (ring400[0] + ring400[1] + ring400[2] + ring400[3]) * 0.25
        } else {
            var sum = 0.0
            for (i in 0 until ring400Count) sum += ring400[i]
            sum / ring400Count
        }
        val mLufs = powerToLufs(currentMomentaryPower)
        momentaryLufs = if (ring400Count == ring400.size) mLufs else null

        if (ring3sCount >= 10) {
            var sum = 0.0
            for (i in 0 until ring3sCount) sum += ring3s[i]
            shortTermLufs = powerToLufs(sum / ring3sCount)
        }

        if (ring400Count == ring400.size && mLufs > ABSOLUTE_GATE_LUFS) {
            val bin = ((mLufs - MIN_BIN_LUFS) / BIN_WIDTH_LU).toInt().coerceIn(0, BIN_COUNT - 1)
            binPower[bin] += currentMomentaryPower
            binCount[bin]++
            totalUngatedPower += currentMomentaryPower
            totalUngatedBlocks++

            if (mLufs > ACTIVE_SIGNAL_GATE_LUFS) {
                activeSignalBlocks++
                activeSignalPowerSum += currentMomentaryPower
                activeAverageLufs = powerToLufs(activeSignalPowerSum / activeSignalBlocks)
            }

            if (totalUngatedBlocks <= 60 || totalUngatedBlocks % 5 == 0) {
                recomputeIntegrated()
            }
        }
    }

    /** Force recomputation (e.g., before persisting when track finishes). */
    fun flushIntegration(): Double? {
        if (totalUngatedBlocks > 0) recomputeIntegrated()
        return integratedLufs
    }

    /** Reset filter delay lines and partial sub-block on seek, keeping track-level integration. */
    fun resetOnSeek() {
        filter.reset()
        stepSquares.fill(0.0)
        framesInStep = 0
        ring400.fill(0.0)
        ring400Cursor = 0
        ring400Count = 0
        ring3s.fill(0.0)
        ring3sCursor = 0
        ring3sCount = 0
        momentaryLufs = null
        shortTermLufs = null
    }

    fun resetAll() {
        resetOnSeek()
        binPower.fill(0.0)
        binCount.fill(0)
        totalUngatedPower = 0.0
        totalUngatedBlocks = 0
        activeSignalBlocks = 0
        activeSignalPowerSum = 0.0
        gatedIntegratedBlocks = 0
        integratedLufs = null
        activeAverageLufs = null
        samplePeak = 0.0
    }

    /** Best instant loudness estimate during the first 100..300 ms before the 400 ms window fills. */
    fun earlyLoudnessEstimate(): Double? {
        momentaryLufs?.let { return it }
        if (ring400Count <= 0) return null
        var sum = 0.0
        for (i in 0 until ring400Count) sum += ring400[i]
        return powerToLufs(sum / ring400Count)
    }

    private fun recomputeIntegrated() {
        if (totalUngatedBlocks == 0) return
        val ungatedLufs = powerToLufs(totalUngatedPower / totalUngatedBlocks)
        val relativeThreshold = ungatedLufs - 10.0
        var powerSum = 0.0
        var countSum = 0
        for (i in 0 until BIN_COUNT) {
            val count = binCount[i]
            if (count == 0) continue
            val center = MIN_BIN_LUFS + (i + 0.5) * BIN_WIDTH_LU
            if (center >= relativeThreshold) {
                powerSum += binPower[i]
                countSum += count
            }
        }
        if (countSum > 0) {
            gatedIntegratedBlocks = countSum
            integratedLufs = powerToLufs(powerSum / countSum)
        }
    }

    companion object {
        private const val ABSOLUTE_GATE_LUFS = -70.0
        private const val ACTIVE_SIGNAL_GATE_LUFS = -45.0
        private const val MIN_BIN_LUFS = -70.0
        private const val BIN_WIDTH_LU = 0.2
        private const val BIN_COUNT = 400

        internal fun powerToLufs(power: Double): Double = -0.691 + 10.0 * log10(max(power, 1e-12))

        internal fun channelWeight(totalChannels: Int, index: Int): Double = when (totalChannels) {
            4 -> if (index >= 2) 1.41 else 1.0
            5 -> if (index >= 3) 1.41 else 1.0
            6 -> when (index) {
                3 -> 0.0 // LFE
                4, 5 -> 1.41
                else -> 1.0
            }
            8 -> when (index) {
                3 -> 0.0 // LFE
                4, 5, 6, 7 -> 1.41
                else -> 1.0
            }
            else -> 1.0
        }
    }
}

/**
 * Real-time loudness normalization DSP combining known metadata/cached LUFS, EBU R128 live
 * estimation, click-free exponential gain smoothing, and linked-channel peak protection.
 */
internal class LoudnessNormalizationDsp(val sampleRate: Int, val channels: Int) {
    val meter = EbuR128Meter(sampleRate, channels)
    private val gainSmooth = 1.0 - exp(-1.0 / (0.040 * sampleRate))
    private val limiterRelease = 1.0 - exp(-1.0 / (0.100 * sampleRate))

    private var spec: TrackLoudnessSpec = TrackLoudnessSpec()
    private var lastEnabled = false
    private var lastTargetLufs = Double.NaN
    private var lastPreferAlbum = false
    private var lastAdaptive = true

    var currentGainDb = 0.0
        private set
    private var targetGainDb = 0.0
    private var targetLinearGain = 1.0
    private var currentLinearGain = 1.0
    private var limiterGain = 1.0
    var activeSourceLabel = ""
        private set

    val settledBypass: Boolean
        get() = !lastEnabled && abs(currentLinearGain - 1.0) < 1e-5 && limiterGain > 0.99999

    fun updateState(
        settings: VolumeNormalizationSettings,
        trackSpec: TrackLoudnessSpec,
        snapInitialGain: Boolean = false,
    ) {
        val specChanged = trackSpec != spec
        spec = trackSpec
        lastEnabled = settings.enabled
        lastTargetLufs = settings.targetLufs.toDouble()
        lastPreferAlbum = settings.preferAlbumGain
        lastAdaptive = settings.adaptive

        if (!settings.enabled) {
            targetGainDb = 0.0
            targetLinearGain = 1.0
            currentGainDb = 0.0
            activeSourceLabel = ""
            if (snapInitialGain) {
                currentLinearGain = 1.0
                limiterGain = 1.0
            }
            return
        }

        val desired = computeDesiredGainDb(settings, stepSlew = !snapInitialGain && !specChanged)
        targetGainDb = desired
        currentGainDb = desired
        targetLinearGain = 10.0.pow(desired / 20.0)
        if (snapInitialGain) {
            currentLinearGain = targetLinearGain
            limiterGain = 1.0
        }
    }

    fun onNewTrack(settings: VolumeNormalizationSettings, trackSpec: TrackLoudnessSpec) {
        meter.resetAll()
        targetGainDb = 0.0
        updateState(settings, trackSpec, snapInitialGain = true)
    }

    fun onSeek(settings: VolumeNormalizationSettings, trackSpec: TrackLoudnessSpec) {
        meter.resetOnSeek()
        updateState(settings, trackSpec, snapInitialGain = false)
    }

    fun processFrame(frame: DoubleArray, settings: VolumeNormalizationSettings) {
        if (settings.enabled != lastEnabled ||
            settings.targetLufs.toDouble() != lastTargetLufs ||
            settings.preferAlbumGain != lastPreferAlbum ||
            settings.adaptive != lastAdaptive
        ) {
            updateState(settings, spec, snapInitialGain = false)
        }

        // Sanitize incoming samples and feed the EBU R128 meter with PRE-GAIN audio.
        for (channel in 0 until channels) {
            val s = frame[channel]
            frame[channel] = if (s == s) s.coerceIn(-1.0, 1.0) else 0.0
        }
        val stepCompleted = if (settings.enabled && settings.adaptive) {
            meter.processFrame(frame)
        } else {
            // Even when adaptive estimation is off, still sample peak/LUFS if enabled so cache/UI stay accurate.
            if (settings.enabled) meter.processFrame(frame) else false
        }

        if (stepCompleted && settings.enabled) {
            val nextDb = computeDesiredGainDb(settings, stepSlew = true)
            targetGainDb = nextDb
            currentGainDb = nextDb
            targetLinearGain = 10.0.pow(nextDb / 20.0)
        }

        currentLinearGain += gainSmooth * (targetLinearGain - currentLinearGain)
        if (abs(targetLinearGain - currentLinearGain) < 1e-6) currentLinearGain = targetLinearGain

        var scaledPeak = 0.0
        for (channel in 0 until channels) {
            val scaled = frame[channel] * currentLinearGain
            frame[channel] = scaled
            if (scaled > scaledPeak) scaledPeak = scaled
            if (-scaled > scaledPeak) scaledPeak = -scaled
        }

        val targetLimiter = if (scaledPeak > LIMITER_CEILING) LIMITER_CEILING / scaledPeak else 1.0
        limiterGain = if (targetLimiter < limiterGain) {
            targetLimiter
        } else {
            limiterGain + limiterRelease * (targetLimiter - limiterGain)
        }

        for (channel in 0 until channels) {
            val out = frame[channel] * limiterGain
            frame[channel] = if (out == out) out.coerceIn(-1.0, 1.0) else 0.0
        }
    }

    private fun computeDesiredGainDb(settings: VolumeNormalizationSettings, stepSlew: Boolean): Double {
        val target = settings.targetLufs.toDouble()
        val ref = spec.preferredReference(settings.preferAlbumGain)
        if (ref != null && ref.authoritative) {
            activeSourceLabel = ref.sourceLabel
            return (target - ref.lufs).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        }

        if (!settings.adaptive) {
            activeSourceLabel = if (ref != null) ref.sourceLabel else ""
            val fallbackLufs = ref?.lufs ?: return 0.0
            return (target - fallbackLufs).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        }

        val activeBlocks = meter.activeSignalBlocks
        val desiredRaw: Double = if (ref != null) {
            // Heuristic initial hint (e.g., NetEase song/url `gain`), refine smoothly once live LUFS settles.
            val integrated = meter.integratedLufs
            if (activeBlocks >= 30 && integrated != null) {
                val blend = ((activeBlocks - 30) / 50.0).coerceIn(0.0, 1.0)
                val blendedLufs = ref.lufs + blend * (integrated - ref.lufs)
                activeSourceLabel = if (blend >= 0.8) "实时测算" else ref.sourceLabel
                (target - blendedLufs).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
            } else {
                activeSourceLabel = ref.sourceLabel
                (target - ref.lufs).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
            }
        } else {
            // Pure real-time adaptive estimation.
            if (activeBlocks == 0) {
                val early = meter.earlyLoudnessEstimate()
                if (early != null && early > target + 2.0) {
                    activeSourceLabel = "实时限幅"
                    (target - early).coerceIn(MIN_GAIN_DB, 0.0)
                } else {
                    activeSourceLabel = "等待人声/乐段"
                    0.0
                }
            } else {
                activeSourceLabel = "实时测算"
                val liveLufs = when {
                    activeBlocks >= 50 && meter.integratedLufs != null -> meter.integratedLufs!!
                    else -> meter.activeAverageLufs ?: meter.integratedLufs ?: target
                }
                val earlyBoostCap = when {
                    activeBlocks < 15 -> 3.0
                    activeBlocks < 50 -> 3.0 + (activeBlocks - 15) / 35.0 * (MAX_GAIN_DB - 3.0)
                    else -> MAX_GAIN_DB
                }
                (target - liveLufs).coerceIn(MIN_GAIN_DB, earlyBoostCap)
            }
        }

        if (!stepSlew) return desiredRaw
        val maxUp = if (activeBlocks < 30) 0.50 else 0.25
        val maxDown = if (activeBlocks < 30) 1.50 else 0.50
        return desiredRaw.coerceIn(targetGainDb - maxDown, targetGainDb + maxUp)
    }

    companion object {
        const val MIN_GAIN_DB = -18.0
        const val MAX_GAIN_DB = 9.0
        const val LIMITER_CEILING = 0.95
    }
}
