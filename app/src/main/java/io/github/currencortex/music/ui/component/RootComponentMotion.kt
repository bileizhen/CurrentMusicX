package io.github.currencortex.music.ui.component

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.collectLatest
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

internal val RootElasticStrainKey = SemanticsPropertyKey<Float>("RootElasticStrain")

/** Three shared followers let visible components trail at different speeds without one
 * animation coroutine per card. Only layer translations change; text and covers stay rigid. */
@Composable
internal fun rememberRootComponentMotion(pager: PagerState, enabled: Boolean,
    viewport: () -> LayoutCoordinates?): RootComponentMotion {
    val fast = rememberPageLag(pager, enabled, 1100f)
    val middle = rememberPageLag(pager, enabled, 650f)
    val slow = rememberPageLag(pager, enabled, 380f)
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    return remember(fast, middle, slow, direction) { RootComponentMotion(fast, middle, slow, direction, viewport) }
}

@Composable
private fun rememberPageLag(pager: PagerState, enabled: Boolean, stiffness: Float): State<Float> {
    val follower = remember(pager, enabled) { AnimationState(pager.currentPage + pager.currentPageOffsetFraction) }
    var animated by remember(enabled) { mutableStateOf(false) }
    LaunchedEffect(pager, enabled) {
        animated = enabled && (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) != 0f
        if (!animated) return@LaunchedEffect
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collectLatest { position ->
            follower.animateTo(position, spring(dampingRatio = .7f, stiffness = stiffness, visibilityThreshold = .001f),
                sequentialAnimation = true)
        }
    }
    return remember(pager, follower, animated) { derivedStateOf {
        if (!animated) 0f else ((pager.currentPage + pager.currentPageOffsetFraction - follower.value) * .32f)
            .coerceIn(-.12f, .12f)
    } }
}

internal class RootComponentMotion(private val fast: State<Float>, private val middle: State<Float>,
    private val slow: State<Float>, private val direction: Float, val viewport: () -> LayoutCoordinates?) {
    val strain get() = slow.value * direction
    val settling get() = maxOf(abs(fast.value), abs(middle.value), abs(slow.value)) > .002f
    fun translation(fraction: Float): Float {
        val t = fraction.coerceIn(0f, 1f) * 2f
        val lag = if (t < 1f) fast.value + (middle.value - fast.value) * t
            else middle.value + (slow.value - middle.value) * (t - 1f)
        return lag * direction * (viewport()?.size?.width ?: 0)
    }
}

internal val LocalRootComponentMotion = staticCompositionLocalOf<RootComponentMotion?> { null }

@Composable
internal fun Modifier.elasticRootComponent(order: Int = 0): Modifier {
    val motion = LocalRootComponentMotion.current ?: return this
    val offset = remember(motion, order) { ComponentOffset(motion, order) }
    return componentLayer(offset)
}

private class ComponentOffset(val motion: RootComponentMotion, val order: Int) {
    var verticalFraction by mutableFloatStateOf(0f)
    val x get() = motion.translation((verticalFraction * .8f + (order % 4) * .065f).coerceIn(0f, 1f))
}

private fun Modifier.componentLayer(offset: ComponentOffset) = onGloballyPositioned { coordinates ->
        val root = offset.motion.viewport()?.takeIf { it.isAttached && it.size.height > 0 }
        if (root != null) offset.verticalFraction = ((coordinates.positionInRoot().y - root.positionInRoot().y +
            coordinates.size.height / 2f) / root.size.height).coerceIn(0f, 1f)
    }.graphicsLayer {
        // Same-row cards also have a small stagger. Translation does not change their size.
        translationX = offset.x
    }

@Composable
internal fun RootMotionItem(order: Int = 0, content: @Composable () -> Unit) {
    val motion = LocalRootComponentMotion.current
    if (motion == null) { content(); return }
    val offset = remember(motion, order) { ComponentOffset(motion, order) }
    val parent = LocalRootTabPlacement.current
    val placement = remember(parent, offset) { parent?.forComponent { offset.x } }
    Column(Modifier.componentLayer(offset)) {
        // A group moves once. Nested cards only opt in when the outer row is explicitly skipped.
        CompositionLocalProvider(LocalRootComponentMotion provides null,
            LocalRootTabPlacement provides placement, content = content)
    }
}

/** Keep lazy keys, item scopes and content types; wrap only composed items, not the page. */
internal fun LazyListScope.rootMotionItems(skipKeys: Set<Any> = emptySet(), content: LazyListScope.() -> Unit) {
    val target = this
    var order = 0
    val scope = object : LazyListScope by target {
        override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
            val index = order++
            target.item(key, contentType) {
                val itemScope = this
                if (key in skipKeys) content(itemScope)
                else RootMotionItem(index) { content(itemScope) }
            }
        }
        override fun items(count: Int, key: ((Int) -> Any)?, contentType: (Int) -> Any?,
            itemContent: @Composable LazyItemScope.(Int) -> Unit) {
            val start = order
            order += count
            target.items(count, key, contentType) { index ->
                val itemScope = this
                RootMotionItem(start + index) { itemContent(itemScope, index) }
            }
        }
    }
    content(scope)
}
