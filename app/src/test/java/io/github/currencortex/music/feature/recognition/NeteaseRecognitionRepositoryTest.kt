package io.github.currencortex.music.feature.recognition

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class NeteaseRecognitionRepositoryTest {
    private val result = """{"code":200,"data":{"result":[{"song":{"id":7,"name":"Identified","artists":[{"id":9,"name":"Singer"}],"album":{"name":"Album","picUrl":"http://p1.music.126.net/cover.jpg"},"duration":200000,"mvid":8}},{"song":{"id":7,"name":"Duplicate"}},{"song":{"id":0,"name":"Invalid"}}]}}"""

    @Test fun lyricRequestUsesAnonymousAacProtocolAndTheSameSession(): Unit = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(result)); server.start()
            val parent = OkHttpClient.Builder().addInterceptor {
                it.proceed(it.request().newBuilder().header("Cookie", "private=account").build())
            }.build()
            val audio = ByteArray(12) { it.toByte() }
            val songs = NeteaseRecognitionRepository(parent, server.url("/").toString()).recognizeLyrics(audio, "shared-session")
            assertEquals(7L, songs.single().id)
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("/api/music/hum/audio/unifymatch", request.path)
            val params = "https://fixture/?${request.body.readUtf8()}".toHttpUrl()
            assertEquals("shared-session", params.queryParameter("sessionId"))
            assertEquals("ASR", params.queryParameter("algorithmCode"))
            assertEquals("1.0.0", params.queryParameter("algorithmVer"))
            assertEquals("AAC", params.queryParameter("recordAudioFormat"))
            assertEquals("app", params.queryParameter("from"))
            assertEquals("1", params.queryParameter("decrypt"))
            assertArrayEquals(audio, java.util.Base64.getDecoder().decode(params.queryParameter("recordAudio")))
            assertNotNull(params.queryParameter("queryId"))
            assertNull(params.queryParameter("rawdata")); assertNull(params.queryParameter("fingerPrint"))
            assertNull(request.getHeader("Cookie")); assertNull(request.getHeader("Authorization"))
        } finally { server.shutdown() }
    }

    @Test fun publicRequestPostsFingerprintAndActualDurationAndParsesOfficialSongShapeWithoutCredentials(): Unit = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(result)); server.start()
            val parent = OkHttpClient.Builder().addInterceptor {
                it.proceed(it.request().newBuilder().header("Cookie", "private=account").header("Authorization", "private").build())
            }.build()
            val songs = NeteaseRecognitionRepository(parent, server.url("/").toString()).recognize("YWJjZA==", 6.0)
            assertEquals(7L, songs.single().id); assertEquals("Singer", songs.single().artists)
            assertEquals(listOf(9L), songs.single().artistIds); assertEquals(200000L, songs.single().durationMs)
            assertEquals(8L, songs.single().mv); assertTrue(songs.single().cover.startsWith("https://"))
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("POST", request.method); assertEquals("/api/music/audio/match", request.path)
            val params = "https://fixture/?${request.body.readUtf8()}".toHttpUrl()
            assertEquals("YWJjZA==", params.queryParameter("rawdata")); assertEquals("6.0", params.queryParameter("duration"))
            assertEquals("shazam_v2", params.queryParameter("algorithmCode")); assertEquals("1", params.queryParameter("decrypt"))
            assertNotNull(params.queryParameter("sessionId"))
            assertNull(request.getHeader("Cookie")); assertNull(request.getHeader("Authorization"))
        } finally { server.shutdown() }
    }

    @Test fun emptyMatchesAreSuccessfulButServiceRefusalsAndMalformedResponsesAreErrors(): Unit = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"code":200,"data":{"result":[]}}"""))
            server.enqueue(MockResponse().setBody("""{"code":429}"""))
            server.enqueue(MockResponse().setBody("{}")); server.start()
            val repository = NeteaseRecognitionRepository(baseUrl = server.url("/").toString())
            assertTrue(repository.recognize("YWJjZA==", 3.0).isEmpty())
            assertTrue(runCatching { repository.recognize("YWJjZA==", 3.0) }.exceptionOrNull()!!.message!!.contains("429"))
            assertTrue(runCatching { repository.recognize("YWJjZA==", 3.0) }.isFailure)
        } finally { server.shutdown() }
    }

    @Test fun invalidClipsNeverStartRequests(): Unit = runBlocking {
        val server = MockWebServer()
        try {
            server.start(); val repository = NeteaseRecognitionRepository(baseUrl = server.url("/").toString())
            assertTrue(runCatching { repository.recognize("invalid*", 6.0) }.isFailure)
            assertTrue(runCatching { repository.recognize("YWJjZA==", 1.9) }.isFailure)
            assertTrue(runCatching { repository.recognize("YWJjZA==", 15.1) }.isFailure)
            assertEquals(0, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun cancellationDuringSlowResponseDoesNotReturnSongs(): Unit = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(result).setBodyDelay(2, TimeUnit.SECONDS)); server.start()
            var completed = false
            val job = launch(Dispatchers.Default) { NeteaseRecognitionRepository(baseUrl = server.url("/").toString()).recognize("YWJjZA==", 6.0); completed = true }
            assertNotNull(server.takeRequest(1, TimeUnit.SECONDS)); job.cancelAndJoin()
            assertFalse(completed)
        } finally { server.shutdown() }
    }

    @Test fun successiveWindowsKeepSessionAndAdvanceRoundIncludingTwoAndFifteenSeconds(): Unit = runBlocking {
        val server = MockWebServer()
        try {
            repeat(2) { server.enqueue(MockResponse().setBody(result)) }; server.start()
            val repository = NeteaseRecognitionRepository(baseUrl = server.url("/").toString())
            repository.recognize("YWJjZA==", 2.0, "same-session", 1)
            repository.recognize("YWJjZA==", 15.0, "same-session", 2)
            for ((index, duration) in listOf("2.0", "15.0").withIndex()) {
                val request = server.takeRequest(1, TimeUnit.SECONDS)!!
                val params = "https://fixture/?${request.body.readUtf8()}".toHttpUrl()
                assertEquals("same-session", params.queryParameter("sessionId"))
                assertEquals((index + 1).toString(), params.queryParameter("times"))
                assertEquals(duration, params.queryParameter("duration"))
            }
        } finally { server.shutdown() }
    }
}
