package io.github.currencortex.music

import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.ApiClient
import io.github.currencortex.music.core.security.SecureTokenStore
import io.github.currencortex.music.data.lx.*
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.data.song.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Every HTTP operation uses a localhost fixture. Never imports or plays third-party catalog audio. */
class LxSourceRuntimeTest {
    // Keep the test process visible; OEM cached-process freezing otherwise suspends wall-clock timers.
    @get:org.junit.Rule val compose = androidx.compose.ui.test.junit4.createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private val header = "/**\n * @name Local fixture\n * @version 1.0\n * @author Tests\n */\n"
    private val init = """lx.send(lx.EVENT_NAMES.inited,{sources:{wy:{type:'music',actions:['musicUrl'],qualitys:['128k','320k','flac']}}});"""
    private fun info(script: String) = lxScriptInfo(script)
    private fun track() = lxTrackRequest(Song(42, "Fixture", "Tester"), AudioQuality.STANDARD,
        listOf(LxCapability("wy", listOf("128k")))).data

    @Test fun healthChecksAudioBytesCachesResultAndRemovalDropsBadgeState() = runBlocking {
        MockWebServer().use { server ->
            val http = LxHttpClient(true)
            val vault = SecureTokenStore(context, ".lx-health-${UUID.randomUUID()}" )
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val store = LxSourceStore(context, vault, scope, LxRuntime(context, http), http)
            val script = header + "lx.on('request', async ({source}) => { if(source !== 'wy') throw Error('platform'); return '${server.url("/probe.wav")}'; });" + init
            try {
                val source = store.importScript(script)
                assertNull(store.health.value[source.id])
                server.enqueue(MockResponse().setBody(Buffer().write(wav())))
                store.checkAvailability(source.id)
                val result = store.health.value.getValue(source.id)
                assertEquals(io.github.currencortex.music.core.network.SourceAvailabilityStatus.AVAILABLE, result.status)
                store.checkAvailability(source.id)
                assertEquals(result, store.health.value[source.id])
                assertEquals(1, server.requestCount)
                assertEquals("bytes=0-4095", server.takeRequest().getHeader("Range"))
                store.remove(source.id)
                assertNull(store.health.value[source.id])
            } finally { store.close(); scope.cancel(); vault.clear() }
        }
    }

    @Test fun initializedDoesNotDiscardConfigurationNeededByTheRequestHandler() = runBlocking {
        MockWebServer().use { server ->
            val http = LxHttpClient(true)
            val runtime = LxRuntime(context, http)
            // Declaring support is independent of the async bootstrap used to resolve URLs.
            val script = header + """
                $init
                const configuration = new Promise((resolve, reject) => lx.request('${server.url("/config")}', {},
                    (error, response) => error ? reject(error) : resolve(response.body)));
                lx.on('request', async ({source}) => {
                    if (source !== 'wy') throw Error('Wrong platform');
                    return (await configuration).url;
                });
            """.trimIndent()
            server.enqueue(MockResponse().setBodyDelay(150, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setBody("""{"url":"https://audio.example.com/initialized.mp3"}"""))
            try {
                runtime.inspect(info(script), script)
                assertEquals("https://audio.example.com/initialized.mp3", withTimeout(5000) {
                    runtime.resolve(info(script), script, track())
                })
                assertEquals("/config", server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS)?.path)
                assertEquals(1, server.requestCount)
            } finally { runtime.close() }
        }
    }

