package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.feature.lyrics.domain.LyricsReferenceMatcher
import io.github.currencortex.music.feature.lyrics.model.*
import org.junit.Assert.*
import org.junit.Test

class LyricsReferenceMatcherTest {
    private val text = listOf("The morning light is bright", "We walk together through rain",
        "Our hopeful voices fill the sky", "We sing until the break of day")
    private fun document(values: List<String> = text, start: Long = 1000) = LyricsDocument(values.mapIndexed { index, value ->
        LyricLine(start + index * 10_000, start + index * 10_000 + 5000, value)
    })
    private fun rich(source: LyricsDocument) = source.copy(lines = source.lines.map { line ->
        val words = Regex("\\S+").findAll(line.text).toList()
        val step = (line.endTimeMs - line.startTimeMs) / words.size.coerceAtLeast(1)
        line.copy(words = words.mapIndexed { index, word -> LyricWord(word.value,
            line.startTimeMs + step * index, line.startTimeMs + step * (index + 1),
            word.range.first, word.range.last + 1) }, translation = "翻译", romanization = "romanization")
    })
    private fun shifted(source: LyricsDocument, offset: Long = 0, speed: Double = 1.0) = source.copy(lines = source.lines.map { line ->
        fun time(value: Long) = (value * speed).toLong() + offset
        line.copy(startTimeMs = time(line.startTimeMs), endTimeMs = time(line.endTimeMs),
            words = line.words.map { it.copy(startTimeMs = time(it.startTimeMs), endTimeMs = time(it.endTimeMs)) })
    })

    @Test fun completeRealWordTimingAndAuxiliaryTracksOutrankThePlainReference() {
        val reference = document()
        val baseline = LyricsReferenceMatcher.compare(reference, reference)
        val candidate = LyricsReferenceMatcher.compare(reference, rich(reference))
        assertTrue(baseline.accepted)
        assertTrue(candidate.accepted)
        assertEquals(1.0, candidate.similarity, .00001)
        assertTrue(candidate.quality > baseline.quality + .5)
        assertTrue(candidate.total > baseline.total)
        assertFalse(reference.hasWordTiming)
    }

    @Test fun wrongContentCannotWinBySupplyingWordCuesOrTranslations() {
        val reference = document()
        val wrong = rich(document(List(4) { "Completely unrelated words from another composition" }))
        val compared = LyricsReferenceMatcher.compare(reference, wrong)
        assertFalse(compared.accepted)
        assertEquals(0.0, compared.total, 0.0)
    }

    @Test fun wordOrderAndRepeatedSectionsArePreservedRatherThanComparedAsCharacterSets() {
        val reference = document(listOf("north south east west", "the sun returns again",
            "north south east west", "we sing another ending"))
        val reordered = document(reference.lines.reversed().map { it.text })
        val missingRepeat = document(reference.lines.filterIndexed { index, _ -> index != 2 }.map { it.text })
        assertFalse(LyricsReferenceMatcher.compare(reference, reordered).accepted)
        assertFalse(LyricsReferenceMatcher.compare(reference, missingRepeat).accepted)
        val reversedCharacters = document(reference.lines.map { it.text.reversed() })
        assertFalse(LyricsReferenceMatcher.compare(reference, reversedCharacters).accepted)
    }

    @Test fun aShortExtractCannotReplaceTheWholeSong() {
        val reference = document()
        assertFalse(LyricsReferenceMatcher.compare(reference, reference.copy(lines = reference.lines.take(1))).accepted)
        assertFalse(LyricsReferenceMatcher.compare(reference, reference.copy(lines = reference.lines.takeLast(2))).accepted)
    }

    private fun uniqueRows(count: Int, width: Int, spacing: Long = 5000) = LyricsDocument((0 until count).map { row ->
        val value = (0 until width).joinToString("") { (0x4e00 + row * width + it).toChar().toString() }
        LyricLine(row * spacing, (row + 1) * spacing, value)
    })

