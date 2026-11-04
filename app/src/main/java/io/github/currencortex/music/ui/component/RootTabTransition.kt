package io.github.currencortex.music.ui.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import kotlinx.coroutines.flow.first
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.ceil

/** Project a hero destination onto the stationary viewport while its page is still sliding in. */
class RootTabPlacement(private val viewport: () -> LayoutCoordinates?, private val page: () -> LayoutCoordinates?,
    private val componentOffset: () -> Float = { 0f }) {
    internal fun forComponent(offset: () -> Float) = RootTabPlacement(viewport, page) { componentOffset() + offset() }
    fun landingBounds(child: LayoutCoordinates): Rect? {
        val root = viewport()?.takeIf { it.isAttached } ?: return null
        val page = page()?.takeIf { it.isAttached } ?: return null
        return page.localBoundingBoxOf(child, clipBounds = false)
            .translate(root.boundsInRoot().topLeft - Offset(componentOffset(), 0f))
    }
    fun visible(bounds: Rect): Boolean {
        val root = viewport()?.takeIf { it.isAttached }?.boundsInRoot() ?: return false
        return bounds.left >= root.left - 1 && bounds.right <= root.right + 1 && bounds.top >= root.top - 1 && bounds.bottom <= root.bottom + 1
    }
}
val LocalRootTabPlacement = staticCompositionLocalOf<RootTabPlacement?> { null }
/** Only root-page title containers opt into moving the root pager. */
val LocalRootTabSwipe = compositionLocalOf<Modifier> { Modifier }

/** Retained root pages with individually trailing components, or the upstream crossfade. */
@Composable fun RootTabTransition(selected: Int, states: SaveableStateHolder,
    onSelected: (Int) -> Unit, fade: Boolean = false, elastic: Boolean = true, content: @Composable (Int) -> Unit) {
    val pager = rememberPagerState(initialPage = selected, pageCount = { 4 })
    val easing = remember { CubicBezierEasing(.2f, 0f, .2f, 1f) }
    var viewport by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val motion = rememberRootComponentMotion(pager, enabled = elastic && !fade, viewport = { viewport })
    val settling by remember(motion) { derivedStateOf { motion.settling } }
    var gesturePending by remember { mutableStateOf(false) }
    var previousSelection by remember { mutableIntStateOf(selected) }
    var fadeFrom by remember { mutableStateOf<Int?>(null) }
    val fadeProgress = remember { Animatable(1f) }
    val latestSelected by rememberUpdatedState(selected)
    val parentActive = LocalMusicPageActive.current
    val select by rememberUpdatedState(onSelected)
    val interactions = remember { MutableInteractionSource() }
    val fling = PagerDefaults.flingBehavior(pager, snapAnimationSpec = tween(320, easing = easing))
    val reverseDirection = LocalLayoutDirection.current == LayoutDirection.Ltr
    val swipe = remember(pager, reverseDirection, fling, interactions) {
        Modifier.scrollable(pager, Orientation.Horizontal, reverseDirection = reverseDirection,
            flingBehavior = fling, interactionSource = interactions).testTag("root_swipe_header")
    }
    LaunchedEffect(pager) {
        interactions.interactions.collect { if (it is DragInteraction.Start) gesturePending = true }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress to pager.currentPage }.collect { (moving, page) ->
            if (gesturePending) {
                if (page != latestSelected) select(page)
                if (!moving) gesturePending = false
            }
        }
    }
    LaunchedEffect(selected, fade) {
        val previous = previousSelection
        previousSelection = selected
        snapshotFlow { pager.layoutInfo.pageSize }.first { it > 0 }
        // Selection follows a finger drag immediately; it must not start a second animation.
        if (gesturePending && pager.isScrollInProgress && selected == pager.currentPage) {
            fadeFrom = null; fadeProgress.snapTo(1f); return@LaunchedEffect
        }
        gesturePending = false
        if (fade && previous != selected) {
            // upstream motion.dart: 220ms easeInOut. Keep the same four retained page owners.
            fadeFrom = previous
            fadeProgress.snapTo(0f)
            pager.scrollToPage(selected)
            if ((coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) fadeProgress.snapTo(1f)
            else fadeProgress.animateTo(1f, tween(220, easing = CubicBezierEasing(.42f, 0f, .58f, 1f)))
            fadeFrom = null
            return@LaunchedEffect
        }
        fadeFrom = null; fadeProgress.snapTo(1f)
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
    val sceneLifecycle = LocalLifecycleOwner.current.lifecycle
    var cachedNeighbors by remember { mutableIntStateOf(0) }
    LaunchedEffect(sceneLifecycle) {
        sceneLifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Returning to the root should first measure the visible tab. Keep the other
            // tabs warm for subsequent swipes once the containing scene is stationary.
            while (cachedNeighbors < 3) {
                withFrameNanos { }
                cachedNeighbors++
            }
        }
    }
    HorizontalPager(pager, modifier = Modifier.fillMaxSize().testTag("root_tab_transition")
        .semantics { this[RootElasticStrainKey] = motion.strain }
        .onGloballyPositioned { viewport = it },
        // Four fixed root pages fit in a bounded cache. Retaining them avoids composing and
        // discarding the nested Discover pager in the middle of every Settings -> Me slide.
        beyondViewportPageCount = cachedNeighbors, key = { it },
        userScrollEnabled = false, flingBehavior = fling) { tab ->
        states.SaveableStateProvider(tab) {
            var page by remember { mutableStateOf<LayoutCoordinates?>(null) }
            val placement = remember { RootTabPlacement({ viewport }, { page }) }
            val moving = pager.isScrollInProgress || pager.settledPage != selected || fadeFrom != null || settling
            val active = parentActive && tab == selected && !moving
            val lifecycle = rememberLifecycleOwner(maxLifecycle = when {
                active -> Lifecycle.State.RESUMED
                parentActive && moving && tab in setOf(selected, pager.currentPage, pager.settledPage) -> Lifecycle.State.STARTED
                else -> Lifecycle.State.CREATED
            })
            CompositionLocalProvider(LocalRootTabPlacement provides placement, LocalRootTabSwipe provides swipe,
                LocalRootComponentMotion provides if (elastic && !fade) motion else null,
                LocalLifecycleOwner provides lifecycle, LocalMusicPageActive provides active) {
                Box(Modifier.fillMaxSize().onGloballyPositioned { page = it }
                    .graphicsLayer {
                        if (fade && !gesturePending) {
                            translationX = if (tab == selected || tab == fadeFrom)
                                (pager.currentPage - tab + pager.currentPageOffsetFraction) * size.width else 0f
                            alpha = when (tab) { selected -> fadeProgress.value; fadeFrom -> 1f - fadeProgress.value; else -> 0f }
                        }
                    }
                    .then(if (tab != selected) Modifier.clearAndSetSemantics {} else Modifier)) { content(tab) }
            }
        }
    }
}
