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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.currencortex.music.ui.component.LocalMusicPageActive
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
    val progress by remember(list, heroHeightPx) { derivedStateOf {
        if (list.firstVisibleItemIndex > 0) 1f
        else (list.firstVisibleItemScrollOffset / (heroHeightPx * .4f).coerceAtLeast(1f)).coerceIn(0f, 1f)
    } }
    val pageActive = LocalMusicPageActive.current
    val statusStyle = LocalPlaylistStatusBarStyle.current
    val darkStatusBackground = LocalDarkTheme.current || progress < .8f
    LaunchedEffect(pageActive, darkStatusBackground, statusStyle) {
        if (pageActive) statusStyle?.invoke(darkStatusBackground)
    }
    val base = rememberGlassBackdrop()
    val body = rememberGlassBackdrop()
    val combined = if (base != null && body != null) rememberCombinedBackdrop(base, body) else base
    SettingsUiScope {
    CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
    Box(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding().testTag(tag)) {
    Box(Modifier.widthIn(max = 840.dp).fillMaxSize().align(Alignment.TopCenter)) {
        Box(Modifier.matchParentSize().then(if (base != null) Modifier.layerBackdrop(base) else Modifier)
            .background(colors.background)) {
            PlaylistArtworkBackground(cover, colors.background, Modifier.fillMaxWidth()
                .height(topInset + heroHeight + 8.dp).graphicsLayer {
                    translationY = if (list.firstVisibleItemIndex == 0) -list.firstVisibleItemScrollOffset.toFloat() else -size.height
                })
        }
        CompositionLocalProvider(LocalGlassBackdrop provides base) {
            Box(Modifier.fillMaxSize().then(if (body != null) Modifier.layerBackdrop(body) else Modifier)) { content(topInset) }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides combined) {
            SettingsFloatingHeader(background = colors.background, opacity = .9f * progress) {
                Box(Modifier.fillMaxWidth().statusBarsPadding()) {
                    toolbar(progress)
                }
            }
        }
    }
    }
    }
    }
}

@Composable private fun PlaylistArtworkBackground(cover: String, surface: Color, modifier: Modifier) {
    val context = LocalContext.current
    Box(modifier.clipToBounds().background(Color(0xFF252931)).testTag("playlist_artwork_background")) {
        if (cover.isNotBlank()) AsyncImage(
            model = remember(context, cover) {
                ImageRequest.Builder(context).data(coverRequestUrl(cover, 192)).size(192).crossfade(220).build()
            }, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }
                .blur(48.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded))
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
