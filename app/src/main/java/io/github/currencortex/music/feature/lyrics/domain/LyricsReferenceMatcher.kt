package io.github.currencortex.music.feature.lyrics.domain

import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricWord
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class LyricsReferenceScore(val accepted: Boolean, val similarity: Double,
    val timingSimilarity: Double, val quality: Double, val total: Double)

/**
 * Compare primary lyric content and its recording against an authoritative reference. Source labels
 * have no influence: a document wins through complete, ordered text and genuine useful timing.
 * Normalization and temporary rendering/quality calculations never modify either document.
 */
object LyricsReferenceMatcher {
    private const val MIN_SIMILARITY = .97
    private const val MIN_LENGTH_RATIO = .97
    private const val MAX_POINTS = 24_000
    private const val MAX_RAW_CHARS = 256_000
    private const val MAX_LINES = 4096
    private const val MAX_WORDS = 16_384
    private const val MAX_EDIT_CELLS = 4_000_000L
    private const val MAX_EDIT_DISTANCE = 512
    private const val MAX_ANCHORS = 96
    private const val MAX_TIME_MS = 86_400_000L
    private const val MAX_FIXED_OFFSET_MS = 1000.0
    private const val MAX_CONSECUTIVE_GAP = 3
    private val credit = Regex("^(?:作词|作曲|编曲|制作)(?:人)?\\s*[:：]\\s*\\S.*$")

    fun compare(reference: LyricsDocument, candidate: LyricsDocument): LyricsReferenceScore {
        val baseline = index(reference) ?: return rejected()
        val other = index(candidate) ?: return rejected()
        if (baseline.points.isEmpty() || other.points.isEmpty()) return rejected(quality = other.quality)
        val maximum = max(baseline.points.size, other.points.size)
        val lengthRatio = min(baseline.points.size, other.points.size).toDouble() / maximum
        if (lengthRatio < MIN_LENGTH_RATIO) return rejected(quality = other.quality)
        val sameText = baseline.text == other.text
        val edit = if (sameText) Edit(0, 0) else editDistance(baseline.points, other.points)
            ?: return rejected(quality = other.quality)
        val similarity = 1.0 - edit.distance.toDouble() / maximum
        // A typo in a very short chorus can be most of its identity. Do not guess in that case.
        if (similarity < MIN_SIMILARITY || (maximum <= 12 && !sameText) ||
            edit.maximumGap > MAX_CONSECUTIVE_GAP || !sameBoundaries(baseline.points, other.points))
            return rejected(similarity, quality = other.quality)
        val intervals = compareCueIntervals(baseline, other, sameText)
        val quality = (other.quality - other.wordQuality * (1 - intervals.mappedWordRatio)).coerceIn(0.0, 1.0)
        val timing = if (intervals.accepted) compareTiming(baseline, other, sameText) else Timing(false, 0.0)
        val accepted = timing.accepted && baseline.validLineRatio >= .95 && other.validLineRatio >= .95
        val total = if (accepted) similarity * .45 + timing.score * .15 + quality * .40 else 0.0
        return LyricsReferenceScore(accepted, similarity, timing.score, quality, total.coerceIn(0.0, 1.0))
    }

    private data class WordCue(val offset: Int, val word: LyricWord, val text: String)
    private data class TextRow(val source: LyricLine, val text: String, val offset: Int,
        val words: List<WordCue>, val valid: Boolean)
    private data class Indexed(val text: String, val points: IntArray, val rows: List<TextRow>,
        val cues: Map<Int, Double>, val validLineRatio: Double, val quality: Double,
        val wordQuality: Double, val durationMs: Long?)
    private data class Anchor(val reference: Double, val candidate: Double)
    private data class Timing(val accepted: Boolean, val score: Double)
    private data class Edit(val distance: Int, val maximumGap: Int)
    private data class CueIntervals(val accepted: Boolean, val mappedWordRatio: Double)

    private fun rejected(similarity: Double = 0.0, timing: Double = 0.0, quality: Double = 0.0) =
        LyricsReferenceScore(false, similarity, timing, quality, 0.0)

