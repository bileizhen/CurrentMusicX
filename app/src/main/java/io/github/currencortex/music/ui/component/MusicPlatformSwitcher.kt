package io.github.currencortex.music.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.currencortex.music.R
import io.github.currencortex.music.data.settings.MusicPlatform
import kotlin.math.roundToInt

private val PlatformRevealEasing = CubicBezierEasing(.2f, 0f, 0f, 1f)
private val DisabledPlatformFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

private data class PlatformOption(
    val label: String,
    val icon: Int,
    val tag: String,
    val platform: MusicPlatform? = null,
)

/** The reveal, search-text displacement and selected icon share one horizontal position. */
@Composable
fun MusicPlatformSwitcher(
    platform: MusicPlatform,
    onSelect: (MusicPlatform) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by rememberSaveable(platform) { mutableStateOf(false) }
    var pendingSelection by rememberSaveable(platform) { mutableStateOf<MusicPlatform?>(null) }
    val selectionMotion = remember(platform) { Animatable(0f) }
    val onSelectLatest by rememberUpdatedState(onSelect)
    val active = LocalMusicPageActive.current && enabled
    val switching = pendingSelection != null
    val reveal by animateFloatAsState(
        targetValue = if (expanded || switching) 1f else 0f,
        animationSpec = tween(if (expanded) 300 else 240, easing = PlatformRevealEasing),
        label = "Platform reveal",
    )
    val progress = selectionMotion.value
    val position = reveal * (1f - progress)
    val scroll = rememberScrollState()
    val alternatives = remember(platform) {
        MusicPlatform.entries.filter { it != platform }.map {
            PlatformOption(it.label, platformIcon(it), it.name, it)
        } + listOf(
            PlatformOption("酷狗音乐", R.drawable.ic_platform_kugou, "KUGOU"),
            PlatformOption("汽水音乐", R.drawable.ic_platform_qishui, "QISHUI"),
            PlatformOption("波点音乐", R.drawable.ic_platform_bodian, "BODIAN"),
            PlatformOption("Spotify", R.drawable.ic_platform_spotify, "SPOTIFY"),
            PlatformOption("YouTube Music", R.drawable.ic_platform_youtube_music, "YOUTUBE_MUSIC"),
        )
    }

    BackHandler(enabled = expanded && active && !switching) { expanded = false }

    LaunchedEffect(active) {
        if (!active) expanded = false
    }
    LaunchedEffect(expanded) {
        if (!expanded) scroll.scrollTo(0)
    }
    LaunchedEffect(pendingSelection, platform) {
        val selection = pendingSelection ?: return@LaunchedEffect
        // Compose applies the system animation-duration scale, including zero duration.
        selectionMotion.animateTo(1f, tween(280, easing = PlatformRevealEasing))
        onSelectLatest(selection)
        // Keep the selected icon in place until the persisted platform reaches this page.
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier.height(48.dp)) {
            val step = (maxWidth / (alternatives.size + 1)).coerceAtLeast(40.dp)
            val stepPx = with(LocalDensity.current) { step.toPx() }
            val cellWidth = 48.dp + (step - 48.dp) * position
            val contentWidth = cellWidth + step * alternatives.size * position
            Box(Modifier.width(contentWidth.coerceAtMost(maxWidth)).height(48.dp)
                .horizontalScroll(scroll, enabled = expanded && !switching)) {
                Box(Modifier.width(contentWidth).height(48.dp)) {
                    if (reveal > 0f || expanded || switching) {
                        alternatives.forEachIndexed { index, option ->
                            val selected = option.platform != null && option.platform == pendingSelection
                            val interactive = option.platform != null
                            Box(
                                Modifier.offset { IntOffset((stepPx * (index + 1) * position).roundToInt(), 0) }
                                    .width(cellWidth).height(48.dp)
                                    .zIndex(if (selected) 2f else 1f)
                                    .graphicsLayer {
                                        alpha = if (selected) 1f else reveal * (1f - progress)
                                        val scale = .88f + .12f * reveal
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .clip(CircleShape)
                                    .testTag("home_platform_${option.tag}")
                                    .semantics {
                                        contentDescription = if (interactive) "切换至${option.label}" else "${option.label}，暂未开放"
                                        if (!interactive) disabled()
                                    }
                                    .then(if (interactive) Modifier.clickable(
                                        enabled = active && expanded && !switching && reveal > .95f,
                                        role = Role.Button,
                                        onClickLabel = "切换至${option.label}",
                                        onClick = { pendingSelection = option.platform },
                                    ) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                MusicPlatformIcon(option.icon, enabled = interactive)
                            }
                        }
                    }
                    Box(
                        Modifier.width(cellWidth).height(48.dp).zIndex(3f)
                            .graphicsLayer {
                                alpha = (1f - progress * 1.5f).coerceAtLeast(0f)
                                translationX = -stepPx * .25f * progress
                                val scale = 1f - .16f * progress
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(CircleShape)
                            .testTag("home_platform_toggle")
                            .semantics {
                                contentDescription = "当前平台，${platform.label}"
                                stateDescription = when {
                                    switching -> "正在切换至${pendingSelection?.label}"
                                    expanded -> "已展开"
                                    else -> "已收起"
                                }
                            }
                            .clickable(
                                enabled = active && !switching,
                                role = Role.Button,
                                onClickLabel = if (expanded) "收起平台选择" else "展开平台选择",
                                onClick = { expanded = !expanded },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        MusicPlatformIcon(platformIcon(platform))
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicPlatformIcon(icon: Int, enabled: Boolean = true) {
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(32.dp).clip(CircleShape),
        colorFilter = if (enabled) null else DisabledPlatformFilter,
        alpha = if (enabled) 1f else .38f,
    )
}

private fun platformIcon(platform: MusicPlatform): Int = when (platform) {
    MusicPlatform.NETEASE -> R.drawable.ic_platform_netease
    MusicPlatform.QQ_MUSIC -> R.drawable.ic_platform_qq_music
}
