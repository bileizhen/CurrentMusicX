package io.github.currencortex.music.ui.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.flow.first
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.ceil

/** Project a hero destination onto the stationary viewport while its page is still sliding in. */
class RootTabPlacement(private val viewport: () -> LayoutCoordinates?, private val page: () -> LayoutCoordinates?) {
    fun landingBounds(child: LayoutCoordinates): Rect? {
        val root = viewport()?.takeIf { it.isAttached } ?: return null
        val page = page()?.takeIf { it.isAttached } ?: return null
        return page.localBoundingBoxOf(child, clipBounds = false).translate(root.boundsInRoot().topLeft)
    }
    fun visible(bounds: Rect): Boolean {
        val root = viewport()?.takeIf { it.isAttached }?.boundsInRoot() ?: return false
        return bounds.left >= root.left - 1 && bounds.right <= root.right + 1 && bounds.top >= root.top - 1 && bounds.bottom <= root.bottom + 1
    }
}
val LocalRootTabPlacement = staticCompositionLocalOf<RootTabPlacement?> { null }

/** Continuous full-width pages, including intermediate tabs; no fade, overshoot or pre-jump. */
@Composable fun RootTabTransition(selected: Int, states: SaveableStateHolder,
    onSelected: (Int) -> Unit, content: @Composable (Int) -> Unit) {
    val pager = rememberPagerState(initialPage = selected, pageCount = { 4 })
    val easing = remember { CubicBezierEasing(.2f, 0f, .2f, 1f) }
    var viewport by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var gesturePending by remember { mutableStateOf(false) }
    val latestSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelected)
    LaunchedEffect(pager) {
        pager.interactionSource.interactions.collect { if (it is DragInteraction.Start) gesturePending = true }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress to pager.settledPage }.collect { (moving, page) ->
            if (!moving && gesturePending) {
                gesturePending = false
                if (page != latestSelected) select(page)
            }
        }
    }
    LaunchedEffect(selected) {
        snapshotFlow { pager.layoutInfo.pageSize }.first { it > 0 }
        if (abs(pager.getOffsetDistanceInPages(selected)) < .0001f) return@LaunchedEffect
        if ((coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) pager.scrollToPage(selected)
        else pager.scroll {
            with(pager) { updateTargetPage(selected) }
            val pages = pager.getOffsetDistanceInPages(selected)
            val distance = pages * pager.layoutInfo.pageSize
            val duration = 320 + (ceil(abs(pages)).toInt() - 1).coerceIn(0,2) * 40
            var previous = 0f
            animate(0f, distance, animationSpec = tween(duration, easing = easing)) { value, _ ->
                scrollBy(value - previous); previous = value
            }
        }
    }
    HorizontalPager(pager, modifier = Modifier.fillMaxSize().testTag("root_tab_transition")
        .onGloballyPositioned { viewport = it },
        // Measure the requested page immediately for hero anchors, then retain only neighbours.
        beyondViewportPageCount = maxOf(1, abs(selected - pager.currentPage)), key = { it },
        flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = tween(320, easing = easing))) { tab ->
        states.SaveableStateProvider(tab) {
            var page by remember { mutableStateOf<LayoutCoordinates?>(null) }
            val placement = remember { RootTabPlacement({ viewport }, { page }) }
            CompositionLocalProvider(LocalRootTabPlacement provides placement) {
                Box(Modifier.fillMaxSize().onGloballyPositioned { page = it }
                    .then(if (tab != selected) Modifier.clearAndSetSemantics {} else Modifier)) { content(tab) }
            }
        }
    }
}
