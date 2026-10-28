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
    val timingSimilarity: Double, val quality: Double, val total: Double,
    val hasWordTiming: Boolean = false)

/**
 * Compare primary lyric content and its recording against an authoritative reference. Source labels
 * have no influence: a document wins through complete, ordered text and genuine useful timing.
 * Normalization and temporary rendering/quality calculations never modify either document.
 */
object LyricsReferenceMatcher {
    private const val MIN_LINE_SIMILARITY = .97
    const val MIN_WORD_SIMILARITY = .75
    private const val MAX_POINTS = 24_000
    private const val MAX_RAW_CHARS = 256_000
    private const val MAX_LINES = 4096
    private const val MAX_WORDS = 16_384
    private const val MAX_EDIT_CELLS = 4_000_000L
    private const val MAX_ANCHORS = 96
    private const val MAX_TIME_MS = 86_400_000L
    private const val MAX_FIXED_OFFSET_MS = 1000.0
    private const val MAX_COARSE_ENDPOINT_ERROR_MS = 3000.0
    private const val MAX_CONSECUTIVE_GAP = 3
    private val credit = Regex("^(?:作词|作曲|编曲|制作)(?:人)?\\s*[:：]\\s*\\S.*$")
    private val trailingCredit = Regex("^(?:作词|作曲|编曲|制作|制作人|吉他|贝斯|和声编写|和声|录音工程师|录音工程室|混音工程师|混音工程室|制作助理|OP|SP)\\s*[:：]\\s*\\S.*$")

    fun compare(reference: LyricsDocument, candidate: LyricsDocument): LyricsReferenceScore {
        val baseline = index(reference) ?: return rejected()
        val other = index(candidate) ?: return rejected()
        val hasWords = other.hasWordTiming
        fun failure(similarity: Double = 0.0) = rejected(similarity, quality = other.quality, hasWordTiming = hasWords)
        if (baseline.points.isEmpty() || other.points.isEmpty()) return failure()
        val threshold = if (hasWords) MIN_WORD_SIMILARITY else MIN_LINE_SIMILARITY
        val maximum = max(baseline.points.size, other.points.size)
        val lengthRatio = min(baseline.points.size, other.points.size).toDouble() / maximum
        if (lengthRatio < threshold) return failure()
        val sameText = baseline.text == other.text
        val edit = if (sameText) Edit(0, 0, IntArray(other.points.size) { it })
            else editDistance(baseline.points, other.points, threshold) ?: return failure()
        val similarity = 1.0 - edit.distance.toDouble() / maximum
        // Real word cues use the user's inclusive 75% content policy. A plain short chorus
        // still needs exact content. Completeness and order are checked separately from this
        // threshold, so a removed verse cannot win by adding word cues to the remainder.
        if (similarity < threshold || (!hasWords && maximum <= 12 && !sameText) ||
            edit.maximumGap > MAX_CONSECUTIVE_GAP || !orderedContent(baseline, other) ||
            !preservesSections(baseline, other, edit.candidateToReference)) return failure(similarity)
        val mapping = characterMapping(baseline, other, edit.candidateToReference)
        val intervals = compareCueIntervals(baseline, other, mapping)
        val quality = (other.quality - other.wordQuality * (1 - intervals.mappedWordRatio)).coerceIn(0.0, 1.0)
        val timing = if (intervals.accepted) compareTiming(baseline, other, mapping) else Timing(false, 0.0)
        val accepted = timing.accepted && baseline.validLineRatio >= .95 && other.validLineRatio >= .95
        val total = if (accepted) similarity * .45 + timing.score * .15 + quality * .40 else 0.0
        return LyricsReferenceScore(accepted, similarity, timing.score, quality, total.coerceIn(0.0, 1.0), hasWords)
    }

    private data class WordCue(val offset: Int, val word: LyricWord, val text: String)
    private data class TextRow(val source: LyricLine, val text: String, val offset: Int,
        val words: List<WordCue>, val valid: Boolean)
    private data class Indexed(val text: String, val points: IntArray, val rows: List<TextRow>,
        val cues: Map<Int, Double>, val validLineRatio: Double, val quality: Double,
        val wordQuality: Double, val durationMs: Long?, val hasWordTiming: Boolean)
    private data class Anchor(val reference: Double, val candidate: Double)
    private data class Timing(val accepted: Boolean, val score: Double)
    private data class Edit(val distance: Int, val maximumGap: Int, val candidateToReference: IntArray)
    private data class CueIntervals(val accepted: Boolean, val mappedWordRatio: Double)