    private fun pointTimed(source: LyricsDocument) = source.copy(lines = source.lines.map { line ->
        val count = line.text.codePointCount(0, line.text.length)
        line.copy(words = (0 until count).map { point ->
            val start = line.text.offsetByCodePoints(0, point)
            val end = line.text.offsetByCodePoints(start, 1)
            LyricWord(line.text.substring(start, end),
                line.startTimeMs + (line.endTimeMs - line.startTimeMs) * point / count,
                line.startTimeMs + (line.endTimeMs - line.startTimeMs) * (point + 1) / count, start, end)
        })
    })

    private fun dispersedReplacements(source: LyricsDocument, count: Int): LyricsDocument {
        val total = source.lines.sumOf { it.text.codePointCount(0, it.text.length) }
        val changed = (0 until count).map { it * total / count }.toSet()
        var point = 0
        return source.copy(lines = source.lines.map { line ->
            line.copy(text = buildString {
                line.text.codePoints().forEach { original ->
                    appendCodePoint(if (point in changed) 0x7000 + point else original)
                    point++
                }
            })
        })
    }

    @Test fun realWordContentAtExactlySeventyFivePercentIsInclusiveForOneHundredAndFourHundredPoints() {
        for (size in listOf(100, 400)) {
            val reference = uniqueRows(size / 10, 10)
            val candidate = pointTimed(dispersedReplacements(reference, size / 4))
            val compared = LyricsReferenceMatcher.compare(reference, candidate)
            assertTrue("$size code points at exactly 75% must qualify", compared.accepted)
            assertTrue(compared.hasWordTiming)
            assertEquals(.75, compared.similarity, 0.0)
            val below = pointTimed(dispersedReplacements(reference, size / 4 + 1))
            assertFalse("74% / 74.75% must not be rounded up", LyricsReferenceMatcher.compare(reference, below).accepted)
            assertFalse("The 75% exception is for real word timing", LyricsReferenceMatcher.compare(reference,
                candidate.copy(lines = candidate.lines.map { it.copy(words = emptyList()) })).accepted)
        }
    }

    @Test fun shortRealWordCandidatesAndPartialRealCueCoverageHaveTheSameInclusiveContentThreshold() {
        val reference = document(listOf("ABCDEFGH"))
        val candidate = document(listOf("ABXDEYGH")).let { source -> source.copy(lines = source.lines.map {
            it.copy(words = listOf(LyricWord("A", it.startTimeMs, it.startTimeMs + 100, 0, 1)))
        }) }
        val compared = LyricsReferenceMatcher.compare(reference, candidate)
        assertTrue(compared.accepted)
        assertTrue(compared.hasWordTiming)
        assertEquals(.75, compared.similarity, 0.0)
        assertTrue(compared.total < LyricsReferenceMatcher.compare(reference,
            reference.copy(lines = reference.lines.map { it.copy(translation = "翻译", romanization = "romanization") })).total)
    }

    @Test fun realCuesUseAlignedPositionsInsteadOfGloballyFindingRepeatedSingleCharacters() {
        val reference = document(List(4) { "a".repeat(25) })
        val candidate = pointTimed(dispersedReplacements(reference, 25))
        val compared = LyricsReferenceMatcher.compare(reference, candidate)
        assertTrue(compared.accepted)
        assertEquals(.75, compared.similarity, 0.0)
        assertEquals(1.0, compared.timingSimilarity, .00001)
    }

