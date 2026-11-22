// SPDX-License-Identifier: GPL-3.0-only
package io.github.currencortex.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
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

/**
 * Reports a page's load failure once per attempt, with an action that starts a retry, and only
 * while [active]. Retained tabs and covered pages keep composing, so without the gate the notice
 * appears on whichever page is showing, where it would expire unseen. The effect's keys stay
 * stable on purpose: relaunching it when [loading] flips cancels a snackbar that is still on
 * screen, and the host stacks the re-queued copy under the first one.
 */
@Composable fun MusicSnackbarLoadError(error: String?, active: Boolean = true, loading: Boolean = false,
    actionLabel: String? = "重试", onAction: () -> Unit = {}) {
    val snackbar = LocalMusicSnackbar.current
    val latestError = rememberUpdatedState(error)
    val latestActive = rememberUpdatedState(active)
    val latestLoading = rememberUpdatedState(loading)
    val latestLabel = rememberUpdatedState(actionLabel)
    val latestAction = rememberUpdatedState(onAction)
    LaunchedEffect(snackbar) {
        val channel = snackbar ?: return@LaunchedEffect
        var reported: String? = null
        snapshotFlow { Triple(latestError.value, latestLoading.value, latestActive.value) }
            .collect { (message, isLoading, isActive) ->
                if (!isActive) return@collect
                if (message == null) { reported = null; return@collect }
                if (isLoading || message == reported) return@collect
                reported = message
                if (channel.show(message, latestLabel.value) == true) latestAction.value()
            }
    }
}

@Composable fun rememberMusicSnackbar(): MusicSnackbarState {
    val host = remember { SnackbarHostState() }
    return remember(host) { MusicSnackbarState(host) }
}

@Composable fun MusicSnackbarHost(state: MusicSnackbarState, modifier: Modifier = Modifier) {
    SnackbarHost(state = state.host, modifier = modifier)
}