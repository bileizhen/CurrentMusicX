// SPDX-License-Identifier: GPL-3.0-only
package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.suqi8.coui.kmp.basic.SegmentButton

/** Small exclusive choice row; the app's thin wrapper over COUI's own SegmentButton. */
@Composable internal fun SettingsSegmentedControl(labels: List<String>, selected: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, tag: String = "settings_segment", enabled: Boolean = true) {
    require(labels.isNotEmpty())
    SegmentButton(items = labels, selectedIndex = selected, onSelectedIndexChange = onSelect,
        modifier = modifier.fillMaxWidth().testTag(tag), enabled = enabled, responsiveWidthEnabled = false)
}