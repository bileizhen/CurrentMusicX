package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import io.github.currencortex.music.feature.lyrics.timeline.*
import org.junit.Assert.*
import org.junit.Test

class LyricsEngineTest {
    @Test fun legacyRowsStayLineTimedAndSort() {
        val doc = LyricsParser.parse(ApiJson.parseToJsonElement("""{"lines":[{"t":3000,"txt":"第二句","trans":"Second"},{"t":1000,"txt":"第一句"}]}"""))
        assertFalse(doc.hasWordTiming)
        assertEquals(1000L, doc.lines.first().startTimeMs)
        assertEquals(3000L, doc.lines.first().endTimeMs)
        assertEquals("Second", doc.lines.last().translation)
    }
    @Test fun yrcKeepsUnequalDurationsAndMixedTextOffsets() {
        val doc = LyricsParser.yrc("[1000,4000](1000,700,0)Hello (1700,1300,0)世界(3000,2000,0) またね")
        assertEquals("Hello 世界 またね", doc.lines.single().text)
        assertEquals(6, doc.lines.single().words[1].startOffset)
        assertEquals(8, doc.lines.single().words[1].endOffset)
        assertEquals(5000L, doc.lines.single().words.last().endTimeMs)
    }
    @Test fun normalizedWordTimingAndAuxiliaryTextSurvive() {
        val doc = LyricsParser.parse(ApiJson.parseToJsonElement("""{"lines":[{"t":100,"end":900,"txt":"你好","trans":"Hi","roma":"ni hao","words":[{"text":"你","start":100,"end":400},{"text":"好","start":400,"end":900}]}]}"""))
        assertTrue(doc.hasWordTiming)
        assertEquals("ni hao", doc.lines.single().romanization)
        assertEquals(1, doc.lines.single().words.last().startOffset)
    }
    @Test fun lrcSupportsOffsetsRepeatedStampsAndClosingWordStamp() {
        val doc = LyricsParser.lrc("[offset:-100]\n[00:01.20][00:03.400]再次相见\n[00:05.00]<00:05.00>Hello <00:05.70>世界<00:06.80>")
        assertEquals(listOf(1100L, 3300L, 4900L), doc.lines.map { it.startTimeMs })
        assertEquals(6700L, doc.lines.last().words.last().endTimeMs)
        assertEquals("Hello 世界", doc.lines.last().text)
    }
    @Test fun rawResponseJoinsTranslationsAndIgnoresYrcMetadata() {
        val doc = LyricsParser.parse(ApiJson.parseToJsonElement("""{"yrc":{"lyric":"{\"metadata\":true}\n[1000,2000](1000,2000,0)风"},"ytlrc":{"lyric":"[00:01.00]Wind"},"yromalrc":{"lyric":"[00:01.00]feng"}}"""))
        assertEquals("Wind", doc.lines.single().translation)
        assertEquals("feng", doc.lines.single().romanization)
    }
    @Test fun malformedTokensDoNotCrashOrInventWordTiming() {
        val doc = LyricsParser.parse(ApiJson.parseToJsonElement("""{"lines":[null,{"t":1,"txt":"one","words":[{"text":"missing","start":1,"end":0}]},{"txt":"missing time"}]}"""))
        assertEquals(1, doc.lines.size)
        assertTrue(doc.lines.single().words.isEmpty())
    }
    @Test fun separateEnglishTokensKeepTimingAcrossSpaces() {
        val doc = LyricsParser.parse(ApiJson.parseToJsonElement("""{"lines":[{"t":100,"txt":"Hello world","words":[{"text":"Hello","start":100,"end":400},{"text":"world","start":400,"end":900}]}]}"""))
        assertTrue(doc.hasWordTiming)
        assertEquals(6, doc.lines.single().words.last().startOffset)
    }
    @Test fun timelineHandlesIntroBoundaryAndBackwardSeek() {
        val timeline = LyricsTimeline(LyricsParser.lrc("[00:02.00]一\n[00:04.00]二\n[00:08.00]三"))
        assertEquals(-1, timeline.lineAt(1999))
        assertEquals(0, timeline.lineAt(2000))
        assertEquals(2, timeline.lineAt(9000))
        assertEquals(0, timeline.lineAt(2500))
        assertEquals(-1, LyricsTimeline(LyricsParser.lrc("")).lineAt(0))
    }
    @Test fun playbackAnchorFreezesAndClampsWithoutConsultingPlayer() {
        assertEquals(1500L, PlaybackAnchor(1000, 50, true).positionAt(550))
        assertEquals(1000L, PlaybackAnchor(1000, 50, false).positionAt(550))
        assertEquals(1400L, PlaybackAnchor(1000, 50, true, 1400).positionAt(550))
        assertEquals(2000L, PlaybackAnchor(1000, 50, true, speed = 2f).positionAt(550))
        assertEquals(1000L, PlaybackAnchor(1000, 50, true).positionAt(0))
        assertEquals(3500L, PlaybackAnchor(1000, 50, true).positionAt(2550))
        assertEquals(4000L, PlaybackAnchor(1000, 50, true).positionAt(10050))
    }
}
