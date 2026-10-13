package io.github.currencortex.music

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.PlaybackLyricsState
import io.github.currencortex.music.feature.lyrics.desktop.*
import io.github.currencortex.music.feature.lyrics.ttml.TtmlParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Explicit TTML timing fixture. Real display/vsync; no test Compose clock or forced FPS. */
class DesktopLyricsDemoTest {
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private fun shell(command: String) = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
        java.io.FileInputStream(it.fileDescriptor).bufferedReader().use { r -> r.readText() }
    }
    @Test fun realWindowWordAnimationDemonstration(): Unit = runBlocking {
        check(context.packageName.endsWith(".verification"))
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assumeTrue(Settings.canDrawOverlays(context))
        val container = AppContainer(context, "desktop-demo-${UUID.randomUUID()}", useNativeNetease = false)
        val song = Song(777, "桌面歌词 · TTML 验收片段", durationMs = 40000)
        val xml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div>
            <p begin="00:00:00.000" end="00:00:05.000"><span begin="00:00:00.000" end="00:00:01.200">循着</span><span begin="00:00:01.200" end="00:00:02.500">星光</span><span begin="00:00:02.500" end="00:00:05.000">向前走</span></p>
            <p begin="00:00:05.000" end="00:00:10.000"><span begin="00:00:05.000" end="00:00:06.500">Stay </span><span begin="00:00:06.500" end="00:00:08.000">with </span><span begin="00:00:08.000" end="00:00:10.000">me</span></p>
            <p begin="00:00:10.000" end="00:00:15.000"><span begin="00:00:10.000" end="00:00:11.000">夜空に</span><span begin="00:00:11.000" end="00:00:15.000">響くメロディー</span></p>
            <p begin="00:00:17.000" end="00:00:22.000">这是只有整句时间的歌词 👩🏽‍🚀</p>
            <p begin="00:00:22.000" end="00:00:32.000"><span begin="00:00:22.000" end="00:00:27.000">Hello world, the music </span><span begin="00:00:27.000" end="00:00:32.000">carries on across the night</span></p>
            </div></body></tt>"""
        val document = TtmlParser.parse(xml).let { d -> d.copy(lines = d.lines.map {
            it.copy(translation = "音乐一直陪着你 · The music stays with you", roles = listOf("chorus"))
        }) }
        val player = MutableStateFlow(PlayerState(song = song, playing = true, durationMs = 40000,
            anchorRealtimeMs = SystemClock.elapsedRealtime()))
        val lyrics = MutableStateFlow(PlaybackLyricsState(document, songId = song.id))
        var host: DesktopLyricsHost? = null
        var samples: Job? = null
        try {
            container.ready.await(); container.desktopLyrics.ready.await()
            container.desktopLyrics.edit { it.copy(enabled = true, locked = true, effect = DesktopEffect.STAGE, x = .5f, y = .35f) }
            withContext(Dispatchers.Main) { host = DesktopLyricsHost(context, lyrics, player, container.desktopLyrics, container.desktopLyricsStatus) }
            withTimeout(10000) { while (!container.desktopLyricsStatus.value.visible) delay(50) }
            val start = SystemClock.elapsedRealtime()
            samples = launch { while (isActive) {
                val now = SystemClock.elapsedRealtime()
                player.value = player.value.copy(positionMs = now - start, anchorRealtimeMs = now)
                delay(300)
            } }
            shell("input keyevent 3")
            android.util.Log.i("DesktopEvidence", "demo ready; clock=real vsync; document=explicit TTML fixture")
            delay(14000)
            container.desktopLyrics.edit { it.copy(effect = DesktopEffect.PRECISE) }
            delay(18000)
            assertTrue(container.desktopLyricsStatus.value.attached)
        } finally {
            samples?.cancel(); withContext(Dispatchers.Main) { host?.close() }; container.close()
        }
    }
}
