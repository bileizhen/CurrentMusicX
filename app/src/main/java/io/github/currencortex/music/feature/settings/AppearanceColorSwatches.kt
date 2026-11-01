// SPDX-License-Identifier: GPL-3.0-only
// _ColorSwatches, _ColorSwatch, _EyedropperBadge and _AddColorButton from upstream
// lib/views/theme.dart @ 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7.
package io.github.currencortex.music.feature.settings

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.nativeui.SettingsSelectionCheck
import io.github.currencortex.music.ui.component.nativeui.settingsPressFeedback
import io.github.currencortex.music.ui.component.nativeui.settingsInkWellColors
import io.github.currencortex.music.ui.theme.LocalDarkTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.*
import kotlin.math.floor

@Composable internal fun AppearanceColorSwatches(config: AppearanceSettings, onSelect: (Long?) -> Unit,
    onEdit: (Long) -> Unit, onAdd: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val ink = settingsInkWellColors()
    val dark = LocalDarkTheme.current
    val systemController = remember(dark) { ThemeController(colorSchemeMode = if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight) }
    val systemColor = if (Build.VERSION.SDK_INT >= 31) systemController.currentColors().primary else Color(0xFF007AFF)
    // 16 - (44 - 32) / 2. Dot edges line up with the text in neighbouring list rows.
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("appearance_palette").clip(RoundedCornerShape(24.dp))
        .background(colors.surfaceContainer).padding(10.dp)) {
        val columns = floor((maxWidth.value + 6) / 50).toInt().coerceAtLeast(1)
        val desiredGap = if (columns > 1) floor((maxWidth.value - columns * 44) / (columns - 1)).dp else 0.dp
        // Flutter Wrap keeps fractional layout pixels. Compose rounds each gap
        // separately: 9dp becomes 32px at 3.5x, making six 154px cells overflow
        // a 1082px row by 2px. Budget the gap in the pixels FlowRow actually uses.
        val gap = with(LocalDensity.current) {
            val cellPixels = 44.dp.roundToPx()
            val gapPixels = if (columns > 1) ((constraints.maxWidth - columns * cellPixels) / (columns - 1)).coerceAtLeast(0) else 0
            minOf(desiredGap.roundToPx(), gapPixels).toDp()
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap),
            maxItemsInEachRow = columns) {
            (listOf<Long?>(null) + config.primaryColors).forEach { color ->
                val selected = if (color == null) config.monet else !config.monet && config.primaryColor == color
                val fill = color?.let(::Color) ?: systemColor
                val interactions = remember { MutableInteractionSource() }
                val focused by interactions.collectIsFocusedAsState()
                val ring by animateFloatAsState(if (selected || focused) 1f else 0f, tween(200), label = "swatch ring")
                val label = color?.let { "#%06X".format(it and 0xFFFFFF) } ?: "跟随系统"
                Box(Modifier.size(44.dp).clip(CircleShape)
                    .combinedClickable(enabled = color != null || Build.VERSION.SDK_INT >= 31, role = Role.RadioButton,
                        interactionSource = interactions, indication = null, onClick = { onSelect(color) },
                        onLongClick = color?.let { { onEdit(it) } })
                    .settingsPressFeedback(interactions, ink)
                    .semantics { this.selected = selected; contentDescription = label }
                    .testTag(if (color == null) "setting_monet" else "appearance_color_${color.toString(16)}")
                    .drawBehind {
                        if (ring > 0f) drawCircle((if (selected) fill else colors.primary).copy(alpha = ring.coerceIn(0f, 1f)),
                            radius = size.minDimension / 2 - 1.25.dp.toPx(), style = Stroke(2.5.dp.toPx()))
                    }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(fill))
                    if (color == null) Box(Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape)
                        .background(colors.surfaceContainer).padding(2.dp)) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                            Icon(MusicIcons.Eyedropper, null, Modifier.size(12.dp), tint = colors.onSurface)
                        }
                    }
                    val luminance = fill.luminance() + .05f
                    SettingsSelectionCheck(selected, color = if (luminance * luminance > .15f) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp))
                }
            }
            val addInteractions = remember { MutableInteractionSource() }
            Box(Modifier.size(44.dp).clip(CircleShape)
                .clickable(enabled = config.primaryColors.size < 32, interactionSource = addInteractions, indication = null, onClick = onAdd)
                .settingsPressFeedback(addInteractions, ink)
                .semantics { contentDescription = "添加主题色" }.testTag("appearance_color_add"), contentAlignment = Alignment.Center) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    Icon(MusicIcons.PaletteAdd, null, Modifier.size(20.dp), tint = colors.onSurfaceVariantSummary)
                }
            }
        }
    }
}