    @Test fun dispersedShortInsertionsAndDeletionsDoNotTriggerTheOldNinetySevenPercentLengthGate() {
        val reference = uniqueRows(10, 20)
        for (size in 1..3) {
            val removed = reference.copy(lines = reference.lines.map {
                it.copy(text = it.text.removeRange(5, 5 + size))
            })
            val inserted = reference.copy(lines = reference.lines.mapIndexed { index, line ->
                line.copy(text = line.text.substring(0, 5) + (0 until size).joinToString("") {
                    (0x7000 + index * 3 + it).toChar().toString()
                } + line.text.substring(5))
            })
            for (candidate in listOf(removed, inserted)) {
                val timed = pointTimed(candidate)
                assertTrue(timed.lines.all { it.words.isNotEmpty() })
                val compared = LyricsReferenceMatcher.compare(reference, timed)
                assertTrue("Dispersed $size-point corrections preserve every verse and real cue interval", compared.accepted)
                assertTrue(compared.hasWordTiming)
                assertTrue(compared.similarity >= .75)
                assertTrue(compared.similarity < .97)
            }
        }
        val shortRows = uniqueRows(100, 2, 1000)
        val wholeShortVerseRemoved = pointTimed(shortRows.copy(lines = shortRows.lines.filterIndexed { index, _ -> index != 50 }))
        assertFalse("A complete two-point verse is not an incidental word correction",
            LyricsReferenceMatcher.compare(shortRows, wholeShortVerseRemoved).accepted)
    }

    @Test fun onlyValidatedNonEstimatedWordCuesUnlockTheLowerContentThreshold() {
        val reference = uniqueRows(10, 10)
        val changed = pointTimed(dispersedReplacements(reference, 25))
        for (candidate in listOf(
            changed.copy(lines = changed.lines.map { it.copy(wordsEstimated = true) }),
            changed.copy(lines = changed.lines.map { line -> line.copy(words = line.words.map {
                it.copy(endTimeMs = it.startTimeMs)
            }) }),
            changed.copy(lines = changed.lines.map { line -> line.copy(words = line.words.map {
                it.copy(endTimeMs = line.endTimeMs + 1)
            }) }))) {
            val compared = LyricsReferenceMatcher.compare(reference, candidate)
            assertFalse(compared.hasWordTiming)
            assertFalse(compared.accepted)
        }
        val plainNearExact = dispersedReplacements(reference, 3)
        assertTrue(LyricsReferenceMatcher.compare(reference, plainNearExact).accepted)
        assertFalse(LyricsReferenceMatcher.compare(reference, dispersedReplacements(reference, 4)).accepted)
    }

    @Test(timeout = 5000) fun moreThanFiveHundredTwelveDispersedWordDifferencesUseTheBoundedLinearPath() {
        val reference = uniqueRows(100, 24)
        val compared = LyricsReferenceMatcher.compare(reference, pointTimed(dispersedReplacements(reference, 600)))
        assertTrue(compared.accepted)
        assertEquals(.75, compared.similarity, 0.0)
    }

    @Test fun genuineWordCuesCannotCompensateForMissingHeadTailOrMiddleVerses() {
        val reference = uniqueRows(10, 10)
        val missing = listOf(reference.lines.drop(1), reference.lines.dropLast(1),
            reference.lines.filterIndexed { index, _ -> index != 5 })
        val baseline = LyricsReferenceMatcher.compare(reference, reference)
        missing.forEach { lines ->
            val candidate = rich(reference.copy(lines = lines))
            assertTrue(candidate.hasWordTiming)
            val compared = LyricsReferenceMatcher.compare(reference, candidate)
            assertFalse(compared.accepted)
            assertEquals(0.0, compared.total, 0.0)
            assertTrue(compared.total < baseline.total)
        }
        // A complete five-character verse can be only 2% of a long song. Its loss still
        // matters: a global length ratio alone must not turn it into a "better" document.
        val longer = uniqueRows(50, 5)
        val omitted = rich(longer.copy(lines = longer.lines.filterIndexed { index, _ -> index != 25 }))
        assertFalse(LyricsReferenceMatcher.compare(longer, omitted).accepted)
    }

