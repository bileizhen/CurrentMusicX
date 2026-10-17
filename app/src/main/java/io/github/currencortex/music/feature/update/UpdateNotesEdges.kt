package io.github.currencortex.music.feature.update

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.currencortex.music.ui.component.ScrollEdgeBlurKey
import io.github.currencortex.music.ui.component.ScrollEdgeBlurState
import io.github.currencortex.music.ui.component.scrollEdgeBlur

internal typealias UpdateNotesEdgeState = ScrollEdgeBlurState
internal val UpdateNotesEdgesKey = ScrollEdgeBlurKey

/** Wrap the scroll viewport, not its full content or the fixed installation controls. */
@Composable
internal fun Modifier.updateNotesEdges(scroll: ScrollState): Modifier =
    scrollEdgeBlur(scroll.canScrollBackward, scroll.canScrollForward)
