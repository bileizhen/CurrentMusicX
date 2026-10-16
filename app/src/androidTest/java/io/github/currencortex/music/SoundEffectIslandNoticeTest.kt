package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.feature.lyrics.desktop.SoundEffectIslandPill
import io.github.currencortex.music.feature.lyrics.desktop.*
import io.github.currencortex.music.feature.lyrics.data.PlaybackLyricsState
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.settings.SoundEffectsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Presentation only; never changes the real account, audio or island preferences. */
class SoundEffectIslandNoticeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun phaseUpdatesKeepOneFullyLitDiscAndWordmarkInsteadOfCrossfadingWholeRows() {
        var notice by mutableStateOf(SoundEffectNotice(1, "AI 调音大师", SoundEffectNoticeTier.SVIP))
        compose.setContent { SoundEffectIslandPill(notice) }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1000)
        fun discBrightness(): Double {
            val bitmap = compose.onNodeWithContentDescription("SVIP 唱片").captureToImage().asAndroidBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return pixels.sumOf { pixel -> ((pixel shr 16 and 255) + (pixel shr 8 and 255) + (pixel and 255)).toDouble() }
        }
        val initialBrightness = discBrightness()
        for (phase in listOf(SoundEffectNoticePhase.READY, SoundEffectNoticePhase.WAITING, SoundEffectNoticePhase.APPLYING)) {
            compose.runOnIdle { notice = notice.copy(phase = phase) }
            repeat(6) {
                compose.mainClock.advanceTimeBy(32)
                compose.onAllNodesWithContentDescription("SVIP 唱片").assertCountEquals(1)
                compose.onAllNodesWithTag("sound_effect_island_wordmark").assertCountEquals(1)
                assertTrue("An unchanged disc must not dim during a status update", discBrightness() >= initialBrightness * .97)
            }
        }
        compose.mainClock.autoAdvance = true
    }
    @Test fun expandingAndReturningKeepsTheNormalBodyAlive() {
        var notice by mutableStateOf<SoundEffectNotice?>(null)
        var mounted = 0
        var disposed = 0
        compose.setContent { Box(Modifier.width(340.dp)) {
            DesktopIslandEffectTransition(notice) {
                DisposableEffect(Unit) { mounted++; onDispose { disposed++ } }
                Spacer(Modifier.width(120.dp).height(32.dp))
            }
        } }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { notice = SoundEffectNotice(1, "AI 调音大师", SoundEffectNoticeTier.SVIP) }
        compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithContentDescription("SVIP 唱片").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertEquals(1, mounted); assertEquals(0, disposed); notice = null }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertEquals(1, mounted); assertEquals(0, disposed) }
        compose.mainClock.autoAdvance = true
    }
    @Test fun emptyPhasesStayCompactAndVisibleFailureResizesSmoothlyWithoutChangingHeight() {
        var notice by mutableStateOf(SoundEffectNotice(1, "原声黑胶", SoundEffectNoticeTier.SVIP))
        compose.setContent { Box(Modifier.width(340.dp)) {
            DesktopIslandEffectTransition(notice) { Spacer(Modifier.width(120.dp).height(32.dp)) }
        } }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(900)
        val initial = compose.onNodeWithTag("desktop_island_transition").getUnclippedBoundsInRoot()
        for (phase in listOf(SoundEffectNoticePhase.FAILED, SoundEffectNoticePhase.READY, SoundEffectNoticePhase.APPLYING)) {
            val widths = mutableListOf<Float>()
            compose.runOnIdle { notice = notice.copy(phase = phase, message = if (phase == SoundEffectNoticePhase.FAILED)
                "此音效的会员授权类型尚未接入，需要在网易云客户端使用，请选择其他音效后重试。" else "已启用") }
            repeat(35) {
                compose.mainClock.advanceTimeByFrame()
                val current = compose.onNodeWithTag("desktop_island_transition").getUnclippedBoundsInRoot()
                assertEquals("Status text must not pump the capsule height", (initial.bottom - initial.top).value,
                    (current.bottom - current.top).value, 1f)
                widths += (current.right - current.left).value
            }
            if (phase == SoundEffectNoticePhase.FAILED) assertTrue(widths.last() > (initial.right - initial.left).value + 1f)
            else assertEquals("Empty status must not reserve space", (initial.right - initial.left).value, widths.last(), 1f)
            if (phase != SoundEffectNoticePhase.APPLYING) assertTrue("Status width must interpolate rather than jump",
                widths.any { it > widths.min() + 1f && it < widths.max() - 1f })
        }
        compose.mainClock.autoAdvance = true
    }
    @Test fun svipLoadingThenReadyAndRapidFailureShowOnlyTheCurrentIntent() {
        var notice by mutableStateOf(SoundEffectNotice(1, "原声黑胶", SoundEffectNoticeTier.SVIP))
        compose.setContent { SoundEffectIslandPill(notice) }
        compose.onNodeWithText("SVIP").assertDoesNotExist()
        compose.onNodeWithContentDescription("SVIP 唱片").assertIsDisplayed()
        compose.onNodeWithText("正在启用").assertDoesNotExist()
        compose.onNodeWithText("已使用").assertDoesNotExist()
        compose.runOnIdle { notice = notice.copy(phase = SoundEffectNoticePhase.READY, message = "已开启，播放后生效") }
        compose.onNodeWithText("已开启，播放后生效").assertDoesNotExist()
        compose.onNodeWithTag("sound_effect_island_notice").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "已开启，播放后生效"))
        compose.onNodeWithText("已使用").assertDoesNotExist()
        compose.onNodeWithContentDescription("SVIP 唱片").assertIsDisplayed()
        compose.runOnIdle { notice = SoundEffectNotice(2, "另一音效", SoundEffectNoticeTier.VIP,
            SoundEffectNoticePhase.FAILED, "当前账号没有权益") }
        compose.onNodeWithText("原声黑胶").assertDoesNotExist()
        compose.onNodeWithText("未启用").assertIsDisplayed()
        compose.onNodeWithText("当前账号没有权益").assertDoesNotExist()
        compose.onNodeWithTag("sound_effect_island_notice").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "未启用：当前账号没有权益"))
        compose.onNodeWithContentDescription("VIP 唱片").assertIsDisplayed()
        compose.onNodeWithContentDescription("SVIP 唱片").assertDoesNotExist()
    }

    @Test fun desktopNoticeRestoresThePreviousFpsWithoutControllingPlayback() {
        val player = MutableStateFlow(PlayerState(song = Song(42, "Test")))
        val notices = MutableStateFlow<SoundEffectNotice?>(null)
        var controls = 0
        compose.setContent { Box(Modifier.width(340.dp)) {
            DesktopLyricsSurface(MutableStateFlow(PlaybackLyricsState()), player,
                MutableStateFlow(DesktopLyricsSettings(locked = true)), {}, {}, {}, defaultFpsMode = true,
                effectNotices = notices, togglePlayback = { controls++ }, changeSong = { controls++ })
        } }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(2600)
        compose.onNodeWithTag("desktop_island_fps").assertIsDisplayed()
        compose.runOnIdle { notices.value = SoundEffectNotice(1, "原声黑胶", SoundEffectNoticeTier.SVIP,
            SoundEffectNoticePhase.READY, "已开启，播放后生效") }
        compose.mainClock.advanceTimeBy(1300)
        compose.onNodeWithText("SVIP").assertDoesNotExist()
        compose.onNodeWithTag("desktop_island_fps").assertIsNotDisplayed()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.getExternalFilesDir(null), "effect-island-svip.png").outputStream().use {
            compose.onNodeWithTag("sound_effect_island_notice").captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.runOnIdle { notices.value = null }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("desktop_island_fps").assertIsDisplayed()
        assertEquals(0, controls)
    }

    @Test fun islandMorphPassesThroughIntermediateSizesAndRapidRetargetKeepsLatestContent() {
        val player = MutableStateFlow(PlayerState(song = Song(42, "Test")))
        val notices = MutableStateFlow<SoundEffectNotice?>(null)
        val lyrics = MutableStateFlow(PlaybackLyricsState())
        val options = MutableStateFlow(DesktopLyricsSettings(locked = true))
        compose.setContent { Box(Modifier.width(340.dp)) {
            DesktopLyricsSurface(lyrics, player, options, {}, {}, {}, defaultFpsMode = true, effectNotices = notices)
        } }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(2600)
        fun width(): Float {
            val bounds = compose.onNodeWithTag("desktop_island_transition").getUnclippedBoundsInRoot()
            return bounds.right.value - bounds.left.value
        }
        val compact = width()
        compose.runOnIdle { notices.value = SoundEffectNotice(1, "原声黑胶", SoundEffectNoticeTier.SVIP) }
        compose.mainClock.advanceTimeBy(130)
        val expanding = width()
        compose.mainClock.advanceTimeBy(600)
        val expanded = width()
        assertTrue("Effect width must interpolate: $compact -> $expanding -> $expanded",
            expanding > minOf(compact, expanded) + 1 && expanding < maxOf(compact, expanded) - 1)
        compose.runOnIdle { notices.value = null }
        compose.mainClock.advanceTimeBy(130)
        val shrinking = width()
        assertTrue("Return must interpolate", shrinking > minOf(compact, expanded) + 1 && shrinking < maxOf(compact, expanded) - 1)
        compose.runOnIdle { notices.value = SoundEffectNotice(2, "全景环绕", SoundEffectNoticeTier.VIP) }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { notices.value = SoundEffectNotice(3, "AI 调音大师", SoundEffectNoticeTier.SVIP) }
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithText("AI 调音大师").assertIsDisplayed()
        compose.onNodeWithText("全景环绕").assertDoesNotExist()
        compose.runOnIdle { notices.value = null }
        compose.mainClock.advanceTimeBy(900)
        assertEquals(compact, width(), 2f)
        compose.onNodeWithTag("desktop_island_fps").assertIsDisplayed()
    }

    @Test fun compactNoticesMatchFpsHeightAtBothFontSizesAndUseOriginalMasterWordmarks() {
        val notices = MutableStateFlow<SoundEffectNotice?>(null)
        val options = MutableStateFlow(DesktopLyricsSettings(locked = true))
        var fontScale by mutableFloatStateOf(1f)
        val player = MutableStateFlow(PlayerState(song = Song(42, "Test")))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Box(Modifier.width(340.dp)) {
                    DesktopLyricsSurface(MutableStateFlow(PlaybackLyricsState()), player, options, {}, {}, {},
                        defaultFpsMode = true, effectNotices = notices)
                }
            }
        }
        compose.mainClock.autoAdvance = false
        fun height(): Float {
            val b = compose.onNodeWithTag("desktop_island_transition").getUnclippedBoundsInRoot()
            return b.bottom.value - b.top.value
        }
        for ((font, scale) in listOf(14f to 1f, 24f to 1.5f)) {
            compose.runOnIdle { notices.value = null; fontScale = scale; options.value = options.value.copy(islandFontSize = font) }
            compose.mainClock.advanceTimeBy(2600)
            val fpsHeight = height()
            for ((index, name) in listOf("AI 调音大师", "AI 情绪调音师", "全景环绕").withIndex()) {
                compose.runOnIdle { notices.value = SoundEffectNotice(index.toLong(), name,
                    if (index == 2) SoundEffectNoticeTier.VIP else SoundEffectNoticeTier.SVIP) }
                compose.mainClock.advanceTimeBy(1400)
                assertEquals("$name at font=$font scale=$scale must match FPS height", fpsHeight, height(), 1f)
                compose.onNodeWithText(name).assertIsDisplayed()
                if (index < 2) compose.onNodeWithTag("sound_effect_island_wordmark").assertIsDisplayed()
                else compose.onNodeWithTag("sound_effect_island_title").assertIsDisplayed()
                val title = compose.onNodeWithTag(if (index < 2) "sound_effect_island_wordmark" else "sound_effect_island_title")
                    .getUnclippedBoundsInRoot()
                val pill = compose.onNodeWithTag("sound_effect_island_notice").getUnclippedBoundsInRoot()
                assertEquals("Text needs a 14 dp trailing inset clear of the capsule curve at font=$font scale=$scale",
                    14f, (pill.right - title.right).value, 1f)
                val context = ApplicationProvider.getApplicationContext<android.content.Context>()
                File(context.getExternalFilesDir(null), "compact-effect-$font-$index.png").outputStream().use {
                    compose.onNodeWithTag("desktop_island_transition").captureToImage().asAndroidBitmap()
                        .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun realViewModelFailureReachesInAppFallbackWithoutEnablingDesktopOrChangingQueue() {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = AppContainer(context, "island-notice-${UUID.randomUUID()}")
        val models = ViewModelStore()
        try {
            runBlocking { container.ready.await(); container.neteaseSessions.ready.await() }
            lateinit var vm: SoundEffectsViewModel
            compose.setContent { SoundEffectIslandFallback(container) }
            compose.runOnIdle { vm = SoundEffectsViewModel(container); models.put("notice", vm) }
            val queue = container.playbackQueue.state.value
            val desktop = container.desktopLyrics.state.value
            compose.runOnIdle { vm.originalAiMasterEnabled(true) }
            compose.waitUntil(8000) { container.soundEffectNotices.state.value?.phase == SoundEffectNoticePhase.FAILED }
            compose.onNodeWithTag("sound_effect_island_notice").assertIsDisplayed()
            compose.onNodeWithText("未启用").assertIsDisplayed()
            assertEquals(SoundEffectNoticeTier.SVIP, container.soundEffectNotices.state.value?.tier)
            assertEquals(queue, container.playbackQueue.state.value)
            assertEquals(desktop, container.desktopLyrics.state.value)
            compose.runOnIdle { vm.turnOff() }
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithTag("sound_effect_island_notice").assertDoesNotExist()
        } finally {
            compose.runOnIdle { models.clear(); container.close() }
        }
    }
}
