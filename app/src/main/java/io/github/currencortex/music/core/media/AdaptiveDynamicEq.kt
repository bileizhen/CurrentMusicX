package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.settings.AdaptiveAudioSettings
import io.github.currencortex.music.data.settings.DynamicEqCurve
import kotlin.math.*

/** Windowed spectral shaping. Analysis never changes the frame or the saved manual curve. */
internal class AdaptiveDynamicEq(private val sampleRate: Int, private val channels: Int) {
    /** Updated once per analysis block. The audio path separately smooths filter coefficients. */
    val gains = DoubleArray(10)
    private val blockFrames = (sampleRate * BLOCK_SECONDS).roundToInt().coerceAtLeast(1)
    private val lowPass = Array(channels) { DoubleArray(9) }
    private val alpha = DoubleArray(9) { index ->
        val angular = 2 * PI * min(CROSSOVERS[index], sampleRate * .45)
        angular / (sampleRate + angular)
    }
    private val blockEnergy = DoubleArray(10)
    private val blockPeaks = DoubleArray(10)
    private val energyHistory = Array(MAX_BLOCKS) { DoubleArray(10) }
    private val peakHistory = Array(MAX_BLOCKS) { DoubleArray(10) }
    private val totalHistory = DoubleArray(MAX_BLOCKS)
    private val energy = DoubleArray(10)
    private val peaks = DoubleArray(10)
    private val confidence = DoubleArray(10)
    private val targets = DoubleArray(10)
    private val shaped = DoubleArray(10)
    private var cursor = 0
    private var historySize = 0
    private var frames = 0
    private var blockTotal = 0.0

    init { require(sampleRate > 0 && channels > 0) }

    fun process(frame: DoubleArray, settings: AdaptiveAudioSettings) {
        for (channel in 0 until channels) {
            // The upstream harmonic stage deliberately keeps floating-point headroom.
            val input = frame[channel].let { if (it.isFinite()) it.coerceIn(-64.0, 64.0) else 0.0 }
            blockTotal += input * input / channels
            var previous = 0.0
            for (band in 0 until 9) {
                val state = lowPass[channel]
                state[band] += alpha[band] * (input - state[band])
                val value = state[band] - previous
                blockEnergy[band] += value * value / channels
                blockPeaks[band] = max(blockPeaks[band], abs(value))
                previous = state[band]
            }
            val high = input - previous
            blockEnergy[9] += high * high / channels
            blockPeaks[9] = max(blockPeaks[9], abs(high))
        }
        frames++
        if (frames < blockFrames) return
        blockEnergy.copyInto(energyHistory[cursor])
        blockPeaks.copyInto(peakHistory[cursor])
        totalHistory[cursor] = blockTotal
        cursor = (cursor + 1) % MAX_BLOCKS
        historySize = min(MAX_BLOCKS, historySize + 1)
        update(settings)
        frames = 0
        blockTotal = 0.0
        blockEnergy.fill(0.0)
        blockPeaks.fill(0.0)
    }