    @Test fun localReorderingIsRejectedEvenWhenItsGlobalEditRatioIsVerySmall() {
        for (reference in listOf(uniqueRows(20, 5), uniqueRows(100, 2, 1000))) {
            val middle = reference.lines.size / 2
            val reordered = reference.copy(lines = reference.lines.mapIndexed { index, line ->
                line.copy(text = when (index) {
                    middle -> reference.lines[middle + 1].text
                    middle + 1 -> reference.lines[middle].text
                    else -> line.text
                })
            })
            assertFalse(LyricsReferenceMatcher.compare(reference, rich(reordered)).accepted)
        }
    }

    @Test fun realWordCuesMustFitTheReferencesRealLineIntervalsAcrossDifferentSplits() {
        val reference = uniqueRows(20, 5)
        val all = reference.lines.joinToString("") { it.text }
        fun combined(start: Int, end: Int, fromMs: Long, untilMs: Long, compress: Boolean): LyricLine {
            val value = all.substring(start, end)
            val words = value.indices.step(13).mapIndexed { index, offset ->
                val until = minOf(offset + 13, value.length)
                LyricWord(value.substring(offset, until),
                    if (compress) fromMs + index * 1000 else fromMs + offset * 1000,
                    if (compress) fromMs + (index + 1) * 1000 else fromMs + until * 1000,
                    offset, until)
            }
            return LyricLine(fromMs, untilMs, value, words)
        }
        fun candidate(compress: Boolean) = LyricsDocument(listOf(
            combined(0, 60, 0, 60_000, compress), combined(60, 100, 60_000, 100_000, compress)))
        val correctSplit = LyricsReferenceMatcher.compare(reference, candidate(compress = false))
        assertTrue("Different line and word grouping still fits the actual reference intervals", correctSplit.accepted)
        val compressed = LyricsReferenceMatcher.compare(reference, candidate(compress = true))
        assertEquals(1.0, compressed.similarity, 0.0)
        assertFalse("Two shared line starts cannot certify every intermediate word cue", compressed.accepted)
        assertEquals(0.0, compressed.total, 0.0)
    }

    @Test fun aFewCoarseLrcBoundariesDoNotRejectTheSameCompleteWordTimedRecording() {
        val original = uniqueRows(50, 10)
        val earlyRows = setOf(8, 23, 37)
        val starts = original.lines.mapIndexed { index, line ->
            line.startTimeMs - if (index in earlyRows) 2000 else 0
        }
        val coarseReference = original.copy(lines = original.lines.mapIndexed { index, line ->
            line.copy(startTimeMs = starts[index], endTimeMs = starts.getOrNull(index + 1) ?: line.endTimeMs)
        })
        val compared = LyricsReferenceMatcher.compare(coarseReference, pointTimed(original))
        assertTrue("A few inaccurate next-line stamps are not authoritative word endings", compared.accepted)
        assertTrue(compared.hasWordTiming)
        assertEquals(1.0, compared.similarity, 0.0)
        assertTrue(compared.timingSimilarity > .8)
        assertEquals(0L, original.lines.first().startTimeMs)
    }

    private fun mergedWordRows(source: LyricsDocument) = pointTimed(source.copy(lines = source.lines.chunked(2).map { pair ->
        LyricLine(pair.first().startTimeMs, pair.last().endTimeMs, pair.joinToString("") { it.text })
    }))

    @Test fun evenOneSeverelyIncorrectWordEndpointCannotUseTheCoarseBoundaryAllowance() {
        val reference = uniqueRows(50, 10)
        val candidate = mergedWordRows(reference).let { document -> document.copy(lines = document.lines.mapIndexed { index, line ->
            if (index != 0) line else line.copy(words = line.words.mapIndexed { word, cue ->
                if (word != 0) cue else cue.copy(endTimeMs = 9001)
            })
        }) }
        // The source line contains this cue and all cue starts remain ordered. Its actual end
        // is nevertheless 4001ms beyond the containing reference line, so it must be rejected.
        assertTrue(candidate.lines.first().words.first().endTimeMs < candidate.lines.first().endTimeMs)
        assertFalse(LyricsReferenceMatcher.compare(reference, candidate).accepted)
    }

