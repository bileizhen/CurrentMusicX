package io.github.currencortex.music.core.download

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricWord
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SongDownloadMetadataTest {
    @Test fun lrcRetainsTimingTranslationAndUnicodeWithoutPlaybackOffset() {
        val song = Song(88, "海边\n夜晚", "歌手", "专辑")
        val document = LyricsDocument(listOf(LyricLine(62345, 70000, "星光", translation = "Starlight"),
            LyricLine(0, 1000, "开始")))
        val lrc = DownloadLyrics.lrc(song, document)
        assertTrue(lrc.startsWith("[ti:海边 夜晚]\n[ar:歌手]\n[al:专辑]"))
        assertTrue(lrc.indexOf("[00:00.000]开始") < lrc.indexOf("[01:02.345]星光"))
        assertTrue(lrc.contains("[01:02.345]Starlight"))
        assertEquals("", DownloadLyrics.lrc(song, LyricsDocument()))
    }
    @Test fun lrcExportsWordTimingAsEnhancedLrcWithFallback() {
        val song = Song(88, "逐字", "歌手", "专辑")
        val yrc = DownloadLyrics.lrc(song, LyricsParser.yrc("[1000,2000](1000,300,0)你好(1300,700,0)世界\n[4000,1000](4000,1000,0)再见"))
        assertTrue(yrc.contains("[00:01.000]<00:01.000>你好<00:01.300>世界"))
        assertTrue(yrc.contains("[00:04.000]<00:04.000>再见"))
        val translated = LyricsDocument(listOf(LyricLine(1000, 2000, "你好，音乐",
            words = listOf(LyricWord("你好", 1000, 1300, 0, 2), LyricWord("，音乐", 1300, 2000, 2, 5)),
            translation = "Hello")))
        val out = DownloadLyrics.lrc(song, translated)
        assertTrue(out.contains("[00:01.000]<00:01.000>你好<00:01.300>，音乐"))
        assertTrue(out.contains("[00:01.000]Hello"))
        // Word offsets that no longer match the laid-out line fall back to the plain timeline.
        val broken = LyricsDocument(listOf(LyricLine(1000, 2000, "你好", words = listOf(LyricWord("你好", 1000, 2000, 5, 9)))))
        assertTrue(DownloadLyrics.lrc(song, broken).contains("[00:01.000]你好"))
    }
    @Test fun exportedYrcRoundTripsEveryWordIncludingSingleAndFinalWords() {
        val source = LyricsParser.yrc("[1000,2000](1000,300,0)你好(1300,700,0)世界\n[4000,1000](4000,1000,0)再见")
        val restored = LyricsParser.lrc(DownloadLyrics.lrc(Song(88, "逐字"), source))
        assertEquals(source.lines, restored.lines)
    }
    @Test fun enhancedLrcKeepsExactEndsPausesWhitespaceAndUnicodeOffsets() {
        val source = LyricsDocument(listOf(LyricLine(1000, 6000, "Hello 世界🌙 またね",
            words = listOf(LyricWord("Hello", 1100, 1500, 0, 5),
                LyricWord("世界🌙", 2200, 3100, 6, 10), LyricWord("またね", 3200, 5000, 11, 14)))))
        val restored = LyricsParser.lrc(DownloadLyrics.lrc(Song(88, "逐字"), source))
        assertEquals(source.lines, restored.lines)
    }
    @Test fun translatedExportNeverCreatesZeroDurationPrimaryLine() {
        val source = LyricsDocument(listOf(LyricLine(1000, 2000, "你好", translation = "Hello",
            words = listOf(LyricWord("你好", 1100, 1500, 0, 2))), LyricLine(3000, 5000, "下一句")))
        val restored = LyricsParser.lrc(DownloadLyrics.lrc(Song(88, "逐字"), source))
        assertEquals(source.lines.first().words, restored.lines.first().words)
        assertEquals(2000L, restored.lines.first().endTimeMs)
        assertTrue(restored.lines.all { it.endTimeMs > it.startTimeMs })
    }
    @Test fun upstreamTtmlWordTimingSurvivesExportAndParsing() {
        val source = io.github.currencortex.music.feature.lyrics.ttml.TtmlParser.parse(
            javaClass.getResource("/ttml/nod-krai.ttml")!!.readText())
        val restored = LyricsParser.lrc(DownloadLyrics.lrc(Song(88, "TTML"), source))
        val rows = source.lines.flatMap { listOf(it) + it.backgroundVocals }.filter { it.words.isNotEmpty() }
        assertTrue(rows.isNotEmpty())
        rows.forEach { row ->
            val copy = restored.lines.first { it.startTimeMs == row.startTimeMs && it.text == row.text }
            assertEquals(row.words.map { listOf(it.text, it.startTimeMs, it.endTimeMs, it.startOffset, it.endOffset) },
                copy.words.map { listOf(it.text, it.startTimeMs, it.endTimeMs, it.startOffset, it.endOffset) })
            assertEquals(row.endTimeMs, copy.endTimeMs)
        }
    }
    @Test fun invalidWordDataFallsBackAsAWholeWithoutDroppingText() {
        val badWords = listOf(
            listOf(LyricWord("你", 1000, 1300, 0, 1), LyricWord("好", 1300, 1500, 8, 9)),
            listOf(LyricWord("错误", 1000, 1300, 0, 1), LyricWord("好", 1300, 1500, 1, 2)),
            listOf(LyricWord("你好", 1500, 1000, 0, 2)),
            listOf(LyricWord("你", 1000, 1300, 0, 1)))
        badWords.forEach { words ->
            val source = LyricsDocument(listOf(LyricLine(1000, 2000, "你好", words)))
            val out = DownloadLyrics.lrc(Song(88, "逐字"), source)
            assertTrue(out.contains("[00:01.000]你好\n"))
            assertFalse(out.contains('<'))
        }
    }
    @Test fun filenameDoesNotAllowTraversalOrInvalidCharacters() {
        val name = SongDownloadNames.base(Song(88, "../歌曲:*?\n", "a/b"))
        assertFalse(name.contains('/')); assertFalse(name.contains('\n')); assertFalse(name.contains(':'))
        assertTrue(name.endsWith("[88]"))
        assertTrue(SongDownloadNames.base(Song(99, "a".repeat(300))).length < 100)
    }
    @Test fun containerSignatureWinsOverServerFormatAndRejectsHtml() {
        val file = File.createTempFile("audio-container", ".bin")
        try {
            for ((bytes, expected) in listOf("fLaC0000".toByteArray() to "flac",
                "OggSvorbis".toByteArray() to "ogg", "RIFF0000WAVE".toByteArray() to "wav",
                "0000ftypM4A " .toByteArray() to "m4a", "ID3hello".toByteArray() to "mp3",
                byteArrayOf(0xff.toByte(), 0xf1.toByte()) to "aac", byteArrayOf(0xff.toByte(), 0xfb.toByte()) to "mp3")) {
                file.writeBytes(bytes); assertEquals(expected, AudioDownloadFormat.detect(file))
            }
            file.writeText("<!doctype html><html>Error</html>")
            assertThrows(java.io.IOException::class.java) { AudioDownloadFormat.detect(file) }
            file.writeText("OggS000000OpusHead")
            assertThrows(java.io.IOException::class.java) { AudioDownloadFormat.detect(file) }
            file.writeBytes(byteArrayOf())
            assertThrows(java.io.IOException::class.java) { AudioDownloadFormat.detect(file) }
        } finally { file.delete() }
    }
}
