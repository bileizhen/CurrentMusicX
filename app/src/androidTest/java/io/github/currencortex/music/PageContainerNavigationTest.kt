package io.github.currencortex.music

import androidx.activity.BackEventCompat
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.component.PageContainerNavigation
import io.github.currencortex.music.ui.component.PageContainerSceneStrategy
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.TextButton

/** Navigation layer regression without library/account/network dependencies. */
class PageContainerNavigationTest {
    @get:Rule val compose=createComposeRule()
    @Test fun ordinaryPagesKeepTheirPredictiveSlideAndCancelWithoutPopping() {
        lateinit var dispatcher:androidx.activity.OnBackPressedDispatcher
        compose.setContent {LeiTheme(AppearanceSettings(blur=false)) {
            dispatcher=LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            var stack by remember {mutableStateOf(listOf("root"))}
            val strategy=remember {PageContainerSceneStrategy(PageContainerNavigation(),emptySet())}
            NavDisplay(backStack=stack,sceneStrategies=listOf(strategy),onBack={stack=stack.dropLast(1)},
                entryProvider=entryProvider {
                    entry("root") {Box(Modifier.fillMaxSize().background(Color.White).testTag("normal_root")) {
                        TextButton("打开",onClick={stack=stack+"child"},modifier=Modifier.testTag("normal_open"))
                    }}
                    entry("child") {Box(Modifier.fillMaxSize().background(Color.Blue).testTag("normal_child"))}
                })
        }}
        compose.onNodeWithTag("normal_open").performClick();compose.waitForIdle()
        val child=compose.onNodeWithTag("normal_child")
        val original=child.fetchSemanticsNode().boundsInRoot
        fun start()=compose.runOnUiThread {dispatcher.dispatchOnBackStarted(BackEventCompat(0f,500f,0f,BackEventCompat.EDGE_LEFT))}
        fun progress(p:Float) {
            compose.runOnUiThread {dispatcher.dispatchOnBackProgressed(BackEventCompat(p*original.width,500f,p,BackEventCompat.EDGE_LEFT))}
            compose.waitForIdle()
        }
        start();progress(.2f)
        val first=child.fetchSemanticsNode().boundsInRoot.left
        progress(.6f)
        assertTrue("Normal pages still follow predictive slide progress",child.fetchSemanticsNode().boundsInRoot.left>first+1f)
        compose.runOnUiThread {dispatcher.dispatchOnBackCancelled()};compose.waitForIdle()
        assertEquals(original,child.fetchSemanticsNode().boundsInRoot)
        start();progress(.5f)
        compose.runOnUiThread {dispatcher.onBackPressed()};compose.waitForIdle()
        child.assertDoesNotExist();compose.onNodeWithTag("normal_root").assertIsDisplayed()
    }
}