    @Test fun numerousModerateEndpointErrorsStillRejectAnOtherwiseAlignedWordTimeline() {
        val reference = uniqueRows(50, 10)
        val candidate = mergedWordRows(reference).let { document -> document.copy(lines = document.lines.map { line ->
            line.copy(words = line.words.mapIndexed { index, cue ->
                if (index >= 3) cue else cue.copy(endTimeMs = line.startTimeMs + 7000)
            })
        }) }
        // All real starts and overall duration agree. 75 of 1050 endpoints exceed the ordinary
        // window by 2s, so the local exception cannot hide a broadly inaccurate word timeline.
        val compared = LyricsReferenceMatcher.compare(reference, candidate)
        assertEquals(1.0, compared.similarity, 0.0)
        assertTrue(compared.hasWordTiming)
        assertFalse(compared.accepted)
    }

    @Test fun unicodeNormalizationKeepsChineseJapaneseKoreanAndEmojiInOrder() {
        val reference = document(listOf("Ｈｅｌｌｏ， 世界！", "また会う日まで", "노래는 계속되고 🎵", "e\u0301toile shines"))
        val candidate = document(listOf("hello 世界", "また会う日まで。", "노래는계속되고🎵", "ÉTOILE SHINES"))
        val compared = LyricsReferenceMatcher.compare(reference, candidate)
        assertTrue(compared.accepted)
        assertEquals(1.0, compared.similarity, 0.0)
        val differentEmoji = candidate.copy(lines = candidate.lines.map { it.copy(text = it.text.replace("🎵", "🎶")) })
        assertTrue(LyricsReferenceMatcher.compare(reference, differentEmoji).similarity < 1.0)
    }

    @Test fun differentLineAndWordBoundariesStillMatchTheSamePrimaryContentAndCues() {
        val reference = document(listOf("Hello world", "明日の空へ向かう", "노래는 계속되고", "We sing together"))
        val first = reference.lines.first()
        val candidate = reference.copy(lines = listOf(
            LyricLine(first.startTimeMs, first.startTimeMs + 2500, "Hello"),
            LyricLine(first.startTimeMs + 2500, first.endTimeMs, "world")) + reference.lines.drop(1))
        val compared = LyricsReferenceMatcher.compare(reference, candidate)
        assertTrue(compared.accepted)
        assertEquals(1.0, compared.similarity, 0.0)
        // This is also the real short LRC -> TTML case; there is only one shared start cue.
        val shortReference = LyricsDocument(listOf(LyricLine(1000, 6000, "Hello world")))
        val shortWords = LyricsDocument(listOf(LyricLine(1000, 3000, "Hello world", listOf(
            LyricWord("Hello", 1000, 2000, 0, 5), LyricWord(" world", 2000, 3000, 5, 11)))))
        val shortScore = LyricsReferenceMatcher.compare(shortReference, shortWords)
        assertTrue(shortScore.accepted)
        assertTrue(shortScore.total > LyricsReferenceMatcher.compare(shortReference, shortReference).total)
    }

    @Test fun translationOrRomanizationMustNotSubstituteForActualPrimaryLyrics() {
        val reference = document()
        val translated = reference.copy(lines = reference.lines.mapIndexed { index, line ->
            line.copy(text = "这是第${index}句中文翻译", translation = line.text, romanization = line.text)
        })
        assertFalse(LyricsReferenceMatcher.compare(reference, translated).accepted)
        val bilingualSinging = document(listOf("We sing together", "明日の空へ向かう", "We sing together", "노래는 계속되고"))
        val onlyEnglish = bilingualSinging.copy(lines = bilingualSinging.lines.filter { it.text.startsWith("We") })
        assertFalse(LyricsReferenceMatcher.compare(bilingualSinging, onlyEnglish).accepted)
    }

