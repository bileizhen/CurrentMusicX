package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.LocalMusicPageActive
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.settings.*
import io.github.currencortex.music.feature.player.PlayerEffectsOverlay
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
        compose.setContent {
            PlayerEffectsOverlay(true, {}, {}, effects = { _, _ -> AdaptiveAudioScreen(vm, {}) }, equalizer = {})
        }
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
        scroll("adaptive_band_0").performSemanticsAction(SemanticsActions.SetProgress) { it(2.5f) }
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

    @Test fun knobDragPreviewsThenCancelsAndEmptyRailScrollsWithoutEditing() {
        runBlocking { container.adaptiveAudio.edit { it.copy(enabled = true) } }
        val active = mutableStateOf(true)
        val vm = SoundEffectsViewModel(container)
        compose.setContent { CompositionLocalProvider(LocalMusicPageActive provides active.value) { AdaptiveAudioScreen(vm, {}) } }
        compose.waitUntil { container.adaptiveAudio.effective.value.enabled }
        (0..9).forEach { compose.onNodeWithTag("adaptive_band_$it").assertIsDisplayed() }
        val knob = compose.onNodeWithTag("adaptive_band_0")
        knob.performTouchInput { down(center); moveBy(Offset(0f, -height * .25f), delayMillis = 250) }
        compose.waitUntil { container.adaptiveAudio.effective.value.gains[0] > 1f }
        assertEquals(0f, runBlocking { container.adaptiveAudio.snapshot() }.gains[0], 0f)
        knob.performTouchInput { cancel() }
        compose.waitUntil { container.adaptiveAudio.effective.value.gains[0] == 0f }
        knob.performTouchInput { down(center); moveBy(Offset(0f, -height * .2f), delayMillis = 250); up() }
        compose.waitUntil { container.adaptiveAudio.state.value.gains[0] > 1f }
        val saved = runBlocking { container.adaptiveAudio.snapshot() }.gains
        val fine = compose.onNodeWithTag("adaptive_band_fine")
        fine.performTouchInput { down(center); moveTo(Offset(width * .85f, centerY), delayMillis = 250) }
        compose.waitUntil { container.adaptiveAudio.effective.value.gains != saved }
        compose.runOnIdle { active.value = false }
        fine.performTouchInput { cancel() }
        compose.waitUntil { container.adaptiveAudio.effective.value.gains == saved }
        compose.runOnIdle { active.value = true }
        val rail = compose.onNodeWithTag("adaptive_band_9")
        val top = rail.fetchSemanticsNode().boundsInRoot.top
        rail.performTouchInput { down(Offset(centerX, height * .85f)); moveBy(Offset(0f, -height * .65f), delayMillis = 350); up() }
        compose.waitForIdle()
        assertTrue("Swiping the empty rail must scroll the page", rail.fetchSemanticsNode().boundsInRoot.top < top - 20f)
        assertEquals(saved, runBlocking { container.adaptiveAudio.snapshot() }.gains)
    }

    @Test fun masterOffDisablesModesAndModuleSwitchesCollapseTheirControls() {
        runBlocking { container.adaptiveAudio.edit { it.copy(haptics = true, virtualBass = true) } }
        val vm = SoundEffectsViewModel(container)
        compose.setContent { AdaptiveAudioScreen(vm, {}) }
        fun scroll(tag: String): SemanticsNodeInteraction {
            compose.onNodeWithTag("adaptive_audio_list").performScrollToNode(hasTestTag(tag))
            return compose.onNodeWithTag(tag)
        }
        scroll("adaptive_reset").assertIsNotEnabled()
        scroll("adaptive_curve_VOCAL").assertIsNotEnabled().performClick()
        assertEquals(DynamicEqCurve.V_SHAPE, runBlocking { container.adaptiveAudio.snapshot() }.curve)
        if (vm.hapticsSupported) scroll("adaptive_haptic_mode_FULL").assertIsNotEnabled().performClick()
        assertEquals(MusicHapticMode.BASS, runBlocking { container.adaptiveAudio.snapshot() }.hapticMode)
        scroll("adaptive_enabled").performClick()
        compose.waitUntil { container.adaptiveAudio.effective.value.enabled }
        scroll("adaptive_dynamic").performClick()
        compose.waitUntil { !container.adaptiveAudio.effective.value.dynamicEq }
        compose.onNodeWithTag("adaptive_curve_VOCAL").assertDoesNotExist()
        scroll("adaptive_bass").performClick()
        compose.waitUntil { !container.adaptiveAudio.effective.value.virtualBass }
        compose.onNodeWithTag("adaptive_bass_strength").assertDoesNotExist()
    }

    @Test fun narrowLayoutKeepsEveryBandVisibleWithoutHorizontalScrolling() {
        val vm = SoundEffectsViewModel(container)
        compose.setContent { Box(Modifier.width(280.dp)) { AdaptiveAudioScreen(vm, {}) } }
        compose.onNodeWithTag("adaptive_audio_list").performScrollToNode(hasTestTag("adaptive_bands"))
        (0..9).forEach { compose.onNodeWithTag("adaptive_band_$it").assertIsDisplayed() }
        val first = compose.onNodeWithTag("adaptive_band_0").fetchSemanticsNode().boundsInRoot
        val sixth = compose.onNodeWithTag("adaptive_band_5").fetchSemanticsNode().boundsInRoot
        assertTrue(sixth.top > first.top)
    }
}
