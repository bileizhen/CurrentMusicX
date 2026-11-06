package io.github.currencortex.music.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import io.github.currencortex.music.data.settings.LyricsStyle
import io.github.currencortex.music.ui.component.nativeui.SettingsChoiceRow
import io.github.currencortex.music.ui.component.nativeui.SettingsDivider

/** The playback page owns the style selection, shown as an inline single-choice list. */
@Composable internal fun LyricsStyleSelector(style: LyricsStyle, onStyle: (LyricsStyle) -> Unit,
    modifier: Modifier = Modifier) {
    Column(modifier.testTag("lyrics_style").semantics { selectableGroup() }) {
        LyricsStyle.entries.forEachIndexed { index, option ->
            if (index > 0) SettingsDivider()
            SettingsChoiceRow(option.label, option == style, { onStyle(option) },
                Modifier.testTag("lyrics_style_${option.name}"))
        }
    }
}
