package io.github.currencortex.music

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.currencortex.music.data.netease.listen.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.room.ListenTogetherFateDeleteDialog
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Confirmation fixtures have no repository and cannot modify a real NetEase account. */
class ListenTogetherFateDeleteUiTest {
    @get:Rule val compose = createComposeRule()
    private val report = ListenTogetherFateReport(9, ListenTogetherParticipant(7, "自己"),
        ListenTogetherParticipant(9, "听友"), ltType = 1, openActivity = true)

    @Test fun openingAndCancellingDoesNotConfirmAndHistoryDefaultsToRetained() {
        val visible = mutableStateOf(true)
        val confirmations = mutableListOf<Boolean>()
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) { top.yukonga.miuix.kmp.basic.Scaffold {
            if (visible.value) ListenTogetherFateDeleteDialog(report, { visible.value = false }, confirmations::add)
        } } }
        compose.onNodeWithTag("listen_fate_delete_records").assertIsOff()
        compose.onNodeWithText("关联的一起听徽章及其进度也会被回收。").assertExists()
        assertTrue(confirmations.isEmpty())
        compose.onNodeWithTag("listen_fate_delete_cancel").performClick()
        assertTrue(confirmations.isEmpty())
    }

    @Test fun confirmingHistoryDeletionRequiresTurningOnTheSeparateOption() {
        var option: Boolean? = null
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) { top.yukonga.miuix.kmp.basic.Scaffold {
            ListenTogetherFateDeleteDialog(report, {}, { option = it })
        } } }
        compose.onNodeWithTag("listen_fate_delete_confirm").performClick()
        assertEquals(false, option)
        compose.onNodeWithTag("listen_fate_delete_records").performClick().assertIsOn()
        compose.onNodeWithTag("listen_fate_delete_confirm").performClick()
        assertEquals(true, option)
    }
}
