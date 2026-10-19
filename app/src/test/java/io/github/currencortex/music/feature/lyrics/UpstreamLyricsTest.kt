package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.MusicRepository
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricWord
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class UpstreamLyricsTest {
    @Test fun oldCachedEstimatesLoseOnlySyntheticTimingIncludingBackgroundVocals() {
        val words = listOf(LyricWord("夜に", 1000, 2000, 0, 2))
        val estimated = LyricLine(1000, 2000, "夜に", words, "夜晚", "yo ni", wordsEstimated = true)
        val genuine = estimated.copy(wordsEstimated = false, backgroundVocals = listOf(estimated))
        val cleaned = LyricsDocument(listOf(estimated, genuine)).withoutEstimatedTiming()
        assertTrue(cleaned.lines.first().words.isEmpty())
        assertEquals("夜に", cleaned.lines.first().text)
        assertEquals("夜晚", cleaned.lines.first().translation)
        assertEquals("yo ni", cleaned.lines.first().romanization)
        assertEquals(words, cleaned.lines.last().words)
        assertTrue(cleaned.lines.last().backgroundVocals.single().words.isEmpty())
        assertEquals(cleaned, cleaned.withoutEstimatedTiming())
    }
    private val json = """{"isYrc":false,"lines":[{"t":1000,"txt":"夜に","w":[[1000,"夜"],[1400,"に"]],"wSynth":true,"roma":"yo ni"},{"t":12000,"txt":"次"}]}"""
    @Test fun estimatedTupleTimingIsIgnoredWhileTextAndRomanizationRemain() {
        val line = LyricsParser.parse(ApiJson.parseToJsonElement(json)).lines.first()
        assertFalse(line.wordsEstimated)
        assertTrue(line.words.isEmpty())
        assertEquals("夜に", line.text)
        assertEquals("yo ni", line.romanization)
    }
    @Test fun malformedOrIncompleteTupleTimingFallsBackToWholeLine() {
        for (axis in listOf("[[1000,\"夜\"]]", "[[1400,\"夜\"],[1000,\"に\"]]")) {
            val line = LyricsParser.parse(ApiJson.parseToJsonElement("""{"lines":[{"t":1000,"txt":"夜に","w":$axis}]}""")).lines.single()
            assertTrue(line.words.isEmpty()); assertEquals("夜に", line.text)
        }
    }
    @Test fun plainNativeLyricsDoNotRequestNormalizedEstimatedAxis() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"lrc":{"lyric":"[00:01.00]夜に"}}"""))
            server.enqueue(MockResponse().setBody(json))
            val repository = MusicRepository(ApiClient({ server.url("/cm/").toString() }, { null }, {}))
            val result = repository.lyricsDocument(9) as AppResult.Success
            assertFalse(result.value.hasWordTiming)
            assertEquals("夜に", result.value.lines.single().text)
            assertEquals(1, server.requestCount)
            assertTrue(server.takeRequest().path!!.startsWith("/cm/ncm/lyric/new"))
        }
    }
}
