package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.MusicTextAction
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val desktopPalette = listOf("白色" to 0xFFFFFFFFL, "冰蓝" to 0xFF80DEFFL, "薄荷" to 0xFF9DE6BAL,
    "暖金" to 0xFFFFD58AL, "樱粉" to 0xFFFFAACCL, "淡紫" to 0xFFC5B5FFL, "黑色" to 0xFF20232AL)

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun DesktopLyricsColorControl(title: String, value: Long, tag: String, save: (Long) -> Unit) {
    val colors = MiuixTheme.colorScheme
    var hex by rememberSaveable(value) { mutableStateOf(desktopColorHex(value)) }
    val parsed = desktopParseColor(hex)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 15.sp)
            Text("#${desktopColorHex(value)}", color = colors.onSurfaceVariantSummary, fontSize = 12.sp,
                modifier = Modifier.testTag("${tag}_value"))
            MusicTextAction("默认", { save(0xFFFFFFFFL) }, Modifier.testTag("${tag}_reset"), enabled = value != 0xFFFFFFFFL)
        }
        FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            desktopPalette.forEach { (label, color) ->
                Box(Modifier.size(44.dp).testTag("${tag}_${desktopColorHex(color)}")
                    .semantics { contentDescription = "$title：$label" }
                    .selectable(value == color, role = Role.RadioButton, onClick = { save(color) }), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(34.dp).border(if (value == color) 2.dp else 1.dp,
                        if (value == color) colors.primary else colors.onSurface.copy(alpha = .2f), CircleShape)
                        .padding(4.dp).background(Color(color.toInt()), CircleShape))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextField(hex, { hex = it.take(7) }, label = "自定义 HEX", singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f).testTag("${tag}_hex"))
            MusicTextAction("应用", { parsed?.let(save) }, Modifier.testTag("${tag}_apply"), enabled = parsed != null && parsed != value)
        }
        Text(if (parsed == null) "请输入 6 位颜色值，例如 80DEFF，可带 #。" else "支持预设色或自定义 6 位 HEX 颜色。",
            fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
    }
}
