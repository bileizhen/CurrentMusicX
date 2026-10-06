package io.github.currencortex.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Both ends of the search transition use the same padding, material and submit action. */
@Composable fun MusicSearchBar(value: String, onSubmit: () -> Unit, modifier: Modifier = Modifier,
    onInput: ((String) -> Unit)? = null, onActivate: (() -> Unit)? = null, requester: FocusRequester? = null) {
    val colors = MiuixTheme.colorScheme
    Row(modifier.then(if (onInput == null) Modifier.clickable(role = Role.Button) { onActivate?.invoke() } else Modifier).fillMaxWidth().height(48.dp).background(colors.surfaceContainer, RoundedCornerShape(24.dp))
        .padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Search, null, Modifier.size(20.dp), tint = colors.primary)
        val textModifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        if (onInput != null) BasicTextField(value, onInput, singleLine = true,
            modifier = textModifier.then(if (requester != null) Modifier.focusRequester(requester) else Modifier).testTag("search_input"),
            textStyle = TextStyle(fontSize = 14.sp, color = colors.onSurface), cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            decorationBox = { input -> Box {
                if (value.isEmpty()) Text("搜索歌曲、歌手", fontSize = 14.sp, color = colors.onSurface.copy(alpha = .55f))
                input()
            } })
        else Box(textModifier.fillMaxHeight().clickable(role = Role.Button) { onActivate?.invoke() }, contentAlignment = Alignment.CenterStart) {
            Text(value.ifEmpty { "搜索歌曲、歌手" }, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = colors.onSurface.copy(alpha = if (value.isEmpty()) .55f else 1f))
        }
        if (value.isNotEmpty() && onInput != null) Box(Modifier.size(28.dp).clickable { onInput("") }.testTag("search_clear"), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Close, "清除输入", Modifier.size(16.dp), tint = colors.onSurface.copy(alpha = .4f))
        }
        Box(Modifier.width(1.dp).height(16.dp).background(colors.onSurface.copy(alpha = .3f)))
        Box(Modifier.width(52.dp).fillMaxHeight().clickable(role = Role.Button, onClick = onSubmit).testTag(if (onInput == null) "home_search_submit" else "submit_search"), contentAlignment = Alignment.Center) {
            Text("搜索", fontSize = 14.sp, color = colors.onSurface)
        }
    }
}