    private fun update(settings: AdaptiveAudioSettings) {
        val speed = settings.speed.coerceIn(0, 4)
        val count = min(historySize, (WINDOW_SECONDS[speed] / BLOCK_SECONDS).roundToInt())
        energy.fill(0.0)
        peaks.fill(0.0)
        var total = 0.0
        for (age in 0 until count) {
            val index = historyIndex(age)
            total += totalHistory[index]
            for (band in 0..9) {
                energy[band] += energyHistory[index][band]
                peaks[band] += peakHistory[index][band] / count
            }
        }
        if (!settings.enabled || !settings.dynamicEq || sqrt(blockTotal / blockFrames) < SILENCE_RMS) {
            for (band in 0..9) gains[band] *= .82
            return
        }
        val strength = STRENGTHS[settings.strength.coerceIn(0, 4)]
        val samples = count.toDouble() * blockFrames
        val sub = sum(energy, 0, 2)
        val bass = sub + energy[3]
        val low = bass + energy[4]
        val presence = energy[5] + energy[6]
        val treble = sum(energy, 7, 9)
        val upper = presence + treble
        val bassAbsolute = smooth(-43.0, -24.0, amplitudeDb(sqrt(bass / samples)))
        val bassRelative = smooth(-27.0, -12.0, powerDb(bass, total))
        val lowPresence = smooth(-34.0, -11.0, powerDb(low, total))
        val lowDominance = smooth(-18.0, -4.0, powerDb(low, upper))
        val presenceAmount = smooth(-29.0, -11.0, powerDb(presence, total))
        val lack4k = 1 - smooth(-28.0, -11.0, powerDb(energy[7], total))
        val lack8k = 1 - smooth(-34.0, -15.0, powerDb(energy[8], total))
        val lack16k = 1 - smooth(-38.0, -18.0, powerDb(energy[9], total))
        val lackTreble = 1 - smooth(-28.0, -10.0, powerDb(treble, total))
        val airNeed = ((.56 * lackTreble + .44 * lack4k) * presenceAmount).coerceIn(0.0, 1.0)
        val bassEvidence = (.44 * bassAbsolute + .36 * bassRelative + .20 * lowPresence).coerceIn(0.0, 1.0)

        val latest = historyIndex(0)
        val previous = historyIndex(if (historySize > 1) 1 else 0)
        val latestLow = sum(energyHistory[latest], 0, 3)
        val latestRms = sqrt(latestLow / blockFrames)
        val latestPeak = peakHistory[latest][0] * .24 + peakHistory[latest][1] * .28 +
            peakHistory[latest][2] * .28 + peakHistory[latest][3] * .20
        val crest = max(0.0, amplitudeDb(latestPeak) - amplitudeDb(latestRms))
        val bassHistoryCount = min(historySize, BASS_HISTORY_BLOCKS)
        var historyBass = 0.0
        var historyTotal = 0.0
        for (age in 0 until bassHistoryCount) {
            val index = historyIndex(age)
            historyBass += sum(energyHistory[index], 0, 3)
            historyTotal += totalHistory[index]
        }
        val historyBassDb = amplitudeDb(sqrt(historyBass / (bassHistoryCount.toDouble() * blockFrames)))
        val sustainedBass = smooth(.7, 1.0, bassHistoryCount.toDouble() / BASS_HISTORY_BLOCKS) * (
            .30 * (1 - smooth(9.0, 18.0, crest)) +
                .22 * smooth(-43.0, -24.0, historyBassDb) +
                .16 * smooth(-27.0, -12.0, powerDb(historyBass, historyTotal)) +
                .18 * smooth(1.2, 6.5, amplitudeDb(latestRms) - historyBassDb) +
                .14 * smooth(-8.0, 0.0, powerDb(energyHistory[latest][3] + energyHistory[latest][4], latestLow)))
        var spectralChange = 0.0
        for (band in 0..9) {
            spectralChange += smooth(1.6, 10.0, abs(powerDb(energyHistory[latest][band], totalHistory[latest]) -
                powerDb(energyHistory[previous][band], totalHistory[previous]))) / 10
            val rmsDb = amplitudeDb(sqrt(energy[band] / samples))
            confidence[band] = .42 * smooth(RMS_THRESHOLDS[band], RMS_THRESHOLDS[band] + 18, rmsDb) +
                .30 * smooth(RELATIVE_THRESHOLDS[band], RELATIVE_THRESHOLDS[band] + 14, powerDb(energy[band], total)) +
                .28 * smooth(PEAK_THRESHOLDS[band], PEAK_THRESHOLDS[band] + 14, amplitudeDb(peaks[band]))
        }
        val levelChange = abs(amplitudeDb(sqrt(totalHistory[latest] / blockFrames)) -
            amplitudeDb(sqrt(totalHistory[previous] / blockFrames)))
        val bassChange = abs(powerDb(latestLow, totalHistory[latest]) -
            powerDb(sum(energyHistory[previous], 0, 3), totalHistory[previous]))
        val transient = (.62 * spectralChange + .18 * smooth(1.8, 8.5, levelChange) +
            .20 * smooth(1.2, 6.2, bassChange)).coerceIn(0.0, 1.0)
        val bassGate = smooth(.18, .54, bassEvidence)
        val warmup = (.12 + .72 * smooth(.78, 1.0, bassHistoryCount.toDouble() / BASS_HISTORY_BLOCKS) +
            .16 * bassEvidence).coerceIn(.12, 1.0)
        val strengthPresence = .78 + .46 * strength
        for (band in 0..9) {
            val audible = confidence[band]
            val relative = powerDb(energy[band], total)
            val lowShare = smooth(-18.0, -3.0, powerDb(energy[band], low))
            targets[band] = when (band) {
                0, 1, 2 -> upperBound(band, strength) * lowPresence * audible * bassGate *
                    (.10 + .42 * lowShare) * (1 - .46 * lowDominance) * (1 - .60 * sustainedBass) * warmup
                3 -> strength * lowPresence * audible * (
                    2.2 * (.26 + .32 * lowShare) * (1 - .38 * sustainedBass) -
                        1.4 * smooth(-27.0, -12.0, powerDb(sub, total)) * smooth(-28.0, -13.0, relative) *
                        smooth(-12.0, -3.0, powerDb(energy[band], low)) * (1 + .42 * sustainedBass))
                4 -> {
                    val muddy = smooth(-26.0, -11.0, relative) * smooth(-10.0, -2.5, powerDb(energy[band], low)) * lowPresence * audible
                    strength * (.45 * smooth(-23.0, -13.0, relative) * (1 - muddy) * lowPresence * audible -
                        2.4 * muddy * (.55 + .45 * smooth(-24.0, -10.0, powerDb(bass, total))))
                }
                5 -> -(.34 + .74 * strengthPresence + .88 * presenceAmount * audible) + .10 * airNeed
                6 -> -(.16 + .46 * strengthPresence + .66 * presenceAmount * audible) + 2.2 * airNeed * (.62 + .22 * lack4k)
                7 -> -(.08 + .28 * strengthPresence + .46 * presenceAmount * audible) + 2.2 * airNeed * (.78 + .22 * lackTreble)
                else -> {
                    val deficit = if (band == 8) lack8k else lack16k
                    val harsh = smooth(-18.0, -6.0, powerDb(energy[8] + energy[9], total)) * audible
                    .26 * (.72 + .28 * strength) * (deficit * (.6 + .4 * lackTreble) * audible - .6 * harsh)
                }
            }.coerceIn(lowerBound(band, strength), upperBound(band, strength))
        }
        // Neighbour blending shapes a curve instead of ten unrelated moving sliders.
        for (band in 0..9) {
            shaped[band] = when {
                band < 3 -> max(targets[band], max(if (band > 0) targets[band - 1] * .18 else 0.0,
                    targets[band + 1] * .22))
                // Upper bass and low mids can require cuts; boost-only blending would erase them.
                band <= 4 -> targets[band]
                else -> .84 * targets[band] + (if (band > 5) .16 * targets[band - 1] else 0.0) +
                    (if (band < 9) .16 * targets[band + 1] else 0.0)
            }
        }
        val profile = profile(settings.curve)
        for (band in 0..9) {
            if (AdaptiveAudioSettings.FREQUENCIES[band] >= sampleRate * .45) {
                gains[band] = 0.0
                continue
            }
            val lower = lowerBound(band, strength)
            val upperBound = upperBound(band, strength)
            val base = shaped[band].coerceIn(lower, upperBound)
            val shape = profile[band]
            val support = when {
                band <= 2 -> bassGate * warmup * (1 - .60 * sustainedBass)
                band <= 4 -> lowPresence * confidence[band]
                band <= 7 -> (.22 + .60 * presenceAmount * confidence[band]).coerceIn(0.0, 1.0)
                else -> .26 * lackTreble * confidence[band]
            }
            val room = if (shape >= 0) max(0.0, upperBound - base) else max(0.0, base - lower)
            var target = (base + sign(shape) * room * (.28 + .72 * sqrt(abs(shape) / 1.48)) * .56 * support)
                .coerceIn(lower, upperBound)
            if (band <= 2) target = min(target, upperBound * (.03 + .97 * bassGate * warmup * (1 - .60 * sustainedBass)))
            // Spectral jumps slow adaptation so an isolated hit cannot repaint the tonal balance.
            val hold = smooth(.22, .78, transient)
            target += (gains[band] - target) * hold * if (band <= 4) .74 else .58
            val maxStep = (.88 + .12 * strength) * (1.4 - 1.16 * hold) * if (band <= 4) .82 else .9
            target = target.coerceIn(gains[band] - maxStep, gains[band] + maxStep)
            val rising = target > gains[band]
            val response = when {
                band < 5 -> if (rising) .28 else .14
                band < 8 -> if (rising) .38 else .19
                else -> if (rising) .46 else .25
            }
            val amount = (response * SPEED_RESPONSE[speed] * (1 - .46 * smooth(.24, .84, transient))).coerceIn(.08, .5)
            gains[band] = (gains[band] + amount * (target - gains[band])).coerceIn(-8.0, 8.0)
        }
    }

