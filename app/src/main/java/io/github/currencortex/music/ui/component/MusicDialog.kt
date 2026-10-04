package io.github.currencortex.music.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor

/** Shared MIUIX bottom dialog; render inside the application's root Scaffold. */
@Composable
fun MusicDialog(title: String, onDismiss: () -> Unit,
                   footer: (@Composable ColumnScope.() -> Unit)? = null,
                   content: @Composable ColumnScope.() -> Unit) {
    // OverlayDialog relocates content to the root Scaffold. Preserve the caller's current
    // appearance palette and typography in that host, including changes while it is open.
    val colors = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val indication = LocalIndication.current
    OverlayDialog(show = true, title = title, onDismissRequest = onDismiss) {
        MiuixTheme(colors = colors, textStyles = textStyles) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, LocalIndication provides indication) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            footer?.invoke(this)
        }
        }
        }
    }
}
