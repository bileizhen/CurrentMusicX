package io.github.currencortex.music.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.settings.LyricsStyle
import io.github.currencortex.music.ui.component.nativeui.SettingsSegmentedControl
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The playback settings page and the player's lyrics dialog edit the same preference. */
@Composable internal fun LyricsStyleSelector(style: LyricsStyle, onStyle: (LyricsStyle) -> Unit,
    modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("歌词样式", fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp))
        SettingsSegmentedControl(LyricsStyle.entries.map { it.label }, LyricsStyle.entries.indexOf(style),
            { onStyle(LyricsStyle.entries[it]) }, tag = "lyrics_style",
            itemTag = { "lyrics_style_${LyricsStyle.entries[it].name}" })
    }
}
