package io.github.currencortex.music.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Forms retain content insets; choice dialogs let every row reach the panel edges. */
@Composable
fun MusicDialog(title: String?, onDismiss: () -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    alignContent: Boolean = true, edgeToEdgeContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    MusicOverlayDialog(show = true, title = title, onDismissRequest = onDismiss,
        alignContent = alignContent, edgeToEdgeContent = edgeToEdgeContent) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (edgeToEdgeContent) 0.dp else 12.dp)) {
            content()
            footer?.let {
                Column(Modifier.fillMaxWidth().padding(horizontal = if (edgeToEdgeContent) 16.dp else 0.dp)
                    .padding(top = 20.dp), content = it)
            }
        }
    }
}
