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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.settings.*
import io.github.currencortex.music.feature.player.PlayerEffectsOverlay
import io.github.currencortex.music.core.media.AdaptiveAudioRuntime
import io.github.currencortex.music.core.media.PlayerMode
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

    @Test fun liveDspGainsDriveNumbersAndKnobsWithoutChangingManualSettings() {
        runBlocking { container.adaptiveAudio.edit { it.copy(enabled = true) } }
        container.playerController.state.value = container.playerController.state.value.copy(playing = true)
        val gains = listOf(7.3f, -8.1f, 1.2f, -.5f, 0f, 2.1f, -1.2f, 3f, -3f, .8f)
        container.adaptiveAudioRuntime.value = AdaptiveAudioRuntime(processing = true, gains = gains)
        val vm = SoundEffectsViewModel(container)
        compose.setContent {
            PlayerEffectsOverlay(true, {}, {}, effects = { _, _ -> AdaptiveAudioScreen(vm, {}) }, equalizer = {})
        }
        fun displayed(index: Int) = compose.onNodeWithTag("adaptive_band_$index").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current
        compose.waitUntil { displayed(0) == 7.3f }
        gains.forEachIndexed { index, value ->
            assertEquals(value, displayed(index), .01f)
            compose.onNodeWithTag("adaptive_band_value_$index").assertTextEquals(java.lang.String.format(java.util.Locale.ROOT, "%+.1f", value))
        }
        assertEquals(-9f..9f, compose.onNodeWithTag("adaptive_band_0").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].range)
        assertEquals(List(10) { 0f }, runBlocking { container.adaptiveAudio.snapshot() }.gains)
        container.adaptiveAudioRuntime.value = AdaptiveAudioRuntime(processing = true, gains = List(10) { -1.5f })
        compose.waitUntil { displayed(0) == -1.5f }
        compose.onNodeWithTag("adaptive_band_value_0").assertTextEquals("-1.5")
        container.playerController.state.value = container.playerController.state.value.copy(playing = false)
        compose.waitUntil { displayed(0) == 0f }
        container.playerController.state.value = container.playerController.state.value.copy(playing = true, mode = PlayerMode.CAST)
        compose.waitForIdle()
        assertEquals(0f, displayed(0), 0f)
        container.playerController.state.value = container.playerController.state.value.copy(mode = PlayerMode.LOCAL)
        compose.waitUntil { displayed(0) == -1.5f }
        runBlocking { container.audioOutput.setMode(AudioOutputMode.BIT_PERFECT) }
        compose.waitUntil { displayed(0) == 0f }
        runBlocking { container.audioOutput.setMode(AudioOutputMode.AUDIO_TRACK); container.adaptiveAudio.edit { it.copy(dynamicEq = false) } }
        compose.waitForIdle()
        assertEquals(0f, displayed(0), 0f)
        assertEquals(List(10) { 0f }, runBlocking { container.adaptiveAudio.snapshot() }.gains)
    }

    @Test fun grabbingLiveKnobFreezesItsAnchorAndSavesOnlyTheManualDelta() {
        runBlocking { container.adaptiveAudio.edit { it.copy(enabled = true) } }
        container.playerController.state.value = container.playerController.state.value.copy(playing = true)
        container.adaptiveAudioRuntime.value = AdaptiveAudioRuntime(processing = true, gains = List(10) { 2f })
        val vm = SoundEffectsViewModel(container)
        compose.setContent {
            PlayerEffectsOverlay(true, {}, {}, effects = { _, _ -> AdaptiveAudioScreen(vm, {}) }, equalizer = {})
        }
        val knob = compose.onNodeWithTag("adaptive_band_0")
        fun displayed() = knob.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        compose.waitUntil { displayed() == 2f }
        val inset = with(compose.density) { 12.dp.toPx() }
        knob.performTouchInput {
            val travel = height - inset * 2
            down(Offset(centerX, inset + 7f / 18f * travel))
            moveBy(Offset(0f, -travel / 18f), delayMillis = 240)
        }
        compose.waitUntil { container.adaptiveAudio.effective.value.gains[0] == 1f }
        compose.onNodeWithTag("adaptive_band_value_0").assertTextEquals("+3.0")
        assertEquals(0f, runBlocking { container.adaptiveAudio.snapshot() }.gains[0], 0f)
        // A new audio report must not move the knob out from under the user's finger.
        container.adaptiveAudioRuntime.value = AdaptiveAudioRuntime(processing = true, gains = List(10) { -2f })
        compose.waitForIdle()
        assertEquals(3f, displayed(), 0f)
        knob.performTouchInput { up() }
        compose.waitUntil { container.adaptiveAudio.state.value.gains[0] == 1f && displayed() == -2f }
        knob.performSemanticsAction(SemanticsActions.SetProgress) { it(-2f) }
        compose.waitForIdle()
        assertEquals(1f, runBlocking { container.adaptiveAudio.snapshot() }.gains[0], 0f)
        knob.performTouchInput {
            val travel = height - inset * 2
            down(Offset(centerX, inset + 11f / 18f * travel))
            moveBy(Offset(0f, -travel / 18f), delayMillis = 240)
        }
        compose.waitUntil { container.adaptiveAudio.effective.value.gains[0] == 2f }
        knob.performTouchInput { cancel() }
        compose.waitUntil { container.adaptiveAudio.effective.value.gains[0] == 1f }
        assertEquals(-2f, displayed(), 0f)
    }
}
