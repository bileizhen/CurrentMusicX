package io.github.currencortex.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.theme.LeiTheme
import io.github.currencortex.music.data.settings.AppearanceSettings
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real list consumption and dock geometry, with no accounts, playback or network. */
class RootDockScrollTest {
    @get:Rule val compose = createComposeRule()
    private var tab by mutableIntStateOf(0)
    private var miniPresent by mutableStateOf(true)
    private var interactive by mutableStateOf(true)
    private var opens = 0
    private lateinit var dockBehavior: RootDockScrollBehavior

    @Composable private fun Fixture(glass: Boolean = true) {
        LeiTheme(AppearanceSettings(blur = false)) {
        val behavior = rememberRootDockScrollBehavior(tab, miniPresent, miniPresent && interactive)
        dockBehavior = behavior
        val list = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().testTag("content").nestedScroll(behavior), state = list,
                contentPadding = PaddingValues(bottom = 170.dp)) {
                item {
                    LazyRow(Modifier.fillMaxWidth().height(90.dp).testTag("carousel")) {
                        items(30) { BasicText("Cover $it", Modifier.width(130.dp)) }
                    }
                }
                items(100) { BasicText("Row $it", Modifier.fillMaxWidth().height(70.dp)) }
            }
            val mini: @Composable () -> Unit = {
                BasicText("Mini player", Modifier.fillMaxWidth().height(52.dp).testTag("mini")
                    .clickable { opens++ })
            }
            val navigation: @Composable () -> Unit = {
                BasicText("Navigation", Modifier.fillMaxWidth().height(64.dp).testTag("navigation"))
            }
            if (glass) UnifiedMusicDock(expanded = !behavior.collapsed, visible = true,
                miniPresent = miniPresent, navigationInteractive = !behavior.collapsed,
                modifier = Modifier.align(Alignment.BottomCenter), mini = mini,
                navigation = { navigation() })
            else ScrollCollapsingNavigation(!behavior.collapsed, Modifier.align(Alignment.BottomCenter)) { navigation() }
        }
        }
    }

    private fun screenshot(name: String) {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        java.io.File(directory, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun realVerticalScrollFoldsDockAndReverseScrollRestoresWithoutResizingPage() {
        compose.setContent { Fixture() }
        val bounds = compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
        screenshot("dock-expanded.png")
        compose.onNodeWithTag("content").performTouchInput { swipeUp(startY = height * .7f, endY = height * .25f) }
        compose.waitForIdle()
        compose.onNodeWithTag("music_dock").assert(SemanticsMatcher.expectValue(DockExpansion, 0f))
        compose.onNodeWithTag("navigation").assertDoesNotExist()
        screenshot("dock-collapsed.png")
        compose.onNodeWithTag("mini").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, opens) }
        assertEquals(bounds, compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("content").performTouchInput { swipeDown(startY = height * .25f, endY = height * .7f) }
        compose.waitForIdle()
        compose.onNodeWithTag("music_dock").assert(SemanticsMatcher.expectValue(DockExpansion, 1f))
        compose.onNodeWithTag("navigation").assertIsDisplayed()
        assertEquals(bounds, compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun horizontalCarouselDoesNotCollapseAndChangingTabsRestoresNavigation() {
        compose.setContent { Fixture() }
        compose.onNodeWithTag("carousel").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("music_dock").assert(SemanticsMatcher.expectValue(DockExpansion, 1f))
        compose.onNodeWithTag("content").performTouchInput { swipeUp(startY = height * .7f, endY = height * .25f) }
        compose.runOnIdle { tab = 1 }
        compose.waitForIdle()
        compose.onNodeWithTag("navigation").assertIsDisplayed()
    }

    @Test fun noSongOrCoveredPageKeepsNavigationAvailable() {
        miniPresent = false
        compose.setContent { Fixture() }
        compose.onNodeWithTag("content").performTouchInput { swipeUp(startY = height * .7f, endY = height * .25f) }
        compose.onNodeWithTag("navigation").assertIsDisplayed()
        compose.runOnIdle { miniPresent = true; interactive = false }
        compose.onNodeWithTag("content").performTouchInput { swipeUp(startY = height * .7f, endY = height * .25f) }
        compose.onNodeWithTag("navigation").assertIsDisplayed()
    }

    @Test fun standardNavigationAlsoHidesAndReturns() {
        compose.setContent { Fixture(glass = false) }
        compose.onNodeWithTag("content").performTouchInput { swipeUp(startY = height * .7f, endY = height * .25f) }
        compose.waitForIdle()
        screenshot("dock-standard-after-scroll.png")
        compose.runOnIdle { assertTrue("The content scroll must collapse standard navigation", dockBehavior.collapsed) }
        compose.onNodeWithTag("navigation").assertDoesNotExist()
        compose.onNodeWithTag("content").performTouchInput { swipeDown(startY = height * .25f, endY = height * .7f) }
        compose.waitForIdle()
        compose.onNodeWithTag("navigation").assertIsDisplayed()
    }
}
