// SPDX-License-Identifier: GPL-3.0-only
// ListHeader/generateSection: upstream lib/ui/list.dart and metrics.dart.
package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.PreferenceCategoryMarginStartType

/** Plain list headings, distinct from the icon-led InfoHeader used on the theme page. */
@Composable internal fun SettingsListHeader(title: String, modifier: Modifier = Modifier,
    marginStartType: PreferenceCategoryMarginStartType = PreferenceCategoryMarginStartType.Large,
    actions: @Composable RowScope.() -> Unit = {}) {
    PreferenceCategory(
        title = title,
        modifier = modifier,
        marginStartType = marginStartType,
        endWidget = actions,
    )
}

/** A titled settings section; the same hierarchy as [SettingsSection], for plain text headings. */
@Composable internal fun SettingsListSection(title: String, modifier: Modifier = Modifier,
    footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    SettingsSection(title, modifier, footer, content)
}
