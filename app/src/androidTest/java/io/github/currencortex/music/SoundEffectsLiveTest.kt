package io.github.currencortex.music

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Explicit opt-in; public metadata only, no stored account, playback or volume changes. */
class SoundEffectsLiveTest {
    @Test fun officialCatalogAndAnalysisAreAvailableThroughAndroidTransport() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSoundEffects") == "true")
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(context.packageName.endsWith(".verification"))
        val container = AppContainer(context, "effects-live-${UUID.randomUUID()}", useNativeNetease = false)
        try {
            container.ready.await(); container.neteaseSessions.ready.await()
            val queue = container.playbackQueue.state.value
            val catalog = container.neteaseSoundEffects.catalog()
            assertTrue(catalog.any { it.id == 1001L && it.name == "3D环绕" })
            val analysis = requireNotNull(container.neteaseSoundEffects.analyze(186016))
            assertEquals(186016L, analysis.songId)
            assertTrue(analysis.bpm != null && analysis.bpm!! > 0)
            assertTrue(analysis.emotions.isNotEmpty())
            assertEquals(32, analysis.melBandsDb.size)
            assertNotNull(analysis.bassEnergy)
            assertNotNull(analysis.vocalEnergy)
            assertEquals(queue, container.playbackQueue.state.value)
            assertFalse(container.soundEffectsSettings.state.value.smartEnabled)
            java.io.File(context.getExternalFilesDir(null), "sound-effects-live.txt")
                .writeText("catalog=${catalog.size}\nsong=${analysis.songId}\nbpm=${analysis.bpm}\nmelBands=${analysis.melBandsDb.size}\n")
        } finally { container.close() }
    }
}
