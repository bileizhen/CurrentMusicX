package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.MusicRepository
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class UpstreamLyricsTest {
    private val json = """{"isYrc":false,"lines":[{"t":1000,"txt":"夜に","w":[[1000,"夜"],[1400,"に"]],"wSynth":true,"roma":"yo ni"},{"t":12000,"txt":"次"}]}"""
    @Test fun estimatedTupleTimingKeepsOffsetsAndDoesNotStretchAcrossLongSilence() {
        val line = LyricsParser.parse(ApiJson.parseToJsonElement(json)).lines.first()
        assertTrue(line.wordsEstimated)
        assertEquals(listOf("夜", "に"), line.words.map { it.text })
        assertEquals(9000L, line.words.last().endTimeMs)
        assertEquals(2, line.words.last().endOffset)
        assertEquals("yo ni", line.romanization)
    }
    @Test fun malformedOrIncompleteTupleTimingFallsBackToWholeLine() {
        for (axis in listOf("[[1000,\"夜\"]]", "[[1400,\"夜\"],[1000,\"に\"]]")) {
            val line = LyricsParser.parse(ApiJson.parseToJsonElement("""{"lines":[{"t":1000,"txt":"夜に","w":$axis}]}""")).lines.single()
            assertTrue(line.words.isEmpty()); assertEquals("夜に", line.text)
        }
    }
    @Test fun plainRawLyricsStillRequestNormalizedEstimatedAxis() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"lrc":{"lyric":"[00:01.00]夜に"}}"""))
            server.enqueue(MockResponse().setBody(json))
            val repository = MusicRepository(ApiClient({ server.url("/cm/").toString() }, { null }, {}))
            val result = repository.lyricsDocument(9) as AppResult.Success
            assertTrue(result.value.hasWordTiming)
            assertEquals(2, server.requestCount)
            assertTrue(server.takeRequest().path!!.startsWith("/cm/ncm/lyric/new"))
            assertTrue(server.takeRequest().path!!.startsWith("/cm/ncm/lyric?"))
        }
    }
}
