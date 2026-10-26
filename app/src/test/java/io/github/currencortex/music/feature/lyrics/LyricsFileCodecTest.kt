package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.Base64
import java.util.zip.DeflaterOutputStream

class LyricsFileCodecTest {
    private fun decode(text: String, name: String = "song.lrc") = LyricsFileCodec.decode(text.toByteArray(), name)

    @Test fun foliaSecondsBecomeMillisecondsAndManualOffsetNeverMovesRawTimestamps() {
        val input = """{"format":"folia-lyricdata","version":1,"offsetMs":250,"lyrics":{"isWordByWord":true,"lines":[{"fullText":"你好","startTime":1.125,"endTime":2.5,"words":[{"text":"你","startTime":1.125,"endTime":1.8},{"text":"好","startTime":1.8,"endTime":2.5}],"translation":"Hello","agentId":"v1"}]}}"""
        val result = decode(input, "song.fia")
        assertEquals(250L, result.offsetMs)
        assertEquals(1125L, result.document.lines.single().startTimeMs)
        assertEquals(1800L, result.document.lines.single().words.first().endTimeMs)
        assertEquals("Hello", result.document.lines.single().translation)
        assertEquals("v1", result.document.lines.single().agent)
    }

    @Test fun fiaExtensionRoundTripsFullDuetBackgroundAndAuxiliaryModel() {
        val bg = LyricLine(1400, 2000, "oh", listOf(LyricWord("oh", 1400, 2000, 0, 2)),
            isBackground = true, agent = "v2", roles = listOf("x-bg"))
        val original = LyricsDocument(listOf(LyricLine(1000, 3000, "Hello world", listOf(
            LyricWord("Hello", 1000, 1500, 0, 5), LyricWord("world", 2000, 2900, 6, 11)),
            translation = "你好世界", romanization = "hello", isDuet = true, agent = "v1", roles = listOf("lead"), key = "line1",
            backgroundVocals = listOf(bg), translations = listOf(LyricAuxiliary("Bonjour", "fr")))),
            LyricsMetadata(mapOf("ncmMusicId" to listOf("123")), listOf(LyricAgent("v1", "person", "Singer")), "en", 6000, "TTML"))
        val encoded = LyricsFileCodec.encodeFia(Song(123, "Song"), original, LyricsOrigin("AMLL", "amll"), -150)
        val result = decode(encoded, "song.fia")
        assertEquals(original, result.document)
        assertEquals(-150L, result.offsetMs)
        val publicRows = Json.parseToJsonElement(encoded).jsonObject.getValue("lyrics").jsonObject.getValue("lines").jsonArray
        assertEquals(1.0, publicRows.first().jsonObject.getValue("startTime").jsonPrimitive.double, 0.0)
        assertEquals("Hello world", publicRows.first().jsonObject.getValue("fullText").jsonPrimitive.content)
    }

    @Test fun lineTimedFoliaPlaceholderWordsAreNotPromotedToRealWordTiming() {
        val input = """{"format":"folia-lyricdata","version":1,"lyrics":{"isWordByWord":false,"lines":[{"fullText":"AB","startTime":1,"endTime":2,"words":[{"text":"A","startTime":1,"endTime":1.5},{"text":"B","startTime":1.5,"endTime":2}]}]}}"""
        assertFalse(decode(input, "song.fia").document.hasWordTiming)
        val estimated = LyricsDocument(listOf(LyricLine(1000, 2000, "AB",
            listOf(LyricWord("AB", 1000, 2000, 0, 2)), wordsEstimated = true)))
        assertFalse(decode(LyricsFileCodec.encodeFia(Song(1, "Song"), estimated, null, 0), "song.fia").document.hasWordTiming)
        assertFalse(LyricsFileCodec.encodeLrc(estimated).contains('<'))
    }