    @Test fun cancellingPageDetectionRestoresPreviousBadgeAndCancelsTheRequest() = runBlocking {
        MockWebServer().use { server ->
            val http = LxHttpClient(true)
            val vault = SecureTokenStore(context, ".lx-cancel-health-${UUID.randomUUID()}" )
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val store = LxSourceStore(context, vault, scope, LxRuntime(context, http), http)
            try {
                val script = header + "lx.on('request', async () => '${server.url("/slow.wav")}');" + init
                val source = store.importScript(script)
                server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
                val job = launch { store.checkAvailability(source.id) }
                withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)) }
                assertEquals(io.github.currencortex.music.core.network.SourceAvailabilityStatus.CHECKING, store.health.value[source.id]?.status)
                job.cancelAndJoin()
                assertEquals(io.github.currencortex.music.core.network.SourceAvailabilityStatus.UNKNOWN, store.health.value[source.id]?.status)
            } finally { store.close(); scope.cancel(); vault.clear() }
        }
    }

    @Test fun savedMultiPlatformDeclarationsExposeOnlyNeteaseWithoutReimport() = runBlocking {
        val script = header + "lx.on('request', async () => 'https://audio.example.com/wy.mp3');\n" + init
        val old = LxStoredSource(info(script).copy(capabilities = listOf(
            LxCapability("wy", listOf("128k")), LxCapability("tx", listOf("flac")))), script)
        val encoded = Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(LxStoredSource.serializer()), listOf(old))
        val vault = object : io.github.currencortex.music.core.security.TokenStore {
            var value: String? = encoded
            override fun read() = value
            override fun write(token: String) { value = token }
            override fun clear() { value = null }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = LxSourceStore(context, vault, scope)
        try {
            store.ready.await()
            assertEquals(listOf(LxCapability("wy", listOf("128k"))), store.state.value.single().capabilities)
            assertEquals(old.info.id, store.state.value.single().id)
            assertEquals("https://audio.example.com/wy.mp3", store.source(Song(42, "Fixture"), AudioQuality.STANDARD, old.info.id).url)
        } finally { store.close(); scope.cancel() }
    }

    @Test fun asyncHttpCryptoCompressionAndExactMetadataWorkInTheSandbox() = runBlocking {
        MockWebServer().use { server ->
            val http = LxHttpClient(true)
            val runtime = LxRuntime(context, http)
            val key = "0123456789abcdef".toByteArray()
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(key))
            }
            val aes = cipher.doFinal("fixture".toByteArray()).joinToString("") { "%02x".format(it) }
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
            val pem = "-----BEGIN PUBLIC KEY-----\n${Base64.getEncoder().encodeToString(pair.public.encoded)}\n-----END PUBLIC KEY-----"
            val rsa = Cipher.getInstance("RSA/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, pair.public) }
                .doFinal(ByteArray(128).apply { this[127] = 42 }).joinToString("") { "%02x".format(it) }
            val script = header + """
                const {buffer:b,crypto:c,zlib:z} = lx.utils;
                if (lx.env !== 'mobile' || lx.currentScriptInfo.rawScript.indexOf('@name Local fixture') < 0) throw Error('metadata');
                fixtureImplicitGlobal = 'browser-script';
                lx.on(lx.EVENT_NAMES.request, async ({source,action,info}) => {
                  if (source !== 'wy' || action !== 'musicUrl' || info.musicInfo.songmid !== '42') throw Error('request');
                  if (c.md5('abc') !== '900150983cd24fb0d6963f7d28e17f72') throw Error('md5');
                  if (b.bufToString(c.aesEncrypt(b.from('fixture'),'aes-128-cbc',b.from('0123456789abcdef'),b.from('0123456789abcdef')),'hex') !== '$aes') throw Error('aes');
                  if (b.bufToString(c.rsaEncrypt(b.from('2a','hex'),${JsonPrimitive(pem)}),'hex') !== '$rsa') throw Error('rsa');
                  if (c.randomBytes(16).length !== 16) throw Error('random');
                  const compressed = await z.deflate(b.from('中文 fixture'));
                  if (b.bufToString(await z.inflate(compressed)) !== '中文 fixture') throw Error('zlib');
                  await new Promise(resolve => setTimeout(resolve,30));
                  return new Promise((resolve,reject) => lx.request('${server.url("/resolve")}',{method:'POST',form:{id:info.musicInfo.songmid}},(error,response,body) => {
                    if(error) return reject(error);
                    if(response.statusCode !== 200 || !response.raw.length) return reject(Error('response'));
                    resolve(body.url);
                  }));
                });
                $init
            """.trimIndent()
            server.enqueue(MockResponse().setBody("""{"url":"https://audio.example.com/fixture.mp3"}"""))
            try {
                assertEquals(listOf(LxCapability("wy", listOf("128k", "320k", "flac"))), runtime.inspect(info(script), script))
                assertEquals("https://audio.example.com/fixture.mp3", runtime.resolve(info(script), script, track()))
                val request = server.takeRequest()
                assertEquals("id=42", request.body.readUtf8()); assertNull(request.getHeader("Cookie")); assertNull(request.getHeader("Authorization"))
            } finally { runtime.close() }
        }
    }

    @Test fun urlImportEncryptedRestoreRepositoryRoutingAndDecoder() = runBlocking {
        MockWebServer().use { server ->
            val http = LxHttpClient(true)
            val vault = SecureTokenStore(context, ".lx-fixture-${UUID.randomUUID()}")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            var store = LxSourceStore(context, vault, scope, LxRuntime(context, http), http)
            val script = header + "lx.on('request', async () => '${server.url("/audio.wav")}');\n" + init
            server.enqueue(MockResponse().setBody(script))
            var access = AudioSourceAccess(AudioProvider.LX)
            try {
                val imported = store.importUrl(server.url("/fixture.js").toString())
                assertEquals("Local fixture", imported.name)
                assertEquals("/fixture.js", server.takeRequest().path)
                store.close()
                store = LxSourceStore(context, vault, scope, LxRuntime(context, http), http)
                store.ready.await(); assertEquals(imported, store.state.value.single())
                access = AudioSourceAccess(AudioProvider.LX, lxSourceId = imported.id)
                val repository = MusicRepository(ApiClient({ error("Project server must not be used") }, { "project-secret" }, {}),
                    { access }, lx = store)
                val source = repository.source(Song(42, "Local fixture"), AudioQuality.AUTO)
                assertEquals("lossless", source.level)
                server.enqueue(MockResponse().setHeader("Content-Type", "audio/wav").setBody(Buffer().write(wav())))
                val player = withContext(Dispatchers.Main) {
                    androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                        setMediaItem(androidx.media3.common.MediaItem.fromUri(source.url)); volume = 0f; prepare()
                    }
                }
                try {
                    withTimeout(10000) { while (withContext(Dispatchers.Main) {
                        player.playbackState != androidx.media3.common.Player.STATE_READY && player.playerError == null
                    }) delay(30) }
                    withContext(Dispatchers.Main) {
                        assertNull(player.playerError); assertEquals(2000L, player.duration)
                        player.seekTo(750)
                    }
                    delay(100)
                    withContext(Dispatchers.Main) { assertEquals(750L, player.currentPosition) }
                    val media = server.takeRequest(); assertEquals("/audio.wav", media.path)
                    assertNull(media.getHeader("Authorization")); assertNull(media.getHeader("Cookie"))
                } finally { withContext(Dispatchers.Main) { player.release() } }
                store.remove(imported.id); assertTrue(store.state.value.isEmpty())
            } finally { store.close(); scope.cancel(); vault.clear() }
        }
    }

    @Test fun runawayScriptIsTerminatedAndNextSourceStillWorks() = runBlocking {
        val runtime = LxRuntime(context)
        try {
            val start = System.nanoTime()
            val bad = header + "while(true) {}"
            try { runtime.inspect(info(bad), bad); fail("Runaway script was accepted") }
            catch (_: LxSourceException) { }
            assertTrue("Termination must be bounded", (System.nanoTime() - start) / 1_000_000 < 15000)
            val good = header + "lx.on('request', () => Promise.resolve('https://audio.example.com/ok.mp3'));\n" + init
            runtime.inspect(info(good), good)
            assertEquals("https://audio.example.com/ok.mp3", runtime.resolve(info(good), good, track()))
        } finally { runtime.close() }
    }

    @Test fun cancellationAndUnsupportedDeclarationsDoNotPoisonNextSource() = runBlocking {
        val runtime = LxRuntime(context)
        try {
            val missing = header + "lx.send('inited',{sources:{}});"
            try { runtime.inspect(info(missing), missing); fail("Invalid declaration accepted") } catch (_: LxSourceException) { }
            val script = header + "lx.on('request', () => new Promise(resolve => setTimeout(() => resolve('https://audio.example.com/late.mp3'),5000)));\n" + init
            runtime.inspect(info(script), script)
            assertNull(withTimeoutOrNull(200) { runtime.resolve(info(script), script, track()) })
            val good = header + "lx.on('request', () => Promise.resolve('https://audio.example.com/after.mp3'));\n" + init
            assertEquals("https://audio.example.com/after.mp3", runtime.resolve(info(good), good, track()))
        } finally { runtime.close() }
    }

    @Test fun excessiveHeapAllocationIsContainedAndRuntimeCanRestart() = runBlocking {
        val runtime = LxRuntime(context)
        try {
            val bad = header + "const allocations = []; while(true) allocations.push(new Array(100000).fill('heap fixture'));"
            try { runtime.inspect(info(bad), bad); fail("Heap exhaustion was accepted") } catch (_: LxSourceException) { }
            val good = header + "lx.on('request', () => Promise.resolve('https://audio.example.com/recovered.mp3'));\n" + init
            assertEquals("https://audio.example.com/recovered.mp3", runtime.resolve(info(good), good, track()))
        } finally { runtime.close() }
    }

    @Test fun suppliedRepositorySampleInitializesWithoutResolvingAnyCatalogAudio() = runBlocking {
        val requested = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("lxRepositorySample") == "true"
        org.junit.Assume.assumeTrue("Optional third-party research sample is never bundled", requested)
        val path = java.io.File(context.getExternalFilesDir(null), "lx-research-source.js")
        val script = path.inputStream().use { lxReadBounded(it, LX_MAX_SCRIPT_BYTES).toString(Charsets.UTF_8) }
        val runtime = LxRuntime(context)
        var failure = ""
        runtime.failureObserver = { failure = it }
        try { assertTrue(runtime.inspect(info(script), script).isNotEmpty()) }
        catch (e: LxSourceException) { throw AssertionError("Public research sample initialization: $failure", e) }
        finally { runtime.close() }
    }

    @Test fun contentUriImportCopiesTheScriptAndSurvivesOriginalFileRemoval() = runBlocking {
        val vault = SecureTokenStore(context, ".lx-file-fixture-${UUID.randomUUID()}")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = LxSourceStore(context, vault, scope)
        val file = java.io.File(context.cacheDir, "diagnostics/lx-fixture-${UUID.randomUUID()}.js")
        val script = header + "lx.on('request', () => Promise.resolve('https://audio.example.com/file.mp3'));\n" + init
        try {
            file.parentFile!!.mkdirs(); file.writeText(script)
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
            val imported = store.importFile(uri)
            assertEquals("Local fixture", imported.name); assertTrue(file.delete())
            assertEquals("https://audio.example.com/file.mp3", store.source(Song(42, "Fixture"), AudioQuality.STANDARD, imported.id).url)
            try { store.importScript(script); fail("Duplicate source accepted") } catch (_: LxSourceException) { }
        } finally { store.close(); scope.cancel(); vault.clear(); file.delete() }
    }

    private fun wav(): ByteArray {
        val samples = 16000 * 2
        return ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples * 2); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(16000); putInt(32000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(samples * 2); repeat(samples) { putShort(0) }
        }.array()
    }
}
