package io.github.currencortex.music.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.currencortex.music.ui.component.LocalMusicPageActive
import io.github.currencortex.music.ui.component.LocalPageContainerSettled
import io.github.currencortex.music.ui.component.coverRequestUrl
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.component.liquid.rememberCombinedBackdrop
import io.github.currencortex.music.ui.theme.LocalDarkTheme
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val LocalPlaylistStatusBarStyle = compositionLocalOf<((Boolean) -> Unit)?> { null }

/** The cover spans the system bars and hero, then fades into the actual list background. */
@Composable internal fun PlaylistPageLayout(cover: String, list: LazyListState,
    heroHeightPx: Int, tag: String, toolbar: @Composable (Float) -> Unit,
    content: @Composable (Dp) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + SettingsToolbarHeight
    val heroHeight = with(density) { heroHeightPx.takeIf { it > 0 }?.toDp() ?: 240.dp }
    val progress = remember(list, heroHeightPx) { derivedStateOf {
        if (list.firstVisibleItemIndex > 0) 1f
        else (list.firstVisibleItemScrollOffset / (heroHeightPx * .4f).coerceAtLeast(1f)).coerceIn(0f, 1f)
    } }
    val pageActive = LocalMusicPageActive.current
    val statusStyle = LocalPlaylistStatusBarStyle.current
    val darkStatusBackground = LocalDarkTheme.current || remember(progress) { derivedStateOf { progress.value < .8f } }.value
    LaunchedEffect(pageActive, darkStatusBackground, statusStyle) {
        if (pageActive) statusStyle?.invoke(darkStatusBackground)
    }
    val base = rememberGlassBackdrop()
    val body = rememberGlassBackdrop()
    val artworkVisible by remember(list) { derivedStateOf { list.firstVisibleItemIndex == 0 } }
    val rowBackdrop = if (artworkVisible) base else rememberSolidGlassBackdrop(base, colors.background)
    // At rest at the top, the list's content padding puts all rows below the
    // toolbar. Its buttons only see the artwork; don't replay every song into them.
    val rowsUnderToolbar = list.canScrollBackward
    val headerBackdrop = if (rowsUnderToolbar && base != null && body != null) rememberCombinedBackdrop(base, body) else base
    val scene = LocalPageGlassScene.current
    // Keep text and controls inside a readable, cutout-safe column while the cover fills the window.
    val contentColumn = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        .widthIn(max = 840.dp).fillMaxWidth()
    SettingsUiScope {
    CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
    Box(Modifier.fillMaxSize().graphicsLayer {
        // Reuse the composited page while it slides in. When it leaves, the remaining
        // visible area shrinks; drawing that directly avoids a full-page offscreen pass.
        compositingStrategy = if (scene?.entering() == true) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }.testTag(tag), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.matchParentSize().then(if (base != null) Modifier.layerBackdrop(base) else Modifier)
            .background(colors.background)) {
            PlaylistArtworkBackground(cover, colors.background, Modifier.fillMaxWidth()
                .height(topInset + heroHeight + 8.dp).graphicsLayer {
                    translationY = if (list.firstVisibleItemIndex == 0) -list.firstVisibleItemScrollOffset.toFloat() else -size.height
                })
        }
        CompositionLocalProvider(LocalGlassBackdrop provides rowBackdrop) {
            Box(contentColumn.fillMaxHeight().navigationBarsPadding()
                .then(if (rowsUnderToolbar && body != null) Modifier.layerBackdrop(body) else Modifier)) { content(topInset) }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides headerBackdrop) {
            SettingsFloatingHeader(background = colors.background, opacity = { .9f * progress.value }) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Box(contentColumn.statusBarsPadding()) { toolbar(progress.value) }
                }
            }
        }
    }
    }
    }
}

@Composable private fun PlaylistArtworkBackground(cover: String, surface: Color, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val containerSettled = LocalPageContainerSettled.current
    Box(modifier.clipToBounds().background(Color(0xFF252931)).testTag("playlist_artwork_background")) {
        if (cover.isNotBlank()) AsyncImage(
            model = remember(context, cover, lifecycle) {
                // A pane already moving into view does not need a second animation under its blur.
                val fadeMs = if (containerSettled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) 220 else 0
                ImageRequest.Builder(context).data(coverRequestUrl(cover, 192)).size(192).crossfade(fadeMs).build()
            }, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }
                .layout { image, constraints ->
                    // Only the blurred 192px artwork uses a smaller surface. The gradient, text
                    // and controls still draw at the device's native resolution.
                    val width = constraints.maxWidth
                    val height = constraints.maxHeight
                    val child = image.measure(Constraints.fixed((width / 4).coerceAtLeast(1), (height / 4).coerceAtLeast(1)))
                    layout(width, height) {
                        child.placeWithLayer(0, 0) {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = width.toFloat() / child.width
                            scaleY = height.toFloat() / child.height
                        }
                    }
                }
                .blur(12.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = .30f), Color.Black.copy(alpha = .52f)))))
        // Fade the artwork into the actual list color over the lower half. A flat
        // slope at both ends avoids a dark stripe followed by a short white ramp.
        val fade = remember(surface) {
            Brush.verticalGradient(*buildList {
                add(0f to surface.copy(alpha = 0f))
                for (step in 0..24) {
                    val t = step / 24f
                    val alpha = t * t * t * (t * (t * 6 - 15) + 10)
                    add(.45f + .55f * t to surface.copy(alpha = alpha))
                }
            }.toTypedArray())
        }
        Box(Modifier.matchParentSize().background(fade))
    }
}
