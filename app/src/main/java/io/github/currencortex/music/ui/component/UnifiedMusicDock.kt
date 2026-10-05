package io.github.currencortex.music.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.currencortex.music.feature.player.PlayerSheetOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val DockExpansion = SemanticsPropertyKey<Float>("DockExpansion")
internal val LocalMusicDockNavigation = staticCompositionLocalOf<(@Composable (Modifier) -> Unit)?> { null }
internal val LocalMusicDockSurface = staticCompositionLocalOf<@Composable (Modifier, () -> Shape, @Composable () -> Unit) -> Unit> {
    { modifier, shape, body -> Box(modifier.background(MiuixTheme.colorScheme.surfaceContainer, shape())) { body() } }
}

private class DockShape(val height: Float, topStart: CornerSize, topEnd: CornerSize,
    bottomEnd: CornerSize, bottomStart: CornerSize) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    constructor(height: Float, radius: Float) : this(height, CornerSize(radius), CornerSize(radius), CornerSize(radius), CornerSize(radius))
    override fun createOutline(size: Size, topStart: Float, topEnd: Float, bottomEnd: Float,
        bottomStart: Float, layoutDirection: LayoutDirection): Outline {
        val maximum = minOf(size.width, height) / 2
        val ltr = layoutDirection == LayoutDirection.Ltr
        fun radius(value: Float) = CornerRadius(value.coerceAtMost(maximum))
        return Outline.Rounded(RoundRect(Rect(0f, 0f, size.width, height),
            topLeft = radius(if (ltr) topStart else topEnd), topRight = radius(if (ltr) topEnd else topStart),
            bottomRight = radius(if (ltr) bottomEnd else bottomStart), bottomLeft = radius(if (ltr) bottomStart else bottomEnd)))
    }
    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize) =
        DockShape(height, topStart, topEnd, bottomEnd, bottomStart)
}

/** One fixed layout and one glass surface; navigation alpha, clip and translation share a clock. */
@Composable internal fun UnifiedMusicDock(expanded: Boolean, visible: Boolean, miniPresent: Boolean,
    navigationInteractive: Boolean, modifier: Modifier = Modifier,
    onOrigin: (() -> PlayerSheetOrigin?) -> Unit = {}, mini: @Composable () -> Unit,
    navigation: @Composable (Modifier) -> Unit) {
    val progress = animateFloatAsState(if (expanded) 1f else 0f, tween(320), label = "music dock expansion")
    val miniSpace = if (miniPresent) 64.dp else 0.dp
    val navigationSpace = 64.dp
    // Share the mini row's empty bottom padding instead of adding it above the tabs.
    val navigationTravel = if (miniPresent) 58.dp else navigationSpace
    val drawDock by remember(visible, miniPresent) { derivedStateOf { visible && (miniPresent || progress.value > .001f) } }
    val drawNavigation by remember { derivedStateOf { progress.value > .001f } }
    val surface = LocalMusicDockSurface.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val reportOrigin by rememberUpdatedState(onOrigin)
    DisposableEffect(Unit) { onDispose { reportOrigin { null } } }
    RetainedOverlay(drawDock, modifier.navigationBarsPadding().padding(horizontal = 26.dp, vertical = 12.dp).widthIn(max = 480.dp)) {
        Box(Modifier.fillMaxWidth().height(miniSpace + navigationTravel).testTag("music_dock")
            .semantics { this[DockExpansion] = progress.value }
            .graphicsLayer {
                translationY = navigationTravel.toPx() * (1 - progress.value)
                shape = DockShape(miniSpace.toPx() + navigationTravel.toPx() * progress.value, 32.dp.toPx())
                // The resting indicator can stretch outside the panel. Only crop children
                // while navigation is disappearing into the shorter secondary-page surface.
                clip = progress.value < 1f
            }) {
            // Backdrop materials clip their children too, so keep the glass in a sibling layer.
            surface(Modifier.matchParentSize(), {
                with(density) {
                    DockShape(miniSpace.toPx() + navigationTravel.toPx() * progress.value, 32.dp.toPx())
                }
            }) {}
            Box(Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
                // Measure inside the moving layer: the outer dock layout reserves the
                // hidden navigation and reports its untranslated position on secondary pages.
                // localToRoot also avoids clipping the visible outline to ancestor bounds.
                reportOrigin {
                    if (!coordinates.isAttached) null else with(density) {
                        val topLeft = coordinates.localToRoot(Offset.Zero)
                        PlayerSheetOrigin(Rect(topLeft, Size(coordinates.size.width.toFloat(),
                            miniSpace.toPx() + navigationTravel.toPx() * progress.value)), 32.dp.toPx())
                    }
                }
            }) {
                if (miniPresent) CompositionLocalProvider(LocalMusicGlassSurface provides { mod, body ->
                    Box(mod) { Box(Modifier.testTag("mini_glass_surface")) { body() } }
                }) {
                    // Preserve the original 64dp bar's 32dp corner; the cover shares its center.
                    Box(Modifier.height(miniSpace).padding(horizontal = 5.dp, vertical = 6.dp)) { mini() }
                }
                RetainedOverlay(drawNavigation, Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .then(if (navigationInteractive) Modifier else Modifier.clearAndSetSemantics {})
                    .pointerInput(navigationInteractive) {
                        if (!navigationInteractive) awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }.graphicsLayer { alpha = progress.value }) {
                    Box(Modifier.height(navigationSpace)) { navigation(Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}
