package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.MusicArtworkCard
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class MusicLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun coverPlaceholderMatchesFinalCardHeightInLightAndLargeDarkType() {
        val settings = mutableStateOf(AppearanceSettings(themeMode = ThemeMode.LIGHT))
        compose.setContent { LeiTheme(settings.value) {
            Column(Modifier.fillMaxSize().background(top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LoadingArtworkCard(Modifier.weight(1f).testTag("loading_card"))
                    MusicArtworkCard("", "加载完成后的两行歌曲名称", "歌手", {}, Modifier.weight(1f).testTag("ready_card"))
                }
                LoadingSongList(2, searchActions = true)
                LoadingProfileCard()
                LoadingPostList(1)
            }
        } }
        for (large in listOf(false, true)) {
            compose.runOnIdle { settings.value = AppearanceSettings(themeMode = if (large) ThemeMode.DARK else ThemeMode.LIGHT,
                customTextScale = large, textScale = 1.4f) }
            capture(if (large) "loading-new-dark-large.png" else "loading-new-light.png")
            val loading = compose.onNodeWithTag("loading_card").fetchSemanticsNode().boundsInRoot
            val ready = compose.onNodeWithTag("ready_card").fetchSemanticsNode().boundsInRoot
            assertEquals("Loading must reserve the final card's width", ready.width, loading.width, 1f)
            assertEquals("Loading must reserve its two-line title and artist", ready.height, loading.height, 2f)
        }
    }

    @Test fun placeholdersExposeOneLoadingAnnouncementAndNoFakeButtons() {
        compose.setContent { LeiTheme(AppearanceSettings()) {
            Column(Modifier.fillMaxSize()) { LoadingSongList(2, searchActions = true); LoadingProfileCard(); LoadingPostList(1) }
        } }
        for (tag in listOf("music_loading", "profile_loading", "posts_loading")) {
            val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
            assertEquals(ProgressBarRangeInfo.Indeterminate, node.config[SemanticsProperties.ProgressBarRangeInfo])
        }
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test fun retainedInactivePageStopsThePlaceholderPulse() {
        val active = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { LeiTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT)) {
            CompositionLocalProvider(LocalMusicPageActive provides active.value) {
                Box(Modifier.size(100.dp).background(Color.White)) {
                    MusicPlaceholderScope { MusicPlaceholder(Modifier.fillMaxSize().testTag("pulse")) }
                }
            }
        } }
        fun shade(): Color {
            val pixels = compose.onNodeWithTag("pulse").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        compose.mainClock.advanceTimeBy(32)
        val first = shade()
        compose.mainClock.advanceTimeBy(700)
        assertNotEquals("Visible loading should have a gentle pulse", first, shade())
        compose.runOnIdle { active.value = false }
        compose.mainClock.advanceTimeBy(32)
        val stopped = shade()
        compose.mainClock.advanceTimeBy(1800)
        assertEquals("Hidden retained pages must stop animating", stopped, shade())
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