    private fun index(document: LyricsDocument): Indexed? {
        if (document.lines.size > MAX_LINES) return null
        var rawChars = 0L
        var wordCount = 0L
        document.lines.forEach {
            rawChars += it.text.length
            wordCount += it.words.size
            if (rawChars > MAX_RAW_CHARS || wordCount > MAX_WORDS) return null
        }
        val text = StringBuilder()
        val rows = mutableListOf<TextRow>()
        val cues = linkedMapOf<Int, Double>()
        var points = 0
        var leading = true
        var removedCredits = 0
        var totalWords = 0
        var validWords = 0
        var timedPoints = 0
        var translated = 0
        var romanized = 0
        for (line in document.lines) {
            val visible = line.text.trim()
            // Only a clearly labelled leading credit block is metadata. Keep all later/main
            // text, including bilingual singing; language alone never marks a translation.
            if (leading && removedCredits < 32 && credit.matches(visible)) {
                removedCredits++
                continue
            }
            val normalized = normalize(line.text)
            if (normalized.isEmpty()) continue
            leading = false
            val count = normalized.codePointCount(0, normalized.length)
            points += count
            if (points > MAX_POINTS) return null
            val valid = line.startTimeMs >= 0 && line.endTimeMs > line.startTimeMs && line.endTimeMs <= MAX_TIME_MS
            val words = if (valid && !line.wordsEstimated) wordCues(line, normalized) else emptyList()
            val row = TextRow(line, normalized, text.length, words, valid)
            rows += row
            if (valid) cues[row.offset] = line.startTimeMs.toDouble()
            words.forEach { cues[row.offset + it.offset] = it.word.startTimeMs.toDouble() }
            text.append(normalized)
            totalWords += line.words.size
            validWords += words.size
            timedPoints += words.sumOf { it.text.codePointCount(0, it.text.length) }.coerceAtMost(count)
            if (line.translation.take(8192).isNotBlank() || line.translations.take(16).any { it.text.take(8192).isNotBlank() }) translated++
            if (line.romanization.take(8192).isNotBlank() || line.romanizations.take(16).any { it.text.take(8192).isNotBlank() }) romanized++
        }
        if (rows.isEmpty() || text.isEmpty()) return null
        val lineRatio = rows.count { it.valid }.toDouble() / rows.size
        val coverage = (timedPoints.toDouble() / points.coerceAtLeast(1)).coerceIn(0.0, 1.0)
        val wordValidity = if (totalWords == 0) 0.0 else validWords.toDouble() / totalWords
        val wordQuality = coverage * .55 + wordValidity * .10
        val quality = lineRatio * .25 + wordQuality +
            translated.toDouble() / rows.size * .07 + romanized.toDouble() / rows.size * .03
        val normalized = text.toString()
        return Indexed(normalized, normalized.codePoints().toArray(), rows, cues.toSortedMap(),
            lineRatio, quality.coerceIn(0.0, 1.0), wordQuality, document.metadata.durationMs?.takeIf { it in 1..MAX_TIME_MS })
    }

    private fun wordCues(line: LyricLine, normalized: String): List<WordCue> {
        val result = mutableListOf<WordCue>()
        var rawCursor = 0
        var normalizedCursor = 0
        var previousTime = Long.MIN_VALUE
        for (word in line.words.sortedBy { it.startOffset }) {
            if (word.startOffset < rawCursor || word.endOffset <= word.startOffset || word.endOffset > line.text.length ||
                word.text.length != word.endOffset - word.startOffset ||
                !line.text.regionMatches(word.startOffset, word.text, 0, word.text.length) ||
                word.startTimeMs < line.startTimeMs || word.endTimeMs <= word.startTimeMs ||
                word.endTimeMs > line.endTimeMs || word.startTimeMs < previousTime) continue
            val start = normalizedCursor + normalize(line.text.substring(rawCursor, word.startOffset)).length
            val token = normalize(word.text)
            if (token.isNotEmpty() && start + token.length <= normalized.length && normalized.regionMatches(start, token, 0, token.length)) {
                result += WordCue(start, word, token)
                previousTime = word.startTimeMs
            }
            rawCursor = word.endOffset
            normalizedCursor = start + token.length
        }
        return result
    }

