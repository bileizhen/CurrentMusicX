package io.github.currencortex.music

import androidx.test.core.app.ApplicationProvider
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.AudioSource
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Uses its own cache and synthetic bytes. Never touches the user's queue or audio service. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioCacheTest {
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private lateinit var directory: File
    private lateinit var cache: AudioCache
    private lateinit var server: MockWebServer
    private var payload = ByteArray(320 * 1024) { (it % 251).toByte() }
    private var requests = 0
    private var throttled = false
    private val key get() = AudioCache.key("https://fixture/cm", 7, 1, AudioQuality.STANDARD)
    private fun source(path: String = "/one?signature=old", md5: String = "first") = AudioSource(server.url(path).toString(), "standard", 44100, 2, "mp3", md5)
    @Before fun prepare() {
        directory = File(context.cacheDir, "audio-test-${UUID.randomUUID()}")
        cache = AudioCache(context, directory)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests++
                assertNull(request.getHeader("Token"))
                assertNull(request.getHeader("x-api-key"))
                val match = Regex("bytes=(\\d+)-(\\d*)").find(request.getHeader("Range").orEmpty())
                val start = match?.groupValues?.get(1)?.toInt() ?: 0
                if (start >= payload.size) return MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */${payload.size}")
                val end = match?.groupValues?.get(2)?.toIntOrNull()?.coerceAtMost(payload.lastIndex) ?: payload.lastIndex
                return MockResponse().setHeader("Content-Type", "audio/mpeg")
                    .setBody(Buffer().write(payload, start, end - start + 1)).apply {
                        if (match != null) setResponseCode(206).setHeader("Content-Range", "bytes $start-$end/${payload.size}")
                        if (throttled) throttleBody(4096, 80, TimeUnit.MILLISECONDS)
                    }
            }
        }
        server.start()
    }
    @After fun finish() {
        cache.close(); server.shutdown()
        check(directory.parentFile == context.cacheDir && directory.name.startsWith("audio-test-"))
        directory.deleteRecursively()
    }
    private fun read(key: String?, source: AudioSource, position: Long = 0, length: Long = C.LENGTH_UNSET.toLong()): ByteArray {
        val ds = cache.dataSourceFactory.createDataSource()
        return try {
            ds.open(DataSpec.Builder().setUri(source.url).setKey(key).setPosition(position).setLength(length).build())
            val bytes = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) { val count = ds.read(buffer, 0, buffer.size); if (count == C.RESULT_END_OF_INPUT) break; bytes.write(buffer, 0, count) }
            bytes.toByteArray()
        } finally { ds.close() }
    }
    @Test fun openingBytesArePreloadedReusedAcrossSignedUrlsAndRemainingBytesAreCached() = runBlocking(Dispatchers.IO) {
        val source = source()
        cache.rememberSource(key, source)
        cache.preload(key, source, 64 * 1024L)
        assertEquals(64 * 1024L, cache.cachedBytes(key, payload.size.toLong()))
        assertArrayEquals(payload.copyOfRange(0, 64 * 1024), read(key, source("/one?signature=new"), length = 64 * 1024L))
        assertEquals(1, requests)
        assertArrayEquals(payload, read(key, source))
        assertEquals(2, requests)
        assertNotNull(cache.completeSource(key))
        assertEquals(payload.size.toLong(), cache.cachedBytes(key, payload.size.toLong()))
    }
    @Test fun completeAudioSurvivesReopenAndOfflineResolverButDeniedAccountDoesNotFallback() = runBlocking(Dispatchers.IO) {
        val source = source()
        cache.rememberSource(key, source); assertArrayEquals(payload, read(key, source))
        cache.close(); cache = AudioCache(context, directory)
        var session = RequestSession("https://fixture/cm", "fixture-token")
        val request = AudioRequest(1, AudioQuality.STANDARD, 7, session)
        var denied = false
        val resolver = AudioSourceResolver(cache, { session }) { _, _, _ ->
            if (denied) throw ApiException(ErrorKind.Forbidden) else throw IOException("offline fixture")
        }
        val resolved = resolver.resolve(request)
        assertEquals(key, resolved.key)
        assertTrue(resolved.source.url.startsWith("cache://audio/"))
        assertArrayEquals(payload, read(resolved.key, resolved.source))
        assertEquals(1, requests)
        denied = true; resolver.invalidate()
        try { resolver.resolve(request); fail("Forbidden must not fall back to cache") } catch (e: ApiException) { assertEquals(ErrorKind.Forbidden, e.kind) }
        denied = false; session = RequestSession(session.server, "different-account-token")
        try { resolver.resolve(request); fail("Stale session must not resolve") } catch (e: ApiException) { assertEquals(ErrorKind.Unauthorized, e.kind) }
    }
    @Test fun sourceMemoIsSharedButQualityAccountServerAndRepresentationAreIsolated() = runBlocking(Dispatchers.IO) {
        val session = RequestSession("https://fixture/cm", "fixture-token")
        val request = AudioRequest(1, AudioQuality.STANDARD, 7, session)
        var loads = 0
        var clock = 0L
        val resolver = AudioSourceResolver(cache, { session }, now = { clock }) { _, _, _ -> loads++; source() }
        coroutineScope { (1..3).map { async { resolver.resolve(request) } }.awaitAll() }
        assertEquals(1, loads)
        clock = 20_000_000_000L; resolver.resolve(request); assertEquals(1, loads)
        clock = 40_000_000_000L; resolver.resolve(request); assertEquals(2, loads)
        cache.preload(key, source(), 64 * 1024L)
        assertNotEquals(key, AudioCache.key(session.server, 8, 1, AudioQuality.STANDARD))
        assertNotEquals(key, AudioCache.key("https://other/cm", 7, 1, AudioQuality.STANDARD))
        assertNotEquals(key, AudioCache.key(session.server, 7, 1, AudioQuality.LOSSLESS))
        cache.rememberSource(key, source(md5 = "changed-rendition"))
        assertEquals(0L, cache.cachedBytes(key, payload.size.toLong()))
        read(null, source()); assertEquals(0L, cache.cachedBytes(key, payload.size.toLong()))
    }
    @Test fun leastRecentlyUsedAudioIsEvictedWithinCapacityAndClearRemovesMetadata() = runBlocking(Dispatchers.IO) {
        cache.close(); cache = AudioCache(context, directory, maxBytes = 96 * 1024L)
        cache.rememberSource(key, source()); cache.preload(key, source(), 64 * 1024L)
        val newer = AudioCache.key("https://fixture/cm", 7, 2, AudioQuality.STANDARD)
        cache.rememberSource(newer, source("/two")); cache.preload(newer, source("/two"), 64 * 1024L)
        assertTrue(cache.bytes.value <= 96 * 1024L)
        assertEquals(0L, cache.cachedBytes(key, payload.size.toLong()))
        cache.clear()
        assertEquals(0L, cache.bytes.value); assertNull(cache.cachedSource(newer))
    }
    @Test fun audioProvidersAndKeysCannotReuseEachOthersBytesOrInFlightSources() = runBlocking(Dispatchers.IO) {
        val session = RequestSession("https://fixture/cm", "fixture-token")
        var provider = "leiz:fixture-first"
        val request = AudioRequest(1, AudioQuality.STANDARD, 7, session, provider)
        assertNotEquals(key, request.key)
        cache.rememberSource(key, source()); read(key, source())
        val resolver = AudioSourceResolver(cache, { session }, currentProvider = { provider }) { _, _, _ -> source() }
        val resolved = resolver.resolve(request)
        assertEquals(0L, cache.cachedBytes(resolved.key, payload.size.toLong()))
        cache.preload(resolved.key, resolved.source, 64 * 1024L)
        assertEquals(64 * 1024L, cache.cachedBytes(resolved.key, payload.size.toLong()))
        provider = "leiz:fixture-second"
        val changed = request.copy(providerIdentity = provider)
        assertNotEquals(request.key, changed.key)
        assertEquals(0L, cache.cachedBytes(changed.key, payload.size.toLong()))
        try { resolver.resolve(request); fail("Stale key identity must be rejected even on memo hit") }
        catch (e: ApiException) { assertEquals(ErrorKind.AudioSourceChanged, e.kind) }
        val inFlight = AudioSourceResolver(cache, { session }, currentProvider = { provider }) { _, _, _ ->
            provider = "currentmusic"; source()
        }
        try { inFlight.resolve(changed); fail("Provider change must reject old result") }
        catch (e: ApiException) { assertEquals(ErrorKind.AudioSourceChanged, e.kind) }
        assertNull(cache.cachedSource(changed.key))
    }
    @Test fun cancelledPreloadStopsAndIncompleteAudioCannotResolveOffline() = runBlocking(Dispatchers.IO) {
        throttled = true
        val source = source(); cache.rememberSource(key, source)
        val job = launch { cache.preload(key, source, payload.size.toLong()) }
        while (server.requestCount == 0) delay(10)
        delay(200); withTimeout(3000) { job.cancelAndJoin() }
        assertTrue(cache.cachedBytes(key, payload.size.toLong()) < payload.size)
        val session = RequestSession("https://fixture/cm", "fixture-token")
        val resolver = AudioSourceResolver(cache, { session }) { _, _, _ -> throw IOException("offline") }
        try { resolver.resolve(AudioRequest(1, AudioQuality.STANDARD, 7, session)); fail("Partial audio is not offline playback") }
        catch (_: IOException) { }
    }
    @Test fun realExoPlayerPreparesAndSeeksFullyCachedAudioWithoutNetworkOrPlayingSound() = runBlocking(Dispatchers.IO) {
        val frames = 16000 * 2
        payload = java.nio.ByteBuffer.allocate(44 + frames * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + frames * 2); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(16000); putInt(32000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(frames * 2)
        }.array()
        val source = source("/silent.wav").copy(format = "wav", sampleRate = 16000, channelCount = 1)
        cache.rememberSource(key, source); cache.preload(key, source)
        val offline = cache.completeSource(key)!!
        assertTrue(offline.url.startsWith("cache://audio/"))
        val player = withContext(Dispatchers.Main) {
            androidx.media3.exoplayer.ExoPlayer.Builder(context)
                .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(cache.dataSourceFactory)).build().apply {
                    setMediaItem(MediaItemFactory.create(io.github.currencortex.music.data.song.Song(1, "Silent cache fixture"),
                        offline.url, key))
                    playWhenReady = false; prepare()
                }
        }
        try {
            withTimeout(10000) {
                while (withContext(Dispatchers.Main) { player.playbackState != androidx.media3.common.Player.STATE_READY && player.playerError == null }) delay(30)
            }
            withContext(Dispatchers.Main) {
                assertNull(player.playerError); assertEquals(androidx.media3.common.Player.STATE_READY, player.playbackState)
                assertEquals(2000L, player.duration); assertFalse(player.isPlaying)
                player.seekTo(750)
            }
            delay(100)
            withContext(Dispatchers.Main) { assertEquals(750L, player.currentPosition); assertFalse(player.isPlaying) }
            assertEquals(1, requests)
        } finally { withContext(Dispatchers.Main) { player.release() } }
    }
}