    private fun historyIndex(age: Int) = (cursor - 1 - age + MAX_BLOCKS) % MAX_BLOCKS
    private fun lowerBound(band: Int, strength: Double): Double = when (band) {
        5 -> -2.8
        6 -> -2.15
        7 -> -1.65
        else -> max(-8.0, LOWER[band] * if (band < 5) strength else .72 + .28 * strength)
    }
    private fun upperBound(band: Int, strength: Double): Double = when (band) {
        5 -> .45
        6, 7 -> 3.2
        else -> min(8.0, UPPER[band] * strength)
    }
    private fun profile(curve: DynamicEqCurve) = when (curve) {
        DynamicEqCurve.WARM -> WARM
        DynamicEqCurve.V_SHAPE -> V_SHAPE
        DynamicEqCurve.VOCAL -> VOCAL
        DynamicEqCurve.BRIGHT -> BRIGHT
    }

    companion object {
        private const val BLOCK_SECONDS = .2
        private const val MAX_BLOCKS = 40
        private const val BASS_HISTORY_BLOCKS = 25
        private const val SILENCE_RMS = .0012
        private val CROSSOVERS = doubleArrayOf(45.0, 90.0, 160.0, 320.0, 640.0, 1400.0, 2800.0, 5600.0, 11200.0)
        private val WINDOW_SECONDS = doubleArrayOf(8.0, 5.0, 4.3, 3.2, 1.8)
        private val SPEED_RESPONSE = doubleArrayOf(.75, 1.0, 1.15, 1.35, 1.65)
        private val STRENGTHS = doubleArrayOf(.3, 1.0, 1.25, 2.3, 3.6)
        private val RMS_THRESHOLDS = doubleArrayOf(-42.0, -38.0, -34.0, -36.0, -40.0, -42.0, -43.0, -44.0, -47.0, -52.0)
        private val RELATIVE_THRESHOLDS = doubleArrayOf(-36.0, -31.0, -28.0, -30.0, -35.0, -35.0, -37.0, -39.0, -42.0, -48.0)
        private val PEAK_THRESHOLDS = doubleArrayOf(-29.0, -25.0, -22.0, -24.0, -29.0, -31.0, -33.0, -35.0, -38.0, -44.0)
        private val LOWER = doubleArrayOf(0.0, 0.0, 0.0, -1.4, -2.4, -2.8, -2.15, -1.65, -.9, -1.0)
        private val UPPER = doubleArrayOf(2.8, 4.2, 5.2, 2.2, .45, .45, 3.2, 3.2, 5.5, 5.5)
        private val WARM = doubleArrayOf(1.45, 1.32, 1.08, .72, .36, -.62, -1.02, -1.16, -.72, -.28)
        private val V_SHAPE = doubleArrayOf(1.18, 1.32, .92, .34, -.42, -.86, -.46, .38, .96, 1.22)
        private val VOCAL = doubleArrayOf(-.82, -.70, -.44, .12, .58, 1.30, 1.48, .92, .34, -.18)
        private val BRIGHT = doubleArrayOf(-1.12, -.94, -.78, -.48, -.18, .28, 1.02, 1.46, 1.34, .94)
        private fun sum(values: DoubleArray, first: Int, last: Int): Double {
            var result = 0.0
            for (index in first..last) result += values[index]
            return result
        }
        private fun amplitudeDb(value: Double) = 20 * log10(max(value, 1e-12))
        private fun powerDb(power: Double, reference: Double) = 10 * log10((power + 1e-12) / (reference + 1e-12))
        private fun smooth(low: Double, high: Double, value: Double): Double {
            val position = ((value - low) / (high - low)).coerceIn(0.0, 1.0)
            return position * position * (3 - 2 * position)
        }
    }
}