    @Test fun leadingExplicitCreditsMayBeOmittedWithoutChangingTheOriginalDocument() {
        val vocals = document(start = 10_000)
        val labels = listOf("作词：", "作曲:", "编曲：", "制作：")
        val credits = labels.mapIndexed { index, label -> LyricLine(index * 1000L, (index + 1) * 1000L,
            label + "音乐人以及参与录制的工作人员".repeat(20)) }
        val reference = vocals.copy(lines = credits + vocals.lines)
        val compared = LyricsReferenceMatcher.compare(reference, rich(vocals))
        assertTrue(compared.accepted)
        assertEquals(1.0, compared.similarity, 0.0)
        assertEquals(8, reference.lines.size)
        assertTrue(reference.lines.first().text.startsWith("作词："))
        // Without an explicit metadata delimiter the text remains primary content.
        val sungCreditWord = LyricLine(0, 5000, "作词的人也在歌唱属于自己的漫长故事".repeat(5))
        assertFalse(LyricsReferenceMatcher.compare(vocals.copy(lines = listOf(sungCreditWord) + vocals.lines), vocals).accepted)
    }

    @Test fun contiguousTrailingProductionCreditsAreNotMissingSungVerses() {
        val vocals = document()
        val labels = listOf("吉他", "贝斯", "和声编写", "和声", "录音工程师", "录音工程室",
            "混音工程师", "混音工程室", "制作助理", "OP", "SP")
        val credits = labels.mapIndexed { index, label ->
            LyricLine(40_000L + index * 1000, 41_000L + index * 1000, "$label：Studio Contributor")
        }
        val reference = rich(vocals).copy(lines = rich(vocals).lines + credits)
        val score = LyricsReferenceMatcher.compare(reference, rich(vocals))
        assertTrue(score.accepted)
        assertEquals(1.0, score.similarity, 0.0)
        assertEquals(15, reference.lines.size)
        assertTrue(LyricsReferenceMatcher.compare(rich(vocals), reference).accepted)
    }

    @Test fun isolatedInteriorUnlabelledAndOversizedCreditLikeBlocksRemainContent() {
        val vocals = document()
        val credit = LyricLine(40_000, 41_000, "吉他：A long sung phrase that must remain part of the recording")
        for (lines in listOf(
            vocals.lines + credit,
            vocals.lines.take(2) + credit + credit.copy(startTimeMs = 42_000, endTimeMs = 43_000) + vocals.lines.drop(2),
            vocals.lines + credit + credit.copy(text = "An unlabelled final verse"),
            vocals.lines + List(33) { credit.copy(startTimeMs = 40_000L + it * 1000, endTimeMs = 41_000L + it * 1000) }
        )) assertFalse(LyricsReferenceMatcher.compare(LyricsDocument(lines), rich(vocals)).accepted)
    }

    @Test fun sustainedFinalWordMayReleaseLaterWithinTheFollowingGap() {
        val reference = rich(document())
        val extended = reference.copy(lines = reference.lines.map { line ->
            line.copy(endTimeMs = line.endTimeMs + 2800,
                words = line.words.dropLast(1) + line.words.last().copy(endTimeMs = line.endTimeMs + 2800))
        })
        assertTrue(LyricsReferenceMatcher.compare(reference, extended).accepted)
        assertEquals(reference.lines.first().startTimeMs, extended.lines.first().startTimeMs)
        // This tolerance cannot rescue shifted starts, earlier endings, or excessive tails.
        assertFalse(LyricsReferenceMatcher.compare(reference, shifted(extended, 3000)).accepted)
        val excessive = extended.copy(lines = extended.lines.map { line -> line.copy(endTimeMs = line.endTimeMs + 500,
            words = line.words.dropLast(1) + line.words.last().copy(endTimeMs = line.endTimeMs + 500)) })
        assertFalse(LyricsReferenceMatcher.compare(reference, excessive).accepted)
        val crossing = rich(document()).let { doc -> doc.copy(lines = doc.lines.mapIndexed { i, line ->
            if (i == 1) line.copy(startTimeMs = doc.lines.first().endTimeMs + 2000) else line
        }) }
        assertFalse(LyricsReferenceMatcher.compare(crossing, extended).accepted)
    }

