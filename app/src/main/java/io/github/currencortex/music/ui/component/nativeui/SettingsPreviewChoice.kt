// SPDX-License-Identifier: GPL-3.0-only
// PreviewChoiceGroup from upstream lib/views/theme_preview.dart @ 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7.
package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.MusicIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun SettingsInfoHeader(title: String, icon: ImageVector, modifier: Modifier = Modifier,
    horizontalInset: androidx.compose.ui.unit.Dp = 16.dp,
    actions: @Composable RowScope.() -> Unit = {}) {
    // InfoHeader: 16dp vertical padding plus max(24dp glyph, measured 20sp text line).
    val fontScale = LocalDensity.current.fontScale
    val inset = 16f * (1 + (fontScale - 1) * .5f).coerceAtMost(1f)
    val height = (kotlin.math.max(24f, 20f * fontScale) + inset * 2).dp
    Row(modifier.fillMaxWidth().padding(horizontal = horizontalInset).heightIn(min = height),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(24.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(title, Modifier.weight(1f), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        actions()
    }
}

@Composable internal fun SettingsFadingReset(visible: Boolean, onClick: () -> Unit, description: String, modifier: Modifier = Modifier) {
    Box(Modifier.size(36.dp)) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            SettingsHeaderReset(onClick, description, modifier)
        }
    }
}

@Composable internal fun SettingsSelectionCheck(selected: Boolean, modifier: Modifier = Modifier.size(20.dp),
    color: Color = MiuixTheme.colorScheme.primary) {
    val amount by animateFloatAsState(if (selected) 1f else 0f,
        tween(200, easing = CubicBezierEasing(.175f, .885f, .32f, 1.275f)), label = "selection check")
    Icon(MusicIcons.Check, null, modifier.graphicsLayer {
        alpha = amount.coerceIn(0f, 1f); scaleX = amount.coerceAtLeast(0f); scaleY = scaleX
    }, tint = color)
}

/** upstream's section-size tonal reset: flat fill, no glass material or shadow. */
@Composable internal fun SettingsHeaderReset(onClick: () -> Unit, description: String, modifier: Modifier = Modifier) {
    ElasticPress(modifier.size(36.dp).semantics { contentDescription = description }
        .clip(CircleShape).clickable(role = Role.Button, onClick = onClick)) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center) {
            Icon(MusicIcons.Reset, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.onSurface)
        }
    }
}

/** A whole row selects; the miniature and check are decorative, with one accessible owner. */
@Composable internal fun SettingsPreviewChoiceGroup(title: String, icon: ImageVector, labels: List<String>,
    selected: Int, onSelect: (Int) -> Unit, tag: String, enabled: (Int) -> Boolean = { true },
    pictogram: @Composable (Int) -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(Modifier.testTag(tag), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsInfoHeader(title, icon)
        SettingsGroup(Modifier.padding(horizontal = 16.dp).semantics { selectableGroup() }) {
            labels.forEachIndexed { index, label ->
                SettingsPreference(label, modifier = Modifier.semantics { this.selected = selected == index }
                    .testTag("${tag}_$index").drawWithContent {
                        drawContent()
                        if (index < labels.lastIndex) drawLine(colors.onSurface.copy(alpha = .08f),
                            Offset(72.dp.toPx(), size.height), Offset(size.width - 16.dp.toPx(), size.height), .5.dp.toPx())
                    }, enabled = enabled(index), onClick = { onSelect(index) }, role = Role.RadioButton,
                    leading = { Box(Modifier.clearAndSetSemantics { }) { pictogram(index) } },
                    trailing = { SettingsSelectionCheck(selected == index) })
            }
        }
    }
}
