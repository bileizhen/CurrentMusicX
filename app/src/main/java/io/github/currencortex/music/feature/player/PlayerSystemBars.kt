package io.github.currencortex.music.feature.player

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** The status bar belongs to this player only; exiting or rotating restores its prior state. */
@Composable internal fun PlayerSystemBars(immersive: Boolean) {
    val window = LocalActivity.current?.window
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(window, view, lifecycle, immersive) {
        if (window == null || !immersive) return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val bars = WindowInsetsCompat.Type.statusBars()
        val wasVisible = ViewCompat.getRootWindowInsets(view)?.isVisible(bars) != false
        val behavior = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(bars)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) controller.hide(bars)
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.systemBarsBehavior = behavior
            if (wasVisible) controller.show(bars)
        }
    }
}