    @Test fun smallCueOffsetIsToleratedButUnappliedThreeSecondOffsetsAreRejected() {
        val reference = document()
        assertTrue(LyricsReferenceMatcher.compare(reference, shifted(rich(reference), 500)).accepted)
        val offset = LyricsReferenceMatcher.compare(reference, shifted(rich(reference), 3000))
        assertEquals(1.0, offset.similarity, 0.0)
        assertFalse(offset.accepted)
        assertEquals(0.0, offset.total, 0.0)
    }

    @Test fun differentTempoAndAChangedInstrumentalGapAreNotTheSameRecording() {
        val reference = document()
        assertFalse(LyricsReferenceMatcher.compare(reference, shifted(rich(reference), speed = 1.12)).accepted)
        val gap = rich(reference).let { it.copy(lines = it.lines.mapIndexed { index, line ->
            if (index == it.lines.lastIndex) shifted(LyricsDocument(listOf(line)), 5000).lines.single() else line
        }) }
        assertFalse(LyricsReferenceMatcher.compare(reference, gap).accepted)
    }

    @Test fun invalidAndEstimatedWordTimesNeverReceiveARealWordQualityBonus() {
        val reference = document()
        val baseline = LyricsReferenceMatcher.compare(reference, reference)
        val estimated = rich(reference).copy(lines = rich(reference).lines.map {
            it.copy(wordsEstimated = true, translation = "", romanization = "")
        })
        val invalid = estimated.copy(lines = estimated.lines.map { line -> line.copy(wordsEstimated = false,
            words = line.words.map { it.copy(endTimeMs = line.endTimeMs + 1000) }) })
        assertEquals(baseline.quality, LyricsReferenceMatcher.compare(reference, estimated).quality, .00001)
        assertEquals(baseline.quality, LyricsReferenceMatcher.compare(reference, invalid).quality, .00001)
        assertTrue(estimated.lines.all { it.wordsEstimated && it.words.isNotEmpty() })
        val invalidLines = reference.copy(lines = reference.lines.map { it.copy(endTimeMs = it.startTimeMs) })
        assertFalse(LyricsReferenceMatcher.compare(reference, invalidLines).accepted)
    }

    @Test fun providerNamesCannotChangeTheScoreAndShortTextsRequireExactContent() {
        val reference = document()
        val renamed = reference.copy(metadata = reference.metadata.copy(source = "An entirely different platform"))
        assertEquals(LyricsReferenceMatcher.compare(reference, reference), LyricsReferenceMatcher.compare(reference, renamed))
        val short = document(listOf("Forever love"))
        assertTrue(LyricsReferenceMatcher.compare(short, short).accepted)
        assertFalse(LyricsReferenceMatcher.compare(short, document(listOf("Forever dove"))).accepted)
    }

    @Test(timeout = 5000) fun oversizedAndAdversarialTextsAreBoundedWhileLongExactTextUsesTheFastPath() {
        val long = document(listOf("alpha beta gamma delta ".repeat(1000)))
        assertTrue(LyricsReferenceMatcher.compare(long, long).accepted)
        val different = document(listOf("alpha beta gamma zelta ".repeat(1000)))
        assertFalse(LyricsReferenceMatcher.compare(long, different).accepted)
        val tooLong = document(listOf("a".repeat(25_000)))
        assertFalse(LyricsReferenceMatcher.compare(tooLong, tooLong).accepted)
        assertFalse(LyricsReferenceMatcher.compare(LyricsDocument(), long).accepted)
    }
}