    private fun rejected(similarity: Double = 0.0, timing: Double = 0.0, quality: Double = 0.0,
        hasWordTiming: Boolean = false) = LyricsReferenceScore(false, similarity, timing, quality, 0.0, hasWordTiming)

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
        // A bounded, contiguous block of explicitly labelled production credits after
        // the vocals is metadata too. A single line or anything inside the song stays.
        val trailing = document.lines.asReversed().dropWhile { it.text.isBlank() }
            .takeWhile { trailingCredit.matches(it.text.trim()) }.size
        val trailingStart = if (trailing in 2..32) document.lines.indexOfLast { it.text.isNotBlank() } - trailing + 1
            else document.lines.size
        for ((lineIndex, line) in document.lines.withIndex()) {
            if (lineIndex >= trailingStart) continue
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
            lineRatio, quality.coerceIn(0.0, 1.0), wordQuality,
            document.metadata.durationMs?.takeIf { it in 1..MAX_TIME_MS }, validWords > 0)
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

    /** Two edit rows plus at most four million one-byte traceback cells; no full text matrix. */
    private fun editDistance(a: IntArray, b: IntArray, threshold: Double): Edit? {
        val allowed = floor(max(a.size, b.size) * (1 - threshold) + .00001).toInt()
        if (abs(a.size - b.size) > allowed || allowed == 0) return null
        // Dispersed replacements need no wide edit band, even for a long song with more than
        // 512 differences. Preserve their positions instead of globally searching repeated
        // single characters when checking the recording's genuine cue intervals.
        if (a.size == b.size) {
            val replacements = a.indices.count { a[it] != b[it] }
            if (replacements <= allowed) return Edit(replacements, 0, IntArray(b.size) { it })
        }
        val cellBand = ((MAX_EDIT_CELLS / a.size.coerceAtLeast(1) - 1) / 2).coerceAtLeast(0).toInt()
        val limit = min(allowed, cellBand)
        if (abs(a.size - b.size) > limit || limit == 0) return null
        val infinity = limit + 1
        var previous = IntArray(b.size + 1) { if (it <= limit) it else infinity }
        var current = IntArray(b.size + 1) { infinity }
        val starts = IntArray(a.size + 1)
        val trace = arrayOfNulls<ByteArray>(a.size + 1)
        trace[0] = ByteArray(min(b.size, limit) + 1) { 3 }
        var cells = trace[0]!!.size.toLong()
        for (i in 1..a.size) {
            val from = max(1, i - limit)
            val until = min(b.size, i + limit)
            starts[i] = max(0, i - limit)
            cells += until - starts[i] + 1
            if (cells > MAX_EDIT_CELLS) return null
            val directions = ByteArray(until - starts[i] + 1)
            trace[i] = directions
            current[0] = if (i <= limit) i else infinity
            if (starts[i] == 0) directions[0] = 2
            if (from > 1) current[from - 1] = infinity
            var minimum = infinity
            for (j in from..until) {
                val deletion = previous[j] + 1
                val insertion = current[j - 1] + 1
                val diagonal = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                when {
                    diagonal <= deletion && diagonal <= insertion -> {
                        current[j] = diagonal
                        directions[j - starts[i]] = 1
                    }
                    deletion <= insertion -> {
                        current[j] = deletion
                        directions[j - starts[i]] = 2
                    }
                    else -> {
                        current[j] = insertion
                        directions[j - starts[i]] = 3
                    }
                }
                minimum = min(minimum, current[j])
            }
            if (until < b.size) current[until + 1] = infinity
            if (minimum > limit) return null
            val swap = previous; previous = current; current = swap
        }
        if (previous[b.size] > limit) return null
        val mapping = IntArray(b.size) { -1 }
        var i = a.size
        var j = b.size
        var gap = 0
        var largestGap = 0
        var lastDirection = 0
        while (i > 0 || j > 0) {
            val direction = trace[i]?.getOrNull(j - starts[i])?.toInt() ?: return null
            when (direction) {
                1 -> { mapping[j - 1] = i - 1; i--; j--; gap = 0 }
                2 -> { i--; gap = if (lastDirection == 2) gap + 1 else 1 }
                3 -> { j--; gap = if (lastDirection == 3) gap + 1 else 1 }
                else -> return null
            }
            largestGap = max(largestGap, gap)
            lastDirection = direction
        }
        return Edit(previous[b.size], largestGap, mapping)
    }

