package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.settings.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

class AdaptiveAudioUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    @Before fun setup() = runBlocking {
        server = MockWebServer().apply {
            dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = MockResponse().setResponseCode(404)
            }
            start()
        }
        container = AppContainer(ApplicationProvider.getApplicationContext(), "adaptive-ui-${UUID.randomUUID()}", useNativeNetease = false)
        container.ready.await(); container.sessionRestored.await()
        container.updateSettings.setAutoCheck(false)
        container.accountRepository.server = server.url("/cm/").toString()
        container.musicSettings.setServer(server.url("/cm/").toString())
    }
    @After fun cleanup() { container.close(); server.shutdown() }
    @Test fun controlsPersistAndBypassRemainsVisibleWithoutDiscardingTheCurve() {
        val vm = SoundEffectsViewModel(container)
        compose.setContent { AdaptiveAudioScreen(vm, {}) }
        fun scroll(tag: String): SemanticsNodeInteraction {
            compose.onNodeWithTag("adaptive_audio_list").performScrollToNode(hasTestTag(tag))
            return compose.onNodeWithTag(tag)
        }
        fun save(name: String) {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                java.io.File(context.externalCacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        compose.onNodeWithTag("adaptive_enabled").assertIsOff().performClick()
        compose.waitUntil { container.adaptiveAudio.state.value.enabled }
        compose.onNodeWithTag("adaptive_band_0").performSemanticsAction(SemanticsActions.SetProgress) { it(2.5f) }
        compose.waitUntil { container.adaptiveAudio.state.value.gains[0] == 2.5f }
        save("adaptive-audio-eq.png")
        scroll("adaptive_bass").performClick()
        compose.waitUntil { container.adaptiveAudio.state.value.virtualBass }
        compose.onNodeWithTag("adaptive_bass_strength").performSemanticsAction(SemanticsActions.SetProgress) { it(63f) }
        compose.waitUntil { container.adaptiveAudio.state.value.bassStrength == 63 }
        scroll("adaptive_haptics")
        if (vm.hapticsSupported) {
            compose.onNodeWithTag("adaptive_haptics").performClick()
            compose.waitUntil { container.adaptiveAudio.state.value.haptics }
        } else compose.onNodeWithTag("adaptive_haptics").assertIsNotEnabled()
        scroll("adaptive_dynamic").assertIsOn()
        compose.onNodeWithText("人声", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil { container.adaptiveAudio.state.value.curve == DynamicEqCurve.VOCAL }
        save("adaptive-audio-dynamic.png")
        val saved = runBlocking { container.adaptiveAudio.snapshot() }
        assertEquals(2.5f, saved.gains[0], 0f); assertEquals(63, saved.bassStrength)
        runBlocking { container.audioOutput.setMode(AudioOutputMode.BIT_PERFECT) }
        scroll("adaptive_status").assertTextContains("已旁路", substring = true)
        assertEquals(saved.gains, container.adaptiveAudio.state.value.gains)
        scroll("adaptive_enabled").performClick()
        compose.waitUntil { !container.adaptiveAudio.state.value.enabled }
        assertEquals(saved.gains, runBlocking { container.adaptiveAudio.snapshot() }.gains)
    }
}
