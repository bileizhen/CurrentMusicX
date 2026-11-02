package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import org.junit.Assert.*
import org.junit.Test

class InlineTranslationsTest {
    @Test fun lrcTranslationBlocksBecomeAuxiliaryLinesWithOriginalTiming() {
        val document = LyricsParser.lrc("[00:24.00]Not a single day goes by [tr]没有一天不思念你[/tr]\n[00:30.00]Show me [tr]让我看清心中所想[/tr]")
        val line = document.lines.first()
        assertEquals("Not a single day goes by", line.text)
        assertEquals("没有一天不思念你", line.translation)
        assertEquals(24000L, line.startTimeMs); assertEquals(30000L, line.endTimeMs)
        assertTrue(line.words.isEmpty())
    }
    @Test fun yrcStripsBlocksAcrossTokensAndKeepsRealPrimaryWordTiming() {
        val line = LyricsParser.yrc("[1000,4000](1000,700,0)Hello (1700,1300,0)world(3000,500,0) [tr]你(3500,500,0)好[/tr]").lines.single()
        assertEquals("Hello world", line.text); assertEquals("你好", line.translation)
        assertEquals(listOf("Hello ", "world"), line.words.map { it.text })
        assertEquals(listOf(1000L, 1700L), line.words.map { it.startTimeMs })
        assertEquals(listOf(1700L, 3000L), line.words.map { it.endTimeMs })
        assertEquals(6, line.words.last().startOffset); assertEquals(11, line.words.last().endOffset)
        assertEquals(5000L, line.endTimeMs)
    }
    @Test fun repeatedTimestampsAndCaseInsensitiveMultipleBlocks() {
        val lines = LyricsParser.lrc("[00:01][00:02]Hello [TR]你好[/TR] [tr]再会[/tr]").lines
        assertEquals(2, lines.size)
        lines.forEach { assertEquals("Hello", it.text); assertEquals("你好\n再会", it.translation) }
    }
    @Test fun normalizedExplicitTranslationWinsAndInvalidTagsRemainLiteral() {
        val line = LyricsParser.parse(ApiJson.parseToJsonElement("""{"lines":[{"t":1000,"txt":"Hello [tr]内嵌[/tr]","trans":"独立译文"}]}""")).lines.single()
        assertEquals("Hello", line.text); assertEquals("独立译文", line.translation)
        assertEquals("Hello [tr]未闭合", LyricsParser.lrc("[00:01]Hello [tr]未闭合").lines.single().text)
        assertEquals("[track]one[/track]", LyricsParser.lrc("[00:01][track]one[/track]").lines.single().text)
    }
    @Test fun cachedDocumentsAndBackgroundVocalsMigrateIdempotently() {
        val source = LyricsDocument(listOf(LyricLine(1000, 4000, "  [tr]你好[/tr]Hello🌙 ",
            words = listOf(LyricWord("Hello🌙", 1000, 3000, 13, 20)),
            backgroundVocals = listOf(LyricLine(1500, 2500, "Hey[tr]嘿[/tr]")))))
        val clean = source.withoutEstimatedTiming()
        assertEquals("Hello🌙", clean.lines.single().text)
        assertEquals("你好", clean.lines.single().translation)
        assertEquals(0, clean.lines.single().words.single().startOffset)
        assertEquals(7, clean.lines.single().words.single().endOffset)
        assertEquals("嘿", clean.lines.single().backgroundVocals.single().translation)
        assertEquals(clean, clean.withoutEstimatedTiming()); assertEquals(clean, clean.withInlineTranslationBlocks())
    }
    @Test fun multilineTranslationsAndStandaloneCreditsAreNotDiscarded() {
        val document = LyricsDocument(listOf(LyricLine(0, 1000, "Original[tr]第一行\n第二行[/tr]"),
            LyricLine(1000, 2000, "[tr]译者说明[/tr]"))).withInlineTranslationBlocks()
        assertEquals("第一行\n第二行", document.lines.first().translation)
        assertEquals("译者说明", document.lines.last().text)
        assertTrue(document.lines.none { "[tr]" in it.text })
    }
}
