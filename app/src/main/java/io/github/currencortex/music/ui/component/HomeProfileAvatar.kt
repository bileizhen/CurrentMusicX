package io.github.currencortex.music.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Fetch during home loading, then reveal only after both the image and launch have settled. */
@Composable fun HomeProfileAvatar(url: String?, ready: Boolean, onOpen: (String?, Rect?) -> Unit,
    decorationUrl: String? = null, decorationScale: Double = 1.0) {
    key(url) {
        var settled by remember { mutableStateOf(url == null) }
        var failed by remember { mutableStateOf(url == null) }
        var revealed by rememberSaveable { mutableStateOf(false) }
        val reveal = remember { Animatable(if (revealed) 1f else 0f) }
        var bounds by remember { mutableStateOf<Rect?>(null) }
        var imageCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val launching = LocalLaunchBrand.current?.active == true
        val flight = LocalAvatarFlight.current
        val placement = LocalRootTabPlacement.current
        var frameLoaded by remember(decorationUrl) { mutableStateOf(false) }
        val originProvider by rememberUpdatedState<() -> AvatarFlightOrigin?>({
            val origin = if (settled && reveal.value > 0f && !launching)
                imageCoordinates?.takeIf { it.isAttached }?.boundsInRoot() ?: bounds else null
            origin?.takeIf { it.bottom > 0f && it.top >= 0f && (placement?.visible(it) != false) }?.let {
                AvatarFlightOrigin(if (failed) null else url, it, decorationUrl.takeIf { frameLoaded }, decorationScale)
            }
        })
        DisposableEffect(flight) {
            val provider = { originProvider() }
            flight?.homeOrigin = provider
            onDispose { if (flight != null && flight.homeOrigin === provider) flight.homeOrigin = null }
        }
        LaunchedEffect(settled && ready && !launching) {
            if (settled && ready && !launching && !revealed) {
                reveal.animateTo(1f, tween(380, easing = CubicBezierEasing(.2f, 0f, .2f, 1f)))
                revealed = true
            }
        }
        Box(Modifier.size(48.dp).testTag("home_profile_avatar")
            .semantics { contentDescription = "账号头像，打开我的" }
            .clickable(role = Role.Button, onClickLabel = "打开我的") {
                val origin = if (settled && reveal.value > 0f) imageCoordinates?.takeIf { it.isAttached }?.boundsInRoot() ?: bounds else null
                onOpen(if (failed) null else url, origin)
            },
            contentAlignment = Alignment.Center) {
            Box(Modifier.size(42.dp).onGloballyPositioned { bounds = it.boundsInRoot() }
                .graphicsLayer {
                    alpha = if (settled && flight?.active != true) reveal.value else 0f
                    scaleX = .84f + .16f * reveal.value; scaleY = scaleX
                    translationY = (1f - reveal.value) * 3.dp.toPx()
                }.onGloballyPositioned {
                    imageCoordinates = it
                    if (flight?.active == true && flight.returning) {
                        (placement?.landingBounds(it) ?: it.boundsInRoot()).takeIf { rect -> rect.top >= 0f }
                            ?.let { rect -> flight.destination = rect }
                    }
                }) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(MiuixTheme.colorScheme.primary.copy(alpha = .08f)),
                    contentAlignment = Alignment.Center) {
                    if (failed) Icon(Icons.Default.Person, null, Modifier.size(24.dp).testTag("home_avatar_fallback"),
                        tint = MiuixTheme.colorScheme.onSurface.copy(alpha = .55f))
                    if (url != null) AsyncImage(rememberAvatarRequest(url), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            .testTag(if (settled && !failed) "home_avatar_image" else "home_avatar_pending"),
                        onSuccess = { settled = true; failed = false }, onError = { settled = true; failed = true })
                }
                if (decorationUrl != null) AsyncImage(rememberDecorationRequest(decorationUrl), null,
                    Modifier.align(Alignment.Center).requiredSize(42.dp * boundedDecorationScale(decorationScale)).testTag("home_avatar_frame"),
                    contentScale = ContentScale.Fit, onSuccess = { frameLoaded = true }, onError = { frameLoaded = false })
            }
        }
    }
}

@Composable internal fun rememberAvatarRequest(url: String?): ImageRequest {
    val context = LocalContext.current
    return remember(context, url) { ImageRequest.Builder(context).data(url).size(256).build() }
}

internal fun boundedDecorationScale(scale: Double) = scale.takeIf { it.isFinite() }?.coerceIn(1.0, 2.5)?.toFloat() ?: 1f
@Composable internal fun rememberDecorationRequest(url: String): ImageRequest {
    val context = LocalContext.current
    return remember(context, url) { ImageRequest.Builder(context).data(url).size(512).build() }
}

@Composable internal fun AvatarFlightPicture(url: String?, modifier: Modifier,
    decorationUrl: String? = null, decorationScale: Double = 1.0) {
    Box(modifier,
        contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(MiuixTheme.colorScheme.primary.copy(alpha = .08f)),
            contentAlignment = Alignment.Center) {
        if (url == null) Icon(Icons.Default.Person, null, Modifier.size(32.dp), tint = MiuixTheme.colorScheme.onSurface.copy(alpha = .55f))
        if (url != null) AsyncImage(rememberAvatarRequest(url), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (decorationUrl != null) AsyncImage(rememberDecorationRequest(decorationUrl), null,
            Modifier.requiredSize(64.dp * boundedDecorationScale(decorationScale)).testTag("flying_avatar_frame"), contentScale = ContentScale.Fit)
    }
}
