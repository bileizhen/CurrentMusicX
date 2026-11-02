package io.github.currencortex.music.feature.player

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.currencortex.music.ui.component.LocalMusicPageActive
import io.github.currencortex.music.ui.component.LocalPageContainerSettled
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A player destination covers the retained player; it never docks or reopens the player. */
@Composable internal fun PlayerEffectsOverlay(open: Boolean, onDismiss: () -> Unit,
    onPresentedChanged: (Boolean) -> Unit,
    effects: @Composable (() -> Unit, () -> Unit) -> Unit,
    equalizer: @Composable (() -> Unit) -> Unit) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = open
    val presented = visibility.currentState || visibility.targetState
    val window = LocalActivity.current?.window
    val lightBars = MiuixTheme.colorScheme.background.luminance() >= .5f
    DisposableEffect(window, presented, lightBars) {
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        if (presented) {
            controller?.isAppearanceLightStatusBars = lightBars
            controller?.isAppearanceLightNavigationBars = lightBars
        }
        onDispose {
            if (oldStatus != null) controller?.isAppearanceLightStatusBars = oldStatus
            if (oldNavigation != null) controller?.isAppearanceLightNavigationBars = oldNavigation
        }
    }
    var showEqualizer by rememberSaveable { mutableStateOf(false) }
    SideEffect { onPresentedChanged(presented) }
    LaunchedEffect(presented) { if (!presented) showEqualizer = false }
    BackHandler(enabled = presented) { if (open) { if (showEqualizer) showEqualizer = false else onDismiss() } }
    AnimatedVisibility(visibility, enter = slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(180)),
        exit = slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(180))) {
        CompositionLocalProvider(LocalMusicPageActive provides open,
            LocalPageContainerSettled provides (open && visibility.isIdle)) {
            Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background).testTag("player_effects_overlay").pointerInput(Unit) {
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }) {
                AnimatedContent(showEqualizer, transitionSpec = {
                    (slideInHorizontally(tween(260)) { if (targetState) it / 4 else -it / 4 } + fadeIn(tween(160))) togetherWith
                        (slideOutHorizontally(tween(260)) { if (targetState) -it / 4 else it / 4 } + fadeOut(tween(160)))
                }, label = "player-effects-equalizer") { eq ->
                    CompositionLocalProvider(LocalMusicPageActive provides (open && eq == showEqualizer)) {
                        if (eq) equalizer { showEqualizer = false }
                        else effects(onDismiss) { showEqualizer = true }
                    }
                }
            }
        }
    }
}
