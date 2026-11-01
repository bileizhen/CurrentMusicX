package io.github.currencortex.music.feature.room

import io.github.currencortex.music.ui.component.nativeui.*

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.LocalMusicPageActive
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Snapshots keep outgoing content intact; only the destination accepts input and back. */
@Composable internal fun <T> ListenTogetherPageTransition(
    target: T, key: (T) -> String, depth: (T) -> Int,
    modifier: Modifier = Modifier, content: @Composable (T) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    val parentActive = LocalMusicPageActive.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    AnimatedContent(target, modifier, contentKey = key, label = "listen-page",
        transitionSpec = {
            val forward = if (depth(targetState) >= depth(initialState)) direction else -direction
            (fadeIn(tween(220)) + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { forward * it / 7 }) togetherWith
                (fadeOut(tween(150)) + slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -forward * it / 10 })
        }) { page ->
        val active = parentActive && key(page) == key(target)
        holder.SaveableStateProvider(key(page)) {
            CompositionLocalProvider(LocalMusicPageActive provides active) {
                Box(if (active) Modifier.fillMaxSize().zIndex(1f) else Modifier.fillMaxSize().clearAndSetSemantics { }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume()
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    }) { content(page) }
            }
        }
    }
}

/** Same geometry as the result cards. No fake progress percentage or delayed network result. */
@Composable internal fun ListenTogetherSkeleton(label: String, tag: String, rows: Int = 3) {
    io.github.currencortex.music.ui.component.MusicPlaceholderScope {
        Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ListenLoadingLabel(label)
            repeat(rows) { index ->
                io.github.currencortex.music.ui.component.LoadingPreferenceCard(showAction = index == 0)
            }
        }
    }
}

@Composable internal fun ListenLoadingLabel(text: String, modifier: Modifier = Modifier, primary: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ListenProgress(primary = primary)
        Text(text, fontSize = 12.sp, color = if (primary) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable internal fun ListenProgress(modifier: Modifier = Modifier, size: Dp = 16.dp, primary: Boolean = false) {
    val ink = if (primary) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.primary
    if (LocalMusicPageActive.current) CircularProgressIndicator(modifier.size(size), size = size, strokeWidth = 2.dp,
        colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = ink, backgroundColor = ink.copy(alpha = .12f)))
    else Spacer(modifier.size(size))
}
