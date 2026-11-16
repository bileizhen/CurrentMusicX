package io.github.currencortex.music.feature.lyrics.ui

import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale

private data class JapaneseReadingUnit(val start: Int, val end: Int, val readings: List<String>?)
private data class RomanLetters(val text: String, val sourceOffsets: List<Int>)

/** Contracted sounds and small-tsu groups share a display cell across source word splits. */
internal fun japanesePhoneticGroups(text: String): List<IntRange> =
    japaneseReadingUnits(text).orEmpty().filter { it.readings != null && it.end - it.start > 1 }
        .map { it.start until it.end }

/** Kana anchors locate the supplied reading of intervening kanji. No pronunciation is generated. */
internal fun alignJapaneseRomanization(text: String, romanization: String): List<ClassicPhoneticCell> {
    if (text.length > 4096 || romanization.length > 4096 || !text.any {
        it in '\u3041'..'\u3096' || it in '\u30a1'..'\u30fa' || it in '\uff66'..'\uff9d'
    }) return emptyList()
    val units = japaneseReadingUnits(text) ?: return emptyList()
    val roman = romanLetters(romanization)
    val length = roman.text.length
    if (units.isEmpty() || units.size > 256 || length == 0 || length > 2048) return emptyList()
    val forward = Array(units.size + 1) { BooleanArray(length + 1) }
    val backward = Array(units.size + 1) { BooleanArray(length + 1) }
    forward[0][0] = true
    backward[units.size][length] = true

    fun readingEnd(end: Int) = roman.text[end - 1] in "aeiounm" &&
        (end == length || roman.sourceOffsets[end] != roman.sourceOffsets[end - 1])
    fun matches(start: Int, reading: String): Boolean {
        val end = start + reading.length
        return end <= length && roman.text.startsWith(reading, start) &&
            (end == length || roman.sourceOffsets[end] != roman.sourceOffsets[end - 1])
    }

    units.forEachIndexed { index, unit ->
        val readings = unit.readings
        if (readings == null) {
            // A kanji group consumes a nonempty reading ending at a syllable boundary.
            var reachable = false
            for (end in 1..length) {
                reachable = reachable || forward[index][end - 1]
                forward[index + 1][end] = reachable && readingEnd(end)
            }
        } else {
            for (start in 0 until length) if (forward[index][start]) {
                for (reading in readings) if (matches(start, reading))
                    forward[index + 1][start + reading.length] = true
            }
        }
    }
    if (!forward[units.size][length]) return emptyList()
    for (index in units.indices.reversed()) {
        val readings = units[index].readings
        if (readings == null) {
            var reachable = false
            for (start in length - 1 downTo 0) {
                val end = start + 1
                reachable = reachable || readingEnd(end) && backward[index + 1][end]
                backward[index][start] = reachable
            }
        } else {
            for (start in 0 until length) {
                backward[index][start] = readings.any {
                    matches(start, it) && backward[index + 1][start + it.length]
                }
            }
        }
    }

    val result = mutableListOf<ClassicPhoneticCell>()
    var firstUnit = 0
    var sourceStart = 0
    for (boundary in 1..units.size) {
        val positions = (0..length).filter { forward[boundary][it] && backward[boundary][it] }
        // Keep an ambiguous boundary inside one shared word block. Every emitted
        // boundary must be agreed on by every complete alignment of the line.
        if (positions.size != 1) continue
        val position = positions.single()
        val sourceEnd = if (position == length) romanization.length else roman.sourceOffsets[position]
        result += ClassicPhoneticCell(units[firstUnit].start, units[boundary - 1].end,
            romanization.substring(sourceStart, sourceEnd).trim())
        firstUnit = boundary
        sourceStart = sourceEnd
    }
    return result
}

private fun romanLetters(text: String): RomanLetters {
    val letters = StringBuilder()
    val offsets = mutableListOf<Int>()
    text.forEachIndexed { index, character ->
        val normalized = Normalizer.normalize(character.toString(), Normalizer.Form.NFKD).lowercase(Locale.ROOT)
        normalized.filter { it in 'a'..'z' || it in '0'..'9' }.forEach {
            letters.append(it)
            offsets += index
        }
    }
    return RomanLetters(letters.toString(), offsets)
}

