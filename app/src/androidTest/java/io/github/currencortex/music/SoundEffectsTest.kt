package io.github.currencortex.music

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.core.netease.NeteaseCrypto
import io.github.currencortex.music.core.netease.NeteaseEndpoints
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.settings.SoundEffectsScreen
import io.github.currencortex.music.feature.settings.SoundEffectsViewModel
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestName
import java.io.File
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Isolated stores, a loopback NetEase protocol server and a silent native audio session. */
class SoundEffectsTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val testName = TestName()
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private lateinit var vm: SoundEffectsViewModel
    private lateinit var scope: CoroutineScope
    private lateinit var track: AudioTrack
    private val analysisRequests = AtomicInteger()
    @Volatile private var failCatalog = false
    @Volatile private var resolvedMaterial: String? = null
    private val resolveRequests = AtomicInteger()
    private var resolveRelease = CountDownLatch(0)
    private var resolveFinished = CountDownLatch(0)

    @Before fun setup() = runBlocking {
        check(context.packageName.endsWith(".verification"))
        failCatalog = testName.methodName == "catalogFailureCanRetryWithoutSwitchingOnAnyAudioProcessing"
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                "/eapi/usertool/sound/mobile/all" -> if (failCatalog) MockResponse().setResponseCode(503) else json("""
                    {"code":200,"data":[{"id":1001,"name":"3D环绕","subTitle":"戴上耳机，沉浸式拥抱音乐","type":1},
                    {"id":1005,"name":"超重低音","subTitle":"网易云官方音效","type":3,"soundType":1,
                    "link":"https://d1.music.126.net/dmusic/test.ncae","md5":"0123456789abcdef0123456789abcdef"}]}
                """.trimIndent())
                "/eapi/usertool/sound/queryAllSound" -> json("""{"code":200,"data":[
                    {"id":1001,"name":"3D环绕","soundCategory":1,"type":1,"exposeDescText":"戴上耳机，沉浸式拥抱音乐"},
                    {"id":1005,"name":"超重低音","soundCategory":1,"type":3,"exposeDescText":"网易云官方音效"}]}""")
                "/eapi/usertool/home/sound/info" -> {
                    resolveRequests.incrementAndGet()
                    resolveRelease.await(5, TimeUnit.SECONDS)
                    resolveFinished.countDown()
                    resolvedMaterial?.let(::json) ?: MockResponse().setResponseCode(404)
                }
                "/eapi/vip-rights/emotion/audio/effect/get" -> {
                    analysisRequests.incrementAndGet()
                    val encoded = request.body.readUtf8().substringAfter("params=").substringBefore('&')
                    val hex = URLDecoder.decode(encoded, "UTF-8")
                    val raw = NeteaseCrypto.decryptResponse(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()).toString(Charsets.UTF_8)
                    val payload = Json.parseToJsonElement(raw.split("-36cd479b6b5-")[1]).jsonObject
                    val id = payload.getValue("songIds").jsonPrimitive.content.trim('[', ']').toLong()
                    json("""{"code":200,"data":[{"song_id":$id,"emotion_label":"抒情,清新,快乐","repbpm":69,
                        "loudness_lufs":-10.55,"vocal_energy_ratio":${if (id == 186016L) .31 else .1},"bass_energy_ratio":${if (id == 186016L) .24 else .8}}]}""")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val url = server.url("/").toString()
        container = AppContainer(context, "sound-effects-${UUID.randomUUID()}", useNativeNetease = false,
            neteaseEndpoints = NeteaseEndpoints(url, url, url))
        container.ready.await(); container.neteaseSessions.ready.await()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        withContext(Dispatchers.Main) {
            track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(9600).build()
            container.equalizer.attach(track.audioSessionId)
            scope.launch { container.smartTuning.effective.collect { container.equalizer.configure(it) } }
            vm = SoundEffectsViewModel(container)
        }
    }
    @After fun finish() {
        resolveRelease.countDown()
        scope.cancel(); container.close(); track.release(); server.shutdown()
    }
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun open() {
        compose.setContent { LeiTheme(AppearanceSettings()) {
            top.yukonga.miuix.kmp.basic.Scaffold { SoundEffectsScreen(vm, {}, {}) }
        } }
    }
    private fun waitFor(predicate: () -> Boolean) = compose.waitUntil(8000, predicate)
    private fun capture(name: String) {
        File(context.getExternalFilesDir(null), "sound-$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun nativeMatchesSaved() {
        compose.runOnIdle {
            val expected = container.smartTuning.effective.value
            assertTrue(container.equalizer.state.value.active)
            container.equalizer.actualLevels().forEach { (frequency, gain) ->
                assertEquals((expected.gainAt(frequency) / 100.0).roundToInt() * 100, gain)
            }
        }
    }

    @Test fun realNeteaseDescriptorsDriveNativeEqAndSwitchingOffRestoresManualCurve() = runBlocking {
        container.equalizerSettings.select(EqualizerPreset.BASS)
        container.equalizerSettings.setEnabled(true)
        waitFor { container.equalizerSettings.state.value.preset == EqualizerPreset.BASS }
        val saved = container.equalizerSettings.snapshot()
        withContext(Dispatchers.Main) { container.playerController.state.value = PlayerState(song = Song(186016, "测试网易云歌曲")) }
        open()
        waitFor { !vm.catalog.value.loading }
        assertEquals(0, analysisRequests.get())
        compose.onNodeWithTag("sound_effects_screen").performScrollToNode(hasTestTag("sound_smart_action"))
        compose.onNodeWithTag("sound_smart_action").performClick()
        waitFor { vm.tuning.value.active && container.equalizer.state.value.active }
        compose.onNodeWithTag("sound_effects_screen").performScrollToNode(hasTestTag("sound_song_analysis"))
        compose.onNodeWithTag("sound_song_analysis").assertTextContains("69 BPM", substring = true)
        nativeMatchesSaved()
        assertEquals(saved, container.equalizerSettings.snapshot())
        val first = vm.tuning.value.gains
        compose.runOnIdle { container.playerController.state.value = PlayerState(song = Song(186017, "第二首歌曲")) }
        waitFor { vm.tuning.value.analysis?.songId == 186017L }
        assertNotEquals(first, vm.tuning.value.gains)
        nativeMatchesSaved()
        vm.smartEnabled(false)
        waitFor { !vm.preferences.value.smartEnabled && container.smartTuning.effective.value == saved }
        nativeMatchesSaved()
        assertEquals(AudioTrack.PLAYSTATE_STOPPED, track.playState)
    }

    @Test fun officialCatalogDetailsDoNotApplyUnsupportedNeteaseEffects() {
        open()
        waitFor { !vm.catalog.value.loading }
        val before = container.equalizerSettings.state.value
        val queue = container.playbackQueue.state.value
        compose.onNodeWithTag("sound_effects_screen").performScrollToNode(hasTestTag("sound_official_1001"))
        compose.onNodeWithTag("sound_official_1001").performClick()
        compose.onNodeWithTag("sound_open_netease").assertExists()
        compose.onNodeWithText("网易云未提供可验证的兼容预设资源").assertExists()
        assertEquals(before, container.equalizerSettings.state.value)
        assertEquals(queue, container.playbackQueue.state.value)
        assertEquals(0, analysisRequests.get())
        compose.onNodeWithTag("sound_official_close").performClick()
    }

    @Test fun localPresetEnablesRealEqualizerAndKeepsQueueAndAccounts() = runBlocking<Unit> {
        open()
        val queue = container.playbackQueue.state.value
        val account = container.accountRepository.state.value
        compose.onNodeWithTag("sound_effects_screen").performScrollToNode(hasTestTag("sound_local_vocal"))
        compose.onNodeWithTag("sound_local_vocal").performClick()
        waitFor { container.equalizerSettings.state.value.enabled && container.equalizerSettings.state.value.preset == EqualizerPreset.VOCAL }
        compose.onNodeWithTag("sound_local_vocal").assertIsSelected()
        waitFor { container.smartTuning.effective.value.preset == EqualizerPreset.VOCAL && container.equalizer.state.value.active }
        nativeMatchesSaved()
        assertFalse(container.soundEffectsSettings.state.value.smartEnabled)
        assertEquals(queue, container.playbackQueue.state.value)
        assertEquals(account, container.accountRepository.state.value)
        assertTrue(container.equalizerSettings.snapshot().enabled)
        File(context.getExternalFilesDir(null), "sound-effects-preview.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun catalogFailureCanRetryWithoutSwitchingOnAnyAudioProcessing() {
        failCatalog = true
        vm.refreshCatalog()
        open()
        waitFor { vm.catalog.value.error != null }
        compose.onNodeWithTag("sound_effects_screen").performScrollToNode(hasTestTag("sound_catalog_retry"))
        failCatalog = false
        compose.onNodeWithTag("sound_catalog_retry").performClick()
        waitFor { vm.catalog.value.items.size == 2 }
        compose.onNodeWithTag("sound_official_1001").assertExists()
        assertFalse(vm.preferences.value.smartEnabled)
        assertFalse(container.equalizer.state.value.active)
    }

    @Test fun castingCanAlwaysTurnSmartOffWithoutEnablingLocalProcessing() {
        vm.smartEnabled(true)
        waitFor { vm.preferences.value.smartEnabled }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            container.playerController.state.value = PlayerState(mode = io.github.currencortex.music.core.media.PlayerMode.CAST)
        }
        open()
        compose.onNodeWithTag("sound_effects_screen").performScrollToNode(hasTestTag("sound_smart_action"))
        compose.onNodeWithTag("sound_smart_action").assertIsEnabled().performClick()
        waitFor { !vm.preferences.value.smartEnabled }
        compose.onNodeWithTag("sound_smart_action").assertIsNotEnabled()
        assertFalse(container.equalizer.state.value.active)
        assertEquals(0, analysisRequests.get())
    }

    @Test fun turnOffFromSmartCardDisablesProcessingAndPreservesManualCurve() = runBlocking<Unit> {
        container.equalizerSettings.select(EqualizerPreset.BASS)
        container.equalizerSettings.setEnabled(true)
        waitFor { container.equalizerSettings.state.value.enabled }
        val saved = container.equalizerSettings.snapshot()
        val queue = container.playbackQueue.state.value
        vm.smartEnabled(true)
        waitFor { vm.preferences.value.smartEnabled }
        open()
        compose.onNodeWithTag("sound_effects_screen").performScrollToNode(hasTestTag("sound_smart_action"))
        compose.onNodeWithTag("sound_smart_action").assertIsDisplayed().performClick()
        waitFor { !vm.preferences.value.smartEnabled && !vm.manual.value.enabled && !vm.runtime.value.active }
        assertEquals(saved.gains, container.equalizerSettings.snapshot().gains)
        assertEquals(saved.preset, container.equalizerSettings.snapshot().preset)
        assertEquals(queue, container.playbackQueue.state.value)
        compose.onNodeWithTag("sound_status_bar").assertDoesNotExist()
        compose.onNodeWithText("个性化音效").assertDoesNotExist()
        compose.onNodeWithTag("sound_smart_action").assertIsEnabled().performClick()
        waitFor { vm.preferences.value.smartEnabled }
    }

    @Test fun latestRapidSelectionWinsAndTurnOffKeepsAllModesOff() {
        open()
        waitFor { vm.catalog.value.items.isNotEmpty() }
        compose.runOnIdle {
            vm.useLocal(EqualizerPreset.ROCK)
            vm.smartEnabled(true)
            vm.useLocal(EqualizerPreset.VOCAL)
        }
        waitFor { vm.manual.value.enabled && vm.manual.value.preset == EqualizerPreset.VOCAL && !vm.preferences.value.smartEnabled }
        compose.runOnIdle {
            vm.originalMoodEnabled(true)
            vm.turnOff()
        }
        waitFor { !vm.manual.value.enabled && !vm.preferences.value.smartEnabled && !vm.runtime.value.active }
        compose.waitForIdle()
        assertNull(vm.original.value.requestedId)
        assertNull(vm.original.value.selectedId)
        assertFalse(vm.originalSmart.value.requestedEnabled)
        assertFalse(vm.originalMood.value.requestedEnabled)
        assertFalse(vm.originalAiMaster.value.requestedEnabled)
        assertFalse(vm.originalSpeaker.value.requestedEnabled)
    }

    @Test fun newMasterAndSpeakerRequestsCanBeCancelledBeforeAnyLateResponse() {
        open()
        compose.runOnIdle {
            vm.originalAiMasterEnabled(true)
            vm.originalSpeakerEnabled(true)
            vm.originalMoodEnabled(true)
            vm.turnOff()
        }
        waitFor { !vm.manual.value.enabled && !vm.preferences.value.smartEnabled }
        compose.waitForIdle()
        assertFalse(vm.originalAiMaster.value.requestedEnabled)
        assertFalse(vm.originalSpeaker.value.requestedEnabled)
        assertFalse(vm.originalMood.value.requestedEnabled)
        assertNull(container.originalAiMasterEffects.selection.value)
        assertNull(container.originalSpeakerEffects.selection.value)
    }

    @Test fun officialMasterTilesDispatchToTheirOwnAlgorithms() {
        open()
        val queue = container.playbackQueue.state.value
        fun tile(id: Long) = io.github.currencortex.music.data.effects.NativeEffectTile(id, "official master", "",
            1, 4, "", "", "", "")
        compose.runOnIdle {
            vm.useTile(tile(104))
            assertEquals(io.github.currencortex.music.feature.settings.SoundEffectKind.AI_MASTER, vm.lastSelection.value.kind)
            vm.useTile(tile(105))
            assertEquals(io.github.currencortex.music.feature.settings.SoundEffectKind.SPEAKER, vm.lastSelection.value.kind)
            vm.turnOff()
        }
        waitFor { !vm.originalAiMaster.value.requestedEnabled && !vm.originalSpeaker.value.requestedEnabled }
        assertNull(vm.actionError.value)
        assertEquals(queue, container.playbackQueue.state.value)
    }

    @Test fun moodDetailsReturnWithoutChangingTheActiveEffect() {
        vm.smartEnabled(true)
        waitFor { vm.preferences.value.smartEnabled }
        open()
        compose.onNodeWithTag("sound_master_mood_detail").performClick()
        compose.onNodeWithTag("mood_detail").assertExists()
        assertTrue(vm.preferences.value.smartEnabled)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("sound_master_mood_detail").assertIsDisplayed()
        assertTrue(vm.preferences.value.smartEnabled)
    }

    @Test fun browsingSpeakerDetailsDoesNotApplySceneAndReturningKeepsPager() {
        vm.smartEnabled(true)
        waitFor { vm.preferences.value.smartEnabled }
        open()
        compose.onNodeWithTag("sound_master_pager").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .7f, centerY),
                androidx.compose.ui.geometry.Offset(width * .2f, centerY), durationMillis = 500)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("sound_master_speaker_detail").performClick()
        compose.onNodeWithTag("sound_speaker_detail").assertExists()
        compose.onNodeWithTag("sound_speaker_detail_scene_sleep").performClick()
        compose.onNodeWithTag("sound_speaker_detail_title").assertTextContains("睡前陪伴", substring = true)
        assertFalse(vm.originalSpeaker.value.requestedEnabled)
        assertTrue(vm.preferences.value.smartEnabled)
        compose.onNodeWithTag("sound_speaker_detail_back").performClick()
        compose.onNodeWithTag("sound_master_speaker_action").assertIsDisplayed()
        assertTrue(vm.preferences.value.smartEnabled)
    }

    @Test fun catalogRemainsVisibleAfterRefreshFailsAndApplyRechecksTheServer() = runBlocking<Unit> {
        open()
        waitFor { vm.catalog.value.items.size == 2 }
        failCatalog = true
        // A session revision expires the repository cache and triggers the real refresh path.
        container.neteaseSessions.clear()
        waitFor { vm.catalog.value.error != null }
        assertEquals(2, vm.catalog.value.items.size)
        compose.onNodeWithTag("sound_effects_screen").performScrollToNode(hasTestTag("sound_apply_1001"))
        // The target is inside a horizontal lazy item; reveal its lower action with a real vertical gesture.
        compose.onNodeWithTag("sound_effects_screen").performTouchInput { swipeUp() }
        compose.onNodeWithTag("sound_apply_1001").performClick()
        waitFor { vm.actionError.value != null }
        assertTrue(resolveRequests.get() > 0)
        compose.onNodeWithTag("sound_effects_screen").performScrollToNode(hasTestTag("sound_tile_error"))
        capture("refresh-unavailable")
        compose.onNodeWithTag("sound_tile_error").assertExists()
        assertNull(vm.original.value.requestedId)
        assertFalse(vm.manual.value.enabled)
    }

    @Test fun browsingMasterCardsKeepsTheActiveEffect() {
        vm.smartEnabled(true)
        waitFor { vm.preferences.value.smartEnabled }
        open()
        waitFor { vm.catalog.value.items.size == 2 }
        val queue = container.playbackQueue.state.value
        capture("master-start")
        // The card is narrower than the viewport. A full-width fast fling may cross two cards.
        compose.onNodeWithTag("sound_master_pager").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .7f, centerY),
                androidx.compose.ui.geometry.Offset(width * .2f, centerY), durationMillis = 500)
        }
        compose.waitForIdle()
        capture("master-browsing")
        compose.onNodeWithTag("sound_master_speaker_action").assertIsDisplayed()
        compose.onNodeWithTag("sound_status_bar").assertDoesNotExist()
        assertTrue(vm.preferences.value.smartEnabled)
        assertNull(vm.original.value.requestedId)
        assertFalse(vm.originalMood.value.requestedEnabled)
        assertEquals(queue, container.playbackQueue.state.value)
        compose.onNodeWithTag("sound_master_pager").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .2f, centerY),
                androidx.compose.ui.geometry.Offset(width * .7f, centerY), durationMillis = 500)
        }
        compose.waitForIdle()
        assertTrue(vm.preferences.value.smartEnabled)
        assertEquals(0, analysisRequests.get())
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun pageAndMoreUseNativeViewsAndReturningKeepsTheCurrentEffect() {
        vm.smartEnabled(true)
        waitFor { vm.preferences.value.smartEnabled }
        open()
        waitFor { !vm.page.value.loading && !vm.catalog.value.loading }
        compose.onNodeWithTag("native_sound_effects_page").assertExists()
        compose.waitUntil(10000) {
            var silentVideoPlaying = false
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
                fun playing(view: android.view.View): Boolean =
                    view is androidx.media3.ui.PlayerView && view.player?.let { it.isPlaying && it.volume == 0f } == true ||
                    view is android.view.ViewGroup && (0 until view.childCount).any { playing(view.getChildAt(it)) }
                silentVideoPlaying = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).any { playing(it.window.decorView) }
            }
            silentVideoPlaying
        }
        compose.onNodeWithTag("sound_catalog_expand").performClick()
        waitFor { !vm.more.value.loading && vm.more.value.featured.size == 2 }
        compose.onNodeWithTag("sound_more_1001").assertExists()
        compose.onNodeWithText("大师音效").assertDoesNotExist()
        compose.onNodeWithText("官方精选音效").assertExists()
        compose.onNodeWithTag("sound_effects_back").performClick()
        compose.onNodeWithText("大师音效").assertExists()
        compose.onNodeWithTag("sound_status_bar").assertDoesNotExist()
        assertTrue(vm.preferences.value.smartEnabled)
        val activities = mutableListOf<android.app.Activity>()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activities.addAll(androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))
            fun hasWeb(view: android.view.View): Boolean = view.javaClass.name == "android.webkit.WebView" ||
                view is android.view.ViewGroup && (0 until view.childCount).any { hasWeb(view.getChildAt(it)) }
            assertTrue(activities.isNotEmpty())
            assertFalse(activities.any { hasWeb(it.window.decorView) })
        }
        assertFalse(context.assets.list("netease-effects-ui")!!.any { it.endsWith(".html") || it.endsWith(".js") })
    }

    @Test fun resumingTheSavedEffectRestoresTheCustomManualCurve() = runBlocking<Unit> {
        container.equalizerSettings.setBand(2, 400)
        container.equalizerSettings.setEnabled(true)
        waitFor { vm.manual.value.enabled && vm.manual.value.preset == EqualizerPreset.CUSTOM }
        open()
        waitFor { vm.lastSelection.value.kind == io.github.currencortex.music.feature.settings.SoundEffectKind.MANUAL }
        val saved = container.equalizerSettings.snapshot()
        compose.runOnIdle { vm.turnOff() }
        waitFor { !vm.manual.value.enabled && !vm.runtime.value.active }
        compose.runOnIdle { vm.resumeEffect() }
        waitFor { vm.manual.value.enabled && vm.runtime.value.active }
        assertEquals(saved, container.equalizerSettings.snapshot())
        nativeMatchesSaved()
    }

    private fun currentMaterial(strategy: String = "requires-current-authorization") = """{"code":200,"data":{"soundList":[
        {"soundMobileDetailDTO":{"id":1001,"name":"最新目录音效","type":1,"soundType":1,
        "newVipStrategyCode":"$strategy","soundMaterialList":[{"type":1,
        "link":"https://d1.music.126.net/current.ncae","md5":"0123456789abcdef0123456789abcdef"}]}}]}}"""

    @Test fun catalogDetailsCanRecheckLatestMaterialWithoutUsingStaleAuthorization() = runBlocking<Unit> {
        container.equalizerSettings.select(EqualizerPreset.BASS)
        container.equalizerSettings.setEnabled(true)
        waitFor { vm.manual.value.enabled && !vm.catalog.value.loading }
        val saved = container.equalizerSettings.snapshot()
        resolvedMaterial = currentMaterial()
        open()
        compose.onNodeWithTag("sound_effects_screen").performScrollToNode(hasTestTag("sound_official_1001"))
        compose.onNodeWithTag("sound_official_1001").performClick()
        compose.onNodeWithTag("sound_use_original").assertTextContains("重新检查并使用").performClick()
        waitFor { vm.actionError.value != null }
        assertEquals(1, resolveRequests.get())
        assertTrue(vm.actionError.value!!.contains("授权策略"))
        assertEquals(saved, container.equalizerSettings.snapshot())
        assertNull(vm.original.value.requestedId)
    }

    @Test fun knownNativeTileResolvesCurrentMetadataAndKeepsTheManualEffectWhenRejected() = runBlocking<Unit> {
        container.equalizerSettings.select(EqualizerPreset.VOCAL)
        container.equalizerSettings.setEnabled(true)
        waitFor { vm.manual.value.enabled && !vm.catalog.value.loading }
        val saved = container.equalizerSettings.snapshot()
        resolvedMaterial = currentMaterial()
        compose.runOnUiThread { vm.useTile(io.github.currencortex.music.data.effects.NativeEffectTile(
            1001L, "旧目录标题", "旧目录没有素材", 1, 1, "", "", "", "")) }
        waitFor { vm.actionError.value != null }
        assertEquals(1, resolveRequests.get())
        assertTrue(vm.actionError.value!!.contains("授权策略"))
        assertEquals(saved, container.equalizerSettings.snapshot())
        assertNull(vm.original.value.requestedId)
        assertNull(vm.tileLoading.value)
    }

    @Test fun closingWhileCurrentMaterialResolvesCancelsTheLateSelection() = runBlocking<Unit> {
        waitFor { !vm.catalog.value.loading }
        resolvedMaterial = currentMaterial(strategy = "")
        resolveRelease = CountDownLatch(1)
        resolveFinished = CountDownLatch(1)
        val oldMetadata = vm.catalog.value.items.first { it.id == 1001L }
        compose.runOnUiThread { vm.useOriginal(oldMetadata) }
        waitFor { resolveRequests.get() == 1 && vm.tileLoading.value == 1001L }
        compose.runOnUiThread { vm.turnOff() }
        resolveRelease.countDown()
        assertTrue(resolveFinished.await(5, TimeUnit.SECONDS))
        compose.waitForIdle()
        assertNull(vm.tileLoading.value)
        assertNull(vm.actionError.value)
        assertNull(vm.original.value.requestedId)
        assertNull(container.originalSoundEffects.selection.value)
        assertFalse(vm.manual.value.enabled)
        assertFalse(vm.preferences.value.smartEnabled)
    }

    @Test fun unresolvedNativeTileKeepsTheCurrentManualEffectAndShowsAnError() = runBlocking<Unit> {
        container.equalizerSettings.setBand(2, 400)
        container.equalizerSettings.setEnabled(true)
        waitFor { vm.manual.value.enabled }
        val saved = container.equalizerSettings.snapshot()
        val queue = container.playbackQueue.state.value
        compose.runOnUiThread { vm.useTile(io.github.currencortex.music.data.effects.NativeEffectTile(
            999L, "未适配音效", "素材需验证", 1, 3, "", "", "", "")) }
        waitFor { vm.actionError.value != null }
        assertNull(vm.tileLoading.value)
        assertEquals(saved, container.equalizerSettings.snapshot())
        assertEquals(queue, container.playbackQueue.state.value)
        assertNull(vm.original.value.requestedId)
        assertFalse(vm.originalMood.value.requestedEnabled)
    }

    @Test fun featuredBannerAppliesTheReturnedEffectInsteadOfAlwaysEnablingSmart() = runBlocking<Unit> {
        container.equalizerSettings.setEnabled(true)
        waitFor { vm.manual.value.enabled && !vm.page.value.loading }
        val saved = container.equalizerSettings.snapshot()
        open()
        compose.onNodeWithTag("sound_effects_screen").performScrollToNode(hasTestTag("sound_original_smart_enabled"))
        compose.onNodeWithTag("sound_original_smart_enabled").performClick()
        waitFor { vm.actionError.value != null }
        assertEquals(saved, container.equalizerSettings.snapshot())
        assertFalse(vm.originalSmart.value.requestedEnabled)
        assertNull(vm.original.value.requestedId)
        compose.onNodeWithTag("sound_tile_error").assertExists()
    }
}
