package io.github.currencortex.music.feature.lyrics.domain

import io.github.currencortex.music.feature.lyrics.model.*
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** Corresponding real lyric onsets estimate timing only; they never decide candidate eligibility. */
object LyricsTimingAligner {
    private data class Row(val text: String, val time: Long)
    private data class Pairing(val source: Long, val target: Long, val length: Int)

    fun align(reference: LyricsDocument, candidate: LyricsDocument): LyricsTimingAlignment? {
        fun rows(document: LyricsDocument): List<Row>? {
            if (document.lines.size > 2048 || document.lines.sumOf { it.text.length.toLong() } > 256_000) return null
            return document.lines.asSequence().filter { it.startTimeMs in 0..86_400_000 && it.endTimeMs > it.startTimeMs }
                .map { Row(normalize(it.text), it.startTimeMs) }.filter { it.text.length >= 3 }.toList()
        }
        val target = rows(reference) ?: return null
        val source = rows(candidate) ?: return null
        if (minOf(source.size, target.size) < 6) return null
        val stride = target.size + 1
        val cells = (source.size + 1L) * stride
        if (cells > 4_000_000) return null
        // Ordered LCS disambiguates repeated choruses; punctuation and line whitespace do not matter.
        val trace = ByteArray(cells.toInt())
        var previous = IntArray(stride)
        var current = IntArray(stride)
        for (i in 1..source.size) {
            for (j in 1..target.size) {
                val at = i * stride + j
                when {
                    source[i - 1].text == target[j - 1].text -> {
                        current[j] = previous[j - 1] + source[i - 1].text.length
                        trace[at] = 1
                    }
                    previous[j] >= current[j - 1] -> { current[j] = previous[j]; trace[at] = 2 }
                    else -> { current[j] = current[j - 1]; trace[at] = 3 }
                }
            }
            val swap = previous; previous = current; current = swap; current.fill(0)
        }
        val pairs = mutableListOf<Pairing>()
        var i = source.size
        var j = target.size
        while (i > 0 && j > 0) when (trace[i * stride + j].toInt()) {
            1 -> { pairs += Pairing(source[i - 1].time, target[j - 1].time, source[i - 1].text.length); i--; j-- }
            2 -> i--
            else -> j--
        }
        pairs.reverse()
        val coverage = pairs.sumOf { it.length }.toDouble() /
            max(source.sumOf { it.text.length }, target.sumOf { it.text.length })
        if (coverage < .65 || pairs.size < 6) return null
        // Simultaneous/ambiguous lines cannot define a single-valued clock map.
        val ordered = mutableListOf<Pairing>()
        for (pair in pairs) if (ordered.isEmpty() ||
            (pair.source > ordered.last().source && pair.target > ordered.last().target)) ordered += pair
        if (ordered.size < 6 || ordered.any { abs(it.target - it.source) > 60_000 }) return null
        val sourceSpan = ordered.last().source - ordered.first().source
        val targetSpan = ordered.last().target - ordered.first().target
        if (minOf(sourceSpan, targetSpan) < 20_000 ||
            sourceSpan < (source.last().time - source.first().time) * .7 ||
            targetSpan < (target.last().time - target.first().time) * .7) return null
        val sampled = if (ordered.size <= 96) ordered else (0 until 96).map { ordered[it * ordered.lastIndex / 95] }
        val slopes = mutableListOf<Double>()
        for (a in sampled.indices) for (b in a + 1 until sampled.size) {
            val span = sampled[b].source - sampled[a].source
            if (span >= 10_000) slopes += (sampled[b].target - sampled[a].target).toDouble() / span
        }
        if (slopes.isEmpty()) return null
        val slope = median(slopes)
        if (slope !in .8..1.25) return null
        val offset = median(ordered.map { it.target - slope * it.source })
        val residualLimit = max(1200.0, targetSpan * .01)
        val inliers = ordered.filter { abs(it.target - slope * it.source - offset) <= residualLimit }
        if (inliers.size < 6 || inliers.size < ordered.size * .85) return null
        // Do not use a bad coarse line timestamp to create a sudden local tempo jump.
        val edges = inliers.zipWithNext().filter { (a, b) -> b.source - a.source >= 500 }
        if (edges.isEmpty() || edges.count { (a, b) ->
            (b.target - a.target).toDouble() / (b.source - a.source) in .5..1.5
        } < edges.size * .9) return null
        val offsets = inliers.map { it.target - it.source }
        if (offsets.all { abs(it) < 80 }) return null
        val anchors = if (offsets.max() - offsets.min() <= 250) {
            val at = inliers.first().source
            val shift = median(offsets.map(Long::toDouble)).toLong()
            if (at + shift < 0) return null
            listOf(LyricsTimeAnchor(at, at + shift))
        } else {
            val points = if (inliers.size <= 128) inliers else
                (0 until 128).map { inliers[it * inliers.lastIndex / 127] }
            points.map { LyricsTimeAnchor(it.source, it.target) }
        }
        return LyricsTimingAlignment(anchors, inliers.size, coverage * inliers.size / ordered.size)
    }

    private fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
    }
}
