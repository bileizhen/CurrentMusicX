package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.currencortex.music.data.profile.Decoration
import io.github.currencortex.music.data.profile.DecorationsDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.feature.profile.DecorationPage
import io.github.currencortex.music.feature.profile.DecorationState
import io.github.currencortex.music.ui.component.DecoratedAvatar
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DecorationScreenTest {
    @get:Rule val compose = createComposeRule()
    private val frames = listOf(Decoration("frame-one", "星光"), Decoration("frame-two", "长长长长的挂件名称"))
    private var submitted: String? = null
    private var refreshes = 0
    private fun show(unlocked: Boolean, dark: Boolean = false, initialError: Boolean = false) {
        compose.setContent {
            var state by remember { mutableStateOf(if (initialError) DecorationState(error = "网络暂时不可用")
                else DecorationState(catalog = DecorationsDto(frames, "frame-one", unlocked, 3600000, 7200000))) }
            LeiTheme(AppearanceSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, blur = false)) {
                DecorationPage(state, false, null, {}, { refreshes++ }, { state = state.copy(query = it) },
                    { state = state.copy(preview = it) }, { submitted = it }) { _, size -> DecoratedAvatar(null, null, size = size) }
            }
        }
    }
    @Test fun choosingAndRestoringPreviewNeverSubmitsUntilConfirmed() {
        show(true)
        compose.onNodeWithTag("wear_decoration").assertIsNotEnabled()
        compose.onNodeWithTag("decoration_frame-two").performClick().assertIsSelected()
        compose.onNodeWithTag("decoration_preview_name").assertTextEquals(frames[1].name)
        assertNull(submitted)
        compose.onNodeWithText("还原").performClick()
        compose.onNodeWithTag("decoration_frame-one").assertIsSelected()
        compose.onNodeWithTag("wear_decoration").assertIsNotEnabled()
        compose.onNodeWithTag("decoration_frame-two").performClick()
        compose.onNodeWithTag("wear_decoration").performClick()
        assertEquals("frame-two", submitted)
    }
    @Test fun lockedCatalogAllowsPreviewAndRemovingCurrentFrameButNotWearingNewOne() {
        show(false, dark = true)
        compose.onNodeWithTag("decoration_frame-two").performClick().assertIsSelected()
        compose.onNodeWithTag("wear_decoration").assertIsNotEnabled()
        compose.onNodeWithText("听歌解锁 · 可先试戴").assertIsDisplayed()
        compose.onNodeWithTag("decoration_none").performClick()
        compose.onNodeWithTag("wear_decoration").assertIsEnabled().performClick()
        assertEquals("", submitted)
    }
    @Test fun emptySearchCanBeClearedWithoutLosingPreview() {
        show(true)
        compose.onNodeWithTag("decoration_frame-two").performClick()
        compose.onNodeWithTag("decoration_query").performTextInput("no match")
        compose.onNodeWithText("没有找到相关挂件").assertIsDisplayed()
        compose.onNodeWithText("清除搜索").performClick()
        compose.onNodeWithTag("decoration_frame-two").assertIsSelected()
        compose.onNodeWithTag("decoration_preview_name").assertTextEquals(frames[1].name)
        assertNull(submitted)
    }
    @Test fun initialFailureProvidesRetryWithoutAnUnusableApplyButton() {
        show(false, initialError = true)
        compose.onNodeWithText("网络暂时不可用").assertIsDisplayed()
        compose.onNodeWithText("重新加载").performClick()
        assertEquals(1, refreshes)
        compose.onNodeWithTag("wear_decoration").assertDoesNotExist()
    }
}
