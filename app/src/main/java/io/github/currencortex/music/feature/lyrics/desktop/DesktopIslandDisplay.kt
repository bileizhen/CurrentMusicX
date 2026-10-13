package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

/** Keep the cover fixed while the content crossfades and its natural height changes. */
@Composable internal fun DesktopIslandDisplay(fpsMode: Boolean, motion: Boolean,
    lyrics: @Composable () -> Unit, fps: @Composable () -> Unit) {
    AnimatedContent(fpsMode, contentAlignment = Alignment.Center, label = "island lyrics and fps",
        transitionSpec = {
            (fadeIn(tween(if (motion) 240 else 0)) togetherWith fadeOut(tween(if (motion) 180 else 0)))
                .using(SizeTransform(clip = false) { _, _ -> tween(if (motion) 260 else 0) })
        }) { showFps ->
        if (showFps) fps() else lyrics()
    }
}