    /** Distinct surviving rows/characters cannot reverse order merely to gain a timing bonus. */
    private fun orderedContent(reference: Indexed, candidate: Indexed): Boolean {
        fun uniquePositions(values: List<String>) = values.withIndex().groupBy { it.value }
            .filterValues { it.size == 1 }.mapValues { it.value.single().index }
        val referenceRows = uniquePositions(reference.rows.map { it.text })
        val candidateRows = uniquePositions(candidate.rows.map { it.text })
        var lastRow = -1
        for (row in reference.rows) {
            if (row.text !in referenceRows) continue
            val position = candidateRows[row.text] ?: continue
            if (position < lastRow) return false
            lastRow = position
        }
        fun uniquePoints(values: IntArray): Map<Int, Int> {
            val positions = mutableMapOf<Int, Int>()
            values.forEachIndexed { index, point -> positions[point] = if (point in positions) -1 else index }
            return positions.filterValues { it >= 0 }
        }
        val referencePoints = uniquePoints(reference.points)
        val candidatePoints = uniquePoints(candidate.points)
        var lastPoint = -1
        for (point in reference.points) {
            if (point !in referencePoints) continue
            val position = candidatePoints[point] ?: continue
            if (position < lastPoint) return false
            lastPoint = position
        }
        return true
    }

    private fun pointCharacters(text: String): IntArray {
        val offsets = IntArray(text.codePointCount(0, text.length))
        var character = 0
        offsets.indices.forEach { point ->
            offsets[point] = character
            character += Character.charCount(text.codePointAt(character))
        }
        return offsets
    }

    /** Entire reference verses must survive, even when a short verse is only 1% of the song. */
    private fun preservesSections(reference: Indexed, candidate: Indexed, mapping: IntArray): Boolean {
        val covered = BooleanArray(reference.points.size)
        mapping.forEach { if (it >= 0) covered[it] = true }
        val referenceOffsets = pointCharacters(reference.text)
        val candidateOffsets = pointCharacters(candidate.text)
        fun hasPoint(row: TextRow, offsets: IntArray, available: (Int) -> Boolean): Boolean {
            val first = offsets.binarySearch(row.offset)
            if (first < 0) return false
            var point = first
            while (point < offsets.size && offsets[point] < row.offset + row.text.length) {
                if (available(point)) return true
                point++
            }
            return false
        }
        return reference.rows.all { hasPoint(it, referenceOffsets) { point -> covered[point] } } &&
            candidate.rows.all { hasPoint(it, candidateOffsets) { point -> mapping[point] >= 0 } }
    }

    private fun characterMapping(reference: Indexed, candidate: Indexed, points: IntArray): IntArray {
        val referenceOffsets = pointCharacters(reference.text)
        val candidateOffsets = pointCharacters(candidate.text)
        val mapping = IntArray(candidate.text.length) { -1 }
        candidateOffsets.forEachIndexed { point, character ->
            if (points[point] >= 0) {
                val referenceCharacter = referenceOffsets[points[point]]
                repeat(Character.charCount(candidate.points[point])) { mapping[character + it] = referenceCharacter }
            }
        }
        return mapping
    }

    private fun mappedInterval(mapping: IntArray, start: Int, end: Int): IntRange? {
        var first = start
        var last = end - 1
        while (first < end && mapping[first] < 0) first++
        while (last >= first && mapping[last] < 0) last--
        return if (first <= last) mapping[first]..mapping[last] else null
    }

