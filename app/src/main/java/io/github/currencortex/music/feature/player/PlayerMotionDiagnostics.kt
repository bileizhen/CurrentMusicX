package io.github.currencortex.music.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/** Geometry probes are opt-in: publishing every animation frame invalidates accessibility.
 * Keep real labels/actions in normal semantics; tests can still inspect the exact paths. */
internal val LocalPlayerMotionDiagnostics = staticCompositionLocalOf { false }

@Composable internal fun Modifier.playerMotionDiagnostics(
    properties: SemanticsPropertyReceiver.() -> Unit,
): Modifier = if (LocalPlayerMotionDiagnostics.current) semantics(properties = properties) else this