private fun japaneseReadingUnits(text: String): List<JapaneseReadingUnit>? {
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    val clusters = mutableListOf<Triple<Int, Int, String>>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        val kana = Normalizer.normalize(text.substring(start, end), Normalizer.Form.NFKC).map {
            if (it in '\u30a1'..'\u30f6') (it.code - 0x60).toChar() else it
        }.joinToString("")
        clusters += Triple(start, end, kana)
        start = end
        end = iterator.next()
    }
    val units = mutableListOf<JapaneseReadingUnit>()
    var index = 0
    while (index < clusters.size) {
        val (from, until, kana) = clusters[index]
        val point = kana.codePointAt(0)
        if (Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN || kana == "々") {
            var last = index + 1
            while (last < clusters.size && (Character.UnicodeScript.of(clusters[last].third.codePointAt(0)) ==
                    Character.UnicodeScript.HAN || clusters[last].third == "々")) last++
            units += JapaneseReadingUnit(from, clusters[last - 1].second, null)
            index = last
            continue
        }
        if (kana.codePoints().noneMatch(Character::isLetterOrDigit) && kana != "ー") {
            index++
            continue
        }
        var last = index + 1
        val next = clusters.getOrNull(last)?.third.orEmpty()
        val pair = next.takeIf { it.isNotEmpty() }?.let { kanaReadings[kana + it] }
        var readings = pair?.also { last++ } ?: kanaReadings[kana]
        if (kana == "っ") {
            val following = clusters.getOrNull(last)?.third ?: return null
            last++
            val small = clusters.getOrNull(last)?.third.orEmpty()
            val contraction = small.takeIf { it.isNotEmpty() }?.let { kanaReadings[following + it] }
            val followingReadings = contraction?.also { last++ }
                ?: kanaReadings[following] ?: return null
            readings = followingReadings.filter { it.first() !in "aeioun" }.flatMap {
                listOf(it.first() + it) + if (it.startsWith("ch")) listOf("t$it") else emptyList()
            }.distinct()
        }
        if (readings == null) {
            // Latin words can anchor mixed Japanese lines too. Other unsupported
            // scripts retain the complete line-level reading in the caller.
            if (!kana.all { it.isLetterOrDigit() && it.code < 128 }) return null
            readings = listOf(kana.lowercase(Locale.ROOT))
        }
        if (readings.isEmpty()) return null
        if (clusters.getOrNull(last)?.third == "ー") {
            readings = readings.flatMap { reading ->
                val vowel = reading.last()
                listOf(reading, reading + vowel) + when (vowel) {
                    'o' -> listOf(reading + 'u')
                    'e' -> listOf(reading + 'i')
                    else -> emptyList()
                }
            }.distinct()
            last++
        }
        units += JapaneseReadingUnit(from, clusters[last - 1].second, readings)
        index = last
    }
    return units
}

private val kanaReadings: Map<String, List<String>> = buildMap {
    fun row(kana: String, vararg readings: String) {
        kana.forEachIndexed { index, character -> put(character.toString(), readings[index].split('/')) }
    }
    row("あいうえお", "a", "i", "u", "e", "o")
    row("かきくけこ", "ka", "ki", "ku", "ke", "ko")
    row("さしすせそ", "sa", "shi/si", "su", "se", "so")
    row("たちつてと", "ta", "chi/ti", "tsu/tu", "te", "to")
    row("なにぬねの", "na", "ni", "nu", "ne", "no")
    row("はひふへほ", "ha/wa", "hi", "fu/hu", "he/e", "ho")
    row("まみむめも", "ma", "mi", "mu", "me", "mo")
    row("やゆよ", "ya", "yu", "yo")
    row("らりるれろ", "ra", "ri", "ru", "re", "ro")
    row("わゐゑをん", "wa", "wi/i", "we/e", "wo/o", "n/nn/m")
    row("がぎぐげご", "ga", "gi", "gu", "ge", "go")
    row("ざじずぜぞ", "za", "ji/zi", "zu", "ze", "zo")
    row("だぢづでど", "da", "ji/di/zi", "zu/du", "de", "do")
    row("ばびぶべぼ", "ba", "bi", "bu", "be", "bo")
    row("ぱぴぷぺぽ", "pa", "pi", "pu", "pe", "po")
    row("ぁぃぅぇぉゃゅょゎゔ", "a", "i", "u", "e", "o", "ya", "yu", "yo", "wa", "vu")
    for ((base, stems) in mapOf("き" to "ky", "ぎ" to "gy", "し" to "sh/sy", "じ" to "j/jy/zy",
        "ち" to "ch/ty", "ぢ" to "j/dy", "に" to "ny", "ひ" to "hy", "び" to "by",
        "ぴ" to "py", "み" to "my", "り" to "ry")) {
        for ((small, vowel) in listOf("ゃ" to "a", "ゅ" to "u", "ょ" to "o"))
            put(base + small, stems.split('/').map { it + vowel })
    }
    for ((kana, reading) in mapOf("いぇ" to "ye", "うぃ" to "wi", "うぇ" to "we", "うぉ" to "wo",
        "しぇ" to "she/sye", "じぇ" to "je/jye/zye", "ちぇ" to "che/tye",
        "てぃ" to "ti", "でぃ" to "di", "とぅ" to "tu", "どぅ" to "du", "てゅ" to "tyu", "でゅ" to "dyu",
        "ふぁ" to "fa", "ふぃ" to "fi", "ふぇ" to "fe", "ふぉ" to "fo", "ふゅ" to "fyu",
        "ゔぁ" to "va", "ゔぃ" to "vi", "ゔぇ" to "ve", "ゔぉ" to "vo", "ゔゅ" to "vyu"))
        put(kana, reading.split('/'))
}