    @Test fun foliaAlternateTracksAndExplicitAgentsReachTheExistingRendererFields() {
        val input = """{"format":"folia-lyricdata","version":1,"lyrics":{"isWordByWord":false,"ttml":{"agents":{"v1":{"id":"v1","type":"person"},"v2":{"id":"v2","type":"person"}}},"lines":[{"fullText":"Second","startTime":3,"endTime":4,"agentId":"v2","words":[],"alternateTexts":[{"role":"translation","language":"fr","text":"Deuxième"},{"role":"translation","language":"zh-Hans","text":"第二句"},{"role":"romanization","text":"second"}]},{"fullText":"First","startTime":1,"endTime":2,"agentId":"v1","words":[]}]}}"""
        val document = decode(input, "voices.fia").document
        assertEquals(listOf(1000L, 3000L), document.lines.map { it.startTimeMs })
        assertFalse(document.lines.first().isDuet)
        assertTrue(document.lines.last().isDuet)
        assertEquals("第二句", document.lines.last().translation)
        assertEquals("second", document.lines.last().romanization)
        assertEquals(2, document.lines.last().translations.size)
    }

    @Test fun lrcExportsOnlyOriginalTextRatherThanTurningAuxiliaryTracksIntoMainVoices() {
        val original = LyricsDocument(listOf(LyricLine(1000, 3000, "Original",
            translation = "翻译", romanization = "romanization")))
        val encoded = LyricsFileCodec.encodeLrc(original)
        assertFalse(encoded.contains("翻译")); assertFalse(encoded.contains("romanization"))
        assertEquals(listOf("Original"), decode(encoded).document.lines.map { it.text })
        val simultaneous = decode("[00:01.000]第一声部\n[00:01.000]第二声部").document
        assertEquals(listOf("第一声部", "第二声部"), simultaneous.lines.map { it.text })
    }

    @Test fun actualWordTimingMustBeChronologicalAndStayInsideItsLine() {
        val backwards = LyricsDocument(listOf(LyricLine(1000, 3000, "AB", listOf(
            LyricWord("A", 2000, 2500, 0, 1), LyricWord("B", 1000, 1500, 1, 2)))))
        assertTrue(runCatching { LyricsFileCodec.encodeFia(Song(1, "Song"), backwards, null, 0) }.isFailure)
        val outOfBounds = LyricsDocument(listOf(LyricLine(1000, 3000, "A", listOf(LyricWord("A", 500, 1500, 0, 1)))))
        assertTrue(runCatching { LyricsFileCodec.encodeLrc(outOfBounds) }.isFailure)
        val unordered = LyricsDocument(listOf(LyricLine(2000, 3000, "Later"), LyricLine(1000, 2000, "Earlier")))
        assertTrue(runCatching { LyricsFileCodec.encodeFia(Song(1, "Song"), unordered, null, 0) }.isFailure)
        val badFia = """{"format":"folia-lyricdata","version":1,"lyrics":{"isWordByWord":true,"lines":[{"fullText":"A","startTime":1,"endTime":2,"words":[{"text":"A","startTime":0.5,"endTime":1.5}]}]}}"""
        assertTrue(runCatching { decode(badFia, "bad.fia") }.isFailure)
    }

    @Test fun emptyFiaIsAnExplicitPureMusicChoiceButEmptyTextFileIsRejected() {
        val encoded = LyricsFileCodec.encodeFia(Song(1, "Instrumental"), LyricsDocument(), null, 0)
        assertTrue(decode(encoded, "song.fia").document.lines.isEmpty())
        assertTrue(runCatching { decode("[ti:No lyrics]", "song.lrc") }.isFailure)
    }

    @Test fun unsupportedFiaVersionsMalformedTimelinesAndOversizedFilesAreRejected() {
        val base = """{"format":"folia-lyricdata","version":2,"lyrics":{"lines":[]}}"""
        assertTrue(runCatching { decode(base, "song.fia") }.isFailure)
        assertTrue(runCatching { decode(base.replace("\"version\":2", "\"version\":1").replace("[]", "[{\"fullText\":\"x\"}]"), "song.fia") }.isFailure)
        assertTrue(runCatching { LyricsFileCodec.decode(ByteArray(LyricsFileCodec.MAX_BYTES + 1), "song.lrc") }.isFailure)
    }

    @Test fun utf16BomAndLegacyGbkFilesDecodeWithoutReplacementCharacters() {
        val lrc = "[00:01.00]你好\r\r\n[00:03.00]世界"
        val utf16 = byteArrayOf(0xff.toByte(), 0xfe.toByte()) + lrc.toByteArray(Charsets.UTF_16LE)
        assertEquals(listOf("你好", "世界"), LyricsFileCodec.decode(utf16, "song.lrc").document.lines.map { it.text })
        val gbk = lrc.toByteArray(Charset.forName("GB18030"))
        assertEquals("你好", LyricsFileCodec.decode(gbk, "song.lrc").document.lines.first().text)
    }

