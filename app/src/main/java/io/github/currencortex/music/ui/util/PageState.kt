package io.github.currencortex.music.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/** MIUIX NavDisplay keeps moving scenes STARTED and resumes them only once settled.
 * Hold dataset updates during that motion to avoid replacing skeletons/remeasuring lists mid-frame.
 * Cached initial content stays visible, and predictive-back cancellation resumes the same page.
 */
@Composable fun <T> StateFlow<T>.collectAsPageState(): State<T> =
    collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
