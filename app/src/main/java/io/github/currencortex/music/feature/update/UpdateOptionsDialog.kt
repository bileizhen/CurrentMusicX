package io.github.currencortex.music.feature.update

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.MusicOverlayDialog
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val optionInk = Color(0xFFF4F5F7)
private val optionMuted = Color(0xFFAFB3BC)
private val optionAccent = Color(0xFFA8C9FA)

/** Scoped to the update page: its art palette must not tint native option sheets purple. */
@Composable internal fun UpdateOptionsDialog(title: String, summary: String? = null,
    onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = MiuixTheme.colorScheme.copy(background = Color(0xFF24262B), onBackground = optionInk,
        surface = Color(0xFF303238), primary = optionAccent)
    MiuixTheme(colors = colors) {
        MusicOverlayDialog(show = true, onDismissRequest = onDismiss,
            insideMargin = DpSize(20.dp, 20.dp), outsideMargin = DpSize(16.dp, 12.dp)) {
            Column(Modifier.fillMaxWidth().testTag("update_options_sheet")) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = optionInk, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f))
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f))
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .semantics { contentDescription = "关闭$title" }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, null, Modifier.size(18.dp), tint = optionMuted)
                    }
                }
                summary?.let {
                    Text(it, color = optionMuted, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), content = content)
            }
        }
    }
}

@Composable internal fun UpdateOptionGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(Color.White.copy(alpha = .035f)), content = content)
}

@Composable internal fun UpdateChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(if (selected) Color.White.copy(alpha = .065f) else Color.Transparent)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = 14.dp).heightIn(min = 48.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = optionInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Canvas(Modifier.size(18.dp)) {
            val stroke = 1.5.dp.toPx()
            drawCircle(if (selected) optionAccent else optionMuted.copy(alpha = .6f),
                radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
            if (selected) drawCircle(optionAccent, radius = 4.dp.toPx())
        }
    }
}

@Composable internal fun UpdateAutoCheckRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("启动时自动检查", color = optionInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable internal fun UpdateOptionSectionLabel(label: String) {
    Text(label, color = optionMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}
