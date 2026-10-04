package io.github.currencortex.music.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.settings.LyricsTypography
import io.github.currencortex.music.feature.lyrics.ui.LyricsFontFamily
import top.yukonga.miuix.kmp.basic.*
import kotlin.math.roundToInt

@Composable internal fun LyricsDisplaySettings(fontSize: Float, onFontSize: (Float) -> Unit,
    translation: Boolean, onTranslation: (Boolean) -> Unit,
    romanization: Boolean, onRomanization: (Boolean) -> Unit,
    animation: Boolean, onAnimation: (Boolean) -> Unit,
    effects: Boolean, onEffects: (Boolean) -> Unit) {
    var preview by remember(fontSize) { mutableFloatStateOf(fontSize) }
    Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())
        .testTag("lyrics_display_settings")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("让音乐说话", fontFamily = LyricsFontFamily, fontSize = preview.sp,
                color = Color.White, modifier = Modifier.testTag("lyrics_font_preview"))
            Text("霞鹜文楷", fontSize = 12.sp, color = Color.White.copy(alpha = .5f), modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("字号 ${preview.roundToInt()}", fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
                Text("恢复默认", fontSize = 13.sp, color = Color.White.copy(alpha = .65f),
                    modifier = Modifier.testTag("lyrics_font_reset").clickable(role = Role.Button) {
                        preview = LyricsTypography.DEFAULT_SIZE; onFontSize(preview)
                    }.padding(horizontal = 8.dp, vertical = 12.dp))
            }
            Slider(value = preview, onValueChange = { preview = it.roundToInt().toFloat() },
                onValueChangeFinished = { onFontSize(preview) },
                valueRange = LyricsTypography.MIN_SIZE..LyricsTypography.MAX_SIZE,
                modifier = Modifier.testTag("lyrics_font_size").semantics {
                    contentDescription = "歌词字号"
                    // Miuix's SetProgress callback does not call onValueChangeFinished.
                    setProgress { target ->
                        preview = LyricsTypography.normalize(target).roundToInt().toFloat()
                        onFontSize(preview); true
                    }
                })
        }
        LyricsToggle("翻译歌词", translation, onTranslation)
        LyricsToggle("罗马音", romanization, onRomanization)
        LyricsToggle("逐字动画", animation, onAnimation)
        LyricsToggle("歌词景深", effects, onEffects)
    }
}

@Composable private fun LyricsToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Switch) { onChange(!checked) }
        .semantics { toggleableState = if (checked) ToggleableState.On else ToggleableState.Off }
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 16.sp, color = Color.White)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