    private fun normalize(source: String): String {
        val folded = Normalizer.normalize(source, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        val result = StringBuilder(folded.length)
        var index = 0
        while (index < folded.length) {
            val point = folded.codePointAt(index)
            index += Character.charCount(point)
            val type = Character.getType(point)
            if (Character.isWhitespace(point) || Character.isSpaceChar(point) || type == Character.CONTROL.toInt() ||
                type == Character.CONNECTOR_PUNCTUATION.toInt() || type == Character.DASH_PUNCTUATION.toInt() ||
                type == Character.START_PUNCTUATION.toInt() || type == Character.END_PUNCTUATION.toInt() ||
                type == Character.INITIAL_QUOTE_PUNCTUATION.toInt() || type == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
                type == Character.OTHER_PUNCTUATION.toInt()) continue
            result.appendCodePoint(point)
        }
        return result.toString()
    }

    /** Two bounded rows and a bounded edit band, never an N-by-M text matrix. */
    private fun editDistance(first: IntArray, second: IntArray): Edit? {
        val a = if (first.size >= second.size) first else second
        val b = if (first.size >= second.size) second else first
        val cellBand = ((MAX_EDIT_CELLS / a.size.coerceAtLeast(1) - 1) / 2).coerceAtLeast(0).toInt()
        val limit = minOf(floor(a.size * (1 - MIN_SIMILARITY) + .00001).toInt(), MAX_EDIT_DISTANCE, cellBand)
        if (a.size - b.size > limit || limit == 0) return null
        val infinity = limit + 1
        var previous = IntArray(b.size + 1) { if (it <= limit) it else infinity }
        var current = IntArray(b.size + 1) { infinity }
        var previousDeletion = IntArray(b.size + 1)
        var currentDeletion = IntArray(b.size + 1)
        var previousInsertion = IntArray(b.size + 1) { it }
        var currentInsertion = IntArray(b.size + 1)
        var previousGap = IntArray(b.size + 1) { it }
        var currentGap = IntArray(b.size + 1)
        for (i in 1..a.size) {
            val from = max(1, i - limit)
            val until = min(b.size, i + limit)
            current[0] = if (i <= limit) i else infinity
            currentDeletion[0] = i
            currentInsertion[0] = 0
            currentGap[0] = i
            if (from > 1) current[from - 1] = infinity
            var minimum = infinity
            for (j in from..until) {
                val deletion = previous[j] + 1
                val insertion = current[j - 1] + 1
                val diagonal = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                when {
                    diagonal <= deletion && diagonal <= insertion -> {
                        current[j] = diagonal
                        currentDeletion[j] = 0; currentInsertion[j] = 0
                        currentGap[j] = previousGap[j - 1]
                    }
                    deletion <= insertion -> {
                        current[j] = deletion
                        currentDeletion[j] = previousDeletion[j] + 1; currentInsertion[j] = 0
                        currentGap[j] = max(previousGap[j], currentDeletion[j])
                    }
                    else -> {
                        current[j] = insertion
                        currentDeletion[j] = 0; currentInsertion[j] = currentInsertion[j - 1] + 1
                        currentGap[j] = max(currentGap[j - 1], currentInsertion[j])
                    }
                }
                minimum = min(minimum, current[j])
            }
            if (until < b.size) current[until + 1] = infinity
            if (minimum > limit) return null
            val swap = previous; previous = current; current = swap
            val deletionSwap = previousDeletion; previousDeletion = currentDeletion; currentDeletion = deletionSwap
            val insertionSwap = previousInsertion; previousInsertion = currentInsertion; currentInsertion = insertionSwap
            val gapSwap = previousGap; previousGap = currentGap; currentGap = gapSwap
        }
        return if (previous[b.size] <= limit) Edit(previous[b.size], previousGap[b.size]) else null
    }

    private fun sameBoundaries(reference: IntArray, candidate: IntArray): Boolean {
        val count = minOf(12, reference.size, candidate.size)
        return (0 until count).all { reference[it] == candidate[it] &&
            reference[reference.lastIndex - it] == candidate[candidate.lastIndex - it] }
    }

    /**
     * A word boundary inside a differently split reference line has no exact reference timestamp,
     * but it does have a genuine containing [line start, line end] interval. Require its actual
     * cue to stay inside that interval; never manufacture line-internal proportional timestamps.
     */
    private fun compareCueIntervals(reference: Indexed, candidate: Indexed, sameText: Boolean): CueIntervals {
        var lineCursor = 0
        var wordCursor = 0
        var words = 0
        var mapped = 0
        for (row in candidate.rows) {
            if (!row.valid) continue
            val lineOffset = if (sameText) row.offset else reference.text.indexOf(row.text, lineCursor)
            if (lineOffset >= 0) {
                if (!withinReferenceInterval(reference, lineOffset, row.source.startTimeMs) ||
                    !withinReferenceInterval(reference, lineOffset + row.text.length - 1, row.source.endTimeMs))
                    return CueIntervals(false, 0.0)
                lineCursor = lineOffset + row.text.length
            }
            for (cue in row.words) {
                val points = cue.text.codePointCount(0, cue.text.length)
                words += points
                val offset = if (sameText) row.offset + cue.offset else reference.text.indexOf(cue.text, wordCursor)
                if (offset < 0) continue
                if (!withinReferenceInterval(reference, offset, cue.word.startTimeMs) ||
                    !withinReferenceInterval(reference, offset + cue.text.length - 1, cue.word.endTimeMs))
                    return CueIntervals(false, 0.0)
                wordCursor = offset + cue.text.length
                mapped += points
            }
        }
        return CueIntervals(true, if (words == 0) 0.0 else mapped.toDouble() / words)
    }

    private fun withinReferenceInterval(reference: Indexed, character: Int, time: Long): Boolean {
        var low = 0
        var high = reference.rows.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (reference.rows[mid].offset <= character) low = mid + 1 else high = mid - 1
        }
        val row = reference.rows.getOrNull(high) ?: return false
        return row.valid && character < row.offset + row.text.length &&
            time.toDouble() >= row.source.startTimeMs - MAX_FIXED_OFFSET_MS &&
            time.toDouble() <= row.source.endTimeMs + MAX_FIXED_OFFSET_MS
    }

