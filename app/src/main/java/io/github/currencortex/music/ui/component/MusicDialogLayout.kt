package io.github.currencortex.music.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import io.github.currencortex.music.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
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
    enabled: Boolean = true, cornerRadius: androidx.compose.ui.unit.Dp = 100.dp,
    minWidth: androidx.compose.ui.unit.Dp = ButtonDefaults.MinWidth,
    minHeight: androidx.compose.ui.unit.Dp = 44.dp,
    colors: TextButtonColors = musicDialogButtonColors(),
    insideMargin: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    textStyle: TextStyle? = null, fillWidth: Boolean = true, destructive: Boolean = false) {
    val press = rememberMusicDialogPress()
    val inkWash = MiuixTheme.colorScheme.onBackground
    val ink = if (destructive) MiuixTheme.colorScheme.error.copy(alpha = if (enabled) 1f else .35f)
        else if (enabled) colors.textColor else colors.disabledTextColor
    Box(modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
        .then(if (LocalDialogActionRow.current) Modifier.fillMaxHeight() else Modifier)
        .heightIn(min = minHeight).widthIn(min = minWidth)
        .graphicsLayer { scaleX = 1f - .025f * press.amount.value; scaleY = scaleX }
        .clip(CircleShape).background(if (enabled) colors.color else colors.disabledColor)
        .drawBehind { drawRect(inkWash.copy(alpha = .055f * press.amount.value)) }
        .clickable(enabled = enabled, role = Role.Button, interactionSource = press.source, indication = null, onClick = onClick)
        .padding(insideMargin), contentAlignment = Alignment.Center) {
        Text(text, color = ink, textAlign = TextAlign.Center,
            style = textStyle ?: MiuixTheme.textStyles.button.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable private fun musicDialogButtonColors(): TextButtonColors {
    val dark = isInDarkTheme()
    return ButtonDefaults.textButtonColors(
        color = Color.White.copy(alpha = if (dark) .13f else .66f),
        textColor = MiuixTheme.colorScheme.onBackground)
}
