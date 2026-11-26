// SPDX-License-Identifier: GPL-3.0-only
package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog

/**
 * A ColorOS settings dialog: COUI's native panel and capsule button bar.
 *
 * A destructive action takes the neutral slot, which COUI draws in the error colour; the
 * remaining action keeps the filled, recommended style.
 */
@Composable internal fun SettingsDialog(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    summary: String? = null,
    positiveText: String? = null,
    onPositive: (() -> Unit)? = null,
    positiveEnabled: Boolean = true,
    negativeText: String? = null,
    onNegative: (() -> Unit)? = null,
    negativeEnabled: Boolean = true,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    OverlayDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest,
    ) {
        // COUI leaves the content slot unpadded so a button bar can span the panel; give the
        // caller's body the same 24dp inset as the title and summary.
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) { content() }
        val negative = negativeText?.let { text ->
            DialogButtonBarAction(text, enabled = negativeEnabled, onClick = onNegative ?: onDismissRequest)
        }
        val positive = positiveText?.takeIf { !destructive }?.let { text ->
            DialogButtonBarAction(text, enabled = positiveEnabled, recommended = true, onClick = onPositive ?: onDismissRequest)
        }
        // A destructive action takes the neutral slot, which COUI draws in the error colour.
        val neutral = positiveText?.takeIf { destructive }?.let { text ->
            DialogButtonBarAction(text, enabled = positiveEnabled, onClick = onPositive ?: onDismissRequest)
        }
        if (negative != null || positive != null || neutral != null) {
            DialogButtonBar(negative = negative, positive = positive, neutral = neutral)
        }
    }
}
