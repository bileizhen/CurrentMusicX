package io.github.currencortex.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.currencortex.music.data.settings.LyricsStyle
import io.github.currencortex.music.ui.component.nativeui.SettingsOption
import io.github.currencortex.music.ui.component.nativeui.SettingsOptionsRow

/** The playback page owns the style selection, using the shared settings list menu. */
@Composable internal fun LyricsStyleSelector(style: LyricsStyle, onStyle: (LyricsStyle) -> Unit,
    modifier: Modifier = Modifier) {
    SettingsOptionsRow("歌词样式", style.label, LyricsStyle.entries.map { option ->
        SettingsOption("lyrics_style_${option.name}", option.label, option == style,
            onSelect = { onStyle(option) })
    }, modifier.testTag("lyrics_style"))
}
