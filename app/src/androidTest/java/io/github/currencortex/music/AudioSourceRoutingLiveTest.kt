package io.github.currencortex.music

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.data.settings.AudioProvider
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Explicit diagnostic: real resolver plus at most 64 KiB per CDN; no playback, queue or preference edits. */
class AudioSourceRoutingLiveTest {
    @Test fun currentProviderResolvesAndPhoneDownloadsDirectlyFromItsReturnedCdn() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("sourceRouting") == "true")
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName == "com.bileizhen.currentmusic")
        val foreground = ActivityScenario.launch(MainActivity::class.java)
        val report = mutableListOf<JsonObject>()
        val evidence = File(app.getExternalFilesDir(null), "audio-source-routing.txt")
        try {
            val container = app.container
            container.ready.await(); container.audioSettings.ready.await()
            val provider = container.audioSettings.access().provider
            val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()
            for (quality in listOf(AudioQuality.STANDARD, AudioQuality.AUTO)) {
                val start = android.os.SystemClock.elapsedRealtime()
                val source = container.musicRepository.source(347230, quality, container.musicSession())
                val resolvedMs = android.os.SystemClock.elapsedRealtime() - start
                val url = source.url.toHttpUrl()
                withContext(Dispatchers.IO) {
                    val t = android.os.SystemClock.elapsedRealtime()
                    http.newCall(Request.Builder().url(url).header("Range", "bytes=0-65535").build()).execute().use { response ->
                        assertTrue("CDN request must succeed", response.isSuccessful)
                        val stream = requireNotNull(response.body).byteStream()
                        val first = stream.read()
                        val firstByteMs = android.os.SystemClock.elapsedRealtime() - t
                        assertTrue("The returned CDN must produce actual audio bytes", first >= 0)
                        val scratch = ByteArray(8192)
                        var count = 1
                        while (count < 65536) {
                            val read = stream.read(scratch, 0, minOf(scratch.size, 65536 - count))
                            if (read < 0) break
                            count += read
                        }
                        report += buildJsonObject {
                            put("provider", provider.name); put("requestedLevel", quality.value)
                            put("returnedLevel", source.level); put("scheme", url.scheme)
                            put("cdnHost", url.host); put("finalCdnHost", response.request.url.host)
                            put("httpStatus", response.code); put("bytes", count)
                            put("resolveMs", resolvedMs); put("cdnFirstByteMs", firstByteMs)
                            put("directNeteaseCdn", url.host.endsWith(".music.126.net"))
                        }
                        evidence.writeText(JsonArray(report).toString())
                    }
                }
            }
            if (provider == AudioProvider.CURRENT_MUSIC)
                assertTrue(report.all { it["directNeteaseCdn"]!!.jsonPrimitive.boolean })
        } finally { foreground.close() }
    }
}
