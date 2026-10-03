package io.github.currencortex.music.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/** Shared MIUIX bottom dialog; render inside the application's root Scaffold. */
@Composable
fun MusicDialog(title: String, onDismiss: () -> Unit,
                   footer: (@Composable ColumnScope.() -> Unit)? = null,
                   content: @Composable ColumnScope.() -> Unit) {
    OverlayDialog(show = true, title = title, onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            footer?.invoke(this)
        }
    }
}
