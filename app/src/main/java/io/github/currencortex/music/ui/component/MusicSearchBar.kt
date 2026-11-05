package io.github.currencortex.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import io.github.currencortex.music.ui.component.nativeui.GlassDockedSearch

/** Home and its expanded search share the upstream field; this adapter supplies music actions. */
@Composable fun MusicSearchBar(value: String, onSubmit: () -> Unit, modifier: Modifier = Modifier,
    onInput: ((String) -> Unit)? = null, onActivate: (() -> Unit)? = null, requester: FocusRequester? = null,
    placeholder: String = "搜索歌曲、歌手或歌词", inputTag: String = "search_input", submitTag: String? = null,
    onInputFocus: (Boolean) -> Unit = {}, onCancel: () -> Unit = {}, clearTag: String = "search_clear") {
    GlassDockedSearch(value, onInput ?: {}, modifier, inputTag = inputTag, placeholder = placeholder,
        focusRequester = requester, onInputFocus = onInputFocus, onSubmit = onSubmit,
        onActivate = if (onInput == null) onActivate ?: onSubmit else null,
        submitTag = submitTag ?: if (onInput == null) "home_search_submit" else "submit_search",
        clearTag = clearTag, onCancel = onCancel)
}
