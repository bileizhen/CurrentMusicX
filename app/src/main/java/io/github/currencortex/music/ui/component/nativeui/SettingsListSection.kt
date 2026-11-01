// SPDX-License-Identifier: GPL-3.0-only
// ListHeader/generateSection: upstream lib/ui/list.dart and metrics.dart.
package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Plain list headings, distinct from the icon-led InfoHeader used on the theme page. */
@Composable internal fun SettingsListHeader(title: String, modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}) {
    val scale = LocalDensity.current.fontScale
    val adaptive = (1 + (scale - 1) * .5f).coerceAtMost(1f)
    val overhang = (36f - 22f * scale) / 2
    Row(modifier.fillMaxWidth().padding(start = (16 * adaptive).dp, end = (16 * adaptive).dp,
        top = (24 * adaptive - overhang).coerceAtLeast(0f).dp,
        bottom = (8 * adaptive - overhang).coerceAtLeast(6f).dp).heightIn(min = 36.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f).semantics { heading() }, fontSize = 17.sp, lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        actions()
    }
}

@Composable internal fun SettingsListSection(title: String, modifier: Modifier = Modifier,
    footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        SettingsListHeader(title)
        SettingsGroup(content = content)
        footer?.takeIf { it.isNotBlank() }?.let { SettingsHint(it) }
    }
}
