package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.feature.lyrics.ttml.TtmlParser
import io.github.currencortex.music.feature.lyrics.model.*
import org.junit.Assert.*
import org.junit.Test

class TtmlParserTest {
    private fun xml(body: String, head: String = "") = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:amll="http://www.example.com/ns/amll" xmlns:itunes="http://itunes.apple.com/lyric-ttml-extensions"><head><metadata>$head</metadata></head><body><div>$body</div></body></tt>"""
    @Test fun spansDefineCjkAndEmojiTokensWithoutSpaceSplitting() {
        val doc = TtmlParser.parse(xml("""<p begin="1" end="3"><span begin="1" end="1.4">你好</span><span begin="1.4" end="2">世界🌙</span><span begin="2" end="3">またね 안녕</span><span ttm:role="x-translation" xml:lang="zh-Hans">明天见</span><span ttm:role="x-roman">mata ne</span></p>"""))
        val line = doc.lines.single()
        assertEquals("你好世界🌙またね 안녕", line.text)
        assertEquals(3, line.words.size)
        assertEquals(2, line.words[1].startOffset)
        assertEquals(6, line.words[1].endOffset)
        assertEquals(1400L, line.words[1].startTimeMs)
        assertEquals("明天见", line.translation)
        assertEquals("zh-Hans", line.translations.single().language)
        assertEquals("mata ne", line.romanization)
    }
    @Test fun inlineEnglishSpacesAndXmlEntitiesSurvive() {
        val line = TtmlParser.parse(xml("""<p begin="0" end="2"><span begin="0" end="1">Hello</span> <span begin="1" end="2">world &amp; you</span></p>""")).lines.single()
        assertEquals("Hello world & you", line.text)
        assertEquals(6, line.words[1].startOffset)
        assertEquals("world & you", line.words[1].text)
        val formatted = TtmlParser.parse(xml("""<p begin="0" end="2">
          <span begin="0" end="1">Hello </span>
          <span begin="1" end="2">world</span>
        </p>""")).lines.single()
        assertEquals("Hello world", formatted.text)
        assertEquals(2, formatted.words.size)
    }
    @Test fun agentsRolesMetadataAndIndependentBackgroundTimingSurvive() {
        val head = """<ttm:agent xml:id="v1" type="person"><ttm:name type="full">Alice</ttm:name></ttm:agent><ttm:agent xml:id="v2" type="person"/><amll:meta key="ncmMusicId" value="123"/><amll:meta key="ncmMusicId" value="456"/><amll:meta key="ttmlAuthorGithubLogin" value="writer"/>"""
        val doc = TtmlParser.parse(xml("""<p begin="1" end="4" ttm:agent="v1" itunes:key="L1"><span begin="1" end="4">主唱</span><span ttm:role="x-bg" ttm:agent="v2"><span begin="2" end="3">和声</span><span ttm:role="x-translation">Background</span><span ttm:role="x-roman">he sheng</span></span></p><p begin="2" end="4" ttm:agent="v2"><span begin="2" end="4">对唱</span></p>""", head))
        assertEquals(listOf("123", "456"), doc.metadata.values["ncmMusicId"])
        assertEquals("Alice", doc.metadata.agents.first().name)
        assertEquals("主唱", doc.lines[0].text)
        val bg = doc.lines[0].backgroundVocals.single()
        assertTrue(bg.isBackground); assertTrue(bg.isDuet)
        assertEquals("v2", bg.agent)
        assertEquals(listOf("x-bg"), bg.roles)
        assertEquals(2000L, bg.startTimeMs)
        assertEquals("he sheng", bg.romanization)
        assertTrue(doc.lines[1].isDuet)
    }
    @Test fun headAuxiliaryTracksLinkByItunesKeyAndKeepBackgroundSeparate() {
        val head = """<iTunesMetadata><translations><translation xml:lang="zh-Hans" type="subtitle"><text for="L1">你好<span ttm:role="x-bg">和声译文</span></text></translation></translations><transliterations><transliteration xml:lang="en-Latn"><text for="L1"><span begin="1" end="2">hello</span></text></transliteration></transliterations></iTunesMetadata>"""
        val line = TtmlParser.parse(xml("""<p begin="1" end="3" itunes:key="L1"><span begin="1" end="3">Hello</span><span ttm:role="x-bg"><span begin="2" end="3">Echo</span></span></p>""", head)).lines.single()
        assertEquals("你好", line.translation)
        assertEquals("hello", line.romanization)
        assertEquals("和声译文", line.backgroundVocals.single().translation)
    }
    @Test fun lineOnlyAndPartialTokensNeverFabricateWordTiming() {
        val line = TtmlParser.parse(xml("""<p begin="1" end="3">未提供逐词时间</p>""")).lines.single()
        assertTrue(line.words.isEmpty()); assertEquals("", line.translation)
        val partial = TtmlParser.parse(xml("""<p begin="1" end="3"><span begin="1" end="2">Hi</span> there</p>""")).lines.single()
        assertTrue(partial.words.isEmpty()); assertEquals("Hi there", partial.text)
    }
    @Test fun invalidXmlTimelineEmptyAndUnsupportedFormatsRemainDistinct() {
        fun failure(source: String, code: LyricsErrorCode) {
            try { TtmlParser.parse(source); fail(source) } catch (e: LyricsException) { assertEquals(code, e.code) }
        }
        failure("<tt>", LyricsErrorCode.PARSE_ERROR)
        failure("<html/>", LyricsErrorCode.UNSUPPORTED_FORMAT)
        failure(xml(""), LyricsErrorCode.NO_LYRICS)
        failure(xml("""<p begin="3" end="1">错误</p>"""), LyricsErrorCode.INVALID_TIMELINE)
        failure(xml("""<p begin="0" end="2"><span begin="0" end="3">越界</span></p>"""), LyricsErrorCode.INVALID_TIMELINE)
        failure("""<!DOCTYPE tt [<!ENTITY x SYSTEM "file:///secret">]><tt>&x;</tt>""", LyricsErrorCode.UNSUPPORTED_FORMAT)
    }
    @Test fun aSingleUpstreamFileParsesWithoutBundlingTheDatabase() {
        val raw = javaClass.getResource("/ttml/nod-krai.ttml")!!.readText()
        val doc = TtmlParser.parse(raw)
        assertTrue(doc.lines.size > 10)
        assertTrue(doc.hasWordTiming)
        assertTrue(doc.metadata.values["appleMusicId"]!!.contains("1845853973"))
    }
}