    @Test fun enhancedLrcRetainsWordSilenceAndTextSpaces() {
        val original = LyricsDocument(listOf(LyricLine(1000, 3000, "Hello world", listOf(
            LyricWord("Hello", 1000, 1500, 0, 5), LyricWord("world", 2000, 2500, 6, 11)))))
        val encoded = LyricsFileCodec.encodeLrc(original)
        val result = decode(encoded).document.lines.single()
        assertEquals("Hello world", result.text)
        assertEquals(listOf(1000L, 2000L), result.words.map { it.startTimeMs })
        assertEquals(listOf(1500L, 2500L), result.words.map { it.endTimeMs })
    }

    @Test fun existingYrcAndTtmlParsersRemainAvailable() {
        val yrc = decode("[1000,2000](1000,500,0)你(1500,1500,0)好", "song.yrc").document
        assertEquals("你好", yrc.lines.single().text)
        assertEquals(3000L, yrc.lines.single().words.last().endTimeMs)
        val ttml = decode("""<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s">Hello</p></div></body></tt>""", "song.ttml")
        assertEquals(1000L, ttml.document.lines.single().startTimeMs)
    }

    @Test fun vttCueTimesAndEntitiesArePreservedWithoutInventingWordTiming() {
        val result = decode("WEBVTT\n\n1\n00:00:01.250 --> 00:00:03.000 align:center\n<v Singer>Hello &amp; world</v>\n", "song.vtt").document.lines.single()
        assertEquals(1250L, result.startTimeMs)
        assertEquals(3000L, result.endTimeMs)
        assertEquals("Hello & world", result.text)
        assertTrue(result.words.isEmpty())
    }

    @Test fun qrcAcceptsLeadingAndTrailingWordTagsAndXmlContainers() {
        val leading = decode("[1000,2000]你(1000,500)好(1500,1500)", "song.qrc").document.lines.single()
        val trailing = decode("[1000,2000](1000,500)你(1500,1500)好", "song.qrc").document.lines.single()
        assertEquals("你好", leading.text); assertEquals(leading.words, trailing.words)
        val xml = decode("""<QrcInfos><LyricInfo LyricContent="[1000,2000]你(1000,500)好(1500,1500)&#10;"/></QrcInfos>""", "song.qrc")
        assertEquals("你好", xml.document.lines.single().text)
    }

    @Test fun encryptedKrcUsesRelativeWordOffsetsAndEmbeddedAuxiliaryTracks() {
        val language = Base64.getEncoder().encodeToString("""{"content":[{"type":1,"lyricContent":[["Hello"]]},{"type":0,"lyricContent":[["ni ","hao"]]}]}""".toByteArray())
        val bytes = krc("[language:$language]\n[1000,2000]<0,500,0>你<500,1500,0>好")
        val row = LyricsFileCodec.decode(bytes, "download.krc").document.lines.single()
        assertEquals("你好", row.text)
        assertEquals(listOf(1000L, 1500L), row.words.map { it.startTimeMs })
        assertEquals("Hello", row.translation); assertEquals("ni hao", row.romanization)
    }

    @Test fun krcExpansionBombAndTruncatedArchiveAreRejected() {
        assertTrue(runCatching { LyricsFileCodec.decode(krc("x".repeat(LyricsFileCodec.MAX_BYTES + 1)), "song.krc") }.isFailure)
        assertTrue(runCatching { LyricsFileCodec.decode(byteArrayOf(107, 114, 99, 49, 0), "song.krc") }.isFailure)
    }

    private fun krc(text: String): ByteArray {
        val compressed = ByteArrayOutputStream().also { buffer -> DeflaterOutputStream(buffer).use { it.write(text.toByteArray()) } }.toByteArray()
        val key = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
        return byteArrayOf(107, 114, 99, 49) + ByteArray(compressed.size) { (compressed[it].toInt() xor key[it % key.size]).toByte() }
    }
}
