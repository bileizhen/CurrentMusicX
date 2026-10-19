package io.github.currencortex.music

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.roaming.PersonalRoamingRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Explicit opt-in, read-only native-account verification. Never exports cookies or starts playback. */
class PersonalRoamingLiveTest {
    @Test fun nativeCatalogAndEachModeReturnRealRecommendations() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("personalRoamingLive") == "true")
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val c = app.container
        c.ready.await(); c.neteaseSessions.ready.await()
        check(c.neteaseSessions.state.value.loggedIn && !c.neteaseSessions.state.value.stale) { "本机网易云未登录或登录已过期" }
        val gateway = c.nativeNetease ?: error("Native gateway required")
        val repository = PersonalRoamingRepository(gateway.transport)
        val catalog = repository.catalog()
        assertTrue(catalog.modes.size >= 3); assertTrue(catalog.scenes.isNotEmpty())
        val report = mutableListOf("modes=${catalog.modes.size}", "scenes=${catalog.scenes.size}")
        for (choice in catalog.modes + catalog.scenes.take(1)) {
            val songs = repository.songs(choice)
            assertTrue("No recommendations for ${choice.code}", songs.isNotEmpty())
            assertTrue(songs.all { it.id > 0 && it.name.isNotBlank() })
            report += "${choice.mode}/${choice.subMode}: ${songs.size} tracks"
        }
        File(app.getExternalFilesDir(null), "roaming-native-proof.txt").writeText(report.joinToString("\n"))
    }
}