    private fun compareTiming(reference: Indexed, candidate: Indexed, sameText: Boolean): Timing {
        if (reference.cues.isEmpty() || candidate.cues.isEmpty()) return Timing(false, 0.0)
        val positions = reference.cues.keys.toList()
        val selected = if (positions.size <= MAX_ANCHORS) positions else
            (0 until MAX_ANCHORS).map { positions[(it.toDouble() * positions.lastIndex / (MAX_ANCHORS - 1)).roundToInt()] }.distinct()
        val anchors = mutableListOf<Anchor>()
        var cursor = 0
        var contentAnchors = 0
        var foundContentAnchors = 0
        for (position in selected) {
            val found = if (sameText) position else {
                val row = reference.rows.lastOrNull { it.offset <= position && position < it.offset + it.text.length } ?: continue
                val local = position - row.offset
                val remaining = row.text.codePointCount(local, row.text.length)
                if (remaining < 1) continue
                val end = row.text.offsetByCodePoints(local, min(16, remaining))
                val token = row.text.substring(local, end)
                contentAnchors++
                val occurrence = candidate.text.indexOf(token, cursor)
                // A known fragment that only occurs earlier cannot be silently skipped: that
                // would let shuffled paragraphs or a missing repeated verse pass on other cues.
                if (occurrence < 0 && candidate.text.indexOf(token) >= 0) return Timing(false, 0.0)
                if (occurrence >= 0) foundContentAnchors++
                occurrence
            }
            if (found < 0) continue
            cursor = found + Character.charCount(candidate.text.codePointAt(found))
            // A genuine line/word start on both sides is required. Never treat a line-internal
            // proportional estimate as evidence that a recording has a different tempo.
            val time = candidate.cues[found] ?: continue
            anchors += Anchor(reference.cues.getValue(position), time)
        }
        if (!sameText && contentAnchors > 0 && foundContentAnchors.toDouble() / contentAnchors < .95)
            return Timing(false, 0.0)
        if (anchors.isEmpty()) {
            // With no shared token boundary, only the first actual cue and broad media bounds
            // are comparable. Do not invent syllable/word timestamps to manufacture anchors.
            anchors += Anchor(reference.cues.values.first(), candidate.cues.values.first())
        }
        val offsets = anchors.map { it.candidate - it.reference }.sorted()
        val offset = percentile(offsets, .5)
        val referenceSpan = (reference.rows.last { it.valid }.source.endTimeMs - reference.rows.first { it.valid }.source.startTimeMs).toDouble().coerceAtLeast(1.0)
        val residual = percentile(anchors.map { abs(it.candidate - it.reference - offset) }.sorted(), .9)
        val residualLimit = max(1200.0, referenceSpan * .015)
        val reliableSpan = anchors.last().reference - anchors.first().reference
        val scale = if (anchors.size >= 3 && reliableSpan >= 20_000)
            (anchors.last().candidate - anchors.first().candidate) / reliableSpan else 1.0
        val durationMismatch = reference.durationMs != null && candidate.durationMs != null &&
            abs(reference.durationMs.toDouble() - candidate.durationMs) > max(5000.0, reference.durationMs * .04)
        val startResidual = abs(candidate.rows.first { it.valid }.source.startTimeMs.toDouble() -
            reference.rows.first { it.valid }.source.startTimeMs - offset)
        val endResidual = abs(candidate.rows.last { it.valid }.source.endTimeMs.toDouble() -
            reference.rows.last { it.valid }.source.endTimeMs - offset)
        val accepted = abs(offset) <= MAX_FIXED_OFFSET_MS && residual <= residualLimit &&
            scale in .94..1.06 && !durationMismatch && startResidual <= max(2000.0, referenceSpan * .025) &&
            endResidual <= max(6000.0, referenceSpan * .08)
        val offsetScore = (1 - abs(offset) / 2000.0).coerceIn(0.0, 1.0)
        val residualScore = (1 - residual / (residualLimit * 2)).coerceIn(0.0, 1.0)
        val tempoScore = (1 - abs(scale - 1) / .10).coerceIn(0.0, 1.0)
        return Timing(accepted, (offsetScore * .45 + residualScore * .40 + tempoScore * .15).coerceIn(0.0, 1.0))
    }

    private fun percentile(sorted: List<Double>, fraction: Double): Double {
        val index = sorted.lastIndex * fraction
        val lower = floor(index).toInt()
        val upper = min(lower + 1, sorted.lastIndex)
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (index - lower)
    }
}
