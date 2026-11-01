package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.theme.LocalCustomFont
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Stable editable surface; only the action slots deform, so dragging text keeps the caret aligned. */
@Composable internal fun GlassTextComposer(value: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier, fieldModifier: Modifier = Modifier,
    placeholder: String = "输入消息…", enabled: Boolean = true,
    leading: @Composable () -> Unit, trailing: @Composable () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(modifier.fillMaxWidth().glassSurface(LocalGlassBackdrop.current, RoundedCornerShape(28.dp))
        .padding(6.dp), verticalAlignment = Alignment.Bottom) {
        leading()
        BasicTextField(value, onValueChange, fieldModifier.weight(1f).heightIn(min = 44.dp)
            .padding(horizontal = 8.dp, vertical = 11.dp), enabled = enabled,
            textStyle = TextStyle(fontFamily = LocalCustomFont.current, fontSize = 15.sp, lineHeight = 22.sp, color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary), maxLines = 5,
            decorationBox = { field -> Box {
                if (value.isEmpty()) Text(placeholder, Modifier.clearAndSetSemantics {}, fontSize = 15.sp,
                    lineHeight = 22.sp, color = colors.onSurfaceVariantSummary.copy(alpha = .65f))
                field()
            } })
        trailing()
    }
}
