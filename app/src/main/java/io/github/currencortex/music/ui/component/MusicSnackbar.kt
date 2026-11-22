// SPDX-License-Identifier: GPL-3.0-only
package io.github.currencortex.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import io.github.suqi8.coui.kmp.basic.SnackbarDuration
import io.github.suqi8.coui.kmp.basic.SnackbarHost
import io.github.suqi8.coui.kmp.basic.SnackbarHostState
import io.github.suqi8.coui.kmp.basic.SnackbarResult

/**
 * App-wide COUI snackbar channel: pages report a failed load or a failed action here instead of
 * leaving a permanent hint card in their content. The host lives once at the root, above the
 * floating player, so a snackbar keeps showing while its page navigates underneath it.
 */
class MusicSnackbarState internal constructor(val host: SnackbarHostState) {
    /** Shows [message]; returns true when the user pressed [actionLabel]. */
    suspend fun show(message: String, actionLabel: String? = null,
        duration: SnackbarDuration = SnackbarDuration.Long): Boolean =
        host.showSnackbar(message = message, actionLabel = actionLabel, duration = duration) ==
            SnackbarResult.ActionPerformed
}

val LocalMusicSnackbar = staticCompositionLocalOf<MusicSnackbarState?> { null }

@Composable fun rememberMusicSnackbar(): MusicSnackbarState {
    val host = remember { SnackbarHostState() }
    return remember(host) { MusicSnackbarState(host) }
}

@Composable fun MusicSnackbarHost(state: MusicSnackbarState, modifier: Modifier = Modifier) {
    SnackbarHost(state = state.host, modifier = modifier)
}