// SPDX-License-Identifier: GPL-3.0-only
// upstream theme.dart _CustomColorSheet/_CustomColorPreview and widgets/palette.dart
// @ 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7, hosted by the native sheet.
package io.github.currencortex.music.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.theme.rememberAppearanceColors
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Confirmation is delivered after the exit animation, so retheming cannot stall it. */
@Composable internal fun AppearanceColorSheet(config: AppearanceSettings, initialColor: Long, onClosed: () -> Unit,
    onConfirmed: (Long) -> Unit, onDeleted: (() -> Unit)? = null) {
    var visible by remember { mutableStateOf(true) }
    var draft by remember { mutableStateOf(Color(initialColor)) }
    var hex by remember { mutableStateOf(hexDigits(draft)) }
    var confirmed by remember { mutableStateOf<Long?>(null) }
    var deleted by remember { mutableStateOf(false) }
    val colors = MiuixTheme.colorScheme
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Capture only the inert sheet color, excluding its controls, to avoid feedback.
    val backdrop = rememberLayerBackdrop(onDraw = { drawRect(colors.surfaceContainer) })
    val preview = config.copy(monet = false, primaryColor = draft.toArgb().toLong() and 0xFFFFFFFF)
    val previewRenderer = rememberCurrentMusicPreviewRenderer()
    val light = rememberAppearanceColors(preview, false)
    val dark = rememberAppearanceColors(preview, true)
    val height = (LocalWindowInfo.current.containerDpSize.height -
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()) * .9f
    val currentConfirmed by rememberUpdatedState(onConfirmed)
    val currentClosed by rememberUpdatedState(onClosed)
    val currentDeleted by rememberUpdatedState(onDeleted)
    fun pick(color: Color) { draft = color; hex = hexDigits(color) }
    fun dismiss() { focus.clearFocus(); keyboard?.hide(); visible = false }
    OverlayBottomSheet(show = visible, title = "自定义颜色", modifier = Modifier.height(height).layerBackdrop(backdrop).testTag("appearance_color_sheet"),
        backgroundColor = colors.surfaceContainer, cornerRadius = 32.dp,
        insideMargin = DpSize(16.dp, 0.dp), outsideMargin = DpSize(0.dp, 0.dp),
        startAction = {
            GlassIconButton(::dismiss, "取消", Modifier.testTag("appearance_color_cancel"), backdrop = backdrop) {
                Icon(MusicIcons.Close, null, Modifier.size(24.dp), tint = colors.onSurface)
            }
        }, endAction = {
            GlassIconButton({ confirmed = draft.toArgb().toLong() and 0xFFFFFFFF; dismiss() }, "确认颜色",
                Modifier.testTag("appearance_color_save"), enabled = hex.length == 6, backdrop = backdrop) {
                Icon(MusicIcons.Check, null, Modifier.size(24.dp), tint = colors.onSurface.copy(alpha = if (hex.length == 6) 1f else .35f))
            }
        }, onDismissRequest = ::dismiss, onDismissFinished = {
            currentClosed()
            confirmed?.let(currentConfirmed)
            if (deleted) currentDeleted?.invoke()
        }) {
        // The source sheet contributes a 14dp gutter around its 16dp body inset.
        Column(Modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 0.dp, bottom = 20.dp)
            .navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceContainerHigh).padding(12.dp).testTag("appearance_color_preview")
                .semantics { stateDescription = "#${hexDigits(draft)}" }) {
                val screenWidth = minOf(72.dp, ((maxWidth - 128.dp) / 2).coerceAtLeast(32.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(light, dark).forEachIndexed { index, palette ->
                        Canvas(Modifier.width(screenWidth).aspectRatio(3f / 4).graphicsLayer {
                            shape = RoundedCornerShape(14.dp); clip = true
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                            .testTag(if (index == 0) "appearance_color_preview_light" else "appearance_color_preview_dark")) {
                            previewRenderer.draw(this, palette, true, config.liquidGlass, 0)
                        }
                    }
                    BasicTextField(hex, { text ->
                        hex = text.removePrefix("#").filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.take(6).uppercase()
                        if (hex.length == 6) draft = Color(0xFF000000 or hex.toLong(16))
                    }, Modifier.weight(1f).clip(CircleShape).background(colors.surfaceContainerHighest)
                        .testTag("appearance_color_hex").semantics { contentDescription = "HEX 颜色" },
                        singleLine = true, textStyle = TextStyle(color = colors.onSurface, fontSize = 17.sp, lineHeight = 22.sp,
                            fontFeatureSettings = "tnum"), cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }),
                        decorationBox = { input ->
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("#", fontSize = 17.sp, lineHeight = 22.sp, color = colors.onSurfaceVariantSummary)
                                Box(Modifier.weight(1f)) { input() }
                            }
                        })
                }
            }
            // Raised cards use the next surface level inside the sheet.
            MiuixTheme(colors = colors.copy(surfaceContainer = colors.surfaceContainerHigh)) {
                ColorPalette(draft, ::pick)
            }
            if (onDeleted != null) GlassActionButton("删除颜色", { deleted = true; dismiss() },
                Modifier.testTag("appearance_color_delete"), destructive = true, backdrop = null)
        }
    }
}
