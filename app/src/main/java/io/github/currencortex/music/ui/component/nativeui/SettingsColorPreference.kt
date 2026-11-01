// SPDX-License-Identifier: GPL-3.0-only
package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import io.github.currencortex.music.ui.component.MusicIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A compact color row. Drafts belong to the sheet and are saved only after confirmation. */
@Composable internal fun SettingsColorPreference(title: String, value: Long, defaultColor: Long,
    presets: List<Pair<String, Long>>, tag: String, onChanged: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = MiuixTheme.colorScheme
    SettingsPreference(title, modifier = Modifier.testTag(tag), onClick = { open = true }, trailing = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("#${hexDigits(Color(value))}", Modifier.testTag("${tag}_value"), fontSize = 13.sp,
                color = colors.onSurfaceVariantSummary)
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(value))
                .border(1.dp, colors.onSurface.copy(alpha = .2f), CircleShape))
            Icon(MusicIcons.Forward, null, Modifier.size(16.dp), tint = colors.onSurfaceVariantSummary)
        }
    })
    if (open) SettingsColorSheet(title, value, defaultColor, presets, tag, { open = false }, onChanged)
}

/** Shared native color editor using the same grid, spectrum and HSV controls as Appearance. */
@Composable internal fun SettingsColorSheet(title: String, initial: Long, defaultColor: Long,
    presets: List<Pair<String, Long>>, tag: String, onClosed: () -> Unit, onConfirmed: (Long) -> Unit) {
    var visible by remember { mutableStateOf(true) }
    var draft by remember { mutableStateOf(Color(initial)) }
    var hex by remember { mutableStateOf(hexDigits(draft)) }
    var confirmed by remember { mutableStateOf<Long?>(null) }
    val closed by rememberUpdatedState(onClosed)
    val saved by rememberUpdatedState(onConfirmed)
    val colors = MiuixTheme.colorScheme
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val backdrop = rememberLayerBackdrop(onDraw = { drawRect(colors.surfaceContainer) })
    val height = (LocalWindowInfo.current.containerDpSize.height -
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()) * .9f
    fun pick(color: Color) { draft = color; hex = hexDigits(color) }
    fun dismiss() { focus.clearFocus(); keyboard?.hide(); visible = false }
    OverlayBottomSheet(show = visible, title = title,
        modifier = Modifier.height(height).layerBackdrop(backdrop).testTag("${tag}_sheet"),
        backgroundColor = colors.surfaceContainer, cornerRadius = 32.dp,
        insideMargin = DpSize(16.dp, 0.dp), outsideMargin = DpSize.Zero,
        startAction = {
            GlassIconButton(::dismiss, "取消", Modifier.testTag("${tag}_cancel"), backdrop = backdrop) {
                Icon(MusicIcons.Close, null, Modifier.size(24.dp))
            }
        }, endAction = {
            GlassIconButton({ confirmed = draft.toArgb().toLong() and 0xFFFFFFFF; dismiss() }, "应用颜色",
                Modifier.testTag("${tag}_apply"), enabled = hex.length == 6, backdrop = backdrop) {
                Icon(MusicIcons.Check, null, Modifier.size(24.dp))
            }
        }, onDismissRequest = ::dismiss, onDismissFinished = {
            closed(); confirmed?.let(saved)
        }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(draft)
                    .border(1.dp, colors.onSurface.copy(alpha = .2f), CircleShape))
                BasicTextField(hex, { input ->
                    hex = input.removePrefix("#").filter { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }.take(6).uppercase()
                    if (hex.length == 6) draft = Color(0xFF000000 or hex.toLong(16))
                }, Modifier.weight(1f).clip(CircleShape).background(colors.surfaceContainerHigh)
                    .testTag("${tag}_hex").semantics { contentDescription = "HEX 颜色" }, singleLine = true,
                    textStyle = TextStyle(color = colors.onSurface, fontSize = 17.sp, lineHeight = 22.sp),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii,
                        capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }),
                    decorationBox = { input -> Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("#", color = colors.onSurfaceVariantSummary); input()
                    } })
                Box(Modifier.size(36.dp)) {
                    if (draft.toArgb() != Color(defaultColor).toArgb()) SettingsHeaderReset({ pick(Color(defaultColor)) },
                        "恢复默认颜色", Modifier.testTag("${tag}_reset"))
                }
            }
            if (presets.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                presets.forEach { (label, color) ->
                    val selected = draft.toArgb() == Color(color).toArgb()
                    Box(Modifier.size(44.dp).testTag("${tag}_${hexDigits(Color(color))}")
                        .semantics { contentDescription = "$title：$label" }
                        .selectable(selected, role = Role.RadioButton, onClick = { pick(Color(color)) }),
                        contentAlignment = Alignment.Center) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(color))
                            .border(if (selected) 2.dp else 1.dp,
                                if (selected) colors.primary else colors.onSurface.copy(alpha = .2f), CircleShape))
                    }
                }
            }
            MiuixTheme(colors = colors.copy(surfaceContainer = colors.surfaceContainerHigh)) {
                ColorPalette(draft, ::pick)
            }
        }
    }
}