    /**
     * A word boundary inside a differently split reference line has no exact reference timestamp,
     * but it does have a genuine containing [line start, line end] interval. Require its actual
     * cue to stay inside that interval; never manufacture line-internal proportional timestamps.
     * Untimed LRC line ends come from the next line's onset. A small number of coarse boundaries
     * may be early/late even when every word and the overall recording agree. Permit at most 5%
     * of mapped endpoints to exceed the normal 1s window, and none by more than 3s from the actual
     * reference interval. Word-timed references retain the strict window; global timing is also
     * checked independently after this local test.
     */
    private fun compareCueIntervals(reference: Indexed, candidate: Indexed, mapping: IntArray): CueIntervals {
        var words = 0
        var mapped = 0
        var endpoints = 0
        var outside = 0
        val coarseReference = !reference.hasWordTiming && candidate.hasWordTiming
        fun accepts(character: Int, time: Long, release: Boolean = false): Boolean {
            val error = referenceIntervalError(reference, character, time, release) ?: return false
            endpoints++
            if (error > MAX_FIXED_OFFSET_MS) {
                outside++
                if (!coarseReference || error > MAX_COARSE_ENDPOINT_ERROR_MS) return false
            }
            return true
        }
        for (row in candidate.rows) {
            if (!row.valid) continue
            val line = mappedInterval(mapping, row.offset, row.offset + row.text.length)
            if (line != null) {
                if (!accepts(line.first, row.source.startTimeMs) ||
                    !accepts(line.last, row.source.endTimeMs, release = true))
                    return CueIntervals(false, 0.0)
            }
            for (cue in row.words) {
                val points = cue.text.codePointCount(0, cue.text.length)
                words += points
                val offset = row.offset + cue.offset
                val interval = mappedInterval(mapping, offset, offset + cue.text.length) ?: continue
                if (!accepts(interval.first, cue.word.startTimeMs) ||
                    !accepts(interval.last, cue.word.endTimeMs, release = true))
                    return CueIntervals(false, 0.0)
                var character = offset
                while (character < offset + cue.text.length) {
                    if (mapping[character] >= 0) mapped++
                    character += Character.charCount(candidate.text.codePointAt(character))
                }
            }
        }
        return CueIntervals(outside * 20 <= endpoints, if (words == 0) 0.0 else mapped.toDouble() / words)
    }

    private fun referenceIntervalError(reference: Indexed, character: Int, time: Long, release: Boolean): Double? {
        var low = 0
        var high = reference.rows.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (reference.rows[mid].offset <= character) low = mid + 1 else high = mid - 1
        }
        val row = reference.rows.getOrNull(high) ?: return null
        if (!row.valid || character >= row.offset + row.text.length) return null
        // Different editors end a sustained final syllable at different points in
        // the following gap. Only its release may extend, by at most 3s and never
        // beyond the next real onset. Starts/interior words retain the strict check.
        if (release && character == row.offset + row.text.lastIndex && time > row.source.endTimeMs &&
            time - row.source.endTimeMs <= MAX_COARSE_ENDPOINT_ERROR_MS &&
            reference.rows.getOrNull(high + 1)?.let { time <= it.source.startTimeMs } != false) return 0.0
        return maxOf(0.0, row.source.startTimeMs - time.toDouble(), time.toDouble() - row.source.endTimeMs)
    }

    private fun compareTiming(reference: Indexed, candidate: Indexed, mapping: IntArray): Timing {
        if (reference.cues.isEmpty() || candidate.cues.isEmpty()) return Timing(false, 0.0)
        val positions = candidate.cues.keys.toList()
        val selected = if (positions.size <= MAX_ANCHORS) positions else
            (0 until MAX_ANCHORS).map { positions[(it.toDouble() * positions.lastIndex / (MAX_ANCHORS - 1)).roundToInt()] }.distinct()
        val anchors = mutableListOf<Anchor>()
        for (position in selected) {
            val found = mapping.getOrNull(position)?.takeIf { it >= 0 } ?: continue
            // A genuine line/word start on both sides is required. Never treat a line-internal
            // proportional estimate as evidence that a recording has a different tempo.
            val time = reference.cues[found] ?: continue
            anchors += Anchor(time, candidate.cues.getValue(position))
        }
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
