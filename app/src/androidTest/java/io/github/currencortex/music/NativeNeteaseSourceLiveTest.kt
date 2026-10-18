package io.github.currencortex.music

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.data.song.MusicRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Explicit read-only live probe. No production preference, queue or playback changes. */
class NativeNeteaseSourceLiveTest {
    @Test fun musicResolvesWithAnUnreachableProjectServer(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("nativeSourceLive") == "true")
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = app.container
        container.ready.await(); container.neteaseSessions.ready.await()
        val api = ApiClient({ "http://127.0.0.1:1/cm/" }, { null }, {},
            netease = requireNotNull(container.nativeNetease), nativeMedia = { true })
        val repository = MusicRepository(api, { AudioSourceAccess(AudioProvider.NETEASE) })
        val report = mutableListOf<JsonObject>()
        val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS).build()
        val search = repository.search("海阔天空")
        assertTrue("Search must succeed without the project server", search is AppResult.Success && search.value.songs.isNotEmpty())
        val song = repository.detail(347230)
        val lyrics = repository.lyricsDocument(song)
        assertTrue("Lyrics must succeed without the project server", lyrics is AppResult.Success && lyrics.value.lines.isNotEmpty())
        val library = io.github.currencortex.music.data.library.LibraryRepository(api, { 0L }) {
            RequestSession("http://127.0.0.1:1/cm/", null, container.neteaseSessions.state.value.revision)
        }
        assertTrue(library.daily().songs.isNotEmpty())
        val mv = if (song.mv > 0) library.mvSource(song.mv).toHttpUrl().host else "not-present"
        report += buildJsonObject {
            put("kind", "metadata"); put("search", "success"); put("lyrics", "success")
            put("recommendations", "success"); put("mvHost", mv)
        }
        for (quality in listOf(AudioQuality.STANDARD, AudioQuality.AUTO)) {
            val owner = RequestSession("https://music.163.com/", null, container.neteaseSessions.state.value.revision)
            val source = repository.source(song.id, quality, owner)
            val url = source.url.toHttpUrl()
            withContext(Dispatchers.IO) {
                http.newCall(Request.Builder().url(url).header("Range", "bytes=0-65535").build()).execute().use { response ->
                    assertTrue("Native CDN must return actual audio bytes", response.isSuccessful)
                    val input = requireNotNull(response.body).byteStream()
                    val buffer = ByteArray(8192)
                    var count = 0
                    var signature = ""
                    while (count < 65536) {
                        val size = input.read(buffer, 0, minOf(buffer.size, 65536 - count))
                        if (size < 0) break
                        if (count == 0 && size >= 4) signature = when {
                            buffer.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "fLaC" -> "FLAC"
                            buffer.copyOfRange(0, 3).toString(Charsets.US_ASCII) == "ID3" -> "MP3"
                            (buffer[0].toInt() and 0xff) == 0xff && (buffer[1].toInt() and 0xe0) == 0xe0 -> "MPEG audio"
                            else -> ""
                        }
                        count += size
                    }
                    assertTrue(count > 0)
                    assertTrue("CDN body must begin with an audio signature", signature.isNotEmpty())
                    report += buildJsonObject {
                        put("requested", quality.value); put("returned", source.level)
                        put("audioSignature", signature)
                        put("cdnHost", response.request.url.host); put("bytes", count)
                        put("status", response.code); put("projectServer", "unreachable")
                    }
                }
            }
        }
        File(app.getExternalFilesDir(null), "native-source-live.json").writeText(JsonArray(report).toString())
    }
}
