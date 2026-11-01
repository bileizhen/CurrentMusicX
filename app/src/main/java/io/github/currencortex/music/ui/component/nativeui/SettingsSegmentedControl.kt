// SPDX-License-Identifier: GPL-3.0-only
// Direct native port of upstream lib/ui/segmented_control.dart
// @ 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7: 44dp high, 4dp inset, 250ms easeOutCubic.
package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun SettingsSegmentedControl(labels: List<String>, selected: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, tag: String = "settings_segment", role: Role = Role.RadioButton,
    enabled: Boolean = true, onReselect: () -> Unit = {}, itemTag: (Int) -> String = { "${tag}_$it" }) {
    require(labels.isNotEmpty())
    val colors = MiuixTheme.colorScheme
    val position by animateFloatAsState(selected.toFloat(), tween(250, easing = CubicBezierEasing(.215f, .61f, .355f, 1f)), label = "segmented selection")
    BoxWithConstraints(modifier.fillMaxWidth().height(44.dp).clip(CircleShape).background(colors.surfaceContainer)
        .padding(4.dp).semantics { selectableGroup() }.testTag(tag)) {
        val width = maxWidth / labels.size
        if (selected in labels.indices) Box(Modifier.offset(x = width * position).width(width).fillMaxHeight()
            .clip(CircleShape).background(colors.primary))
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == selected
                val ink by animateColorAsState(if (isSelected) colors.onPrimary else colors.onSurfaceVariantSummary,
                    tween(200), label = "segment text")
                Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                    .selectable(isSelected, enabled = enabled, role = role, onClick = { if (!isSelected) onSelect(index) else onReselect() })
                    .testTag(itemTag(index)), contentAlignment = Alignment.Center) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, lineHeight = 20.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = ink)
                }
            }
        }
    }
}
