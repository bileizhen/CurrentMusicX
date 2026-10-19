package io.github.currencortex.music.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import top.yukonga.miuix.kmp.layout.DialogDefaults
import io.github.currencortex.music.ui.component.MusicOverlayDialog as OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor

/** Shared MIUIX bottom dialog; render inside the application's root Scaffold. */
@Composable
fun MusicDialog(title: String?, onDismiss: () -> Unit,
                   footer: (@Composable ColumnScope.() -> Unit)? = null,
                   alignContent: Boolean = true,
                   content: @Composable ColumnScope.() -> Unit) {
    // OverlayDialog relocates content to the root Scaffold. Preserve the caller's current
    // appearance palette and typography in that host, including changes while it is open.
    val colors = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val indication = LocalIndication.current
    OverlayDialog(show = true, title = title, onDismissRequest = onDismiss,
        alignContent = alignContent,
        insideMargin = if (alignContent) DpSize(20.dp, 20.dp) else DialogDefaults.insideMargin) {
        MiuixTheme(colors = colors, textStyles = textStyles) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, LocalIndication provides indication) {
        Column(if (alignContent) Modifier.fillMaxWidth() else Modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            if (alignContent) footer?.let { Column(Modifier.fillMaxWidth().padding(top = 4.dp), content = it) }
            else footer?.invoke(this)
        }
        }
        }
    }
}
