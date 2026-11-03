package io.github.currencortex.music.feature.search

import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import org.junit.Assert.*
import org.junit.Test

class SearchLyricBackfillTest {
    private val document = LyricsDocument(listOf(
        LyricLine(0, 1000, "所有的光汇成河"),
        LyricLine(1000, 2000, "抬头看着星星在唱歌"),
        LyricLine(2000, 3000, "她的呼吸好似对我说"),
        LyricLine(3000, 4000, "长句   里有多余  空格")))

    @Test fun matchedLineNeighboursAreExtractedFromTheFullDocument() {
        val context = lyricContextFromDocument(document, "抬头看着星星在唱歌")!!
        assertEquals("所有的光汇成河", context.before)
        assertEquals("抬头看着星星在唱歌", context.matched)
        assertEquals("她的呼吸好似对我说", context.after)
    }
    @Test fun joinedMultiHitMatchLocatesItsFirstSegmentLine() {
        val context = lyricContextFromDocument(document, "抬头看着星星在唱歌 · 她的呼吸好似对我说")!!
        assertEquals("所有的光汇成河", context.before)
        assertEquals("长句 里有多余 空格", context.after)
    }
    @Test fun firstAndLastLinesYieldOneSidedNeighbours() {
        assertNull(lyricContextFromDocument(document, "所有的光汇成河")!!.before)
        assertNull(lyricContextFromDocument(document, "长句 里有多余 空格")!!.after)
    }
    @Test fun absentMatchOrEmptyDocumentProduceNoContext() {
        assertNull(lyricContextFromDocument(document, "查无此句"))
        assertNull(lyricContextFromDocument(LyricsDocument(emptyList()), "抬头看着星星在唱歌"))
        assertNull(lyricContextFromDocument(document, "  ·  "))
    }
}
