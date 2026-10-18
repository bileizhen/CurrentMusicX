package io.github.currencortex.music.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextButtonColors
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Dialog-only spacing; page rows keep their existing insets. */
internal val LocalMusicDialogContent = staticCompositionLocalOf { false }
private val LocalDialogActionRow = staticCompositionLocalOf { false }

@Composable
fun MusicDialogActions(modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(10.dp),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit) {
    CompositionLocalProvider(LocalDialogActionRow provides true) {
        Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement, verticalAlignment, content)
    }
}

/** A standalone action fills the content column; weighted actions share one row height. */
@Composable
fun MusicDialogButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, cornerRadius: androidx.compose.ui.unit.Dp = 12.dp,
    minWidth: androidx.compose.ui.unit.Dp = ButtonDefaults.MinWidth,
    minHeight: androidx.compose.ui.unit.Dp = 44.dp,
    colors: TextButtonColors = ButtonDefaults.textButtonColors(),
    insideMargin: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    textStyle: TextStyle? = null, fillWidth: Boolean = true, destructive: Boolean = false) {
    TextButton(text, onClick,
        modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .then(if (LocalDialogActionRow.current) Modifier.fillMaxHeight() else Modifier),
        enabled, cornerRadius, minWidth, minHeight,
        if (destructive) ButtonDefaults.textButtonColors(color = MiuixTheme.colorScheme.error,
            textColor = MiuixTheme.colorScheme.onError) else colors, insideMargin,
        textStyle ?: MiuixTheme.textStyles.button.copy(fontSize = 14.sp, lineHeight = 20.sp))
}
