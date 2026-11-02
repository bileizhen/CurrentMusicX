package io.github.currencortex.music

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.data.lx.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in check of an explicitly supplied script; no account, queue, focus or audible playback. */
class LxImportedSourceDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun suppliedNeteaseScriptResolvesAndDecodesOnDevice() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Requires an explicitly supplied research script", args.getString("lxLiveSource") == "true")
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(BuildConfig.APPLICATION_ID.endsWith(".verification")) { "Use the isolated test package" }
        val script = java.io.File(context.getExternalFilesDir(null), "lx-research-source.js")
            .inputStream().use { lxReadBounded(it, LX_MAX_SCRIPT_BYTES).toString(Charsets.UTF_8) }
        val runtime = LxRuntime(context)
        val info = lxScriptInfo(script)
        var player: ExoPlayer? = null
        try {
            val capabilities = runtime.inspect(info, script)
            assertEquals(listOf("wy"), capabilities.map { it.platform })
            val song = Song(2135426631, "5:20AM", "刀酱", "5:20AM", durationMs = 142000)
            val quality = AudioQuality.from(args.getString("lxQuality") ?: "standard")
            val request = lxTrackRequest(song, quality, capabilities)
            assertEquals("wy", request.platform)
            val url = runtime.resolve(info, script, request.data)
            val instance = withContext(Dispatchers.Main) {
                ExoPlayer.Builder(context).build().apply {
                    volume = 0f
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
                    prepare()
                }
            }
            player = instance
            withTimeout(35000) {
                while (withContext(Dispatchers.Main) { instance.playbackState != Player.STATE_READY && instance.playerError == null }) delay(50)
            }
            withContext(Dispatchers.Main) {
                assertNull("Source URL could not be decoded; code=${instance.playerError?.errorCode}", instance.playerError)
                assertEquals(Player.STATE_READY, instance.playbackState)
                assertTrue(instance.duration > 0)
                val format = instance.audioFormat
                assertNotNull(format)
                println("LX_NETEASE_DECODE_PASS source=${info.name} quality=${request.quality} mime=${format?.sampleMimeType} rate=${format?.sampleRate} channels=${format?.channelCount}")
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { player?.release() }
            runtime.close()
        }
    }

    @Test fun suppliedSourceHealthReflectsActualAudioResponse() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Requires an explicitly supplied research script", args.getString("lxLiveSource") == "true")
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(BuildConfig.APPLICATION_ID.endsWith(".verification"))
        val script = java.io.File(context.getExternalFilesDir(null), "lx-research-source.js")
            .inputStream().use { lxReadBounded(it, LX_MAX_SCRIPT_BYTES).toString(Charsets.UTF_8) }
        val vault = io.github.currencortex.music.core.security.SecureTokenStore(context, ".lx-health-live-${java.util.UUID.randomUUID()}")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = LxSourceStore(context, vault, scope)
        try {
            val source = store.importScript(script)
            store.checkAvailability(source.id)
            val expected = io.github.currencortex.music.core.network.SourceAvailabilityStatus.valueOf(args.getString("lxExpectedHealth") ?: "AVAILABLE")
            assertEquals(expected, store.health.value[source.id]?.status)
            println("LX_HEALTH_PASS source=${source.name} status=${store.health.value[source.id]?.status}")
        } finally { store.close(); scope.cancel(); vault.clear() }
    }
}
