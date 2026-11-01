// SPDX-License-Identifier: GPL-3.0-only
// Shared native port of upstream lib/ui/search_field.dart.
package io.github.currencortex.music.ui.component.nativeui

import android.icu.text.BreakIterator
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.theme.LocalCustomFont
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

private fun limitSearchInput(text: String): String {
    // LengthLimitingTextInputFormatter(256) counts grapheme clusters, including composed emoji.
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    iterator.first()
    repeat(256) { if (iterator.next() == BreakIterator.DONE) return text }
    return text.substring(0, iterator.current())
}

@Composable internal fun GlassSearchField(value: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier, requester: FocusRequester, inputTag: String,
    placeholder: String = "搜索", onInputFocus: (Boolean) -> Unit = {},
    onSubmit: (() -> Unit)? = null, submitTag: String? = null,
    clearTag: String = "${inputTag}_clear", onActivate: (() -> Unit)? = null, enabled: Boolean = true) {
    val colors = MiuixTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    Row(modifier.fillMaxSize().then(if (onActivate != null) Modifier.clickable(enabled = enabled, role = Role.Button,
        interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onActivate) else Modifier),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(41.dp).fillMaxHeight().clickable(enabled = enabled,
            interactionSource = remember { MutableInteractionSource() }, indication = null) {
            if (onSubmit != null) onSubmit()
            else if (onActivate != null) onActivate()
            else { requester.requestFocus(); keyboard?.show() }
        }.then(if (submitTag != null) Modifier.testTag(submitTag).semantics { contentDescription = "搜索" } else Modifier),
            contentAlignment = Alignment.CenterStart) {
            Icon(MusicIcons.Search, null, Modifier.padding(start = 12.dp).size(20.dp), tint = colors.onSurface)
        }
        if (onActivate != null) Text(value.ifEmpty { placeholder }, Modifier.weight(1f).padding(end = 12.dp),
            fontSize = 17.sp, lineHeight = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (value.isEmpty()) colors.onSurfaceVariantSummary else colors.onSurface)
        else BasicTextField(value, { onValueChange(limitSearchInput(it)) }, Modifier.weight(1f)
            .padding(end = if (value.isEmpty()) 12.dp else 0.dp).focusRequester(requester).onFocusChanged {
                onInputFocus(it.isFocused)
            }
            .testTag(inputTag), singleLine = true, enabled = enabled,
            textStyle = TextStyle(fontFamily = LocalCustomFont.current, fontSize = 17.sp, lineHeight = 22.sp, color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // Selection sheets keep focus; a catalog search supplies its submit action.
            keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }), decorationBox = { input -> Box {
                if (value.isEmpty()) Text(placeholder, fontSize = 17.sp, lineHeight = 22.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariantSummary)
                input()
            } })
        if (onActivate == null && value.isNotEmpty()) Box(Modifier.width(49.dp).fillMaxHeight()
            .testTag(clearTag).clickable(enabled = enabled, role = Role.Button,
                interactionSource = remember { MutableInteractionSource() }, indication = null) { onValueChange("") }
            .semantics { contentDescription = "清除搜索" }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(24.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                drawCircle(colors.onSurface)
                val reach = size.minDimension * .145f
                drawLine(Color.Transparent, center - Offset(reach, reach), center + Offset(reach, reach),
                    1.7.dp.toPx(), StrokeCap.Round, blendMode = BlendMode.Clear)
                drawLine(Color.Transparent, center + Offset(reach, -reach), center + Offset(-reach, reach),
                    1.7.dp.toPx(), StrokeCap.Round, blendMode = BlendMode.Clear)
            }
        }
    }
}
