package io.github.currencortex.music.feature.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

internal val PlaylistPanelCornerRadius = SemanticsPropertyKey<Float>("PlaylistPanelCornerRadius")

/** Scroll-linked easing reaches an exact rectangle before the header becomes sticky. */
internal fun playlistPanelCornerPx(list: LazyListState, pullOffset: Float, density: Density): Float = with(density) {
    val top = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "playback" }?.offset?.toFloat()
        ?: if (list.firstVisibleItemIndex > 0) 0f else Float.MAX_VALUE
    val progress = ((top + pullOffset) / 64.dp.toPx()).coerceIn(0f, 1f)
    26.dp.toPx() * progress * progress * (3 - 2 * progress)
}
