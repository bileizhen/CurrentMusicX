package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import io.github.currencortex.music.core.media.IslandNoticeKind
import io.github.currencortex.music.core.media.SoundEffectNotice
import kotlin.math.roundToInt

/** Keep one body per kind so progress/result updates never restart the content transition. */
@Composable internal fun DesktopIslandNoticeTransition(notice: SoundEffectNotice?, height: Dp,
    font: Float, onInstall: () -> Unit) {
    var retainedDownload by remember { mutableStateOf<SoundEffectNotice?>(null) }
    var retainedEffect by remember { mutableStateOf<SoundEffectNotice?>(null) }
    val download = notice?.takeIf { it.kind == IslandNoticeKind.UPDATE_DOWNLOAD } ?: retainedDownload
    val effect = notice?.takeIf { it.kind == IslandNoticeKind.SOUND_EFFECT } ?: retainedEffect
    SideEffect {
        if (notice?.kind == IslandNoticeKind.UPDATE_DOWNLOAD) retainedDownload = notice
        if (notice?.kind == IslandNoticeKind.SOUND_EFFECT) retainedEffect = notice
    }
    val motion = rememberDesktopMotion()
    val progress = animateFloatAsState(if (notice?.kind == IslandNoticeKind.UPDATE_DOWNLOAD) 0f else 1f,
        tween(if (motion && download != null && effect != null) 400 else 0,
            easing = FastOutSlowInEasing), label = "island notice kind")
    Layout(modifier = Modifier.testTag("desktop_island_notice_transition"), content = {
        Box { download?.let { SoundEffectIslandPill(it, material = false, height = height, font = font,
            onUpdateInstall = onInstall) } }
        Box { effect?.let { SoundEffectIslandPill(it, material = false, height = height, font = font) } }
    }) { children, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val from = children[0].measure(loose)
        val to = children[1].measure(loose)
        val p = progress.value
        val width = constraints.constrainWidth((from.width + (to.width - from.width) * p).roundToInt())
        val measuredHeight = constraints.constrainHeight((from.height + (to.height - from.height) * p).roundToInt())
        val drift = 3.dp.roundToPx()
        layout(width, measuredHeight) {
            if (p < 1f) from.placeWithLayer((width - from.width) / 2,
                (measuredHeight - from.height) / 2) {
                alpha = 1f - p
                translationY = -drift * p
            }
            if (p > 0f) to.placeWithLayer((width - to.width) / 2,
                (measuredHeight - to.height) / 2) {
                alpha = p
                translationY = drift * (1f - p)
            }
        }
    }
}
