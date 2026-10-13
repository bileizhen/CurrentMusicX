package io.github.currencortex.music.core.media

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

internal data class MusicEnergyProfile(val rms: Float, val tailRatio: Float = 1f,
    val leadingSilenceMs: Long = 0, val beatPeriodMs: Long? = null)

/** Bounded numeric summaries of decoded music; never retains audio samples. */
internal class TrackEnergyProbe {
    @Volatile var enabled = false
    private val samples = ArrayDeque<Pair<Float, Float>>()
    @Synchronized fun reset() { samples.clear() }
    @Synchronized fun publish(rms: Float, bass: Float) {
        if (!enabled || !rms.isFinite() || !bass.isFinite()) return
        samples.addLast(rms.coerceIn(0f, 1f) to bass.coerceIn(0f, 1f))
        if (samples.size > 250) samples.removeFirst()
    }
    @Synchronized fun profile(): MusicEnergyProfile? {
        if (samples.size < 8) return null
        val values = samples.toList()
        fun average(part: List<Pair<Float, Float>>) = part.map { it.first.toDouble() }.average().toFloat()
        val split = values.size / 2
        val first = average(values.take(split)); val last = average(values.drop(split))
        val onset = mutableListOf<Int>()
        for (i in 5 until values.size) {
            val energy = values[i].first + values[i].second
            val baseline = values.subList(i - 5, i).map { it.first + it.second }.average()
            if (energy > maxOf(.015, baseline * 1.5) && (onset.isEmpty() || i - onset.last() >= 5)) onset += i
        }
        val intervals = onset.zipWithNext { a, b -> (b - a) * 40L }.filter { it in 280..1000 }
        val period = intervals.sorted().getOrNull(intervals.size / 2)?.takeIf { median ->
            intervals.size >= 3 && intervals.count { kotlin.math.abs(it - median) <= median * .18 } >= intervals.size * .75
        }
        val firstAudible = values.indexOfFirst { it.first > .001f }
        return MusicEnergyProfile(average(values), if (first > .001f) last / first else 1f,
            if (firstAudible >= 0) firstAudible * 40L else 0, period)
    }
}

internal data class MusicTransitionPlan(val durationMs: Long, val incomingStartMs: Long = 0,
    val incomingGain: Float = 1f)

internal fun smartMusicTransition(outgoing: MusicEnergyProfile?, incoming: MusicEnergyProfile?): MusicTransitionPlan {
    if (outgoing == null || incoming == null) return MusicTransitionPlan(5000)
    var duration = when {
        outgoing.tailRatio < .5f -> 7500L
        outgoing.rms > .16f && incoming.rms > .16f -> 2800L
        incoming.rms < .04f || outgoing.rms < .04f -> 6500L
        else -> 4800L
    }
    outgoing.beatPeriodMs?.let { beat -> duration = ((duration.toDouble() / beat).roundToLong() * beat).coerceIn(2500, 8000) }
    val gain = if (incoming.rms > outgoing.rms && outgoing.rms > .01f)
        sqrt(outgoing.rms / incoming.rms).coerceIn(.7f, 1f) else 1f
    return MusicTransitionPlan(duration, incoming.leadingSilenceMs.coerceIn(0, 1500), gain)
}

/** Equal-power overlap; both gains meet the ordinary volume continuously at the endpoints. */
internal fun musicTransitionGains(progress: Float, incomingGain: Float = 1f): Pair<Float, Float> {
    val p = progress.coerceIn(0f, 1f)
    val correction = incomingGain + (1f - incomingGain) * p
    return cos(p * PI / 2).toFloat().coerceAtLeast(0f) to (sin(p * PI / 2).toFloat() * correction)
}
